import { Router } from 'express';
import adminRoutes from './admin';
import { requireRole } from '../middleware/rbac.middleware';
import { authenticate } from '../middleware/auth.middleware';

const router = Router();

router.use(authenticate, requireRole('admin'), adminRoutes);

export default router;