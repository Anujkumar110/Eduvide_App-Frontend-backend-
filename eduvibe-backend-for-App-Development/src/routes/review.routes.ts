import { Router } from "express";
import * as reviewController from "../controllers/review.controller";
import { authenticate } from "../middleware/auth.middleware";

const router = Router();

// Student review submit kare
router.post(
    "/",
    authenticate,
    // requireRole("student"),  // temporarily disabled for debug
    reviewController.createReview
);

// Kya student ne pehle se review diya hai? (auth required)
router.get(
    "/check/:classId",
    authenticate,
    reviewController.checkReviewed
);

// Teacher ke saare reviews — public (profile page pe dikhega)
router.get("/teacher/:teacherId", reviewController.getTeacherReviews);

// Class ke saare reviews
router.get("/class/:classId", reviewController.getClassReviews);

export default router;