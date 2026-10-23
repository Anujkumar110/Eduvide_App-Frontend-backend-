import { EarlyLeaveReport } from '../models/earlyLeaveReport.model';
import { Session } from '../models/session.model';
import { StudentProfile } from '../models/studentProfile.model';
import { Types } from 'mongoose';

// ─── Submit Early Leave Report ─────────────────────────────────────────────────
export const submitEarlyLeave = async (data: {
    roomId:          string;
    studentId:       string;
    reason:          string;
    minutesAttended: number;
}) => {
    const { roomId, studentId, reason, minutesAttended } = data;

    const profile = await StudentProfile.findOne({
        userId: new Types.ObjectId(studentId)
    }).lean();

    const studentName = profile?.fullName ?? 'Unknown Student';

    const session = await Session.findOne({
        classId: new Types.ObjectId(roomId),
        status:  'ongoing',
    }).lean();

    const sessionId = session?._id?.toString() ?? 'unknown';

    const report = await EarlyLeaveReport.create({
        classId:         roomId,
        sessionId,
        studentId,
        studentName,
        reason,
        minutesAttended,
        leftAt:          new Date(),
    });

    console.log(
        `[earlyLeave] ${studentName} (${studentId}) left class ${roomId} ` +
        `after ${minutesAttended} min — reason: "${reason}"`
    );

    return report;
};

// ─── Teacher: apne class ke saare early leave reports ──────────────────────────
export const getEarlyLeaveReports = async (classId: string) => {
    const oldReports = await EarlyLeaveReport.find({ classId })
        .sort({ leftAt: -1 })
        .lean();

    const sessions = await Session.find({ classId }).lean();
    const newReports: any[] = [];

    for (const session of sessions) {
        for (const req of ((session as any).earlyLeaveRequests ?? [])) {
            let studentName = 'Student';

            let profile = await StudentProfile.findOne({
                userId: new Types.ObjectId(req.studentId)
            }).lean();

            if (!profile) {
                profile = await StudentProfile.findById(req.studentId).lean();
            }

            if (profile) {
                studentName = (profile as any).fullName ?? 'Student';
            }

            // ✅ Minutes calculate karo actualStartTime se
            const leftAt    = new Date(req.leftAt);
            const startTime = (session as any).actualStartTime
                ? new Date((session as any).actualStartTime)
                : new Date(session.startTime);
            const minutesAttended = Math.max(
                0,
                Math.round((leftAt.getTime() - startTime.getTime()) / 60_000)
            );

            newReports.push({
                classId,
                sessionId:       session._id.toString(),
                studentId:       req.studentId.toString(),
                studentName,
                reason:          req.reason,
                minutesAttended,
                leftAt:          req.leftAt,
            });
        }
    }

    const all = [...oldReports, ...newReports]
        .sort((a, b) => new Date(b.leftAt).getTime() - new Date(a.leftAt).getTime());

    return all;
};