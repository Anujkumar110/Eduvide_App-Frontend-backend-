import { Request, Response } from "express";
import { DeviceToken } from "../models/deviceToken.model";

export const saveToken = async (req: Request, res: Response): Promise<void> => {
    try {
        // ✅ userId auth middleware se lo (req.user), body se nahi
        const userId = (req as any).user?.userId;
        const { token } = req.body;

        if (!userId || !token) {
            res.status(400).json({ success: false, message: "userId and token are required" });
            return;
        }

        await DeviceToken.updateOne(
            { userId },
            { $set: { userId, token, updatedAt: new Date() } },
            { upsert: true }
        );

        res.status(200).json({ success: true, message: "FCM token saved" });
    } catch (error) {
        console.error("Error saving device token:", error);
        res.status(500).json({ success: false, message: "Internal server error" });
    }
};