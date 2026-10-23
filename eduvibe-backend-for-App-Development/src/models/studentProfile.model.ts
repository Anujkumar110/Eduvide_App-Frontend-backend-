import mongoose, { Schema, Document, Types } from 'mongoose';

export interface IGoal {
  title: string;
  targetDate: Date;
  isCompleted: boolean;
  progress: number;
}

export interface ISubject {
  title: string;
  isCompleted: boolean;
  progress: number;
}

export interface IStudentProfile extends Document {
  userId: Types.ObjectId;
  fullName: string;
  profilePhoto?: string;
  profilePhotoFileId?: string;
  gradeLevel?: string;
  subjects: ISubject[];
  goals: IGoal[];
  createdAt: Date;
  updatedAt: Date;
}

const studentProfileSchema = new Schema<IStudentProfile>(
  {
    userId: { type: Schema.Types.ObjectId, ref: 'User', required: true, unique: true },
    fullName: { type: String, required: true, trim: true },
    profilePhoto: { type: String },
    profilePhotoFileId: { type: String },
    gradeLevel: { type: String },
    subjects: [
      {
        title: { type: String, required: true },
        isCompleted: { type: Boolean, default: false },
        progress: { type: Number, default: 0, min: 0, max: 100 },
      }
    ],
    goals: [
      {
        title: { type: String, required: true },
        targetDate: { type: Date },
        isCompleted: { type: Boolean, default: false },
        progress: { type: Number, default: 0, min: 0, max: 100 },
      },
    ],
  },
  { timestamps: true }
);

// ✅ userId unique: true field mein hai, isliye schema.index() hata diya

export const StudentProfile = mongoose.model<IStudentProfile>('StudentProfile', studentProfileSchema);