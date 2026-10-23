import mongoose, { Schema, Document, Types } from 'mongoose';

export interface IReview extends Document {
  studentId: Types.ObjectId;
  teacherId: Types.ObjectId;
  classId: Types.ObjectId;
  rating: number;
  comment?: string;
  createdAt: Date;
  updatedAt: Date;
}

const reviewSchema = new Schema<IReview>(
  {
    studentId: { type: Schema.Types.ObjectId, ref: 'StudentProfile', required: true },
    teacherId: { type: Schema.Types.ObjectId, ref: 'TeacherProfile', required: true },
    classId: { type: Schema.Types.ObjectId, ref: 'Class', required: true },
    rating: { type: Number, required: true, min: 1, max: 5 },
    comment: { type: String, trim: true, maxlength: 1000 },
  },
  { timestamps: true }
);

// Prevent duplicate reviews by the same student for the same class
reviewSchema.index({ studentId: 1, classId: 1 }, { unique: true });
// Optimize querying by teacher and class
reviewSchema.index({ teacherId: 1 });
reviewSchema.index({ classId: 1 });
reviewSchema.index({ createdAt: -1 });

export const Review = mongoose.model<IReview>('Review', reviewSchema);
