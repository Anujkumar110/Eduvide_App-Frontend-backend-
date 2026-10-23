import { Request, Response, NextFunction } from 'express';
import * as studentService from '../services/student.service';
import { sendSuccess, sendPaginated } from '../utils/response';

export const getProfile = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const profile = await studentService.getStudentProfile(req.user!.userId);
    sendSuccess(res, 'Profile fetched', profile);
  } catch (err) {
    next(err);
  }
};

export const updateProfile = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const files = req.files as Record<string, Express.Multer.File[]> | undefined;
    const profilePhoto = files?.profilePhoto?.[0];
    const profile = await studentService.updateStudentProfile(req.user!.userId, req.body, profilePhoto);
    sendSuccess(res, 'Profile updated', profile);
  } catch (err) {
    next(err);
  }
};

export const getEnrolledClasses = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { enrollments, pagination } = await studentService.getEnrolledClasses(req.user!.userId, req.query);
    sendPaginated(res, 'Enrolled classes fetched', enrollments, pagination);
  } catch (err) {
    next(err);
  }
};
