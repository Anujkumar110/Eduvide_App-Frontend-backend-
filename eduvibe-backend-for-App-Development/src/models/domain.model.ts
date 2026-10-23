import mongoose, { Schema, Document, Types } from 'mongoose';

export interface IDomain extends Document {
  name: string;
  description: string;
  parentId?: Types.ObjectId | null;
  pricing: {
    baseRate: number;
    currency: string;
  };
  isActive: boolean;
  createdAt: Date;
  updatedAt: Date;
}

const domainSchema = new Schema<IDomain>(
  {
    name: { type: String, required: true, unique: true, trim: true },
    description: { type: String, required: true, trim: true },
    parentId: { type: Schema.Types.ObjectId, ref: 'Domain', default: null },
    pricing: {
      baseRate: { type: Number, required: true, default: 0 },
      currency: { type: String, default: 'INR' },
    },
    isActive: { type: Boolean, default: true },
  },
  { timestamps: true }
);

// ✅ name unique: true field mein hai, isliye schema.index() hata diya
domainSchema.index({ parentId: 1 });
domainSchema.index({ isActive: 1 });

export const Domain = mongoose.model<IDomain>('Domain', domainSchema);