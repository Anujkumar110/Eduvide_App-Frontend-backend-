package com.example.tutorapp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.ui.screen.ControlButton
import com.example.tutorapp.ui.theme.*
import com.example.tutorapp.webrtc.WebRTCClient
import com.example.tutorapp.websocket.SignalingClient
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer


// ═══════════════════════════════════════════════════════════════════
// DEPRECATION NOTICE
// This screen is deprecated. Use CallScreen from ui.screen package instead.
// CallScreen provides better state management via CallViewModel and consistent UX.
// TODO: Migrate all navigation to CallScreen and remove this file.
// ═══════════════════════════════════════════════════════════════════

@RequiresApi(Build.VERSION_CODES.O)
@Deprecated("Use CallScreen instead. This screen will be removed in a future release.")
@Composable
fun LiveClassScreen(
    navController: NavHostController,
    roomId: String,
    role: String = "teacher",
    viewModel: SessionViewModel
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val eglBase = remember { EglBase.create() }

    val signalingClient = remember { SignalingClient(roomId) }
    val webRTCClient    = remember { WebRTCClient(context, eglBase, signalingClient, isTeacher = true) }

    var isMicOn          by remember { mutableStateOf(true) }
    var isCameraOn       by remember { mutableStateOf(true) }
    var isScreenSharing  by remember { mutableStateOf(false) }
    var isChatOpen       by remember { mutableStateOf(false) }
    var chatInput        by remember { mutableStateOf("") }
    var isConnected      by remember { mutableStateOf(false) }
    var connectionError  by remember { mutableStateOf(false) }
    var participantCount by remember { mutableStateOf(0) }
    val chatMessages     = remember { mutableStateListOf<ChatMessage>() }
    val listState        = rememberLazyListState()
    var isEndedByTeacher by remember { mutableStateOf(false) }

    // ✅ FIX: Track karo kya pehle se connect ho chuke hain
    // Reconnect pe startAsTeacher() dobara mat chalao
    val hasInitialized = remember { mutableStateOf(false) }

    val localRendererRef  = remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val remoteRendererRef = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    val screenShareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            isScreenSharing = true
            webRTCClient.startScreenShare(result.resultCode, result.data!!)
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        permissionsGranted =
            perms[Manifest.permission.CAMERA] == true &&
                    perms[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) return@LaunchedEffect

        val token = TokenManager.getBearerToken(context)

        signalingClient.onConnected = {
            isConnected     = true
            connectionError = false

            if (!hasInitialized.value) {
                // ✅ FIX: Pehli baar — sab initialize karo
                hasInitialized.value = true
                webRTCClient.startAsTeacher()
                localRendererRef.value?.let  { webRTCClient.setLocalView(it)  }
                remoteRendererRef.value?.let { webRTCClient.setRemoteView(it) }
                viewModel.updateStatusOnBackend(roomId, "ongoing")
                signalingClient.notifySessionStarted(className = roomId)
            } else {
                // ✅ FIX: Reconnect case — WebRTC state reset karo, media dobara mat banao
                android.util.Log.d("LiveClass", "Reconnected — resetting WebRTC state")
                webRTCClient.resetForNewPeer()
                localRendererRef.value?.let  { webRTCClient.setLocalView(it)  }
                remoteRendererRef.value?.let { webRTCClient.setRemoteView(it) }
            }

            // joinRoom hamesha karo — fresh ya reconnect dono pe
            signalingClient.joinRoom("teacher")
        }

        signalingClient.onDisconnected = {
            isConnected     = false
            connectionError = true
        }

        signalingClient.onNewViewer = { peerId ->
            android.util.Log.d("LiveClass", "New viewer joined: $peerId")
            // ✅ FIX: resetForNewPeer() pehle karo — offerSent=false ho
            // Phir offer bhejo — ek hi baar jayega kyunki sendOfferToNewViewer() guard karta hai
            webRTCClient.resetForNewPeer()
            webRTCClient.sendOfferToNewViewer()
        }

        signalingClient.onJoinedWithPeers = { peers ->
            android.util.Log.d("LiveClass", "Joined with existing peers: $peers")
            // ✅ FIX: resetForNewPeer() mat karo yahan — onConnected mein already ho chuka hai
            // Dobara reset karne se offerSent=false hoga aur onRenegotiationNeeded bhi offer bhejega
            // → duplicate offer ban jaata tha
            if (peers.isNotEmpty()) {
                webRTCClient.sendOfferToNewViewer()
            }
        }

        signalingClient.onPeerLeft = {
            android.util.Log.d("LiveClass", "Peer left — resetting for next join")
            webRTCClient.resetForNewPeer()
            participantCount = maxOf(0, participantCount - 1)
        }

        signalingClient.onAnswer = { sdp ->
            webRTCClient.handleAnswer(sdp)
        }

        signalingClient.onIceCandidate = { c, mid, idx ->
            webRTCClient.handleIceCandidate(c, mid, idx)
        }

        signalingClient.onParticipantCount = { count ->
            participantCount = count
        }

        webRTCClient.onMessageReceived = { message ->
            chatMessages.add(ChatMessage("Student (DC)", message, isMe = false))
            scope.launch {
                if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.lastIndex)
            }
        }

        signalingClient.onChatMessage = { sender, text ->
            chatMessages.add(ChatMessage(sender, text, isMe = false))
            scope.launch {
                if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.lastIndex)
            }
        }

        signalingClient.onHandRaised = { name ->
            chatMessages.add(ChatMessage("System", "✋ $name raised hand", isMe = false))
        }

        signalingClient.connect(token)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isEndedByTeacher) {
                viewModel.updateStatus(roomId, ClassStatus.COMPLETED)
                viewModel.updateStatusOnBackend(roomId, "completed")
                viewModel.loadTeacherSessions()
            }
            webRTCClient.disconnect()
            signalingClient.disconnect()
            eglBase.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            factory = { ctx ->
                SurfaceViewRenderer(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    init(eglBase.eglBaseContext, null)
                    setMirror(true)
                    setEnableHardwareScaler(true)
                    localRendererRef.value = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isCameraOn && !isScreenSharing) {
            Box(
                modifier         = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideocamOff, null, tint = Color.Gray, modifier = Modifier.size(60.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Camera Off", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }

        if (!isConnected) {
            Box(
                modifier         = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (connectionError) {
                        Icon(Icons.Default.WifiOff, null, tint = Color(0xFFE53935), modifier = Modifier.size(48.dp))
                        Text("Connection Failed", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(EduBlue)
                                .clickable {
                                    connectionError = false
                                    signalingClient.connect(TokenManager.getBearerToken(context))
                                }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("Retry", color = Color.White, fontWeight = FontWeight.SemiBold) }
                    } else if (!permissionsGranted) {
                        Icon(Icons.Default.VideocamOff, null, tint = Color(0xFFE53935), modifier = Modifier.size(48.dp))
                        Text("Camera & Mic permission chahiye", color = Color.White, fontSize = 14.sp)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(EduBlue)
                                .clickable {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                                }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("Allow", color = Color.White, fontWeight = FontWeight.SemiBold) }
                    } else {
                        CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 3.dp)
                        Text("Starting Live Class...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Room: $roomId", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        }

        // ── Top Bar ──────────────────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 40.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            IconButton(
                onClick  = { navController.popBackStack() },
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
            ) { Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp)) }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) Color(0xFFE53935) else Color.Gray)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isConnected) "LIVE" else if (connectionError) "ERROR" else "...",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text("$participantCount", color = Color.White, fontSize = 11.sp)
            }
        }

        // Student ka remote video — top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 100.dp, end = 12.dp)
                .size(width = 120.dp, height = 160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A1A))
                .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        init(eglBase.eglBaseContext, null)
                        setMirror(false)
                        setEnableHardwareScaler(true)
                        remoteRendererRef.value = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Student", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Bottom Controls ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            if (isChatOpen) {
                ChatPanel(
                    messages  = chatMessages,
                    listState = listState,
                    input     = chatInput,
                    onInput   = { chatInput = it },
                    onSend    = {
                        if (chatInput.isNotBlank()) {
                            val msg = chatInput
                            chatMessages.add(ChatMessage("You (Teacher)", msg, isMe = true))
                            webRTCClient.sendMessage(msg)
                            signalingClient.sendChat("Teacher", msg)
                            chatInput = ""
                            scope.launch {
                                if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.lastIndex)
                            }
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                ControlButton(
                    if (isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                    if (isMicOn) "Mute" else "Unmute",
                    if (isMicOn) Color.White else Color(0xFFE53935),
                    Color.Black.copy(alpha = 0.6f)
                ) { isMicOn = !isMicOn; webRTCClient.toggleAudio(isMicOn) }

                ControlButton(
                    if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    if (isCameraOn) "Cam Off" else "Cam On",
                    if (isCameraOn) Color.White else Color(0xFFE53935),
                    Color.Black.copy(alpha = 0.6f)
                ) { isCameraOn = !isCameraOn; webRTCClient.toggleVideo(isCameraOn) }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                            .clickable {
                                isEndedByTeacher = true
                                navController.popBackStack()
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                    Spacer(Modifier.height(4.dp))
                    Text("End", color = Color.White, fontSize = 10.sp)
                }

                ControlButton(
                    if (isScreenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                    if (isScreenSharing) "Stop" else "Share",
                    if (isScreenSharing) Color(0xFFE53935) else Color.White,
                    Color.Black.copy(alpha = 0.6f)
                ) {
                    if (isScreenSharing) {
                        isScreenSharing = false
                        webRTCClient.stopScreenShare()
                    } else {
                        screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    }
                }

                ControlButton(
                    Icons.Default.Chat, "Chat",
                    if (isChatOpen) PrimaryBlue else Color.White,
                    Color.Black.copy(alpha = 0.6f)
                ) { isChatOpen = !isChatOpen }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// CHAT PANEL
// ═══════════════════════════════════════════════════════════════════
@Composable
fun ChatPanel(
    messages:  SnapshotStateList<ChatMessage>,
    listState: LazyListState,
    input:     String,
    onInput:   (String) -> Unit,
    onSend:    () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(12.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Live Chat", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("${messages.size} messages", color = Color.Gray, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(messages) { msg ->
                val bubbleColor  = if (msg.isMe) Color(0xFFDCF8C6) else Color(0xFFFFFFFF)
                val isTeacherMsg = msg.sender.contains("Teacher", ignoreCase = true)
                val labelColor   = if (isTeacherMsg) Color(0xFF53BDEB) else Color(0xFF25D366)

                Column(
                    modifier            = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start
                ) {
                    Text(msg.sender, color = labelColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(bubbleColor)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(msg.text, color = Color(0xFF111111), fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF222222))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = input,
                onValueChange = onInput,
                placeholder   = { Text("Type a message...", color = Color.Gray, fontSize = 12.sp) },
                modifier      = Modifier.weight(1f),
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White
                )
            )
            IconButton(onClick = onSend) {
                Icon(Icons.Default.Send, null, tint = Color(0xFF25D366))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// STUDENT LIVE SCREEN
// ═══════════════════════════════════════════════════════════════════
@Deprecated("Use CallScreen instead. This screen will be removed in a future release.")
@Composable
fun StudentLiveScreen(navController: NavHostController, roomId: String) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val eglBase = remember { EglBase.create() }

    val signalingClient = remember { SignalingClient(roomId) }
    val webRTCClient    = remember { WebRTCClient(context, eglBase, signalingClient, isTeacher = false) }

    var isMicOn         by remember { mutableStateOf(true) }
    var isCameraOn      by remember { mutableStateOf(true) }
    var isChatOpen      by remember { mutableStateOf(false) }
    var chatInput       by remember { mutableStateOf("") }
    var isConnected     by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf(false) }
    val chatMessages    = remember { mutableStateListOf<ChatMessage>() }
    val listState       = rememberLazyListState()

    val remoteRendererRef = remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val localRendererRef  = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    // ✅ FIX: Student ke liye bhi reconnect track karo
    val hasInitialized = remember { mutableStateOf(false) }

    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        permissionsGranted =
            perms[Manifest.permission.CAMERA] == true &&
                    perms[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) return@LaunchedEffect

        val token = TokenManager.getBearerToken(context)

        signalingClient.onConnected = {
            isConnected     = true
            connectionError = false

            if (!hasInitialized.value) {
                // ✅ FIX: Pehli baar — media initialize karo
                hasInitialized.value = true
                webRTCClient.startAsViewer()
                remoteRendererRef.value?.let { webRTCClient.setRemoteView(it) }
                localRendererRef.value?.let  { webRTCClient.setLocalView(it)  }
            } else {
                // ✅ FIX: Reconnect — sirf state reset karo, media dobara mat banao
                android.util.Log.d("StudentLive", "Reconnected — resetting WebRTC state")
                webRTCClient.resetForNewPeer()
                remoteRendererRef.value?.let { webRTCClient.setRemoteView(it) }
                localRendererRef.value?.let  { webRTCClient.setLocalView(it)  }
            }

            signalingClient.joinRoom("student")
        }

        signalingClient.onDisconnected = { isConnected = false; connectionError = true }

        signalingClient.onPeerLeft = {
            isConnected = false
            android.util.Log.d("StudentLive", "Teacher left the room")
        }

        signalingClient.onOffer = { sdp ->
            // ✅ FIX: handleOffer ke andar remoteDescriptionSet reset hota hai
            // Duplicate offers bhi safe hain — har offer pe fresh answer jayega
            webRTCClient.handleOffer(sdp)
        }

        signalingClient.onIceCandidate = { c, mid, idx ->
            webRTCClient.handleIceCandidate(c, mid, idx)
        }

        webRTCClient.onMessageReceived = { message ->
            chatMessages.add(ChatMessage("Teacher (DC)", message, isMe = false))
            scope.launch {
                if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.lastIndex)
            }
        }

        signalingClient.onChatMessage = { sender, text ->
            chatMessages.add(ChatMessage(sender, text, isMe = false))
            scope.launch {
                if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.lastIndex)
            }
        }

        signalingClient.connect(token)
    }

    DisposableEffect(Unit) {
        onDispose {
            webRTCClient.disconnect()
            signalingClient.disconnect()
            eglBase.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            factory = { ctx ->
                SurfaceViewRenderer(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    init(eglBase.eglBaseContext, null)
                    setMirror(false)
                    setEnableHardwareScaler(true)
                    remoteRendererRef.value = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isConnected) {
            Box(
                modifier         = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (connectionError) {
                        Icon(Icons.Default.WifiOff, null, tint = Color(0xFFE53935), modifier = Modifier.size(48.dp))
                        Text("Connection Failed", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(EduBlue)
                                .clickable {
                                    connectionError = false
                                    signalingClient.connect(TokenManager.getBearerToken(context))
                                }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("Retry", color = Color.White, fontWeight = FontWeight.SemiBold) }
                    } else {
                        CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 3.dp)
                        Text("Joining Live Class...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Room: $roomId", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        }

        // Student ka local camera — top right small preview
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 12.dp)
                .size(width = 120.dp, height = 160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A1A))
                .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            if (isCameraOn) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            init(eglBase.eglBaseContext, null)
                            setMirror(true)
                            setEnableHardwareScaler(true)
                            localRendererRef.value = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier         = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.VideocamOff, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("You", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Bottom Controls ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            if (isChatOpen) {
                ChatPanel(
                    messages  = chatMessages,
                    listState = listState,
                    input     = chatInput,
                    onInput   = { chatInput = it },
                    onSend    = {
                        if (chatInput.isNotBlank()) {
                            val msg = chatInput
                            chatMessages.add(ChatMessage("You", msg, isMe = true))
                            webRTCClient.sendMessage(msg)
                            signalingClient.sendChat("Student", msg)
                            chatInput = ""
                            scope.launch {
                                if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.lastIndex)
                            }
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                ControlButton(
                    if (isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                    if (isMicOn) "Mute" else "Unmute",
                    if (isMicOn) Color.White else Color(0xFFE53935),
                    Color.Black.copy(alpha = 0.6f)
                ) { isMicOn = !isMicOn; webRTCClient.toggleAudio(isMicOn) }

                ControlButton(
                    if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    if (isCameraOn) "Cam Off" else "Cam On",
                    if (isCameraOn) Color.White else Color(0xFFE53935),
                    Color.Black.copy(alpha = 0.6f)
                ) { isCameraOn = !isCameraOn; webRTCClient.toggleVideo(isCameraOn) }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                            .clickable { navController.popBackStack() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                    Spacer(Modifier.height(4.dp))
                    Text("Leave", color = Color.White, fontSize = 10.sp)
                }

                ControlButton(
                    Icons.Default.Chat, "Chat",
                    if (isChatOpen) PrimaryBlue else Color.White,
                    Color.Black.copy(alpha = 0.6f)
                ) { isChatOpen = !isChatOpen }
            }
        }
    }
}