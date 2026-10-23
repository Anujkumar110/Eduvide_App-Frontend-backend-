package com.example.tutorapp.student

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tutorapp.SessionViewModel
import com.example.tutorapp.SharedSession
import com.example.tutorapp.ui.theme.*

@Composable
fun ReviewDialog(
    session:   SharedSession,
    viewModel: SessionViewModel,
    onDismiss: () -> Unit
) {
    var selectedRating by remember { mutableStateOf(0) }
    var comment        by remember { mutableStateOf("") }
    var isSubmitting   by remember { mutableStateOf(false) }
    var errorMsg       by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        containerColor = Color.White,
        shape          = RoundedCornerShape(20.dp),
        title = {
            Box(modifier = Modifier.fillMaxWidth()) {
                // ✅ X close button — top right
                IconButton(
                    onClick  = { if (!isSubmitting) onDismiss() },
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Close,
                        contentDescription = "Close",
                        tint               = EduGray,
                        modifier           = Modifier.size(20.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.fillMaxWidth().padding(end = 28.dp)
                ) {
                    Text(
                        text       = "Rate Your Session",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = EduTextDark
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = session.studentName,
                        fontSize = 13.sp,
                        color    = EduGray
                    )
                }
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(color = EduBorder)

                Text(
                    text       = "How was your experience?",
                    fontSize   = 14.sp,
                    color      = EduTextDark,
                    fontWeight = FontWeight.Medium
                )

                // ── Star Rating ──────────────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    (1..5).forEach { star ->
                        Icon(
                            imageVector        = if (star <= selectedRating) Icons.Filled.Star
                            else Icons.Outlined.StarOutline,
                            contentDescription = "Star $star",
                            tint               = if (star <= selectedRating) Color(0xFFFFC107)
                            else Color(0xFFDDDDDD),
                            modifier           = Modifier
                                .size(42.dp)
                                .clickable(enabled = !isSubmitting) { selectedRating = star }
                                .padding(4.dp)
                        )
                    }
                }

                if (selectedRating > 0) {
                    Text(
                        text = when (selectedRating) {
                            1    -> "😞 Poor"
                            2    -> "😐 Fair"
                            3    -> "🙂 Good"
                            4    -> "😊 Very Good"
                            else -> "🤩 Excellent!"
                        },
                        fontSize   = 14.sp,
                        color      = EduBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // ── Comment Box ──────────────────────────────────────────────
                OutlinedTextField(
                    value         = comment,
                    onValueChange = { if (!isSubmitting) comment = it },
                    label         = { Text("Write a comment (optional)", fontSize = 12.sp) },
                    placeholder   = {
                        Text(
                            "Share your experience...",
                            fontSize = 12.sp,
                            color    = Color(0xFFBBBBBB)
                        )
                    },
                    modifier  = Modifier.fillMaxWidth(),
                    minLines  = 3,
                    maxLines  = 5,
                    enabled   = !isSubmitting,
                    shape     = RoundedCornerShape(10.dp),
                    colors    = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = EduBlue,
                        unfocusedBorderColor = EduBorder,
                        focusedLabelColor    = EduBlue
                    )
                )

                errorMsg?.let {
                    Text(it, color = Color(0xFFE53935), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (selectedRating == 0) {
                            errorMsg = "Please select a rating"
                            return@Button
                        }
                        isSubmitting = true
                        errorMsg     = null

                        viewModel.submitReview(
                            classId   = session.roomId,
                            rating    = selectedRating,
                            comment   = comment,
                            onSuccess = {
                                isSubmitting = false
                                onDismiss()
                            },
                            onError   = { msg ->
                                isSubmitting = false
                                errorMsg     = msg
                            }
                        )
                    },
                    enabled  = !isSubmitting,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = EduBlue,
                        disabledContainerColor = Color(0xFFCCCCCC)
                    )
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            color       = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text       = "Submit Review",
                            color      = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick  = { if (!isSubmitting) onDismiss() },
                    enabled  = !isSubmitting,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Text("Skip for now", color = EduGray)
                }
            }
        }
    )
}