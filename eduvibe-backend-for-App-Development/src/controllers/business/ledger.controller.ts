import { Request, Response, NextFunction } from 'express';
import * as ledgerService from '../../services/business/ledger.service';
import { sendSuccess } from '../../utils/response';

export const getLedger = async (req: Request, res: Response, next: NextFunction) => {
    try {
        const ledger = await ledgerService.getLedger(req.user!.userId);
        sendSuccess(res, 'Ledger fetched', ledger);
    } catch (err) {
        next(err);
    }
};