import { Session } from '../models/session.model';
import { Class } from '../models/class.model';
import { Booking } from '../models/booking.model';
import { connectionManager } from '../ws/connectionManager';
import { SESSION_STATUS, CLASS_STATUS } from '../constants';
import { Types } from 'mongoose';


type PopulatedBooking = {
  studentId: {
    userId: Types.ObjectId
  }
}
/**
 * Auto-start sessions whose start time has arrived (UTC-based server time)
 * Runs every 30 seconds for better accuracy
 */
export const autoStartSessions = async (): Promise<number> => {
  const now = new Date();

  // ✅ No buffer — only start sessions whose startTime has already passed
  const sessionsToStart = await Session.find({
    status: SESSION_STATUS.UPCOMING,
    startTime: { $lte: now },
  }).populate('classId');

  let startedCount = 0;

  for (const session of sessionsToStart) {
    if (session.status !== SESSION_STATUS.UPCOMING) continue;

    // ✅ Safely extract classId and className regardless of populate result
    const classId = (session.classId as any)?._id ?? session.classId;
    const className = (session.classId as any)?.title ?? 'Class';

    console.log(`[SessionWorker] ⏰ Auto-starting session: ${session._id} (${className}) at ${now.toISOString()}`);

    session.status = SESSION_STATUS.ONGOING;
    session.actualStartTime = now;
    await session.save();

    // ✅ Update class status to ongoing
    await Class.findByIdAndUpdate(classId, {
      status: CLASS_STATUS.ONGOING,
    });

    // Notify teacher via WebSocket
    connectionManager.send(session.teacherId.toString(), {
      type: 'session:auto-started',
      payload: {
        sessionId: session._id,
        classId,
        className,
        startTime: now.toISOString(),
        message: 'Session started automatically based on schedule',
      },
    });

    // Notify all enrolled students via WebSocket
    const bookings = await Booking.find({
      sessionId: session._id,
      paymentStatus: { $in: ['completed', 'pending'] },
    }).populate<PopulatedBooking>({
      path: 'studentId',
      populate: { path: 'userId' },
    });

    for (const booking of bookings) {
      const studentUserId = booking.studentId?.userId?.toString();
      if (studentUserId) {
        connectionManager.send(studentUserId, {
          type: 'session:ready-to-join',
          payload: {
            sessionId: session._id,
            classId,
            className,
            startTime: now.toISOString(),
            message: 'Session is now live! Join now.',
          },
        });
      }
    }

    startedCount++;
  }

  if (startedCount > 0) {
    console.log(`[SessionWorker] ✅ Auto-started ${startedCount} sessions at ${now.toISOString()}`);
  }

  return startedCount;
};

/**
 * Auto-end sessions whose end time has passed (UTC-based server time)
 * Runs every 30 seconds for better accuracy
 */
export const autoEndSessions = async (): Promise<number> => {
  const now = new Date();

  const sessionsToEnd = await Session.find({
    status: SESSION_STATUS.ONGOING,
    endTime: { $lte: now },
  }).populate('classId');

  let endedCount = 0;

  for (const session of sessionsToEnd) {
    if (session.status !== SESSION_STATUS.ONGOING) continue;

    // ✅ Safely extract classId and className
    const classId = (session.classId as any)?._id ?? session.classId;
    const className = (session.classId as any)?.title ?? 'Class';

    console.log(`[SessionWorker] ⏱️ Auto-ending session: ${session._id} (${className}) at ${now.toISOString()}`);

    session.status = SESSION_STATUS.COMPLETED;
    session.actualEndTime = now;
    await session.save();

    // Update class status if no more ongoing sessions for this class
    const ongoingSessions = await Session.countDocuments({
      classId,
      status: SESSION_STATUS.ONGOING,
    });

    if (ongoingSessions === 0) {
      await Class.findByIdAndUpdate(classId, {
        status: CLASS_STATUS.COMPLETED,
      });
    }

    // Notify teacher via WebSocket
    connectionManager.send(session.teacherId.toString(), {
      type: 'session:auto-ended',
      payload: {
        sessionId: session._id,
        classId,
        className,
        endTime: now.toISOString(),
        message: 'Session ended automatically (duration completed)',
      },
    });

    // Notify all students via WebSocket
    const bookings = await Booking.find({ sessionId: session._id })
      .populate<PopulatedBooking>({
        path: 'studentId',
        populate: { path: 'userId' },
      });

    for (const booking of bookings) {
      const studentUserId = booking.studentId?.userId?.toString();
      if (studentUserId) {
        connectionManager.send(studentUserId, {
          type: 'session:ended',
          payload: {
            sessionId: session._id,
            classId,
            className,
            endTime: now.toISOString(),
            message: 'Session has ended.',
          },
        });
      }
    }

    // Update booking status to completed
    await Booking.updateMany(
      { sessionId: session._id },
      { status: 'completed' }
    );

    endedCount++;
  }

  if (endedCount > 0) {
    console.log(`[SessionWorker] ✅ Auto-ended ${endedCount} sessions at ${now.toISOString()}`);
  }

  return endedCount;
};

/**
 * Mark missed sessions (teacher didn't start within grace period)
 * Runs every 2 minutes
 * Grace period: 15 minutes after scheduled start time
 */
export const markMissedSessions = async (graceMinutes = 15): Promise<number> => {
  const now = new Date();
  const graceTime = new Date(now.getTime() - graceMinutes * 60 * 1000);

  // ✅ Only mark as missed if autoStartSessions hasn't already started it
  const missedSessions = await Session.find({
    status: SESSION_STATUS.UPCOMING,
    startTime: { $lt: graceTime },
  }).populate('classId');

  let missedCount = 0;

  for (const session of missedSessions) {
    // ✅ Safely extract classId and className
    const classId = (session.classId as any)?._id ?? session.classId;
    const className = (session.classId as any)?.title ?? 'Class';

    console.log(`[SessionWorker] ❌ Marking session as missed: ${session._id} (${className})`);

    session.status = SESSION_STATUS.MISSED;
    await session.save();

    // ✅ Also update class status to missed
    await Class.findByIdAndUpdate(classId, {
      status: CLASS_STATUS.MISSED,
    });

    // Notify teacher
    connectionManager.send(session.teacherId.toString(), {
      type: 'session:missed',
      payload: {
        sessionId: session._id,
        classId,
        className,
        scheduledStartTime: session.startTime.toISOString(),
        message: `Session marked as missed (no activity for ${graceMinutes} min)`,
      },
    });

    // Notify students
    const bookings = await Booking.find({ sessionId: session._id })
      .populate<PopulatedBooking>({
        path: 'studentId',
        populate: { path: 'userId' },
      });

    for (const booking of bookings) {
      await Booking.findByIdAndUpdate(booking._id, { status: 'missed' });
      const studentUserId = booking.studentId?.userId?.toString();
      if (studentUserId) {
        connectionManager.send(studentUserId, {
          type: 'session:missed',
          payload: {
            sessionId: session._id,
            classId,
            className,
            scheduledStartTime: session.startTime.toISOString(),
            message: 'Session was missed by teacher.',
          },
        });
      }
    }

    missedCount++;
  }

  if (missedCount > 0) {
    console.log(`[SessionWorker] ❌ Marked ${missedCount} sessions as missed at ${now.toISOString()}`);
  }

  return missedCount;
};

/**
 * Start the session worker (call from worker.ts)
 * SCHEDULE:
 * - Auto-start: Every 30 seconds (for accurate timing)
 * - Auto-end: Every 30 seconds (for accurate timing)
 * - Missed detection: Every 2 minutes
 */
export const startSessionWorker = (): void => {
  console.log('[SessionWorker] 🚀 Starting session automation worker...');
  console.log('[SessionWorker] ⏰ Schedule:');
  console.log('[SessionWorker]   - Auto-start: Every 30 seconds');
  console.log('[SessionWorker]   - Auto-end: Every 30 seconds');
  console.log('[SessionWorker]   - Missed detection: Every 2 minutes');

  // Auto-start every 30 seconds
  setInterval(async () => {
    try {
      await autoStartSessions();
    } catch (error) {
      console.error('[SessionWorker] Error in autoStartSessions:', error);
    }
  }, 30 * 1000);

  // Auto-end every 30 seconds
  setInterval(async () => {
    try {
      await autoEndSessions();
    } catch (error) {
      console.error('[SessionWorker] Error in autoEndSessions:', error);
    }
  }, 30 * 1000);

  // Mark missed every 2 minutes
  setInterval(async () => {
    try {
      await markMissedSessions(15);
    } catch (error) {
      console.error('[SessionWorker] Error in markMissedSessions:', error);
    }
  }, 2 * 60 * 1000);

  console.log('[SessionWorker] ✅ Worker scheduled successfully');
};