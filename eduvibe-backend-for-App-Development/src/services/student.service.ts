import { StudentProfile } from '../models/studentProfile.model';
import { Enrollment } from '../models/enrollment.model';
import { AppError } from '../utils/errors';
import { getPagination } from '../utils/pagination';
import { Request } from 'express';
import { uploadQueue, UPLOAD_JOB_TYPES } from '../queues/upload.queue';

export interface UpdateStudentProfileInput {
  fullName?: string;
  gradeLevel?: string;
  subjects?: any[];
  goals?: any[];
}

export const getStudentProfile = async (userId: string) => {
  const profile = await StudentProfile.findOne({ userId });
  if (!profile) throw new AppError('Student profile not found', 404);
  return profile;
};

export const updateStudentProfile = async (userId: string, data: UpdateStudentProfileInput, file?: Express.Multer.File) => {
  const profile = await StudentProfile.findOne({ userId });
  if (!profile) throw new AppError('Student profile not found', 404);

  if (data.subjects && Array.isArray(data.subjects)) {
    data.subjects = data.subjects
      .filter(Boolean)
      .map((s: any) => {
        if (typeof s === 'string') {
          return { title: s, isCompleted: false, progress: 0 };
        }
        if (typeof s === 'object' && s.title) {
          return {
            title: s.title,
            isCompleted: s.isCompleted ?? false,
            progress: s.progress ?? 0,
          };
        }
        return null;
      })
      .filter(Boolean);
  }

  if (data.goals && Array.isArray(data.goals)) {
    data.goals = data.goals
      .filter(Boolean)
      .map((g: any) => {
        if (typeof g === 'string') {
          return { title: g, isCompleted: false, progress: 0 };
        }
        if (typeof g === 'object' && g.title) {
          return {
            title: g.title,
            targetDate: g.targetDate ?? undefined,
            isCompleted: g.isCompleted ?? false,
            progress: g.progress ?? 0,
          };
        }
        return null;
      })
      .filter((g: any) => g && g.title);
  }

  if (file) {
    await uploadQueue.add(UPLOAD_JOB_TYPES.PROFILE_PHOTO, {
      userId,
      role: 'student',
      filePath: file.path,
      originalName: file.originalname,
      mimeType: file.mimetype,
    });
  }

  const updatedProfile = await StudentProfile.findOneAndUpdate(
    { userId },
    { $set: data },
    { new: true, runValidators: true }
  );

  return updatedProfile;
};

export const getEnrolledClasses = async (userId: string, query: Request['query']) => {
  const profile = await StudentProfile.findOne({ userId });
  if (!profile) throw new AppError('Student profile not found', 404);

  const { page, limit, skip } = getPagination(query);

  const [enrollments, total] = await Promise.all([
    Enrollment.find({ studentId: profile._id })
      .populate({ path: 'classId', populate: { path: 'teacherId', select: 'fullName' } })
      .sort('-enrolledAt')
      .skip(skip)
      .limit(limit),
    Enrollment.countDocuments({ studentId: profile._id }),
  ]);

  return { enrollments, pagination: { total, page, limit } };
};