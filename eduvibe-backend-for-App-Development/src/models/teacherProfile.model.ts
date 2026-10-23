import mongoose, { Schema, Document, Types } from 'mongoose';
import { ITeacherAvailability, dayAvailabilitySchema } from './availability.model';

export interface IQualification {
  degree: string;
  institution: string;
  year: number;
  certificateUrl: string;
}

export interface IVerification {
  status: 'pending' | 'verified' | 'rejected' | 'reupload_requested';
  idProofUrl: string;
  idProofFileId?: string;
  idProofPath?: string;
  reviewedBy?: Types.ObjectId;
  reviewedAt?: Date;
  rejectionReason?: string;
}

export interface ITeacherProfile extends Document {
  userId: Types.ObjectId;
  fullName: string;
  email: string;
  gender: 'male' | 'female' | 'other';
  age: number;
  bio: string;
  profilePhoto?: string;
  profilePhotoFileId?: string;
  introVideo?: string;
  introVideoFileId?: string;
  subjects: string[];      // ✅ ObjectId[] → string[]
  boards: string[];        // ✅ added
  languages: string[];     // ✅ added
  qualifications: IQualification[];
  experience: number;
  pricing: { hourlyRate: number; currency: string };
  availability: ITeacherAvailability;
  verification: IVerification;
  rating: { average: number; count: number };
  totalStudents: number;
  totalEarnings: number;
  isLive: boolean;
  createdAt: Date;
  updatedAt: Date;
}

const teacherProfileSchema = new Schema<ITeacherProfile>(
  {
    userId:            { type: Schema.Types.ObjectId, ref: 'User', required: true, unique: true },
    fullName:          { type: String, required: true, trim: true },
    email:             { type: String, required: true, unique: true, trim: true, lowercase: true },
    gender:            { type: String, enum: ['male', 'female', 'other', 'prefer not to say'] },
    age:               { type: Number, min: 18, max: 100 },
    bio:               { type: String, default: '' },
    profilePhoto:      { type: String },
    profilePhotoFileId:{ type: String },
    introVideo:        { type: String },
    introVideoFileId:  { type: String },

    // ✅ subjects: ObjectId ref hata ke plain String array kar diya
    subjects:  [{ type: String, trim: true }],
    boards:    [{ type: String, trim: true }],   // ✅ added
    languages: [{ type: String, trim: true }],   // ✅ added

    qualifications: [
      {
        degree:         { type: String, required: true },
        institution:    { type: String, default: '' },
        year:           { type: Number, default: 0 },
        certificateUrl: { type: String, default: '' },
      },
    ],
    experience: { type: Number, default: 0 },
    pricing: {
      hourlyRate: { type: Number, default: 0 },
      currency:   { type: String, default: 'INR' },
    },
    availability: {
      monday:    { type: dayAvailabilitySchema, default: { isAvailable: false, slots: [] } },
      tuesday:   { type: dayAvailabilitySchema, default: { isAvailable: false, slots: [] } },
      wednesday: { type: dayAvailabilitySchema, default: { isAvailable: false, slots: [] } },
      thursday:  { type: dayAvailabilitySchema, default: { isAvailable: false, slots: [] } },
      friday:    { type: dayAvailabilitySchema, default: { isAvailable: false, slots: [] } },
      saturday:  { type: dayAvailabilitySchema, default: { isAvailable: false, slots: [] } },
      sunday:    { type: dayAvailabilitySchema, default: { isAvailable: false, slots: [] } },
    },
    verification: {
      status: {
        type: String,
        enum: ['pending', 'verified', 'rejected', 'reupload_requested'],
        default: 'pending',
      },
      idProofUrl:      { type: String, default: '' },
      idProofFileId:   { type: String },
      idProofPath:     { type: String },
      reviewedBy:      { type: Schema.Types.ObjectId, ref: 'User' },
      reviewedAt:      { type: Date },
      rejectionReason: { type: String },
    },
    rating: {
      average: { type: Number, default: 0 },
      count:   { type: Number, default: 0 },
    },
    totalStudents:  { type: Number, default: 0 },
    totalEarnings:  { type: Number, default: 0 },
    isLive:         { type: Boolean, default: false },
  },
  { timestamps: true }
);

teacherProfileSchema.index({ subjects: 1 });
teacherProfileSchema.index({ 'verification.status': 1 });
teacherProfileSchema.index({ isLive: 1 });
teacherProfileSchema.index({ fullName: 'text', bio: 'text' });

export const TeacherProfile = mongoose.model<ITeacherProfile>('TeacherProfile', teacherProfileSchema);