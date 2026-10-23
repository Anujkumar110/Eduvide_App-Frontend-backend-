import { Ledger } from "../../models/wallet/ledger.model";
import { Wallet } from "../../models/wallet/wallet.model";
import { AppError } from "../../utils/errors";

export const createLedger = async (walletId: string, amount: number, type: string) => {
    const wallet = await Wallet.findById(walletId);
    if (!wallet) throw new AppError('Wallet not found', 404);
    const ledger = await Ledger.create({ walletId, amount, type });
    return ledger;
};

export const getLedger = async (userId: string) => {
    const wallet = await Wallet.findOne({ userId });
    if (!wallet) throw new AppError('Wallet not found', 404);
    const ledger = await Ledger.find({ walletId: wallet._id });
    return ledger;
};