import { Router } from 'express';
import { userController } from '../../controllers/user.controller';

const router = Router();

router.get('/', userController.listUsers);
router.get('/student/:id', userController.getStudent);
router.get('/teacher/:id', userController.getTeacher);
router.put('/:id', userController.updateUser);
router.delete('/:id', userController.deleteUser);

export default router;