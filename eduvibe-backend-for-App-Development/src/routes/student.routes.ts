import { Router } from 'express';
import * as studentController from '../controllers/student.controller';
import { authenticate } from '../middleware/auth.middleware';
import { requireRole } from '../middleware/rbac.middleware';
import { createFieldsUploader, validateMagicBytes } from '../middleware/upload';
import { uploadLimiter } from '../middleware/rateLimit.middleware';
import { getStudentSlots } from '../controllers/slot.controller'; // ✅ ADD

const router = Router();

const studentProfileUploader = createFieldsUploader([
  { name: 'profilePhoto', profile: 'photo' },
]);

router.get('/profile', authenticate, requireRole('student'), studentController.getProfile);

router.patch(
  '/profile',
  authenticate,
  requireRole('student'),
  uploadLimiter,
  studentProfileUploader.fields([
    { name: 'profilePhoto', maxCount: 1 },
  ]),
  validateMagicBytes,
  studentController.updateProfile
);

router.get(
  '/enrolled-classes',
  authenticate,
  requireRole('student'),
  studentController.getEnrolledClasses
);

// ✅ ADD: Student ke booked slots
router.get(
  '/my-slots',
  authenticate,
  requireRole('student'),
  getStudentSlots
);

export default router;