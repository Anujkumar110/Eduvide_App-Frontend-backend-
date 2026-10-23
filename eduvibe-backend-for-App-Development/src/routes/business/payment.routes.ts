import { Router } from 'express';
import * as paymentController from '../../controllers/business/payment.controller';
import { authenticate } from '../../middleware/auth.middleware';
import { requireRole } from '../../middleware/rbac.middleware';

const router = Router();

// Create order (student only)
router.post(
  '/create-order',
  authenticate,
  requireRole('student'),
  paymentController.createOrder
);

// Verify payment (student only)
router.post(
  '/verify',
  authenticate,
  requireRole('student'),
  paymentController.verifyPayment
);

// Webhook (no auth required - Razorpay calls this)
router.post(
  '/webhook',
  paymentController.handleWebhook
);

// Get Razorpay key (public)
router.get(
  '/razorpay-key',
  paymentController.getRazorpayKey
);

export default router;
