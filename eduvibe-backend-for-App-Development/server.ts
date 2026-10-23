import app from './src/app';
import { connectDB } from './src/config/db';
import { env } from './src/config/env';
import { logger } from './src/utils/logger';
import { initWSServer } from './src/ws/wsServer';
import { startSessionCronJob } from './src/cron/session.cron';
import { runSlotGeneration } from './src/cron/slot.cron';

const PORT = parseInt(env.PORT, 10);

const bootstrap = async () => {
  try {
    await connectDB(); // ✅ DB pehle connect hoga

    startSessionCronJob(); // ✅ Cron DB ke baad start hoga — safe hai ab
    runSlotGeneration();

    const server = app.listen(PORT, () => {
      logger.info(`Online Tutor API running on http://localhost:${PORT}/api/v1`);
    });


    const { shutdown: shutdownWebSocket } = initWSServer(server);

    // Graceful shutdown
    const shutdown = async (signal: string) => {
      logger.info(`${signal} received — shutting down gracefully`);
      await shutdownWebSocket().catch((err) => logger.warn('WebSocket shutdown failed', { err }));
      server.close(() => {
        logger.info('HTTP server closed');
        process.exit(0);
      });
    };

    process.on('SIGTERM', () => shutdown('SIGTERM'));
    process.on('SIGINT', () => shutdown('SIGINT'));
  } catch (err) {
    logger.error('Failed to start server', { error: err });
    process.exit(1);
  }
};

bootstrap();