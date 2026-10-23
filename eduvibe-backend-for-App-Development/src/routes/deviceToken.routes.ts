import { Router } from 'express';
import { saveToken } from '../controllers/saveToken.controller';
import { authenticate } from '../middleware/auth.middleware';

const router = Router();

router.use(authenticate);

router.post('/', saveToken);

export default router;
