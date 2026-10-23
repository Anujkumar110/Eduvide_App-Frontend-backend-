import { Types } from "mongoose";
import { Wallet } from "../../models/wallet/wallet.model";
import { Transaction } from "../../models/wallet/transaction.model";
import { AppError } from "../../utils/errors";

// ─────────────────────────────────────────────────────────────────────────────
// Helper — string → ObjectId with validation
// ─────────────────────────────────────────────────────────────────────────────
const toObjectId = (id: string, label = "userId"): Types.ObjectId => {
    if (!Types.ObjectId.isValid(id)) {
        throw new AppError(`Invalid ${label}: "${id}"`, 400);
    }
    return new Types.ObjectId(id);
};

// ─────────────────────────────────────────────────────────────────────────────
// Create wallet — idempotent
// ─────────────────────────────────────────────────────────────────────────────
export const createWallet = async (userId: string) => {
    const oid = toObjectId(userId);

    // FIX: query with ObjectId, not raw string — string match fails silently
    const existingWallet = await Wallet.findOne({ userId: oid });
    if (existingWallet) {
        console.log(`[createWallet] Existing wallet found — _id: ${existingWallet._id} | userId: ${oid}`);
        return existingWallet;
    }

    const wallet = await Wallet.create({ userId: oid });
    console.log(`[createWallet] New wallet created — _id: ${wallet._id} | userId: ${oid}`);
    return wallet;
};

// ─────────────────────────────────────────────────────────────────────────────
// Get wallet — returns existing wallet, NEVER silently creates a new one
//
// ROOT CAUSE of your bug:
//   Old code called createWallet(userId) which did findOne({ userId: "string" })
//   MongoDB stores userId as ObjectId → string match returns null → new phantom
//   wallet gets created with balance: 0, different _id from the real one.
//
// FIX:
//   Query with ObjectId cast. If genuinely missing, throw 404 so you know
//   immediately instead of silently getting a ghost wallet.
// ─────────────────────────────────────────────────────────────────────────────
export const getWallet = async (userId: string) => {
    const oid = toObjectId(userId);

    console.log(`[getWallet] Querying wallet for userId: ${oid}`);

    const wallet = await Wallet.findOne({ userId: oid }).lean();

    if (!wallet) {
        // This error means: JWT userId does NOT match any wallet's userId in DB.
        // → Teacher likely has a stale token. Ask them to logout + login again.
        console.error(
            `[getWallet] ❌ No wallet found for userId: ${oid}. ` +
            `Stale JWT token? Compare this ID to wallet.userId in MongoDB.`
        );
        throw new AppError(`Wallet not found for user ${oid}. Please logout and login again.`, 404);
    }

    console.log(`[getWallet] ✅ Found wallet — _id: ${wallet._id} | balance: ${wallet.balance}`);
    return wallet;
};

// ─────────────────────────────────────────────────────────────────────────────
// Transfer — student wallet se teacher wallet mein paise bhejo
// ─────────────────────────────────────────────────────────────────────────────
export const transferFromStudentToTeacher = async (
    studentUserId: string,
    teacherUserId: string,
    amount: number,
    referenceId: string // bookingId
) => {
    if (!amount || amount <= 0) throw new AppError("Invalid amount", 400);

    const studentOid = toObjectId(studentUserId, "studentUserId");
    const teacherOid = toObjectId(teacherUserId, "teacherUserId");

    // FIX: both queries use ObjectId — no silent string-vs-ObjectId mismatch
    const [studentWallet, teacherWallet] = await Promise.all([
        Wallet.findOne({ userId: studentOid }),
        Wallet.findOne({ userId: teacherOid }),
    ]);

    if (!studentWallet) throw new AppError(`Student wallet not found for userId: ${studentOid}`, 404);

    // Teacher wallet still auto-created here (acceptable at booking time)
    const resolvedTeacherWallet = teacherWallet ?? await Wallet.create({ userId: teacherOid });

    if (studentWallet.balance < amount) {
        throw new AppError(
            `Insufficient balance. Has: ${studentWallet.balance}, needs: ${amount}`,
            400
        );
    }

    // Atomic-style debit + credit
    const [updatedStudentWallet, updatedTeacherWallet] = await Promise.all([
        Wallet.findByIdAndUpdate(
            studentWallet._id,
            { $inc: { balance: -amount } },
            { new: true }
        ),
        Wallet.findByIdAndUpdate(
            resolvedTeacherWallet._id,
            { $inc: { balance: amount } },
            { new: true }
        ),
    ]);

    // Transaction record
    await Transaction.create({
        amount,
        type: "PAYMENT",
        status: "success",
        fromWalletId: studentWallet._id,
        toWalletId: resolvedTeacherWallet._id,
        referenceId,
    });

    console.log(
        `[transfer] ✅ Student ${studentOid} → Teacher ${teacherOid} | amount: ${amount} | ` +
        `Student balance: ${updatedStudentWallet!.balance} | Teacher balance: ${updatedTeacherWallet!.balance}`
    );

    return {
        studentNewBalance: updatedStudentWallet!.balance,
        teacherNewBalance: updatedTeacherWallet!.balance,
    };
};