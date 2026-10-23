package com.example.tutorapp.teacher

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.tutorapp.SessionViewModel
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.resolvedStudentDisplayName
import com.example.tutorapp.ui.theme.EduBackground
import com.example.tutorapp.ui.theme.EduBlue
import com.example.tutorapp.ui.theme.EduBorder
import com.example.tutorapp.ui.theme.EduGray
import com.example.tutorapp.ui.theme.EduTextDark
import java.time.Instant
import java.time.temporal.ChronoUnit

// ── Model ─────────────────────────────────────────────────────────────────────

private data class Review(
    val studentName: String,
    val rating:      Float,
    val comment:     String,
    val timeAgo:     String,
    val avatarColor: Color
)

// ── Helpers ───────────────────────────────────────────────────────────────────

private val avatarColors = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFF57C00),
    Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFE53935),
)

private fun nameToColor(name: String): Color =
    avatarColors[Math.abs(name.hashCode()) % avatarColors.size]

@RequiresApi(Build.VERSION_CODES.O)
private fun timeAgo(createdAt: String?): String {
    if (createdAt == null) return ""
    return try {
        val instant = Instant.parse(createdAt)
        val days    = ChronoUnit.DAYS.between(instant, Instant.now())
        when {
            days == 0L  -> "Today"
            days == 1L  -> "Yesterday"
            days < 7L   -> "$days days ago"
            days < 14L  -> "1 week ago"
            days < 30L  -> "${days / 7} weeks ago"
            days < 60L  -> "1 month ago"
            else        -> "${days / 30} months ago"
        }
    } catch (e: Exception) { "" }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(
    navController: NavController,
    viewModel:     SessionViewModel,
    context:       Context
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var allReviews     by remember { mutableStateOf<List<Review>>(emptyList()) }
    var isLoading      by remember { mutableStateOf(true) }
    var errorMsg       by remember { mutableStateOf<String?>(null) }

    val teacherProfile by viewModel.teacherProfile.collectAsState()

    // Step 1: profile load karo agar nahi hai
    LaunchedEffect(Unit) {
        if (teacherProfile == null) {
            viewModel.loadTeacherProfile(
                onSuccess = {},
                onError   = { errorMsg = it; isLoading = false }
            )
        }
    }

    // Step 2: teacherId decide karo — backend `_id` field = TeacherProfileData.id
    // Kuch backends `teacherId` = profile._id use karte hain, kuch userId
    // Dono try karo — pehle id, agar khaali ho toh userId
    LaunchedEffect(teacherProfile) {
        val profile = teacherProfile ?: return@LaunchedEffect

        // ✅ FIX: `id` (_id field) primary hai; fallback `userId` pe karo
        val teacherId = profile.id?.takeIf { it.isNotBlank() }
            ?: profile.userId?.takeIf { it.isNotBlank() }

        if (teacherId == null) {
            errorMsg  = "Teacher ID nahi mila — profile dobara load karo"
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        errorMsg  = null
        try {
            val response = RetrofitClient.api.getTeacherReviews(teacherId)
            if (response.isSuccessful && response.body()?.success == true) {
                val raw = response.body()!!.data?.reviews ?: emptyList()
                allReviews = raw.map { r ->
                    val name = r.resolvedStudentDisplayName()
                    Review(
                        studentName = name,
                        rating      = r.rating.toFloat(),
                        comment     = r.comment?.takeIf { it.isNotBlank() } ?: "",
                        timeAgo     = timeAgo(r.createdAt),
                        avatarColor = nameToColor(name)
                    )
                }
            } else {
                errorMsg = "Reviews load nahi ho sake (${response.code()})"
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "Network error"
        } finally {
            isLoading = false
        }
    }

    val filters   = listOf("All", "5★", "4★", "3★", "1-2★")
    val displayed = when (selectedFilter) {
        "5★"  -> allReviews.filter { it.rating >= 5.0f }
        "4★"  -> allReviews.filter { it.rating >= 4.0f && it.rating < 5.0f }
        "3★"  -> allReviews.filter { it.rating >= 3.0f && it.rating < 4.0f }
        "1-2★" -> allReviews.filter { it.rating < 3.0f }
        else  -> allReviews
    }

    val avgRating    = if (allReviews.isEmpty()) 0f else allReviews.map { it.rating }.average().toFloat()
    val totalReviews = allReviews.size

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                title  = { Text("My Reviews", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = EduBlue)
            )
        },
        containerColor = EduBackground
    ) { innerPadding ->

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding      = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── Loading ───────────────────────────────────────────────────────
            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = EduBlue)
                            Text("Reviews load ho rahi hain...", fontSize = 13.sp, color = EduGray)
                        }
                    }
                }
                return@LazyColumn
            }

            // ── Error ─────────────────────────────────────────────────────────
            if (errorMsg != null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(errorMsg ?: "Error", color = EduGray, fontSize = 14.sp)
                    }
                }
                return@LazyColumn
            }

            // ── Rating Summary Card ───────────────────────────────────────────
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(16.dp),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = EduBlue),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            // ── Left: Big average number ──────────────────────
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text       = if (totalReviews == 0) "0.0" else String.format("%.1f", avgRating),
                                    fontSize   = 48.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = Color.White
                                )
                                StarRow(rating = avgRating, starSize = 18.dp, activeColor = Color.White)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "$totalReviews ${if (totalReviews == 1) "review" else "reviews"}",
                                    fontSize = 12.sp,
                                    color    = Color.White.copy(alpha = 0.8f)
                                )
                            }

                            Box(modifier = Modifier.width(1.dp).height(80.dp).background(Color.White.copy(alpha = 0.3f)))

                            // ── Right: Star breakdown bars ────────────────────
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf(5, 4, 3, 2, 1).forEach { star ->
                                    val count = when (star) {
                                        5    -> allReviews.count { it.rating >= 5f }
                                        1    -> allReviews.count { it.rating < 2f }
                                        else -> allReviews.count { it.rating >= star && it.rating < star + 1 }
                                    }
                                    val fraction = if (totalReviews > 0) count.toFloat() / totalReviews else 0f
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "$star",
                                            fontSize = 11.sp,
                                            color    = Color.White,
                                            modifier = Modifier.width(10.dp)
                                        )
                                        Icon(
                                            Icons.Filled.Star,
                                            contentDescription = null,
                                            tint     = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(80.dp).height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color.White.copy(alpha = 0.25f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(fraction)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(Color.White)
                                            )
                                        }
                                        Text(
                                            "$count",
                                            fontSize = 11.sp,
                                            color    = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.width(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Filter Chips ──────────────────────────────────────────────────
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filters.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick  = { selectedFilter = filter },
                            label    = { Text(filter, fontSize = 12.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EduBlue,
                                selectedLabelColor     = Color.White,
                                containerColor         = Color.White,
                                labelColor             = EduGray
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled             = true,
                                selected            = selectedFilter == filter,
                                selectedBorderColor = EduBlue,
                                borderColor         = EduBorder
                            )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Review Cards ──────────────────────────────────────────────────
            if (displayed.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier.fillMaxWidth().padding(48.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⭐", fontSize = 48.sp)
                            Text(
                                if (totalReviews == 0) "Abhi tak koi review nahi mila"
                                else "Is category mein koi review nahi",
                                fontSize = 14.sp,
                                color    = EduGray
                            )
                        }
                    }
                }
            } else {
                items(displayed) { review -> ReviewCard(review) }
            }
        }
    }
}

// ── Review Card ───────────────────────────────────────────────────────────────

@Composable
private fun ReviewCard(review: Review) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Top row: avatar + name + stars + time ─────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar circle with initials
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(review.avatarColor)
                ) {
                    Text(
                        text       = review.studentName.first().toString().uppercase(),
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Student name
                    Text(
                        review.studentName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 14.sp,
                        color      = EduTextDark
                    )
                    // Time ago
                    if (review.timeAgo.isNotBlank()) {
                        Text(review.timeAgo, fontSize = 11.sp, color = EduGray)
                    }
                }

                // Stars — right side
                Column(horizontalAlignment = Alignment.End) {
                    StarRow(rating = review.rating, starSize = 15.dp)
                    Spacer(Modifier.height(2.dp))
                    // Numeric rating badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    review.rating >= 4 -> Color(0xFFE8F5E9)
                                    review.rating >= 3 -> Color(0xFFFFF3E0)
                                    else               -> Color(0xFFFFEBEE)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "${review.rating.toInt()}/5",
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color      = when {
                                review.rating >= 4 -> Color(0xFF2E7D32)
                                review.rating >= 3 -> Color(0xFFE65100)
                                else               -> Color(0xFFC62828)
                            }
                        )
                    }
                }
            }

            // ── Comment ───────────────────────────────────────────────────────
            if (review.comment.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = EduBorder)
                Spacer(Modifier.height(10.dp))
                Text(
                    text       = "\"${review.comment}\"",
                    fontSize   = 13.sp,
                    color      = EduTextDark,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

// ── Star Row ──────────────────────────────────────────────────────────────────

@Composable
fun StarRow(
    rating:      Float,
    starSize:    androidx.compose.ui.unit.Dp = 16.dp,
    activeColor: Color = Color(0xFFFFC107)
) {
    Row {
        for (i in 1..5) {
            val icon = when {
                rating >= i       -> Icons.Filled.Star
                rating >= i - 0.5 -> Icons.Filled.StarHalf
                else              -> Icons.Filled.StarBorder
            }
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = if (rating >= i - 0.5) activeColor else EduGray.copy(alpha = 0.4f),
                modifier           = Modifier.size(starSize)
            )
        }
    }
}