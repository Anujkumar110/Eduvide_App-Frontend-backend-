import mongoose, { Schema, Document } from "mongoose";

export interface IEarlyLeaveReport extends Document {
    classId:         string;
    sessionId:       string;
    studentId:       string;
    studentName:     string;
    reason:          string;
    minutesAttended: number;
    leftAt:          Date;
    createdAt:       Date;
}

const EarlyLeaveReportSchema = new Schema<IEarlyLeaveReport>(
    {
        classId:         { type: String, required: true, index: true },
        sessionId:       { type: String, required: true, index: true },
        studentId:       { type: String, required: true, index: true },
        studentName:     { type: String, required: true },
        reason:          { type: String, required: true },
        minutesAttended: { type: Number, default: 0 },
        leftAt:          { type: Date, default: Date.now },
    },
    { timestamps: true }
);

export const EarlyLeaveReport = mongoose.model<IEarlyLeaveReport>(
    "EarlyLeaveReport",
    EarlyLeaveReportSchema
);