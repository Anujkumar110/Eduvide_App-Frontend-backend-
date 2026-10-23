import Redis from 'ioredis';
import { env } from './env';
import { logger } from '../utils/logger';

export const redisConnection = new Redis({
  username: env.REDIS_USER,
  host: env.REDIS_HOST,
  port: Number(env.REDIS_PORT),
  password: env.REDIS_PASSWORD,
  maxRetriesPerRequest: null,
  enableReadyCheck: false,
  lazyConnect: false,
});

redisConnection.on('connect', () => {
  logger.info('Connected to Redis successfully');
});

redisConnection.on('error', (err) => {
  logger.error('Redis connection error', { err });
});