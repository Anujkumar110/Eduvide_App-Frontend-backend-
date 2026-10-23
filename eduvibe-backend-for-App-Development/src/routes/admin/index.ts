import { Router } from 'express';
import verificationRoutes from './verification.routes';
import userRoutes from './user.routes';

const router = Router();


router.use("/users", userRoutes)
router.use('/verifications', verificationRoutes);

export default router;