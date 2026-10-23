import { Worker, Job } from 'bullmq';
import { redisConnection } from '../config/redis';
import { logger } from '../utils/logger';
import { NOTIFICATION_JOB_TYPES } from '../queues/notification.queue';
import { Notification } from '../models';
import { publishWsEvent } from '../ws/wsPublisher';

interface NotificationJobData {
  userId: string;
  type: string;
  title: string;
  message: string;
  metadata?: Record<string, any>;
}

export const notificationWorker = new Worker(
  'notificationQueue',
  async (job: Job<NotificationJobData>) => {
    logger.info(`Processing notification job ${job.id}`, { type: job.data.type, userId: job.data.userId });

    if (job.name === NOTIFICATION_JOB_TYPES.SEND_NOTIFICATION) {
      const { userId, type, title, message, metadata } = job.data;

      const notification = new Notification({
        userId,
        type,
        title,
        message,
        metadata,
      });

      await notification.save();
      logger.info(`Notification saved for user ${userId}`);

      // Real-time delivery (best-effort): WebSocket clients receive EVENT_PUSH.
      // If there are no WS subscribers, this is a no-op at the client layer.
      try {
        await publishWsEvent({
          channel: `notifications:user-${userId}`,
          event: 'notification.received',
          data: {
            notificationId: notification._id.toString(),
            notificationType: type,
            title,
            message,
            metadata,
          },
        });
      } catch (err) {
        logger.warn('Failed to publish WS notification event', { err: (err as Error).message, userId });
      }
    } else if (job.name === NOTIFICATION_JOB_TYPES.BULK_NOTIFICATION) {
      // Future: handle bulk
    }

    return { success: true };
  },
  {
    connection: redisConnection as any,
    drainDelay: 60 * 1000
  }
);

notificationWorker.on('failed', (job, err) => {
  logger.error(`Notification job ${job?.id} failed with error: ${err.message}`);
});
