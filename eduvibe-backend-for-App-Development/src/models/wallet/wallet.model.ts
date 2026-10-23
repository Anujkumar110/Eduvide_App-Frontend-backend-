import mongoose, { Schema, Document } from "mongoose";

export interface IWallet extends Document {
    userId: mongoose.Types.ObjectId;
    balance: number;
    createdAt: Date;
    updatedAt: Date;
}

const WalletSchema = new Schema<IWallet>(
    {
        userId: {
            type: Schema.Types.ObjectId,
            ref: 'User',
            required: true,
            unique: true
        },
        balance: {
            type: Number,
            required: true,
            default: 0
        }
    },
    { timestamps: true }
);

export const Wallet = mongoose.model<IWallet>('Wallet', WalletSchema);