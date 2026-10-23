import { Router } from 'express';
import * as classController from '../controllers/class.controller';
import { authenticate, optionalAuthenticate } from '../middleware/auth.middleware';
import { requireRole } from '../middleware/rbac.middleware';
import { createFieldsUploader, validateMagicBytes } from '../middleware/upload';

const router = Router();

const videoUploader = createFieldsUploader([
  { name: 'video', profile: 'video' },
]);

// ── Public routes (optionalAuthenticate so ?mine and ?enrolled work) ──────────
router.get('/', optionalAuthenticate, classController.listClasses);
router.get('/:id', classController.getClass);
router.get('/:id/peers', classController.getClassPeers);

// ── Teacher-only routes ───────────────────────────────────────────────────────
router.post('/', authenticate, requireRole('teacher'), classController.createClass);
router.put('/:id', authenticate, requireRole('teacher'), classController.updateClass);
router.delete('/:id', authenticate, requireRole('teacher'), classController.deleteClass);
router.get('/:id/students', authenticate, requireRole('teacher'), classController.getClassStudents);
router.post('/:id/video', authenticate, requireRole('teacher'), videoUploader.single('video'), validateMagicBytes, classController.uploadVideo);

export default router;