import { Request, Response, NextFunction } from 'express';
import * as paymentService from '../../services/business/payment.service';
import { sendSuccess } from '../../utils/response';

/**
 * POST /api/v1/payments/create-order
 * Create a Razorpay order for wallet top-up
 */
export const createOrder = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { amount } = req.body;

    const result = await paymentService.createOrder({
      amount,
      userId: req.user!.userId,
    });

    sendSuccess(res, 'Wallet top-up order created', result);
  } catch (err) {
    next(err);
  }
};

/**
 * POST /api/v1/payments/verify
 * Verify Razorpay payment and credit wallet
 */
export const verifyPayment = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { razorpay_order_id, razorpay_payment_id, razorpay_signature } = req.body;

    if (!razorpay_order_id || !razorpay_payment_id || !razorpay_signature) {
      return res.status(400).json({
        success: false,
        message: 'Missing required payment fields',
      });
    }

    const result = await paymentService.handlePaymentSuccess(
      razorpay_order_id,
      razorpay_payment_id,
      razorpay_signature,
    );

    sendSuccess(res, 'Payment verified and wallet credited', result);
  } catch (err) {
    next(err);
  }
};

/**
 * POST /api/v1/payments/webhook
 * Handle Razorpay webhook events
 */
export const handleWebhook = async (req: Request, res: Response, next: NextFunction) => {
  try {
    await paymentService.handleWebhook(req.body);
    sendSuccess(res, 'Webhook processed');
  } catch (err) {
    next(err);
  }
};

/**
 * GET /api/v1/payments/razorpay-key
 * Get Razorpay key ID for client
 */
export const getRazorpayKey = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const keyId = paymentService.getRazorpayKeyId();
    sendSuccess(res, 'Razorpay key fetched', { keyId });
  } catch (err) {
    next(err);
  }
};