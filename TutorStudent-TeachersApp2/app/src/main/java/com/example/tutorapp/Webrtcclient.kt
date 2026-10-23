package com.example.tutorapp.webrtc

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.tutorapp.network.IceServerConfig as BackendIceServerConfig
import com.example.tutorapp.ScreenShareService
import com.example.tutorapp.websocket.SignalingClient
import org.webrtc.*
import java.nio.ByteBuffer

data class IceServerConfig(
    val iceServers: List<BackendIceServerConfig> = DEFAULT_ICE_SERVERS
) {
    companion object {
        val DEFAULT_ICE_SERVERS = listOf(
            BackendIceServerConfig("stun:stun.l.google.com:19302"),
            BackendIceServerConfig("stun:stun1.l.google.com:19302"),
            BackendIceServerConfig(
                urls       = "turn:d8c1f7788f90e6154a37646d.metered.live:80",
                username   = "d8c1f7788f90e6154a37646d",
                credential = "lADDxMCD9rHhvsrB"
            ),
            BackendIceServerConfig(
                urls       = "turn:d8c1f7788f90e6154a37646d.metered.live:443",
                username   = "d8c1f7788f90e6154a37646d",
                credential = "lADDxMCD9rHhvsrB"
            ),
            BackendIceServerConfig(
                urls       = "turn:d8c1f7788f90e6154a37646d.metered.live:443?transport=tcp",
                username   = "d8c1f7788f90e6154a37646d",
                credential = "lADDxMCD9rHhvsrB"
            )
        )

        fun fromBackendConfig(iceServers: List<BackendIceServerConfig>) =
            IceServerConfig(DEFAULT_ICE_SERVERS + iceServers)
    }
}

class WebRTCClient(
    private val context: Context,
    private val eglBase: EglBase,
    private val signalingClient: SignalingClient,
    private val isTeacher: Boolean,
    private val iceConfig: IceServerConfig = IceServerConfig()
) {
    private val TAG = "WebRTCClient"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val iceServers: List<PeerConnection.IceServer> by lazy {
        iceConfig.iceServers.map { cfg ->
            PeerConnection.IceServer.builder(cfg.urls)
                .apply {
                    cfg.username?.let   { setUsername(it) }
                    cfg.credential?.let { setPassword(it) }
                }
                .createIceServer()
        }.also { list ->
            Log.d(TAG, "ICE servers configured: ${list.size}")
            iceConfig.iceServers.forEach { Log.d(TAG, "  - ${it.urls}") }
        }
    }

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    private var peerConnection:        PeerConnection?        = null
    private var localVideoTrack:       VideoTrack?            = null
    private var localAudioTrack:       AudioTrack?            = null
    private var localView:             SurfaceViewRenderer?   = null
    private var remoteView:            SurfaceViewRenderer?   = null
    private var cameraVideoCapturer:   CameraVideoCapturer?   = null
    private var screenCapturerAndroid: ScreenCapturerAndroid? = null
    private var screenVideoSource:     VideoSource?           = null
    private var screenVideoTrack:      VideoTrack?            = null
    private var localVideoSender:      RtpSender?             = null

    private val remoteVideoTracks = mutableListOf<VideoTrack>()

    private var isLocalVideoTrackDisposed = false
    private var isDisconnecting           = false
    private var isScreenSharing           = false
    private var offerSent                 = false
    private var remoteDescriptionSet      = false
    private var renegotiationPending      = false
    private var iceRestartPending         = false

    // FIX: track pending renegotiation runnable so we can cancel stale ones
    private var pendingRenegotiationRunnable: Runnable? = null

    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    private var dataChannel: DataChannel? = null
    var onMessageReceived: ((String) -> Unit)? = null
    var onMediaReady:      (() -> Unit)?       = null

    fun setLocalView(renderer: SurfaceViewRenderer) {
        localView = renderer
        mainHandler.post {
            if (!isLocalVideoTrackDisposed && localVideoTrack != null) {
                try {
                    localVideoTrack!!.removeSink(renderer)
                    localVideoTrack!!.addSink(renderer)
                    Log.d(TAG, "setLocalView: local track re-attached to new renderer")
                } catch (e: Exception) {
                    Log.e(TAG, "setLocalView sink error: ${e.message}")
                }
            }
        }
    }

    fun setRemoteView(renderer: SurfaceViewRenderer) {
        remoteView = renderer
        mainHandler.post {
            remoteVideoTracks.forEach { track ->
                try {
                    track.removeSink(renderer)
                    track.addSink(renderer)
                    Log.d(TAG, "setRemoteView: re-attached existing remote track")
                } catch (e: Exception) {
                    Log.e(TAG, "setRemoteView sink error: ${e.message}")
                }
            }
        }
    }

    private fun enableSpeakerphone() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = true
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            Log.d(TAG, "Speakerphone enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Speakerphone error: ${e.message}")
        }
    }

    fun startAsTeacher() {
        Log.d(TAG, "startAsTeacher()")
        offerSent = false
        renegotiationPending = false
        iceRestartPending = false
        isLocalVideoTrackDisposed = false
        isDisconnecting = false
        buildPeerConnection()
        createDataChannel()
        startLocalMedia()
        enableSpeakerphone()
    }

    fun startAsViewer() {
        Log.d(TAG, "startAsViewer()")
        isLocalVideoTrackDisposed = false
        isDisconnecting = false
        iceRestartPending = false
        buildPeerConnection()
        startLocalMediaAsViewer()
        enableSpeakerphone()
    }

    fun sendOfferToNewViewer() {
        if (offerSent) { Log.d(TAG, "sendOfferToNewViewer — already sent, skipping"); return }
        val pc = peerConnection ?: run { Log.e(TAG, "peerConnection null!"); return }

        if (localAudioTrack == null) {
            Log.w(TAG, "sendOfferToNewViewer — audio track not ready"); return
        }

        offerSent = true
        renegotiationPending = false
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(SdpObserverAdapter(), sdp)
                signalingClient.sendOffer(sdp.description)
                Log.d(TAG, "Offer sent")
            }
            override fun onCreateFailure(error: String) {
                offerSent = false
                Log.e(TAG, "createOffer failed: $error")
            }
        }, constraints)
    }

    fun handleOffer(sdpDescription: String) {
        val pc = peerConnection ?: return
        remoteDescriptionSet = false
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                drainPendingIceCandidates()
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
                pc.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(answerSdp: SessionDescription) {
                        pc.setLocalDescription(SdpObserverAdapter(), answerSdp)
                        signalingClient.sendAnswer(answerSdp.description)
                        Log.d(TAG, "Answer sent")
                    }
                    override fun onCreateFailure(error: String) { Log.e(TAG, "createAnswer failed: $error") }
                }, constraints)
            }
            override fun onSetFailure(error: String) { Log.e(TAG, "setRemoteDesc(offer) failed: $error") }
        }, SessionDescription(SessionDescription.Type.OFFER, sdpDescription))
    }

    fun handleAnswer(sdpDescription: String) {
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                drainPendingIceCandidates()
                Log.d(TAG, "Remote answer set OK")
            }
            override fun onSetFailure(error: String) { Log.e(TAG, "setRemoteDesc(answer) failed: $error") }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdpDescription))
    }

    fun handleIceCandidate(candidateSdp: String, sdpMid: String, sdpMLineIndex: Int) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
        if (remoteDescriptionSet) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            pendingIceCandidates.add(candidate)
            Log.d(TAG, "ICE queued — size: ${pendingIceCandidates.size}")
        }
    }

    private fun drainPendingIceCandidates() {
        pendingIceCandidates.forEach { peerConnection?.addIceCandidate(it) }
        pendingIceCandidates.clear()
    }

    fun toggleAudio(enabled: Boolean) { localAudioTrack?.setEnabled(enabled) }
    fun toggleVideo(enabled: Boolean) {
        if (!isLocalVideoTrackDisposed) localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        cameraVideoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                mainHandler.post { localView?.setMirror(isFrontCamera) }
            }
            override fun onCameraSwitchError(error: String) { Log.e(TAG, "Camera switch failed: $error") }
        })
    }

    // FIX: helper to cancel any pending renegotiation and schedule a fresh one
    private fun scheduleRenegotiation(delayMs: Long, label: String) {
        pendingRenegotiationRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingRenegotiationRunnable = null
            if (!isDisconnecting && !isScreenSharing) {
                Log.d(TAG, "[$label] Sending renegotiation offer (camera restore)")
                sendOfferToNewViewer()
            } else if (!isDisconnecting && isScreenSharing) {
                Log.d(TAG, "[$label] Sending renegotiation offer (screen share)")
                sendOfferToNewViewer()
            }
        }
        pendingRenegotiationRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    fun startScreenShare(resultCode: Int, data: Intent) {
        if (isDisconnecting) return
        Log.d(TAG, "startScreenShare() — starting foreground service first")

        val serviceIntent = Intent(context, ScreenShareService::class.java).apply {
            action = ScreenShareService.ACTION_START_SCREEN_SHARE
            putExtra(ScreenShareService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenShareService.EXTRA_DATA, data)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        mainHandler.postDelayed({
            if (!isDisconnecting) {
                startScreenCaptureInternal(resultCode, data)
            }
        }, 300)
    }

    private fun startScreenCaptureInternal(resultCode: Int, data: Intent) {
        try {
            Log.d(TAG, "startScreenCaptureInternal()")

            val videoSource = factory.createVideoSource(true)
            screenVideoSource = videoSource

            val capturer = ScreenCapturerAndroid(data, object : android.media.projection.MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped externally")
                    if (!isDisconnecting) mainHandler.post { stopScreenShare() }
                }
            })
            screenCapturerAndroid = capturer

            val surfaceHelper = SurfaceTextureHelper.create("ScreenCapThread", eglBase.eglBaseContext)
            capturer.initialize(surfaceHelper, context, videoSource.capturerObserver)
            capturer.startCapture(1280, 720, 15)
            Log.d(TAG, "Screen capture started: 1280x720@15fps")

            val track = factory.createVideoTrack("SCREEN_TRACK", videoSource)
            screenVideoTrack = track
            track.setEnabled(true)
            isScreenSharing = true

            if (!isLocalVideoTrackDisposed) localVideoTrack?.setEnabled(false)

            try {
                localVideoSender?.setTrack(track, false)
                Log.d(TAG, "Screen track set on sender")

                localVideoSender?.let { sender ->
                    val params = sender.parameters
                    if (params.encodings.isNotEmpty()) {
                        params.encodings.forEach { enc ->
                            enc.maxBitrateBps = 1_500_000
                            enc.maxFramerate  = 15
                        }
                        sender.parameters = params
                        Log.d(TAG, "Screen share encoding optimized")
                    }
                }

                // FIX: reset offerSent and use longer delay (500ms) so WS is stable
                offerSent = false
                scheduleRenegotiation(500, "startScreenShare")

            } catch (e: Exception) {
                Log.e(TAG, "setTrack(screen) error: ${e.message}")
            }

            signalingClient.sendScreenShare(true)

        } catch (e: Exception) {
            Log.e(TAG, "startScreenCaptureInternal failed: ${e.message}")
            isScreenSharing = false
        }
    }

    fun stopScreenShare() {
        if (!isScreenSharing || isDisconnecting) {
            isScreenSharing = false
            return
        }
        isScreenSharing = false
        Log.d(TAG, "stopScreenShare()")

        // Cancel any in-flight renegotiation from screen share start
        pendingRenegotiationRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRenegotiationRunnable = null

        context.startService(Intent(context, ScreenShareService::class.java).apply {
            action = ScreenShareService.ACTION_STOP_SCREEN_SHARE
        })

        try { screenCapturerAndroid?.stopCapture() } catch (_: Exception) {}
        try { screenCapturerAndroid?.dispose() }     catch (_: Exception) {}
        screenCapturerAndroid = null

        try { screenVideoSource?.dispose() } catch (_: Exception) {}
        screenVideoSource = null

        try { screenVideoTrack?.setEnabled(false) } catch (_: Exception) {}
        try { screenVideoTrack?.dispose() }          catch (_: Exception) {}
        screenVideoTrack = null

        // FIX: Background thread mein camera recreate — delay kam hoga
        if (!isLocalVideoTrackDisposed) {
            Thread {
                try {
                    Log.d(TAG, "Recreating camera (background thread)...")

                    try { cameraVideoCapturer?.stopCapture() } catch (_: Exception) {}
                    try { cameraVideoCapturer?.dispose() }     catch (_: Exception) {}
                    cameraVideoCapturer = null

                    val newVideoSource   = factory.createVideoSource(false)
                    val newSurfaceHelper = SurfaceTextureHelper.create("CameraThread2", eglBase.eglBaseContext)
                    val enumerator       = Camera2Enumerator(context)
                    val cameraName       = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                        ?: enumerator.deviceNames.firstOrNull()

                    if (cameraName == null) { Log.e(TAG, "No camera found"); return@Thread }

                    val newCapturer = enumerator.createCapturer(cameraName, null) as CameraVideoCapturer
                    cameraVideoCapturer = newCapturer
                    newCapturer.initialize(newSurfaceHelper, context, newVideoSource.capturerObserver)
                    newCapturer.startCapture(1280, 720, 30)
                    Log.d(TAG, "New camera capturer started")

                    val newTrack = factory.createVideoTrack("VIDEO_TRACK_RESTORED", newVideoSource)
                    newTrack.setEnabled(true)

                    mainHandler.post {
                        try {
                            localView?.let {
                                try { localVideoTrack?.removeSink(it) } catch (_: Exception) {}
                                newTrack.addSink(it)
                            }
                            localVideoSender?.setTrack(newTrack, false)

                            try { localVideoTrack?.setEnabled(false) } catch (_: Exception) {}
                            try { localVideoTrack?.dispose() }          catch (_: Exception) {}
                            localVideoTrack = newTrack

                            localVideoSender?.let { sender ->
                                val params = sender.parameters
                                if (params.encodings.isNotEmpty()) {
                                    params.encodings.forEach { enc ->
                                        enc.maxBitrateBps = 1_000_000
                                        enc.maxFramerate  = 30
                                    }
                                    sender.parameters = params
                                }
                            }

                            offerSent = false
                            scheduleRenegotiation(200, "stopScreenShare")
                            Log.d(TAG, "Camera recreated — renegotiation in 200ms")
                        } catch (e: Exception) {
                            Log.e(TAG, "Camera UI swap error: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Camera recreate failed: ${e.message}")
                    mainHandler.post { isLocalVideoTrackDisposed = true }
                }
            }.start()
        }

        signalingClient.sendScreenShare(false)
        Log.d(TAG, "Screen share stopped")
    }

    fun sendMessage(message: String) {
        try {
            val buffer = ByteBuffer.wrap(message.toByteArray())
            dataChannel?.send(DataChannel.Buffer(buffer, false))
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage error: ${e.message}")
        }
    }

    fun resetForNewPeer() {
        offerSent = false
        renegotiationPending = false
        iceRestartPending = false
        pendingIceCandidates.clear()
        remoteDescriptionSet = false
        Log.d(TAG, "Reset for new peer — offerSent=false, remoteDescriptionSet=false")
    }

    fun disconnect() {
        if (isDisconnecting) { Log.w(TAG, "Already disconnecting"); return }
        isDisconnecting = true
        Log.d(TAG, "disconnect()")

        // FIX: cancel any pending renegotiation before cleanup
        pendingRenegotiationRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRenegotiationRunnable = null

        isScreenSharing = false
        try {
            context.startService(Intent(context, ScreenShareService::class.java).apply {
                action = ScreenShareService.ACTION_STOP_SCREEN_SHARE
            })
        } catch (_: Exception) {}

        try { screenCapturerAndroid?.stopCapture() } catch (_: Exception) {}
        try { screenCapturerAndroid?.dispose() }     catch (_: Exception) {}
        screenCapturerAndroid = null
        try { screenVideoSource?.dispose() }         catch (_: Exception) {}
        screenVideoSource = null
        try { screenVideoTrack?.setEnabled(false) }  catch (_: Exception) {}
        try { screenVideoTrack?.dispose() }           catch (_: Exception) {}
        screenVideoTrack = null

        try { cameraVideoCapturer?.stopCapture() }   catch (_: Exception) {}
        try { cameraVideoCapturer?.dispose() }        catch (_: Exception) {}
        cameraVideoCapturer = null

        isLocalVideoTrackDisposed = true
        try { localVideoTrack?.setEnabled(false) }   catch (_: Exception) {}
        try { localVideoTrack?.dispose() }            catch (_: Exception) {}
        localVideoTrack = null
        try { localAudioTrack?.setEnabled(false) }   catch (_: Exception) {}
        try { localAudioTrack?.dispose() }            catch (_: Exception) {}
        localAudioTrack = null
        localVideoSender = null

        remoteVideoTracks.clear()

        try { dataChannel?.close() }                 catch (_: Exception) {}
        try { dataChannel?.dispose() }               catch (_: Exception) {}
        dataChannel = null

        try { peerConnection?.close() }              catch (_: Exception) {}
        try { peerConnection?.dispose() }             catch (_: Exception) {}
        peerConnection = null

        remoteDescriptionSet = false
        offerSent            = false
        renegotiationPending = false
        iceRestartPending    = false
        pendingIceCandidates.clear()
        Log.d(TAG, "Disconnected & cleaned up")
    }

    private fun buildPeerConnection() {
        if (peerConnection != null) return
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics             = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peerConnection = factory.createPeerConnection(config, object : PeerConnection.Observer {

            override fun onIceCandidate(candidate: IceCandidate) {
                signalingClient.sendIceCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track() ?: run {
                    Log.e(TAG, "onTrack — track is null!"); return
                }
                Log.d(TAG, "onTrack: kind=${track.kind()}, id=${track.id()}")
                if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                    val videoTrack = track as VideoTrack

                    if (!remoteVideoTracks.contains(videoTrack)) {
                        remoteVideoTracks.add(videoTrack)
                    }

                    mainHandler.post {
                        remoteView?.let {
                            try { videoTrack.removeSink(it) } catch (_: Exception) {}
                            videoTrack.addSink(it)
                            Log.d(TAG, "Remote video sink attached")
                        } ?: Log.e(TAG, "remoteView is NULL — cannot display video!")
                    }
                }
            }

            override fun onDataChannel(dc: DataChannel) {
                dataChannel = dc
                dc.registerObserver(dataChannelObserver())
                Log.d(TAG, "DataChannel received")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE state: $state")
                when (state) {
                    PeerConnection.IceConnectionState.FAILED -> {
                        offerSent = false
                        renegotiationPending = false
                        if (!isDisconnecting && !iceRestartPending) {
                            iceRestartPending = true
                            mainHandler.post {
                                val pc = peerConnection
                                if (pc != null && !isDisconnecting) {
                                    Log.d(TAG, "ICE FAILED — calling restartIce()")
                                    pc.restartIce()
                                    if (isTeacher) {
                                        offerSent = false
                                        mainHandler.postDelayed({
                                            iceRestartPending = false
                                            if (!isDisconnecting) {
                                                Log.d(TAG, "Sending offer after ICE restart")
                                                sendOfferToNewViewer()
                                            }
                                        }, 500)
                                    } else {
                                        mainHandler.postDelayed({
                                            iceRestartPending = false
                                        }, 500)
                                    }
                                } else {
                                    iceRestartPending = false
                                }
                            }
                        }
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        offerSent = false
                        renegotiationPending = false
                        if (!isDisconnecting && !iceRestartPending) {
                            iceRestartPending = true
                            mainHandler.postDelayed({
                                val pc = peerConnection
                                if (pc != null && !isDisconnecting) {
                                    val iceState = pc.iceConnectionState()
                                    if (iceState == PeerConnection.IceConnectionState.DISCONNECTED ||
                                        iceState == PeerConnection.IceConnectionState.FAILED) {
                                        Log.d(TAG, "ICE still DISCONNECTED after 5s — restarting")
                                        pc.restartIce()
                                        if (isTeacher) {
                                            offerSent = false
                                            mainHandler.postDelayed({
                                                iceRestartPending = false
                                                if (!isDisconnecting) sendOfferToNewViewer()
                                            }, 500)
                                        } else {
                                            mainHandler.postDelayed({ iceRestartPending = false }, 500)
                                        }
                                    } else {
                                        Log.d(TAG, "ICE recovered on its own — state: $iceState")
                                        iceRestartPending = false
                                    }
                                } else {
                                    iceRestartPending = false
                                }
                            }, 5000)
                        }
                    }
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        iceRestartPending = false
                        Log.d(TAG, "ICE connected successfully")
                    }
                    else -> {}
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                Log.d(TAG, "Connection state: $newState")
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded — ignored (manual offer flow)")
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState)            {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState)      {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>)        {}
            override fun onIceConnectionReceivingChange(receiving: Boolean)                  {}
            override fun onAddStream(stream: MediaStream)                                    {}
            override fun onRemoveStream(stream: MediaStream)                                 {}
        })
        Log.d(TAG, "PeerConnection built")
    }

    private fun createDataChannel() {
        dataChannel = peerConnection?.createDataChannel("CHAT_CHANNEL", DataChannel.Init())
        dataChannel?.registerObserver(dataChannelObserver())
        Log.d(TAG, "DataChannel created")
    }

    private fun dataChannelObserver() = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) {}
        override fun onStateChange() { Log.d(TAG, "DataChannel state: ${dataChannel?.state()}") }
        override fun onMessage(buffer: DataChannel.Buffer) {
            val bytes = ByteArray(buffer.data.remaining())
            buffer.data.get(bytes)
            mainHandler.post { onMessageReceived?.invoke(String(bytes)) }
        }
    }

    private fun startLocalMedia() {
        val pc = peerConnection ?: run { Log.e(TAG, "startLocalMedia: pc null"); return }
        Log.d(TAG, "startLocalMedia() — TEACHER")

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl",  "true"))
        }
        localAudioTrack = factory.createAudioTrack("AUDIO_TRACK", factory.createAudioSource(audioConstraints))
        localAudioTrack?.setEnabled(true)
        pc.addTrack(localAudioTrack!!, listOf("TEACHER_AUDIO"))

        val videoSource   = factory.createVideoSource(false)
        val surfaceHelper = SurfaceTextureHelper.create("CameraThread", eglBase.eglBaseContext)
        val enumerator    = Camera2Enumerator(context)
        val cameraName    = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()

        if (cameraName == null) { Log.e(TAG, "No camera found"); return }

        val capturer = enumerator.createCapturer(cameraName, null) as CameraVideoCapturer
        cameraVideoCapturer = capturer
        capturer.initialize(surfaceHelper, context, videoSource.capturerObserver)
        capturer.startCapture(1280, 720, 30)

        localVideoTrack = factory.createVideoTrack("VIDEO_TRACK", videoSource)
        localVideoTrack?.setEnabled(true)
        mainHandler.post { localView?.let { localVideoTrack?.addSink(it) } }

        localVideoSender = pc.addTrack(localVideoTrack!!, listOf("TEACHER_VIDEO"))
        Log.d(TAG, "Teacher media setup COMPLETE")
        mainHandler.post { onMediaReady?.invoke() }
    }

    private fun startLocalMediaAsViewer() {
        val pc = peerConnection ?: run { Log.e(TAG, "startLocalMediaAsViewer: pc null"); return }
        Log.d(TAG, "startLocalMediaAsViewer() — STUDENT")

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl",  "true"))
        }
        localAudioTrack = factory.createAudioTrack("AUDIO_TRACK_STUDENT", factory.createAudioSource(audioConstraints))
        localAudioTrack?.setEnabled(true)
        pc.addTrack(localAudioTrack!!, listOf("STUDENT_AUDIO"))

        val videoSource   = factory.createVideoSource(false)
        val surfaceHelper = SurfaceTextureHelper.create("StudentCamThread", eglBase.eglBaseContext)
        val enumerator    = Camera2Enumerator(context)
        val cameraName    = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()

        if (cameraName == null) { Log.e(TAG, "No camera found for student"); return }

        val capturer = enumerator.createCapturer(cameraName, null) as CameraVideoCapturer
        cameraVideoCapturer = capturer
        capturer.initialize(surfaceHelper, context, videoSource.capturerObserver)
        capturer.startCapture(640, 480, 20)

        localVideoTrack = factory.createVideoTrack("VIDEO_TRACK_STUDENT", videoSource)
        localVideoTrack?.setEnabled(true)
        mainHandler.post { localView?.let { localVideoTrack?.addSink(it) } }

        localVideoSender = pc.addTrack(localVideoTrack!!, listOf("STUDENT_VIDEO"))
        Log.d(TAG, "Student media setup COMPLETE")
        mainHandler.post { onMediaReady?.invoke() }
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String) { Log.e("SdpObserver", "Create failed: $error") }
        override fun onSetFailure(error: String)    { Log.e("SdpObserver", "Set failed: $error")    }
    }
}