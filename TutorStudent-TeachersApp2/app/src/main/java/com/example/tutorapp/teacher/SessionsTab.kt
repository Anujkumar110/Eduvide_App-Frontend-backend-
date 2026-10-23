package com.example.tutorapp.teacher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.tutorapp.ClassStatus
import com.example.tutorapp.ClassUiState
import com.example.tutorapp.SessionViewModel
import com.example.tutorapp.SharedSession
import com.example.tutorapp.network.SlotData
import com.example.tutorapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SessionsTab(
    viewModel:     SessionViewModel,
    onStartLive:   (roomId: String, role: String) -> Unit,
    isTabSelected: Boolean = true
) {
    var selectedTab      by remember { mutableStateOf(0) }
    var joinSession      by remember { mutableStateOf<SharedSession?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }

    val allSessions     by viewModel.sessions.collectAsStateWithLifecycle()
    val isLoading       by viewModel.isLoading.collectAsStateWithLifecycle()
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingStudents by viewModel.pendingStudents.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    fun onRefresh() {
        scope.launch {
            isRefreshing = true
            viewModel.loadTeacherSessions(forceRefresh = true)
            viewModel.loadPendingStudents()
            delay(500)
            isRefreshing = false
        }
    }

    LaunchedEffect(isTabSelected, joinSession) {
        if (isTabSelected) {
            viewModel.loadTeacherSessions(forceRefresh = false)
            viewModel.loadPendingStudents()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            if (isTabSelected) {
                viewModel.loadTeacherSessions(forceRefresh = true)
                viewModel.loadPendingStudents()
                val hasOngoing = viewModel.sessions.value.any { it.status == ClassStatus.ONGOING }
                if (hasOngoing && selectedTab == 0) {
                    selectedTab = 1
                }
            }
        }
    }

    val tabs = listOf("Scheduled", "Ongoing", "Completed", "Missed")

    val tabCounts = remember(allSessions) {
        listOf(
            allSessions.count { it.status == ClassStatus.SCHEDULED },
            allSessions.count { it.status == ClassStatus.ONGOING },
            allSessions.count { it.status == ClassStatus.COMPLETED },
            allSessions.count { it.status == ClassStatus.MISSED }
        )
    }

    val filteredSessions = remember(selectedTab, allSessions) {
        when (selectedTab) {
            0    -> allSessions.filter { it.status == ClassStatus.SCHEDULED }
            1    -> allSessions.filter { it.status == ClassStatus.ONGOING   }
            2    -> allSessions.filter { it.status == ClassStatus.COMPLETED }
            else -> allSessions.filter { it.status == ClassStatus.MISSED    }
        }
    }

    var errorMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState) {
        if (uiState is ClassUiState.Error) {
            errorMsg = (uiState as ClassUiState.Error).message
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize().background(EduBackground)) {

            Row(
                modifier          = Modifier.fillMaxWidth().background(Color.White),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = Color.White,
                    contentColor     = EduBlue,
                    edgePadding      = 0.dp,
                    modifier         = Modifier.weight(1f)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick  = { selectedTab = index },
                            text = {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text       = title,
                                        fontSize   = 14.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                        color      = if (selectedTab == index) EduBlue else EduGray
                                    )
                                    if (tabCounts[index] > 0) {
                                        Text(
                                            text       = "${tabCounts[index]}",
                                            fontSize   = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color      = if (selectedTab == index) Color.White else EduGray,
                                            modifier   = Modifier
                                                .clip(CircleShape)
                                                .background(if (selectedTab == index) EduBlue else EduBorder)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
                IconButton(
                    onClick  = { onRefresh() },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = EduBlue, modifier = Modifier.size(20.dp))
                }
            }

            HorizontalDivider(color = EduBorder)

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh    = { onRefresh() },
                modifier     = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading && allSessions.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EduBlue)
                        }
                    }
                    filteredSessions.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("📋", fontSize = 52.sp)
                                Text(
                                    "No ${tabs[selectedTab].lowercase()} sessions",
                                    fontSize   = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color      = EduTextDark
                                )
                                Text("Tap + to create a new session", fontSize = 13.sp, color = EduGray)
                                Spacer(Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = { onRefresh() },
                                    shape   = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp), tint = EduBlue)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Refresh", fontSize = 13.sp, color = EduBlue)
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 90.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredSessions) { session ->
                                TeacherSessionCard(
                                    session       = session,
                                    viewModel     = viewModel,           // ← ADDED
                                    onJoinClick   = { joinSession = session },
                                    onDeleteClick = { viewModel.deleteClass(session.roomId) }
                                )
                            }
                        }
                    }
                }
            }
        }

        errorMsg?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    TextButton(onClick = { errorMsg = null }) {
                        Text("OK", color = Color.White)
                    }
                }
            ) { Text(msg) }
        }

        FloatingActionButton(
            onClick        = { showCreateDialog = true },
            modifier       = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = EduBlue,
            contentColor   = Color.White,
            shape          = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier          = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Create Session", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    joinSession?.let { session ->
        JoinSessionDialog(
            session     = session,
            onDismiss   = { joinSession = null },
            onStartLive = {
                viewModel.startSessionWithWebSocket(session.roomId, session.studentName)
                viewModel.updateStatusOnBackend(session.roomId, "ongoing")
                onStartLive(session.roomId, "teacher")
            }
        )
    }

    if (showCreateDialog) {
        CreateSessionDialog(
            pendingStudents = pendingStudents,
            onDismiss       = { showCreateDialog = false },
            onCreate        = { title, description, price, date, time, durationMin, currency, maxStudents, studentId ->
                viewModel.createClass(
                    title       = title,
                    description = description,
                    price       = price,
                    date        = date,
                    time        = time,
                    durationMin = durationMin,
                    currency    = currency,
                    maxStudents = maxStudents,
                    studentId   = studentId,
                    onSuccess   = { showCreateDialog = false },
                    onError     = { err -> errorMsg = err }
                )
            }
        )
    }
}

// ── Teacher Session Card ──────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun TeacherSessionCard(
    session:       SharedSession,
    viewModel:     SessionViewModel,    // ← ADDED
    onJoinClick:   () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // ← ADDED: local state for student photo, initialized from session
    var studentPhoto by remember(session.studentId) { mutableStateOf(session.studentPhoto) }

    // ← ADDED: fetch photo if not already available
    LaunchedEffect(session.studentId) {
        if (studentPhoto.isNullOrBlank() && !session.studentId.isNullOrBlank()) {
            viewModel.getStudentPhoto(session.studentId!!) { photo ->
                if (!photo.isNullOrBlank()) studentPhoto = photo
            }
        }
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {

            // ── Student Avatar / Photo ─────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(session.avatarColor)
            ) {
                if (!studentPhoto.isNullOrBlank()) {           // ← uses local studentPhoto
                    AsyncImage(
                        model              = studentPhoto,
                        contentDescription = session.studentName,
                        modifier           = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale       = ContentScale.Crop
                    )
                } else {
                    Text(
                        text       = session.studentName.first().toString(),
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(session.studentName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = EduTextDark)
                Text(session.subject, fontSize = 12.sp, color = EduGray)
                Spacer(Modifier.height(4.dp))
                Text("${session.date}, ${session.time}", fontSize = 12.sp, color = EduBlue, fontWeight = FontWeight.Medium)
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(session.duration, fontSize = 11.sp, color = EduGray)
                    Text("•", fontSize = 11.sp, color = EduGray)
                    Text(
                        text       = if (session.price > 0) "₹${session.price.toInt()}" else "Free",
                        fontSize   = 11.sp,
                        color      = if (session.price > 0) Color(0xFF22C55E) else EduGray,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (session.status) {
                    ClassStatus.SCHEDULED -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(EduBlue)
                                .clickable { onJoinClick() }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text("Join", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    ClassStatus.ONGOING -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE53935))
                                .clickable { onJoinClick() }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                                Spacer(Modifier.width(4.dp))
                                Text("LIVE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    ClassStatus.COMPLETED -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF22C55E))
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text("Done ✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    ClassStatus.MISSED -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE53935).copy(alpha = 0.15f))
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text("Missed", color = Color(0xFFE53935), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                IconButton(
                    onClick  = { showDeleteConfirm = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint     = Color(0xFFE53935).copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text("Delete Session?", fontWeight = FontWeight.Bold) },
            text    = { Text("\"${session.studentName}\" session will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; onDeleteClick() },
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Create Session Dialog ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSessionDialog(
    pendingStudents: List<SlotData>,
    onDismiss: () -> Unit,
    onCreate: (
        title:       String,
        description: String,
        price:       Double,
        date:        String,
        time:        String,
        durationMin: Int,
        currency:    String,
        maxStudents: Int?,
        studentId:   String
    ) -> Unit
) {
    val context = LocalContext.current

    var title               by remember { mutableStateOf("") }
    var description         by remember { mutableStateOf("") }
    var priceText           by remember { mutableStateOf("") }
    var maxStudentsText     by remember { mutableStateOf("") }
    var duration            by remember { mutableStateOf("1 hour") }
    var durationExpanded    by remember { mutableStateOf(false) }

    var selectedStudent     by remember { mutableStateOf<SlotData?>(null) }
    var studentDropExpanded by remember { mutableStateOf(false) }

    var dateDisplay by remember { mutableStateOf("") }
    var dateValue   by remember { mutableStateOf("") }
    var timeDisplay by remember { mutableStateOf("") }
    var timeValue   by remember { mutableStateOf("") }

    val monthNames = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            val monthStr = monthNames[month]
            dateDisplay  = "$day $monthStr $year"
            dateValue    = "$day $monthStr $year"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hour, minute ->
            val ampm   = if (hour < 12) "AM" else "PM"
            val hour12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
            val minStr = minute.toString().padStart(2, '0')
            timeDisplay = "$hour12:$minStr $ampm"
            timeValue   = "$hour12:$minStr $ampm"
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        false
    )

    val durationOptions = listOf(60 to "1 hour")
    val selectedDurMin = durationOptions.firstOrNull { it.second == duration }?.first ?: 60

    val price = priceText.toDoubleOrNull() ?: -1.0
    val selectedStudentId = selectedStudent?.bookedBy?.takeIf { it.isNotBlank() }

    val isValid = title.isNotBlank() && description.isNotBlank()
            && dateValue.isNotBlank() && timeValue.isNotBlank()
            && price >= 0
            && selectedStudent != null
            && !selectedStudentId.isNullOrBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(EduBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Create New Session", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = EduTextDark)
            }
        },
        text = {
            Column(
                modifier            = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = EduBorder)

                ExposedDropdownMenuBox(
                    expanded         = studentDropExpanded,
                    onExpandedChange = {
                        if (pendingStudents.isNotEmpty()) studentDropExpanded = it
                    }
                ) {
                    OutlinedTextField(
                        value         = selectedStudent?.studentName?.ifBlank { "Student" } ?: "",
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Select Student *", fontSize = 12.sp) },
                        placeholder   = {
                            Text(
                                if (pendingStudents.isEmpty()) "No paid students yet" else "Tap to select student",
                                fontSize = 12.sp,
                                color    = Color(0xFFBBBBBB)
                            )
                        },
                        leadingIcon   = { Icon(Icons.Default.Person, null, tint = EduBlue, modifier = Modifier.size(18.dp)) },
                        trailingIcon  = {
                            if (pendingStudents.isNotEmpty())
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = studentDropExpanded)
                        },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                        shape         = RoundedCornerShape(10.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = EduBlue,
                            unfocusedBorderColor = if (selectedStudent == null) Color(0xFFE53935) else EduBorder,
                            focusedLabelColor    = EduBlue
                        )
                    )
                    if (pendingStudents.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded         = studentDropExpanded,
                            onDismissRequest = { studentDropExpanded = false }
                        ) {
                            pendingStudents.forEach { student ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                student.studentName?.ifBlank { "Student" } ?: "Student",
                                                fontSize   = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (!student.studentEmail.isNullOrBlank()) {
                                                Text(student.studentEmail, fontSize = 11.sp, color = EduGray)
                                            }
                                            Text(
                                                "${student.date} • ${student.startTime}",
                                                fontSize = 11.sp,
                                                color    = EduBlue
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedStudent     = student
                                        studentDropExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (selectedStudent != null && selectedStudentId.isNullOrBlank()) {
                    Text(
                        "⚠ Student ID missing — session create nahi hoga.",
                        fontSize = 11.sp,
                        color    = Color(0xFFE53935)
                    )
                }

                CField(value = title, onChange = { title = it }, label = "Session Title *", icon = Icons.Default.Title, hint = "e.g. Quadratic Equations")

                OutlinedTextField(
                    value         = description,
                    onValueChange = { description = it },
                    label         = { Text("Description *", fontSize = 12.sp) },
                    placeholder   = { Text("What will students learn?", fontSize = 12.sp, color = Color(0xFFBBBBBB)) },
                    leadingIcon   = { Icon(Icons.Default.Notes, null, tint = EduBlue, modifier = Modifier.size(18.dp)) },
                    modifier      = Modifier.fillMaxWidth(),
                    minLines      = 2,
                    maxLines      = 4,
                    shape         = RoundedCornerShape(10.dp),
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = EduBlue, unfocusedBorderColor = EduBorder, focusedLabelColor = EduBlue)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value           = priceText,
                        onValueChange   = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                        label           = { Text("Price (₹) *", fontSize = 12.sp) },
                        placeholder     = { Text("0 = Free", fontSize = 12.sp, color = Color(0xFFBBBBBB)) },
                        leadingIcon     = { Icon(Icons.Default.CurrencyRupee, null, tint = EduBlue, modifier = Modifier.size(18.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier        = Modifier.weight(1f),
                        singleLine      = true,
                        isError         = priceText.isNotBlank() && price < 0,
                        shape           = RoundedCornerShape(10.dp),
                        colors          = OutlinedTextFieldDefaults.colors(focusedBorderColor = EduBlue, unfocusedBorderColor = EduBorder, focusedLabelColor = EduBlue)
                    )
                    OutlinedTextField(
                        value           = maxStudentsText,
                        onValueChange   = { maxStudentsText = it.filter { c -> c.isDigit() } },
                        label           = { Text("Max Students", fontSize = 12.sp) },
                        placeholder     = { Text("Optional", fontSize = 12.sp, color = Color(0xFFBBBBBB)) },
                        leadingIcon     = { Icon(Icons.Default.Group, null, tint = EduBlue, modifier = Modifier.size(18.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier        = Modifier.weight(1f),
                        singleLine      = true,
                        shape           = RoundedCornerShape(10.dp),
                        colors          = OutlinedTextFieldDefaults.colors(focusedBorderColor = EduBlue, unfocusedBorderColor = EduBorder, focusedLabelColor = EduBlue)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = dateDisplay.ifBlank { "" },
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Date *", fontSize = 12.sp) },
                        placeholder   = { Text("Tap to select", fontSize = 12.sp, color = Color(0xFFBBBBBB)) },
                        leadingIcon   = { Icon(Icons.Default.CalendarMonth, null, tint = EduBlue, modifier = Modifier.size(18.dp)) },
                        modifier      = Modifier.weight(1f).clickable { datePickerDialog.show() },
                        enabled       = false,
                        shape         = RoundedCornerShape(10.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = EduTextDark, disabledBorderColor = if (dateValue.isBlank()) EduBorder else EduBlue,
                            disabledLabelColor = EduGray, disabledLeadingIconColor = EduBlue, disabledPlaceholderColor = Color(0xFFBBBBBB)
                        )
                    )
                    OutlinedTextField(
                        value         = timeDisplay.ifBlank { "" },
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Time *", fontSize = 12.sp) },
                        placeholder   = { Text("Tap to select", fontSize = 12.sp, color = Color(0xFFBBBBBB)) },
                        leadingIcon   = { Icon(Icons.Default.Schedule, null, tint = EduBlue, modifier = Modifier.size(18.dp)) },
                        modifier      = Modifier.weight(1f).clickable { timePickerDialog.show() },
                        enabled       = false,
                        shape         = RoundedCornerShape(10.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = EduTextDark, disabledBorderColor = if (timeValue.isBlank()) EduBorder else EduBlue,
                            disabledLabelColor = EduGray, disabledLeadingIconColor = EduBlue, disabledPlaceholderColor = Color(0xFFBBBBBB)
                        )
                    )
                }

                ExposedDropdownMenuBox(expanded = durationExpanded, onExpandedChange = { durationExpanded = it }) {
                    OutlinedTextField(
                        value         = duration,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Duration", fontSize = 12.sp) },
                        leadingIcon   = { Icon(Icons.Default.Timer, null, tint = EduBlue, modifier = Modifier.size(18.dp)) },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                        shape         = RoundedCornerShape(10.dp),
                        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = EduBlue, unfocusedBorderColor = EduBorder, focusedLabelColor = EduBlue)
                    )
                    ExposedDropdownMenu(expanded = durationExpanded, onDismissRequest = { durationExpanded = false }) {
                        durationOptions.forEach { (_, label) ->
                            DropdownMenuItem(text = { Text(label, fontSize = 13.sp) }, onClick = { duration = label; durationExpanded = false })
                        }
                    }
                }

                if (!isValid && (title.isNotBlank() || priceText.isNotBlank())) {
                    Text("* Student, title, description, price (≥0), date and time required", fontSize = 11.sp, color = Color(0xFFE53935))
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (isValid && !selectedStudentId.isNullOrBlank()) {
                            onCreate(title, description, price, dateValue, timeValue, selectedDurMin, "INR", maxStudentsText.toIntOrNull(), selectedStudentId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = if (isValid) EduBlue else Color(0xFFCCCCCC)),
                    enabled  = isValid
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create Session", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp)) {
                    Text("Cancel", color = EduGray)
                }
            }
        }
    )
}

// ── TextField Helper ──────────────────────────────────────────────────────────

@Composable
private fun CField(
    value:    String,
    onChange: (String) -> Unit,
    label:    String,
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    hint:     String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label, fontSize = 12.sp) },
        placeholder   = { Text(hint, fontSize = 12.sp, color = Color(0xFFBBBBBB)) },
        leadingIcon   = { Icon(icon, null, tint = EduBlue, modifier = Modifier.size(18.dp)) },
        modifier      = modifier,
        singleLine    = true,
        shape         = RoundedCornerShape(10.dp),
        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = EduBlue, unfocusedBorderColor = EduBorder, focusedLabelColor = EduBlue)
    )
}

// ── Join Session Dialog ───────────────────────────────────────────────────────

@Composable


private fun JoinSessionDialog(
    session:     SharedSession,
    onDismiss:   () -> Unit,
    onStartLive: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.size(60.dp).clip(CircleShape).background(session.avatarColor)
                ) {
                    if (!session.studentPhoto.isNullOrBlank()) {
                        AsyncImage(
                            model              = session.studentPhoto,
                            contentDescription = session.studentName,
                            modifier           = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale       = ContentScale.Crop
                        )
                    } else {
                        Text(session.studentName.first().toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Start Session", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = EduTextDark, textAlign = TextAlign.Center)
                Text(session.studentName, fontSize = 14.sp, color = EduGray, textAlign = TextAlign.Center)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = EduBorder)
                JoinDetailRow(Icons.Filled.Title,         "Title",    session.subject)
                JoinDetailRow(Icons.Filled.Notes,         "Topic",    session.topic)
                JoinDetailRow(Icons.Filled.Schedule,      "Time",     "${session.date}, ${session.time}")
                JoinDetailRow(Icons.Filled.Timer,         "Duration", session.duration)
                JoinDetailRow(Icons.Filled.CurrencyRupee, "Price",    if (session.price > 0) "₹${session.price.toInt()}" else "Free")
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick  = { onDismiss(); onStartLive() },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Icon(Icons.Filled.Videocam, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Live Class", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp)) {
                    Text("Cancel", color = EduGray)
                }
            }
        }
    )
}

@Composable
private fun JoinDetailRow(
    icon:  androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = EduBlue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label:", fontSize = 13.sp, color = EduGray, modifier = Modifier.width(64.dp))
        Text(value,     fontSize = 13.sp, fontWeight = FontWeight.Medium, color = EduTextDark)
    }
}