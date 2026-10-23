import { TeacherProfile } from '../models/teacherProfile.model';
import { Enrollment } from '../models/enrollment.model';
import { Class } from '../models/class.model';
import { Slot } from '../models/slot.model';
import { AppError } from '../utils/errors';
import { getPagination } from '../utils/pagination';
import { Request } from 'express';
import { uploadToImageKit, deleteFromImageKit } from './imagekit.service';
import { logger } from '../utils/logger';
import { uploadQueue, UPLOAD_JOB_TYPES } from '../queues/upload.queue';

export interface UpdateTeacherProfileInput {
  fullName?: string;
  email?: string;
  gender?: string;
  age?: number;
  bio?: string;
  degree?: string;
  subjects?: string[];
  boards?: string[];
  languages?: string[];
  experience?: number;
  pricing?: any;
  availability?: any[];
}

export interface OnboardingInput {
  fullName?: string;
  bio?: string;
  subjects?: string[];
  experience?: number;
  pricing?: any;
  qualifications?: any[];
}

export const getTeacherProfile = async (userId: string) => {
  const profile = await TeacherProfile.findOne({ userId });
  if (!profile) throw new AppError('Teacher profile not found', 404);
  return profile;
};

export const getTeacherPublicProfile = async (teacherProfileId: string) => {
  const profile = await TeacherProfile.findById(teacherProfileId)
    .select('-verification.idProofUrl -totalEarnings');
  if (!profile) throw new AppError('Teacher not found', 404);
  return profile;
};

export const getClasses = async (userId: string) => {
  const teacher = await TeacherProfile.findOne({ userId });
  if (!teacher) throw new AppError('Teacher profile not found', 404);
  const classes = await Class.find({ teacherId: teacher._id });
  return classes;
};

export const searchTeachers = async (query: Request['query']) => {
  const { page, limit, skip, sort } = getPagination(query);

  const filter: Record<string, unknown> = {
    'verification.status': 'verified',
  };

  if (query.subject) filter.subjects = query.subject;
  if (query.search) {
    filter.$text = { $search: query.search as string };
  }
  if (query.minRate || query.maxRate) {
    filter['pricing.hourlyRate'] = {};
    if (query.minRate) (filter['pricing.hourlyRate'] as any).$gte = parseFloat(query.minRate as string);
    if (query.maxRate) (filter['pricing.hourlyRate'] as any).$lte = parseFloat(query.maxRate as string);
  }

  const [teachers, total] = await Promise.all([
    TeacherProfile.find(filter)
      .sort(sort)
      .skip(skip)
      .limit(limit)
      .lean(),
    TeacherProfile.countDocuments(filter),
  ]);

  return { teachers, pagination: { total, page, limit } };
};

export const updateTeacherProfile = async (
  userId: string,
  data: UpdateTeacherProfileInput,
  file?: Express.Multer.File,
  introVideo?: Express.Multer.File,
) => {
  const profile = await TeacherProfile.findOne({ userId });
  if (!profile) throw new AppError('Teacher profile not found', 404);

  if (file) {
    await uploadQueue.add(UPLOAD_JOB_TYPES.PROFILE_PHOTO, {
      userId,
      role: 'teacher',
      filePath: file.path,
      originalName: file.originalname,
      mimeType: file.mimetype,
    });
  }

  if (introVideo) {
    await uploadQueue.add(UPLOAD_JOB_TYPES.INTRO_VIDEO, {
      userId,
      role: 'teacher',
      filePath: introVideo.path,
      originalName: introVideo.originalname,
      mimeType: introVideo.mimetype,
    });
  }

  const updateData: Record<string, any> = {};

  if (data.fullName   !== undefined) updateData.fullName   = data.fullName;
  if (data.email      !== undefined) updateData.email      = data.email;
  if (data.gender     !== undefined) updateData.gender     = data.gender?.toLowerCase();
  if (data.age        !== undefined) updateData.age        = Number(data.age);
  if (data.bio        !== undefined) updateData.bio        = data.bio;
  if (data.experience !== undefined) updateData.experience = Number(data.experience);
  if (data.pricing    !== undefined) updateData.pricing    = data.pricing;

  if (data.degree !== undefined && data.degree.trim() !== '') {
    updateData['qualifications'] = [
      {
        degree:         data.degree.trim(),
        institution:    profile.qualifications?.[0]?.institution ?? '',
        year:           profile.qualifications?.[0]?.year        ?? 0,
        certificateUrl: profile.qualifications?.[0]?.certificateUrl ?? '',
      }
    ];
  }

  if (data.subjects !== undefined) {
    updateData.subjects = (Array.isArray(data.subjects) ? data.subjects : [data.subjects])
      .map((s: any) => String(s).trim())
      .filter((s) => s.length > 0);
  }

  if (data.boards !== undefined) {
    updateData.boards = (Array.isArray(data.boards) ? data.boards : [data.boards])
      .map((s: any) => String(s).trim())
      .filter((s) => s.length > 0);
  }

  if (data.languages !== undefined) {
    updateData.languages = (Array.isArray(data.languages) ? data.languages : [data.languages])
      .map((s: any) => String(s).trim())
      .filter((s) => s.length > 0);
  }

  logger.info(`Updating teacher profile for userId=${userId}`);
  logger.info(`subjects=${JSON.stringify(updateData.subjects)}`);
  logger.info(`boards=${JSON.stringify(updateData.boards)}`);
  logger.info(`languages=${JSON.stringify(updateData.languages)}`);
  logger.info(`degree=${data.degree}`);

  const updatedProfile = await TeacherProfile.findOneAndUpdate(
    { userId },
    { $set: updateData },
    { returnDocument: 'after', runValidators: false }
  );
  return updatedProfile;
};

export const submitOnboarding = async (
  userId: string,
  data: OnboardingInput,
  idProofFile?: Express.Multer.File,
  profilePhotoFile?: Express.Multer.File
) => {
  const update: Record<string, unknown> = {
    fullName: data.fullName,
    bio: data.bio,
    subjects: (data.subjects || [])
      .map((s: any) => String(s).trim())
      .filter((s) => s.length > 0),
    experience: data.experience,
    pricing: data.pricing,
    qualifications: (data.qualifications || []).map((q) => ({ ...q, certificateUrl: '' })),
    'verification.status': 'pending',
  };

  if (idProofFile) {
    await uploadQueue.add(UPLOAD_JOB_TYPES.DOCUMENT, {
      userId,
      role: 'teacher',
      filePath: idProofFile.path,
      originalName: idProofFile.originalname,
      mimeType: idProofFile.mimetype,
      documentType: 'idProof',
    });
  }

  if (profilePhotoFile) {
    await uploadQueue.add(UPLOAD_JOB_TYPES.PROFILE_PHOTO, {
      userId,
      role: 'teacher',
      filePath: profilePhotoFile.path,
      originalName: profilePhotoFile.originalname,
      mimeType: profilePhotoFile.mimetype,
    });
  }

  const profile = await TeacherProfile.findOneAndUpdate(
    { userId },
    { $set: update },
    { returnDocument: 'after', runValidators: false }
  );
  if (!profile) throw new AppError('Teacher profile not found', 404);
  return profile;
};

export const getTeacherDashboard = async (userId: string) => {
  const profile = await TeacherProfile.findOne({ userId });
  if (!profile) throw new AppError('Teacher profile not found', 404);

  const enrolledStudents = await Enrollment.countDocuments({ teacherId: profile._id, status: 'active' });

  return {
    totalStudents: profile.totalStudents,
    totalEarnings: profile.totalEarnings,
    rating: profile.rating,
    activeStudents: enrolledStudents,
    verificationStatus: profile.verification.status,
  };
};

export const getTeacherStudents = async (userId: string, query: Request['query']) => {
  const profile = await TeacherProfile.findOne({ userId });
  if (!profile) throw new AppError('Teacher profile not found', 404);

  const { page, limit, skip } = getPagination(query);

  const [enrollments, total] = await Promise.all([
    Enrollment.find({ teacherId: profile._id, status: 'active' })
      .populate({ path: 'studentId', populate: { path: 'userId', select: 'email' } })
      .populate('classId', 'title')
      .skip(skip)
      .limit(limit),
    Enrollment.countDocuments({ teacherId: profile._id, status: 'active' }),
  ]);

  return { enrollments, pagination: { total, page, limit } };
};

export const getTeacherSlots = async (userId: string, query: Request['query']) => {
  const profile = await TeacherProfile.findOne({ userId });
  if (!profile) throw new AppError('Teacher profile not found', 404);

  const filter: Record<string, unknown> = { teacherId: profile._id };

  if (query.status) filter.status = query.status;

  if (query.date) {
    const start = new Date(query.date as string);
    start.setHours(0, 0, 0, 0);
    const end = new Date(query.date as string);
    end.setHours(23, 59, 59, 999);
    filter.date = { $gte: start, $lte: end };
  }

  // ✅ FIX: pagination hata di — saare slots aayenge
  const slots = await Slot.find(filter)
    .sort({ date: 1, startTime: 1 })
    .lean();

  return { slots, pagination: { total: slots.length, page: 1, limit: slots.length } };
};