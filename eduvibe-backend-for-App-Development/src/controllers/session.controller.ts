import { Request, Response, NextFunction } from 'express';
import * as sessionService from '../services/session.service';
import { sendSuccess, sendPaginated } from '../utils/response';
import { connectionManager } from '../ws/connectionManager';
import { AppError } from '../utils/errors';
import {
  notifySessionStarted,
  notifySessionEnded,
} from '../services/business/payment.notifications';

/**
 * POST /api/v1/classes/:id/sessions/start
 * Teacher starts a session
 */
export const startSession = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { id: classId } = req.params;
    const teacherId = req.user!.userId;

    const { session, class: cls } = await sessionService.startSession(teacherId, classId as string);

    const { Enrollment } = await import('../models/enrollment.model');
    const { TeacherProfile } = await import('../models/teacherProfile.model');

    const teacherProfile = await TeacherProfile.findOne({ userId: teacherId }).lean();
    const enrollments = await Enrollment.find({ classId, status: 'active' }).populate('studentId');

    for (const enrollment of enrollments) {
      const studentUserId = (enrollment.studentId as any).userId?.toString();
      if (studentUserId) {
        connectionManager.sendToUser(studentUserId, {
          type: 'session:started',
          payload: {
            classId,
            sessionId: session._id,
            className: cls.title,
            teacherId: cls.teacherId,
            actualStartTime: session.actualStartTime,
          },
        });

        try {
          await notifySessionStarted(
            studentUserId,
            cls.title,
            teacherProfile?.fullName ?? 'Teacher',
            session._id.toString()
          );
        } catch (e) {
          console.error('[FCM] Session start notification failed for student:', studentUserId, e);
        }
      }
    }

    sendSuccess(res, 'Session started successfully', { session, class: cls }, 200);
  } catch (err) {
    next(err);
  }
};

/**
 * POST /api/v1/sessions/:id/end
 * Teacher ends a session
 */
export const endSession = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { id: sessionId } = req.params;
    const teacherId = req.user!.userId;

    const { session, class: cls } = await sessionService.endSession(teacherId, sessionId as string);

    const { Enrollment } = await import('../models/enrollment.model');
    const enrollments = await Enrollment.find({ classId: session.classId, status: 'active' }).populate('studentId');

    for (const enrollment of enrollments) {
      const studentUserId = (enrollment.studentId as any).userId?.toString();
      if (studentUserId) {
        connectionManager.sendToUser(studentUserId, {
          type: 'session:ended',
          payload: {
            classId: session.classId,
            sessionId: session._id,
            actualEndTime: session.actualEndTime,
          },
        });

        try {
          await notifySessionEnded(
            studentUserId,
            cls?.title ?? "New Class",
            session._id.toString()
          );
        } catch (e) {
          console.error('[FCM] Session end notification failed for student:', studentUserId, e);
        }
      }
    }

    sendSuccess(res, 'Session ended successfully', { session, class: cls }, 200);
  } catch (err) {
    next(err);
  }
};

/**
 * GET /api/v1/classes/:id/sessions
 * List all sessions for a class
 */
export const listSessions = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { id: classId } = req.params;
    const { sessions, pagination } = await sessionService.listSessions(classId as string, req.query);
    sendPaginated(res, 'Sessions fetched', sessions, pagination);
  } catch (err) {
    next(err);
  }
};

/**
 * GET /api/v1/sessions/:id
 * Get single session details
 */
export const getSession = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { id: sessionId } = req.params;
    const session = await sessionService.getSessionById(sessionId as string);
    sendSuccess(res, 'Session fetched', session);
  } catch (err) {
    next(err);
  }
};

/**
 * POST /api/v1/sessions/:id/attend
 * Student marks attendance for a session
 */
export const attendSession = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { id: sessionId } = req.params;
    const studentId = req.user!.userId;

    const session = await sessionService.attendSession(studentId, sessionId as string);

    connectionManager.sendToRoom(session.classId.toString(), {
      type: 'session:student-joined',
      payload: {
        classId: session.classId,
        sessionId: session._id,
        studentId,
      },
    });

    sendSuccess(res, 'Attendance marked', session, 200);
  } catch (err) {
    next(err);
  }
};

/**
 * GET /api/v1/students/my-sessions
 * Student ki sirf apni enrolled classes ke sessions
 */
export const getStudentSessions = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { StudentProfile } = await import('../models/studentProfile.model');

    const studentProfile = await StudentProfile.findOne({ userId: req.user!.userId });
    if (!studentProfile) return next(new AppError('Student profile not found', 404));

    const { sessions, pagination } = await sessionService.getStudentSessions(
      studentProfile._id.toString(),
      req.query
    );

    sendPaginated(res, 'Student sessions fetched', sessions, pagination);
  } catch (err) {
    next(err);
  }
};

/**
 * GET /api/v1/teachers/my-sessions
 * Teacher ki apni saari classes ke sessions
 */
export const getTeacherSessions = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { TeacherProfile } = await import('../models/teacherProfile.model');

    const teacherProfile = await TeacherProfile.findOne({ userId: req.user!.userId });
    if (!teacherProfile) return next(new AppError('Teacher profile not found', 404));

    const { sessions, pagination } = await sessionService.getTeacherSessions(
      teacherProfile._id.toString(),
      req.query
    );

    sendPaginated(res, 'Teacher sessions fetched', sessions, pagination);
  } catch (err) {
    next(err);
  }
};

/**
 * POST /api/v1/sessions/:id/leave
 * ✅ NEW — Student early leave karta hai with reason
 */
export const leaveSession = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { id: sessionId } = req.params;
    const studentUserId = req.user!.userId;
    const { reason } = req.body;

    if (!reason?.trim()) {
      return next(new AppError('Leave reason is required', 400));
    }

    const { session } = await sessionService.leaveSession(
      studentUserId,
      sessionId as string,
      reason.trim()
    );

    // ── Teacher ko WS se notify karo ─────────────────────────────────────────
    const { TeacherProfile } = await import('../models/teacherProfile.model');
    const teacherProfile = await TeacherProfile.findById(session.teacherId).lean();

    if (teacherProfile) {
      connectionManager.sendToUser((teacherProfile as any).userId.toString(), {
        type: 'session:student-early-leave',
        payload: {
          sessionId: session._id,
          classId: session.classId,
          studentId: studentUserId,
          reason,
          leftAt: new Date(),
          sessionCompleted: session.status === 'completed',
        },
      });
    }

    // ── Agar session complete hua toh saare students ko bhi notify karo ───────
    if (session.status === 'completed') {
      const { Enrollment } = await import('../models/enrollment.model');
      const enrollments = await Enrollment.find({
        classId: session.classId,
        status: 'active',
      }).populate('studentId');

      for (const enrollment of enrollments) {
        const sUserId = (enrollment.studentId as any).userId?.toString();
        if (sUserId) {
          connectionManager.sendToUser(sUserId, {
            type: 'session:ended',
            payload: {
              sessionId: session._id,
              classId: session.classId,
              actualEndTime: session.actualEndTime,
            },
          });
        }
      }
    }

    sendSuccess(res, 'Early leave submitted', { session }, 200);
  } catch (err) {
    next(err);
  }
};