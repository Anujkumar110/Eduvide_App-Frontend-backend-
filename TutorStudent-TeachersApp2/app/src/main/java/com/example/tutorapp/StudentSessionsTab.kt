package com.example.tutorapp.student

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tutorapp.ClassStatus
import com.example.tutorapp.SessionViewModel
import com.example.tutorapp.SharedSession
import com.example.tutorapp.ui.theme.*
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun StudentSessionsTab(
    viewModel:     SessionViewModel,
    onJoinSession: (roomId: String, role: String) -> Unit,
    isTabSelected: Boolean = true
) {
    val allSessions   by viewModel.sessions.collectAsStateWithLifecycle()
    val isLoading     by viewModel.isLoading.collectAsStateWithLifecycle()
    val pendingReview by viewModel.pendingReviewSession.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs        = listOf("Upcoming", "Ongoing", "Completed", "Missed")

    val tabCounts = remember(allSessions) {
        listOf(
            allSessions.count { it.status == ClassStatus.SCHEDULED },
            allSessions.count { it.status == ClassStatus.ONGOING },
            allSessions.count { it.status == ClassStatus.COMPLETED },
            allSessions.count { it.status == ClassStatus.MISSED }
        )
    }

    val filteredSessions = remember(selectedTab, allSessions) {
        when (selectedTab) {
            0    -> allSessions.filter { it.status == ClassStatus.SCHEDULED }
            1    -> allSessions.filter { it.status == ClassStatus.ONGOING }
            2    -> allSessions.filter { it.status == ClassStatus.COMPLETED }
            else -> allSessions.filter { it.status == ClassStatus.MISSED }
        }
    }

    // Initial load
    LaunchedEffect(isTabSelected) {
        if (isTabSelected) viewModel.loadStudentSessions(forceRefresh = false)
    }

    // Auto-refresh every 15 sec
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            if (isTabSelected) {
                viewModel.loadStudentSessions(forceRefresh = true)
                // Ongoing session hai toh tab switch karo
                val hasOngoing = allSessions.any { it.status == ClassStatus.ONGOING }
                if (hasOngoing && selectedTab == 0) selectedTab = 1
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize().background(EduBackground)) {

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Color.White,
                contentColor     = EduBlue,
                edgePadding      = 0.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text = {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text       = title,
                                    fontSize   = 14.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.SemiBold
                                    else FontWeight.Normal,
                                    color      = if (selectedTab == index) EduBlue else EduGray
                                )
                                if (tabCounts[index] > 0) {
                                    Text(
                                        text       = "${tabCounts[index]}",
                                        fontSize   = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = if (selectedTab == index) Color.White else EduGray,
                                        modifier   = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                if (selectedTab == index) EduBlue else EduBorder
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }

            HorizontalDivider(color = EduBorder)

            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EduBlue)
                    }
                }
                filteredSessions.isEmpty() -> {
                    EmptySessionState(tabIndex = selectedTab, tabName = tabs[selectedTab])
                }
                else -> {
                    LazyColumn(
                        contentPadding      = PaddingValues(
                            start  = 16.dp,
                            end    = 16.dp,
                            top    = 16.dp,
                            bottom = 90.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = filteredSessions,
                            key   = { it.roomId }          // stable key — recomposition avoid
                        ) { session ->
                            StudentSessionCard(
                                session       = session,
                                onJoinClick   = {
                                    viewModel.joinSessionWithAttend(session.roomId, session.roomId)
                                    onJoinSession(session.roomId, "student")
                                },
                                onReviewClick = {
                                    viewModel.triggerReviewIfNeeded(session)
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Review Dialog overlay ─────────────────────────────────────────────
        pendingReview?.let { session ->
            ReviewDialog(
                session   = session,
                viewModel = viewModel,
                onDismiss = { /* viewModel already handles dismiss internally */ }
            )
        }
    }
}

// ── Empty State ───────────────────────────────────────────────────────────────
@Composable
private fun EmptySessionState(tabIndex: Int, tabName: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = when (tabIndex) {
                    0    -> "📅"; 1 -> "🟢"; 2 -> "✅"; else -> "😞"
                },
                fontSize = 52.sp
            )
            Text(
                "No ${tabName.lowercase()} sessions",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Medium,
                color      = EduTextDark
            )
            Text(
                text = when (tabIndex) {
                    0    -> "Enroll in a class to see upcoming sessions"
                    1    -> "No live classes right now"
                    2    -> "Completed sessions will appear here"
                    else -> "No missed sessions 🎉"
                },
                fontSize = 13.sp,
                color    = EduGray
            )
        }
    }
}

// ── Session Card ──────────────────────────────────────────────────────────────
@Composable
private fun StudentSessionCard(
    session:       SharedSession,
    onJoinClick:   () -> Unit,
    onReviewClick: () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(session.avatarColor)
            ) {
                Text(
                    text       = session.studentName.firstOrNull()?.toString() ?: "?",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp
                )
            }
            Spacer(Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = session.studentName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    color      = EduTextDark
                )
                Text(text = session.subject, fontSize = 12.sp, color = EduGray)
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = "${session.date}, ${session.time}",
                    fontSize   = 12.sp,
                    color      = EduBlue,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(session.duration, fontSize = 11.sp, color = EduGray)
                    Text("•", fontSize = 11.sp, color = EduGray)
                    Text(
                        text       = if (session.price > 0) "₹${session.price.toInt()}" else "Free",
                        fontSize   = 11.sp,
                        color      = if (session.price > 0) Color(0xFF22C55E) else EduGray,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Action buttons
            SessionActionButtons(
                status        = session.status,
                onJoinClick   = onJoinClick,
                onReviewClick = onReviewClick
            )
        }
    }
}

// ── Action Buttons — status ke hisaab se ─────────────────────────────────────
@Composable
private fun SessionActionButtons(
    status:        ClassStatus,
    onJoinClick:   () -> Unit,
    onReviewClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (status) {
            ClassStatus.SCHEDULED -> {
                StatusChip(
                    label     = "Upcoming",
                    bgColor   = EduBorder,
                    textColor = EduGray
                )
            }

            ClassStatus.ONGOING -> {
                // Live join button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE53935))
                        .clickable { onJoinClick() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text       = "Join Live",
                            color      = Color.White,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            ClassStatus.COMPLETED -> {
                StatusChip(
                    label     = "Done ✓",
                    bgColor   = Color(0xFF22C55E),
                    textColor = Color.White
                )
                ReviewChip(onClick = onReviewClick)
            }

            ClassStatus.MISSED -> {
                StatusChip(
                    label     = "Missed",
                    bgColor   = Color(0xFFE53935).copy(alpha = 0.15f),
                    textColor = Color(0xFFE53935)
                )
                ReviewChip(onClick = onReviewClick)
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, bgColor: Color, textColor: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text = label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReviewChip(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF3E0))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = Icons.Default.Star,
                contentDescription = "Review",
                tint               = Color(0xFFFFC107),
                modifier           = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text       = "Review",
                color      = Color(0xFFE65100),
                fontSize   = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}