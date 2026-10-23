import { WebSocket } from "ws";
import { handleChat } from "./handlers/chatHandler";
import { handleSignaling } from "./handlers/signalingHandler";

interface WSMessage {
    type: string;
    payload: Record<string, unknown>;
}

export async function handleMessage(ws: WebSocket, userId: string, raw: string): Promise<void> {
    try {
        const msg: WSMessage = JSON.parse(raw);
        const { type, payload } = msg;

        if (type.startsWith("chat:") || type.startsWith("class-chat:")) {
            handleChat(type, userId, payload);
        } else if (
            type.startsWith("call:") ||
            type.startsWith("webrtc:") ||
            type.startsWith("room:") ||
            type.startsWith("session:")
        ) {
            // ✅ Await async signalingHandler for session lifecycle events
            await handleSignaling(type, userId, payload);
        } else {
            ws.send(JSON.stringify({ type: "error", payload: { message: "Unknown type" } }));
        }
    } catch {
        ws.send(JSON.stringify({ type: "error", payload: { message: "Invalid JSON" } }));
    }
}