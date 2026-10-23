// crons/session.cron.ts
// ✅ v4 — Early leave auto-complete added (Step 6)
// Existing Steps 0-5 bilkul same hain — kuch nahi toda

import cron from 'node-cron';
import { Types } from 'mongoose';
import { Session } from '../models/session.model';
import { Class } from '../models/class.model';
import { Enrollment } from '../models/enrollment.model';
import { sendNotification } from '../services/notification.service';

// ─── Grace period — 2 minutes after endTime before marking missed ─────────────
const GRACE_MS = 2 * 60_000;

// ─── Participant resolver ─────────────────────────────────────────────────────

async function resolveParticipants(sessionId: Types.ObjectId): Promise<{
  teacherUserId: string | null;
  studentUserIds: string[];
}> {
  const session = await Session.findById(sessionId).populate({
    path: 'teacherId',
    select: 'userId',
  });

  if (!session) return { teacherUserId: null, studentUserIds: [] };

  const teacher = session.teacherId as any;
  const teacherUserId: string | null = teacher?.userId?.toString() ?? null;

  const enrollments = await Enrollment.find({
    classId: session.classId,
    status: 'active',
  }).populate<{ studentId: { userId: Types.ObjectId } }>({
    path: 'studentId',
    select: 'userId',
  });

  const studentUserIds: string[] = enrollments
    .map((e) => (e.studentId as any)?.userId?.toString())
    .filter(Boolean);

  return { teacherUserId, studentUserIds };
}

async function notifyParticipants(
  sessionId: Types.ObjectId,
  title: string,
  body: string,
  metadata: Record<string, any> = {}
): Promise<void> {
  const { teacherUserId, studentUserIds } = await resolveParticipants(sessionId);
  const userIds = [...(teacherUserId ? [teacherUserId] : []), ...studentUserIds];

  if (userIds.length === 0) {
    console.warn(`[Cron] No participants for session ${sessionId}`);
    return;
  }

  await Promise.allSettled(
    userIds.map((userId) =>
      sendNotification(userId, title, body, {
        sessionId: sessionId.toString(),
        ...metadata,
      })
    )
  );
}

// ─── Helper: did anyone actually participate? ─────────────────────────────────
function anyoneJoined(session: any): boolean {
  const hasJoined = Array.isArray(session.joinedUsers) && session.joinedUsers.length > 0;
  const hasAttended = Array.isArray(session.attendedStudents) && session.attendedStudents.length > 0;
  return hasJoined || hasAttended;
}

// ─── Master cron — runs every minute ─────────────────────────────────────────

async function runSessionCron(): Promise<void> {
  const now = new Date();
  const gracedNow = new Date(now.getTime() - GRACE_MS);

  console.log(`\n[Cron] ⏱  Tick at ${now.toISOString()}`);

  // ════════════════════════════════════════════════════════════════
  // STEP 0 — EDGE CASE: 'upcoming' session jiska endTime pass ho gaya
  //           aur attendedStudents > 0 → directly 'completed'
  // ════════════════════════════════════════════════════════════════
  try {
    const upcomingExpiredWithAttend = await Session.find({
      status: 'upcoming',
      endTime: { $lte: gracedNow },
      $or: [
        { joinedUsers: { $exists: true, $not: { $size: 0 } } },
        { attendedStudents: { $exists: true, $not: { $size: 0 } } },
      ],
    }).select('_id classId joinedUsers attendedStudents');

    if (upcomingExpiredWithAttend.length > 0) {
      const ids = upcomingExpiredWithAttend.map((s) => s._id);
      const classIds = upcomingExpiredWithAttend.map((s) => s.classId);

      await Session.updateMany(
        { _id: { $in: ids } },
        { $set: { status: 'completed', actualEndTime: now } }
      );
      await Class.updateMany(
        { _id: { $in: classIds } },
        { $set: { status: 'completed' } }
      );

      console.log(`[Cron] ✅ Step 0: ${upcomingExpiredWithAttend.length} upcoming-but-attended session(s) → completed`);

      for (const session of upcomingExpiredWithAttend) {
        await notifyParticipants(
          session._id as Types.ObjectId,
          'Class Completed',
          'Your session has ended. Great work!',
          { type: 'session_ended' }
        ).catch((err) =>
          console.error(`[Cron] Notify error (step0 completed) ${session._id}:`, err)
        );
      }
    }
  } catch (err) {
    console.error('[Cron] ❌ Step 0 (edge-case completed) failed:', err);
  }

  // ════════════════════════════════════════════════════════════════
  // STEP 1 — Mark MISSED
  // ════════════════════════════════════════════════════════════════
  try {
    const missedSessions = await Session.find({
      status: { $in: ['upcoming', 'ongoing'] },
      endTime: { $lte: gracedNow },
      $and: [
        {
          $or: [
            { joinedUsers: { $exists: false } },
            { joinedUsers: { $size: 0 } },
          ],
        },
        {
          $or: [
            { attendedStudents: { $exists: false } },
            { attendedStudents: { $size: 0 } },
          ],
        },
      ],
    }).select('_id classId joinedUsers attendedStudents');

    if (missedSessions.length > 0) {
      const missedIds = missedSessions.map((s) => s._id);
      const classIds = missedSessions.map((s) => s.classId);

      await Session.updateMany(
        { _id: { $in: missedIds } },
        { $set: { status: 'missed' } }
      );
      await Class.updateMany(
        { _id: { $in: classIds } },
        { $set: { status: 'missed' } }
      );

      console.log(`[Cron] ❌ Step 1: ${missedSessions.length} session(s) → missed`);

      for (const session of missedSessions) {
        await notifyParticipants(
          session._id as Types.ObjectId,
          'Class Missed',
          'Your scheduled session was missed.',
          { type: 'session_missed' }
        ).catch((err) =>
          console.error(`[Cron] Notify error (missed) ${session._id}:`, err)
        );
      }
    }
  } catch (err) {
    console.error('[Cron] ❌ Step 1 (missed) failed:', err);
  }

  // ════════════════════════════════════════════════════════════════
  // STEP 2 — Mark ONGOING
  // ════════════════════════════════════════════════════════════════
  try {
    const ongoingSessions = await Session.find({
      status: 'upcoming',
      startTime: { $lte: now },
      endTime: { $gt: now },
    }).select('_id classId');

    if (ongoingSessions.length > 0) {
      const ongoingIds = ongoingSessions.map((s) => s._id);
      const classIds = ongoingSessions.map((s) => s.classId);

      await Session.updateMany(
        { _id: { $in: ongoingIds } },
        { $set: { status: 'ongoing', actualStartTime: now } }
      );
      await Class.updateMany(
        { _id: { $in: classIds } },
        { $set: { status: 'ongoing' } }
      );

      console.log(`[Cron] 🟢 Step 2: ${ongoingSessions.length} session(s) → ongoing`);

      for (const session of ongoingSessions) {
        await notifyParticipants(
          session._id as Types.ObjectId,
          'Class Has Started',
          'Your session is now live. Join now!',
          { type: 'session_started' }
        ).catch((err) =>
          console.error(`[Cron] Notify error (ongoing) ${session._id}:`, err)
        );
      }
    }
  } catch (err) {
    console.error('[Cron] ❌ Step 2 (ongoing) failed:', err);
  }

  // ════════════════════════════════════════════════════════════════
  // STEP 3 — Mark COMPLETED (time-based)
  // ════════════════════════════════════════════════════════════════
  try {
    const expiredOngoing = await Session.find({
      status: 'ongoing',
      endTime: { $lte: gracedNow },
    }).select('_id classId joinedUsers attendedStudents');

    const completedSessions = expiredOngoing.filter((s) => anyoneJoined(s));
    const alsoMissed = expiredOngoing.filter((s) => !anyoneJoined(s));

    if (completedSessions.length > 0) {
      const completedIds = completedSessions.map((s) => s._id);
      const classIds = completedSessions.map((s) => s.classId);

      await Session.updateMany(
        { _id: { $in: completedIds } },
        { $set: { status: 'completed', actualEndTime: now } }
      );
      await Class.updateMany(
        { _id: { $in: classIds } },
        { $set: { status: 'completed' } }
      );

      console.log(`[Cron] ✅ Step 3: ${completedSessions.length} session(s) → completed`);

      for (const session of completedSessions) {
        await notifyParticipants(
          session._id as Types.ObjectId,
          'Class Completed',
          'Your session has ended. Great work!',
          { type: 'session_ended' }
        ).catch((err) =>
          console.error(`[Cron] Notify error (completed) ${session._id}:`, err)
        );
      }
    }

    // Safety net: ongoing-missed jo Step 1 se bach gaye
    if (alsoMissed.length > 0) {
      const ids = alsoMissed.map((s) => s._id);
      const classIds = alsoMissed.map((s) => s.classId);

      await Session.updateMany({ _id: { $in: ids } }, { $set: { status: 'missed' } });
      await Class.updateMany({ _id: { $in: classIds } }, { $set: { status: 'missed' } });

      console.log(`[Cron] ❌ Step 3 safety-net: ${alsoMissed.length} ongoing-missed → missed`);

      for (const session of alsoMissed) {
        await notifyParticipants(
          session._id as Types.ObjectId,
          'Class Missed',
          'Your scheduled session was missed.',
          { type: 'session_missed' }
        ).catch((err) =>
          console.error(`[Cron] Notify error (safety missed) ${session._id}:`, err)
        );
      }
    }
  } catch (err) {
    console.error('[Cron] ❌ Step 3 (completed) failed:', err);
  }

  // ════════════════════════════════════════════════════════════════
  // STEP 4 — REMINDERS (15 min before start)
  // ════════════════════════════════════════════════════════════════
  try {
    const reminderFrom = new Date(now.getTime() + 15 * 60_000);
    const reminderTo = new Date(now.getTime() + 16 * 60_000);

    const upcomingSessions = await Session.find({
      status: 'upcoming',
      startTime: { $gte: reminderFrom, $lt: reminderTo },
    }).select('_id classId');

    if (upcomingSessions.length > 0) {
      console.log(`[Cron] 🔔 Step 4: ${upcomingSessions.length} reminder(s) to send`);

      for (const session of upcomingSessions) {
        await notifyParticipants(
          session._id as Types.ObjectId,
          'Class Starting Soon',
          'Your session begins in 15 minutes. Get ready!',
          { type: 'session_reminder' }
        ).catch((err) =>
          console.error(`[Cron] Notify error (reminder) ${session._id}:`, err)
        );
      }
    }
  } catch (err) {
    console.error('[Cron] ❌ Step 4 (reminders) failed:', err);
  }

  // ════════════════════════════════════════════════════════════════
  // STEP 5 — ENDING SOON (10 min before end)
  // ════════════════════════════════════════════════════════════════
  try {
    const endingSoonFrom = new Date(now.getTime() + 10 * 60_000);
    const endingSoonTo = new Date(now.getTime() + 11 * 60_000);

    const endingSessions = await Session.find({
      status: 'ongoing',
      endTime: { $gte: endingSoonFrom, $lt: endingSoonTo },
    }).select('_id classId');

    if (endingSessions.length > 0) {
      console.log(`[Cron] ⚠️  Step 5: ${endingSessions.length} session(s) ending soon`);

      for (const session of endingSessions) {
        await notifyParticipants(
          session._id as Types.ObjectId,
          'Class Ending Soon',
          'Your session ends in 10 minutes. Start wrapping up!',
          { type: 'session_ending_soon' }
        ).catch((err) =>
          console.error(`[Cron] Notify error (ending) ${session._id}:`, err)
        );
      }
    }
  } catch (err) {
    console.error('[Cron] ❌ Step 5 (ending soon) failed:', err);
  }

  // ════════════════════════════════════════════════════════════════
  // STEP 6 — ✅ NEW: Early leave safety net
  // Ongoing sessions jisme saare students ne early leave submit ki
  // lekin kisi reason se controller ne complete nahi kiya (e.g. network issue)
  // Cron 1 minute mein pakad leta hai
  // ════════════════════════════════════════════════════════════════
  try {
    const sessionsWithLeave = await Session.find({
      status: 'ongoing',
      'earlyLeaveRequests.0': { $exists: true },
    }).select('_id classId teacherId earlyLeaveRequests');

    for (const session of sessionsWithLeave) {
      const activeEnrollments = await Enrollment.find({
        classId: session.classId,
        status: 'active',
      }).lean();

      const leftStudentIds = new Set(
        (session.earlyLeaveRequests ?? []).map((r: any) => r.studentId.toString())
      );

      const allLeft = activeEnrollments.every((e) =>
        leftStudentIds.has(e.studentId.toString())
      );

      if (allLeft) {
        await Session.findByIdAndUpdate(session._id, {
          $set: { status: 'completed', actualEndTime: now },
        });
        await Class.findByIdAndUpdate(session.classId, {
          $set: { status: 'completed' },
        });

        console.log(`[Cron] ✅ Step 6: Session ${session._id} → completed (all students left early)`);

        await notifyParticipants(
          session._id as Types.ObjectId,
          'Class Completed',
          'Session ended as all students left early.',
          { type: 'session_ended' }
        ).catch((err) =>
          console.error(`[Cron] Notify error (step6 early-leave) ${session._id}:`, err)
        );
      }
    }
  } catch (err) {
    console.error('[Cron] ❌ Step 6 (early leave safety net) failed:', err);
  }
}

// ─── Register cron job ────────────────────────────────────────────────────────

let isRunning = false;

export function startSessionCronJob(): void {
  cron.schedule('* * * * *', async () => {
    if (isRunning) {
      console.warn('[Cron] ⚠️  Previous tick still running — skipping');
      return;
    }
    isRunning = true;
    try {
      await runSessionCron();
    } finally {
      isRunning = false;
    }
  }, {
    timezone: 'Asia/Kolkata',
  });

  console.log('[Cron] ✅ Session cron job registered — runs every 1 minute (IST)');
}