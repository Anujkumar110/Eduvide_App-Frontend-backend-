package com.example.tutorapp.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.tutorapp.network.NotificationItem
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TokenManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private fun formatTeacherTime(createdAt: String?): String {
    if (createdAt.isNullOrBlank()) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")

        val date = sdf.parse(createdAt) ?: return ""
        val diff = Date().time - date.time

        val mins = diff / 60000
        val hrs = mins / 60
        val days = hrs / 24

        when {
            mins < 60 -> "$mins min ago"
            hrs < 24 -> "$hrs hr ago"
            days == 1L -> "Yesterday"
            else -> "$days days ago"
        }
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun NotificationsScreen(navController: NavController) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<NotificationItem?>(null) }

    LaunchedEffect(Unit) {
        try {
            val token = TokenManager.getToken(context) ?: ""
            val resp = RetrofitClient.api.getNotifications("Bearer $token")

            if (resp.isSuccessful) {
                notifications = resp.body()?.data ?: emptyList()
            } else {
                errorMsg = "Error ${resp.code()}"
            }
        } catch (e: Exception) {
            errorMsg = e.message
        } finally {
            isLoading = false
        }
    }

    selected?.let { notif ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(notif.title ?: "") },
            text = { Text(notif.body ?: notif.message ?: "") },
            confirmButton = {
                TextButton(onClick = { selected = null }) {
                    Text("OK")
                }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {

        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }

            Text("Notifications", fontWeight = FontWeight.Bold)
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            notifications.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No notifications")
            }

            else -> LazyColumn {
                itemsIndexed(notifications) { _, notif ->

                    val unread = !notif.isRead

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clickable {
                                selected = notif

                                if (unread && notif.id != null) {
                                    scope.launch {
                                        val token = TokenManager.getToken(context) ?: return@launch
                                        RetrofitClient.api.markAsRead("Bearer $token", notif.id)
                                    }
                                }
                            }
                    ) {
                        Row(Modifier.padding(12.dp)) {

                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(if (unread) Color.Blue else Color.Gray),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Notifications, null, tint = Color.White)
                            }

                            Spacer(Modifier.width(10.dp))

                            Column(Modifier.weight(1f)) {
                                Text(notif.title ?: "", fontWeight = FontWeight.Bold)
                                Text(
                                    notif.body ?: notif.message ?: "",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}