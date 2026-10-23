// services/business/payment.notifications.ts
import { sendNotification } from '../notification.service';

export const notifyTopUpSuccess = (userId: string, amount: number, newBalance: number) =>
  sendNotification(userId, 'Wallet Credited', `₹${amount} has been added to your wallet. New balance: ₹${newBalance}`, {
    type: 'WALLET_TOPUP_SUCCESS',
    amount: String(amount),
    newBalance: String(newBalance),
  });

export const notifyTopUpFailed = (userId: string, amount: number, reason?: string) =>
  sendNotification(userId, 'Payment Failed', `Your wallet top-up of ₹${amount} could not be completed. ${reason ? `Reason: ${reason}` : ''}`, {
    type: 'WALLET_TOPUP_FAILED',
    amount: String(amount),
    reason: reason || 'Unknown error',
  });

export const notifyTopUpPending = (userId: string, amount: number) =>
  sendNotification(userId, 'Payment Initiated', `Your wallet top-up of ₹${amount} is being processed.`, {
    type: 'WALLET_TOPUP_PENDING',
    amount: String(amount),
  });

export const notifyPaymentInitiated = (studentUserId: string, amount: number, teacherName: string) =>
  sendNotification(studentUserId, 'Payment Initiated', `Your payment of ₹${amount} to ${teacherName} is being processed.`, {
    type: 'PAYMENT_INITIATED',
    amount: String(amount),
    teacherName,
  });

export const notifyPaymentSuccess = (studentUserId: string, amount: number, teacherName: string, newBalance: number) =>
  sendNotification(studentUserId, 'Payment Successful', `₹${amount} has been successfully paid to ${teacherName}. Remaining balance: ₹${newBalance}`, {
    type: 'PAYMENT_SUCCESS',
    amount: String(amount),
    teacherName,
    newBalance: String(newBalance),
  });

export const notifySessionStarted = (studentUserId: string, className: string, teacherName: string, sessionId: string) =>
  sendNotification(studentUserId, 'Class Started! 🎓', `${teacherName} has started "${className}". Join now!`, {
    type: 'SESSION_STARTED',
    sessionId,
    className,
    teacherName,
  });

export const notifySessionEnded = (studentUserId: string, className: string, sessionId: string) =>
  sendNotification(studentUserId, 'Class Ended', `"${className}" has ended. We hope you had a great session!`, {
    type: 'SESSION_ENDED',
    sessionId,
    className,
  });