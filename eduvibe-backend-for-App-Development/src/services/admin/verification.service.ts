import { TeacherProfile } from '../../models/teacherProfile.model';
import { User } from '../../models/user.model';
import { AuditLog } from '../../models/auditLog.model';
import { AppError } from '../../utils/errors';
import { getPagination } from '../../utils/pagination';
import { Request } from 'express';
import {
  teacherApprovedEmail,
  teacherRejectedEmail,
  teacherReuploadEmail,
} from '../../utils/mailer';
import { getSignedUrl } from '../imagekit.service';
import { notificationQueue, NOTIFICATION_JOB_TYPES, NOTIFICATION_EVENTS } from '../../queues/notification.queue';

const logAction = async (
  adminId: string,
  action: string,
  targetId: string,
  targetType: 'teacher' | 'user' | 'class',
  metadata: Record<string, unknown> = {}
) => {
  await AuditLog.create({ adminId, action, targetId, targetType, metadata });
};

export const getDashboardStats = async () => {
  const [totalUsers, totalTeachers, totalStudents, pendingVerifications] = await Promise.all([
    User.countDocuments({ role: { $ne: 'admin' } }),
    TeacherProfile.countDocuments({ 'verification.status': 'verified' }),
    User.countDocuments({ role: 'student' }),
    TeacherProfile.countDocuments({ 'verification.status': 'pending' }),
  ]);

  return { totalUsers, totalTeachers, totalStudents, pendingVerifications };
};

export const listPendingVerifications = async (query: Request['query']) => {
  const { page, limit, skip } = getPagination(query);

  const filter: Record<string, unknown> = {};
  if (query.status) filter['verification.status'] = query.status;
  else filter['verification.status'] = 'pending';

  const [verifications, total] = await Promise.all([
    TeacherProfile.find(filter)
      .populate('userId', 'email createdAt')
      .skip(skip)
      .limit(limit)
      .sort('-updatedAt'),
    TeacherProfile.countDocuments(filter),
  ]);

  return { verifications, pagination: { total, page, limit } };
};

export const getVerificationById = async (teacherProfileId: string) => {
  const profile = await TeacherProfile.findById(teacherProfileId).populate(
    'userId',
    'email createdAt'
  );
  if (!profile) throw new AppError('Verification not found', 404);

  // Generate presigned URL for the ID proof  
  let idProofPresignedUrl: string | null = null;
  if (profile.verification.idProofPath) {
    idProofPresignedUrl = getSignedUrl(profile.verification.idProofPath, 3600);
  } else if (profile.verification.idProofUrl) {
    // fallback for older records
    idProofPresignedUrl = profile.verification.idProofUrl;
  }

  const profileObj = profile.toObject();

  return {
    ...profileObj,
    verification: {
      ...profileObj.verification,
      idProofUrl: idProofPresignedUrl,
      idProofPath: undefined // ensure raw path isn't exposed
    }
  };
};

export const approveTeacher = async (adminId: string, teacherProfileId: string) => {
  const profile = await TeacherProfile.findByIdAndUpdate(
    teacherProfileId,
    {
      'verification.status': 'verified',
      'verification.reviewedBy': adminId,
      'verification.reviewedAt': new Date(),
    },
    { new: true }
  ).populate('userId', 'email');
  if (!profile) throw new AppError('Teacher profile not found', 404);

  await logAction(adminId, 'approve_teacher', teacherProfileId, 'teacher');

  const user = profile.userId as any;
  if (user?.email) {
    await teacherApprovedEmail(user.email, profile.fullName);
  }

  await notificationQueue.add(NOTIFICATION_JOB_TYPES.SEND_NOTIFICATION, {
    userId: (profile.userId as any)._id.toString(),
    type: NOTIFICATION_EVENTS.VERIFICATION_STATUS,
    title: 'Verification Approved',
    message: 'Your teacher verification has been approved. You can now create classes.',
  });

  return profile;
};

export const rejectTeacher = async (adminId: string, teacherProfileId: string, reason: string) => {
  if (!reason?.trim()) throw new AppError('Rejection reason is required', 400);

  const profile = await TeacherProfile.findByIdAndUpdate(
    teacherProfileId,
    {
      'verification.status': 'rejected',
      'verification.rejectionReason': reason,
      'verification.reviewedBy': adminId,
      'verification.reviewedAt': new Date(),
    },
    { new: true }
  ).populate('userId', 'email');
  if (!profile) throw new AppError('Teacher profile not found', 404);

  await logAction(adminId, 'reject_teacher', teacherProfileId, 'teacher', { reason });

  const user = profile.userId as any;
  if (user?.email) {
    await teacherRejectedEmail(user.email, profile.fullName, reason);
  }

  await notificationQueue.add(NOTIFICATION_JOB_TYPES.SEND_NOTIFICATION, {
    userId: (profile.userId as any)._id.toString(),
    type: NOTIFICATION_EVENTS.VERIFICATION_STATUS,
    title: 'Verification Rejected',
    message: `Your teacher verification was rejected. Reason: ${reason}`,
    metadata: { reason }
  });

  return profile;
};

export const requestReupload = async (adminId: string, teacherProfileId: string, reason: string) => {
  if (!reason?.trim()) throw new AppError('Reason for re-upload request is required', 400);

  const profile = await TeacherProfile.findByIdAndUpdate(
    teacherProfileId,
    {
      'verification.status': 'reupload_requested',
      'verification.rejectionReason': reason,
      'verification.reviewedBy': adminId,
      'verification.reviewedAt': new Date(),
    },
    { new: true }
  ).populate('userId', 'email');
  if (!profile) throw new AppError('Teacher profile not found', 404);

  await logAction(adminId, 'request_reupload', teacherProfileId, 'teacher', { reason });

  const user = profile.userId as any;
  if (user?.email) {
    await teacherReuploadEmail(user.email, profile.fullName, reason);
  }

  await notificationQueue.add(NOTIFICATION_JOB_TYPES.SEND_NOTIFICATION, {
    userId: (profile.userId as any)._id.toString(),
    type: NOTIFICATION_EVENTS.VERIFICATION_STATUS,
    title: 'Re-upload Requested',
    message: `We need you to re-upload some documents. Reason: ${reason}`,
    metadata: { reason }
  });

  return profile;
};
