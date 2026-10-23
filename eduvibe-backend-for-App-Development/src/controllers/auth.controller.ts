import { Request, Response, NextFunction } from 'express';
import * as authService from '../services/auth.service';
import { sendSuccess } from '../utils/response';
import { AppError } from '../utils/errors';

export const signup = async (req: Request, res: Response, next: NextFunction) => {
  try {
    console.log('SIGNUP BODY:', req.body);
    console.log('SIGNUP HEADERS:', req.headers['content-type']);
    const result = await authService.signup(req.body);
    sendSuccess(res, 'Account created successfully', result, 201);
  } catch (err) {
    next(err);
  }
};

export const login = async (req: Request, res: Response, next: NextFunction) => {
  try {
    console.log('LOGIN BODY:', req.body);
    const result = await authService.login(req.body);
    sendSuccess(res, 'Login successful', result);
  } catch (err) {
    next(err);
  }
};

export const googleAuth = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { token, role } = req.body;
    if (!token) {
      return next(new AppError('Google token is required', 400));
    }

    const result = await authService.verifyGoogleToken(token, role);
    sendSuccess(res, 'Google Authentication successful', result);
  } catch (err) {
    next(err);
  }
};

export const firebaseAuthentication = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { token, role } = req.body;
    if (!token) {
      return next(new AppError('Firebase token is required', 400));
    }
    const result = await authService.firebaseAuthentication(token, role);
    sendSuccess(res, 'Firebase Authentication successful', result);
  } catch (err) {
    next(err);
  }
};

export const logout = async (req: Request, res: Response, next: NextFunction) => {
  try {
    await authService.logout(req.user!.userId);
    sendSuccess(res, 'Logged out successfully');
  } catch (err) {
    next(err);
  }
};

export const forgotPassword = async (req: Request, res: Response, next: NextFunction) => {
  try {
    await authService.forgotPassword(req.body.email);
    sendSuccess(res, 'If that email exists, a reset link has been sent');
  } catch (err) {
    next(err);
  }
};

export const resetPassword = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { token, password } = req.body;
    await authService.resetPassword(token, password);
    sendSuccess(res, 'Password reset successfully');
  } catch (err) {
    next(err);
  }
};