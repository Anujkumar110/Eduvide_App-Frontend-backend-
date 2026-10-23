import { Router } from 'express';
import * as ledgerController from '../../controllers/business/ledger.controller';
import { authenticate } from '../../middleware/auth.middleware';
import { requireRole } from '../../middleware/rbac.middleware';

const router = Router();

router.get(
    '/',
    authenticate,
    // requireRole('student'),
    ledgerController.getLedger
);

export default router;