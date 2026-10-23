import mongoose, { Schema, Document, Types } from 'mongoose';

export type LedgerEntryType = "credit" | "debit";

export interface ILedger extends Document {
    walletId: Types.ObjectId;
    amount: number;
    type: LedgerEntryType;
    transactionId: Types.ObjectId;
    createdAt: Date;
    updatedAt: Date;
}

const ledgerSchema = new Schema<ILedger>(
    {
        walletId: { type: Schema.Types.ObjectId, ref: 'Wallet', required: true },
        amount: { type: Number, required: true },
        type: { type: String, enum: ['credit', 'debit'], required: true },
        transactionId: { type: Schema.Types.ObjectId, ref: 'Transaction', required: true },
    },
    { timestamps: true }
);

export const Ledger = mongoose.model<ILedger>('Ledger', ledgerSchema);