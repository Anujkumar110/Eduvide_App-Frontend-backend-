package com.example.tutorapp.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tutorapp.network.IceServerConfig
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.webrtc.IceServerConfig as WebRTCIceServerConfig
import com.example.tutorapp.webrtc.WebRTCClient
import com.example.tutorapp.websocket.SignalingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val _connectionState  = MutableStateFlow("Connecting...")
    val connectionState:  StateFlow<String>  = _connectionState.asStateFlow()

    private val _participantCount = MutableStateFlow(1)
    val participantCount: StateFlow<Int>     = _participantCount.asStateFlow()

    private val _isMuted          = MutableStateFlow(false)
    val isMuted:          StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isCameraOff      = MutableStateFlow(false)
    val isCameraOff:      StateFlow<Boolean> = _isCameraOff.asStateFlow()

    private val _isScreenSharing  = MutableStateFlow(false)
    val isScreenSharing:  StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    private val _chatMessages     = MutableStateFlow<List<Triple<String, String, Boolean>>>(emptyList())
    val chatMessages:     StateFlow<List<Triple<String, String, Boolean>>> = _chatMessages.asStateFlow()

    private val _handRaised       = MutableStateFlow<String?>(null)
    val handRaised:       StateFlow<String?> = _handRaised.asStateFlow()

    private val _teacherLeftBanner = MutableStateFlow(false)
    val teacherLeftBanner: StateFlow<Boolean> = _teacherLeftBanner.asStateFlow()

    private val _studentForceEnded = MutableStateFlow<String?>(null)
    val studentForceEnded: StateFlow<String?> = _studentForceEnded.asStateFlow()

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()

    val eglBase: EglBase = EglBase.create()

    private var signalingClient: SignalingClient? = null
    private var webRTCClient:    WebRTCClient?    = null
    private var isTeacher                         = false
    private var initialized                       = false
    private var currentUserName: String           = "Me"
    private var cachedIceServers: List<IceServerConfig>? = null

    private var localViewInitialized  = false
    private var remoteViewInitialized = false

    private var storedRoomId:    String = ""
    private var storedToken:     String = ""
    private var storedRole:      String = ""
    private var storedLocalView:  SurfaceViewRenderer? = null
    private var storedRemoteView: SurfaceViewRenderer? = null

    private var initJob: Job? = null
    private var pollingJob: Job? = null
    private var onAutoEnd: (() -> Unit)? = null

    private var teacherPaused = false

    // ── ICE servers: 20 min baad bhi fresh milein, cache mat karo ────────────
    private suspend fun fetchIceServers(): List<IceServerConfig> {
        return try {
            val response = RetrofitClient.api.getWebRTCConfig()
            if (response.isSuccessful && response.body()?.success == true) {
                cachedIceServers = response.body()!!.iceServers
                cachedIceServers!!
            } else {
                cachedIceServers ?: WebRTCIceServerConfig.DEFAULT_ICE_SERVERS
            }
        } catch (e: Exception) {
            Log.e("CallViewModel", "ICE fetch error: ${e.message}")
            cachedIceServers ?: WebRTCIceServerConfig.DEFAULT_ICE_SERVERS
        }
    }

    fun initCall(
        roomId:     String,
        token:      String,
        role:       String,
        localView:  SurfaceViewRenderer,
        remoteView: SurfaceViewRenderer,
        userName:   String = "Me"
    ) {
        if (initialized) {
            Log.d("CallViewModel", "initCall skipped — already initialized")
            return
        }
        initialized     = true
        teacherPaused   = false
        currentUserName = userName
        isTeacher       = (role == "teacher")

        storedRoomId     = roomId
        storedToken      = token
        storedRole       = role
        storedLocalView  = localView
        storedRemoteView = remoteView

        Log.d("CallViewModel", "initCall → roomId=$roomId | role=$role | user=$userName")

        initJob = viewModelScope.launch {
            val iceServers = fetchIceServers()
            val iceConfig  = WebRTCIceServerConfig.fromBackendConfig(iceServers)

            val client = SignalingClient(roomId)
            signalingClient = client

            val rtc = WebRTCClient(
                context         = getApplication(),
                eglBase         = eglBase,
                signalingClient = client,
                isTeacher       = isTeacher,
                iceConfig       = iceConfig
            )
            webRTCClient = rtc

            initializeViewsAndStart(localView, remoteView, client, rtc, token, role)
        }
    }

    fun reattachViews(localView: SurfaceViewRenderer, remoteView: SurfaceViewRenderer) {
        storedLocalView  = localView
        storedRemoteView = remoteView

        if (teacherPaused) {
            Log.d("CallViewModel", "Teacher returning from pause — reattaching views only")
            teacherPaused = false
            viewModelScope.launch(Dispatchers.Main) {
                webRTCClient?.setLocalView(localView)
                webRTCClient?.setRemoteView(remoteView)
            }
            return
        }

        if (!initialized && storedToken.isNotBlank() && storedRoomId.isNotBlank()) {
            Log.d("CallViewModel", "reattachViews — not initialized, reinitializing")
            initCall(
                roomId     = storedRoomId,
                token      = storedToken,
                role       = storedRole,
                localView  = localView,
                remoteView = remoteView,
                userName   = currentUserName
            )
            return
        }

        viewModelScope.launch(Dispatchers.Main) {
            webRTCClient?.setLocalView(localView)
            webRTCClient?.setRemoteView(remoteView)
        }
    }

    fun teacherPauseBack() {
        Log.d("CallViewModel", "teacherPauseBack()")
        if (_isScreenSharing.value) stopScreenShare()
        teacherPaused = true
    }

    private fun handlePeerLeft() {
        _participantCount.value = maxOf(1, _participantCount.value - 1)
        _connectionState.value  = if (isTeacher) "Waiting for student..." else "Waiting for teacher..."
        webRTCClient?.resetForNewPeer()
    }

    private fun handleNewViewer(peerId: String) {
        if (isTeacher) {
            Log.d("CallViewModel", "New viewer: $peerId — sending offer")
            viewModelScope.launch {
                delay(500)
                webRTCClient?.resetForNewPeer()
                delay(300)
                webRTCClient?.sendOfferToNewViewer()
            }
        }
    }

    private fun handleTeacherLeft() {
        Log.d("CallViewModel", "Teacher left signal received — ignoring on student side")
        webRTCClient?.resetForNewPeer()
    }

    private fun handleStudentForceEnd(reason: String) {
        Log.d("CallViewModel", "Student left — reason: $reason — auto-ending teacher side in 1s")
        _studentForceEnded.value = reason

        viewModelScope.launch {
            delay(1_000L)
            Log.d("CallViewModel", "Auto-ending call on teacher side after student left")
            endCall()
            _navigateBack.value = true
        }
    }

    // ── WebSocket reconnect pe room dobara join ───────────────────────────────
    private fun handleWebSocketReconnected() {
        if (!initialized || storedRoomId.isBlank() || storedToken.isBlank()) return
        Log.d("CallViewModel", "WebSocket reconnected — rejoining room: $storedRoomId role: $storedRole")
        viewModelScope.launch {
            delay(500) // socket stable hone do
            signalingClient?.joinRoom(storedRole)
        }
    }

    fun dismissTeacherLeftBanner() { _teacherLeftBanner.value = false }
    fun clearStudentForceEnded()   { _studentForceEnded.value = null  }
    fun clearNavigateBack()        { _navigateBack.value = false      }

    private fun initializeViewsAndStart(
        localView:  SurfaceViewRenderer,
        remoteView: SurfaceViewRenderer,
        client:     SignalingClient,
        rtc:        WebRTCClient,
        token:      String,
        role:       String
    ) {
        try {
            if (!localViewInitialized) {
                localView.init(eglBase.eglBaseContext, null)
                localView.setMirror(isTeacher)
                localViewInitialized = true
            }
        } catch (e: Exception) {
            Log.e("CallViewModel", "Local view init error: ${e.message}")
            try {
                localView.release()
                localView.init(eglBase.eglBaseContext, null)
                localView.setMirror(isTeacher)
                localViewInitialized = true
            } catch (e2: Exception) {
                Log.e("CallViewModel", "Local view re-init failed: ${e2.message}")
            }
        }

        try {
            if (!remoteViewInitialized) {
                remoteView.init(eglBase.eglBaseContext, null)
                remoteView.setMirror(false)
                remoteViewInitialized = true
            }
        } catch (e: Exception) {
            Log.e("CallViewModel", "Remote view init error: ${e.message}")
            try {
                remoteView.release()
                remoteView.init(eglBase.eglBaseContext, null)
                remoteView.setMirror(false)
                remoteViewInitialized = true
            } catch (e2: Exception) {
                Log.e("CallViewModel", "Remote view re-init failed: ${e2.message}")
            }
        }

        rtc.setLocalView(localView)
        rtc.setRemoteView(remoteView)

        client.onChatMessage = { sender, text ->
            val isFromMe      = (sender == currentUserName)
            val alreadyExists = _chatMessages.value.any { it.first == sender && it.second == text }
            if (!alreadyExists) {
                _chatMessages.value = _chatMessages.value + Triple(sender, text, isFromMe)
            }
        }

        rtc.onMessageReceived = { message ->
            try {
                val parts = if (message.startsWith("DCHAT:")) {
                    message.removePrefix("DCHAT:").split(":", limit = 2)
                } else {
                    message.split(":", limit = 2)
                }
                if (parts.size == 2) {
                    val sender        = parts[0]
                    val text          = parts[1]
                    val isFromMe      = (sender == currentUserName)
                    val alreadyExists = _chatMessages.value.any { it.first == sender && it.second == text }
                    if (!alreadyExists) {
                        _chatMessages.value = _chatMessages.value + Triple(sender, text, isFromMe)
                    }
                }
            } catch (e: Exception) {
                Log.e("CallViewModel", "DataChannel message error: ${e.message}")
            }
        }

        client.onConnected = {
            _connectionState.value = "JOINED"
            client.joinRoom(role)
        }

        client.onDisconnected = {
            _connectionState.value = "Disconnected"
        }

        client.onParticipantCount = { count -> _participantCount.value = count }

        client.onJoinedWithPeers = { peers ->
            if (isTeacher && peers.isNotEmpty()) {
                viewModelScope.launch {
                    delay(500)
                    rtc.resetForNewPeer()
                    delay(300)
                    rtc.sendOfferToNewViewer()
                }
            }
        }

        client.onNewViewer    = { peerId -> handleNewViewer(peerId) }
        client.onOffer        = { sdp    -> rtc.handleOffer(sdp) }
        client.onAnswer       = { sdp    -> rtc.handleAnswer(sdp) }
        client.onIceCandidate = { sdp, mid, index -> rtc.handleIceCandidate(sdp, mid, index) }

        client.onPeerLeft        = { handlePeerLeft() }
        client.onTeacherLeft     = { handleTeacherLeft() }
        client.onStudentForceEnd = { reason -> handleStudentForceEnd(reason) }

        // ── WebSocket reconnect handler ───────────────────────────────────────
        client.onReconnected = { handleWebSocketReconnected() }

        client.onHandRaised  = { name   -> _handRaised.value      = name   }
        client.onScreenShare = { active -> _isScreenSharing.value = active }

        rtc.onMediaReady = {
            Log.d("CallViewModel", "Media ready — connecting signaling")
            client.connect(token)
        }

        if (isTeacher) rtc.startAsTeacher() else rtc.startAsViewer()
    }

    fun teacherLeaveGracefully() {
        Log.d("CallViewModel", "teacherLeaveGracefully() — disabled, no action taken")
    }

    fun studentForceLeave(reason: String) {
        Log.d("CallViewModel", "studentForceLeave() — reason: $reason")
        signalingClient?.sendStudentForceEnd(reason)
        viewModelScope.launch {
            delay(300)
            endCall()
            _navigateBack.value = true
        }
    }

    fun startClassStatusPolling(classId: String, onEnded: () -> Unit) {
        onAutoEnd = onEnded
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(30_000L)
                try {
                    val response = RetrofitClient.api.getClassById(classId)
                    if (response.isSuccessful) {
                        val status = response.body()?.data?.status
                        Log.d("CallViewModel", "Polling class status: $status")
                        if (status == "completed" || status == "cancelled") {
                            Log.d("CallViewModel", "Class ended by backend — auto cutting")
                            endCall()
                            _navigateBack.value = true
                            onAutoEnd?.invoke()
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CallViewModel", "Polling error: ${e.message}")
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun toggleMute()   { val v = !_isMuted.value;     _isMuted.value     = v; webRTCClient?.toggleAudio(!v) }
    fun toggleCamera() { val v = !_isCameraOff.value; _isCameraOff.value = v; webRTCClient?.toggleVideo(!v) }
    fun switchCamera() = webRTCClient?.switchCamera()

    fun startScreenShare(resultCode: Int, data: Intent) {
        _isScreenSharing.value = true
        webRTCClient?.startScreenShare(resultCode, data)
        signalingClient?.sendScreenShare(true)
    }

    fun stopScreenShare() {
        _isScreenSharing.value = false
        webRTCClient?.stopScreenShare()
        signalingClient?.sendScreenShare(false)
    }

    fun sendChat(sender: String, text: String) {
        if (text.isBlank()) return
        _chatMessages.value = _chatMessages.value + Triple(sender, text, true)
        signalingClient?.sendChat(sender, text)
        webRTCClient?.sendMessage("DCHAT:$sender:$text")
    }

    fun raiseHand(name: String) = signalingClient?.sendHandRaise(name)
    fun dismissHandRaise()      { _handRaised.value = null }

    fun isInitialized(): Boolean = initialized

    fun endCall() {
        Log.d("CallViewModel", "endCall()")
        teacherPaused = false
        stopPolling()
        initJob?.cancel()
        initJob = null

        signalingClient?.leaveRoom(storedRole)
        webRTCClient?.disconnect()
        webRTCClient = null
        signalingClient?.disconnect()
        signalingClient = null

        initialized           = false
        localViewInitialized  = false
        remoteViewInitialized = false

        _connectionState.value   = "Connecting..."
        _isScreenSharing.value   = false
        _isMuted.value           = false
        _isCameraOff.value       = false
        _participantCount.value  = 1
        _handRaised.value        = null
        _teacherLeftBanner.value = false
        _studentForceEnded.value = null
    }

    override fun onCleared() {
        super.onCleared()
        initJob?.cancel()
        stopPolling()
        webRTCClient?.disconnect()
        signalingClient?.disconnect()
        try { eglBase.release() } catch (e: Exception) {
            Log.w("CallViewModel", "eglBase release error: ${e.message}")
        }
    }
}