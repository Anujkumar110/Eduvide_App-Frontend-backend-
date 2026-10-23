import { Request, Response, NextFunction } from 'express';
import { Notification } from '../models/notification.model';
import { sendPaginated, sendSuccess } from '../utils/response';
import { AppError } from '../utils/errors';
import { sendNotification as sendPushNotification } from '../services/notification.service';

// ─────────────────────────────────────────────
// 📥 GET NOTIFICATIONS
// ─────────────────────────────────────────────
export const getNotifications = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const userId = req.user!.userId;

    let page = parseInt(req.query.page as string, 10);
    let limit = parseInt(req.query.limit as string, 10);

    if (isNaN(page) || page < 1) page = 1;
    if (isNaN(limit) || limit < 1) limit = 20;
    if (limit > 100) limit = 100;

    const query: Record<string, any> = { userId };

    if (req.query.read !== undefined) {
      if (req.query.read === 'true') query.isRead = true;
      if (req.query.read === 'false') query.isRead = false;
    }

    const skip = (page - 1) * limit;

    const [notifications, total] = await Promise.all([
      Notification.find(query)
        .sort({ createdAt: -1 })
        .skip(skip)
        .limit(limit)
        .lean(),

      Notification.countDocuments(query),
    ]);

    return sendPaginated(res, 'Notifications fetched successfully', notifications, {
      total,
      page,
      limit,
    });

  } catch (err) {
    console.error("❌ getNotifications error:", err);
    next(err);
  }
};

// ─────────────────────────────────────────────
// ✅ MARK SINGLE AS READ
// ─────────────────────────────────────────────
export const markAsRead = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const userId = req.user!.userId;
    const { id } = req.params;

    if (!id) {
      return next(new AppError('Notification ID is required', 400));
    }

    const notification = await Notification.findOneAndUpdate(
      { _id: id, userId },
      { isRead: true },
      { new: true }
    );

    if (!notification) {
      return next(new AppError('Notification not found', 404));
    }

    return sendSuccess(res, 'Notification marked as read', notification);

  } catch (err) {
    console.error("❌ markAsRead error:", err);
    next(err);
  }
};

// ─────────────────────────────────────────────
// ✅ MARK ALL AS READ
// ─────────────────────────────────────────────
export const markAllAsRead = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const userId = req.user!.userId;

    const result = await Notification.updateMany(
      { userId, isRead: false },
      { isRead: true }
    );

    console.log("📖 Marked all as read:", result.modifiedCount);

    return sendSuccess(res, 'All unread notifications marked as read');

  } catch (err) {
    console.error("❌ markAllAsRead error:", err);
    next(err);
  }
};

// ─────────────────────────────────────────────
// 🔔 SEND NOTIFICATION (MAIN FIXED PART)
// ─────────────────────────────────────────────
export const sendNotification = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { userId, type, title, message, metadata } = req.body;

    if (!userId || !type || !title || !message) {
      return next(new AppError('userId, type, title, and message are required', 400));
    }

    console.log("📨 Creating notification:", { userId, type, title });

    // ✅ 1. Save in DB
    const notification = await Notification.create({
      userId,
      type,
      title,
      message,
      metadata,
      isRead: false
    });

    console.log("💾 Notification saved:", notification._id);

    // ✅ 2. Send PUSH (IMPORTANT)
    try {
      await sendPushNotification(userId, title, message, metadata);
      console.log("📲 Push notification sent");
    } catch (pushError) {
      console.error("⚠️ Push failed BUT DB saved:", pushError);
      // ⚠️ don't break API if push fails
    }

    return sendSuccess(res, 'Notification sent and saved successfully', notification);

  } catch (err) {
    console.error("❌ sendNotification error:", err);
    next(err);
  }
};