import { Request, Response } from 'express';
import { submitEarlyLeave, getEarlyLeaveReports } from '../services/earlyLeave.service';

// POST /api/sessions/early-leave
// Body: { roomId, studentId, reason, minutesAttended }
// ✅ studentName body se NAHI — DB se fetch hoga
export const reportEarlyLeave = async (req: Request, res: Response) => {
    try {
        const { roomId, studentId, reason, minutesAttended } = req.body;

        if (!roomId || !studentId || !reason) {
            return res.status(400).json({
                success: false,
                message: 'roomId, studentId aur reason required hain',
            });
        }

        const report = await submitEarlyLeave({
            roomId,
            studentId,
            reason,
            minutesAttended: minutesAttended ?? 0,
        });

        return res.status(201).json({
            success: true,
            message: 'Early leave report submitted',
            data: report,
        });
    } catch (err: any) {
        console.error('[reportEarlyLeave] Error:', err.message);
        return res.status(500).json({
            success: false,
            message: err.message ?? 'Server error',
        });
    }
};

// GET /api/sessions/early-leave/:classId
export const fetchEarlyLeaveReports = async (req: Request, res: Response) => {
    try {
        const { classId } = req.params;
        const reports = await getEarlyLeaveReports(classId as string);

        return res.status(200).json({
            success: true,
            data: reports,
        });
    } catch (err: any) {
        console.error('[fetchEarlyLeaveReports] Error:', err.message);
        return res.status(500).json({
            success: false,
            message: err.message ?? 'Server error',
        });
    }
};