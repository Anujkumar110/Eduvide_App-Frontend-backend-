package com.example.tutorapp

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.ReviewWithStudent
import com.example.tutorapp.network.resolvedStudentDisplayName
import com.example.tutorapp.ui.theme.CardWhite
import com.example.tutorapp.ui.theme.LightBg
import com.example.tutorapp.ui.theme.PrimaryBlue
import com.example.tutorapp.ui.theme.TextGray
import com.example.tutorapp.ui.theme.TextPrimary
import com.example.tutorapp.viewmodel.TeacherListState
import com.example.tutorapp.viewmodel.TeacherViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentorDetailScreen(navController: NavHostController, teacherId: String) {

    val context = LocalContext.current
    val vm: TeacherViewModel = viewModel()
    val uiState by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        if (uiState !is TeacherListState.Success) {
            vm.loadTeachers(context)
        }
    }

    val teacher = (uiState as? TeacherListState.Success)
        ?.teachers?.find { it.id == teacherId }

    var showSheet  by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope      = rememberCoroutineScope()

    // ── Reviews state ─────────────────────────────────────────────────────────
    var reviewsExpanded by remember { mutableStateOf(false) }
    var reviewsList     by remember { mutableStateOf<List<ReviewWithStudent>>(emptyList()) }
    var reviewsLoading  by remember { mutableStateOf(false) }
    var reviewsError    by remember { mutableStateOf("") }

    // ── Review click → dialog ─────────────────────────────────────────────────
    var selectedReview by remember { mutableStateOf<ReviewWithStudent?>(null) }

    LaunchedEffect(teacherId) {
        reviewsExpanded = false
        reviewsList     = emptyList()
        reviewsLoading  = false
        reviewsError    = ""
    }

    // ── Fetch reviews — seedha teacherId use karo, koi guard nahi ────────────
    fun fetchReviews() {
        scope.launch {
            reviewsLoading = true
            reviewsError   = ""
            try {
                val resp = RetrofitClient.api.getTeacherReviews(teacherId = teacherId)
                if (resp.isSuccessful && resp.body()?.success == true) {
                    reviewsList = resp.body()!!.data?.reviews ?: emptyList()
                } else {
                    reviewsError = "Could not load reviews (${resp.code()})"
                }
            } catch (e: Exception) {
                reviewsError = "Network error: ${e.message}"
            } finally {
                reviewsLoading = false
            }
        }
    }

    // ── Review Detail Dialog ──────────────────────────────────────────────────
    selectedReview?.let { rev ->
        val sName    = rev.resolvedStudentDisplayName()
        val photoUrl = RetrofitClient.fullUrl(rev.studentId?.profilePhoto)
        val comment  = rev.comment?.takeIf { it.isNotBlank() } ?: "No comment provided."
        val rating   = rev.rating.coerceIn(0, 5)

        AlertDialog(
            onDismissRequest = { selectedReview = null },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model              = photoUrl,
                            contentDescription = sName,
                            modifier           = Modifier
                                .size(44.dp)
                                .clip(CircleShape),
                            contentScale       = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier         = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text       = sName.first().uppercaseChar().toString(),
                                fontSize   = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color      = PrimaryBlue
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text       = sName,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color      = TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Row {
                            repeat(5) { i ->
                                Text(
                                    text     = if (i < rating) "★" else "☆",
                                    fontSize = 14.sp,
                                    color    = if (i < rating) Color(0xFFFFC107) else Color(0xFFDDDDDD)
                                )
                            }
                        }
                    }
                }
            },
            text = {
                Text(
                    text       = "\"$comment\"",
                    fontSize   = 13.sp,
                    color      = TextGray,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { selectedReview = null }) {
                    Text(
                        text       = "Close",
                        color      = PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }

    if (showSheet && teacher != null) {
        RegisterBottomSheet(
            mentor        = teacher,
            sheetState    = sheetState,
            navController = navController,
            onDismiss     = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { showSheet = false }
            }
        )
    }

    // ── Loading ───────────────────────────────────────────────────────────────
    if (uiState is TeacherListState.Loading) {
        Box(
            modifier         = Modifier.fillMaxSize().background(LightBg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = PrimaryBlue)
        }
        return
    }

    // ── Not found ─────────────────────────────────────────────────────────────
    if (teacher == null) {
        Box(
            modifier         = Modifier.fillMaxSize().background(LightBg),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Teacher not found", color = TextGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { navController.popBackStack() },
                    colors  = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Go Back", color = Color.White)
                }
            }
        }
        return
    }

    val profileImageUrl = RetrofitClient.fullUrl(teacher.profilePhoto)
    val videoUrl        = RetrofitClient.fullUrl(teacher.introVideo)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .verticalScroll(rememberScrollState())
    ) {

        // ── Top Image with back button ─────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model              = profileImageUrl,
                contentDescription = teacher.fullName,
                modifier           = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentScale       = ContentScale.Crop
            )
            Box(
                modifier         = Modifier
                    .padding(12.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint               = TextPrimary,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ── Detail Card ────────────────────────────────────────────────────────
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // ── Name + bio ─────────────────────────────────────────────────
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text       = teacher.fullName,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = TextPrimary
                    )
                    Text(
                        text       = teacher.bio.ifBlank { "No bio available" },
                        fontSize   = 14.sp,
                        color      = PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Stars + Reviews toggle ─────────────────────────────────────
                val rating = teacher.rating.average.toInt().coerceIn(0, 5)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier
                        .clickable {
                            reviewsExpanded = !reviewsExpanded
                            if (reviewsExpanded) fetchReviews()
                        }
                        .padding(vertical = 4.dp)
                ) {
                    repeat(5) { index ->
                        Text(
                            text     = if (index < rating) "★" else "☆",
                            fontSize = 18.sp,
                            color    = if (index < rating) Color(0xFFFFC107) else Color(0xFFDDDDDD)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text       = "(${teacher.rating.count} reviews)",
                        fontSize   = 12.sp,
                        color      = PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector        = if (reviewsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (reviewsExpanded) "Hide reviews" else "Show reviews",
                        tint               = PrimaryBlue,
                        modifier           = Modifier.size(18.dp)
                    )
                }

                // ── Expandable Reviews Section ─────────────────────────────────
                AnimatedVisibility(
                    visible = reviewsExpanded,
                    enter   = expandVertically(),
                    exit    = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Divider(color = Color(0xFFEEEEEE))
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text       = "Student Reviews",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color      = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        when {
                            reviewsLoading -> {
                                Box(
                                    modifier         = Modifier.fillMaxWidth().height(80.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color       = PrimaryBlue,
                                        modifier    = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                }
                            }

                            reviewsError.isNotEmpty() -> {
                                Text(
                                    text     = reviewsError,
                                    fontSize = 12.sp,
                                    color    = Color(0xFFE53935),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            reviewsList.isEmpty() -> {
                                Text(
                                    text     = "No reviews yet. Be the first to review!",
                                    fontSize = 12.sp,
                                    color    = TextGray,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            else -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    reviewsList.forEach { review ->
                                        ReviewCard(
                                            review  = review,
                                            onClick = { selectedReview = review }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = Color(0xFFEEEEEE))
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFFEEEEEE))
                Spacer(modifier = Modifier.height(16.dp))

                // ── Description ────────────────────────────────────────────────
                Text(
                    text = teacher.bio.ifBlank {
                        "An experienced teacher committed to helping students build strong fundamentals."
                    },
                    fontSize   = 13.sp,
                    color      = TextGray,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFFEEEEEE))
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text       = "About ${teacher.fullName}",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint               = Color(0xFF34A853),
                        modifier           = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text       = "Verified",
                        fontSize   = 13.sp,
                        color      = Color(0xFF34A853),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Intro Video ────────────────────────────────────────────────
                if (!videoUrl.isNullOrBlank()) {
                    var isVideoReady  by remember { mutableStateOf(false) }
                    var hasVideoError by remember { mutableStateOf(false) }
                    var isPlaying     by remember { mutableStateOf(false) }
                    var videoViewRef  by remember { mutableStateOf<VideoView?>(null) }

                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A1A2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    setVideoPath(videoUrl)
                                    setOnPreparedListener { mp ->
                                        isVideoReady = true
                                        mp.isLooping  = false
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        hasVideoError = true
                                        true
                                    }
                                    setOnCompletionListener { isPlaying = false }
                                    videoViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update   = { vv -> videoViewRef = vv }
                        )

                        DisposableEffect(Unit) {
                            onDispose { videoViewRef?.stopPlayback() }
                        }

                        if (!isVideoReady && !hasVideoError) {
                            CircularProgressIndicator(
                                color       = Color.White,
                                modifier    = Modifier.size(40.dp),
                                strokeWidth = 3.dp
                            )
                        }

                        if (hasVideoError) {
                            Text(
                                "Video could not be loaded",
                                color    = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }

                        if (isVideoReady && !isPlaying) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier         = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryBlue.copy(alpha = 0.9f))
                                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                        .clickable {
                                            videoViewRef?.start()
                                            isPlaying = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint               = Color.White,
                                        modifier           = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text     = "Tap to Watch Intro Video",
                                    fontSize = 12.sp,
                                    color    = Color.White
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A1A2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier         = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "No video",
                                    tint               = Color.White,
                                    modifier           = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text     = "No Intro Video",
                                fontSize = 12.sp,
                                color    = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Price + Register button ────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        val hourlyRate = teacher.pricing?.hourlyRate
                        Text(
                            text       = if (hourlyRate != null) "₹ $hourlyRate/-hour" else "₹ 299/-hour",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color      = PrimaryBlue
                        )
                        Text("Demo class free", fontSize = 11.sp, color = Color(0xFF34A853))
                    }
                    Button(
                        onClick  = { showSheet = true },
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text(
                            "Register Now",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

// ── Review Card ───────────────────────────────────────────────────────────────

@Composable
private fun ReviewCard(review: ReviewWithStudent, onClick: () -> Unit = {}) {
    val studentName = review.resolvedStudentDisplayName()
    val photoUrl    = RetrofitClient.fullUrl(review.studentId?.profilePhoto)
    val comment     = review.comment?.takeIf { it.isNotBlank() } ?: ""
    val rating      = review.rating.coerceIn(0, 5)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF7F9FF))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model              = photoUrl,
                contentDescription = studentName,
                modifier           = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale       = ContentScale.Crop
            )
        } else {
            Box(
                modifier         = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = studentName.first().uppercaseChar().toString(),
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = PrimaryBlue
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = studentName,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary
                )
                Row {
                    repeat(5) { i ->
                        Text(
                            text     = if (i < rating) "★" else "☆",
                            fontSize = 12.sp,
                            color    = if (i < rating) Color(0xFFFFC107) else Color(0xFFDDDDDD)
                        )
                    }
                }
            }

            if (comment.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text       = comment,
                    fontSize   = 12.sp,
                    color      = TextGray,
                    lineHeight = 18.sp
                )
            }
        }
    }
}