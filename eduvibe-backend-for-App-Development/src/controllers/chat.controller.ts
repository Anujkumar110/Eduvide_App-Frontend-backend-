import { Request, Response, NextFunction } from 'express';
import * as chatService from '../services/chat.service';

export const getConversations = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const userId = req.user!.userId;
    const role = req.user!.role;
    const conversations = await chatService.getConversations(userId, role);
    res.json({ success: true, message: 'Conversations fetched successfully', data: conversations });
  } catch (error) {
    next(error);
  }
};

export const getMessages = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const conversationId = req.params.conversationId as string;
    const limit = parseInt(req.query.limit as string || '50', 10);
    const skip = parseInt(req.query.skip as string || '0', 10);

    const messages = await chatService.getMessages(conversationId, limit, skip);
    res.json({ success: true, message: 'Messages fetched successfully', data: messages });
  } catch (error) {
    next(error);
  }
};
