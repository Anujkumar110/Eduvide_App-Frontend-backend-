import rateLimit from 'express-rate-limit';

const createLimiter = (windowMs: number, max: number, message: string) =>
  rateLimit({
    windowMs,
    max,
    message: { success: false, message },
    standardHeaders: true,
    legacyHeaders: false,
    validate: { xForwardedForHeader: false },
  });

export const globalLimiter = createLimiter(
  15 * 60 * 1000,
  10000,
  'Too many requests, please try again later'
);

export const authLimiter = createLimiter(
  15 * 60 * 1000,
  50,
  'Too many auth attempts, please try again later'
);

export const uploadLimiter = createLimiter(
  60 * 60 * 1000,
  20,
  'Upload limit reached, please try again later'
);