import { Router } from "express";
import * as walletController from "../../controllers/business/wallet.controller";
import { authenticate }      from "../../middleware/auth.middleware";

const router = Router();

// POST /wallet                    → create wallet manually
router.post('/',                    authenticate, walletController.createWallet);

// GET  /wallet                    → get wallet balance
router.get('/',                     authenticate, walletController.getWallet);

// GET  /wallet/transactions        → transaction history
router.get('/transactions',         authenticate, walletController.getTransactions);

// POST /wallet/create-topup-order → Step 1: Razorpay order for top-up
router.post('/create-topup-order',  authenticate, walletController.createTopUpOrder);

// POST /wallet/topup               → Step 2: verify + credit wallet
router.post('/topup',               authenticate, walletController.topUpWallet);

// POST /wallet/pay-teacher         → student pays teacher from wallet
router.post('/pay-teacher',         authenticate, walletController.payTeacher);

export default router;