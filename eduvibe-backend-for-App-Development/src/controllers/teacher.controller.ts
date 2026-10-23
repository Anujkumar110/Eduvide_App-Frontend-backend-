import { Request, Response, NextFunction } from 'express';
import * as teacherService from '../services/teacher.service';
import { getUploadAuthParams } from '../services/imagekit.service';
import { sendSuccess, sendPaginated, sendError } from '../utils/response';

export const getTeacherProfile = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const profile = await teacherService.getTeacherProfile(req.user!.userId);
    if (!profile) {
      return sendError(res, 'Teacher profile not found', 404);
    }
    const data = profile.toObject();
    const availabilityArray = Object.entries(data.availability)
      .filter(([_, value]) => value.isAvailable)
      .map(([day, value]) => ({
        day,
        ...value
      }));
    sendSuccess(res, 'Teacher profile fetched', { ...data, availability: availabilityArray });
  } catch (err) {
    next(err);
  }
};

export const getClasses = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const classes = await teacherService.getClasses(req.user!.userId);
    sendSuccess(res, 'Classes fetched', classes);
  } catch (err) {
    next(err);
  }
};

export const listTeachers = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { teachers, pagination } = await teacherService.searchTeachers(req.query);
    sendPaginated(res, 'Teachers fetched', teachers, pagination);
  } catch (err) {
    next(err);
  }
};

export const getTeacher = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const profile = await teacherService.getTeacherPublicProfile(req.params.id as string);
    sendSuccess(res, 'Teacher profile fetched', profile);
  } catch (err) {
    next(err);
  }
};

export const updateProfile = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const files = req.files as { [fieldname: string]: Express.Multer.File[] } | undefined;
    const profilePhotoFile = files?.profilePhoto?.[0];
    const introVideoFile = files?.introVideo?.[0];

    if (req.body.availability && typeof req.body.availability === 'string') {
      try {
        req.body.availability = JSON.parse(req.body.availability);
      } catch (e) {
        req.body.availability = [];
      }
    }

    // :white_check_mark: FIX: agar availability aa bhi jaaye toh array hi rehne do, object nahi
    if (req.body.availability && !Array.isArray(req.body.availability)) {
      req.body.availability = [];
    }

    if (req.body.subjects && typeof req.body.subjects === 'string') {
      try {
        req.body.subjects = JSON.parse(req.body.subjects);
      } catch (e) {
        req.body.subjects = req.body.subjects
          .split(',')
          .map((s: string) => s.trim())
          .filter(Boolean);
      }
    }

    if (req.body.boards && typeof req.body.boards === 'string') {
      try {
        req.body.boards = JSON.parse(req.body.boards);
      } catch (e) {
        req.body.boards = req.body.boards
          .split(',')
          .map((s: string) => s.trim())
          .filter(Boolean);
      }
    }

    if (req.body.languages && typeof req.body.languages === 'string') {
      try {
        req.body.languages = JSON.parse(req.body.languages);
      } catch (e) {
        req.body.languages = req.body.languages
          .split(',')
          .map((s: string) => s.trim())
          .filter(Boolean);
      }
    }

    if (!req.body.degree) {
      const degreeFromQual =
        req.body?.qualifications?.[0]?.degree ||
        req.body?.['qualifications[0][degree]'];

      if (degreeFromQual && typeof degreeFromQual === 'string' && degreeFromQual.trim()) {
        req.body.degree = degreeFromQual.trim();
      }
    }

    const profile = await teacherService.updateTeacherProfile(
      req.user!.userId,
      req.body,
      profilePhotoFile,
      introVideoFile
    );

    if (!profile) {
      return sendError(res, 'Teacher profile not found', 404);
    }
    const data = profile.toObject();
    const availabilityArray = Object.entries(data.availability)
      .filter(([_, value]) => value.isAvailable)
      .map(([day, value]) => ({
        day,
        ...value
      }));

    sendSuccess(res, 'Profile updated', { ...data, availability: availabilityArray });
  } catch (err) {
    next(err);
  }
};

export const getUploadAuth = (_req: Request, res: Response, next: NextFunction) => {
  try {
    const authParams = getUploadAuthParams();
    sendSuccess(res, 'Auth params generated', authParams);
  } catch (err) {
    next(err);
  }
};

export const submitOnboarding = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const files = req.files as { [fieldname: string]: Express.Multer.File[] } | undefined;
    const idProofFile = files?.idProof?.[0];
    const profilePhotoFile = files?.profilePhoto?.[0];
    const profile = await teacherService.submitOnboarding(
      req.user!.userId,
      req.body,
      idProofFile,
      profilePhotoFile
    );
    sendSuccess(res, 'Onboarding submitted for review', profile);
  } catch (err) {
    next(err);
  }
};

export const getDashboard = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const stats = await teacherService.getTeacherDashboard(req.user!.userId);
    sendSuccess(res, 'Dashboard stats fetched', stats);
  } catch (err) {
    next(err);
  }
};

export const getStudents = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { enrollments, pagination } = await teacherService.getTeacherStudents(req.user!.userId, req.query);
    sendPaginated(res, 'Students fetched', enrollments, pagination);
  } catch (err) {
    next(err);
  }
};

export const getMySlots = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const result = await teacherService.getTeacherSlots(req.user!.userId, req.query);
    const slots = Array.isArray(result) ? result : (result as any)?.slots ?? (result as any)?.data ?? [];
    sendSuccess(res, 'Slots fetched', slots);
  } catch (err) {
    next(err);
  }
};