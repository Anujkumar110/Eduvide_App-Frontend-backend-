import Razorpay from 'razorpay';
import crypto from 'crypto';
import { Payment } from '../../models/wallet/payment.model';
import { Wallet } from '../../models/wallet/wallet.model';
import { Transaction } from '../../models/wallet/transaction.model';
import { AppError } from '../../utils/errors';
import { createWallet } from './wallet.service';
import {
  notifyTopUpSuccess,
  notifyTopUpFailed,
  notifyTopUpPending,
  notifyPaymentInitiated,
  notifyPaymentSuccess,
} from './payment.notifications';

const DUMMY_MODE =
  process.env.RAZORPAY_DUMMY_MODE === 'true' ||
  !process.env.RAZORPAY_KEY_ID ||
  process.env.RAZORPAY_KEY_ID === 'rzp_test_your_key_id';

console.log(`[Payment] DUMMY_MODE: ${DUMMY_MODE}, KEY: ${process.env.RAZORPAY_KEY_ID}`);

let razorpay: Razorpay | null = null;
if (!DUMMY_MODE) {
  razorpay = new Razorpay({
    key_id: process.env.RAZORPAY_KEY_ID!,
    key_secret: process.env.RAZORPAY_KEY_SECRET!,
  });
}

export interface CreateOrderInput {
  amount: number;
  userId: string;
}

export const createOrder = async ({ amount, userId }: CreateOrderInput) => {
    if (!amount || amount <= 0) throw new AppError('Invalid top-up amount', 400);

    const wallet = await createWallet(userId);

    const shortUserId = userId.toString().slice(-8);
    const shortTime = Date.now().toString().slice(-6);
    const receipt = `wt_${shortUserId}_${shortTime}`;
    const amountInPaise = Math.round(amount * 100);

    if (DUMMY_MODE) {
      console.log(`[Payment DUMMY] Creating dummy order for ₹${amount}`);

      try {
          // ✅ Payment init notification — user ko batao process shuru ho rahi hai
          await notifyPaymentInitiated(userId, amount, 'Wallet');

          const dummyOrderId = `order_dummy_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

          const payment = await Payment.create({
              userId,
              walletId: wallet._id,
              amount,
              razorpayOrderId: dummyOrderId,
              status: 'success',
              receipt,
          });

          const updatedWallet = await Wallet.findByIdAndUpdate(
              wallet._id,
              { $inc: { balance: amount } },
              { new: true }
          );

          await Transaction.create({
              amount,
              type: 'ADD_MONEY',
              status: 'success',
              fromWalletId: wallet._id,
              toWalletId: wallet._id,
              referenceId: payment._id,
          });

          // ✅ Payment success notification — paise wallet mein aa gaye
          await notifyPaymentSuccess(userId, amount, 'Wallet', updatedWallet!.balance);
          await notifyTopUpSuccess(userId, amount, updatedWallet!.balance);

          return {
              orderId: dummyOrderId,
              amount,
              walletId: wallet._id,
              newBalance: updatedWallet!.balance,
              isDummy: true,
          };
      } catch (error: any) {
          throw new AppError(`Dummy order creation failed: ${error.message}`, 500);
      }
    }

    // Real Razorpay mode
    if (!razorpay) {
        throw new AppError('Razorpay not configured', 500);
    }

    try {
        // ✅ Payment init notification — user ko batao order ban raha hai
        await notifyPaymentInitiated(userId, amount, 'Wallet');

        const order = await razorpay.orders.create({
          amount: amountInPaise,
          currency: 'INR',
          receipt,
          notes: {
              userId,
              walletId: wallet._id.toString(),
              purpose: 'wallet_topup',
          },
        });

        await Payment.create({
          userId,
          walletId: wallet._id,
          amount,
          razorpayOrderId: order.id,
          status: 'pending',
          receipt,
        });

        await notifyTopUpPending(userId, amount);

        return {
          orderId: order.id,
          amount,
          walletId: wallet._id,
          isDummy: false,
        };
    } catch (err: any) {
      console.error('[Payment] Razorpay order creation error:', err);
      throw new AppError(`Razorpay order creation failed: ${err.message}`, 500);
    }
};

/**
 * Verify Razorpay payment signature
 */
export const verifyPayment = (
  razorpayOrderId: string,
  razorpayPaymentId: string,
  razorpaySignature: string,
): boolean => {
  const expectedSignature = crypto
    .createHmac('sha256', process.env.RAZORPAY_KEY_SECRET!)
    .update(`${razorpayOrderId}|${razorpayPaymentId}`)
    .digest('hex');

  return expectedSignature === razorpaySignature;
};

/**
 * Verify signature, credit wallet, and settle all records atomically
 */
export const handlePaymentSuccess = async (
  razorpayOrderId: string,
  razorpayPaymentId: string,
  razorpaySignature: string,
) => {
  if (!verifyPayment(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
    throw new AppError('Invalid payment signature', 400);
  }

  const payment = await Payment.findOne({ razorpayOrderId });
  if (!payment) {
    throw new AppError('Payment record not found', 404);
  }

  if (payment.status === 'success') {
    return { payment, alreadyProcessed: true };
  }

  try {
    const updatedPayment = await Payment.findByIdAndUpdate(
      payment._id,
      { razorpayPaymentId, razorpaySignature, status: 'success' },
      { new: true }
    );

    const updatedWallet = await Wallet.findByIdAndUpdate(
      payment.walletId,
      { $inc: { balance: payment.amount } },
      { new: true }
    );

    if (!updatedWallet) {
      throw new AppError('Wallet not found during credit', 404);
    }

    await Transaction.create({
      amount: payment.amount,
      type: 'ADD_MONEY',
      status: 'success',
      fromWalletId: payment.walletId,
      toWalletId: payment.walletId,
      referenceId: payment._id,
    });

    // ✅ Payment verify success — paise wallet mein aa gaye
    await notifyPaymentSuccess(
      payment.userId.toString(),
      payment.amount,
      'Wallet',
      updatedWallet.balance
    );
    await notifyTopUpSuccess(payment.userId.toString(), payment.amount, updatedWallet.balance);

    return {
      payment: updatedPayment,
      newBalance: updatedWallet.balance,
      amountCredited: payment.amount,
      alreadyProcessed: false,
    };
  } catch (err) {
    throw new AppError(`Payment processing failed: ${err instanceof Error ? err.message : 'Unknown error'}`, 500);
  }
};

/**
 * Mark a payment as failed
 */
export const handlePaymentFailure = async (
  razorpayOrderId: string,
  reason: string,
) => {
  const payment = await Payment.findOneAndUpdate(
    { razorpayOrderId, status: 'pending' },
    { status: 'failed', failureReason: reason },
    { new: true },
  );

  if (!payment) {
    return { payment: null, success: false };
  }

  await Transaction.findOneAndUpdate(
    { referenceId: payment._id, status: 'pending' },
    { status: 'failed' },
  );

  await notifyTopUpFailed(payment.userId.toString(), payment.amount, reason);

  return { payment, success: true };
};

/**
 * Handle Razorpay webhook events
 */
export const handleWebhook = async (event: any) => {
  const { event: eventType, payload } = event;

  console.log(`[Razorpay Webhook] Event: ${eventType}`);

  switch (eventType) {
    case 'payment.captured': {
      const entity = payload.payment?.entity;
      const { order_id, id: paymentId, notes } = entity || {};

      if (notes?.purpose !== 'wallet_topup') {
        console.log('[Razorpay Webhook] Ignoring non-wallet-topup event');
        break;
      }

      if (order_id && paymentId) {
        const order = await razorpay!.orders.fetch(order_id);
        const payment = await Payment.findOne({ razorpayOrderId: order_id });

        if (payment?.status === 'success') {
          console.log('[Razorpay Webhook] Already processed, skipping');
          break;
        }

        await handlePaymentSuccess(order_id, paymentId, '');
      }
      break;
    }

    case 'payment.failed': {
      const { order_id, error } = payload.payment?.entity || {};
      if (order_id) {
        await handlePaymentFailure(order_id, error?.description || 'Payment failed');
      }
      break;
    }

    default:
      console.log(`[Razorpay Webhook] Unhandled event: ${eventType}`);
  }
};

/**
 * Get Razorpay key ID for client
 */
export const getRazorpayKeyId = (): string => {
  return DUMMY_MODE ? 'rzp_test_dummy_key' : (process.env.RAZORPAY_KEY_ID || '');
};