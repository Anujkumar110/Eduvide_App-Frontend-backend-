import { Router } from 'express';
import * as transactionController from '../../controllers/business/transaction.controller';
import { authenticate } from '../../middleware/auth.middleware';
import { requireRole } from '../../middleware/rbac.middleware';

const router = Router();

router.post(
    '/',
    authenticate,
    // requireRole('student'),
    transactionController.createTransaction
);

router.get(
    '/',
    authenticate,
    // requireRole('student'),
    transactionController.getTransaction
);

export default router;