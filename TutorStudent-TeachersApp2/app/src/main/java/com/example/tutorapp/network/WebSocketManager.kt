package com.example.tutorapp.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val context: Context,
    private val tokenProvider: () -> String
) {
    companion object {
        private const val TAG = "WebSocketManager"
        private const val MAX_RETRY_MS = 32_000L
    }

    private var webSocket: WebSocket? = null
    private var retryDelay = 2_000L
    private var isIntentionalDisconnect = false

    private val _events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WebSocketEvent> = _events

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "Connected to EduVibe WebSocket")
            retryDelay = 2_000L
            _events.tryEmit(WebSocketEvent.WebSocketConnected)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json    = org.json.JSONObject(text)
                val type    = json.getString("type")
                val payload = json.getJSONObject("payload")

                Log.d(TAG, "Received event: $type")

                when (type) {
                    "session:started" -> {
                        val classId         = payload.getString("classId")
                        val sessionId       = payload.optString("sessionId", "")
                        val actualStartTime = payload.optString("actualStartTime", "")
                        val className       = payload.optString("className", "")
                        _events.tryEmit(WebSocketEvent.SessionStarted(classId, sessionId, actualStartTime, className))
                    }

                    "session:ended" -> {
                        val classId       = payload.getString("classId")
                        val sessionId     = payload.getString("sessionId")
                        val actualEndTime = payload.optString("actualEndTime", "")
                        _events.tryEmit(WebSocketEvent.SessionEnded(classId, sessionId, actualEndTime))
                    }

                    "session:student-joined" -> {
                        val classId   = payload.getString("classId")
                        val sessionId = payload.getString("sessionId")
                        val studentId = payload.getString("studentId")
                        _events.tryEmit(WebSocketEvent.StudentJoinedSession(classId, sessionId, studentId))
                    }

                    "room:joined" -> {
                        val classId = payload.getString("classId")
                        _events.tryEmit(WebSocketEvent.RoomJoined(classId))
                    }

                    "room:peer-joined" -> {
                        val classId = payload.getString("classId")
                        val peerId  = payload.getString("peerId")
                        _events.tryEmit(WebSocketEvent.PeerJoinedRoom(classId, peerId))
                    }

                    "room:peer-left" -> {
                        val classId = payload.getString("classId")
                        val peerId  = payload.getString("peerId")
                        _events.tryEmit(WebSocketEvent.PeerLeftRoom(classId, peerId))
                    }

                    "session:student-early-leave" -> {
                        val classId          = payload.getString("classId")
                        val sessionId        = payload.optString("sessionId", "")
                        val studentId        = payload.optString("studentId", "")
                        val studentName      = payload.optString("studentName", "Student")
                        val reason           = payload.optString("reason", "")
                        val minutesAttended  = payload.optInt("minutesAttended", 0)
                        val leftAt           = payload.optString("leftAt", "")
                        val sessionCompleted = payload.optBoolean("sessionCompleted", false)
                        _events.tryEmit(
                            WebSocketEvent.StudentLeftEarly(
                                classId          = classId,
                                sessionId        = sessionId,
                                studentId        = studentId,
                                studentName      = studentName,
                                reason           = reason,
                                minutesAttended  = minutesAttended,
                                leftAt           = leftAt,
                                sessionCompleted = sessionCompleted
                            )
                        )
                    }

                    "webrtc:offer" -> {
                        val from = payload.optString("from", "")
                        val sdp  = payload.optString("sdp", "")
                        _events.tryEmit(WebSocketEvent.WebRtcOffer(from, sdp))
                    }

                    "webrtc:answer" -> {
                        val from = payload.optString("from", "")
                        val sdp  = payload.optString("sdp", "")
                        _events.tryEmit(WebSocketEvent.WebRtcAnswer(from, sdp))
                    }

                    "webrtc:ice-candidate" -> {
                        val from      = payload.optString("from", "")
                        val candidate = payload.optString("candidate", "")
                        _events.tryEmit(WebSocketEvent.WebRtcIceCandidate(from, candidate))
                    }

                    "room:new-viewer" -> {
                        val classId = payload.getString("classId")
                        val peerId  = payload.optString("peerId", "")
                        val role    = payload.optString("role", "")
                        _events.tryEmit(WebSocketEvent.NewViewer(classId, peerId, role))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing WebSocket message: ${e.message}")
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Connection closed: $reason")
            if (!isIntentionalDisconnect) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Connection failed: ${t.message}")
            if (!isIntentionalDisconnect) scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        Log.d(TAG, "Reconnecting in ${retryDelay}ms...")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isIntentionalDisconnect) connect()
        }, retryDelay)
        retryDelay = minOf(retryDelay * 2, MAX_RETRY_MS)
    }

    fun connect() {
        isIntentionalDisconnect = false
        val token = tokenProvider()
        if (token.isBlank()) {
            Log.w(TAG, "Cannot connect: token is blank")
            return
        }
        webSocket?.cancel()
        webSocket = null
        val wsUrl   = AppConfig.wsUrl() + "?token=$token"
        val request = Request.Builder().url(wsUrl).build()
        webSocket   = client.newWebSocket(request, listener)
        Log.d(TAG, "Connecting to: $wsUrl")
    }

    fun disconnect() {
        isIntentionalDisconnect = true
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
    }

    fun sendEvent(type: String, payload: Map<String, Any>) {
        try {
            val json = org.json.JSONObject().apply {
                put("type", type)
                val payloadJson = org.json.JSONObject()
                payload.forEach { (key, value) -> payloadJson.put(key, value) }
                put("payload", payloadJson)
            }
            webSocket?.send(json.toString())
            Log.d(TAG, "Sent event: $type")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending event: ${e.message}")
        }
    }

    fun sendJoinRoom(classId: String, role: String) {
        sendEvent("room:join", mapOf("classId" to classId, "role" to role))
    }

    fun sendLeaveRoom(classId: String) {
        sendEvent("room:leave", mapOf("classId" to classId))
    }

    fun sendSessionStarted(classId: String, className: String, sessionId: String = "") {
        sendEvent("session:teacher-started", mapOf(
            "classId"   to classId,
            "className" to className,
            "sessionId" to sessionId
        ))
    }

    fun sendSessionEnded(classId: String, sessionId: String) {
        sendEvent("session:teacher-ended", mapOf(
            "classId"   to classId,
            "sessionId" to sessionId
        ))
    }

    fun sendStudentJoined(classId: String, sessionId: String) {
        sendEvent("session:student-joined", mapOf(
            "classId"   to classId,
            "sessionId" to sessionId
        ))
    }

    fun sendEarlyLeave(
        classId: String,
        sessionId: String,
        reason: String,
        studentName: String,
        studentId: String,
        minutesAttended: Int
    ) {
        sendEvent(
            type = "session:student-early-leave",
            payload = mapOf(
                "classId"         to classId,
                "sessionId"       to sessionId,
                "reason"          to reason,
                "studentName"     to studentName,
                "studentId"       to studentId,
                "minutesAttended" to minutesAttended
            )
        )
    }

    fun sendRoomOffer(classId: String, sdp: String) {
        sendEvent("webrtc:room-offer", mapOf("classId" to classId, "sdp" to sdp))
    }

    fun sendRoomAnswer(classId: String, sdp: String) {
        sendEvent("webrtc:room-answer", mapOf("classId" to classId, "sdp" to sdp))
    }

    fun sendIceCandidate(classId: String, candidate: String) {
        sendEvent("webrtc:room-ice-candidate", mapOf("classId" to classId, "candidate" to candidate))
    }
}

sealed class WebSocketEvent {
    data class SessionStarted(
        val classId:         String,
        val sessionId:       String,
        val actualStartTime: String,
        val className:       String
    ) : WebSocketEvent()

    data class SessionEnded(
        val classId:       String,
        val sessionId:     String,
        val actualEndTime: String
    ) : WebSocketEvent()

    data class StudentJoinedSession(
        val classId:   String,
        val sessionId: String,
        val studentId: String
    ) : WebSocketEvent()

    data class RoomJoined(val classId: String) : WebSocketEvent()
    data class PeerJoinedRoom(val classId: String, val peerId: String) : WebSocketEvent()
    data class PeerLeftRoom(val classId: String, val peerId: String) : WebSocketEvent()

    data class StudentLeftEarly(
        val classId:          String,
        val sessionId:        String,
        val studentId:        String,
        val studentName:      String,
        val reason:           String,
        val minutesAttended:  Int,
        val leftAt:           String,
        val sessionCompleted: Boolean = false
    ) : WebSocketEvent()

    data class WebRtcOffer(val from: String, val sdp: String) : WebSocketEvent()
    data class WebRtcAnswer(val from: String, val sdp: String) : WebSocketEvent()
    data class WebRtcIceCandidate(val from: String, val candidate: String) : WebSocketEvent()
    data class NewViewer(val classId: String, val peerId: String, val role: String) : WebSocketEvent()

    // Auto-reconnect ke baad room rejoin ke liye
    object WebSocketConnected : WebSocketEvent()
}