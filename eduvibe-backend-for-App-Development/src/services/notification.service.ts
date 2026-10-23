import { messaging } from "../config/firebase";
import { DeviceToken } from "../models/deviceToken.model";
import { MulticastMessage } from "firebase-admin/messaging";

export const sendNotification = async (
    userId: string,
    title: string,
    body: string,
    metadata?: Record<string, any>
) => {
    try {
        const tokensData = await DeviceToken.find({ userId });

        if (!tokensData || tokensData.length === 0) {
            return;
        }

        const tokens = tokensData.map(t => t.token);
        console.log("Tokens:", tokens);

        const dataPayload: Record<string, string> = metadata
            ? Object.fromEntries(
                Object.entries(metadata).map(([k, v]) => [k, String(v)])
            )
            : {};

        const message: MulticastMessage = {
            tokens: tokens,

            data: {
                title: title,
                body: body,
                ...dataPayload,
            },

            android: {
                priority: "high",
            },
        };

        const response = await messaging.sendMulticast(message);

        console.log("FCM Response:", response);
        if (response.failureCount > 0) {
            const failedTokens: string[] = [];

            response.responses.forEach((resp: any, idx: any) => {
                if (!resp.success) {
                    console.log("Failed token:", tokens[idx], resp.error);
                    failedTokens.push(tokens[idx]);
                }
            });

            if (failedTokens.length > 0) {
                await DeviceToken.deleteMany({ token: { $in: failedTokens } });
            }
        }

    } catch (error) {
        console.error("FCM Send Error:", error);
    }
};