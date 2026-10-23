package com.example.tutorapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.navigation.NavHostController
import com.example.tutorapp.network.NotificationItem
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private fun formatNotifTime(createdAt: String?): String {
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
fun NotificationScreen(navController: NavHostController) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedNotif by remember { mutableStateOf<NotificationItem?>(null) }

    fun getToken(): String? {
        val t = TokenManager.getToken(context)
        return if (t.isNullOrBlank()) null else "Bearer $t"
    }

    LaunchedEffect(Unit) {
        try {
            val token = getToken()
            if (token == null) {
                errorMsg = "Token missing — login again"
                isLoading = false
                return@LaunchedEffect
            }

            val resp = RetrofitClient.api.getNotifications(token)

            if (resp.isSuccessful) {
                notifications = resp.body()?.data ?: emptyList()
            } else {
                errorMsg = "Error ${resp.code()}"
            }

        } catch (e: Exception) {
            errorMsg = "Exception: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    selectedNotif?.let { notif ->
        AlertDialog(
            onDismissRequest = { selectedNotif = null },
            title = {
                Text(notif.title ?: "", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(notif.body ?: "")
                    Spacer(Modifier.height(8.dp))
                    Text(formatNotifTime(notif.createdAt), fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedNotif = null }) {
                    Text("Close")
                }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {

        // Top Bar
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(10.dp)
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, null)
            }

            Text(
                "Notifications",
                modifier = Modifier.align(Alignment.Center),
                fontWeight = FontWeight.Bold
            )

            TextButton(
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = {
                    scope.launch {
                        val token = getToken() ?: return@launch
                        val resp = RetrofitClient.api.markAllAsRead(token)
                        if (resp.isSuccessful) {
                            notifications = notifications.map { it.copy(isRead = true) }
                        }
                    }
                }
            ) {
                Text("Mark all read")
            }
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            errorMsg != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMsg ?: "", color = Color.Red)
                }
            }

            notifications.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notifications yet")
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp)
                ) {
                    itemsIndexed(notifications) { _, notif ->

                        val unread = !notif.isRead

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {

                                    selectedNotif = notif

                                    if (unread && notif.id != null) {
                                        scope.launch {
                                            val token = getToken() ?: return@launch
                                            val resp = RetrofitClient.api.markAsRead(token, notif.id)

                                            if (resp.isSuccessful) {
                                                notifications = notifications.map {
                                                    if (it.id == notif.id) it.copy(isRead = true) else it
                                                }
                                            }
                                        }
                                    }
                                }
                        ) {
                            Row(Modifier.padding(12.dp)) {

                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (unread) Color.Blue else Color.Gray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Notifications, null, tint = Color.White)
                                }

                                Spacer(Modifier.width(10.dp))

                                Column(Modifier.weight(1f)) {

                                    Text(
                                        notif.title ?: "",
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        notif.body ?: "",
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}