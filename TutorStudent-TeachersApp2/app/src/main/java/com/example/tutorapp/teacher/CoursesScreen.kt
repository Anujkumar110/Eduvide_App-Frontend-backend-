package com.example.tutorapp.teacher

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.tutorapp.ui.theme.EduBlue
import com.example.tutorapp.ui.theme.EduBlueLight
import com.example.tutorapp.ui.theme.EduBorder
import com.example.tutorapp.ui.theme.EduChipBg
import com.example.tutorapp.ui.theme.EduGray
import com.example.tutorapp.ui.theme.EduTextDark
import com.example.tutorapp.ui.theme.EduBackground

// ── Model ─────────────────────────────────────────────────────────────────────

data class Course(
    val title:     String,
    val subject:   String,
    val price:     String,
    val students:  Int
)

data class Batch(
    val title:    String,
    val schedule: String,
    val students: Int
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CoursesScreenPreview() {
    CoursesScreen(navController = rememberNavController())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(navController: NavController) {

    var selectedTab by remember { mutableStateOf(0) }

    // ── Sample data (empty by default to match Figma "0 Courses") ────────────
    val courses = remember { mutableStateListOf<Course>() }
    val batches = remember { mutableStateListOf<Batch>() }

    var showCreateCourseDialog by remember { mutableStateOf(false) }
    var showCreateBatchDialog  by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text       = "Courses",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = EduBlue
                )
            )
        },
        containerColor = EduBackground
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // ── Tabs ──────────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Color.White,
                contentColor     = EduBlue
            ) {
                listOf("Courses", "Batches").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text = {
                            Text(
                                text       = title,
                                fontSize   = 14.sp,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (selectedTab == index) EduBlue else EduGray
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> CoursesContent(
                    courses              = courses,
                    onCreateCourseClick  = { showCreateCourseDialog = true }
                )
                1 -> BatchesContent(
                    batches             = batches,
                    onCreateBatchClick  = { showCreateBatchDialog = true }
                )
            }
        }
    }

    // ── Create Course Dialog ──────────────────────────────────────────────────
    if (showCreateCourseDialog) {
        CreateCourseDialog(
            onDismiss = { showCreateCourseDialog = false },
            onCreate  = { course ->
                courses.add(course)
                showCreateCourseDialog = false
            }
        )
    }

    // ── Create Batch Dialog ───────────────────────────────────────────────────
    if (showCreateBatchDialog) {
        CreateBatchDialog(
            onDismiss = { showCreateBatchDialog = false },
            onCreate  = { batch ->
                batches.add(batch)
                showCreateBatchDialog = false
            }
        )
    }
}

// ── Courses content ───────────────────────────────────────────────────────────

@Composable
private fun CoursesContent(
    courses:             List<Course>,
    onCreateCourseClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EduBackground)
    ) {

        // ── Header row ────────────────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = "${courses.size} Courses",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
                color      = EduTextDark
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(EduBlue)
                    .clickable { onCreateCourseClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text       = "Create Course",
                        color      = Color.White,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        HorizontalDivider(color = EduBorder)

        if (courses.isEmpty()) {
            // Empty state
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, EduBorder, RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("📚", fontSize = 48.sp)
                        Text(
                            text       = "Add detail about your courses.",
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp,
                            color      = EduTextDark,
                            textAlign  = TextAlign.Center
                        )
                        Text(
                            text      = "A course is a structured program with a defined syllabus and price.",
                            fontSize  = 13.sp,
                            color     = EduGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(courses) { course ->
                    CourseCard(course)
                }
            }
        }
    }
}

// ── Batches content ───────────────────────────────────────────────────────────

@Composable
private fun BatchesContent(
    batches:            List<Batch>,
    onCreateBatchClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EduBackground)
    ) {

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = "${batches.size} Batches",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
                color      = EduTextDark
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(EduBlue)
                    .clickable { onCreateBatchClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text       = "Create Batch",
                        color      = Color.White,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        HorizontalDivider(color = EduBorder)

        if (batches.isEmpty()) {
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, EduBorder, RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🗂️", fontSize = 48.sp)
                        Text(
                            text       = "No batches yet.",
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp,
                            color      = EduTextDark
                        )
                        Text(
                            text      = "Group your students into batches for easier session management.",
                            fontSize  = 13.sp,
                            color     = EduGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(batches) { batch ->
                    BatchCard(batch)
                }
            }
        }
    }
}

// ── Course card ───────────────────────────────────────────────────────────────

@Composable
private fun CourseCard(course: Course) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(EduBlueLight)
            ) {
                Icon(
                    Icons.Filled.MenuBook,
                    contentDescription = null,
                    tint     = EduBlue,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(course.title,   fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = EduTextDark)
                Text(course.subject, fontSize = 12.sp, color = EduGray)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("₹${course.price}/hr", fontSize = 12.sp, color = EduBlue, fontWeight = FontWeight.Medium)
                    Text("• ${course.students} students", fontSize = 12.sp, color = EduGray)
                }
            }

            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = EduGray, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Batch card ────────────────────────────────────────────────────────────────

@Composable
private fun BatchCard(batch: Batch) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF3E0))
            ) {
                Icon(
                    Icons.Filled.Groups,
                    contentDescription = null,
                    tint     = Color(0xFFFF9800),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(batch.title,    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = EduTextDark)
                Text(batch.schedule, fontSize = 12.sp, color = EduGray)
                Spacer(Modifier.height(4.dp))
                Text("${batch.students} students", fontSize = 12.sp, color = EduGray)
            }

            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = EduGray, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Create Course Dialog ──────────────────────────────────────────────────────

@Composable
private fun CreateCourseDialog(
    onDismiss: () -> Unit,
    onCreate:  (Course) -> Unit
) {
    var title      by remember { mutableStateOf("") }
    var subject    by remember { mutableStateOf("") }
    var price      by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }

    val titleError   = showErrors && title.isBlank()
    val subjectError = showErrors && subject.isBlank()
    val priceError   = showErrors && (price.isBlank() || price.toDoubleOrNull() == null)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(16.dp),
        title = {
            Text(
                text       = "Create Course",
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp,
                color      = EduTextDark
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EduTextField(
                    value         = title,
                    onValueChange = { title = it },
                    label         = "Course Title",
                    isError       = titleError,
                    errorMessage  = "Title is required"
                )
                EduTextField(
                    value         = subject,
                    onValueChange = { subject = it },
                    label         = "Subject / Category",
                    isError       = subjectError,
                    errorMessage  = "Subject is required"
                )
                EduTextField(
                    value         = price,
                    onValueChange = { price = it },
                    label         = "Price per hour (₹)",
                    isError       = priceError,
                    errorMessage  = "Enter a valid price"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    showErrors = true
                    if (title.isNotBlank() && subject.isNotBlank() && price.toDoubleOrNull() != null) {
                        onCreate(Course(title, subject, price, 0))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EduBlue),
                shape  = RoundedCornerShape(8.dp)
            ) {
                Text("Create", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = EduGray)
            }
        }
    )
}

// ── Create Batch Dialog ───────────────────────────────────────────────────────

@Composable
private fun CreateBatchDialog(
    onDismiss: () -> Unit,
    onCreate:  (Batch) -> Unit
) {
    var title      by remember { mutableStateOf("") }
    var schedule   by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }

    val titleError    = showErrors && title.isBlank()
    val scheduleError = showErrors && schedule.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(16.dp),
        title = {
            Text(
                text       = "Create Batch",
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp,
                color      = EduTextDark
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EduTextField(
                    value         = title,
                    onValueChange = { title = it },
                    label         = "Batch Name",
                    isError       = titleError,
                    errorMessage  = "Batch name is required"
                )
                EduTextField(
                    value         = schedule,
                    onValueChange = { schedule = it },
                    label         = "Schedule (e.g. Mon, Wed 5PM)",
                    isError       = scheduleError,
                    errorMessage  = "Schedule is required"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    showErrors = true
                    if (title.isNotBlank() && schedule.isNotBlank()) {
                        onCreate(Batch(title, schedule, 0))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EduBlue),
                shape  = RoundedCornerShape(8.dp)
            ) {
                Text("Create", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = EduGray)
            }
        }
    )
}