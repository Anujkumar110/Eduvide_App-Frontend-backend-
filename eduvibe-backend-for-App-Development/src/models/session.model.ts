import mongoose, { Schema, Document, Types } from 'mongoose';

export interface IEarlyLeaveRequest {
  studentId: Types.ObjectId;
  reason: string;
  leftAt: Date;
  notifiedTeacher: boolean;
}

export interface ISession extends Document {
  classId: Types.ObjectId;
  teacherId: Types.ObjectId;
  startTime: Date;
  endTime: Date;
  status: 'upcoming' | 'ongoing' | 'completed' | 'missed' | 'cancelled';
  actualStartTime?: Date;
  actualEndTime?: Date;
  recordingUrl?: string;
  recordingFileId?: string;
  attendedStudents: { userId: Types.ObjectId; joinedAt: Date }[];
  joinedUsers: { userId: Types.ObjectId; joinedAt: Date }[];
  earlyLeaveRequests: IEarlyLeaveRequest[]; // ✅ NEW
  createdAt: Date;
  updatedAt: Date;
}

const sessionSchema = new Schema<ISession>(
  {
    classId:   { type: Schema.Types.ObjectId, ref: 'Class',          required: true },
    teacherId: { type: Schema.Types.ObjectId, ref: 'TeacherProfile', required: true },
    startTime: { type: Date, required: true },
    endTime:   { type: Date, required: true },

    status: {
      type:    String,
      enum:    ['upcoming', 'ongoing', 'completed', 'missed', 'cancelled'],
      default: 'upcoming',
    },

    actualStartTime: { type: Date },
    actualEndTime:   { type: Date },
    recordingUrl:    { type: String },
    recordingFileId: { type: String },

    // ✅ ObjectId array → object array (joinedAt track karne ke liye)
    attendedStudents: [
      {
        userId:   { type: Schema.Types.ObjectId, ref: 'StudentProfile' },
        joinedAt: { type: Date, default: Date.now },
      },
    ],

    // ✅ Teacher + student WebSocket joins
    // Cron ka anyoneJoined() check karta hai — agar empty → missed
    joinedUsers: [
      {
        userId:   { type: Schema.Types.ObjectId, ref: 'User' },
        joinedAt: { type: Date, default: Date.now },
      },
    ],

    // ✅ NEW — Student early leave with reason
    earlyLeaveRequests: [
      {
        studentId:       { type: Schema.Types.ObjectId, ref: 'StudentProfile' },
        reason:          { type: String, required: true },
        leftAt:          { type: Date, default: Date.now },
        notifiedTeacher: { type: Boolean, default: false },
      },
    ],
  },
  { timestamps: true }
);

// Indexes for efficient queries
sessionSchema.index({ classId: 1 });
sessionSchema.index({ teacherId: 1 });
sessionSchema.index({ status: 1 });
sessionSchema.index({ startTime: 1 });
sessionSchema.index({ classId: 1, status: 1 });
sessionSchema.index({ startTime: 1, status: 1 });

// Helper method to check if a student attended
sessionSchema.methods.hasStudentAttended = function (studentId: string): boolean {
  return this.attendedStudents.some(
    (entry: { userId: Types.ObjectId }) => entry.userId.toString() === studentId
  );
};

export const Session = mongoose.model<ISession>('Session', sessionSchema);