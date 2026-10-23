import mongoose, { Schema, Document } from 'mongoose';
import { Role } from '../constants';

export type FcmToken = {
  token: string;
  deviceInfo: {
    deviceId: string;
    deviceType: 'android' | 'ios' | 'web';
  };
  createdAt: Date;
}

export interface IUser extends Document {
  email: string;
  passwordHash?: string;
  authProvider: 'local' | 'google' | 'firebase';
  fcmTokens?: FcmToken[];
  role: Role[];
  isActive: boolean;
  isBanned: boolean;
  banReason?: string;
  lastLoginAt?: Date;
  createdAt: Date;
  updatedAt: Date;
}

const fcmTokenSchema = new Schema<FcmToken>(
  {
    token: { type: String, required: true },
    deviceInfo: {
      deviceId: { type: String, required: true },
      deviceType: {
        type: String,
        enum: ['android', 'ios', 'web'],
        required: true,
      },
    },
    createdAt: { type: Date, default: Date.now },
  },
  { _id: false }
);

const userSchema = new Schema<IUser>(
  {
    email: { type: String, required: true, unique: true, lowercase: true, trim: true },
    passwordHash: { type: String, required: function (this: IUser) { return this.authProvider === 'local'; } },
    authProvider: { type: String, enum: ['local', 'google', 'firebase'], default: 'local' },
    fcmTokens: { type: [fcmTokenSchema], default: [] },
    role: { type: [String], enum: ['student', 'teacher', 'admin', 'moderator'], required: true },
    isActive: { type: Boolean, default: true },
    isBanned: { type: Boolean, default: false },
    banReason: { type: String },
    lastLoginAt: { type: Date },
  },
  { timestamps: true }
);

// ✅ email unique: true field mein hai, isliye yahan sirf extra indexes
userSchema.index({ role: 1 });
userSchema.index({ isBanned: 1 });

userSchema.set('toJSON', {
  transform: (_doc, ret) => {
    const { passwordHash: _pw, ...safe } = ret as unknown as Record<string, unknown>;
    return safe;
  },
});

export const User = mongoose.model<IUser>('User', userSchema);