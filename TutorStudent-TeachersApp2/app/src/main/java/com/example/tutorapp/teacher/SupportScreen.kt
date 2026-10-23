package com.example.tutorapp.teacher

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.tutorapp.ui.theme.EduBackground
import com.example.tutorapp.ui.theme.EduBlue
import com.example.tutorapp.ui.theme.EduBlueLight
import com.example.tutorapp.ui.theme.EduBorder
import com.example.tutorapp.ui.theme.EduGray
import com.example.tutorapp.ui.theme.EduTextDark

// ── Models ────────────────────────────────────────────────────────────────────

private data class FaqItem(
    val question: String,
    val answer:   String
)

private data class ContactOption(
    val icon:     ImageVector,
    val label:    String,
    val subtitle: String,
    val color:    Color
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SupportScreenPreview() {
    SupportScreen(navController = rememberNavController())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(navController: NavController) {

    var expandedFaq by remember { mutableStateOf<Int?>(null) }
    var messageText by remember { mutableStateOf("") }
    var messageSent by remember { mutableStateOf(false) }

    val faqs = listOf(
        FaqItem(
            "How do I get more student enquiries?",
            "Complete your profile fully — add a profile photo, intro video, and detailed subject info. Tutors with complete profiles get 3× more enquiries. Also ensure your hourly rate is competitive."
        ),
        FaqItem(
            "How and when do I get paid?",
            "Payments are processed weekly every Friday. Once a session is marked 'Attended' and confirmed by the student, the amount is released to your registered bank account within 3–5 business days."
        ),
        FaqItem(
            "Can I cancel a scheduled session?",
            "Yes. Go to Sessions → Scheduled → tap the session → Cancel. Cancellations made less than 2 hours before the session may affect your reliability rating."
        ),
        FaqItem(
            "How do I add more subjects I teach?",
            "Go to Settings → Personal Information → scroll to the subjects section and select from the available list. Tap '+View more' to see all available subjects."
        ),
        FaqItem(
            "Why is my profile not showing up in search?",
            "Your profile must be at least 80% complete to appear in student searches. Check that you've added: profile photo, teaching mode, hourly rate, subjects, and degree."
        ),
        FaqItem(
            "How do I conduct an online session?",
            "EduVibe integrates with Zoom. After a session is booked, a Zoom link is automatically generated and shared with both you and the student 30 minutes before the scheduled time."
        ),
    )

    val contactOptions = listOf(
        ContactOption(Icons.Filled.Chat,       "Live Chat",    "Typically replies in 5 min",   Color(0xFF1E88E5)),
        ContactOption(Icons.Filled.Email,      "Email Us",     "support@eduvibe.com",           Color(0xFF43A047)),
        ContactOption(Icons.Filled.Phone,      "Call Support", "+91 1800-EDU-VIBE",             Color(0xFFF57C00)),
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                title = {
                    Text("Help & Support", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = EduBlue)
            )
        },
        containerColor = EduBackground
    ) { innerPadding ->

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding      = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── Hero banner ───────────────────────────────────────────────────
            item {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .background(EduBlue)
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("👋", fontSize = 32.sp)
                        Text(
                            text       = "How can we help you?",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                        Text(
                            text  = "Find answers or contact our support team",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Contact options ───────────────────────────────────────────────
            item {
                Text(
                    text       = "CONTACT US",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = EduGray,
                    letterSpacing = 1.sp,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    contactOptions.forEach { option ->
                        ContactCard(option = option, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── FAQ header ────────────────────────────────────────────────────
            item {
                Text(
                    text       = "FREQUENTLY ASKED QUESTIONS",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = EduGray,
                    letterSpacing = 1.sp,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // ── FAQ accordion ─────────────────────────────────────────────────
            items(faqs.indices.toList()) { index ->
                val faq      = faqs[index]
                val isOpen   = expandedFaq == index

                Card(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape     = RoundedCornerShape(12.dp),
                    colors    = CardDefaults.cardColors(
                        containerColor = if (isOpen) EduBlueLight else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(if (isOpen) 0.dp else 1.dp)
                ) {
                    Column {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedFaq = if (isOpen) null else index
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier         = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isOpen) EduBlue else Color(0xFFF0F4F8))
                            ) {
                                Text(
                                    text       = "Q",
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = if (isOpen) Color.White else EduGray
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text       = faq.question,
                                fontSize   = 14.sp,
                                fontWeight = if (isOpen) FontWeight.SemiBold else FontWeight.Medium,
                                color      = if (isOpen) EduBlue else EduTextDark,
                                modifier   = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector        = if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint     = if (isOpen) EduBlue else EduGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(visible = isOpen) {
                            Column {
                                HorizontalDivider(color = EduBlue.copy(alpha = 0.15f), modifier = Modifier.padding(horizontal = 16.dp))
                                Text(
                                    text     = faq.answer,
                                    fontSize = 13.sp,
                                    color    = EduTextDark,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Send message ──────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text       = "SEND A MESSAGE",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = EduGray,
                    letterSpacing = 1.sp,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                Card(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape     = RoundedCornerShape(14.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (messageSent) {
                            Row(
                                modifier              = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE8F5E9))
                                    .padding(12.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF43A047), modifier = Modifier.size(20.dp))
                                Text("Message sent! We'll get back to you within 24 hours.", fontSize = 13.sp, color = Color(0xFF2E7D32))
                            }
                        } else {
                            OutlinedTextField(
                                value         = messageText,
                                onValueChange = { messageText = it },
                                placeholder   = { Text("Describe your issue or question…", fontSize = 13.sp, color = EduGray) },
                                modifier      = Modifier.fillMaxWidth().height(110.dp),
                                shape         = RoundedCornerShape(10.dp),
                                maxLines      = 5,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = EduBlue,
                                    unfocusedBorderColor = EduBorder,
                                    focusedTextColor     = EduTextDark,
                                    unfocusedTextColor   = EduTextDark
                                )
                            )
                            Button(
                                onClick  = { if (messageText.isNotBlank()) messageSent = true },
                                enabled  = messageText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape    = RoundedCornerShape(10.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = EduBlue)
                            ) {
                                Icon(Icons.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Send Message", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Contact card ──────────────────────────────────────────────────────────────

@Composable
private fun ContactCard(option: ContactOption, modifier: Modifier = Modifier) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(option.color.copy(alpha = 0.12f))
            ) {
                Icon(option.icon, contentDescription = null, tint = option.color, modifier = Modifier.size(18.dp))
            }
            Text(option.label,    fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = EduTextDark)
            Text(option.subtitle, fontSize = 9.sp, color = EduGray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}