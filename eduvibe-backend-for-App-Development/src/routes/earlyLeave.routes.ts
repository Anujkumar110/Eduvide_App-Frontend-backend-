import { Router } from 'express';
import { reportEarlyLeave, fetchEarlyLeaveReports } from '../controllers/earlyLeave.controller';
import { authenticate } from '../middleware/auth.middleware'; // apna existing auth middleware

const router = Router();

// Student → class early leave report submit karo
// POST /api/sessions/early-leave
router.post('/early-leave', authenticate, reportEarlyLeave);

// Teacher → apne class ke reports dekho
// GET /api/sessions/early-leave/:classId
router.get('/early-leave/:classId', authenticate, fetchEarlyLeaveReports);

export default router;