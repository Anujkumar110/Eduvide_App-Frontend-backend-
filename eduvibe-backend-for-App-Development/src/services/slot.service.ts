import { Slot, Booking, WalletTransaction } from '../models/slot.model';
import { TeacherProfile } from '../models/teacherProfile.model';
import { StudentProfile } from '../models/studentProfile.model';
import { AppError } from '../utils/errors';
import { connectionManager } from '../ws/connectionManager';
import { enrollAfterSlotBooking } from './enrollment.service'; // ✅

// ─── HELPERS ──────────────────────────────────────────────────────────────────

function timeToMinutes(t: string): number {
  const [h, m] = t.split(':').map(Number);
  return h * 60 + m;
}

function minutesToTime(minutes: number): string {
  const h = Math.floor(minutes / 60).toString().padStart(2, '0');
  const m = (minutes % 60).toString().padStart(2, '0');
  return `${h}:${m}`;
}

// ─── IST PASSED CHECK ─────────────────────────────────────────────────────────
function isSlotPassed(slotDate: Date, startTime: string): boolean {
  const [h, m] = startTime.split(':').map(Number);

  const IST_OFFSET_MS = 5.5 * 60 * 60 * 1000;
  const slotMidnightIST = new Date(slotDate.getTime() + IST_OFFSET_MS);

  slotMidnightIST.setHours(h, m, 0, 0);

  return slotMidnightIST < new Date();
}

// ─── 1. GENERATE SLOTS ────────────────────────────────────────────────────────
export const generateSlotsForDate = async (
  teacherUserId: string,
  dateStr: string,
  startTime: string,
  endTime: string,
  durationMinutes: 30 | 60 = 60,
  priceInPaise: number = 0
) => {
  const teacher = await TeacherProfile.findOne({ userId: teacherUserId });
  if (!teacher) throw new AppError('Teacher not found', 404);

  if (teacher.verification?.status !== 'verified') {
    throw new AppError('Only verified teachers can create slots', 403);
  }

  const date = new Date(dateStr + 'T00:00:00.000Z');

  const existing = await Slot.findOne({ teacherId: teacher._id, date, startTime });
  if (existing) throw new AppError('Slots already exist for this date and time', 409);

  const slots: any[] = [];
  let current = timeToMinutes(startTime);
  const end = timeToMinutes(endTime);

  while (current + durationMinutes <= end) {
    const slotStartTime = minutesToTime(current);

    if (!isSlotPassed(date, slotStartTime)) {
      slots.push({
        teacherId: teacher._id,
        date,
        startTime: slotStartTime,
        endTime: minutesToTime(current + durationMinutes),
        durationMinutes,
        priceInPaise,
        status: 'available',
      });
    }

    current += durationMinutes;
  }

  if (slots.length === 0) {
    throw new AppError('Selected time has already passed. Please choose a future time.', 400);
  }

  const created = await Slot.insertMany(slots, { ordered: false }).catch((err: any) => {
    if (err.code === 11000) throw new AppError('Some slots already exist for this time', 409);
    throw err;
  });

  console.log(`[SlotService] ✅ Generated ${created.length} slots for ${teacher.fullName} on ${dateStr}`);
  return created;
};

// ─── 2. GET TEACHER SLOTS (for students browsing) ─────────────────────────────
export const getTeacherSlots = async (teacherUserId: string, dateStr: string) => {
  const teacher = await TeacherProfile.findOne({ userId: teacherUserId });
  if (!teacher) throw new AppError('Teacher not found', 404);

  if (teacher.verification?.status !== 'verified') {
    throw new AppError('Teacher is not approved', 403);
  }

  const date = new Date(dateStr + 'T00:00:00.000Z');

  const slots = await Slot.find({
    teacherId: teacher._id,
    date,
    status: { $in: ['available', 'booked'] },
  }).sort({ startTime: 1 }).lean();

  return slots.map((slot) => {
    const isPassed = isSlotPassed(slot.date, slot.startTime);
    return {
      ...slot,
      isPassed,
      slotStatus: isPassed ? 'passed' : slot.status,
    };
  });
};

// ─── 3. BOOK SLOT ─────────────────────────────────────────────────────────────
export const bookSlot = async (
  studentUserId: string,
  slotId: string,
  studentName?: string,
  studentEmail?: string,
  studentPhone?: string
) => {
  const student = await StudentProfile.findOne({ userId: studentUserId });
  if (!student) throw new AppError('Student not found', 404);

  const existingSlot = await Slot.findById(slotId);
  if (!existingSlot) throw new AppError('Slot not found', 404);

  if (
    existingSlot.status === 'booked' &&
    existingSlot.bookedBy?.toString() === student._id.toString()
  ) {
    const existingBooking = existingSlot.bookingId
      ? await Booking.findById(existingSlot.bookingId)
      : null;
    console.log(`[SlotService] ℹ️ Slot already booked by same student — returning existing booking`);
    return { booking: existingBooking, slot: existingSlot };
  }

  const slot = await Slot.findOneAndUpdate(
    { _id: slotId, status: 'available' },
    {
      $set: {
        status: 'booked',
        bookedBy: student._id,        // StudentProfile._id stored here
        bookedByUserId: studentUserId, // ✅ FIX: student ka User._id bhi store karo
        studentName: studentName || student.fullName || '',
        studentEmail: studentEmail || '',
        studentPhone: studentPhone || '',
      },
    },
    { new: true }
  ).populate('teacherId', '_id userId');

  if (!slot) throw new AppError('Slot is not available or already booked', 400);

  const teacher = slot.teacherId as any;

  const booking = await Booking.create({
    slotId: slot._id,
    studentId: student._id,
    teacherId: teacher._id,
    amountInPaise: slot.priceInPaise ?? 0,
    status: 'upcoming',
  });

  await Slot.findByIdAndUpdate(slot._id, { bookingId: booking._id });

  const teacherUserId = teacher.userId?.toString();
  if (teacherUserId) {
    connectionManager.send(teacherUserId, {
      type: 'slot:booked',
      payload: {
        slotId: slot._id,
        date: slot.date,
        startTime: slot.startTime,
        endTime: slot.endTime,
        studentId: student._id,
        studentName: studentName || student.fullName,
        studentEmail: studentEmail || '',
        studentPhone: studentPhone || '',
        amount: (slot.priceInPaise ?? 0) / 100,
      },
    });
  }

  console.log(`[SlotService] ✅ Slot booked: ${slot.startTime}–${slot.endTime} by ${studentName || student.fullName}`);

  await enrollAfterSlotBooking(
    studentUserId,
    teacher._id.toString(),
    slot.classId?.toString() ?? null
  );

  return { booking, slot };
};

// ─── 4. CANCEL BOOKING ────────────────────────────────────────────────────────
export const cancelBooking = async (studentUserId: string, slotId: string) => {
  const student = await StudentProfile.findOne({ userId: studentUserId });
  if (!student) throw new AppError('Student not found', 404);

  const slot = await Slot.findById(slotId).populate('teacherId');
  if (!slot) throw new AppError('Slot not found', 404);

  if (slot.bookedBy?.toString() !== student._id.toString()) {
    throw new AppError('You did not book this slot', 403);
  }

  if (slot.status !== 'booked') {
    throw new AppError('Only booked slots can be cancelled', 400);
  }

  const teacher = slot.teacherId as any;

  await Slot.findByIdAndUpdate(slot._id, {
    $set: {
      status: 'available',
      bookedBy: null,
      bookedByUserId: null,
      bookingId: null,
      studentName: '',
      studentEmail: '',
      studentPhone: '',
    }
  });

  if (slot.bookingId) {
    await Booking.findByIdAndUpdate(slot.bookingId, {
      $set: { status: 'cancelled', cancelledBy: 'student' }
    });
  }

  connectionManager.send(teacher.userId?.toString(), {
    type: 'slot:cancelled',
    payload: {
      slotId: slot._id,
      startTime: slot.startTime,
      endTime: slot.endTime,
      studentName: student.fullName,
    },
  });

  console.log(`[SlotService] ❌ Booking cancelled for ${student.fullName}`);
  return { slot };
};

// ─── 5. GET STUDENT'S BOOKED SLOTS ───────────────────────────────────────────
export const getStudentBookedSlots = async (studentUserId: string) => {
  const student = await StudentProfile.findOne({ userId: studentUserId });
  if (!student) throw new AppError('Student not found', 404);

  return Slot.find({
    bookedBy: student._id,
    status: { $in: ['booked', 'completed', 'missed'] },
  })
    .populate({ path: 'teacherId', select: 'fullName profilePhoto userId' })
    .sort({ date: 1, startTime: 1 })
    .lean();
};

// ─── 6. GET TEACHER'S ALL SLOTS ───────────────────────────────────────────────
// ✅ FIX: bookedBy populate HATA diya — Android ko String chahiye, Object nahi
//         Pehle .populate({ path: 'bookedBy', ... }) tha jisse Android parse nahi kar pata tha
//         Ab bookedBy = raw ObjectId string aayega (StudentProfile._id)
//         bookedByUserId = student ka User._id (jo class.service.ts mein resolve hoga)
export const getTeacherBookedSlots = async (teacherUserId: string, dateStr?: string) => {
  const teacher = await TeacherProfile.findOne({ userId: teacherUserId });
  if (!teacher) throw new AppError('Teacher not found', 404);

  const filter: any = { teacherId: teacher._id };

  if (dateStr) {
    const date = new Date(dateStr + 'T00:00:00.000Z');
    const nextDay = new Date(date);
    nextDay.setUTCDate(nextDay.getUTCDate() + 1);
    filter.date = { $gte: date, $lt: nextDay };
  }

  // ✅ populate HATAYA — bookedBy ab raw ObjectId string hoga
  const slots = await Slot.find(filter)
    .sort({ date: 1, startTime: 1 })
    .lean();

  return slots.map((slot) => {
    const isPassed = isSlotPassed(slot.date, slot.startTime);
    return {
      ...slot,
      // bookedBy = StudentProfile._id (string) — Android isko studentId ke roop mein use karega
      bookedBy: slot.bookedBy?.toString() ?? null,
      // bookedByUserId = User._id — extra field for reference
      bookedByUserId: (slot as any).bookedByUserId?.toString() ?? null,
      isPassed,
      slotStatus: isPassed ? 'passed' : slot.status,
    };
  });
};

// ─── 7. AUTO UPDATE STATUSES ──────────────────────────────────────────────────
export const autoUpdateSlotStatuses = async () => {
  const now = new Date();

  const missedResult = await Slot.updateMany(
    { status: 'booked', date: { $lte: now } },
    { $set: { status: 'missed' } }
  );

  await Booking.updateMany({ status: 'upcoming' }, { $set: { status: 'missed' } });

  console.log(`[SlotService] ⏰ Auto-updated ${missedResult.modifiedCount} slots to missed`);
};

// ─── 8. LINK SLOT TO SESSION ──────────────────────────────────────────────────
export const linkSlotToSession = async (slotId: string, classId: string) => {
  const slot = await Slot.findById(slotId);
  if (!slot) throw new AppError('Slot not found', 404);

  slot.classId = classId as any;
  slot.status = 'completed';
  await slot.save();

  if (slot.bookingId) {
    await Booking.findByIdAndUpdate(slot.bookingId, { status: 'completed' });
  }

  return slot;
};