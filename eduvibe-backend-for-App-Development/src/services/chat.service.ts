import { Message } from '../models/message.model';
import { Role } from '../constants';

export const getConversations = async (userId: string, role: Role) => {
  let matchCondition: any = {
    $or: [{ senderId: userId }, { receiverId: userId }],
  };

  if (role === 'admin' || role === 'moderator') {
    matchCondition = {
      $or: [
        { senderId: userId },
        { receiverId: userId },
        { conversationId: { $regex: /^support-/ } },
      ],
    };
  }

  const conversations = await Message.aggregate([
    { $match: matchCondition },
    { $sort: { createdAt: -1 } },
    {
      $group: {
        _id: "$conversationId",
        lastMessage: { $first: "$content" },
        lastMessageAt: { $first: "$createdAt" },
        senderId: { $first: "$senderId" },
        receiverId: { $first: "$receiverId" },
        unreadCount: {
          $sum: {
            $cond: [
              { $and: [{ $eq: ["$receiverId", userId] }, { $eq: ["$readAt", null] }] },
              1,
              0,
            ],
          },
        },
      },
    },
    { $sort: { lastMessageAt: -1 } },
  ]);

  return conversations.map(conv => {
    let contactId = conv.senderId === userId ? conv.receiverId : conv.senderId;

    // Admin support override: If admin viewing a support chat, contact is the user
    if (conv._id.startsWith('support-') && (role === 'admin' || role === 'moderator')) {
      contactId = conv._id.replace('support-', '');
    }

    return {
      conversationId: conv._id,
      contactId,
      lastMessage: conv.lastMessage,
      lastMessageAt: conv.lastMessageAt,
      unreadCount: conv.unreadCount,
    };
  });
};

export const getMessages = async (conversationId: string, limit = 50, skip = 0) => {
  const messages = await Message.find({ conversationId })
    .sort({ createdAt: 1 })
    .skip(skip)
    .limit(limit);
  return messages;
};
