import { Router } from 'express';
import { getNotifications, markAsRead, markAllAsRead, sendNotification } from '../controllers/notification.controller';
import { authenticate } from '../middleware/auth.middleware';

const router = Router();

// Apply authentication middleware to all notification routes
router.use(authenticate);

router.get('/', getNotifications);
router.patch('/read-all', markAllAsRead);
router.patch('/:id/read', markAsRead);
router.post('/send', sendNotification);

export default router;
