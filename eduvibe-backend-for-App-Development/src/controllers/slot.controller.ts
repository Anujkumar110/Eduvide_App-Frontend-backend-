import { Request, Response, NextFunction } from 'express';
import * as slotService from '../services/slot.service';
import { TeacherProfile } from '../models/teacherProfile.model';
import { Slot } from '../models/slot.model';
import { sendSuccess } from '../utils/response';
import { connectionManager } from '../ws/connectionManager';

// ─── TEACHER: TOGGLE LIVE ─────────────────────────────────────────────────────
/**
 * PUT /api/v1/teachers/live
 * Body: { isLive: true/false }
 */
export const toggleLiveStatus = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { isLive } = req.body;

    const teacher = await TeacherProfile.findOneAndUpdate(
      { userId: req.user!.userId },
      { $set: { isLive: isLive === true } },
      { new: true }
    );

    if (!teacher) {
      return res.status(404).json({ success: false, message: 'Teacher profile not found' });
    }

    // Broadcast to all connected students
    connectionManager.broadcast({
      type: teacher.isLive ? 'teacher:went-live' : 'teacher:stopped-live',
      payload: {
        teacherId: teacher.userId.toString(),
        teacherName: teacher.fullName,
        isLive: teacher.isLive,
      },
    });

    sendSuccess(
      res,
      teacher.isLive ? 'You are now LIVE' : 'You are now offline',
      { isLive: teacher.isLive }
    );
  } catch (err) {
    next(err);
  }
};

// ─── PUBLIC: GET LIVE TEACHERS ────────────────────────────────────────────────
/**
 * GET /api/v1/teachers/live
 * Returns only LIVE + verified teachers with their TODAY's slots count
 */
export const getLiveTeachers = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const liveTeachers = await TeacherProfile.find({
      isLive: true,
      'verification.status': 'verified',
    }).select('userId fullName profilePhoto bio subjects pricing experience rating isLive');

    // Attach available slot count for today
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const teachersWithSlots = await Promise.all(
      liveTeachers.map(async (t) => {
        const availableSlots = await Slot.countDocuments({
          teacherId: t._id,
          date: today,
          status: 'available',
        });
        return { ...t.toObject(), availableSlotsToday: availableSlots };
      })
    );

    sendSuccess(res, 'Live teachers fetched', teachersWithSlots);
  } catch (err) {
    next(err);
  }
};

// ─── TEACHER: GENERATE SLOTS ──────────────────────────────────────────────────
/**
 * POST /api/v1/teachers/slots/generate
 * Body: { date, startTime, endTime, durationMinutes }
 * NOTE: priceInPaise is AUTO-CALCULATED from teacher's pricing.hourlyRate
 *       Teacher does NOT need to enter price every time
 */
export const generateSlots = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { date, startTime, endTime, durationMinutes } = req.body;

    if (!date || !startTime || !endTime) {
      return res.status(400).json({
        success: false,
        message: 'date, startTime, endTime are required',
      });
    }

    // ── Auto-calculate price from teacher's profile hourlyRate ────────────────
    const teacherProfile = await TeacherProfile.findOne({ userId: req.user!.userId });
    if (!teacherProfile) {
      return res.status(404).json({ success: false, message: 'Teacher profile not found' });
    }

    const hourlyRateInRupees: number = (teacherProfile.pricing as any)?.hourlyRate ?? 0;
    const slotDuration: number = durationMinutes ?? 60;

    // Convert: hourlyRate (₹/hr) × duration (min) / 60 × 100 → paise
    const priceInPaise = Math.round((hourlyRateInRupees * slotDuration / 60) * 100);

    const slots = await slotService.generateSlotsForDate(
      req.user!.userId,
      date,
      startTime,
      endTime,
      slotDuration as 30 | 60,
      priceInPaise
    );

    sendSuccess(res, `${slots.length} slots generated successfully`, slots);
  } catch (err) {
    next(err);
  }
};

// ─── TEACHER: DELETE A SLOT ───────────────────────────────────────────────────
/**
 * DELETE /api/v1/slots/:id
 * Only teacher who owns the slot can delete it
 * Only 'available' slots can be deleted (booked slots cannot)
 */
export const deleteSlot = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const slotId = req.params.id;
    const userId = req.user!.userId;

    // Find teacher profile
    const teacherProfile = await TeacherProfile.findOne({ userId });
    if (!teacherProfile) {
      return res.status(404).json({ success: false, message: 'Teacher profile not found' });
    }

    // Find the slot
    const slot = await Slot.findById(slotId);
    if (!slot) {
      return res.status(404).json({ success: false, message: 'Slot not found' });
    }

    // Check ownership — slot.teacherId is TeacherProfile._id
    if (slot.teacherId.toString() !== teacherProfile._id.toString()) {
      return res.status(403).json({ success: false, message: 'You are not authorized to delete this slot' });
    }

    // Cannot delete booked slot — student ka paisa involved hai
    if (slot.status === 'booked') {
      return res.status(400).json({
        success: false,
        message: 'Cannot delete a booked slot. Ask student to cancel first.',
      });
    }

    await Slot.findByIdAndDelete(slotId);

    sendSuccess(res, 'Slot deleted successfully', { slotId });
  } catch (err) {
    next(err);
  }
};

// ─── PUBLIC: GET TEACHER SLOTS ────────────────────────────────────────────────
/**
 * GET /api/v1/teachers/:id/slots?date=2026-04-28
 * :id = teacher's userId (not _id)
 */
export const getTeacherSlots = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { date } = req.query;

    if (!date) {
      return res.status(400).json({ success: false, message: 'date query param required (YYYY-MM-DD)' });
    }

    const slots = await slotService.getTeacherSlots(
      req.params.id as string,
      date as string
    );

    sendSuccess(res, 'Slots fetched', slots);
  } catch (err) {
    next(err);
  }
};

// ─── STUDENT: BOOK SLOT ───────────────────────────────────────────────────────
/**
 * POST /api/v1/slots/:id/book
 * Wallet deduction happens inside service (atomic)
 * Slot is ONLY marked booked after successful wallet deduction
 */
export const bookSlot = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { studentName, studentEmail, studentPhone } = req.body;

    const { booking, slot } = await slotService.bookSlot(
      req.user!.userId,
      req.params.id as string,
      studentName,
      studentEmail,
      studentPhone
    );

    sendSuccess(res, 'Slot booked successfully', {
      booking: booking,
      slot: slot,
      walletDeductedInRs: slot.priceInPaise / 100,
      // newWalletBalanceInRs: slot?.balanceAfterBooking / 100,
    });
  } catch (err) {
    next(err);
  }
};

// ─── STUDENT: CANCEL BOOKING ──────────────────────────────────────────────────
/**
 * POST /api/v1/slots/:id/cancel
 * Full refund to student wallet
 */
export const cancelBooking = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { slot } = await slotService.cancelBooking(
      req.user!.userId,
      req.params.id as string
    );

    sendSuccess(res, 'Booking cancelled and refund processed', {
      slot: slot,
      refundedInRs: (slot.priceInPaise ?? 0) / 100,
    });
  } catch (err) {
    next(err);
  }
};

// ─── STUDENT: MY BOOKED SLOTS ─────────────────────────────────────────────────
/**
 * GET /api/v1/students/my-slots
 */
export const getStudentSlots = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const slots = await slotService.getStudentBookedSlots(req.user!.userId);
    sendSuccess(res, 'Your booked slots', slots);
  } catch (err) {
    next(err);
  }
};

// ─── TEACHER: MY SLOTS ────────────────────────────────────────────────────────
/**
 * GET /api/v1/teachers/my-slots?date=2026-04-28  (date optional)
 */
export const getTeacherSlotsList = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { date } = req.query;
    const slots = await slotService.getTeacherBookedSlots(
      req.user!.userId,
      date as string | undefined
    );
    sendSuccess(res, 'Your slots fetched', slots);
  } catch (err) {
    next(err);
  }
};

// ─── TEACHER: LINK SLOT TO SESSION ───────────────────────────────────────────
/**
 * POST /api/v1/slots/:id/link-session
 * Body: { classId }
 */
export const linkSession = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { classId } = req.body;
    if (!classId) {
      return res.status(400).json({ success: false, message: 'classId required' });
    }

    const slot = await slotService.linkSlotToSession(req.params.id as string, classId);
    sendSuccess(res, 'Slot linked to session', slot);
  } catch (err) {
    next(err);
  }
};