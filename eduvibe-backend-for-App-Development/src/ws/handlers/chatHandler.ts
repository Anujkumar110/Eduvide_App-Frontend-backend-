import { connectionManager } from "../connectionManager";
import { roomManager } from "../roomManager";
import { Message } from "../../models/message.model";

export async function handleChat(
    type: string,
    senderId: string,
    payload: Record<string, unknown>
): Promise<void> {
    switch (type) {
        case "class-chat:send": {
            const { classId, content } = payload as { classId: string; content: string };
            const peerId = roomManager.getPeer(classId, senderId);

            const message = {
                senderId,
                content,
                createdAt: new Date().toISOString()
            };

            if (peerId) {
                connectionManager.send(peerId, {
                    type: "class-chat:message",
                    payload: message,
                });
            }

            connectionManager.send(senderId, {
                type: "class-chat:message:ack",
                payload: message,
            });
            break;
        }

        case "chat:send": {
            const { receiverId, content, conversationId } = payload as {
                receiverId: string;
                content: string;
                conversationId: string;
            };

            const msg = await Message.create({
                senderId,
                receiverId,
                content,
                conversationId,
                readAt: null,
            });

            connectionManager.send(receiverId, { type: "chat:message", payload: msg });
            connectionManager.send(senderId, { type: "chat:message:ack", payload: msg });
            break;
        }

        case "chat:typing": {
            const { receiverId, isTyping } = payload as {
                receiverId: string;
                isTyping: boolean;
            };
            connectionManager.send(receiverId, {
                type: "chat:typing",
                payload: { senderId, isTyping },
            });
            break;
        }

        case "chat:read": {
            const { conversationId, senderId: originalSender } = payload as {
                conversationId: string;
                senderId: string;
            };

            await Message.updateMany(
                { conversationId, receiverId: senderId, readAt: null },
                { readAt: new Date() }
            );

            connectionManager.send(originalSender, {
                type: "chat:read:ack",
                payload: { conversationId, readBy: senderId },
            });
            break;
        }
    }
}