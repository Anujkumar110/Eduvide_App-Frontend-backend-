import { Request, Response, NextFunction } from 'express';
import * as enrollmentService from '../services/enrollment.service';
import { sendSuccess } from '../utils/response';

export const enroll = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const enrollment = await enrollmentService.enrollInClass(req.user!.userId, req.body.classId);
    sendSuccess(res, 'Enrolled successfully', enrollment, 201);
  } catch (err) {
    next(err);
  }
};

export const unenroll = async (req: Request, res: Response, next: NextFunction) => {
  try {
    await enrollmentService.unenrollFromClass(req.user!.userId, req.params.id as string);
    sendSuccess(res, 'Unenrolled successfully');
  } catch (err) {
    next(err);
  }
};