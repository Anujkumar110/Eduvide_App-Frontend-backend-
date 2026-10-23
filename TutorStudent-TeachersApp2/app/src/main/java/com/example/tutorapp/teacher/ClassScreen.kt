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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private data class ClassItem(
    val id:          Int,
    val studentName: String,
    val subject:     String,
    val grade:       String,
    val schedule:    String,
    val mode:        String,
    val fee:         String,
    val status:      String,
    val avatarColor: Color
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ClassScreenPreview() {
    ClassScreen(navController = rememberNavController())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassScreen(navController: NavController) {

    var selectedTab          by remember { mutableStateOf(0) }
    var showAddDialog        by remember { mutableStateOf(false) }
    var selectedClass        by remember { mutableStateOf<ClassItem?>(null) }

    val classes = remember {
        mutableStateListOf(
            ClassItem(1, "Rahul Sharma",  "Mathematics", "Class 9",  "Mon, Wed, Fri  5:00 PM", "Online",  "₹500/hr",  "Active",   Color(0xFF1E88E5)),
            ClassItem(2, "Priya Patel",   "Science",     "Class 8",  "Tue, Thu  4:00 PM",      "Offline", "₹450/hr",  "Active",   Color(0xFF43A047)),
            ClassItem(3, "Amit Kumar",    "English",     "Class 6",  "Mon, Wed  6:00 PM",      "Online",  "₹400/hr",  "Active",   Color(0xFFF57C00)),
            ClassItem(4, "Sneha Gupta",   "Computer",    "Class 10", "Sat, Sun  10:00 AM",     "Online",  "₹600/hr",  "Paused",   Color(0xFF8E24AA)),
            ClassItem(5, "Dev Sharma",    "Maths",       "Class 7",  "Tue, Thu  5:30 PM",      "Offline", "₹450/hr",  "Completed",Color(0xFF00897B)),
        )
    }

    val tabs      = listOf("All", "Active", "Paused", "Completed")
    val displayed = if (selectedTab == 0) classes
    else classes.filter { it.status == tabs[selectedTab] }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                title = {
                    Text("My Classes", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Class", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = EduBlue)
            )
        },
        containerColor = EduBackground
    ) { innerPadding ->

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // ── Summary stats ─────────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ClassStat("${classes.count { it.status == "Active" }}",    "Active",    Color(0xFF43A047))
                ClassStatDivider()
                ClassStat("${classes.count { it.status == "Paused" }}",    "Paused",    Color(0xFFF57C00))
                ClassStatDivider()
                ClassStat("${classes.count { it.status == "Completed" }}", "Completed", EduBlue)
                ClassStatDivider()
                ClassStat("${classes.size}",                               "Total",     EduTextDark)
            }

            HorizontalDivider(color = EduBorder)

            // ── Tab row ───────────────────────────────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Color.White,
                contentColor     = EduBlue,
                edgePadding      = 0.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text = {
                            Text(
                                text       = title,
                                fontSize   = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (selectedTab == index) EduBlue else EduGray
                            )
                        }
                    )
                }
            }

            // ── Class list ────────────────────────────────────────────────────
            if (displayed.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("📚", fontSize = 48.sp)
                        Text("No classes in this category", fontSize = 14.sp, color = EduGray)
                        TextButton(onClick = { showAddDialog = true }) {
                            Text("+ Add a class", color = EduBlue)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayed) { cls ->
                        ClassCard(
                            cls     = cls,
                            onClick = { selectedClass = cls }
                        )
                    }
                }
            }
        }
    }

    // ── Add class dialog ──────────────────────────────────────────────────────
    if (showAddDialog) {
        AddClassDialog(
            onDismiss = { showAddDialog = false },
            onAdd     = { newClass ->
                classes.add(newClass.copy(id = classes.size + 1))
                showAddDialog = false
            }
        )
    }

    // ── Class detail dialog ───────────────────────────────────────────────────
    selectedClass?.let { cls ->
        ClassDetailDialog(
            cls       = cls,
            onDismiss = { selectedClass = null }
        )
    }
}

// ── Class card ────────────────────────────────────────────────────────────────

@Composable
private fun ClassCard(cls: ClassItem, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(cls.avatarColor)
            ) {
                Text(cls.studentName.first().toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(cls.studentName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = EduTextDark)
                Text("${cls.subject}  •  ${cls.grade}", fontSize = 12.sp, color = EduGray)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniChip(text = cls.mode,     color = if (cls.mode == "Online") Color(0xFF1E88E5) else Color(0xFF43A047))
                    MiniChip(text = cls.fee,      color = EduTextDark)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = EduGray, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(cls.schedule, fontSize = 11.sp, color = EduGray)
                }
            }

            // Status badge
            val (badgeBg, badgeText) = when (cls.status) {
                "Active"    -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
                "Paused"    -> Color(0xFFFFF3E0) to Color(0xFFE65100)
                "Completed" -> EduBlueLight      to EduBlue
                else        -> Color(0xFFF5F5F5) to EduGray
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(cls.status, fontSize = 11.sp, color = badgeText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Class detail dialog ───────────────────────────────────────────────────────

@Composable
private fun ClassDetailDialog(cls: ClassItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.size(40.dp).clip(CircleShape).background(cls.avatarColor)
                ) {
                    Text(cls.studentName.first().toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Column {
                    Text(cls.studentName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = EduTextDark)
                    Text("${cls.subject}  •  ${cls.grade}", fontSize = 12.sp, color = EduGray)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(color = EduBorder)
                ClassDetailRow(Icons.Filled.Schedule,      "Schedule",  cls.schedule)
                ClassDetailRow(Icons.Filled.Wifi,          "Mode",      cls.mode)
                ClassDetailRow(Icons.Filled.AttachMoney,   "Fee",       cls.fee)
                ClassDetailRow(Icons.Filled.Info,          "Status",    cls.status)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors  = ButtonDefaults.buttonColors(containerColor = EduBlue),
                shape   = RoundedCornerShape(8.dp)
            ) {
                Text("Close", color = Color.White)
            }
        }
    )
}

@Composable
private fun ClassDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = EduBlue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text("$label:", fontSize = 13.sp, color = EduGray, modifier = Modifier.width(70.dp))
        Text(value, fontSize = 13.sp, color = EduTextDark, fontWeight = FontWeight.Medium)
    }
}

// ── Add class dialog ──────────────────────────────────────────────────────────

@Composable
private fun AddClassDialog(
    onDismiss: () -> Unit,
    onAdd:     (ClassItem) -> Unit
) {
    var studentName  by remember { mutableStateOf("") }
    var subject      by remember { mutableStateOf("") }
    var grade        by remember { mutableStateOf("") }
    var fee          by remember { mutableStateOf("") }
    var showErrors   by remember { mutableStateOf(false) }

    val nameError    = showErrors && studentName.isBlank()
    val subjectError = showErrors && subject.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Text("Add New Class", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = EduTextDark)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EduTextField(value = studentName, onValueChange = { studentName = it },
                    label = "Student Name", isError = nameError, errorMessage = "Required")
                EduTextField(value = subject, onValueChange = { subject = it },
                    label = "Subject", isError = subjectError, errorMessage = "Required")
                EduTextField(value = grade, onValueChange = { grade = it }, label = "Grade / Class")
                EduTextField(value = fee, onValueChange = { fee = it }, label = "Fee per hour (₹)")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    showErrors = true
                    if (studentName.isNotBlank() && subject.isNotBlank()) {
                        onAdd(ClassItem(0, studentName, subject, grade.ifBlank { "N/A" },
                            "Not scheduled", "Online", fee.ifBlank { "Not set" }, "Active",
                            Color(0xFF1E88E5)))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EduBlue),
                shape  = RoundedCornerShape(8.dp)
            ) { Text("Add", color = Color.White, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = EduGray) }
        }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun ClassStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color)
        Text(label, fontSize = 11.sp, color = EduGray)
    }
}

@Composable
private fun ClassStatDivider() {
    Box(modifier = Modifier.width(1.dp).height(28.dp).background(EduBorder))
}

@Composable
private fun MiniChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
    }
}