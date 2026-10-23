import { Router } from 'express';
import { authenticate } from '../middleware/auth.middleware';
import * as chatController from '../controllers/chat.controller';

const router = Router();

router.use(authenticate);

router.get('/conversations', chatController.getConversations);
router.get('/:conversationId/messages', chatController.getMessages);

export default router;
