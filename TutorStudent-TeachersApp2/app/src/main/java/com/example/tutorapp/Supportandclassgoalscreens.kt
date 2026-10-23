package com.example.tutorapp

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.tutorapp.ui.theme.CardWhite
import com.example.tutorapp.ui.theme.LightBg
import com.example.tutorapp.ui.theme.PrimaryBlue
import com.example.tutorapp.ui.theme.TextGray
import com.example.tutorapp.ui.theme.TextPrimary

// ══════════════════════════════════════════════════════════════════════════════
// SUPPORT SCREEN
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(navController: NavHostController) {

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                text       = "Support",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                modifier   = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {

            Text(text = "How can we help you?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Reach out to us through any of the options below.", fontSize = 13.sp, color = TextGray)

            Spacer(modifier = Modifier.height(20.dp))

            // ── Call Us — dialer open ──────────────────────────────────────────
            SupportOption(
                icon     = Icons.Default.Phone,
                title    = "Call Us",
                subtitle = "+91 98765 00000",
                color    = Color(0xFF1A73E8),
                onClick  = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+919876500000"))
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── Email Us — email app open ──────────────────────────────────────
            SupportOption(
                icon     = Icons.Default.Email,
                title    = "Email Us",
                subtitle = "support@eduvibe.com",
                color    = Color(0xFFE8572A),
                onClick  = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data    = Uri.parse("mailto:support@eduvibe.com")
                        putExtra(Intent.EXTRA_SUBJECT, "EduVibe Support Request")
                    }
                    context.startActivity(Intent.createChooser(intent, "Send Email"))
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── Live Chat — nayi screen ────────────────────────────────────────
            SupportOption(
                icon     = Icons.Outlined.Chat,
                title    = "Live Chat",
                subtitle = "Chat with our support team",
                color    = Color(0xFF22C55E),
                onClick  = { navController.navigate("LiveChatScreen") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "FAQs", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))

            listOf(
                "How do I book a demo class?" to "Go to Mentor screen, tap Register Now and fill the form.",
                "How do I change my class or goal?" to "Go to Settings → Class & Goal to update your preferences.",
                "Is the demo class free?" to "Yes! Every mentor offers one free demo class."
            ).forEach { (q, a) ->
                FaqItem(question = q, answer = a)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SupportOption(icon: ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(text = subtitle, fontSize = 12.sp, color = TextGray)
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier  = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape     = RoundedCornerShape(10.dp),
        colors    = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = question, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = answer, fontSize = 12.sp, color = TextGray, lineHeight = 17.sp)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// LIVE CHAT SCREEN
// ══════════════════════════════════════════════════════════════════════════════
data class ChatMsg(val text: String, val isUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChatScreen(navController: NavHostController) {

    var inputText  by remember { mutableStateOf("") }
    val messages   = remember { mutableStateListOf(
        ChatMsg("Hi! Welcome to EduVibe Support 👋 How can I help you today?", false)
    )}
    val listState  = rememberLazyListState()

    // Auto scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .imePadding()
    ) {
        // ── Top bar ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "EduVibe Support", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Online", fontSize = 11.sp, color = Color(0xFF22C55E))
                }
            }
        }

        // ── Messages list ──────────────────────────────────────────────────────
        LazyColumn(
            state           = listState,
            modifier        = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            contentPadding  = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                ChatBubble(msg = msg)
            }
        }

        // ── Input bar ──────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = inputText,
                onValueChange = { inputText = it },
                placeholder   = { Text("Type a message...", color = TextGray, fontSize = 13.sp) },
                modifier      = Modifier.weight(1f),
                shape         = RoundedCornerShape(24.dp),
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = PrimaryBlue,
                    unfocusedBorderColor = Color(0xFFDDDDDD)
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier         = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (inputText.isNotBlank()) PrimaryBlue else Color(0xFFDDDDDD))
                    .clickable {
                        if (inputText.isNotBlank()) {
                            messages.add(ChatMsg(inputText.trim(), true))
                            inputText = ""
                            // Auto reply
                            messages.add(ChatMsg("Thanks for reaching out! Our team will get back to you shortly. 🙏", false))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── Chat bubble ────────────────────────────────────────────────────────────────
@Composable
private fun ChatBubble(msg: ChatMsg) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) androidx.compose.foundation.layout.Arrangement.End
        else             androidx.compose.foundation.layout.Arrangement.Start
    ) {
        if (!msg.isUser) {
            // Support avatar
            Box(
                modifier         = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue),
                contentAlignment = Alignment.Center
            ) {
                Text("E", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(
                    RoundedCornerShape(
                        topStart    = if (msg.isUser) 16.dp else 4.dp,
                        topEnd      = if (msg.isUser) 4.dp  else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd   = 16.dp
                    )
                )
                .background(if (msg.isUser) PrimaryBlue else CardWhite)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text      = msg.text,
                fontSize  = 13.sp,
                color     = if (msg.isUser) Color.White else TextPrimary,
                lineHeight = 18.sp
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// CLASS & GOAL SCREEN
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassGoalScreen(navController: NavHostController) {

    var selectedClass by remember { mutableIntStateOf(-1) }
    var selectedGoal  by remember { mutableIntStateOf(-1) }

    val classes = listOf("Class 6th", "Class 7th", "Class 8th", "Class 9th", "Class 10th", "Class 11th", "Class 12th")
    val goals   = listOf("JEE Mains", "NEET", "CBSE", "ICSC", "State Board Exams")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                text       = "Class & Goal",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                modifier   = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {

            Text(text = "Select Your Class", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))

            classes.chunked(2).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEachIndexed { i, cls ->
                        val idx        = classes.indexOf(cls)
                        val isSelected = selectedClass == idx
                        Box(
                            modifier         = Modifier
                                .weight(1f)
                                .padding(end = if (i == 0) 6.dp else 0.dp, bottom = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) PrimaryBlue else CardWhite)
                                .border(1.5.dp, if (isSelected) PrimaryBlue else Color(0xFFDDDDDD), RoundedCornerShape(10.dp))
                                .clickable { selectedClass = idx }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = cls, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (isSelected) Color.White else TextPrimary)
                        }
                    }
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(text = "What's your Goal?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))

            goals.chunked(2).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEachIndexed { i, goal ->
                        val idx        = goals.indexOf(goal)
                        val isSelected = selectedGoal == idx
                        val isFullWidth = rowItems.size == 1
                        Box(
                            modifier         = Modifier
                                .then(if (isFullWidth) Modifier.fillMaxWidth() else Modifier.weight(1f))
                                .padding(end = if (i == 0 && !isFullWidth) 6.dp else 0.dp, bottom = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) PrimaryBlue else CardWhite)
                                .border(1.5.dp, if (isSelected) PrimaryBlue else Color(0xFFDDDDDD), RoundedCornerShape(10.dp))
                                .clickable { selectedGoal = idx }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = goal, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (isSelected) Color.White else TextPrimary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick  = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Save Changes", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}