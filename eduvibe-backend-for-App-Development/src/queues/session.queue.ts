import { Queue } from 'bullmq';
import { redisConnection } from '../config/redis';

export const sessionQueue = new Queue('sessionQueue', {
  connection: redisConnection as any,
  defaultJobOptions: {
    attempts: 3,
    backoff: {
      type: 'exponential',
      delay: 5000,
    },
    removeOnComplete: true,
    removeOnFail: false,
  },
});

export const SESSION_JOB_TYPES = {
  CHECK_UPCOMING: 'CHECK_UPCOMING_SESSIONS',
  START_MISSED: 'START_MISSED_SESSIONS',
  AUTO_END: 'AUTO_END_SESSIONS',
  SEND_REMINDER: 'SEND_SESSION_REMINDER',
} as const;
