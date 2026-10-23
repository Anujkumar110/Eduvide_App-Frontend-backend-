import mongoose, { Schema, Document, Types } from 'mongoose';

export interface IScheduleSlot {
  startTime: Date;
  endTime: Date;
  isRecurring: boolean;
  recurrenceRule?: string;
}

export interface IClass extends Document {
  teacherId: Types.ObjectId;
  studentId?: Types.ObjectId;   // ← ADDED
  title: string;
  description: string;
  domainId?: Types.ObjectId;
  price: number;
  currency: string;
  maxStudents?: number;
  schedule: IScheduleSlot[];
  status: 'draft' | 'upcoming' | 'published' | 'ongoing' | 'cancelled' | 'completed' | 'missed';
  enrollmentCount: number;
  isModerated: boolean;
  moderationNote?: string;
  videoUrl?: string;
  videoFileId?: string;
  createdAt: Date;
  updatedAt: Date;
}

const classSchema = new Schema<IClass>(
  {
    teacherId: { type: Schema.Types.ObjectId, ref: 'TeacherProfile', required: true },
    studentId: { type: Schema.Types.ObjectId, ref: 'StudentProfile' },   // ← ADDED
    title: { type: String, required: true, trim: true },
    description: { type: String, required: true },
    domainId: { type: Schema.Types.ObjectId, ref: 'Domain' },
    price: { type: Number, required: true, min: 0 },
    currency: { type: String, default: 'INR' },
    maxStudents: { type: Number },
    schedule: [
      {
        startTime: { type: Date, required: true },
        endTime: { type: Date, required: true },
        isRecurring: { type: Boolean, default: false },
        recurrenceRule: { type: String },
      },
    ],
    status: {
      type: String,
      enum: ['draft', 'upcoming', 'published', 'ongoing', 'cancelled', 'completed', 'missed'],
      default: 'upcoming',
    },
    enrollmentCount: { type: Number, default: 0 },
    isModerated: { type: Boolean, default: false },
    moderationNote: { type: String },
    videoUrl: { type: String },
    videoFileId: { type: String },
  },
  { timestamps: true }
);

classSchema.index({ teacherId: 1 });
classSchema.index({ studentId: 1 });   // ← ADDED
classSchema.index({ domainId: 1 });
classSchema.index({ status: 1 });
classSchema.index({ title: 'text', description: 'text' });

export const Class = mongoose.model<IClass>('Class', classSchema);