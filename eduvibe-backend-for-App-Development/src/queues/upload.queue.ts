import { Queue } from 'bullmq';
import { redisConnection } from '../config/redis';

export const uploadQueue = new Queue('uploadQueue', {
  connection: redisConnection as any,
  defaultJobOptions: {
    attempts: 3,
    backoff: {
      type: 'exponential',
      delay: 1000,
    },
    removeOnComplete: true,
    removeOnFail: false,
  },
});

export const UPLOAD_JOB_TYPES = {
  PROFILE_PHOTO: 'UPLOAD_PROFILE_PHOTO',
  DOCUMENT: 'UPLOAD_DOCUMENT',
  VIDEO: 'UPLOAD_VIDEO',
  INTRO_VIDEO: 'UPLOAD_INTRO_VIDEO',
} as const;
