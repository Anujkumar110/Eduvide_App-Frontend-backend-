import { Router } from 'express';
import * as bookingController from '../controllers/booking.controller';
import { authenticate } from '../middleware/auth.middleware';
import { requireRole } from '../middleware/rbac.middleware';

const router = Router();

// Student routes
router.post(
  '/',
  authenticate,
  requireRole('student'),
  bookingController.createBooking
);

router.get(
  '/students/my-bookings',
  authenticate,
  requireRole('student'),
  bookingController.getStudentBookings
);

// Teacher routes
router.get(
  '/teachers/my-bookings',
  authenticate,
  requireRole('teacher'),
  bookingController.getTeacherBookings
);

// Get teacher availability (public)
router.get(
  '/teachers/:id/availability',
  bookingController.getTeacherAvailability
);

export default router;
