import mongoose, { Schema, Document, Types } from 'mongoose';

export interface IEnrollment extends Document {
  studentId: Types.ObjectId;
  classId?: Types.ObjectId;
  teacherId: Types.ObjectId;
  status: 'active' | 'completed' | 'dropped';
  enrolledAt: Date;
  paymentId?: Types.ObjectId;
  createdAt: Date;
  updatedAt: Date;
}

const enrollmentSchema = new Schema<IEnrollment>(
  {
    studentId: { type: Schema.Types.ObjectId, ref: 'StudentProfile', required: true },
    classId:   { type: Schema.Types.ObjectId, ref: 'Class', required: false },
    teacherId: { type: Schema.Types.ObjectId, ref: 'TeacherProfile', required: true },
    status:    { type: String, enum: ['active', 'completed', 'dropped'], default: 'active' },
    enrolledAt: { type: Date, default: Date.now },
    paymentId:  { type: Schema.Types.ObjectId },
  },
  { timestamps: true }
);

enrollmentSchema.index({ studentId: 1 });
enrollmentSchema.index({ classId: 1 });
enrollmentSchema.index({ teacherId: 1 });

// ✅ FIX: Partial unique index — sirf tab unique check hoga jab classId exist kare aur null na ho
// Isse same student + same teacher ki multiple classes ban sakti hain (har class ka alag record)
// Null classId wale records pe koi conflict nahi hoga
enrollmentSchema.index(
  { studentId: 1, teacherId: 1, classId: 1 },
  {
    unique: true,
    partialFilterExpression: { classId: { $exists: true, $ne: null } },
  }
);

export const Enrollment = mongoose.model<IEnrollment>('Enrollment', enrollmentSchema);