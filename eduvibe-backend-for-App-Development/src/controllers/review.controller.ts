import { Request, Response, NextFunction } from "express";
import * as reviewService from "../services/review.service";
import { AppError } from "../utils/errors";

// ── POST /reviews ─────────────────────────────────────────────────────────────
export const createReview = async (
    req: Request,
    res: Response,
    next: NextFunction
) => {
    try {
        console.log("REVIEW BODY:", JSON.stringify(req.body));
        console.log("REVIEW HEADERS content-type:", req.headers["content-type"]);

        const userId = (req as any).user?.userId || (req as any).user?.id;
        const { classId, rating, comment } = req.body;

        if (!classId || !rating) {
            throw new AppError("classId and rating are required", 400);
        }
        if (rating < 1 || rating > 5) {
            throw new AppError("Rating must be between 1 and 5", 400);
        }

        const review = await reviewService.createReview(userId, {
            classId,
            rating: Number(rating),
            comment: comment ?? "",
        });

        res.status(201).json({
            success: true,
            message: "Review submitted successfully",
            data: review,
        });
    } catch (err) {
        next(err);
    }
};

// ── GET /reviews/teacher/:teacherId ──────────────────────────────────────────
export const getTeacherReviews = async (
    req: Request,
    res: Response,
    next: NextFunction
) => {
    try {
        const { teacherId } = req.params;
        const result = await reviewService.getTeacherReviews(teacherId as string);
        res.json({ success: true, data: result });
    } catch (err) {
        next(err);
    }
};

// ── GET /reviews/class/:classId ───────────────────────────────────────────────
export const getClassReviews = async (
    req: Request,
    res: Response,
    next: NextFunction
) => {
    try {
        const { classId } = req.params;
        const result = await reviewService.getClassReviews(classId as string);
        res.json({ success: true, data: result });
    } catch (err) {
        next(err);
    }
};

// ── GET /reviews/check/:classId ───────────────────────────────────────────────
export const checkReviewed = async (
    req: Request,
    res: Response,
    next: NextFunction
) => {
    try {
        const userId = (req as any).user?.userId || (req as any).user?.id;
        const { classId } = req.params;
        const hasReviewed = await reviewService.hasStudentReviewed(userId, classId as string);
        res.json({ success: true, data: { hasReviewed } });
    } catch (err) {
        next(err);
    }
};