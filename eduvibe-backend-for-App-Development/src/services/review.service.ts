import { Review } from "../models/review.model";
import { AppError } from "../utils/errors";
import { StudentProfile } from "../models/studentProfile.model";
import { TeacherProfile } from "../models/teacherProfile.model";
import { Class } from "../models/class.model";
import { Enrollment } from "../models/enrollment.model";
import { Session } from "../models/session.model";
import mongoose from "mongoose";

// ── Create Review ─────────────────────────────────────────────────────────────
// classId ya sessionId dono accept karta hai — Android roomId (sessionId) bhi
export const createReview = async (
    userId: string,
    reviewData: { classId: string; rating: number; comment: string }
) => {
    const studentProfile = await StudentProfile.findOne({ userId });
    if (!studentProfile) throw new AppError("Student profile not found", 404);

    // Step 1: classId se class dhundo — agar nahi mila toh sessionId samajh ke session se classId nikalo
    let cls = await Class.findById(reviewData.classId).catch(() => null);
    let resolvedClassId = reviewData.classId;

    if (!cls) {
        // roomId == sessionId ho sakta hai Android se
        const session = await Session.findById(reviewData.classId).catch(() => null);
        if (session?.classId) {
            resolvedClassId = session.classId.toString();
            cls = await Class.findById(resolvedClassId);
        }
    }

    if (!cls) throw new AppError("Class not found", 404);

    const teacher = await TeacherProfile.findById(cls.teacherId);
    if (!teacher) throw new AppError("Teacher profile not found", 404);

    // Step 2: Enrollment check — ya toh enrolled ho ya session mein attended ho
    const enrollment = await Enrollment.findOne({
        studentId: studentProfile._id,
        classId: resolvedClassId,
    });

    if (!enrollment) {
        // Fallback: session mein attendedStudents mein hai?
        const sessionAttended = await Session.findOne({
            classId: new mongoose.Types.ObjectId(resolvedClassId),
            "attendedStudents.userId": new mongoose.Types.ObjectId(userId)
        });

        if (!sessionAttended) {
            // ✅ FIX: Missed session bhi allow karo — student enrolled tha but class miss ki
            const missedSession = await Session.findOne({
                classId: new mongoose.Types.ObjectId(resolvedClassId),
                status: "missed",
            });
            if (!missedSession) {
                throw new AppError(
                    "You must attend the class before submitting a review",
                    403
                );
            }
        }
    }

    // Step 3: Duplicate check
    const existingReview = await Review.findOne({
        studentId: studentProfile._id,
        classId: resolvedClassId,
    });
    if (existingReview) throw new AppError("Review already submitted for this class", 409);

    // Step 4: Review create karo
    const review = await Review.create({
        studentId: studentProfile._id,
        teacherId: teacher._id,
        classId: resolvedClassId,
        rating: reviewData.rating,
        comment: reviewData.comment,
    });

    // Step 5: Teacher rating update — correct weighted average
    const currentAvg = teacher.rating?.average ?? 0;
    const currentCount = teacher.rating?.count ?? 0;
    const newCount = currentCount + 1;
    const newAverage = (currentAvg * currentCount + reviewData.rating) / newCount;

    await TeacherProfile.findByIdAndUpdate(teacher._id, {
        $set: {
            "rating.average": Math.round(newAverage * 10) / 10, // 1 decimal
            "rating.count": newCount,
        },
    });

    return review;
};

// ── Get Teacher Reviews ───────────────────────────────────────────────────────
export const getTeacherReviews = async (teacherId: string) => {
    const [reviews, total] = await Promise.all([
        Review.find({ teacherId })
            .sort({ createdAt: -1 })
            .limit(50)
            .populate("studentId", "fullName profilePhoto")
            .populate("classId", "title"),
        Review.countDocuments({ teacherId }),
    ]);
    return { reviews, total };
};

// ── Get Class Reviews ─────────────────────────────────────────────────────────
export const getClassReviews = async (classId: string) => {
    const [reviews, total] = await Promise.all([
        Review.find({ classId })
            .sort({ createdAt: -1 })
            .populate("studentId", "fullName profilePhoto"),
        Review.countDocuments({ classId }),
    ]);
    return { reviews, total };
};

// ── Check if student already reviewed a class ─────────────────────────────────
// Android app use karega — completed session ke baad check karo
export const hasStudentReviewed = async (
    userId: string,
    classId: string
): Promise<boolean> => {
    const studentProfile = await StudentProfile.findOne({ userId });
    if (!studentProfile) return false;

    // classId ya sessionId dono try karo
    let resolvedClassId = classId;
    const cls = await Class.findById(classId).catch(() => null);
    if (!cls) {
        const session = await Session.findById(classId).catch(() => null);
        if (session?.classId) resolvedClassId = session.classId.toString();
    }

    const existing = await Review.findOne({
        studentId: studentProfile._id,
        classId: resolvedClassId,
    });
    return !!existing;
};