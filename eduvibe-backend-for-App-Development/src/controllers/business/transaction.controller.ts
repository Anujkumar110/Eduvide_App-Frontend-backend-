import { Request, Response, NextFunction } from 'express';
import * as transactionService from '../../services/business/transaction.service';
import { sendSuccess } from '../../utils/response';

export const createTransaction = async (req: Request, res: Response, next: NextFunction) => {
    try {
        const transaction = await transactionService.createTransaction(req.body.fromWalletId, req.body.toWalletId, req.body.amount, req.body.type);
        sendSuccess(res, 'Transaction created', transaction);
    } catch (err) {
        next(err);
    }
};

export const getTransaction = async (req: Request, res: Response, next: NextFunction) => {
    try {
        const transaction = await transactionService.getTransaction(req.user!.userId);
        sendSuccess(res, 'Transaction fetched', transaction);
    } catch (err) {
        next(err);
    }
};