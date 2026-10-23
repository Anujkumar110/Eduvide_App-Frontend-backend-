import { Enrollment } from '../models/enrollment.model';
import { Class } from '../models/class.model';
import { StudentProfile } from '../models/studentProfile.model';
import { TeacherProfile } from '../models/teacherProfile.model';
import { AppError } from '../utils/errors';
import { notificationQueue, NOTIFICATION_JOB_TYPES, NOTIFICATION_EVENTS } from '../queues/notification.queue';

export const enrollInClass = async (userId: string, classId: string) => {
  const [studentProfile, cls] = await Promise.all([
    StudentProfile.findOne({ userId }),
    Class.findById(classId),
  ]);

  if (!studentProfile) throw new AppError('Student profile not found', 404);
  if (!cls) throw new AppError('Class not found', 404);

  if (!['upcoming', 'published'].includes(cls.status)) {
    throw new AppError('This class is not open for enrollment', 400);
  }

  if (cls.maxStudents && cls.enrollmentCount >= cls.maxStudents) {
    throw new AppError('Class is full', 400);
  }

  // ✅ FIXED: classId bhi check mein — same teacher ke alag courses allow honge
  const existing = await Enrollment.findOne({
    studentId: studentProfile._id,
    teacherId: cls.teacherId,
    classId,
  });
  if (existing) return existing;

  const enrollment = await Enrollment.create({
    studentId: studentProfile._id,
    classId,
    teacherId: cls.teacherId,
  });

  await Promise.all([
    Class.findByIdAndUpdate(classId, { $inc: { enrollmentCount: 1 } }),
    TeacherProfile.findByIdAndUpdate(cls.teacherId, { $inc: { totalStudents: 1 } }),
  ]);

  const teacherProfile = await TeacherProfile.findById(cls.teacherId);
  if (teacherProfile) {
    await notificationQueue.add(NOTIFICATION_JOB_TYPES.SEND_NOTIFICATION, {
      userId: teacherProfile.userId.toString(),
      type: NOTIFICATION_EVENTS.CLASS_JOINED,
      title: 'New Student Enrolled',
      message: `${studentProfile.fullName} has joined your class: ${cls.title}`,
      metadata: { classId, studentId: studentProfile._id.toString() },
    });
  }

  return enrollment;
};

// ✅ FIXED: classId optional — slot booking ke liye
export const enrollAfterSlotBooking = async (
  studentUserId: string,
  teacherProfileId: string,
  classId?: string | null
): Promise<void> => {
  try {
    const studentProfile = await StudentProfile.findOne({ userId: studentUserId });
    if (!studentProfile) return;

    // ✅ FIXED: classId bhi existing check mein
    const existing = await Enrollment.findOne({
      studentId: studentProfile._id,
      teacherId: teacherProfileId,
      classId: classId ?? null,
    });
    if (existing) {
      console.log(`[EnrollmentService] ℹ️ Already enrolled — skipping`);
      return;
    }

    await Enrollment.create({
      studentId: studentProfile._id,
      teacherId: teacherProfileId,
      ...(classId ? { classId } : {}),
    });

    await TeacherProfile.findByIdAndUpdate(teacherProfileId, { $inc: { totalStudents: 1 } });

    console.log(`[EnrollmentService] ✅ Enrolled student ${studentProfile.fullName} with teacher ${teacherProfileId}`);
  } catch (err) {
    console.error('[EnrollmentService] ⚠️ Auto-enroll failed:', err);
  }
};

export const unenrollFromClass = async (userId: string, enrollmentId: string) => {
  const studentProfile = await StudentProfile.findOne({ userId });
  if (!studentProfile) throw new AppError('Student profile not found', 404);

  const enrollment = await Enrollment.findOne({
    _id: enrollmentId,
    studentId: studentProfile._id,
  });
  if (!enrollment) throw new AppError('Enrollment not found', 404);
  if (enrollment.status === 'dropped') throw new AppError('Already unenrolled',400);

  enrollment.status = 'dropped';
  await enrollment.save();

  await Promise.all([
    enrollment.classId
      ? Class.findByIdAndUpdate(enrollment.classId, { $inc: { enrollmentCount: -1 } })
      : Promise.resolve(),
    TeacherProfile.findByIdAndUpdate(enrollment.teacherId, { $inc: { totalStudents: -1 } }),
  ]);
};