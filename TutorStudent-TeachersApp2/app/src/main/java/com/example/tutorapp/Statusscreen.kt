package com.example.tutorapp

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.tutorapp.student.ReviewDialog
import com.example.tutorapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    navController: NavHostController,
    viewModel:     SessionViewModel
) {
    var selectedTab         by remember { mutableIntStateOf(1) }
    var selectedStatus      by remember { mutableIntStateOf(0) }
    var navigateToLiveClass by remember { mutableStateOf<String?>(null) }
    var snackbarMessage     by remember { mutableStateOf<String?>(null) }
    var isRefreshing        by remember { mutableStateOf(false) }
    var reviewSession       by remember { mutableStateOf<SharedSession?>(null) }
    var lastJoinedRoomId    by remember { mutableStateOf<String?>(null) }

    val allSessions    by viewModel.sessions.collectAsStateWithLifecycle()
    val isLoading      by viewModel.isLoading.collectAsStateWithLifecycle()
    val reviewedIds    by viewModel.reviewedClassIds.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope          = rememberCoroutineScope()

    fun onRefresh() {
        scope.launch {
            isRefreshing = true
            viewModel.loadStudentSessions(forceRefresh = true)
            viewModel.autoCompleteExpiredSessions()
            delay(500)
            isRefreshing = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val roomId = lastJoinedRoomId ?: return@LifecycleEventObserver
                lastJoinedRoomId = null
                val session = viewModel.sessions.value.find { it.roomId == roomId }
                if (session != null) {
                    when (session.status) {
                        ClassStatus.COMPLETED,
                        ClassStatus.MISSED -> {
                            if (!viewModel.reviewedClassIds.value.contains(session.roomId)) {
                                reviewSession = session
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(navigateToLiveClass) {
        navigateToLiveClass?.let { roomId ->
            navController.navigate("CallScreen/$roomId/student")
            navigateToLiveClass = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadStudentSessions(forceRefresh = false)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            viewModel.loadStudentSessions(forceRefresh = true)
            viewModel.autoCompleteExpiredSessions()
        }
    }

    LaunchedEffect(allSessions) {
        val hasOngoing  = allSessions.any { it.status == ClassStatus.ONGOING }
        val hasUpcoming = allSessions.any { it.status == ClassStatus.SCHEDULED }
        when {
            hasOngoing  -> selectedStatus = 0
            hasUpcoming -> selectedStatus = 1
        }
        if (reviewSession == null) {
            val autoSession = allSessions.firstOrNull { session ->
                (session.status == ClassStatus.COMPLETED || session.status == ClassStatus.MISSED) &&
                        !viewModel.reviewedClassIds.value.contains(session.roomId) &&
                        !viewModel.isReviewSkipped(session.roomId)
            }
            if (autoSession != null) reviewSession = autoSession
        }
    }

    val filteredClasses = remember(selectedStatus, allSessions) {
        val status = when (selectedStatus) {
            0    -> ClassStatus.ONGOING
            1    -> ClassStatus.SCHEDULED
            2    -> ClassStatus.COMPLETED
            else -> ClassStatus.MISSED
        }
        allSessions.filter { it.status == status }
    }

    reviewSession?.let { session ->
        if (!reviewedIds.contains(session.roomId)) {
            ReviewDialog(
                session   = session,
                viewModel = viewModel,
                onDismiss = {
                    viewModel.skipReview(session.roomId)
                    reviewSession = null
                }
            )
        } else {
            reviewSession = null
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite),
                title  = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier         = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(PrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("E", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("EduVibe", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                },
                actions = {
                    IconButton(onClick = { onRefresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextGray, modifier = Modifier.size(22.dp))
                    }
                    IconButton(
                        onClick  = { navController.navigate("NotificationScreen") },
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(LightBg)
                    ) {
                        Icon(Icons.Outlined.Notifications, null, tint = TextGray, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(15.dp))
                    IconButton(
                        onClick  = { navController.navigate("SettingsScreen") },
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(LightBg)
                    ) {
                        Icon(Icons.Default.Settings, null, tint = TextGray, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(16.dp))
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val tabs = listOf(
                    Triple("Home",     Icons.Default.Home,       "MentorScreen"),
                    Triple("Status",   Icons.Outlined.DateRange, ""),
                    Triple("Settings", Icons.Default.Settings,   "SettingsScreen")
                )
                tabs.forEachIndexed { index, (label, icon, route) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick  = {
                            selectedTab = index
                            if (route.isNotEmpty()) navController.navigate(route)
                        },
                        icon   = { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
                        label  = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = PrimaryBlue,
                            selectedTextColor   = PrimaryBlue,
                            unselectedIconColor = TextGray,
                            unselectedTextColor = TextGray,
                            indicatorColor      = Color(0xFFE8F0FE)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { onRefresh() },
            modifier     = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(LightBg)
            ) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .background(CardWhite)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusTab("On-Going",  selectedStatus == 0, Color(0xFFFF9800), Modifier.weight(1f)) { selectedStatus = 0 }
                    StatusTab("Upcoming",  selectedStatus == 1, PrimaryBlue,       Modifier.weight(1f)) { selectedStatus = 1 }
                    StatusTab("Completed", selectedStatus == 2, Color(0xFF22C55E), Modifier.weight(1f)) { selectedStatus = 2 }
                    StatusTab("Missed",    selectedStatus == 3, Color(0xFFE53935), Modifier.weight(1f)) { selectedStatus = 3 }
                }

                when {
                    isLoading && allSessions.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = PrimaryBlue)
                                Text("Loading classes...", fontSize = 13.sp, color = TextGray)
                            }
                        }
                    }

                    filteredClasses.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text     = when (selectedStatus) { 0 -> "📡"; 1 -> "📅"; 2 -> "✅"; else -> "📭" },
                                    fontSize = 40.sp
                                )
                                Text(
                                    "No classes here",
                                    fontSize   = 14.sp,
                                    color      = TextGray,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = when (selectedStatus) {
                                        0    -> "No live classes right now\nWait for teacher to start"
                                        1    -> "No upcoming classes\nTeacher hasn't created any session yet"
                                        2    -> "You haven't completed any classes yet"
                                        else -> "No missed classes — great job! 🎉"
                                    },
                                    fontSize  = 12.sp,
                                    color     = TextGray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { onRefresh() },
                                    shape   = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Refresh", fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier            = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            items(filteredClasses) { session ->
                                StudentSessionCard(
                                    session       = session,
                                    isReviewed    = reviewedIds.contains(session.roomId),
                                    onJoinLive    = {
                                        lastJoinedRoomId = session.roomId
                                        viewModel.joinSessionWithAttend(session.roomId, session.roomId)
                                        navigateToLiveClass = session.roomId
                                    },
                                    onRateSession = { reviewSession = session }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Status Tab ────────────────────────────────────────────────────────────────

@Composable
private fun StatusTab(
    label:    String,
    selected: Boolean,
    color:    Color,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit
) {
    Box(
        modifier         = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) color else Color.White)
            .border(1.5.dp, color, RoundedCornerShape(20.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color      = if (selected) Color.White else color
        )
    }
}

// ── Student Session Card ──────────────────────────────────────────────────────

@Composable
private fun StudentSessionCard(
    session:       SharedSession,
    isReviewed:    Boolean    = false,
    onJoinLive:    () -> Unit,
    onRateSession: () -> Unit = {}
) {
    val borderColor = when (session.status) {
        ClassStatus.ONGOING   -> Color(0xFFFF9800)
        ClassStatus.COMPLETED -> Color(0xFF22C55E)
        ClassStatus.MISSED    -> Color(0xFFE53935)
        ClassStatus.SCHEDULED -> PrimaryBlue
    }

    Card(
        modifier  = Modifier.fillMaxWidth().border(2.dp, borderColor, RoundedCornerShape(14.dp)),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column {
            // ── Card Header (Avatar / Photo area) ────────────────────────────
            Box {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(session.avatarColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                        // ── Profile pic OR fallback initial ──────────────────
                        if (!session.teacherPhoto.isNullOrBlank()) {
                            AsyncImage(
                                model             = session.teacherPhoto,
                                contentDescription = "Teacher photo",
                                contentScale      = ContentScale.Crop,
                                modifier          = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, session.avatarColor, CircleShape)
                            )
                        } else {
                            Box(
                                modifier         = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(session.avatarColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    session.studentName.first().toString(),
                                    fontSize   = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = Color.White
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(
                            session.studentName,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = TextPrimary
                        )
                    }
                }

                // ── Status badge (top-left) ───────────────────────────────────
                val (badgeColor, badgeText) = when (session.status) {
                    ClassStatus.ONGOING   -> Pair(Color(0xFFE53935), "● LIVE")
                    ClassStatus.COMPLETED -> Pair(Color(0xFF22C55E), if (isReviewed) "REVIEWED ✓" else "DONE ✓")
                    ClassStatus.MISSED    -> Pair(Color(0xFFE53935), "MISSED")
                    ClassStatus.SCHEDULED -> Pair(PrimaryBlue,       "UPCOMING")
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(badgeText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // ── Price badge (top-right) ───────────────────────────────────
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF22C55E))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text       = if (session.price > 0) "₹${session.price.toInt()}" else "Free",
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                }
            }

            // ── Card Body ────────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        session.subject,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color      = PrimaryBlue,
                        modifier   = Modifier.weight(1f)
                    )
                    Text(session.date, fontSize = 10.sp, color = TextGray)
                }
                Spacer(Modifier.height(2.dp))
                Text(session.time, fontSize = 11.sp, color = TextGray)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Book, null, tint = TextGray, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(session.topic, fontSize = 12.sp, color = TextGray, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DateRange, null, tint = TextGray, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(session.duration, fontSize = 12.sp, color = TextGray)
                }

                if (session.rating > 0) {
                    Spacer(Modifier.height(6.dp))
                    Row {
                        repeat(5) { index ->
                            Text(
                                text     = if (index < session.rating) "★" else "☆",
                                fontSize = 14.sp,
                                color    = if (index < session.rating) Color(0xFFFFC107) else Color(0xFFDDDDDD)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Action Buttons ───────────────────────────────────────────
                when (session.status) {
                    ClassStatus.ONGOING -> {
                        Button(
                            onClick        = { onJoinLive() },
                            shape          = RoundedCornerShape(20.dp),
                            colors         = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            modifier       = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Join Live", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    ClassStatus.SCHEDULED -> {
                        // No button for upcoming
                    }

                    ClassStatus.COMPLETED -> {
                        if (isReviewed) {
                            Button(
                                onClick        = {},
                                enabled        = false,
                                shape          = RoundedCornerShape(20.dp),
                                colors         = ButtonDefaults.buttonColors(
                                    disabledContainerColor = Color(0xFFE0E0E0),
                                    disabledContentColor   = Color(0xFF9E9E9E)
                                ),
                                modifier       = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reviewed", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick        = { onRateSession() },
                                shape          = RoundedCornerShape(20.dp),
                                colors         = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                modifier       = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp)
                            ) {
                                Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Rate Session", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    ClassStatus.MISSED -> {
                        if (isReviewed) {
                            Button(
                                onClick        = {},
                                enabled        = false,
                                shape          = RoundedCornerShape(20.dp),
                                colors         = ButtonDefaults.buttonColors(
                                    disabledContainerColor = Color(0xFFE0E0E0),
                                    disabledContentColor   = Color(0xFF9E9E9E)
                                ),
                                modifier       = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reviewed", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick        = { onRateSession() },
                                shape          = RoundedCornerShape(20.dp),
                                colors         = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                modifier       = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp)
                            ) {
                                Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Rate Session", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}