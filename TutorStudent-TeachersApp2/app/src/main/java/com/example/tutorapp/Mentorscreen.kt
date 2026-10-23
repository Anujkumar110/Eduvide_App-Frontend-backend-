package com.example.tutorapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.SlotData
import com.example.tutorapp.network.TeacherItem
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.network.idForSlotApis
import com.example.tutorapp.teacher.parseISODateToReadable
import com.example.tutorapp.ui.theme.*
import com.example.tutorapp.viewmodel.TeacherListState
import com.example.tutorapp.viewmodel.TeacherViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── Data model ────────────────────────────────────────────────────────────────

data class SlotDisplayItem(
    val id:        String,
    val startTime: String,
    val endTime:   String,
    val date:      String,
    val price:     Int,
    val status:    String
)

private fun SlotData.toDisplayItem(): SlotDisplayItem {
    val startDisplay = try {
        val sdf24 = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        val sdf12 = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        sdf12.format(sdf24.parse(this.startTime)!!)
    } catch (e: Exception) { this.startTime }

    val endDisplay = try {
        val sdf24 = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        val sdf12 = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        sdf12.format(sdf24.parse(this.endTime)!!)
    } catch (e: Exception) { this.endTime }

    val parsedDate  = parseISODateToReadable(this.date)
    val todayStr    = SimpleDateFormat("dd MMM", Locale.ENGLISH).format(Date())
    val dateDisplay = if (parsedDate == todayStr) "Today, $parsedDate" else parsedDate

    return SlotDisplayItem(
        id        = this.id,
        startTime = startDisplay,
        endTime   = endDisplay,
        date      = dateDisplay,
        price     = this.priceInRupees,
        status    = this.status
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentorScreen(navController: NavHostController) {

    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vm: TeacherViewModel = viewModel()
    val uiState by vm.state.collectAsState()

    var searchTutor  by remember { mutableStateOf("") }
    var searchDomain by remember { mutableStateOf("") }
    var selectedTab  by remember { mutableIntStateOf(0) }

    var isRefreshing by remember { mutableStateOf(false) }

    // ── Slot sheet state ───────────────────────────────────────────────────────
    var showSlotSheet   by remember { mutableStateOf(false) }
    var selectedTeacher by remember { mutableStateOf<TeacherItem?>(null) }
    var teacherSlots    by remember { mutableStateOf<List<SlotDisplayItem>>(emptyList()) }
    var rawSlots        by remember { mutableStateOf<List<SlotData>>(emptyList()) }
    var slotsLoading    by remember { mutableStateOf(false) }
    var slotsError      by remember { mutableStateOf("") }

    // ── Register sheet state ───────────────────────────────────────────────────
    var showRegisterSheet by remember { mutableStateOf(false) }
    var preSelectedSlotId by remember { mutableStateOf<String?>(null) }
    var registerSlots     by remember { mutableStateOf<List<SlotData>>(emptyList()) }

    val registerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val slotSheetState     = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope              = rememberCoroutineScope()
    val snackbarHostState  = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.loadTeachers(context) }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) vm.loadTeachers(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun onRefresh() {
        scope.launch {
            isRefreshing = true
            vm.loadTeachers(context)
            delay(500)
            isRefreshing = false
        }
    }

    fun fetchSlots(teacher: TeacherItem) {
        scope.launch {
            slotsLoading = true
            slotsError   = ""
            teacherSlots = emptyList()
            rawSlots     = emptyList()
            try {
                val authHeader = TokenManager.getBearerToken(context)
                    .takeIf { it.isNotBlank() && it != "Bearer " } ?: ""
                val teacherKey = teacher.idForSlotApis()
                if (teacherKey.isBlank()) {
                    slotsError = "Teacher id missing."
                    return@launch
                }
                val ist    = TimeZone.getTimeZone("Asia/Kolkata")
                val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply { timeZone = ist }
                val base   = Calendar.getInstance(ist)
                val mergedDisplay = mutableListOf<SlotDisplayItem>()
                val mergedRaw     = mutableListOf<SlotData>()
                var lastMessage: String? = null
                for (day in 0 until 5) {
                    val c = base.clone() as Calendar
                    c.add(Calendar.DAY_OF_YEAR, day)
                    val dateStr = isoFmt.format(c.time)
                    val resp = RetrofitClient.api.getTeacherSlots(
                        token     = authHeader,
                        teacherId = teacherKey,
                        date      = dateStr
                    )
                    when {
                        resp.isSuccessful && resp.body()?.success == true -> {
                            val data = resp.body()!!.data
                            mergedDisplay += data.map { it.toDisplayItem() }
                            mergedRaw     += data
                        }
                        resp.code() == 403 -> lastMessage = "Teacher profile not verified yet."
                        resp.code() == 404 -> lastMessage = "Teacher profile not found."
                        else               -> lastMessage = resp.body()?.message ?: "Could not load slots (${resp.code()})"
                    }
                }
                teacherSlots = mergedDisplay
                rawSlots     = mergedRaw
                slotsError   = if (mergedDisplay.isEmpty() && lastMessage != null) lastMessage!! else ""
            } catch (e: Exception) {
                slotsError = "Network error: ${e.message}"
            } finally {
                slotsLoading = false
            }
        }
    }

    // ── Slot bottom sheet ─────────────────────────────────────────────────────
    if (showSlotSheet && selectedTeacher != null) {
        SlotBookingSheet(
            teacher        = selectedTeacher!!,
            slots          = teacherSlots,
            isLoading      = slotsLoading,
            slotsError     = slotsError,
            sheetState     = slotSheetState,
            onDismiss      = {
                scope.launch { slotSheetState.hide() }.invokeOnCompletion {
                    showSlotSheet = false
                }
            },
            onSlotSelected = { slot ->
                // ✅ KEY: pehle registerSlots set karo rawSlots se, phir sheet open karo
                registerSlots     = rawSlots.toList()
                preSelectedSlotId = slot.id
                scope.launch { slotSheetState.hide() }.invokeOnCompletion {
                    showSlotSheet     = false
                    showRegisterSheet = true
                }
            }
        )
    }

    if (showRegisterSheet && selectedTeacher != null) {
        RegisterBottomSheet(
            mentor            = selectedTeacher!!,
            sheetState        = registerSheetState,
            navController     = navController,
            preSelectedSlotId = preSelectedSlotId,
            prefetchedSlots   = registerSlots,   // ✅ rawSlots yahan pahunch rahi hain
            onDismiss         = {
                scope.launch { registerSheetState.hide() }.invokeOnCompletion {
                    showRegisterSheet = false
                    preSelectedSlotId = null
                    registerSlots     = emptyList()
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite),
                title  = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier         = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("E", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("EduVibe", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                },
                actions = {
                    IconButton(onClick = { onRefresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextGray, modifier = Modifier.size(22.dp))
                    }
                    IconButton(
                        onClick  = { navController.navigate("NotificationScreen") },
                        modifier = Modifier.size(38.dp).background(LightBg, CircleShape).clip(CircleShape)
                    ) {
                        Icon(Icons.Outlined.Notifications, null, tint = TextGray, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(20.dp))
                    IconButton(
                        onClick  = { navController.navigate("SettingsScreen") },
                        modifier = Modifier.size(38.dp).background(LightBg, CircleShape).clip(CircleShape)
                    ) {
                        Icon(Icons.Default.Settings, null, tint = TextGray, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val tabs = listOf(
                    Pair("Home",     Icons.Default.Home),
                    Pair("Status",   Icons.Outlined.DateRange),
                    Pair("Settings", Icons.Default.Settings)
                )
                tabs.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick  = {
                            selectedTab = index
                            when (index) {
                                1 -> navController.navigate("StatusScreen")
                                2 -> navController.navigate("SettingsScreen")
                            }
                        },
                        icon   = { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
                        label  = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = PrimaryBlue,
                            selectedTextColor   = PrimaryBlue,
                            unselectedIconColor = TextGray,
                            unselectedTextColor = TextGray,
                            indicatorColor      = Color(0xFFE8F0FE)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { onRefresh() },
            modifier     = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightBg)
            ) {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .background(CardWhite)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value         = searchTutor,
                        onValueChange = { searchTutor = it },
                        placeholder   = { Text("Search tutors...", color = TextGray, fontSize = 13.sp) },
                        leadingIcon   = { Icon(Icons.Default.Search, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp)) },
                        modifier   = Modifier.fillMaxWidth().height(50.dp),
                        shape      = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors     = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = PrimaryBlue,
                            unfocusedBorderColor = Color(0xFFDDDDDD)
                        )
                    )
                    OutlinedTextField(
                        value         = searchDomain,
                        onValueChange = { searchDomain = it },
                        placeholder   = { Text("Search by subject...", color = TextGray, fontSize = 13.sp) },
                        leadingIcon   = { Icon(Icons.Default.Search, null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                        modifier   = Modifier.fillMaxWidth().height(50.dp),
                        shape      = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors     = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = PrimaryBlue,
                            unfocusedBorderColor = Color(0xFFDDDDDD)
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))

                when (val s = uiState) {
                    is TeacherListState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PrimaryBlue)
                                Spacer(Modifier.height(16.dp))
                                Text("Loading teachers...", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }

                    is TeacherListState.Error -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier            = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    "Error:\n${s.message}",
                                    color     = Color.Red,
                                    fontSize  = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { vm.loadTeachers(context) },
                                    colors  = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                ) {
                                    Text("Retry", color = Color.White)
                                }
                            }
                        }
                    }

                    is TeacherListState.Success -> {
                        val filtered = s.teachers.filter { t ->
                            val nq       = searchTutor.trim()
                            val dq       = searchDomain.trim()
                            val nameOk   = nq.isBlank() || t.fullName.contains(nq, ignoreCase = true)
                            val domainOk = dq.isBlank() || run {
                                val q = dq.lowercase()
                                t.subjects.any { it.lowercase().contains(q) } || t.bio.lowercase().contains(q)
                            }
                            nameOk && domainOk
                        }

                        if (filtered.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier            = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        "No teachers found!\nTry another name or subject.",
                                        color     = Color(0xFFFF6600),
                                        fontSize  = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    if (searchTutor.isNotBlank() || searchDomain.isNotBlank()) {
                                        Spacer(Modifier.height(12.dp))
                                        Button(
                                            onClick = { searchTutor = ""; searchDomain = "" },
                                            colors  = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                        ) {
                                            Text("Clear Search", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier            = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                itemsIndexed(filtered) { _, teacher ->
                                    MentorCard(
                                        teacher    = teacher,
                                        onReadMore = { navController.navigate("MentorDetailScreen/${teacher.id}") },
                                        onRegister = {
                                            selectedTeacher   = teacher
                                            registerSlots     = emptyList()
                                            preSelectedSlotId = null
                                            showSlotSheet     = true
                                            fetchSlots(teacher)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Mentor Card ───────────────────────────────────────────────────────────────

@Composable
private fun MentorCard(teacher: TeacherItem, onReadMore: () -> Unit, onRegister: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column {
            Box {
                AsyncImage(
                    model              = RetrofitClient.fullUrl(teacher.profilePhoto),
                    contentDescription = teacher.fullName,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                    contentScale       = ContentScale.Crop
                )
                if (teacher.isLive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color.Red.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                            Text("LIVE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 0.5.sp)
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (teacher.isLive) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier              = Modifier.padding(bottom = 3.dp)
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                            Text("LIVE NOW", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.Red, letterSpacing = 0.5.sp)
                        }
                    }
                    Text(teacher.fullName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    val subjectText = when {
                        teacher.subjects.isNotEmpty() -> teacher.subjects.joinToString(" • ")
                        teacher.bio.isNotBlank()      -> teacher.bio.lines().firstOrNull()?.take(40) ?: ""
                        else                          -> ""
                    }
                    if (subjectText.isNotBlank()) {
                        Text(subjectText, fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (teacher.bio.isNotBlank()) {
                        Text(teacher.bio, fontSize = 12.sp, color = TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(Modifier.height(8.dp))

                val rating = teacher.rating.average.toInt().coerceIn(0, 5)
                Row {
                    repeat(5) { i ->
                        Text(
                            if (i < rating) "★" else "☆",
                            fontSize = 16.sp,
                            color    = if (i < rating) Color(0xFFFFC107) else Color(0xFFDDDDDD)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Read more...",
                        fontSize   = 12.sp,
                        color      = PrimaryBlue,
                        fontWeight = FontWeight.Medium,
                        modifier   = Modifier.clickable { onReadMore() }
                    )
                    Button(
                        onClick        = { onRegister() },
                        modifier       = Modifier.height(36.dp),
                        shape          = RoundedCornerShape(8.dp),
                        colors         = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (teacher.isLive) "Book Slot" else "Check Slots",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ── Slot Booking Bottom Sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotBookingSheet(
    teacher:        TeacherItem,
    slots:          List<SlotDisplayItem>,
    isLoading:      Boolean,
    slotsError:     String,
    sheetState:     SheetState,
    onDismiss:      () -> Unit,
    onSlotSelected: (SlotDisplayItem) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color.White,
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFDDDDDD))
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model              = RetrofitClient.fullUrl(teacher.profilePhoto),
                    contentDescription = teacher.fullName,
                    modifier           = Modifier.size(46.dp).clip(CircleShape),
                    contentScale       = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(teacher.fullName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        if (teacher.isLive) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Red)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("LIVE", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }
                    }
                    Text("Available slots (next 5 days) — tap to book", fontSize = 12.sp, color = TextGray)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, null, tint = TextGray)
                }
            }

            HorizontalDivider(color = Color(0xFFEEEEEE))

            when {
                isLoading -> {
                    Box(
                        modifier         = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 3.dp)
                            Spacer(Modifier.height(10.dp))
                            Text("Loading slots...", fontSize = 13.sp, color = TextGray)
                        }
                    }
                }

                slotsError.isNotEmpty() -> {
                    Box(
                        modifier         = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier            = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFE53935), modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(slotsError, fontSize = 13.sp, color = Color(0xFFE53935), textAlign = TextAlign.Center)
                        }
                    }
                }

                slots.isEmpty() -> {
                    Box(
                        modifier         = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.EventBusy, null, tint = TextGray, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No slots in the next few days", fontSize = 14.sp, color = TextGray)
                            Spacer(Modifier.height(4.dp))
                            Text("Check back later or contact the teacher", fontSize = 12.sp, color = TextGray.copy(alpha = 0.7f))
                        }
                    }
                }

                else -> {
                    val availableCount = slots.count { it.status == "available" }
                    val bookedCount    = slots.count { it.status == "booked" }

                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(slots.firstOrNull()?.date ?: "", fontSize = 12.sp, color = TextGray, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (availableCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("$availableCount free", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (bookedCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFE3F2FD))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("$bookedCount booked", fontSize = 11.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier            = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(slots) { slot ->
                            val isAvailable = slot.status == "available"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.5.dp,
                                        if (isAvailable) Color(0xFF1565C0) else Color(0xFFEEEEEE),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isAvailable) Color(0xFFE3F2FD) else Color(0xFFF5F5F5))
                                    .then(
                                        if (isAvailable) Modifier.clickable { onSlotSelected(slot) }
                                        else Modifier
                                    )
                                    .padding(14.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Schedule,
                                        null,
                                        tint     = if (isAvailable) PrimaryBlue else TextGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(slot.date, fontSize = 11.sp, color = TextGray, fontWeight = FontWeight.Medium)
                                        Text(
                                            "${slot.startTime}  –  ${slot.endTime}",
                                            fontSize   = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color      = if (isAvailable) PrimaryBlue else TextPrimary
                                        )
                                        Text("₹${slot.price}", fontSize = 12.sp, color = TextGray)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isAvailable) Color(0xFF1565C0) else Color(0xFFFCE4EC))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        if (isAvailable) "Tap to Book" else "Booked",
                                        fontSize   = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = if (isAvailable) Color.White else Color(0xFFC62828)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}