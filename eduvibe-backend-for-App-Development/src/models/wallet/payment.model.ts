import mongoose, { Schema, Document, Types } from 'mongoose';

export interface IPayment extends Document {
  userId: Types.ObjectId;
  walletId: Types.ObjectId;
  amount: number;
  razorpayOrderId: string;
  razorpayPaymentId?: string;
  razorpaySignature?: string;
  status: 'pending' | 'success' | 'failed';
  failureReason?: string;
  receipt: string;
  createdAt: Date;
  updatedAt: Date;
}

const paymentSchema = new Schema<IPayment>(
  {
    userId: {
      type: Schema.Types.ObjectId,
      ref: 'User',
      required: true,
    },
    walletId: {
      type: Schema.Types.ObjectId,
      ref: 'Wallet',
      required: true,
    },
    amount: {
      type: Number,
      required: true,
      min: 1,
    },
    razorpayOrderId: {
      type: String,
      required: true,
      unique: true,
    },
    razorpayPaymentId: {
      type: String,
    },
    razorpaySignature: {
      type: String,
    },
    status: {
      type: String,
      enum: ['pending', 'success', 'failed'],
      default: 'pending',
    },
    failureReason: {
      type: String,
    },
    receipt: {
      type: String,
      required: true,
      unique: true,
    },
  },
  { timestamps: true }
);

paymentSchema.index({ userId: 1 });
paymentSchema.index({ walletId: 1 });
paymentSchema.index({ status: 1 });
paymentSchema.index({ createdAt: -1 });

export const Payment = mongoose.model<IPayment>('Payment', paymentSchema);