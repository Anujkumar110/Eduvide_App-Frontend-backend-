package com.example.tutorapp.teacher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.tutorapp.ui.theme.EduBlue
import com.example.tutorapp.ui.theme.EduBlueLight
import com.example.tutorapp.ui.theme.EduBorder
import com.example.tutorapp.ui.theme.EduChipBg
import com.example.tutorapp.ui.theme.EduGray
import com.example.tutorapp.ui.theme.EduTextDark
import com.example.tutorapp.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

// ── Model ─────────────────────────────────────────────────────────────────────

// ChatMessage moved to ChatViewModel

// ── Screen ────────────────────────────────────────────────────────────────────


@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ChatScreenPreview() {
    // 1. Create a "fake" NavController for the preview window
    val navController = rememberNavController()

    // 2. Pass an actual name (text in quotes) for the contactName
    ChatScreen(
        navController = navController,
        contactName = "John Doe", // This is the sample data that will show up
        contactUserId = "student123",
        roomId = "class456",
        onVoiceCall = { },
        onVideoCall = { }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    contactName: String,
    contactUserId: String,
    roomId: String,
    onVoiceCall: () -> Unit = {},
    onVideoCall: () -> Unit = {}
) {
    val chatViewModel: ChatViewModel = viewModel()
    val messages by chatViewModel.messages.collectAsState()
    val isTyping by chatViewModel.isTyping.collectAsState()

    var inputText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    // Initialize chat on first composition
    LaunchedEffect(roomId, contactUserId, contactName) {
        chatViewModel.initChat(roomId, contactUserId, contactName)
    }

    // Format timestamp to readable time
    fun formatTime(timestamp: String?): String {
        if (timestamp == null) return "Now"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date)
        } catch (e: Exception) {
            "Now"
        }
    }

    BackHandler {
        chatViewModel.disconnect()
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { 
                        chatViewModel.disconnect()
                        navController.popBackStack() 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Mini avatar
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text       = contactName.first().uppercaseChar().toString(),
                                color      = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 15.sp
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(contactName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            Text(
                                text = if (isTyping) "typing..." else "Online",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onVoiceCall) {
                        Icon(Icons.Filled.Phone, contentDescription = "Voice Call", tint = Color.White)
                    }
                    IconButton(onClick = onVideoCall) {
                        Icon(Icons.Filled.Videocam, contentDescription = "Video Call", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EduBlue)
            )
        },
        containerColor = Color(0xFFECF0F4)
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // ── Chat messages ─────────────────────────────────────────────────
            LazyColumn(
                state               = listState,
                modifier            = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding      = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(msg.text, msg.isSent, formatTime(msg.timestamp))
                }
            }

            // ── Input row ─────────────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Add, contentDescription = "Attach", tint = EduGray)
                }

                OutlinedTextField(
                    value         = inputText,
                    onValueChange = { 
                        inputText = it
                        chatViewModel.sendTyping(it.isNotEmpty())
                    },
                    placeholder   = { Text("Type a message…", fontSize = 14.sp) },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(24.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = EduBlue,
                        unfocusedBorderColor    = EduBorder,
                        focusedTextColor        = EduTextDark,
                        unfocusedTextColor      = EduTextDark,
                        focusedContainerColor   = Color(0xFFF5F7FA),
                        unfocusedContainerColor = Color(0xFFF5F7FA)
                    )
                )

                // Send button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isBlank()) EduBorder else EduBlue)
                        .then(
                            if (inputText.isNotBlank()) Modifier.clickable {
                                chatViewModel.sendMessage(inputText.trim())
                                inputText = ""
                            } else Modifier
                        )
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint     = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Bubble ────────────────────────────────────────────────────────────────────

@Composable
fun ChatBubble(message: String, isSent: Boolean, time: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier            = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart    = if (isSent) 16.dp else 4.dp,
                            topEnd      = if (isSent) 4.dp  else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd   = 16.dp
                        )
                    )
                    .background(if (isSent) EduBlue else Color(0xFFE8F0FE))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    text     = message,
                    color    = if (isSent) Color.White else Color(0xFF1A73E8),
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(time, fontSize = 10.sp, color = EduGray)
        }
    }
}