import { Router } from 'express';
import * as teacherController from '../controllers/teacher.controller';
import { authenticate } from '../middleware/auth.middleware';
import { requireRole } from '../middleware/rbac.middleware';
import { teacherProfileUploader, validateMagicBytes } from '../middleware/upload';
import { uploadLimiter } from '../middleware/rateLimit.middleware';

const router = Router();

router.get(
  '/me',
  authenticate,
  requireRole('teacher'),
  teacherController.getTeacherProfile
);
router.put(
  '/profile',
  authenticate,
  requireRole('teacher'),
  uploadLimiter,
  teacherProfileUploader.fields([
    { name: 'profilePhoto', maxCount: 1 },
    { name: 'introVideo', maxCount: 1 },
  ]),
  validateMagicBytes,
  teacherController.updateProfile
);

router.post(
  '/onboarding',
  authenticate,
  requireRole('teacher'),
  uploadLimiter,
  teacherProfileUploader.fields([
    { name: 'idProof', maxCount: 1 },
    { name: 'profilePhoto', maxCount: 1 },
  ]),
  validateMagicBytes,
  teacherController.submitOnboarding
);

router.get(
  '/classes/upload-auth',
  authenticate,
  requireRole('teacher'),
  teacherController.getUploadAuth
);

router.get('/dashboard', authenticate, requireRole('teacher'), teacherController.getDashboard);
router.get('/students',  authenticate, requireRole('teacher'), teacherController.getStudents);

// ── Slot routes — BEFORE /:id ─────────────────────────────────────────────────
router.get('/my-slots', authenticate, requireRole('teacher'), teacherController.getMySlots);

// ── Public routes ─────────────────────────────────────────────────────────────
router.get('/classes', authenticate, teacherController.getClasses);
router.get('/',        teacherController.listTeachers);

// ⚠️  Dynamic routes ALWAYS last
router.get('/:id', teacherController.getTeacher);

export default router;