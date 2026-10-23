import { Request, Response, NextFunction } from 'express';
import * as walletService      from '../../services/business/wallet.service';
import * as transactionService from '../../services/business/transaction.service';
import * as paymentService     from '../../services/business/payment.service';
import { TeacherProfile }      from '../../models/teacherProfile.model';
import { Slot }                from '../../models/slot.model';
import { sendSuccess }         from '../../utils/response';
import { AppError }            from '../../utils/errors';
import { enrollAfterSlotBooking } from '../../services/enrollment.service';
import { bookSlot }            from '../../services/slot.service';

export const createWallet = async (req: Request, res: Response, next: NextFunction) => {
    try {
        const wallet = await walletService.createWallet(req.user!.userId);
        sendSuccess(res, 'Wallet created', wallet);
    } catch (err) { next(err); }
};

export const getWallet = async (req: Request, res: Response, next: NextFunction) => {
    try {
        const userId = req.user!.userId;
        console.log(`[getWallet] userId from token: ${userId}`);
        const wallet = await walletService.getWallet(userId);
        sendSuccess(res, 'Wallet fetched', {
            _id:     wallet._id.toString(),
            id:      wallet._id.toString(),
            balance: wallet.balance,
            userId:  wallet.userId.toString(),
        });
    } catch (err) { next(err); }
};

export const getTransactions = async (req: Request, res: Response, next: NextFunction) => {
    try {
        const userId = req.user!.userId;
        console.log(`[getTransactions] userId from token: ${userId}`);
        const transactions = await transactionService.getTransactionsByUserId(userId);
        const mapped = transactions.map(tx => {
            const obj = tx.toObject();
            return {
                ...obj,
                _id:          obj._id.toString(),
                fromWalletId: obj.fromWalletId.toString(),
                toWalletId:   obj.toWalletId.toString(),
                referenceId:  obj.referenceId?.toString() ?? null,
            };
        });
        console.log(`[getTransactions] returning: ${mapped.length} tx`);
        sendSuccess(res, 'Transactions fetched', mapped);
    } catch (err) { next(err); }
};

export const createTopUpOrder = async (req: Request, res: Response, next: NextFunction) => {
    try {
        const { amount } = req.body;
        if (!amount || Number(amount) <= 0) throw new AppError('Invalid amount', 400);
        const result = await paymentService.createOrder({
            amount: Number(amount),
            userId: req.user!.userId,
        });
        sendSuccess(res, 'Top-up order created', {
            orderId:  result.orderId,
            amount:   result.amount,
            currency: 'INR',
            keyId:    paymentService.getRazorpayKeyId(),
            isDummy:  result.isDummy,
        });
    } catch (err) { next(err); }
};

export const topUpWallet = async (req: Request, res: Response, next: NextFunction) => {
    try {
        const {
            razorpay_order_id:   razorpayOrderId,
            razorpay_payment_id: razorpayPaymentId,
            razorpay_signature:  razorpaySignature,
        } = req.body;
        await paymentService.handlePaymentSuccess(razorpayOrderId, razorpayPaymentId, razorpaySignature);
        const wallet = await walletService.getWallet(req.user!.userId);
        sendSuccess(res, 'Wallet credited successfully', {
            _id:     wallet._id.toString(),
            id:      wallet._id.toString(),
            balance: wallet.balance,
            userId:  wallet.userId.toString(),
        });
    } catch (err) { next(err); }
};

export const payTeacher = async (req: Request, res: Response, next: NextFunction) => {
    try {
        const {
            teacherId,
            amount,
            classId,
            slotId,
            studentName,   // ✅ NEW
            studentEmail,  // ✅ NEW
            studentPhone,  // ✅ NEW
        } = req.body;

        if (!teacherId)                    throw new AppError('teacherId is required', 400);
        if (!amount || Number(amount) <= 0) throw new AppError('Invalid amount', 400);

        console.log(`[payTeacher] teacherId received: ${teacherId}`);
        console.log(`[payTeacher] full body: ${JSON.stringify(req.body)}`);

        if (slotId) {
            const slot = await Slot.findById(slotId).lean();
            if (!slot) throw new AppError('Slot not found', 404);
            if (slot.status !== 'available') throw new AppError('Slot is already booked. Please select another slot.', 409);
            console.log(`[payTeacher] ✅ Slot ${slotId} is available — proceeding with payment`);
        }

        const teacher = await TeacherProfile.findOne({ userId: teacherId }).lean();
        console.log(`[payTeacher] teacher found: ${teacher ? teacher._id : 'NOT FOUND'}`);
        if (!teacher) throw new AppError('Teacher not found', 404);

        const result = await transactionService.payTeacher({
            studentUserId: req.user!.userId,
            teacherUserId: teacher.userId.toString(),
            amount:        Number(amount),
            classId,
            slotId,
        });

        if (slotId) {
            try {
                // ✅ FIX: studentName, studentEmail, studentPhone ab pass ho raha hai
                await bookSlot(
                    req.user!.userId,
                    slotId,
                    studentName,
                    studentEmail,
                    studentPhone
                );
                console.log(`[payTeacher] ✅ Slot ${slotId} booked for student ${req.user!.userId} (name: ${studentName})`);
            } catch (bookErr: any) {
                console.warn(`[payTeacher] ⚠️ bookSlot warning: ${bookErr.message}`);
            }
        }

        await enrollAfterSlotBooking(req.user!.userId, teacher._id.toString(), classId ?? null);
        console.log(`[payTeacher] ✅ Enrollment triggered for student ${req.user!.userId}`);

        const paidAmount = Number(amount);
        const newBalance = result.studentWallet!.balance;

        sendSuccess(res, `₹${paidAmount} paid to teacher successfully`, {
            _id:                  result.studentWallet!._id.toString(),
            id:                   result.studentWallet!._id.toString(),
            balance:              newBalance,
            walletDeductedInRs:   paidAmount,
            newWalletBalanceInRs: newBalance,
        });
    } catch (err) { next(err); }
};