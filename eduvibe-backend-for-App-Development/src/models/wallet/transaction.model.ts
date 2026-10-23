import mongoose, { Schema, Document, Types } from 'mongoose';

export type TransactionType = "ADD_MONEY" | "TRANSFER" | "PAYMENT" | "REFUND" | "WITHDRAWAL";
export type TransactionStatus = 'pending' | 'success' | 'failed';

export interface ITransaction extends Document {
    amount: number;
    type: TransactionType;
    status: TransactionStatus;

    fromWalletId: Types.ObjectId;
    toWalletId: Types.ObjectId;

    referenceId: Types.ObjectId;
    createdAt: Date;
    updatedAt: Date;
}

const transactionSchema = new Schema<ITransaction>(
    {
        amount: { type: Number, required: true },
        type: { type: String, enum: ['ADD_MONEY', 'TRANSFER', 'PAYMENT', 'REFUND', 'WITHDRAWAL'], required: true },
        status: { type: String, enum: ['pending', 'success', 'failed'], default: 'pending' },
        fromWalletId: { type: Schema.Types.ObjectId, ref: 'Wallet', required: true },
        toWalletId: { type: Schema.Types.ObjectId, ref: 'Wallet', required: true },
        referenceId: { type: Schema.Types.ObjectId, ref: 'Payment' },
    },
    { timestamps: true }
);

export const Transaction = mongoose.model<ITransaction>('Transaction', transactionSchema);