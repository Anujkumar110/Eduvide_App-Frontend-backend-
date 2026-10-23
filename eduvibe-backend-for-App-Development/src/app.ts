import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import { Request, Response, NextFunction } from 'express';
import { globalLimiter } from './middleware/rateLimit.middleware';
import { errorHandler, notFound } from './middleware/error.middleware';
import { logger } from './utils/logger';
import routes from './routes';
import './models';

const app = express();

// ── Security ─────────────────────────────────────────────────────────────────
app.use(helmet());
app.use(
  cors({
    origin: [
      'http://localhost:3000',
      'https://admin-panel-eduvibe-app.vercel.app',
    ],
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'ngrok-skip-browser-warning'],
    credentials: true,
  })
);

// ── Body Parsing ──────────────────────────────────────────────────────────────
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

// ── Rate Limiting ─────────────────────────────────────────────────────────────
app.use(globalLimiter);

// ── Request Logger ────────────────────────────────────────────────────────────
app.use((req: Request, res: Response, next: NextFunction) => {
  const start = Date.now();
  res.on('finish', () => {
    logger.info('Request', {
      method: req.method,
      url: req.originalUrl,
      statusCode: res.statusCode,
      duration: `${Date.now() - start}ms`,
      userId: req.user?.userId || 'anonymous',
    });
  });
  next();
});

// ── Routes ────────────────────────────────────────────────────────────────────
app.use('/api/v1', routes);

// ── Error Handling ────────────────────────────────────────────────────────────
app.use(notFound);
app.use(errorHandler);

export default app;