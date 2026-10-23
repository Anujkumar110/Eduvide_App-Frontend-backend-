package com.example.tutorapp.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.tutorapp.ui.theme.EduBackground
import com.example.tutorapp.ui.theme.EduBlue
import com.example.tutorapp.ui.theme.EduBorder
import com.example.tutorapp.ui.theme.EduGray
import com.example.tutorapp.ui.theme.EduTextDark

// ── Models ────────────────────────────────────────────────────────────────────

data class ContactItem(
    val id:          String,
    val name:        String,
    val lastMessage: String,
    val time:        String,
    val avatarColor: Color,
    val isOnline:    Boolean = false,
    val roomId:      String = ""
)

// ── Screen ────────────────────────────────────────────────────────────────────
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun StudentsTabPreview() {
    val navController = rememberNavController()
    StudentsTab(
        navController = navController,
    )
}

@Composable
fun StudentsTab(navController: NavController) {

    var searchQuery by remember { mutableStateOf("") }

    // ✅ Individual students only (Group feature removed)
    val individuals = listOf(
        ContactItem(id = "student1", name = "Rahul Sharma", lastMessage = "you deleted this message", time = "Yesterday", avatarColor = Color(0xFF1E88E5), roomId = "class_rahu"),
        ContactItem(id = "student2", name = "Priya Patel", lastMessage = "Good morning sir", time = "10:30 AM", avatarColor = Color(0xFF43A047), roomId = "class_priya"),
        ContactItem(id = "student3", name = "Amit Kumar", lastMessage = "Ok I'll check", time = "Yesterday", avatarColor = Color(0xFFF57C00), roomId = "class_amit"),
        ContactItem(id = "student4", name = "Sneha Gupta", lastMessage = "Thank you sir!", time = "Monday", avatarColor = Color(0xFF8E24AA), roomId = "class_sneha"),
    )

    val displayed = individuals
        .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize().background(EduBackground)) {

        // ── Search bar + action ───────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder   = { Text("Search", fontSize = 13.sp, color = EduGray) },
                leadingIcon   = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = EduGray, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                modifier   = Modifier.weight(1f),
                shape      = RoundedCornerShape(24.dp),
                colors     = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = EduBlue,
                    unfocusedBorderColor    = EduBorder,
                    focusedTextColor        = EduTextDark,
                    unfocusedTextColor      = EduTextDark,
                    focusedContainerColor   = Color(0xFFF5F7FA),
                    unfocusedContainerColor = Color(0xFFF5F7FA)
                )
            )

            // Add student button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {}
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = EduBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(2.dp))
                Text(
                    text       = "Add student",
                    color      = EduBlue,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        HorizontalDivider(color = EduBorder)

        // ── Contact list ──────────────────────────────────────────────────────
        LazyColumn(modifier = Modifier.fillMaxSize().background(Color.White)) {
            items(displayed) { contact ->
                ContactRow(
                    contact = contact,
                    onClick = { navController.navigate("${Routes.CHAT}/${contact.name}/${contact.id}/${contact.roomId}") }
                )
                HorizontalDivider(color = EduBorder, modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

// ── Contact row ───────────────────────────────────────────────────────────────

@Composable
fun ContactRow(contact: ContactItem, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar + optional online dot
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(contact.avatarColor)
            ) {
                Text(
                    text       = contact.name.first().uppercaseChar().toString(),
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp
                )
            }
            if (contact.isOnline) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name,        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = EduTextDark)
            Spacer(Modifier.height(2.dp))
            Text(contact.lastMessage, fontSize = 12.sp, color = EduGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Text(contact.time, fontSize = 11.sp, color = EduGray)
    }
}