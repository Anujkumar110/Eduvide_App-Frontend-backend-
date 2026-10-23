import { Class } from '../models/class.model';
import { TeacherProfile } from '../models/teacherProfile.model';
import { AppError } from '../utils/errors';
import { getPagination } from '../utils/pagination';
import { Request } from 'express';
import { uploadQueue, UPLOAD_JOB_TYPES } from '../queues/upload.queue';

export interface CreateClassInput {
  title?: string;
  description?: string;
  domainId?: string;
  price?: number;
  currency?: string;
  maxStudents?: number;
  schedule?: any[];
  date?: string;
  time?: string;
  durationMin?: number;
  studentId?: string;
}

export interface UpdateClassInput {
  title?: string;
  description?: string;
  domainId?: string;
  price?: number;
  currency?: string;
  maxStudents?: number;
  schedule?: any[];
  status?: string;
}

function buildScheduleFromAndroid(
  date: string,
  time: string,
  durationMin: number
): { startTime: Date; endTime: Date }[] {

  const monthMap: Record<string, number> = {
    jan: 0, feb: 1, mar: 2, apr: 3, may: 4, jun: 5,
    jul: 6, aug: 7, sep: 8, oct: 9, nov: 10, dec: 11,
  };

  const dateParts = date.trim().split(/\s+/);
  if (dateParts.length !== 3) {
    throw new AppError(
      `Invalid date format. Expected "d MMM yyyy" (e.g. "25 Apr 2026"), got "${date}"`,
      400
    );
  }
  const day   = parseInt(dateParts[0], 10);
  const month = monthMap[dateParts[1].toLowerCase().slice(0, 3)];
  const year  = parseInt(dateParts[2], 10);

  if (isNaN(day) || month === undefined || isNaN(year)) {
    throw new AppError(`Cannot parse date "${date}". Use format "25 Apr 2026"`, 400);
  }

  const timeMatch = time.trim().match(/^(\d{1,2}):(\d{2})\s*(AM|PM|am|pm)$/i);
  if (!timeMatch) {
    throw new AppError(
      `Invalid time format. Expected "h:mm AM/PM" (e.g. "3:40 PM"), got "${time}"`,
      400
    );
  }
  let hours  = parseInt(timeMatch[1], 10);
  const mins = parseInt(timeMatch[2], 10);
  const ampm = timeMatch[3].toUpperCase();

  if (ampm === 'PM' && hours !== 12) hours += 12;
  if (ampm === 'AM' && hours === 12) hours = 0;

  const istOffsetMs = 5.5 * 60 * 60 * 1000;
  const startTime = new Date(Date.UTC(year, month, day, hours, mins, 0, 0) - istOffsetMs);

  if (isNaN(startTime.getTime())) {
    throw new AppError(`Date/time parsing failed for date="${date}" time="${time}"`, 400);
  }

  if (durationMin <= 0) throw new AppError('Duration must be greater than 0 minutes', 400);

  const endTime = new Date(startTime.getTime() + durationMin * 60 * 1000);

  console.log(`[createClass] IST input: date="${date}" time="${time}" → UTC startTime=${startTime.toISOString()} endTime=${endTime.toISOString()}`);

  return [{ startTime, endTime }];
}

export const createClass = async (userId: string, data: CreateClassInput) => {
  const profile = await TeacherProfile.findOne({ userId });
  if (!profile) throw new AppError('Teacher profile not found', 404);
  if (profile.verification.status !== 'verified')
    throw new AppError('Only verified teachers can create classes', 403);

  if (!data.studentId || data.studentId.trim() === '') {
    throw new AppError('studentId is required — kis student ke liye class ban rahi hai?', 400);
  }

  const { StudentProfile } = await import('../models/studentProfile.model');

  let resolvedStudentId: string = data.studentId;

  const studentProf =
    await StudentProfile.findOne({ userId: data.studentId }).lean()
    ?? await StudentProfile.findById(data.studentId).lean();

  if (studentProf) {
    resolvedStudentId = studentProf._id.toString();
    console.log(`[createClass] studentId resolved: ${data.studentId} → StudentProfile._id: ${resolvedStudentId}`);
  } else {
    console.warn(`[createClass] StudentProfile nahi mila for studentId=${data.studentId} — raw id use karunga`);
  }

  let finalSchedule: { startTime: Date; endTime: Date; isRecurring: boolean }[] = [];

  if (data.date && data.time && data.durationMin) {
    const slots = buildScheduleFromAndroid(data.date, data.time, data.durationMin);
    finalSchedule = slots.map((s) => ({ ...s, isRecurring: false }));
  } else if (data.schedule && data.schedule.length > 0) {
    finalSchedule = data.schedule.map((s: any) => ({
      startTime: new Date(s.startTime),
      endTime: new Date(s.endTime),
      isRecurring: s.isRecurring ?? false,
      recurrenceRule: s.recurrenceRule,
    }));
  } else {
    throw new AppError(
      'Schedule is required. Provide either (date + time + durationMin) or schedule array',
      400
    );
  }

  // ← FIXED: studentId ab Class document mein save hoga
  const newClass = await Class.create({
    teacherId:   profile._id,
    studentId:   resolvedStudentId,   // ← ADDED
    title:       data.title,
    description: data.description,
    domainId:    data.domainId,
    price:       data.price,
    currency:    data.currency ?? 'INR',
    maxStudents: data.maxStudents,
    schedule:    finalSchedule,
    status:      'upcoming',
  });

  // ✅ Session create karo class banate waqt hi
  const { Session } = await import('../models/session.model');
  const { Types } = await import('mongoose');

  const sessions = finalSchedule.map((slot) => ({
    classId:   new Types.ObjectId(newClass._id.toString()),
    teacherId: profile._id,
    startTime: slot.startTime,
    endTime:   slot.endTime,
    status:    'upcoming' as const,
  }));

  const createdSessions = await Session.insertMany(sessions);
  console.log(`[createClass] ✅ ${createdSessions.length} session(s) created for class ${newClass._id}`);
  console.log(`[createClass] Session startTime=${sessions[0]?.startTime?.toISOString()} endTime=${sessions[0]?.endTime?.toISOString()}`);

  const { Enrollment } = await import('../models/enrollment.model');

  const linkResult = await Enrollment.findOneAndUpdate(
    {
      teacherId: profile._id,
      studentId: resolvedStudentId,
      classId:   newClass._id,
    },
    {
      $setOnInsert: {
        teacherId: profile._id,
        studentId: resolvedStudentId,
        classId:   newClass._id,
        status:    'active',
      },
    },
    { upsert: true, new: true }
  );

  console.log(`[createClass] Enrollment upserted for studentId=${resolvedStudentId} classId=${newClass._id} enrollmentId=${linkResult._id}`);

  const { Slot } = await import('../models/slot.model');

  await Slot.updateMany(
    {
      bookedBy:  new Types.ObjectId(data.studentId),
      teacherId: profile._id,
      classId:   { $exists: false },
    },
    {
      $set: { classId: newClass._id }
    }
  );
  console.log(`[createClass] ✅ Slot classId updated for studentId=${data.studentId}`);

  return newClass;
};

export const listClasses = async (query: Request['query']) => {
  const { page, limit, skip, sort } = getPagination(query);

  if ((query as any).mine === 'true') {
    const filter: Record<string, unknown> = {};
    if ((query as any).teacherId) filter.teacherId = (query as any).teacherId;
    if (query.status) {
      const s = query.status as string;
      filter.status = s === 'published' ? 'upcoming' : s;
    }

    const [classes, total] = await Promise.all([
      Class.find(filter)
        .populate({ path: 'teacherId', select: 'fullName rating profilePhoto' })
        .populate({ path: 'studentId', select: 'fullName profilePhoto userId' })  // ← ADDED
        .sort(sort).skip(skip).limit(limit),
      Class.countDocuments(filter),
    ]);
    return { classes, pagination: { total, page, limit } };
  }

  if ((query as any).enrolled === 'true') {
    const { Enrollment } = await import('../models/enrollment.model');

    const enrollments = await Enrollment.find({
      studentId: (query as any).studentId,
      status: 'active',
    }).select('classId').lean();

    if (enrollments.length === 0) {
      return { classes: [], pagination: { total: 0, page, limit } };
    }

    const classIds = enrollments
      .filter(e => e.classId)
      .map(e => e.classId);

    if (classIds.length === 0) {
      return { classes: [], pagination: { total: 0, page, limit } };
    }

    const classFilter: Record<string, unknown> = {
      _id: { $in: classIds },
    };

    if (query.status) {
      const s = query.status as string;
      classFilter.status = s === 'published' ? 'upcoming' : s;
    }

    const [classes, total] = await Promise.all([
      Class.find(classFilter)
        .populate({ path: 'teacherId', select: 'fullName rating profilePhoto' })
        .sort(sort).skip(skip).limit(limit),
      Class.countDocuments(classFilter),
    ]);

    return { classes, pagination: { total, page, limit } };
  }

  const requestedStatus = (query.status as string) || 'upcoming';
  const dbStatus = requestedStatus === 'published' ? 'upcoming' : requestedStatus;
  const filter: Record<string, unknown> = { status: dbStatus };
  if (query.domain) filter.domainId = query.domain;
  if (query.teacher) filter.teacherId = query.teacher;
  if (query.search) filter.$text = { $search: query.search as string };
  if (query.minPrice || query.maxPrice) {
    filter.price = {};
    if (query.minPrice) (filter.price as any).$gte = parseFloat(query.minPrice as string);
    if (query.maxPrice) (filter.price as any).$lte = parseFloat(query.maxPrice as string);
  }
  const [classes, total] = await Promise.all([
    Class.find(filter)
      .populate({ path: 'teacherId', select: 'fullName rating profilePhoto' })
      .sort(sort).skip(skip).limit(limit),
    Class.countDocuments(filter),
  ]);
  return { classes, pagination: { total, page, limit } };
};

export const getClassById = async (classId: string) => {
  const cls = await Class.findById(classId).populate({
    path: 'teacherId',
    select: 'fullName bio rating profilePhoto',
  });
  if (!cls) throw new AppError('Class not found', 404);
  return cls;
};

export const uploadVideo = async (userId: string, classId: string, file?: Express.Multer.File) => {
  if (!file) throw new AppError('Video file is required', 400);

  const profile = await TeacherProfile.findOne({ userId });
  if (!profile) throw new AppError('Teacher profile not found', 404);

  const cls = await Class.findOne({ _id: classId, teacherId: profile._id });
  if (!cls) throw new AppError('Class not found or you are not the owner', 404);

  await uploadQueue.add(UPLOAD_JOB_TYPES.VIDEO, {
    userId,
    role: 'teacher',
    filePath: file.path,
    originalName: file.originalname,
    mimeType: file.mimetype,
    classId,
  });

  return cls;
};

export const updateClass = async (userId: string, classId: string, data: UpdateClassInput) => {
  const profile = await TeacherProfile.findOne({ userId });
  if (!profile) throw new AppError('Teacher profile not found', 404);

  const cls = await Class.findOne({ _id: classId, teacherId: profile._id });
  if (!cls) throw new AppError('Class not found or you are not the owner', 404);

  const updated = await Class.findByIdAndUpdate(
    classId,
    {
      $set: {
        ...data,
        ...(data.schedule && {
          schedule: data.schedule.map((s: any) => ({
            ...s,
            startTime: new Date(s.startTime as string),
            endTime:   new Date(s.endTime as string),
          })),
        }),
      },
    },
    { new: true }
  );
  return updated;
};

export const deleteClass = async (userId: string, classId: string) => {
  const profile = await TeacherProfile.findOne({ userId });
  if (!profile) throw new AppError('Teacher profile not found', 404);

  const cls = await Class.findOneAndDelete({ _id: classId, teacherId: profile._id });
  if (!cls) throw new AppError('Class not found or you are not the owner', 404);

  const { Session } = await import('../models/session.model');
  const { Types } = await import('mongoose');
  await Session.deleteMany({ classId: new Types.ObjectId(classId) });
  console.log(`[deleteClass] Sessions deleted for class ${classId}`);
};

export const getClassStudents = async (userId: string, classId: string, query: Request['query']) => {
  const profile = await TeacherProfile.findOne({ userId });
  if (!profile) throw new AppError('Teacher profile not found', 404);

  const cls = await Class.findOne({ _id: classId, teacherId: profile._id });
  if (!cls) throw new AppError('Class not found or you are not the owner', 404);

  const { Enrollment } = await import('../models/enrollment.model');
  const { page, limit, skip } = getPagination(query);

  const [enrollments, total] = await Promise.all([
    Enrollment.find({ classId, status: 'active' })
      .populate({ path: 'studentId', populate: { path: 'userId', select: 'email' } })
      .skip(skip)
      .limit(limit),
    Enrollment.countDocuments({ classId, status: 'active' }),
  ]);

  return { enrollments, pagination: { total, page, limit } };
};