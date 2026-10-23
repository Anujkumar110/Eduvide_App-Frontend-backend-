import mongoose from 'mongoose';
import { env } from './src/config/env';
import { logger } from './src/utils/logger';

import './src/workers/upload.worker';
import './src/workers/notification.worker';
// ❌ REMOVED: startSessionWorker — session automation is handled by session.cron.ts in server.ts

const startWorker = async () => {
  try {
    // Connect to database
    await mongoose.connect(env.MONGO_URI);
    logger.info('Worker connected to MongoDB successfully');

    // The workers are automatically initialized upon import and will start listening to the queues.
    logger.info('Workers are running and listening to queues...');

    // Graceful shutdown
    process.on('SIGINT', async () => {
      logger.info('SIGINT received. Shutting down worker gracefully...');
      await mongoose.connection.close();
      process.exit(0);
    });

    process.on('SIGTERM', async () => {
      logger.info('SIGTERM received. Shutting down worker gracefully...');
      await mongoose.connection.close();
      process.exit(0);
    });

  } catch (error) {
    logger.error('Worker failed to start', { error });
    process.exit(1);
  }
};

startWorker();