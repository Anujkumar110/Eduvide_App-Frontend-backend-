import { Session } from '../models/session.model';
import { Class } from '../models/class.model';
import { Types } from 'mongoose';
import { getPagination } from '../utils/pagination';
import { AppError } from '../utils/errors';

// ─── Create sessions from class schedule ────────────────────────────────────

export const createSessionsFromClass = async (
  classId: string,
  schedule: { startTime: Date; endTime: Date; isRecurring?: boolean }[]
) => {
  const cls = await Class.findById(classId);
  if (!cls) throw new Error(`Class not found: ${classId}`);

  const sessions = schedule.map((slot) => ({
    classId:   new Types.ObjectId(classId),
    teacherId: cls.teacherId,
    startTime: slot.startTime,
    endTime:   slot.endTime,
    status:    'upcoming' as const,
  }));

  const created = await Session.insertMany(sessions);
  console.log(`[createSessionsFromClass] Created ${created.length} session(s) for class ${classId}`);
  console.log(`[createSessionsFromClass] First session: startTime=${sessions[0]?.startTime?.toISOString()} endTime=${sessions[0]?.endTime?.toISOString()}`);
  return created;
};

// ─── List sessions for a class ───────────────────────────────────────────────

export const listSessions = async (classId: string, query: any) => {
  const { page, limit, skip } = getPagination(query);

  const filter: Record<string, unknown> = {
    classId: new Types.ObjectId(classId),
  };

  if (query.status) filter.status = query.status;

  const [sessions, total] = await Promise.all([
    Session.find(filter)
      .sort({ startTime: 1 })
      .skip(skip)
      .limit(limit),
    Session.countDocuments(filter),
  ]);

  return { sessions, pagination: { total, page, limit } };
};

// ─── Get single session by ID ─────────────────────────────────────────────────

export const getSessionById = async (sessionId: string) => {
  const session = await Session.findById(sessionId)
    .populate({ path: 'classId',   select: 'title description teacherId' })
    .populate({ path: 'teacherId', select: 'fullName profilePhoto' });

  if (!session) throw new Error(`Session not found: ${sessionId}`);
  return session;
};

// ─── Student: sirf apne enrolled classes ke sessions ────────────────────────

export const getStudentSessions = async (studentId: string, query: any) => {
  const { Enrollment } = await import('../models/enrollment.model');
  const { page, limit, skip } = getPagination(query);

  const enrollments = await Enrollment.find({
    studentId,
    status:  'active',
    classId: { $exists: true, $ne: null },
  }).select('classId').lean();

  console.log(`[getStudentSessions] studentId=${studentId} enrollments found: ${enrollments.length}`);

  if (enrollments.length === 0) {
    return { sessions: [], pagination: { total: 0, page, limit } };
  }

  const classIds = enrollments.map(e => e.classId);

  const filter: Record<string, unknown> = {
    classId: { $in: classIds },
  };

  if (query.status) filter.status = query.status;

  const [sessions, total] = await Promise.all([
    Session.find(filter)
      .populate({ path: 'classId', select: 'title description teacherId' })
      .sort({ startTime: 1 })
      .skip(skip)
      .limit(limit),
    Session.countDocuments(filter),
  ]);

  console.log(`[getStudentSessions] sessions found: ${sessions.length}`);

  return { sessions, pagination: { total, page, limit } };
};

// ─── Teacher: apni saari classes ke sessions ─────────────────────────────────

export const getTeacherSessions = async (teacherId: string, query: any) => {
  const { page, limit, skip } = getPagination(query);

  const filter: Record<string, unknown> = {
    teacherId: new Types.ObjectId(teacherId),
  };

  if (query.status) filter.status = query.status;

  const [sessions, total] = await Promise.all([
    Session.find(filter)
      .populate({ path: 'classId', select: 'title description' })
      .sort({ startTime: 1 })
      .skip(skip)
      .limit(limit),
    Session.countDocuments(filter),
  ]);

  console.log(`[getTeacherSessions] teacherId=${teacherId} sessions found: ${sessions.length}`);

  return { sessions, pagination: { total, page, limit } };
};

// ─── Cron job functions — called every minute ────────────────────────────────

export const autoStartSessions = async () => {
  const now = new Date();

  const result = await Session.updateMany(
    {
      status:    'upcoming',
      startTime: { $lte: now },
      endTime:   { $gt: now },
    },
    {
      $set: {
        status:          'ongoing',
        actualStartTime: now,
      },
    }
  );

  if (result.modifiedCount > 0) {
    console.log(`[cron:autoStart] ${result.modifiedCount} session(s) → ongoing at ${now.toISOString()}`);

    const ongoingSessions = await Session.find({
      status:    'ongoing',
      startTime: { $lte: now },
      endTime:   { $gt: now },
    }).select('classId');

    const classIds = ongoingSessions.map((s) => s.classId);
    if (classIds.length > 0) {
      await Class.updateMany(
        { _id: { $in: classIds } },
        { $set: { status: 'ongoing' } }
      );
      console.log(`[cron:autoStart] Updated ${classIds.length} class(es) → ongoing`);
    }
  }

  return result.modifiedCount;
};

export const autoCompleteSessions = async () => {
  const now = new Date();

  const result = await Session.updateMany(
    {
      status:  'ongoing',
      endTime: { $lte: now },
    },
    {
      $set: {
        status:        'completed',
        actualEndTime: now,
      },
    }
  );

  if (result.modifiedCount > 0) {
    console.log(`[cron:autoComplete] ${result.modifiedCount} session(s) → completed at ${now.toISOString()}`);

    const completedSessions = await Session.find({
      status:  'completed',
      endTime: { $lte: now },
    }).select('classId');

    const classIds = completedSessions.map((s) => s.classId);
    if (classIds.length > 0) {
      await Class.updateMany(
        { _id: { $in: classIds } },
        { $set: { status: 'completed' } }
      );
      console.log(`[cron:autoComplete] Updated ${classIds.length} class(es) → completed`);
    }
  }

  return result.modifiedCount;
};

export const markMissedSessions = async () => {
  const now = new Date();

  const result = await Session.updateMany(
    {
      status:  'upcoming',
      endTime: { $lte: now },
    },
    {
      $set: { status: 'missed' },
    }
  );

  if (result.modifiedCount > 0) {
    console.log(`[cron:markMissed] ${result.modifiedCount} session(s) → missed at ${now.toISOString()}`);

    const missedSessions = await Session.find({ status: 'missed' }).select('classId');

    const classIds = missedSessions.map((s) => s.classId);
    if (classIds.length > 0) {
      await Class.updateMany(
        { _id: { $in: classIds } },
        { $set: { status: 'missed' } }
      );
    }
  }

  return result.modifiedCount;
};

export const debugUpcomingSessions = async () => {
  const now      = new Date();
  const sessions = await Session.find({ status: 'upcoming' })
    .sort({ startTime: 1 })
    .limit(10)
    .lean();

  console.log(`[debug] Current server time (UTC): ${now.toISOString()}`);
  sessions.forEach((s) => {
    const startsIn = Math.round((s.startTime.getTime() - now.getTime()) / 1000 / 60);
    console.log(
      `[debug] Session ${s._id}: startTime=${s.startTime.toISOString()} endTime=${s.endTime.toISOString()} | starts in ${startsIn} min`
    );
  });

  return sessions;
};

// ─── WebSocket session lifecycle ──────────────────────────────────────────────

export const startSession = async (teacherId: string, classId: string) => {
  const session = await Session.findOneAndUpdate(
    {
      classId: new Types.ObjectId(classId),
      status:  'ongoing',
    },
    {
      $set:       { actualStartTime: new Date() },
      $addToSet:  {
        joinedUsers: { userId: new Types.ObjectId(teacherId), joinedAt: new Date() },
      },
    },
    { new: true }
  );

  if (!session) {
    const fallback = await Session.findOneAndUpdate(
      {
        classId: new Types.ObjectId(classId),
        status:  'upcoming',
      },
      {
        $set:      { status: 'ongoing', actualStartTime: new Date() },
        $addToSet: {
          joinedUsers: { userId: new Types.ObjectId(teacherId), joinedAt: new Date() },
        },
      },
      { new: true }
    );

    if (!fallback) throw new Error(`No active session found for class ${classId}`);

    const cls = await Class.findById(classId);
    if (!cls) throw new Error(`Class not found: ${classId}`);

    console.log(`[startSession] Fallback: session manually → ongoing for class ${classId}`);
    return { session: fallback, class: cls };
  }

  const cls = await Class.findById(classId);
  if (!cls) throw new Error(`Class not found: ${classId}`);

  console.log(`[startSession] Teacher ${teacherId} joined session for class ${classId}`);
  return { session, class: cls };
};

export const endSession = async (teacherId: string, sessionId: string) => {
  const session = await Session.findOneAndUpdate(
    {
      _id:    new Types.ObjectId(sessionId),
      status: 'ongoing',
    },
    {
      $set: {
        status:        'completed',
        actualEndTime: new Date(),
      },
    },
    { new: true }
  );

  if (!session) throw new Error(`Session ${sessionId} not found or not ongoing`);

  const cls = await Class.findByIdAndUpdate(
    session.classId,
    { $set: { status: 'completed' } },
    { new: true }
  );

  console.log(`[endSession] Teacher ${teacherId} ended session ${sessionId}`);
  return { session, class: cls };
};

export const attendSession = async (studentId: string, sessionId: string) => {
  const session = await Session.findOneAndUpdate(
    {
      _id:    new Types.ObjectId(sessionId),
      status: 'ongoing',
    },
    {
      $addToSet: {
        attendedStudents: { userId: new Types.ObjectId(studentId), joinedAt: new Date() },
      },
    },
    { new: true }
  );

  if (!session) throw new Error(`Session ${sessionId} not found or not ongoing`);

  console.log(`[attendSession] Student ${studentId} attended session ${sessionId}`);
  return session;
};

// ─── ✅ NEW — Student early leave with reason ─────────────────────────────────

export const leaveSession = async (
  studentUserId: string,
  sessionId:     string,
  reason:        string
) => {
  const { StudentProfile } = await import('../models/studentProfile.model');
  const { Enrollment }     = await import('../models/enrollment.model');

  // 1. Student profile dhundo
  const studentProfile = await StudentProfile.findOne({ userId: studentUserId });
  if (!studentProfile) throw new AppError('Student profile not found', 404);

  // 2. Session dhundo
  const session = await Session.findById(sessionId);
  if (!session) throw new AppError('Session not found', 404);
  if (session.status !== 'ongoing') throw new AppError('Session is not ongoing', 400);

  // 3. Already leave submit ki hai?
  const alreadyLeft = (session.earlyLeaveRequests ?? []).some(
    (r) => r.studentId.toString() === studentProfile._id.toString()
  );
  if (alreadyLeft) throw new AppError('You have already submitted early leave', 400);

  // 4. Reason save karo
  session.earlyLeaveRequests = session.earlyLeaveRequests ?? [];
  (session.earlyLeaveRequests as any[]).push({
    studentId:       studentProfile._id,
    reason,
    leftAt:          new Date(),
    notifiedTeacher: false,
  });

  // 5. Check: kya saare active students chale gaye?
  const activeEnrollments = await Enrollment.find({
    classId: session.classId,
    status:  'active',
  }).lean();

  const leftStudentIds = new Set(
    session.earlyLeaveRequests.map((r) => r.studentId.toString())
  );

  const allLeft = activeEnrollments.every((e) =>
    leftStudentIds.has(e.studentId.toString())
  );

  if (allLeft) {
    // Saare students chale gaye → session + class turant complete
    session.status        = 'completed';
    session.actualEndTime = new Date();

    await Class.findByIdAndUpdate(session.classId, { $set: { status: 'completed' } });
    console.log(`[leaveSession] All students left — session ${sessionId} → completed`);
  }

  await session.save();

  console.log(`[leaveSession] Student ${studentUserId} left session ${sessionId} — reason: ${reason}`);
  return { session };
};