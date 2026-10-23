import { Request, Response, NextFunction } from 'express';
import * as bookingService from '../services/booking.service';
import { sendSuccess, sendPaginated } from '../utils/response';

/**
 * POST /api/v1/bookings
 * Create a new booking (auto-creates session)
 */
export const createBooking = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { teacherId, classId, startTime, endTime, amount, currency } = req.body;

    const result = await bookingService.createBooking(req.user!.userId, {
      teacherId,
      classId,
      startTime,
      endTime,
      amount: amount || 0,
      currency: currency || 'INR',
    });

    sendSuccess(res, 'Booking created successfully', result, 201);
  } catch (err) {
    next(err);
  }
};

/**
 * GET /api/v1/students/my-bookings
 * Get student's bookings
 */
export const getStudentBookings = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { bookings, pagination } = await bookingService.getStudentBookings(
      req.user!.userId,
      req.query
    );
    sendPaginated(res, 'Student bookings fetched', bookings, pagination);
  } catch (err) {
    next(err);
  }
};

/**
 * GET /api/v1/teachers/my-bookings
 * Get teacher's bookings
 */
export const getTeacherBookings = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { bookings, pagination } = await bookingService.getTeacherBookings(
      req.user!.userId,
      req.query
    );
    sendPaginated(res, 'Teacher bookings fetched', bookings, pagination);
  } catch (err) {
    next(err);
  }
};

/**
 * GET /api/v1/teachers/:id/availability
 * Get teacher's available slots for a specific date
 */
export const getTeacherAvailability = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { date } = req.query;
    if (!date) {
      return res.status(400).json({ success: false, message: 'Date query parameter is required' });
    }

    const { slots, message } = await bookingService.getTeacherAvailability(
      req.params.id as string,
      date as string
    );

    sendSuccess(res, message || 'Availability fetched', { slots });
  } catch (err) {
    next(err);
  }
};
