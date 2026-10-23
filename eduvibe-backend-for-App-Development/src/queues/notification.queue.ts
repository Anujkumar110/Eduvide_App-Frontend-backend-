import { Queue } from 'bullmq';
import { redisConnection } from '../config/redis';

export const notificationQueue = new Queue('notificationQueue', {
  connection: redisConnection as any,
  defaultJobOptions: {
    attempts: 5,
    backoff: {
      type: 'exponential',
      delay: 2000,
    },
    removeOnComplete: true,
    removeOnFail: false,
  },
});

export const NOTIFICATION_JOB_TYPES = {
  SEND_NOTIFICATION: 'SEND_NOTIFICATION',
  BULK_NOTIFICATION: 'BULK_NOTIFICATION',
} as const;

export const NOTIFICATION_EVENTS = {
  USER_SIGNUP_TEACHER: 'USER_SIGNUP_TEACHER',
  CLASS_JOINED: 'CLASS_JOINED',
  VERIFICATION_STATUS: 'VERIFICATION_STATUS',
  UPLOAD_COMPLETE: 'UPLOAD_COMPLETE',
  NEW_MESSAGE: 'NEW_MESSAGE',
} as const;
