import { Request, Response, NextFunction } from 'express';
import * as classService from '../services/class.service';
import { sendSuccess, sendPaginated } from '../utils/response';
import { roomManager } from '../ws/roomManager';
import { AppError } from '../utils/errors';
import { TeacherProfile } from '../models/teacherProfile.model';
import { StudentProfile } from '../models/studentProfile.model';

export const createClass = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const cls = await classService.createClass(req.user!.userId, req.body);
    sendSuccess(res, 'Class created', cls, 201);
  } catch (err) {
    next(err);
  }
};

export const listClasses = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const query = { ...req.query } as Record<string, any>;

    // ✅ Teacher: ?mine=true — fetch only their own classes
    if (query.mine === 'true') {
      if (!req.user) return next(new AppError('Authentication required', 401));
      const teacherProfile = await TeacherProfile.findOne({ userId: req.user.userId });
      if (!teacherProfile) return next(new AppError('Teacher profile not found', 404));
      query.teacherId = teacherProfile._id.toString();
    }

    // ✅ Student: ?enrolled=true — fetch only enrolled classes
    if (query.enrolled === 'true') {
      if (!req.user) return next(new AppError('Authentication required', 401));
      const studentProfile = await StudentProfile.findOne({ userId: req.user.userId });
      if (!studentProfile) return next(new AppError('Student profile not found', 404));
      query.studentId = studentProfile._id.toString();
    }

    const { classes, pagination } = await classService.listClasses(query);
    sendPaginated(res, 'Classes fetched', classes, pagination);
  } catch (err) {
    next(err);
  }
};

export const getClass = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const cls = await classService.getClassById(req.params.id as string);
    sendSuccess(res, 'Class fetched', cls);
  } catch (err) {
    next(err);
  }
};

export const updateClass = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const cls = await classService.updateClass(req.user!.userId, req.params.id as string, req.body);
    sendSuccess(res, 'Class updated', cls);
  } catch (err) {
    next(err);
  }
};

export const deleteClass = async (req: Request, res: Response, next: NextFunction) => {
  try {
    await classService.deleteClass(req.user!.userId, req.params.id as string);
    sendSuccess(res, 'Class deleted');
  } catch (err) {
    next(err);
  }
};

export const uploadVideo = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const cls = await classService.uploadVideo(req.user!.userId, req.params.id as string, req.file);
    sendSuccess(res, 'Video upload queued', cls);
  } catch (err) {
    next(err);
  }
};

export const getClassStudents = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { enrollments, pagination } = await classService.getClassStudents(
      req.user!.userId,
      req.params.id as string,
      req.query
    );
    sendPaginated(res, 'Students fetched', enrollments, pagination);
  } catch (err) {
    next(err);
  }
};

export const getClassPeers = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const peers = roomManager.getConnectedPeers(req.params.id as string);
    sendSuccess(res, 'Peers fetched', { peers });
  } catch (err) {
    next(err);
  }
};