import { Types } from "mongoose";
import { Transaction } from "../../models/wallet/transaction.model";
import { Wallet } from "../../models/wallet/wallet.model";
import { Ledger } from "../../models/wallet/ledger.model";
import { AppError } from "../../utils/errors";
import { createWallet } from "./wallet.service";
import { sendNotification } from "../notification.service";

// ─────────────────────────────────────────────────────────────────────────────
// Core transaction creator
// FIX: Ledger.insertMany → individual Ledger.create calls
//      insertMany internally uses MongoDB transactions on some drivers
//      which require a replica set — local dev mein fail hota hai
// ─────────────────────────────────────────────────────────────────────────────
export const createTransaction = async (
    fromWalletId: string,
    toWalletId:   string,
    amount:       number,
    type:         string
) => {
    if (amount <= 0)                        throw new AppError('Invalid amount', 400);
    if (fromWalletId === toWalletId)        throw new AppError('From and to wallet cannot be same', 400);

    const fromWallet = await Wallet.findById(fromWalletId);
    if (!fromWallet)                        throw new AppError('From wallet not found', 404);

    const toWallet = await Wallet.findById(toWalletId);
    if (!toWallet)                          throw new AppError('To wallet not found', 404);

    if (type === 'TRANSFER' || type === 'PAYMENT') {
        if (fromWallet.balance < amount)    throw new AppError('Insufficient balance', 400);
    }

    const transaction = await Transaction.create({ fromWalletId, toWalletId, amount, type });

    // ✅ FIX: insertMany → individual create calls (no replica set needed)
    await Ledger.create({ walletId: fromWalletId, amount: -amount, type: 'debit',  transactionId: transaction._id });
    await Ledger.create({ walletId: toWalletId,   amount:  amount, type: 'credit', transactionId: transaction._id });

    const updatedFromWallet = await Wallet.findOneAndUpdate(
        { _id: fromWalletId, balance: { $gte: amount } },
        { $inc: { balance: -amount } },
        { new: true }
    );
    const updatedToWallet = await Wallet.findByIdAndUpdate(
        toWalletId,
        { $inc: { balance: amount } },
        { new: true }
    );

    if (!updatedFromWallet || !updatedToWallet) throw new AppError('Transaction failed', 500);

    return { transaction, updatedFromWallet, updatedToWallet };
};

// ─────────────────────────────────────────────────────────────────────────────
// Get transactions by walletId
// ─────────────────────────────────────────────────────────────────────────────
export const getTransaction = async (walletId: string) => {
    const wallet = await Wallet.findById(walletId);
    if (!wallet) throw new AppError('Wallet not found', 404);

    const transaction = await Transaction.find({
        $or: [{ fromWalletId: walletId }, { toWalletId: walletId }],
    }).sort({ createdAt: -1 });

    return transaction;
};

// ─────────────────────────────────────────────────────────────────────────────
// Get transactions by userId
// ─────────────────────────────────────────────────────────────────────────────
export const getTransactionsByUserId = async (userId: string) => {
    if (!Types.ObjectId.isValid(userId)) throw new AppError(`Invalid userId: ${userId}`, 400);

    const wallet = await Wallet.findOne({ userId: new Types.ObjectId(userId) });
    if (!wallet) throw new AppError('Wallet not found. Please logout and login again.', 404);

    console.log(`[getTransactionsByUserId] walletId: ${wallet._id} | userId: ${userId}`);

    const transactions = await Transaction.find({
        $or: [
            { fromWalletId: wallet._id },
            { toWalletId:   wallet._id },
        ],
    }).sort({ createdAt: -1 });

    console.log(`[getTransactionsByUserId] found: ${transactions.length} transactions`);
    return transactions;
};

// ─────────────────────────────────────────────────────────────────────────────
// Student pays teacher from wallet
// ─────────────────────────────────────────────────────────────────────────────
export const payTeacher = async ({
    studentUserId,
    teacherUserId,
    amount,
    classId,
    slotId,
}: {
    studentUserId: string;
    teacherUserId: string;
    amount:        number;
    classId?:      string;
    slotId?:       string;
}) => {
    if (!amount || amount <= 0) throw new AppError('Invalid amount', 400);

    const studentWallet = await createWallet(studentUserId);
    const teacherWallet = await createWallet(teacherUserId);

    if (studentWallet.balance < amount) {
        throw new AppError(
            `Insufficient balance. Available: ₹${studentWallet.balance.toFixed(2)}`,
            400
        );
    }

    const result = await createTransaction(
        studentWallet._id.toString(),
        teacherWallet._id.toString(),
        amount,
        'PAYMENT'
    );

    // Student notification — paisa debit hua
    await sendNotification(
        studentUserId,
        'Payment Debited',
        `₹${amount} aapke wallet se teacher ko pay ho gaye.`,
        {
            type:       'WALLET_PAYMENT_DEBITED',
            amount:     String(amount),
            newBalance: String(result.updatedFromWallet?.balance ?? 0),
        }
    );

    // Teacher notification — paisa credit hua
    await sendNotification(
        teacherUserId,
        'Payment Received',
        `₹${amount} ek student se aapke wallet mein credit ho gaye.`,
        {
            type:       'WALLET_PAYMENT_CREDITED',
            amount:     String(amount),
            newBalance: String(result.updatedToWallet?.balance ?? 0),
        }
    );

    return {
        studentWallet: result.updatedFromWallet,
        teacherWallet: result.updatedToWallet,
        transaction:   result.transaction,
    };
};