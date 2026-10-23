import { Booking } from '../models/booking.model';
import { Session } from '../models/session.model';
import { Class } from '../models/class.model';
import { TeacherProfile } from '../models/teacherProfile.model';
import { StudentProfile } from '../models/studentProfile.model';
import { AppError } from '../utils/errors';
import { getPagination } from '../utils/pagination';
import { Request } from 'express';
import { transferFromStudentToTeacher } from './business/wallet.service';
import { sendNotification } from './notification.service';
import {
  notifyPaymentInitiated,
  notifyPaymentSuccess,
} from './business/payment.notifications';

export interface CreateBookingInput {
  teacherId: string;
  classId: string;
  startTime: string;
  endTime: string;
  amount: number;
  currency: string;
}

/**
 * Create a booking, deduct from student wallet, credit teacher wallet, send FCM
 */
export const createBooking = async (studentUserId: string, input: CreateBookingInput) => {
  // Verify student exists
  const studentProfile = await StudentProfile.findOne({ userId: studentUserId });
  if (!studentProfile) throw new AppError('Student profile not found', 404);

  // Verify teacher is approved
  const teacherProfile = await TeacherProfile.findById(input.teacherId);
  if (!teacherProfile) throw new AppError('Teacher not found', 404);
  if (teacherProfile.verification.status !== 'verified') {
    throw new AppError('Teacher is not approved yet', 400);
  }

  // Verify class exists and belongs to teacher
  const cls = await Class.findOne({ _id: input.classId, teacherId: teacherProfile._id });
  if (!cls) throw new AppError('Class not found', 404);

  const startTime = new Date(input.startTime);
  const endTime = new Date(input.endTime);

  // Check if session already exists for this time slot
  const existingSession = await Session.findOne({
    classId: cls._id,
    startTime: { $lte: endTime },
    endTime: { $gte: startTime },
  });

  // ✅ BUG FIX: Pehle auto-create karta tha — ab error throw karta hai
  // Session sirf teacher create karta hai, student sirf book karta hai
  if (!existingSession) {
    throw new AppError('No session found for this time slot', 404);
  }

  const session = existingSession;

  // Check if student already booked this session
  const existingBooking = await Booking.findOne({
    sessionId: session._id,
    studentId: studentProfile._id,
  });
  if (existingBooking) throw new AppError('Already booked this session', 409);

  // Check if session is already booked by someone else (1:1 sessions)
  const sessionBookings = await Booking.countDocuments({
    sessionId: session._id,
    status: { $in: ['confirmed', 'completed'] },
  });
  if (sessionBookings > 0) throw new AppError('Session already booked', 409);

  // ── WALLET TRANSFER ──────────────────────────────────────────────────────────
  let studentNewBalance = 0;
  let teacherNewBalance = 0;
  const amount = input.amount || 0;

  if (amount > 0) {
    // ✅ Payment init notification — student ko batao payment shuru ho rahi hai
    try {
      await notifyPaymentInitiated(studentUserId, amount, teacherProfile.fullName);
    } catch (e) {
      console.error('[FCM] Payment init notification failed:', e);
    }

    const transfer = await transferFromStudentToTeacher(
      studentUserId,
      teacherProfile.userId.toString(),
      amount,
      session._id.toString()
    );
    studentNewBalance = transfer.studentNewBalance;
    teacherNewBalance = transfer.teacherNewBalance;
  }

  // Create booking
  const booking = await Booking.create({
    studentId: studentProfile._id,
    teacherId: teacherProfile._id,
    amountInPaise: amount,
    status: 'upcoming',
  });

  // ── FCM NOTIFICATIONS ────────────────────────────────────────────────────────

  // ✅ Student ko — payment success
  if (amount > 0) {
    try {
      await notifyPaymentSuccess(
        studentUserId,
        amount,
        teacherProfile.fullName,
        studentNewBalance
      );
    } catch (e) {
      console.error('[FCM] Student payment success notification failed:', e);
    }
  }

  // Teacher ko — payment received
  try {
    await sendNotification(
      teacherProfile.userId.toString(),
      'Payment Received',
      `₹${amount} received from ${studentProfile.fullName} for ${cls.title}`,
      {
        type: 'WALLET_PAYMENT_CREDITED',
        amount: String(amount),
        studentName: studentProfile.fullName,
        className: cls.title,
        newBalance: String(teacherNewBalance),
        bookingId: booking._id.toString(),
      }
    );
  } catch (e) {
    console.error('[FCM] Teacher notification failed:', e);
  }

  return { booking, session };
};

/**
 * Get student's bookings
 */
export const getStudentBookings = async (studentId: string, query: Request['query']) => {
  const { page, limit, skip } = getPagination(query);

  const studentProfile = await StudentProfile.findOne({ userId: studentId });
  if (!studentProfile) throw new AppError('Student profile not found', 404);

  const filter: Record<string, unknown> = { studentId: studentProfile._id };
  if (query.status) filter.status = query.status;

  const [bookings, total] = await Promise.all([
    Booking.find(filter)
      .populate({ path: 'sessionId', select: 'startTime endTime status' })
      .populate({ path: 'classId', select: 'title description' })
      .populate({ path: 'teacherId', select: 'fullName profilePhoto' })
      .sort({ startTime: 1 })
      .skip(skip)
      .limit(limit),
    Booking.countDocuments(filter),
  ]);

  return { bookings, pagination: { total, page, limit } };
};

/**
 * Get teacher's bookings
 */
export const getTeacherBookings = async (teacherId: string, query: Request['query']) => {
  const { page, limit, skip } = getPagination(query);

  const teacherProfile = await TeacherProfile.findOne({ userId: teacherId });
  if (!teacherProfile) throw new AppError('Teacher profile not found', 404);

  const filter: Record<string, unknown> = { teacherId: teacherProfile._id };
  if (query.status) filter.status = query.status;

  const [bookings, total] = await Promise.all([
    Booking.find(filter)
      .populate({ path: 'sessionId', select: 'startTime endTime status' })
      .populate({ path: 'classId', select: 'title description' })
      .populate({ path: 'studentId', select: 'userId fullName profilePhoto' })
      .sort({ startTime: 1 })
      .skip(skip)
      .limit(limit),
    Booking.countDocuments(filter),
  ]);

  return { bookings, pagination: { total, page, limit } };
};

/**
 * Get available slots for a teacher
 */
export const getTeacherAvailability = async (teacherId: string, date: string) => {
  const teacherProfile = await TeacherProfile.findOne({ userId: teacherId });
  if (!teacherProfile) throw new AppError('Teacher not found', 404);

  if (teacherProfile.verification.status !== 'verified') {
    throw new AppError('Teacher is not approved yet', 400);
  }

  const requestedDate = new Date(date);
  const dayOfWeek = requestedDate.getDay();

  const dayAvailability = teacherProfile.availability ? [
    dayOfWeek
  ] : [];

  if (dayAvailability.length === 0) {
    return { slots: [], message: 'No availability set for this day' };
  }

  const slots = dayAvailability.map((avail: any) => {
    const [startHour, startMin] = avail.startTime.split(':').map(Number);
    const [endHour, endMin] = avail.endTime.split(':').map(Number);

    const slotStart = new Date(requestedDate);
    slotStart.setHours(startHour, startMin, 0, 0);

    const slotEnd = new Date(requestedDate);
    slotEnd.setHours(endHour, endMin, 0, 0);

    return {
      startTime: slotStart,
      endTime: slotEnd,
      price: teacherProfile.pricing?.hourlyRate || 0,
    };
  });

  return { slots };
};