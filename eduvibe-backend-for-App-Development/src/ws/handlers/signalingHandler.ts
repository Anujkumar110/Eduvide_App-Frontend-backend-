import { connectionManager } from "../connectionManager";
import { roomManager } from "../roomManager";
import * as sessionService from "../../services/session.service";
import { SESSION_STATUS } from "../../constants";

export async function handleSignaling(
    type: string,
    senderId: string,
    payload: Record<string, unknown>
): Promise<void> {

    if (type === "session:teacher-started") {
        const { classId, className } = payload as { classId: string; className: string };
        try {
            const result = await sessionService.startSession(senderId, classId);
            connectionManager.sendToRoom(classId, { type: "session:started", payload: { classId, className, sessionId: result.session._id, teacherId: senderId, actualStartTime: result.session.actualStartTime } });
            connectionManager.broadcast({ type: "session:started", payload: { classId, className, sessionId: result.session._id, teacherId: senderId, actualStartTime: result.session.actualStartTime } });
            console.log(`[Session] Teacher ${senderId} started session for class ${classId}`);
        } catch (error: any) {
            console.error(`[Session] Error starting session: ${error.message}`);
            connectionManager.send(senderId, { type: "session:error", payload: { classId, error: error.message } });
        }
        return;
    }

    if (type === "session:teacher-ended") {
        const { sessionId, classId } = payload as { sessionId: string; classId: string };
        try {
            const result = await sessionService.endSession(senderId, sessionId);
            connectionManager.sendToRoom(classId, { type: "session:ended", payload: { classId, sessionId, actualEndTime: result.session.actualEndTime } });
            connectionManager.broadcast({ type: "session:ended", payload: { classId, sessionId, actualEndTime: result.session.actualEndTime } });
            console.log(`[Session] Teacher ${senderId} ended session ${sessionId}`);
        } catch (error: any) {
            console.error(`[Session] Error ending session: ${error.message}`);
        }
        return;
    }

    if (type === "session:student-joined") {
        const { classId, sessionId } = payload as { classId: string; sessionId: string };
        try {
            await sessionService.attendSession(senderId, sessionId);
            connectionManager.sendToRoom(classId, { type: "session:student-joined", payload: { classId, sessionId, studentId: senderId } });
            console.log(`[Session] Student ${senderId} joined session ${sessionId}`);
        } catch (error: any) {
            console.error(`[Session] Error marking attendance: ${error.message}`);
        }
        return;
    }

    if (type === "session:student-early-leave") {
        const { classId, sessionId, reason, minutesAttended } = payload as { classId: string; sessionId: string; reason: string; minutesAttended: number };
        try {
            const { StudentProfile } = await import("../../models/studentProfile.model");
            const { Types } = await import("mongoose");
            const profile = await StudentProfile.findOne({ userId: new Types.ObjectId(senderId) }).lean();
            const studentName = (profile as any)?.fullName ?? "Unknown Student";
            console.log(`[Session] ⚠️ Student ${senderId} (${studentName}) left early. Reason: "${reason}"`);
            const { EarlyLeaveReport } = await import("../../models/earlyLeaveReport.model");
            await EarlyLeaveReport.create({ classId, sessionId, studentId: senderId, studentName, reason, minutesAttended: minutesAttended ?? 0, leftAt: new Date() });
            console.log(`[Session] ✅ EarlyLeaveReport saved for student ${senderId}`);
            const { Class } = await import("../../models/class.model");
            const classDoc = await Class.findById(classId).select("teacherId").lean();
            const teacherId = (classDoc as any)?.teacherId?.toString();
            if (teacherId) {
                connectionManager.send(teacherId, { type: "session:student-left-early", payload: { classId, sessionId, studentId: senderId, studentName, reason, minutesAttended: minutesAttended ?? 0, leftAt: new Date().toISOString() } });
                console.log(`[Session] ✅ Teacher ${teacherId} notified about early leave`);
            }
        } catch (err: any) {
            console.error(`[Session] ❌ Error saving early leave report: ${err.message}`);
        }
        return;
    }

    // ── Room join ─────────────────────────────────────────────────────────────
    if (type === "room:join") {
        const { classId, role } = payload as { classId: string; role: string };
        console.log(`[Signaling] 📞 ${senderId} (${role}) attempting to join room ${classId}`);

        if (role === "teacher") {
            try {
                await sessionService.startSession(senderId, classId);
                console.log(`[Session] ✅ Teacher ${senderId} session started for class ${classId}`);
            } catch (err: any) {
                console.warn(`[Session] ⚠️ startSession failed (non-fatal): ${err.message}`);
            }
        }

        if (role === "student") {
            try {
                const { Session } = await import("../../models/session.model");
                const { Types } = await import("mongoose");
                await Session.findOneAndUpdate(
                    { classId: new Types.ObjectId(classId), status: "ongoing" },
                    { $addToSet: { attendedStudents: { userId: new Types.ObjectId(senderId), joinedAt: new Date() } } }
                );
                console.log(`[Session] ✅ Student ${senderId} attendance marked for class ${classId}`);
            } catch (err: any) {
                console.warn(`[Session] ⚠️ attendSession failed (non-fatal): ${err.message}`);
            }
        }

        roomManager.join(classId, senderId).then(({ success, peers, alreadyInRoom, error }) => {
            if (!success) {
                console.error(`[Signaling] ❌ ${senderId} failed to join room ${classId}: ${error}`);
                connectionManager.send(senderId, { type: "room:error", payload: { classId, error } });
                return;
            }

            roomManager.setUserRole(senderId, role);
            console.log(`[Signaling] ✅ ${senderId} (${role}) joined room ${classId}, peers: ${JSON.stringify(peers)}, alreadyInRoom: ${alreadyInRoom}`);
            connectionManager.send(senderId, { type: "room:joined", payload: { classId, peers } });

            if (peers.length > 0) {
                if (!alreadyInRoom) {
                    connectionManager.send(peers[0], { type: "room:new-viewer", payload: { classId, peerId: senderId, role } });
                    if (role === "teacher") {
                        connectionManager.send(senderId, { type: "room:joined-with-peers", payload: { classId, peers } });
                    }
                } else {
                    if (role === "teacher") {
                        connectionManager.send(senderId, { type: "room:joined-with-peers", payload: { classId, peers } });
                    } else {
                        connectionManager.send(peers[0], { type: "room:new-viewer", payload: { classId, peerId: senderId, role, isReconnect: true } });
                    }
                }
                const bufferedMessages = roomManager.flushBufferedMessages(classId);
                bufferedMessages.forEach((msg) => {
                    const target = msg.from === senderId ? peers[0] : senderId;
                    if (target) connectionManager.send(target, { type: msg.type, payload: msg.payload });
                });
            }
        }).catch((err) => {
            console.error(`[Signaling] ❌ Room join error:`, err);
            connectionManager.send(senderId, { type: "room:error", payload: { classId, error: "Internal server error" } });
        });
        return;
    }

    // ── Room leave — role-aware ───────────────────────────────────────────────
    if (type === "room:leave") {
        const { classId, role } = payload as { classId: string; role: string };
        console.log(`[Signaling] 👋 ${senderId} (${role}) leaving room ${classId}`);

        const peer = roomManager.leave(classId, senderId);

        if (role === "student") {
            const notifyTarget = peer ?? roomManager.getTeacherForRoom(classId);
            if (notifyTarget) {
                connectionManager.send(notifyTarget, {
                    type: "room:peer-left",
                    payload: { classId, peerId: senderId },
                });
                console.log(`[Signaling] ✅ Student left — teacher ${notifyTarget} notified`);
            } else {
                connectionManager.sendToRoom(classId, {
                    type: "room:peer-left",
                    payload: { classId, peerId: senderId },
                });
            }
        } else {
            console.log(`[Signaling] ✅ Teacher left — student NOT notified (student rahe)`);
        }

        roomManager.setUserRole(senderId, "");
        return;
    }

    // ── ✅ FIX: Teacher ne pause kiya — student ko banner dikhao, class mat kato
    if (type === "room:teacher-left") {
        const { classId } = payload as { classId: string };
        console.log(`[Signaling] 🏃 Teacher ${senderId} paused — notifying student with banner`);

        const peer = roomManager.getPeer(classId, senderId);
        if (peer) {
            connectionManager.send(peer, {
                type: "room:teacher-left",
                payload: { classId },
            });
            console.log(`[Signaling] ✅ Student ${peer} notified — teacher stepped away`);
        }
        return;
    }

    // ── ✅ FIX: Student ne reason deke leave kiya — teacher ko bhi cut karo
    if (type === "room:student-force-end") {
        const { classId, reason } = payload as { classId: string; reason: string };
        console.log(`[Signaling] ⚠️ Student ${senderId} force-ended class ${classId} — reason: ${reason}`);

        // Teacher ko dhundho — room mein ho ya na ho
        const peer = roomManager.getPeer(classId, senderId);
        const teacherId = peer ?? roomManager.getTeacherForRoom(classId);

        if (teacherId) {
            connectionManager.send(teacherId, {
                type: "room:student-force-end",
                payload: { classId, reason },
            });
            console.log(`[Signaling] ✅ Teacher ${teacherId} notified — student force-ended`);
        } else {
            console.warn(`[Signaling] ⚠️ No teacher found for room ${classId} — cannot notify`);
        }
        return;
    }

    // ── WebRTC relay ──────────────────────────────────────────────────────────
    if (type === "webrtc:room-offer") {
        const { classId, sdp } = payload as { classId: string; sdp: unknown };
        const peer = roomManager.getPeer(classId, senderId);
        if (peer) { connectionManager.send(peer, { type: "webrtc:offer", payload: { from: senderId, sdp } }); }
        else { roomManager.bufferMessage(classId, "webrtc:offer", { from: senderId, sdp }, senderId); }
        return;
    }

    if (type === "webrtc:room-answer") {
        const { classId, sdp } = payload as { classId: string; sdp: unknown };
        const peer = roomManager.getPeer(classId, senderId);
        if (peer) { connectionManager.send(peer, { type: "webrtc:answer", payload: { from: senderId, sdp } }); }
        else { roomManager.bufferMessage(classId, "webrtc:answer", { from: senderId, sdp }, senderId); }
        return;
    }

    if (type === "webrtc:room-ice-candidate") {
        const { classId, candidate } = payload as { classId: string; candidate: unknown };
        const peer = roomManager.getPeer(classId, senderId);
        if (peer) { connectionManager.send(peer, { type: "webrtc:ice-candidate", payload: { from: senderId, candidate } }); }
        else { roomManager.bufferMessage(classId, "webrtc:ice-candidate", { from: senderId, candidate }, senderId); }
        return;
    }

    if (type === "room:raise-hand") {
        const { classId, name } = payload as { classId: string; name: string };
        const roomMembers = roomManager.getRoomMembers(classId);
        roomMembers.forEach((userId) => { connectionManager.send(userId, { type: "room:hand-raised", payload: { classId, userId: senderId, name } }); });
        return;
    }

    if (type === "room:screen-share") {
        const { classId, active } = payload as { classId: string; active: boolean };
        const roomMembers = roomManager.getRoomMembers(classId);
        roomMembers.forEach((userId) => { if (userId !== senderId) connectionManager.send(userId, { type: "room:screen-share", payload: { classId, userId: senderId, active } }); });
        return;
    }

    // ── Direct targetId-based signaling ──────────────────────────────────────
    const { targetId } = payload as { targetId: string };

    switch (type) {
        case "call:initiate": connectionManager.send(targetId, { type: "call:incoming", payload: { callerId: senderId, ...payload } }); break;
        case "call:accept": connectionManager.send(targetId, { type: "call:accepted", payload: { acceptedBy: senderId } }); break;
        case "call:reject": connectionManager.send(targetId, { type: "call:rejected", payload: { rejectedBy: senderId } }); break;
        case "webrtc:offer": connectionManager.send(targetId, { type: "webrtc:offer", payload: { from: senderId, sdp: payload.sdp } }); break;
        case "webrtc:answer": connectionManager.send(targetId, { type: "webrtc:answer", payload: { from: senderId, sdp: payload.sdp } }); break;
        case "webrtc:ice-candidate": connectionManager.send(targetId, { type: "webrtc:ice-candidate", payload: { from: senderId, candidate: payload.candidate } }); break;
    }
}