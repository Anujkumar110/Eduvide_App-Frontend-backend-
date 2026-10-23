package com.example.tutorapp.websocket

import android.util.Log
import com.example.tutorapp.network.AppConfig
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject

class SignalingClient(private val roomId: String) {

    var onConnected:          (() -> Unit)?                    = null
    var onDisconnected:       (() -> Unit)?                    = null
    var onOffer:              ((String) -> Unit)?              = null
    var onAnswer:             ((String) -> Unit)?              = null
    var onIceCandidate:       ((String, String, Int) -> Unit)? = null
    var onNewViewer:          ((String) -> Unit)?              = null
    var onParticipantCount:   ((Int) -> Unit)?                 = null
    var onChatMessage:        ((String, String) -> Unit)?      = null
    var onTyping:             ((String, Boolean) -> Unit)?     = null
    var onReadReceipt:        ((String, String) -> Unit)?      = null
    var onHandRaised:         ((String) -> Unit)?              = null
    var onScreenShare:        ((Boolean) -> Unit)?             = null
    var onPeerLeft:           (() -> Unit)?                    = null
    var onJoinedWithPeers:    ((List<String>) -> Unit)?        = null
    var onSessionStarted:     ((classId: String, className: String) -> Unit)? = null
    var onTeacherLeft:        (() -> Unit)?                    = null
    var onStudentForceEnd:    ((reason: String) -> Unit)?      = null
    var onReconnected:        (() -> Unit)?                    = null  // ← NAYA

    private var webSocket:    WebSocket? = null
    @Volatile private var isConnected  = false
    @Volatile private var isConnecting = false
    @Volatile private var isIntentionalDisconnect = false

    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 5
    private val BASE_RECONNECT_DELAY_MS = 2000L
    private var reconnectJob: Job? = null
    private var storedToken: String? = null
    private var storedRole: String? = null

    private var isFirstConnect = true  // ← NAYA: pehli baar vs reconnect track karne ke liye

    private val pendingMessages = mutableListOf<Pair<String, JSONObject>>()

    private var currentParticipantCount = 1

    private val client = OkHttpClient.Builder()
        .pingInterval(25, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)

    fun connect(token: String) {
        if (token.isBlank()) { Log.e(TAG, "Token empty — aborting"); return }
        if (isConnected || isConnecting) { Log.d(TAG, "Already connected/connecting"); return }
        if (isIntentionalDisconnect) { Log.d(TAG, "Intentional disconnect — skip connect"); return }

        storedToken  = token.removePrefix("Bearer ").trim()
        isConnecting = true

        Log.d(TAG, "Connecting… roomId=$roomId")

        val request = Request.Builder()
            .url("${AppConfig.wsUrl()}?token=$storedToken")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WS Connected ✅")
                isConnected       = true
                isConnecting      = false
                reconnectAttempts = 0

                if (isFirstConnect) {
                    isFirstConnect = false
                    onConnected?.invoke()
                } else {
                    // Reconnect hua — room dobara join karo
                    Log.d(TAG, "WS Reconnected — invoking onReconnected")
                    onReconnected?.invoke()
                }

                flushPendingMessages()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WS closing: $code $reason")
                isConnected  = false
                isConnecting = false
                ws.close(1000, null)

                if (!isIntentionalDisconnect) {
                    scheduleReconnect()
                } else {
                    onDisconnected?.invoke()
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WS Failure: ${t.message}")
                isConnected  = false
                isConnecting = false

                if (!isIntentionalDisconnect) {
                    scheduleReconnect()
                } else {
                    onDisconnected?.invoke()
                }
            }
        })
    }

    private fun flushPendingMessages() {
        synchronized(pendingMessages) {
            if (pendingMessages.isEmpty()) return
            Log.d(TAG, "Flushing ${pendingMessages.size} pending messages")
            val toSend = pendingMessages.toList()
            pendingMessages.clear()
            toSend.forEach { (type, payload) ->
                sendNow(type, payload)
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached")
            onDisconnected?.invoke()
            return
        }

        reconnectJob?.cancel()

        val delayMs = BASE_RECONNECT_DELAY_MS * (1L shl reconnectAttempts)
        reconnectAttempts++

        Log.d(TAG, "Scheduling reconnect $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS in ${delayMs}ms")

        reconnectJob = scope.launch {
            delay(delayMs)
            if (!isIntentionalDisconnect) {
                Log.d(TAG, "Reconnecting… attempt $reconnectAttempts")
                storedToken?.let { connect(it) }
            }
        }
    }

    private fun handleMessage(text: String) {
        try {
            val json    = JSONObject(text)
            val type    = json.getString("type")
            val payload = json.optJSONObject("payload") ?: JSONObject()

            Log.d(TAG, "← $type | $payload")

            when (type) {

                "room:joined" -> {
                    val peersArray = payload.optJSONArray("peers")
                    val count      = peersArray?.length() ?: 0
                    currentParticipantCount = count + 1
                    onParticipantCount?.invoke(currentParticipantCount)
                    val peerList = mutableListOf<String>()
                    if (peersArray != null) {
                        for (i in 0 until count) peerList.add(peersArray.getString(i))
                    }
                    onJoinedWithPeers?.invoke(peerList)
                }

                "room:peer-joined" -> {
                    currentParticipantCount++
                    onParticipantCount?.invoke(currentParticipantCount)
                    onNewViewer?.invoke(payload.optString("peerId", "unknown"))
                }

                "room:new-viewer" -> {
                    currentParticipantCount++
                    onParticipantCount?.invoke(currentParticipantCount)
                    onNewViewer?.invoke(payload.optString("peerId", "unknown"))
                }

                "room:joined-with-peers" -> {
                    val peersArray = payload.optJSONArray("peers")
                    val count      = peersArray?.length() ?: 0
                    currentParticipantCount = count + 1
                    onParticipantCount?.invoke(currentParticipantCount)
                    val peerList = mutableListOf<String>()
                    if (peersArray != null) {
                        for (i in 0 until count) peerList.add(peersArray.getString(i))
                    }
                    onJoinedWithPeers?.invoke(peerList)
                }

                "room:peer-left" -> {
                    currentParticipantCount = maxOf(1, currentParticipantCount - 1)
                    onParticipantCount?.invoke(currentParticipantCount)
                    onPeerLeft?.invoke()
                }

                "room:teacher-left" -> {
                    currentParticipantCount = maxOf(1, currentParticipantCount - 1)
                    onParticipantCount?.invoke(currentParticipantCount)
                    onTeacherLeft?.invoke()
                }

                "room:student-force-end" -> {
                    val reason = payload.optString("reason", "Student left")
                    onStudentForceEnd?.invoke(reason)
                }

                "room:error" -> {
                    Log.e(TAG, "Room error: ${payload.optString("error")}")
                    onDisconnected?.invoke()
                }

                "session:started" -> {
                    val classId   = payload.optString("classId",   roomId)
                    val className = payload.optString("className", classId)
                    onSessionStarted?.invoke(classId, className)
                }

                "webrtc:offer" -> {
                    val sdp = extractSdpString(payload)
                    if (sdp != null) onOffer?.invoke(sdp)
                    else Log.e(TAG, "Could not extract SDP from offer")
                }

                "webrtc:answer" -> {
                    val sdp = extractSdpString(payload)
                    if (sdp != null) onAnswer?.invoke(sdp)
                    else Log.e(TAG, "Could not extract SDP from answer")
                }

                "webrtc:ice-candidate" -> {
                    val candidateObj  = payload.optJSONObject("candidate") ?: payload
                    val sdp           = candidateObj.optString("candidate",  "")
                    val sdpMid        = candidateObj.optString("sdpMid",     "")
                    val sdpMLineIndex = candidateObj.optInt("sdpMLineIndex", 0)
                    if (sdp.isNotBlank()) onIceCandidate?.invoke(sdp, sdpMid, sdpMLineIndex)
                }

                "chat:message" -> {
                    onChatMessage?.invoke(
                        payload.optString("sender", "Unknown"),
                        payload.optString("text",   "")
                    )
                }

                "chat:typing" -> {
                    onTyping?.invoke(
                        payload.optString("senderId", "Unknown"),
                        payload.optBoolean("isTyping", false)
                    )
                }

                "chat:read:ack" -> {
                    onReadReceipt?.invoke(
                        payload.optString("conversationId", ""),
                        payload.optString("readBy", "")
                    )
                }

                "room:hand-raised" -> {
                    onHandRaised?.invoke(payload.optString("name", "Student"))
                }

                "room:screen-share" -> {
                    onScreenShare?.invoke(payload.optBoolean("active", false))
                }

                else -> Log.w(TAG, "Unknown message type: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Message parse error: ${e.message}")
        }
    }

    private fun extractSdpString(payload: JSONObject): String? {
        val sdpField = payload.opt("sdp") ?: return null
        return when (sdpField) {
            is String     -> sdpField.takeIf { it.isNotBlank() }
            is JSONObject -> sdpField.optString("sdp").takeIf { it.isNotBlank() }
            else          -> sdpField.toString().takeIf { it.isNotBlank() }
        }
    }

    private fun send(type: String, payload: JSONObject) {
        if (!isConnected) {
            if (type in listOf(
                    "webrtc:room-offer",
                    "webrtc:room-answer",
                    "webrtc:room-ice-candidate",
                    "room:join",
                    "room:screen-share"
                )) {
                synchronized(pendingMessages) {
                    if (type == "webrtc:room-offer") {
                        pendingMessages.removeAll { it.first == "webrtc:room-offer" }
                    }
                    pendingMessages.add(Pair(type, payload))
                }
                Log.d(TAG, "Queued (not connected): $type")
            } else {
                Log.w(TAG, "Not connected — dropping: $type")
            }
            return
        }
        sendNow(type, payload)
    }

    private fun sendNow(type: String, payload: JSONObject) {
        val message = JSONObject().apply {
            put("type",    type)
            put("payload", payload)
        }.toString()
        Log.d(TAG, "→ $type | $payload")
        webSocket?.send(message)
    }

    fun joinRoom(role: String) {
        storedRole = role
        send("room:join", JSONObject().apply {
            put("classId", roomId)
            put("role",    role)
        })
    }

    fun notifySessionStarted(className: String = "") = send("session:teacher-started", JSONObject().apply {
        put("classId",   roomId)
        put("className", className)
    })

    fun sendOffer(sdp: String) = send("webrtc:room-offer", JSONObject().apply {
        put("classId", roomId)
        put("sdp", JSONObject().apply { put("type", "offer"); put("sdp", sdp) })
    })

    fun sendAnswer(sdp: String) = send("webrtc:room-answer", JSONObject().apply {
        put("classId", roomId)
        put("sdp", JSONObject().apply { put("type", "answer"); put("sdp", sdp) })
    })

    fun sendIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) =
        send("webrtc:room-ice-candidate", JSONObject().apply {
            put("classId", roomId)
            put("candidate", JSONObject().apply {
                put("candidate",     candidate)
                put("sdpMid",        sdpMid)
                put("sdpMLineIndex", sdpMLineIndex)
            })
        })

    fun sendChat(sender: String, text: String) = send("chat:message", JSONObject().apply {
        put("classId", roomId)
        put("sender",  sender)
        put("text",    text)
    })

    fun sendTyping(receiverId: String, isTyping: Boolean) = send("chat:typing", JSONObject().apply {
        put("receiverId", receiverId)
        put("isTyping",   isTyping)
    })

    fun sendMarkAsRead(conversationId: String, senderId: String) = send("chat:read", JSONObject().apply {
        put("conversationId", conversationId)
        put("senderId",       senderId)
    })

    fun sendHandRaise(name: String) = send("room:raise-hand", JSONObject().apply {
        put("classId", roomId)
        put("name",    name)
    })

    fun sendScreenShare(active: Boolean) = send("room:screen-share", JSONObject().apply {
        put("classId", roomId)
        put("active",  active)
    })

    fun leaveRoom(role: String) = send("room:leave", JSONObject().apply {
        put("classId", roomId)
        put("role",    role)
    })

    fun sendTeacherLeft() = send("room:teacher-left", JSONObject().apply {
        put("classId", roomId)
    })

    fun sendStudentForceEnd(reason: String) = send("room:student-force-end", JSONObject().apply {
        put("classId", roomId)
        put("reason",  reason)
    })

    fun disconnect() {
        isIntentionalDisconnect = true
        isConnected             = false
        isConnecting            = false
        isFirstConnect          = true  // reset for next time

        reconnectJob?.cancel()
        reconnectJob      = null
        reconnectAttempts = 0

        synchronized(pendingMessages) { pendingMessages.clear() }

        currentParticipantCount = 1

        webSocket?.close(1000, "User left")
        webSocket = null

        supervisorJob.cancel()

        Log.d(TAG, "Disconnected (intentional)")
    }

    companion object { private const val TAG = "SignalingClient" }
}