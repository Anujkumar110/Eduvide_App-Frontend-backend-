import mongoose, { Schema, Document, Types } from 'mongoose';

// ─── SLOT MODEL ───────────────────────────────────────────────────────────────
export interface ISlot extends Document {
  teacherId: Types.ObjectId;
  date: Date;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  priceInPaise: number;
  status: 'available' | 'booked' | 'completed' | 'missed' | 'cancelled';
  bookedBy?: Types.ObjectId;
  bookingId?: Types.ObjectId;
  paymentId?: Types.ObjectId;
  classId?: Types.ObjectId;
  studentName?: string;
  studentEmail?: string;
  studentPhone?: string;
  createdAt: Date;
  updatedAt: Date;
}

const slotSchema = new Schema<ISlot>(
  {
    teacherId:       { type: Schema.Types.ObjectId, ref: 'TeacherProfile', required: true },
    date:            { type: Date, required: true },
    startTime:       { type: String, required: true },
    endTime:         { type: String, required: true },
    durationMinutes: { type: Number, enum: [30, 60], default: 60 },
    priceInPaise:    { type: Number, required: true, default: 0 },
    status: {
      type:    String,
      enum:    ['available', 'booked', 'completed', 'missed', 'cancelled'],
      default: 'available',
    },
    bookedBy:     { type: Schema.Types.ObjectId, ref: 'StudentProfile' },
    bookingId:    { type: Schema.Types.ObjectId, ref: 'Booking' },
    paymentId:    { type: Schema.Types.ObjectId, ref: 'Payment' },
    classId:      { type: Schema.Types.ObjectId, ref: 'Class' },
    studentName:  { type: String },
    studentEmail: { type: String },
    studentPhone: { type: String },
  },
  { timestamps: true }
);

slotSchema.index({ teacherId: 1, date: 1, startTime: 1 }, { unique: true });
slotSchema.index({ teacherId: 1, status: 1 });
slotSchema.index({ date: 1, status: 1 });
slotSchema.index({ bookedBy: 1 });

export const Slot =
  (mongoose.models.Slot as mongoose.Model<ISlot>) ||
  mongoose.model<ISlot>('Slot', slotSchema);


// ─── BOOKING MODEL ────────────────────────────────────────────────────────────
export interface IBooking extends Document {
  slotId: Types.ObjectId;
  studentId: Types.ObjectId;
  teacherId: Types.ObjectId;
  amountInPaise: number;
  status: 'upcoming' | 'ongoing' | 'completed' | 'missed' | 'cancelled';
  cancelledBy?: 'student' | 'teacher';
  refundAmountInPaise: number;
  createdAt: Date;
  updatedAt: Date;
}

const bookingSchema = new Schema<IBooking>(
  {
    slotId:    { type: Schema.Types.ObjectId, ref: 'Slot',           required: true },
    studentId: { type: Schema.Types.ObjectId, ref: 'StudentProfile', required: true },
    teacherId: { type: Schema.Types.ObjectId, ref: 'TeacherProfile', required: true },
    amountInPaise: { type: Number, required: true },
    status: {
      type:    String,
      enum:    ['upcoming', 'ongoing', 'completed', 'missed', 'cancelled'],
      default: 'upcoming',
    },
    cancelledBy:         { type: String, enum: ['student', 'teacher'] },
    refundAmountInPaise: { type: Number, default: 0 },
  },
  { timestamps: true }
);

bookingSchema.index({ studentId: 1, status: 1 });
bookingSchema.index({ teacherId: 1, status: 1 });

export const Booking =
  (mongoose.models.Booking as mongoose.Model<IBooking>) ||
  mongoose.model<IBooking>('Booking', bookingSchema);


// ─── WALLET TRANSACTION MODEL ─────────────────────────────────────────────────
export interface IWalletTransaction extends Document {
  userId: Types.ObjectId;
  bookingId?: Types.ObjectId;
  amountInPaise: number;
  type: 'booking_debit' | 'booking_credit' | 'refund' | 'recharge' | 'withdrawal';
  description: string;
  balanceAfterInPaise: number;
  createdAt: Date;
}

const walletTransactionSchema = new Schema<IWalletTransaction>(
  {
    userId:    { type: Schema.Types.ObjectId, required: true },
    bookingId: { type: Schema.Types.ObjectId, ref: 'Booking' },
    amountInPaise: { type: Number, required: true },
    type: {
      type:     String,
      enum:     ['booking_debit', 'booking_credit', 'refund', 'recharge', 'withdrawal'],
      required: true,
    },
    description:         { type: String, required: true },
    balanceAfterInPaise: { type: Number, required: true },
  },
  { timestamps: true }
);

walletTransactionSchema.index({ userId: 1, createdAt: -1 });

export const WalletTransaction =
  (mongoose.models.WalletTransaction as mongoose.Model<IWalletTransaction>) ||
  mongoose.model<IWalletTransaction>('WalletTransaction', walletTransactionSchema);