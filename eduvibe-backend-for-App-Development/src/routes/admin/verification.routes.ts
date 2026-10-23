import { Router } from 'express';
import * as adminController from '../../controllers/admin/verification.controller'

const router = Router();

router.get('/dashboard', adminController.getDashboard);
router.get('/users', adminController.listUsers);
router.get('/', adminController.listVerifications);
router.get('/:id', adminController.getVerification);
router.put('/:id/approve', adminController.approveTeacher);
router.put('/:id/reject', adminController.rejectTeacher);
router.put('/:id/request-reupload', adminController.requestReupload);

export default router;
