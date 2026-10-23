import { Router } from 'express';
import * as enrollmentController from '../controllers/enrollment.controller';
import { authenticate } from '../middleware/auth.middleware';
import { requireRole } from '../middleware/rbac.middleware';


const router = Router();

router.post('/', authenticate, requireRole('student'), enrollmentController.enroll);
router.delete('/:id', authenticate, requireRole('student'), enrollmentController.unenroll);

export default router;
