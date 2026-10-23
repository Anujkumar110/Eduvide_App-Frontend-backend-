package com.example.tutorapp.student

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tutorapp.network.ApiService
import com.example.tutorapp.network.ReviewWithStudent
import com.example.tutorapp.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TeacherReviewsSection(
    teacherId:  String,
    apiService: ApiService
) {
    val coroutineScope = rememberCoroutineScope()
    var reviews        by remember { mutableStateOf<List<ReviewWithStudent>>(emptyList()) }
    var total          by remember { mutableStateOf(0) }
    var isLoading      by remember { mutableStateOf(true) }

    LaunchedEffect(teacherId) {
        coroutineScope.launch {
            try {
                val resp = apiService.getTeacherReviews(teacherId)
                if (resp.isSuccessful && resp.body()?.success == true) {
                    reviews = resp.body()!!.data?.reviews ?: emptyList()
                    total   = resp.body()!!.data?.total   ?: 0
                }
            } catch (_: Exception) { }
            finally { isLoading = false }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {

        Row(
            modifier              = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text       = "Student Reviews",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = EduTextDark
            )
            if (total > 0) {
                Text("$total reviews", fontSize = 13.sp, color = EduGray)
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(24.dp),
                        color       = EduBlue,
                        strokeWidth = 2.dp
                    )
                }
            }
            reviews.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text     = "No reviews yet. Be the first to review!",
                        fontSize = 13.sp,
                        color    = EduGray,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            else -> {
                reviews.forEach { review ->
                    ReviewCard(review = review)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(review: ReviewWithStudent) {
    val studentName = review.studentId?.fullName ?: "Student"
    val initial     = studentName.firstOrNull()?.toString() ?: "S"

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EduBlue)
                    ) {
                        Text(
                            text       = initial,
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text       = studentName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 13.sp,
                            color      = EduTextDark
                        )
                        Text(
                            text     = review.createdAt?.take(10) ?: "",
                            fontSize = 11.sp,
                            color    = EduGray
                        )
                    }
                }
                // Star rating badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { i ->
                        Icon(
                            imageVector        = Icons.Filled.Star,
                            contentDescription = null,
                            tint               = if (i < review.rating) Color(0xFFFFC107) else Color(0xFFE0E0E0),
                            modifier           = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text       = "${review.rating}/5",
                        fontSize   = 11.sp,
                        color      = EduGray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (!review.comment.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = review.comment,
                    fontSize   = 13.sp,
                    color      = Color(0xFF555555),
                    lineHeight = 18.sp
                )
            }
        }
    }
}