import { WebSocket } from "ws";
import { roomManager } from "./roomManager";

type UserId = string;

class ConnectionManager {
    private map = new Map<UserId, WebSocket>();

    addConnection(userId: UserId, ws: WebSocket): void {
        const existing = this.map.get(userId);
        if (existing && existing !== ws) {
            console.log(`Closing previous connection for ${userId}`);
            existing.close(4000, "New connection established");
        }
        this.map.set(userId, ws);
    }

    removeConnection(userId: UserId, ws: WebSocket): void {
        const current = this.map.get(userId);
        if (current === ws) {
            this.map.delete(userId);
        }
    }

    getConnection(userId: UserId): WebSocket | undefined {
        return this.map.get(userId);
    }

    //  FIX: "useId" typo fixed to "userId" — poora signaling tod raha tha yeh
    send(userId: UserId, payload: Object): void {
        const ws = this.map.get(userId);
        if (ws?.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(payload));
        } else {
            console.warn(`[ConnectionManager] Cannot send to ${userId} — not connected`);
        }
    }

    isOnline(userId: UserId): boolean {
        return this.map.has(userId);
    }

    /**
     * Broadcast a message to all connected users
     * Used for session announcements, notifications, etc.
     */
    broadcast(payload: Object, excludeUserId?: UserId): void {
        const message = JSON.stringify(payload);
        this.map.forEach((ws, userId) => {
            if (excludeUserId && userId === excludeUserId) return;
            if (ws.readyState === WebSocket.OPEN) {
                ws.send(message);
            }
        });
    }

    /**
     * Send message to all users in a specific room
     * Used for class-specific events
     */
    sendToRoom(classId: string, payload: Object): void {
        const message = JSON.stringify(payload);
        const roomMembers = roomManager.getRoomMembers(classId);
        
        roomMembers.forEach((userId) => {
            const ws = this.map.get(userId);
            if (ws?.readyState === WebSocket.OPEN) {
                ws.send(message);
            }
        });
    }

    /**
     * Send message to a specific user by userId
     * Alias for send() for consistency
     */
    sendToUser(userId: UserId, payload: Object): void {
        this.send(userId, payload);
    }
}

export const connectionManager = new ConnectionManager();