import { Router } from 'express';
import * as paymentController from '../controllers/business/payment.controller';
import { authenticate } from '../middleware/auth.middleware';

const router = Router();

// Create order - sabhi authenticated users (student + teacher)
router.post('/create-order', authenticate, paymentController.createOrder);

// Verify payment - sabhi authenticated users
router.post('/verify', authenticate, paymentController.verifyPayment);

// Webhook (no auth - Razorpay calls this directly)
router.post('/webhook', paymentController.handleWebhook);

// Get Razorpay key (public)
router.get('/razorpay-key', paymentController.getRazorpayKey);

export default router;