import { Router } from 'express';

const router = Router();

/**
 * GET /api/v1/config/webrtc
 * 
 * Returns ICE server configuration for WebRTC clients.
 * Includes STUN servers and optionally TURN servers.
 * This endpoint is public (no auth required) so students/teachers
 * can fetch ICE config before joining a call.
 */
router.get('/webrtc', (_req, res) => {
    // Default ICE server configuration
    // In production, you can fetch these from a database or environment variables
    const iceServers = [
        {
            urls: 'stun:stun.l.google.com:19302',
        },
        {
            urls: 'stun:stun1.l.google.com:19302',
        },
        // Optional: Add TURN servers for better connectivity in restrictive networks
        // {
        //     urls: 'turn:your-turn-server.com:3478',
        //     username: process.env.TURN_USERNAME || 'user',
        //     credential: process.env.TURN_CREDENTIAL || 'pass',
        // },
        // {
        //     urls: 'turn:your-turn-server.com:443?transport=tcp',
        //     username: process.env.TURN_USERNAME || 'user',
        //     credential: process.env.TURN_CREDENTIAL || 'pass',
        // },
    ];

    res.json({
        success: true,
        iceServers,
    });
});

export default router;
