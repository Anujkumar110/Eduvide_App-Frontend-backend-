import { WebSocket } from "ws";
import { IncomingMessage } from "http";
import jwt from "jsonwebtoken";
import { parse } from "url";
import { env } from "../config/env";

export async function authenticateWS(
    ws: WebSocket,
    req: IncomingMessage
): Promise<string | null> {
    try {
        const url = new URL(req.url!, "http://localhost")
        let token = url.searchParams.get("token")

        if (!token) throw new Error("No token");

        if (token.startsWith("Bearer ")) {
            token = token.slice(7);
        }

        const payload = jwt.verify(token, env.JWT_SECRET) as { userId: string };
        console.log("Payload", payload)
        return payload.userId;
    } catch (error: any) {
        console.log("Auth Failed", error.message)
        ws.close(4001, "Unauthorized");
        return null;
    }
}