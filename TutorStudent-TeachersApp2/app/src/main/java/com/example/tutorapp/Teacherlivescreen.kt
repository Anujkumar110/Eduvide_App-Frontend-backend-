package com.example.tutorapp

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.tutorapp.webrtc.WebRTCClient
import com.example.tutorapp.websocket.SignalingClient
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

/**
 * DEPRECATION NOTICE
 * This screen is deprecated. Use CallScreen from ui.screen package instead.
 * CallScreen provides better state management via CallViewModel and consistent UX.
 * TODO: Migrate all navigation to CallScreen and remove this file.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Deprecated("Use CallScreen instead. This screen will be removed in a future release.")
@Composable
fun TeacherLiveScreen(
    navController: NavHostController,
    roomId: String,
    viewModel: SessionViewModel
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val eglBase = remember { EglBase.create() }

    val signalingClient = remember { SignalingClient(roomId) }
    val webRTCClient    = remember { WebRTCClient(context, eglBase, signalingClient, isTeacher = true) }

    var isMicOn          by remember { mutableStateOf(true) }
    var isCameraOn       by remember { mutableStateOf(true) }
    var isChatOpen       by remember { mutableStateOf(false) }
    var isLive           by remember { mutableStateOf(false) }
    var connectionError  by remember { mutableStateOf(false) }
    var chatInput        by remember { mutableStateOf("") }
    var participantCount by remember { mutableStateOf(0) }
    var handRaisedBy     by remember { mutableStateOf<String?>(null) }
    val chatMessages     = remember { mutableStateListOf<ChatMessage>() }
    val listState        = rememberLazyListState()

    val localRendererRef  = remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val remoteRendererRef = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[Manifest.permission.CAMERA] ?: false
        hasAudioPermission  = perms[Manifest.permission.RECORD_AUDIO] ?: false
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }

        val token = TokenManager.getBearerToken(context)

        signalingClient.onConnected = {
            isLive          = true
            connectionError = false
            signalingClient.joinRoom("teacher")

            webRTCClient.startAsTeacher()
            localRendererRef.value?.let  { webRTCClient.setLocalView(it)  }
            remoteRendererRef.value?.let { webRTCClient.setRemoteView(it) }

            viewModel.updateStatusOnBackend(roomId, "ongoing")
            signalingClient.notifySessionStarted(className = roomId)
        }

        signalingClient.onDisconnected = {
            isLive          = false
            connectionError = true
        }

        signalingClient.onNewViewer = { _ ->
            webRTCClient.sendOfferToNewViewer()
        }

        signalingClient.onJoinedWithPeers = { _ ->
            webRTCClient.sendOfferToNewViewer()
        }

        signalingClient.onAnswer = { sdpString ->
            webRTCClient.handleAnswer(sdpString)
        }

        signalingClient.onIceCandidate = { candidate, sdpMid, sdpMLineIndex ->
            webRTCClient.handleIceCandidate(candidate, sdpMid, sdpMLineIndex)
        }

        signalingClient.onParticipantCount = { count ->
            participantCount = count
        }

        signalingClient.onChatMessage = { sender, text ->
            chatMessages.add(ChatMessage(sender, text, isMe = false))
            scope.launch {
                if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.lastIndex)
            }
        }

        signalingClient.onHandRaised = { studentName ->
            handRaisedBy = studentName
        }

        signalingClient.connect(token)
    }

    DisposableEffect(Unit) {
        onDispose {
            webRTCClient.disconnect()
            signalingClient.disconnect()
            eglBase.release()

            android.util.Log.d("TeacherLive", " Cleaned up WebRTC resources (session status unchanged)")
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

        if (!isCameraOn) {
            Box(
                modifier         = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideocamOff, null, tint = Color.Gray, modifier = Modifier.size(60.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Camera Off", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }

        if (connectionError) {
            Box(
                modifier         = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.WifiOff, null, tint = Color(0xFFE53935), modifier = Modifier.size(48.dp))
                    Text("Connection Failed", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE53935))
                            .clickable {
                                connectionError = false
                                val token = TokenManager.getBearerToken(context)
                                signalingClient.connect(token)
                            }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Retry", color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        // ── Top bar ──────────────────────────────────────────────────────────
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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isLive) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFE53935))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Gray)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text     = if (connectionError) "Disconnected" else "Connecting...",
                            color    = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Room: $roomId", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("$participantCount Students", color = Color.White, fontSize = 11.sp)
            }
        }

        // Student ka remote video — top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 110.dp, end = 12.dp)
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

        // ── Hand raised notification ─────────────────────────────────────────
        handRaisedBy?.let { name ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFF9800).copy(alpha = 0.95f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable { handRaisedBy = null }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✋", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "$name raised hand",
                        color      = Color.White,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }

        // ── Bottom controls ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            if (isChatOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
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
                        Text("${chatMessages.size} messages", color = Color.Gray, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(chatMessages) { msg ->
                            //Exact WhatsApp: apna → Light green #DCF8C6 black text | doosra → White black text
                            val bubbleColor  = if (msg.isMe) Color(0xFFDCF8C6) else Color(0xFFFFFFFF)
                            val isTeacherMsg = msg.sender.contains("Teacher", ignoreCase = true)
                            val labelColor   = if (isTeacherMsg) Color(0xFF53BDEB) else Color(0xFF25D366)

                            Column(
                                modifier            = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start
                            ) {
                                Text(
                                    msg.sender,
                                    color      = labelColor,
                                    fontSize   = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF222222))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value         = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder   = { Text("Message to students...", color = Color.Gray, fontSize = 12.sp) },
                            modifier      = Modifier.weight(1f),
                            singleLine    = true,
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor     = Color.White,
                                unfocusedTextColor   = Color.White
                            )
                        )
                        IconButton(onClick = {
                            if (chatInput.isNotBlank()) {
                                chatMessages.add(ChatMessage("You (Teacher)", chatInput, isMe = true))
                                signalingClient.sendChat("Teacher", chatInput)
                                chatInput = ""
                                scope.launch {
                                    if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.lastIndex)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Send, null, tint = Color(0xFF25D366))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("End", color = Color.White, fontSize = 10.sp)
                }

                ControlButton(
                    Icons.Default.Chat, "Chat",
                    if (isChatOpen) Color(0xFF25D366) else Color.White,
                    Color.Black.copy(alpha = 0.6f)
                ) { isChatOpen = !isChatOpen }

                ControlButton(
                    Icons.Default.SwitchCamera, "Flip",
                    Color.White,
                    Color.Black.copy(alpha = 0.6f)
                ) { webRTCClient.switchCamera() }
            }
        }
    }
}