import { Router } from 'express';
import * as slotController from '../controllers/slot.controller';
import { authenticate } from '../middleware/auth.middleware';
import { requireRole } from '../middleware/rbac.middleware';

const router = Router();

// ─── TEACHER LIVE STATUS ──────────────────────────────────────────────────────
// PUT  /api/v1/teachers/live          → toggle live on/off
// GET  /api/v1/teachers/live          → get all live teachers (public)
router.put(
  '/teachers/live',
  authenticate,
  requireRole('teacher'),
  slotController.toggleLiveStatus
);

router.get(
  '/teachers/live',
  slotController.getLiveTeachers        // no auth needed — public screen
);

// ─── TEACHER: SLOT MANAGEMENT ─────────────────────────────────────────────────
// POST /api/v1/teachers/slots/generate → generate slots for a date+time range
// GET  /api/v1/teachers/my-slots       → teacher sees all their slots
router.post(
  '/teachers/slots/generate',
  authenticate,
  requireRole('teacher'),
  slotController.generateSlots
);

router.get(
  '/teachers/:id',
  authenticate,
  
  slotController.getTeacherSlotsList
);

// ─── PUBLIC: GET TEACHER SLOTS ────────────────────────────────────────────────
// GET /api/v1/teachers/:id/slots?date=2026-04-28
// :id = teacher's userId
// NOTE: keep this BELOW /teachers/my-slots and /teachers/live
//       so express doesn't treat "my-slots" as :id param
router.get(
  '/teachers/:id/slots',
  slotController.getTeacherSlots
);

// ─── STUDENT: BOOK / CANCEL ───────────────────────────────────────────────────
// POST /api/v1/slots/:id/book         → book a slot (wallet deducted)
// POST /api/v1/slots/:id/cancel       → cancel + refund
router.post(
  '/slots/:id/book',
  authenticate,
  requireRole('student'),
  slotController.bookSlot
);

router.post(
  '/slots/:id/cancel',
  authenticate,
  requireRole('student'),
  slotController.cancelBooking
);

// ─── TEACHER: DELETE SLOT ─────────────────────────────────────────────────────
// DELETE /api/v1/slots/:id
// Sirf 'available' slots delete ho sakte hain
// Booked slots delete nahi honge (student ka paisa involved)
router.delete(
  '/slots/:id',
  authenticate,
  requireRole('teacher'),
  slotController.deleteSlot
);

// ─── STUDENT: MY BOOKINGS ─────────────────────────────────────────────────────
// GET /api/v1/students/my-slots
router.get(
  '/students/my-slots',
  authenticate,
  requireRole('student'),
  slotController.getStudentSlots
);

// ─── TEACHER: LINK SESSION ────────────────────────────────────────────────────
// POST /api/v1/slots/:id/link-session
router.post(
  '/slots/:id/link-session',
  authenticate,
  requireRole('teacher'),
  slotController.linkSession
);

export default router;