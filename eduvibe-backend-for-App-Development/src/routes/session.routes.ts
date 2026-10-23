import { Router } from 'express';
import * as sessionController from '../controllers/session.controller';
import { authenticate } from '../middleware/auth.middleware';
import { requireRole } from '../middleware/rbac.middleware';

const router = Router();

// Start a session (teacher only)
router.post(
  '/classes/:id/sessions/start',
  authenticate,
  requireRole('teacher'),
  sessionController.startSession
);

// List sessions for a class
router.get(
  '/classes/:id/sessions',
  authenticate,
  sessionController.listSessions
);

// End a session (teacher only)
router.post(
  '/sessions/:id/end',
  authenticate,
  requireRole('teacher'),
  sessionController.endSession
);

// Get single session details
router.get(
  '/sessions/:id',
  authenticate,
  sessionController.getSession
);

// Mark attendance (student only)
router.post(
  '/sessions/:id/attend',
  authenticate,
  requireRole('student'),
  sessionController.attendSession
);

// Get student's sessions
router.get(
  '/students/my-sessions',
  authenticate,
  requireRole('student'),
  sessionController.getStudentSessions
);

// Get teacher's sessions
router.get(
  '/teachers/my-sessions',
  authenticate,
  requireRole('teacher'),
  sessionController.getTeacherSessions
);

// ✅ NEW — Student early leave with reason
router.post(
  '/sessions/:id/leave',
  authenticate,
  requireRole('student'),
  sessionController.leaveSession
);

export default router;