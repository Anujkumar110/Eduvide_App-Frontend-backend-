import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { env } from '../config/env';
import { AppError } from '../utils/errors';
import { Role } from '../constants';

interface JwtPayload {
  userId: string;
  loggedInAs: Role;
  email: string;
}

export const authenticate = (req: Request, _res: Response, next: NextFunction): void => {
  const authHeader = req.headers.authorization;
  if (!authHeader?.startsWith('Bearer ')) {
    return next(new AppError('No token provided', 401));
  }

  const token = authHeader.split(' ')[1];
  try {
    const decoded = jwt.verify(token, env.JWT_SECRET) as JwtPayload;
    req.user = { userId: decoded.userId, role: decoded.loggedInAs, email: decoded.email };
    next();
  } catch {
    next(new AppError('Invalid or expired token', 401));
  }
};

// ✅ NEW: Token optional — if present and valid, sets req.user; otherwise continues as guest
export const optionalAuthenticate = (req: Request, _res: Response, next: NextFunction): void => {
  const authHeader = req.headers.authorization;
  if (!authHeader?.startsWith('Bearer ')) {
    return next(); // No token — public access, req.user stays undefined
  }

  const token = authHeader.split(' ')[1];
  try {
    const decoded = jwt.verify(token, env.JWT_SECRET) as JwtPayload;
    req.user = { userId: decoded.userId, role: decoded.loggedInAs, email: decoded.email };
  } catch {
    // Invalid token — ignore silently, treat as guest
  }
  next();
};