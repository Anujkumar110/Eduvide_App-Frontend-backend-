import { WebSocketServer, WebSocket } from "ws";
import { Server } from "http";
import { authenticateWS } from "./wsAuth";
import { connectionManager } from "./connectionManager";
import { roomManager } from "./roomManager";
import { handleMessage } from "./messageRouter";

interface WSServerHandle {
    shutdown: () => Promise<void>;
}

export function initWSServer(httpServer: Server): WSServerHandle {
    const wss = new WebSocketServer({ server: httpServer });

    wss.on("connection", async (ws: WebSocket, req) => {
        const userId = await authenticateWS(ws, req);
        if (!userId) return;
        console.log("User connected:", userId);

        connectionManager.addConnection(userId, ws);
        startHeartbeat(ws);

        ws.on("message", async (data) => {
            try {
                await handleMessage(ws, userId, data.toString());
            } catch (error) {
                console.error(`[WS] Error handling message from ${userId}:`, error);
                ws.send(JSON.stringify({
                    type: "error",
                    payload: { message: "Failed to process message" }
                }));
            }
        });

        ws.on("close", () => {
            connectionManager.removeConnection(userId, ws);

            // ✅ Role-aware cleanup:
            // Teacher disconnect → student ko peer-left NAHI → student ki class chalta rahe
            // Student disconnect → teacher ko peer-left → dono cut
            const affected = roomManager.cleanup(userId);
            for (const { peerId, userRole } of affected) {
                if (userRole === "teacher") {
                    console.log(`[WS] Teacher ${userId} disconnected — student ${peerId} NOT notified, class continues`);
                    // Student ko kuch nahi bhejte — class chalta rahe
                } else {
                    console.log(`[WS] Student ${userId} disconnected — notifying teacher ${peerId} (peer-left)`);
                    connectionManager.send(peerId, {
                        type: "room:peer-left",
                        payload: { peerId: userId },
                    });
                }
            }
        });

        ws.on("error", (err) => console.error(`WS Error [${userId}]:`, err));
    });

    const shutdown = (): Promise<void> =>
        new Promise((resolve, reject) => {
            wss.clients.forEach((ws) => ws.close(1001, "Server shutting down"));
            wss.close((err) => (err ? reject(err) : resolve()));
        });

    return { shutdown };
}

function startHeartbeat(ws: WebSocket): void {
    const interval = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) ws.ping();
        else clearInterval(interval);
    }, 30_000);

    ws.on("pong", () => { /* alive */ });
    ws.on("close", () => clearInterval(interval));
}