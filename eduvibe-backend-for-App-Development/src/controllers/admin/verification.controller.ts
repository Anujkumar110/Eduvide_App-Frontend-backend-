import { Request, Response, NextFunction } from 'express';
import * as verificationService from '../../services/admin/verification.service';
import * as userService from '../../services/user.service';
import { sendSuccess, sendPaginated } from '../../utils/response';

export const getDashboard = async (_req: Request, res: Response, next: NextFunction) => {
  try {
    const stats = await verificationService.getDashboardStats();
    sendSuccess(res, 'Dashboard stats', stats);
  } catch (err) {
    next(err);
  }
};

export const listUsers = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { users, pagination } = await userService.listUsers(req.query);
    sendPaginated(res, 'Users fetched', users, pagination);
  } catch (err) {
    next(err);
  }
};

export const listVerifications = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { verifications, pagination } = await verificationService.listPendingVerifications(req.query);
    sendPaginated(res, 'Verifications fetched', verifications, pagination);
  } catch (err) {
    next(err);
  }
};

export const getVerification = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const result = await verificationService.getVerificationById(req.params.id as string);
    sendSuccess(res, 'Verification details fetched', result);
  } catch (err) {
    next(err);
  }
};

export const approveTeacher = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const profile = await verificationService.approveTeacher(req.user!.userId, req.params.id as string);
    sendSuccess(res, 'Teacher approved', profile);
  } catch (err) {
    next(err);
  }
};

export const rejectTeacher = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { reason } = req.body;
    const profile = await verificationService.rejectTeacher(req.user!.userId, req.params.id as string, reason);
    sendSuccess(res, 'Teacher rejected', profile);
  } catch (err) {
    next(err);
  }
};

export const requestReupload = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { reason } = req.body;
    const profile = await verificationService.requestReupload(req.user!.userId, req.params.id as string, reason);
    sendSuccess(res, 'Re-upload requested', profile);
  } catch (err) {
    next(err);
  }
};
