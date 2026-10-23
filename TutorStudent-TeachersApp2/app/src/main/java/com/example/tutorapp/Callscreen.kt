package com.example.tutorapp.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.viewmodel.CallViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
    roomId: String,
    onLeave: () -> Unit,
    role: String,
    classId: String = "",
    onEarlyLeave: ((reason: String, minutesAttended: Int) -> Unit)? = null,
    sessionStartTime: Long = System.currentTimeMillis(),
    slotEndTime: Long = 0L
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val token = TokenManager.getToken(context) ?: ""
    val actualRole = TokenManager.getRole(context) ?: "student"
    val userName = remember {
        context.getSharedPreferences("eduvibe_prefs", android.content.Context.MODE_PRIVATE)
            .getString("user_name", "Me") ?: "Me"
    }

    var permissionsGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        permissionsGranted = (perms[Manifest.permission.CAMERA] == true) &&
                (perms[Manifest.permission.RECORD_AUDIO] == true)
    }

    LaunchedEffect(Unit) {
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasAudio  = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasCamera && hasAudio) {
            permissionsGranted = true
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    val callViewModel: CallViewModel = viewModel()
    val connectionState  by callViewModel.connectionState.collectAsState()
    val isMuted          by callViewModel.isMuted.collectAsState()
    val isCameraOff      by callViewModel.isCameraOff.collectAsState()
    val participantCount by callViewModel.participantCount.collectAsState()
    val handRaised       by callViewModel.handRaised.collectAsState()
    val chatMessages     by callViewModel.chatMessages.collectAsState()
    val isScreenSharing  by callViewModel.isScreenSharing.collectAsState()
    val teacherLeftBanner  by callViewModel.teacherLeftBanner.collectAsState()
    val studentForceEnded  by callViewModel.studentForceEnded.collectAsState()

    val localRenderer  = remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val remoteRenderer = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    // ✅ rememberSaveable — recomposition pe reset nahi hoga (screen share fix)
    var callInitialized by rememberSaveable { mutableStateOf(false) }

    var showChatOverlay      by remember { mutableStateOf(false) }
    var chatInputText        by remember { mutableStateOf("") }
    val chatListState        = rememberLazyListState()
    var isHandRaised         by remember { mutableStateOf(false) }
    var showEarlyLeaveDialog by remember { mutableStateOf(false) }
    var earlyLeaveReason     by remember { mutableStateOf("") }
    var isSubmittingLeave    by remember { mutableStateOf(false) }
    var timeRemainingSeconds by remember { mutableStateOf(0L) }
    var showEndingSoonBanner by remember { mutableStateOf(false) }

    // studentForceEnded → teacher navigate out
    LaunchedEffect(studentForceEnded) {
        if (studentForceEnded != null) {
            callViewModel.endCall()
            callViewModel.clearStudentForceEnded()
            onLeave()
        }
    }

    // ✅ FIX: navigateBack — student leave ya slot end pe dono side navigate out
    val navigateBack by callViewModel.navigateBack.collectAsState()
    LaunchedEffect(navigateBack) {
        if (navigateBack) {
            callViewModel.clearNavigateBack()
            onLeave()
        }
    }

    // Time slot timer
    LaunchedEffect(slotEndTime) {
        if (slotEndTime <= 0L) return@LaunchedEffect
        while (true) {
            val remaining = (slotEndTime - System.currentTimeMillis()) / 1000
            timeRemainingSeconds = maxOf(0L, remaining)
            showEndingSoonBanner = remaining in 1..300
            if (remaining <= 0) {
                callViewModel.endCall()
                onLeave()
                break
            }
            delay(1000L)
        }
    }

    // Backend polling: dono side ke liye — slot end pe teacher bhi cut hoga
    LaunchedEffect(classId) {
        if (classId.isBlank()) return@LaunchedEffect
        callViewModel.startClassStatusPolling(classId) { onLeave() }
    }

    DisposableEffect(Unit) {
        onDispose { callViewModel.stopPolling() }
    }

    LaunchedEffect(handRaised) {
        if (handRaised != null && actualRole == "student") {
            isHandRaised = true
            delay(5000)
            isHandRaised = false
            callViewModel.dismissHandRaise()
        }
    }

    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val screenShareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            callViewModel.startScreenShare(result.resultCode, result.data!!)
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) chatListState.animateScrollToItem(chatMessages.lastIndex)
    }

    // ✅ Key sirf permissionsGranted — ghost reconnect loop khatam
    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) return@LaunchedEffect
        if (callInitialized) return@LaunchedEffect

        var waited = 0
        while ((localRenderer.value == null || remoteRenderer.value == null) && waited < 50) {
            delay(50)
            waited++
        }

        val local  = localRenderer.value  ?: return@LaunchedEffect
        val remote = remoteRenderer.value ?: return@LaunchedEffect

        callInitialized = true

        if (callViewModel.isInitialized()) {
            callViewModel.reattachViews(local, remote)
        } else {
            callViewModel.initCall(
                roomId     = roomId,
                token      = token,
                role       = actualRole,
                localView  = local,
                remoteView = remote,
                userName   = userName
            )
        }
    }

    // ✅ FIX: endCall() karo — teacher rejoin kar sake (teacherPauseBack se initialized=true rehta tha)
    BackHandler {
        if (actualRole == "student" && onEarlyLeave != null) {
            showEarlyLeaveDialog = true
        } else if (actualRole == "teacher") {
            callViewModel.endCall()
            onLeave()
        }
    }

    // Early leave dialog
    if (showEarlyLeaveDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingLeave) { showEarlyLeaveDialog = false; earlyLeaveReason = "" }
            },
            containerColor    = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor  = Color.White,
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(24.dp))
                        Text(text = "Leaving the session?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Please provide a reason for your teacher", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val quickReasons = listOf("Emergency", "Internet problem", "Not feeling well")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Quick select:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickReasons.take(2).forEach { reason ->
                                FilterChip(
                                    selected = earlyLeaveReason == reason,
                                    onClick  = { earlyLeaveReason = reason },
                                    label    = { Text(reason, fontSize = 11.sp, maxLines = 1) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFF44336).copy(alpha = 0.2f),
                                        selectedLabelColor     = Color(0xFFF44336),
                                        containerColor         = Color.White.copy(alpha = 0.08f),
                                        labelColor             = Color.White.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickReasons.drop(2).forEach { reason ->
                                FilterChip(
                                    selected = earlyLeaveReason == reason,
                                    onClick  = { earlyLeaveReason = reason },
                                    label    = { Text(reason, fontSize = 11.sp, maxLines = 1) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFF44336).copy(alpha = 0.2f),
                                        selectedLabelColor     = Color(0xFFF44336),
                                        containerColor         = Color.White.copy(alpha = 0.08f),
                                        labelColor             = Color.White.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value         = earlyLeaveReason,
                        onValueChange = { earlyLeaveReason = it },
                        placeholder   = { Text("Or type your own reason...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        maxLines      = 3,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Color(0xFFF44336),
                            unfocusedBorderColor    = Color.White.copy(alpha = 0.3f),
                            focusedTextColor        = Color.White,
                            unfocusedTextColor      = Color.White,
                            cursorColor             = Color(0xFFF44336),
                            focusedContainerColor   = Color.White.copy(alpha = 0.05f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (earlyLeaveReason.isBlank()) return@Button
                        isSubmittingLeave = true
                        val minutesAttended = ((System.currentTimeMillis() - sessionStartTime) / 60_000).toInt()
                        val reason = earlyLeaveReason.trim()
                        scope.launch {
                            onEarlyLeave?.invoke(reason, minutesAttended)
                            callViewModel.studentForceLeave(reason)
                            delay(500)
                            showEarlyLeaveDialog = false
                            earlyLeaveReason     = ""
                            isSubmittingLeave    = false
                            onLeave()
                        }
                    },
                    enabled = earlyLeaveReason.isNotBlank() && !isSubmittingLeave,
                    colors  = ButtonDefaults.buttonColors(
                        containerColor         = Color(0xFFF44336),
                        disabledContainerColor = Color(0xFFF44336).copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSubmittingLeave) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Leave & Send", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEarlyLeaveDialog = false; earlyLeaveReason = "" }, enabled = !isSubmittingLeave) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }

    if (!permissionsGranted) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VideocamOff, null, tint = Color(0xFFE53935), modifier = Modifier.size(60.dp))
                Spacer(Modifier.height(12.dp))
                Text("Camera & Mic permission required", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Grant permissions to join the call", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) {
                    Text("Grant Permissions")
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // Remote view — full screen
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                SurfaceViewRenderer(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    if (remoteRenderer.value == null) remoteRenderer.value = this
                }
            },
            update = { view ->
                view.setZOrderMediaOverlay(false)
                view.setEnableHardwareScaler(true)
            }
        )

        // Local view
        if (actualRole == "teacher") {
            AndroidView(
                modifier = Modifier
                    .size(width = 160.dp, height = 220.dp)
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 120.dp)
                    .clip(RoundedCornerShape(16.dp)),
                factory  = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        if (localRenderer.value == null) localRenderer.value = this
                    }
                },
                update = { view ->
                    view.setZOrderMediaOverlay(true)
                    view.setEnableHardwareScaler(true)
                }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 310.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = "You", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        } else {
            AndroidView(
                modifier = Modifier
                    .size(width = 110.dp, height = 150.dp)
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp)),
                factory  = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        if (localRenderer.value == null) localRenderer.value = this
                    }
                },
                update = { view ->
                    view.setZOrderMediaOverlay(true)
                    view.setEnableHardwareScaler(true)
                }
            )
        }

        // Top bar
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopStart),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (connectionState == "JOINED") Color(0xFF4CAF50) else Color(0xFFFFC107)))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = connectionState, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (slotEndTime > 0L && timeRemainingSeconds > 0L) {
                    val minutes    = timeRemainingSeconds / 60
                    val seconds    = timeRemainingSeconds % 60
                    val timerColor = if (timeRemainingSeconds <= 300) Color(0xFFF44336) else Color.White
                    Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(text = "%02d:%02d".format(minutes, seconds), color = timerColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "$participantCount", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // Ending soon banner
        if (showEndingSoonBanner) {
            Box(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp, start = 24.dp, end = 24.dp)
                    .background(Color(0xFFF44336).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(text = "Class ending in ${timeRemainingSeconds / 60} min", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Hand raised banner
        handRaised?.let { name ->
            if (actualRole == "teacher") {
                val topPad = if (showEndingSoonBanner) 110.dp else 60.dp
                Box(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = topPad, start = 24.dp, end = 24.dp)
                        .background(Color(0xFFFF9800).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("✋", fontSize = 24.sp)
                        Column {
                            Text(text = "$name raised their hand", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = "Tap to dismiss", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { callViewModel.dismissHandRaise() }, modifier = Modifier.size(28.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Teacher left banner (student ke liye)
        if (teacherLeftBanner && actualRole == "student") {
            Box(
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = if (showEndingSoonBanner) 110.dp else 60.dp, start = 24.dp, end = 24.dp)
                    .background(Color(0xFFFF9800).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.PersonOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Teacher has stepped away", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Please wait, they will be back soon", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                    IconButton(onClick = { callViewModel.dismissTeacherLeftBanner() }, modifier = Modifier.size(24.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Screen sharing banner
        if (isScreenSharing) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ScreenShare, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                    Text(text = "Screen sharing active", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
            }
        }

        // Bottom controls
        Row(
            modifier              = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            ControlButton(icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, label = if (isMuted) "Unmute" else "Mute", tint = if (isMuted) Color(0xFFF44336) else Color.White, onClick = { callViewModel.toggleMute() })
            ControlButton(icon = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, label = if (isCameraOff) "Cam On" else "Cam Off", tint = if (isCameraOff) Color(0xFFF44336) else Color.White, onClick = { callViewModel.toggleCamera() })
            ControlButton(
                icon       = Icons.Default.CallEnd,
                label      = "Leave",
                tint       = Color.White,
                background = Color(0xFFF44336),
                onClick    = {
                    if (actualRole == "student" && onEarlyLeave != null) {
                        showEarlyLeaveDialog = true
                    } else if (actualRole == "teacher") {
                        // ✅ FIX: endCall karo — teacher rejoin kar sake
                        callViewModel.endCall()
                        onLeave()
                    }
                }
            )
            ControlButton(icon = if (showChatOverlay) Icons.Default.Close else Icons.Default.Chat, label = if (showChatOverlay) "Close Chat" else "Chat", tint = if (showChatOverlay) Color(0xFFFF9800) else Color.White, onClick = { showChatOverlay = !showChatOverlay })
            ControlButton(
                icon    = if (isScreenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                label   = if (isScreenSharing) "Stop" else "Share",
                tint    = if (isScreenSharing) Color(0xFFE53935) else Color.White,
                onClick = {
                    if (isScreenSharing) callViewModel.stopScreenShare()
                    else screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                }
            )
            if (actualRole == "teacher") {
                ControlButton(icon = Icons.Default.FlipCameraAndroid, label = "Flip", tint = Color.White, onClick = { callViewModel.switchCamera() })
            }
            if (actualRole == "student") {
                ControlButton(
                    icon    = if (isHandRaised) Icons.Default.PanTool else Icons.Default.PanToolAlt,
                    label   = if (isHandRaised) "Raised!" else "Hand",
                    tint    = if (isHandRaised) Color(0xFFFF9800) else Color.White,
                    onClick = { if (!isHandRaised) { callViewModel.raiseHand(userName); isHandRaised = true } }
                )
            }
        }

        // Chat overlay
        if (showChatOverlay) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth().heightIn(max = 400.dp)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("💬 Class Chat", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showChatOverlay = false }) { Icon(Icons.Default.Close, contentDescription = "Close Chat", tint = Color.White) }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
                LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp), state = chatListState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(chatMessages) { (sender, text, isFromMe) ->
                        val bubbleColor = if (isFromMe) Color(0xFFDCF8C6) else Color(0xFFFFFFFF)
                        val alignment   = if (isFromMe) Arrangement.End else Arrangement.Start
                        val cornerShape = if (isFromMe) RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 4.dp)
                        else RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 4.dp, bottomEnd = 12.dp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = alignment) {
                            Column(horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 280.dp)) {
                                Box(modifier = Modifier.clip(cornerShape).background(bubbleColor).border(width = if (isFromMe) 0.dp else 1.dp, color = if (isFromMe) Color.Transparent else Color(0xFFDDDDDD), shape = cornerShape).padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 260.dp)) {
                                    Text(text = text, fontSize = 14.sp, color = Color(0xFF111111))
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = chatInputText, onValueChange = { chatInputText = it },
                        placeholder = { Text("Type a message...", color = Color.White.copy(alpha = 0.6f)) },
                        singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF25D366), unfocusedBorderColor = Color.White.copy(alpha = 0.4f), focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color.Black.copy(alpha = 0.5f), unfocusedContainerColor = Color.Black.copy(alpha = 0.5f), cursorColor = Color.White),
                        keyboardOptions = KeyboardOptions.Default,
                        keyboardActions = KeyboardActions(onDone = { if (chatInputText.isNotBlank()) { callViewModel.sendChat(userName, chatInputText.trim()); chatInputText = "" } })
                    )
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).clip(CircleShape).background(if (chatInputText.isBlank()) Color.Gray else Color(0xFF25D366))) {
                        IconButton(onClick = { if (chatInputText.isNotBlank()) { callViewModel.sendChat(userName, chatInputText.trim()); chatInputText = "" } }, enabled = chatInputText.isNotBlank()) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControlButton(
    icon:       ImageVector,
    label:      String,
    tint:       Color = Color.White,
    background: Color = Color(0xFF424242),
    onClick:    () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            IconButton(onClick = onClick) { Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp)) }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, color = Color.White, fontSize = 11.sp)
    }
}