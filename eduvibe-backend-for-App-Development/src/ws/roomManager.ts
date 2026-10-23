import { Class } from "../models/class.model";
import { Enrollment } from "../models/enrollment.model";
import { TeacherProfile } from "../models/teacherProfile.model";
import { StudentProfile } from "../models/studentProfile.model";

const MAX_ROOM_SIZE = 2;

interface JoinResult {
    success: boolean;
    peers: string[];
    alreadyInRoom?: boolean;
    error?: "not-authorized" | "room-full";
}

interface BufferedMessage {
    type: string;
    payload: Record<string, unknown>;
    from: string;
    timestamp: number;
}

class RoomManager {
    private rooms = new Map<string, Set<string>>();
    private bufferedMessages = new Map<string, BufferedMessage[]>();
    private userRoles = new Map<string, string>();

    // roomId → teacherId — teacher WS disconnect ke baad bhi yaad rahe
    private roomTeachers = new Map<string, string>();

    // ✅ FIX 1: Track karo kaun authorized hai room mein (room complete hone tak)
    // Taaki reconnect pe MAX_ROOM_SIZE block na kare
    private roomAuthorized = new Map<string, Set<string>>();

    async validateMembership(classId: string, userId: string): Promise<boolean> {
        return true;
    }

    setUserRole(userId: string, role: string): void {
        this.userRoles.set(userId, role);
        console.log(`[RoomManager] Role set: ${userId} = ${role}`);

        if (role === "teacher") {
            for (const [roomId, room] of this.rooms.entries()) {
                if (room.has(userId)) {
                    this.roomTeachers.set(roomId, userId);
                    console.log(`[RoomManager] Teacher ${userId} saved for room ${roomId}`);
                }
            }
        }
    }

    getUserRole(userId: string): string {
        return this.userRoles.get(userId) ?? "student";
    }

    getTeacherForRoom(roomId: string): string | null {
        return this.roomTeachers.get(roomId) ?? null;
    }

    async join(roomId: string, userId: string): Promise<JoinResult> {
        const isMember = await this.validateMembership(roomId, userId);
        if (!isMember) {
            console.warn(`[RoomManager] join rejected — not authorized: userId=${userId} roomId=${roomId}`);
            return { success: false, peers: [], error: "not-authorized" };
        }

        if (!this.rooms.has(roomId)) {
            this.rooms.set(roomId, new Set());
        }
        // ✅ FIX 1: authorized set initialize karo
        if (!this.roomAuthorized.has(roomId)) {
            this.roomAuthorized.set(roomId, new Set());
        }

        const room = this.rooms.get(roomId)!;
        const authorized = this.roomAuthorized.get(roomId)!;

        // Already in room — reconnect case
        if (room.has(userId)) {
            const peers = [...room].filter((id) => id !== userId);
            console.log(`[RoomManager] ${userId} already in room ${roomId}, peers: ${peers}`);
            return { success: true, peers, alreadyInRoom: true };
        }

        // ✅ FIX 1: Agar pehle se authorized hai (reconnect) toh size check bypass karo
        const isReconnecting = authorized.has(userId);

        if (!isReconnecting && room.size >= MAX_ROOM_SIZE) {
            console.warn(`[RoomManager] Room full: ${roomId}`);
            return { success: false, peers: [], error: "room-full" };
        }

        room.add(userId);
        authorized.add(userId); // ✅ Authorized mark karo
        const peers = [...room].filter((id) => id !== userId);
        console.log(`[RoomManager] ${userId} joined room ${roomId}, peers: ${peers}`);
        return { success: true, peers, alreadyInRoom: false };
    }

    leave(roomId: string, userId: string): string | null {
        const room = this.rooms.get(roomId);
        if (!room) return null;
        room.delete(userId);
        const peer = [...room][0] ?? null;
        if (room.size === 0) {
            this.rooms.delete(roomId);
            this.roomTeachers.delete(roomId);
            this.roomAuthorized.delete(roomId); // ✅ Room khatam toh authorized bhi clear
        }
        console.log(`[RoomManager] ${userId} left room ${roomId}, notifying peer: ${peer}`);
        return peer;
    }

    getPeer(roomId: string, userId: string): string | null {
        const room = this.rooms.get(roomId);
        if (!room) return null;
        for (const id of room) {
            if (id !== userId) return id;
        }
        return null;
    }

    getConnectedPeers(roomId: string): string[] {
        const room = this.rooms.get(roomId);
        if (!room) return [];
        return [...room];
    }

    getRoomMembers(roomId: string): string[] {
        const room = this.rooms.get(roomId);
        if (!room) return [];
        return [...room];
    }

    bufferMessage(roomId: string, type: string, payload: Record<string, unknown>, from: string): void {
        if (!this.bufferedMessages.has(roomId)) {
            this.bufferedMessages.set(roomId, []);
        }
        const buffer = this.bufferedMessages.get(roomId)!;
        buffer.push({ type, payload, from, timestamp: Date.now() });
        console.log(`[RoomManager] 📨 Buffered ${type} from ${from} for room ${roomId} (buffer size: ${buffer.length})`);
    }

    flushBufferedMessages(roomId: string): BufferedMessage[] {
        const messages = this.bufferedMessages.get(roomId) || [];
        this.bufferedMessages.delete(roomId);
        if (messages.length > 0) {
            console.log(`[RoomManager] 📤 Flushing ${messages.length} buffered messages for room ${roomId}`);
        }
        return messages;
    }

    cleanup(userId: string): Array<{ roomId: string; peerId: string; userRole: string }> {
        const affected: Array<{ roomId: string; peerId: string; userRole: string }> = [];
        const userRole = this.userRoles.get(userId) ?? "student";

        for (const [roomId, room] of this.rooms.entries()) {
            if (room.has(userId)) {
                room.delete(userId);
                // ✅ FIX 2: authorized set se DELETE mat karo — reconnect ke liye zaroori hai
                // (roomAuthorized mein userId rehne do taaki wapas join kar sake)

                const peer = [...room][0];

                // ✅ FIX 2: Teacher disconnect pe peer ko notify MAT karo
                // Sirf student disconnect pe notify karo taaki dono cut hon
                if (peer && userRole === "student") {
                    affected.push({ roomId, peerId: peer, userRole });
                }
                // Teacher disconnect: affected mein push nahi karte — student safe rahega

                if (room.size === 0) {
                    this.rooms.delete(roomId);
                    this.bufferedMessages.delete(roomId);
                    this.roomTeachers.delete(roomId);
                    this.roomAuthorized.delete(roomId); // Room khatam toh sab clear
                }
            }
        }

        this.userRoles.delete(userId);
        return affected;
    }
}

export const roomManager = new RoomManager();