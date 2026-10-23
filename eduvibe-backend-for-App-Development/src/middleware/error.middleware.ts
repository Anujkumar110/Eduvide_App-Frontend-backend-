import { Request, Response, NextFunction } from 'express';
import { MulterError } from 'multer';
import { AppError } from '../utils/errors';
import { logger } from '../utils/logger';

export const errorHandler = (
  err: Error,
  req: Request,
  res: Response,
  _next: NextFunction
): void => {
  if (err instanceof AppError) {
    logger.warn('Operational error', {
      message: err.message,
      statusCode: err.statusCode,
      path: req.path,
    });
    res.status(err.statusCode).json({ success: false, message: err.message });
    return;
  }

  // Multer upload errors
  if (err instanceof MulterError) {
    const msg = err.code === 'LIMIT_FILE_SIZE'
      ? 'File too large'
      : `Upload error: ${err.message}`;
    res.status(400).json({ success: false, message: msg });
    return;
  }

  // MongoDB duplicate key error
  if ((err as any).code === 11000) {
    const field = Object.keys((err as any).keyValue || {})[0] || 'field';
    res.status(409).json({ success: false, message: `${field} already exists` });
    return;
  }

  // Unknown / programmer errors
  logger.error('Unhandled error', { error: err.message, stack: err.stack, path: req.path });
  res.status(500).json({ success: false, message: 'Internal server error' });
};

export const notFound = (req: Request, _res: Response, next: NextFunction): void => {
  next(new AppError(`Route ${req.originalUrl} not found`, 404));
};
