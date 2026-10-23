import { Router } from 'express';
import authRoutes from './auth.routes';
import teacherRoutes from './teacher.routes';
import studentRoutes from './student.routes';
import classRoutes from './class.routes';
import sessionRoutes from './session.routes';
import enrollmentRoutes from './enrollment.routes';
import bookingRoutes from './booking.routes';
import paymentRoutes from './payment.routes';
import slotRoutes from './slot.routes';
import adminRoutes from './admin.routes';
import notificationRoutes from './notification.routes';
import chatRoutes from './chat.routes';
import reviewRoutes from './review.routes';
import configRoutes from './config.routes';
import deviceTokenRoutes from './deviceToken.routes';
import walletRoutes from './business/wallet.routes';
import earlyLeave from './earlyLeave.routes';

const router = Router();

router.get('/health', (_req, res) => {
  res.json({ success: true, message: 'EduVibe API is running', version: 'v1' });
});

router.use('/auth', authRoutes);
router.use('/teachers', teacherRoutes);
router.use('/students', studentRoutes);
router.use('/classes', classRoutes);
router.use('', sessionRoutes);
router.use('/enrollments', enrollmentRoutes);
router.use('/bookings', bookingRoutes);
router.use('/payments', paymentRoutes);
router.use('/slots', slotRoutes);
router.use('/admin', adminRoutes);
router.use('/notifications', notificationRoutes);
router.use('/device-token', deviceTokenRoutes);
router.use('/chat', chatRoutes);
router.use('/reviews', reviewRoutes);
router.use('/config', configRoutes);
router.use('/wallet', walletRoutes);
router.use('', earlyLeave); // NEW: Early leave reporting routes

export default router;
