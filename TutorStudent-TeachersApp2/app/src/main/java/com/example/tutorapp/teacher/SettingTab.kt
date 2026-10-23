package com.example.tutorapp.teacher

import android.content.SharedPreferences
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tutorapp.SessionViewModel
import com.example.tutorapp.network.AppConfig
import com.example.tutorapp.network.LiveStatusRequest
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TeacherProfileData
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ngrok free-tier ke liye MANDATORY header
private const val NGROK_KEY   = "ngrok-skip-browser-warning"
private const val NGROK_VALUE = "true"

private data class SettingsMenuItem(
    val icon:       ImageVector,
    val label:      String,
    val route:      String,
    val badgeCount: Int = 0
)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SettingsTab(
    navController:    NavController,
    prefs:            SharedPreferences,
    sessionViewModel: SessionViewModel? = null
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var profile          by remember { mutableStateOf<TeacherProfileData?>(null) }
    var isProfileLoading by remember { mutableStateOf(true) }

    var isLive           by remember { mutableStateOf(false) }
    var showLiveDialog   by remember { mutableStateOf(false) }
    var pendingLiveValue by remember { mutableStateOf(false) }
    var isTogglingLive   by remember { mutableStateOf(false) }

    // ── Early leave badge count ─────────────────────────────────────────────
    val latestEarlyLeaveEvent by sessionViewModel
        ?.studentEarlyLeaveEvent
        ?.collectAsState()
        ?: remember { mutableStateOf(null) }

    var earlyLeaveCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(latestEarlyLeaveEvent) {
        if (latestEarlyLeaveEvent != null) {
            earlyLeaveCount++
        }
    }

    suspend fun fetchProfile() {
        try {
            val rawToken = TokenManager.getToken(context)
            if (rawToken.isNullOrBlank()) return
            val bearer = if (rawToken.startsWith("Bearer ")) rawToken else "Bearer $rawToken"
            val resp   = RetrofitClient.api.getTeacherProfile(bearer)
            if (resp.isSuccessful) {
                profile = resp.body()?.data
                isLive  = profile?.isLive == true
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsTab", "fetchProfile exception: ${e.message}", e)
        } finally {
            isProfileLoading = false
        }
    }

    LaunchedEffect(Unit) { fetchProfile() }

    val backStackEntry = navController.currentBackStackEntry
    LaunchedEffect(backStackEntry) {
        delay(350)
        fetchProfile()
    }

    // ── Notification badge ──────────────────────────────────────────────────
    var unreadCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            val raw  = TokenManager.getToken(context) ?: return@LaunchedEffect
            val tok  = if (raw.startsWith("Bearer ")) raw else "Bearer $raw"
            val resp = RetrofitClient.api.getNotifications(tok)
            if (resp.isSuccessful)
                unreadCount = resp.body()?.data?.count { !it.isRead } ?: 0
        } catch (_: Exception) {}
    }

    // ── Wallet ──────────────────────────────────────────────────────────────
    var walletBalance   by remember { mutableStateOf(0.0) }
    var isWalletLoading by remember { mutableStateOf(true) }

    suspend fun fetchWallet() {
        try {
            val raw  = TokenManager.getToken(context) ?: return
            val tok  = if (raw.startsWith("Bearer ")) raw else "Bearer $raw"
            val resp = RetrofitClient.api.getWallet(tok)
            if (resp.isSuccessful) walletBalance = resp.body()?.data?.balance ?: 0.0
        } catch (_: Exception) {
        } finally {
            isWalletLoading = false
        }
    }
    LaunchedEffect(Unit) { fetchWallet() }
    LaunchedEffect(backStackEntry) { delay(350); fetchWallet() }

    // ── Derived display values ──────────────────────────────────────────────
    val fullName   = profile?.fullName  ?: ""
    val bio        = profile?.bio       ?: ""
    val experience = profile?.experience?.toString() ?: ""
    val hourlyRate = profile?.pricing?.hourlyRate?.toString() ?: ""
    val subjects   = profile?.subjects  ?: emptyList()
    val languages  = profile?.languages ?: emptyList()
    val boards     = profile?.boards    ?: emptyList()

    // ── URL builder ─────────────────────────────────────────────────────────
    val profilePhotoUrl: String? = remember(profile?.profilePhoto) {
        val raw = profile?.profilePhoto
        when {
            raw.isNullOrBlank()    -> null
            raw.startsWith("http") -> raw
            else                   -> "${AppConfig.MEDIA_BASE}$raw"
        }
    }

    val introVideoUrl: String? = remember(profile?.introVideo) {
        val raw = profile?.introVideo
        when {
            raw.isNullOrBlank()      -> null
            raw.startsWith("0 mins") -> null
            raw.startsWith("http")   -> raw
            else                     -> "${AppConfig.MEDIA_BASE}$raw"
        }.also { android.util.Log.d("SettingsTab", "videoUrl → $it") }
    }

    var isVideoPlaying by remember { mutableStateOf(false) }

    val initials = fullName.trim().split(" ")
        .filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it[0].uppercaseChar().toString() }
        .ifEmpty { "?" }

    val menuItems = listOf(
        SettingsMenuItem(Icons.Filled.Notifications, "Notifications",        Routes.NOTIFICATIONS, unreadCount),
        SettingsMenuItem(Icons.Filled.Star,          "Reviews",               Routes.REVIEWS),
        // ✅ Early Leave Reports — badge shows real-time count
        SettingsMenuItem(Icons.Filled.ExitToApp,     "Early Leave Reports",   "EarlyLeaveReportsScreen", earlyLeaveCount),
        SettingsMenuItem(Icons.Filled.SupportAgent,  "Support",               Routes.SUPPORT),
    )

    // ── Go Live dialog ──────────────────────────────────────────────────────
    if (showLiveDialog) {
        AlertDialog(
            onDismissRequest = { showLiveDialog = false },
            title = { Text(if (pendingLiveValue) "Go Live?" else "Stop Live?") },
            text  = {
                Text(
                    if (pendingLiveValue)
                        "Students will be able to see and book your slots. Continue?"
                    else
                        "Students won't be able to book your slots until you're live again."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLiveDialog = false
                        isTogglingLive = true
                        scope.launch {
                            try {
                                val raw  = TokenManager.getToken(context) ?: return@launch
                                val tok  = if (raw.startsWith("Bearer ")) raw else "Bearer $raw"
                                val resp = RetrofitClient.api.toggleLiveStatus(tok, LiveStatusRequest(pendingLiveValue))
                                val ok   = resp.isSuccessful && resp.body()?.success != false
                                if (ok) {
                                    profile = profile?.copy(isLive = pendingLiveValue)
                                    isLive  = pendingLiveValue
                                    fetchProfile()
                                }
                            } catch (_: Exception) {
                            } finally { isTogglingLive = false }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingLiveValue) Color(0xFF4CAF50) else Color(0xFFE53935)
                    )
                ) { Text(if (pendingLiveValue) "Go Live" else "Stop") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLiveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // UI
    // ════════════════════════════════════════════════════════════════════════
    LazyColumn(
        modifier       = Modifier.fillMaxSize().background(EduBackground),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {

        // Loading bar
        if (isProfileLoading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color    = EduBlue
                )
            }
        }

        // ── 1. Profile Banner ───────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EduBlue)
                    .clickable { navController.navigate(Routes.PROFILE) }
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.size(68.dp)) {
                        if (profilePhotoUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(profilePhotoUrl)
                                    .crossfade(true)
                                    .addHeader(NGROK_KEY, NGROK_VALUE)
                                    .build(),
                                contentDescription = "Profile photo",
                                modifier     = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    initials,
                                    color      = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize   = 28.sp
                                )
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { navController.navigate("teacher_profile_edit") }
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit",
                                tint     = EduBlue,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = fullName.ifEmpty { "Your Name" },
                            color      = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 20.sp
                        )
                        if (bio.isNotEmpty()) {
                            Text(
                                text     = bio.take(60) + if (bio.length > 60) "…" else "",
                                color    = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                maxLines = 2
                            )
                        }
                        val infoLine = listOfNotNull(
                            experience.takeIf { it.isNotEmpty() }?.let { "${it}y exp" },
                            hourlyRate.takeIf { it.isNotEmpty() }?.let { "₹$it/hr" }
                        ).joinToString(" • ")
                        if (infoLine.isNotEmpty()) {
                            Text(infoLine, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                        }
                    }

                    Icon(
                        Icons.Filled.ChevronRight,
                        null,
                        tint     = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // ── 2. Subjects / Boards / Languages chips ──────────────────────────
        item {
            val hasChips = subjects.isNotEmpty() || boards.isNotEmpty() || languages.isNotEmpty()
            if (hasChips) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (subjects.isNotEmpty()) {
                        Text("Subjects", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EduBlue)
                        Row(
                            modifier              = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) { subjects.forEach { ChipBadge(it, EduBlue) } }
                    }
                    if (boards.isNotEmpty()) {
                        Text("Boards", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF6A1B9A))
                        Row(
                            modifier              = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) { boards.forEach { ChipBadge(it, Color(0xFF6A1B9A)) } }
                    }
                    if (languages.isNotEmpty()) {
                        Text("Languages", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF00796B))
                        Row(
                            modifier              = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) { languages.forEach { ChipBadge(it, Color(0xFF00796B)) } }
                    }
                }
                HorizontalDivider(color = EduBorder)
            }
        }

        // ── 3. Go Live toggle ───────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (isLive) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                        null,
                        tint     = if (isLive) Color(0xFF4CAF50) else EduGray,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            if (isLive) "You are LIVE 🔴" else "Go Live",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            color      = if (isLive) Color(0xFF4CAF50) else EduTextDark
                        )
                        Text(
                            if (isLive) "Students can see your slots" else "Enable to let students book slots",
                            fontSize = 12.sp,
                            color    = EduGray
                        )
                    }
                }
                if (isTogglingLive) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Switch(
                        checked         = isLive,
                        onCheckedChange = { pendingLiveValue = it; showLiveDialog = true },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = Color.White,
                            checkedTrackColor   = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = EduBorder
                        )
                    )
                }
            }
            HorizontalDivider(color = EduBorder)
        }

        // ── 4. Wallet card ──────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF1565C0), Color(0xFF42A5F5)))
                    )
                    .clickable { navController.navigate("TeacherWalletScreen") }
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("My Wallet", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        if (isWalletLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                "₹ ${String.format("%.2f", walletBalance)}",
                                color      = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize   = 20.sp
                            )
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        // ── 5. Intro Video ──────────────────────────────────────────────────
        item {
            if (introVideoUrl != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EduBlueLight)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.PlayCircle, null, tint = EduBlue, modifier = Modifier.size(20.dp))
                            Text("Intro Video", fontSize = 14.sp, color = EduBlue, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick        = { navController.navigate("teacher_profile_edit") },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.Edit, null, tint = EduBlue, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Change", fontSize = 12.sp, color = EduBlue)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    key(introVideoUrl) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(android.net.Uri.parse(introVideoUrl))
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    requestFocus()
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = false
                                        android.util.Log.d("SettingsTab", "Video prepared ✓")
                                    }
                                    setOnErrorListener { _, what, extra ->
                                        android.util.Log.e("SettingsTab", "Video error what=$what extra=$extra")
                                        false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap video controls to play • Tap Change to update",
                        fontSize = 11.sp,
                        color    = EduGray
                    )
                }
                HorizontalDivider(color = EduBorder)
            } else if (!isProfileLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .clickable { navController.navigate("teacher_profile_edit") }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3E0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.VideoCall, null, tint = Color(0xFFFF9800), modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("No Intro Video", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = EduTextDark)
                        Text("Add a video to attract more students", fontSize = 12.sp, color = EduGray)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = EduGray, modifier = Modifier.size(18.dp))
                }
                HorizontalDivider(color = EduBorder)
            }
        }

        item { Spacer(Modifier.height(10.dp)) }

        // ── 6. Menu items ───────────────────────────────────────────────────
        items(menuItems.size) { index ->
            val item = menuItems[index]
            ProfileMenuItem(
                icon       = item.icon,
                label      = item.label,
                badgeCount = item.badgeCount,
                onClick    = {
                    // Early leave screen pe jaane ke baad badge reset karo
                    if (item.route == "EarlyLeaveReportsScreen") {
                        earlyLeaveCount = 0
                        sessionViewModel?.dismissEarlyLeaveEvent()
                    }
                    navController.navigate(item.route)
                }
            )
            if (index < menuItems.lastIndex) {
                HorizontalDivider(color = EduBorder, modifier = Modifier.padding(start = 56.dp))
            }
        }

        // ── 7. Logout ───────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    context.getSharedPreferences("eduvibe_prefs", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("teacher_logged_in", false)
                        .putBoolean("needs_role_selection", false)
                        .remove("google_user_name")
                        .remove("google_user_email")
                        .apply()
                    navController.navigate("WelcomeScreen") { popUpTo(0) { inclusive = true } }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(48.dp),
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Icon(Icons.Filled.Logout, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Logout", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Chip ─────────────────────────────────────────────────────────────────────
@Composable
private fun ChipBadge(label: String, bgColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor.copy(alpha = 0.13f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = bgColor)
    }
}

// ── Menu row ──────────────────────────────────────────────────────────────────
@Composable
private fun ProfileMenuItem(
    icon:       ImageVector,
    label:      String,
    badgeCount: Int = 0,
    onClick:    () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EduBlueLight)
        ) {
            Icon(icon, null, tint = EduBlue, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            fontWeight = FontWeight.Medium,
            fontSize   = 15.sp,
            color      = EduTextDark,
            modifier   = Modifier.weight(1f)
        )
        if (badgeCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
            ) {
                Text(
                    if (badgeCount > 99) "99+" else "$badgeCount",
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.Filled.ChevronRight, null, tint = EduGray, modifier = Modifier.size(20.dp))
    }
}