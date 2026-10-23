package com.example.tutorapp.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.tutorapp.network.GenericResponse
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.SlotData
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.ui.theme.EduBlue
import com.example.tutorapp.ui.theme.EduBlueLight
import com.example.tutorapp.ui.theme.EduBorder
import com.example.tutorapp.ui.theme.EduGray
import com.example.tutorapp.ui.theme.EduTextDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

// ── Models ────────────────────────────────────────────────────────────────────

data class SlotItem(
    val id:           String,
    val date:         String,   // readable "23 May"
    val rawDate:      String,   // ISO "yyyy-MM-dd" — for expiry check
    val startTime:    String,
    val endTime:      String,
    val price:        Int,
    val status:       SlotStatus,
    val studentName:  String? = null,
    val studentEmail: String? = null,
    val studentPhone: String? = null
)

enum class SlotStatus(val label: String, val color: Color) {
    AVAILABLE("Available", Color(0xFF1B8B5E)),
    BOOKED("Booked",       Color(0xFF1565C0)),
    COMPLETED("Completed", Color(0xFF6A1B9A)),
    MISSED("Missed",       Color(0xFFB71C1C)),
    CANCELLED("Cancelled", Color(0xFF757575))
}

private fun SlotData.toSlotItem(): SlotItem {
    val statusEnum = when (this.status) {
        "booked"    -> SlotStatus.BOOKED
        "completed" -> SlotStatus.COMPLETED
        "missed"    -> SlotStatus.MISSED
        "cancelled" -> SlotStatus.CANCELLED
        else        -> SlotStatus.AVAILABLE
    }
    return SlotItem(
        id           = this.id,
        date         = parseISODateToReadable(this.date),
        rawDate      = this.date,   // keep original ISO for expiry logic
        startTime    = this.startTime,
        endTime      = this.endTime,
        price        = this.priceInRupees,
        status       = statusEnum,
        studentName  = this.studentName,
        studentEmail = this.studentEmail,
        studentPhone = this.studentPhone
    )
}

fun parseISODateToReadable(isoDate: String): String {
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd"
    )
    val istZone = TimeZone.getTimeZone("Asia/Kolkata")
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.ENGLISH)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val parsed = sdf.parse(isoDate) ?: continue
            val out = SimpleDateFormat("dd MMM", Locale.ENGLISH)
            out.timeZone = istZone
            return out.format(parsed)
        } catch (_: Exception) {}
    }
    return isoDate
}

// ── Expiry check ──────────────────────────────────────────────────────────────
// Returns true if the slot's end time has already passed in IST
private fun isSlotExpired(rawDate: String, endTime: String): Boolean {
    return try {
        // rawDate may be ISO datetime or plain "yyyy-MM-dd"
        val datePart = rawDate.substringBefore('T').trim()  // "yyyy-MM-dd"
        val ist      = TimeZone.getTimeZone("Asia/Kolkata")
        val sdf      = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).apply { timeZone = ist }
        val slotEnd  = sdf.parse("$datePart $endTime") ?: return false
        slotEnd.before(Date())  // true if end time is in the past
    } catch (_: Exception) {
        false
    }
}


private const val SLOT_DURATION_MINUTES = 60

private fun buildHourlyLabelsIST(): List<String> {
    val ist = TimeZone.getTimeZone("Asia/Kolkata")
    val fmt = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply { timeZone = ist }
    val cal = Calendar.getInstance(ist).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    // Only full hours (00 & 30 min marks kept for flexibility, but slots are 60 min each)
    // Still show every 30-min label in picker so teacher can set start at :30 if needed,
    // but slot count is always calculated in 60-min blocks.
    return List(48) {
        fmt.format(cal.time).also { cal.add(Calendar.MINUTE, 30) }
    }
}

private val timeOptions by lazy { buildHourlyLabelsIST() }

private fun minutesFrom12hLabel(label: String): Int? {
    return try {
        val sdf = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val d = sdf.parse(label) ?: return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        cal.time = d
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    } catch (_: Exception) {
        null
    }
}

private fun currentISTMinutesFromMidnight(): Int {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

private val time24Regex = Regex("^\\d{1,2}:\\d{2}$")

private fun to24Hr(t: String): String {
    val trimmed = t.trim()
    if (time24Regex.matches(trimmed)) {
        val parts = trimmed.split(':')
        val h = parts[0].toIntOrNull() ?: return trimmed
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return trimmed
        return "%02d:%02d".format(h.coerceIn(0, 23), m.coerceIn(0, 59))
    }
    return try {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val sdf12 = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply { timeZone = ist }
        val sdf24 = SimpleDateFormat("HH:mm", Locale.ENGLISH).apply { timeZone = ist }
        sdf24.format(sdf12.parse(trimmed)!!)
    } catch (_: Exception) {
        trimmed
    }
}

private fun slotApiErrorMessage(resp: Response<GenericResponse>): String? {
    resp.body()?.message?.takeIf { it.isNotBlank() }?.let { return it }
    val raw = try {
        resp.errorBody()?.string()
    } catch (_: Exception) {
        null
    } ?: return null
    return Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
}

fun generateDateOptions(): List<Pair<String, String>> {
    val options = mutableListOf<Pair<String, String>>()
    val istZone = TimeZone.getTimeZone("Asia/Kolkata")
    val isoFmt  = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).also { it.timeZone = istZone }
    val dayFmt  = SimpleDateFormat("d MMM",      Locale.ENGLISH).also { it.timeZone = istZone }

    for (i in 0..4) {
        val cal = Calendar.getInstance(istZone)
        cal.add(Calendar.DAY_OF_YEAR, i)
        val iso    = isoFmt.format(cal.time)
        val dayStr = dayFmt.format(cal.time)
        val label  = when (i) {
            0    -> "Today, $dayStr"
            1    -> "Tomorrow, $dayStr"
            else -> dayStr
        }
        options.add(Pair(label, iso))
    }
    return options
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
fun HomeTabPreview() {
    HomeTab()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab() {

    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var selectedFilter by remember { mutableStateOf("All") }
    var showAddDialog  by remember { mutableStateOf(false) }
    var slots          by remember { mutableStateOf<List<SlotItem>>(emptyList()) }
    var isLoadingSlots by remember { mutableStateOf(false) }
    var isRefreshing   by remember { mutableStateOf(false) }
    var apiError       by remember { mutableStateOf("") }
    var isTeacherLive  by remember { mutableStateOf(false) }

    var slotToDelete      by remember { mutableStateOf<SlotItem?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deletingSlotId    by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    val filterOptions = listOf("All", "Available", "Booked")
    val displayedSlots = when (selectedFilter) {
        "Available" -> slots.filter { it.status == SlotStatus.AVAILABLE }
        "Booked"    -> slots.filter { it.status == SlotStatus.BOOKED }
        "Completed" -> slots.filter { it.status == SlotStatus.COMPLETED }
        else        -> slots
    }

    val totalSlots     = slots.size
    val availableCount = slots.count { it.status == SlotStatus.AVAILABLE }
    val bookedCount    = slots.count { it.status == SlotStatus.BOOKED }

    suspend fun syncLiveFromProfile() {
        try {
            val tok = TokenManager.getBearerToken(context)
            if (tok.isBlank() || tok == "Bearer ") return
            val resp = RetrofitClient.api.getTeacherProfile(tok)
            if (resp.isSuccessful) isTeacherLive = resp.body()?.data?.isLive == true
        } catch (_: Exception) {}
    }

    suspend fun loadMySlotsInternal() {
        apiError = ""
        try {
            val token = TokenManager.getBearerToken(context)
            if (token.isBlank() || token == "Bearer ") {
                apiError = "Login session expired. Please login again."
                return
            }
            val resp = RetrofitClient.api.getTeacherMySlots(token)
            if (resp.isSuccessful && resp.body()?.success == true) {
                slots = resp.body()!!.data.map { it.toSlotItem() }
            } else {
                val code = resp.code()
                apiError = when (code) {
                    401  -> "Session expired. Please login again."
                    403  -> "Profile not verified yet. Complete verification to create slots."
                    else -> resp.body()?.message ?: "Failed to load slots (Error $code)"
                }
            }
        } catch (e: Exception) {
            apiError = "Network error: ${e.message}"
        }
    }

    fun loadMySlots() {
        scope.launch {
            isLoadingSlots = true
            loadMySlotsInternal()
            isLoadingSlots = false
        }
    }

    fun onRefresh() {
        scope.launch {
            isRefreshing = true
            loadMySlotsInternal()
            isRefreshing = false
        }
    }

    fun deleteSlot(slot: SlotItem) {
        scope.launch {
            deletingSlotId = slot.id
            try {
                val token = TokenManager.getBearerToken(context)
                if (token.isBlank() || token == "Bearer ") {
                    snackbarHostState.showSnackbar("Session expired.")
                    return@launch
                }
                val resp = RetrofitClient.api.deleteSlot(token, slot.id)
                if (resp.isSuccessful && resp.body()?.success == true) {
                    snackbarHostState.showSnackbar("Slot deleted successfully")
                    loadMySlots()
                } else {
                    val msg = when (resp.code()) {
                        400  -> "Cannot delete a booked slot."
                        403  -> "Not authorized to delete this slot."
                        404  -> "Slot not found."
                        else -> resp.body()?.message ?: "Delete failed (${resp.code()})"
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Network error: ${e.message}")
            } finally {
                deletingSlotId = ""
            }
        }
    }

    // ── Auto-delete expired available slots (instant UI remove + background API) ─
    suspend fun autoDeleteExpiredSlots() {
        try {
            val token = TokenManager.getBearerToken(context)
            if (token.isBlank() || token == "Bearer ") return

            val expired = slots.filter { slot ->
                slot.status == SlotStatus.AVAILABLE &&
                        isSlotExpired(slot.rawDate, slot.endTime)
            }
            if (expired.isEmpty()) return

            // ✅ Step 1: Turant UI se hata do — user ko instantly dikhega
            val expiredIds = expired.map { it.id }.toSet()
            slots = slots.filter { it.id !in expiredIds }

            // ✅ Step 2: Background mein API delete karo (silently)
            expired.forEach { slot ->
                try {
                    RetrofitClient.api.deleteSlot(token, slot.id)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }



    LaunchedEffect(Unit) {
        loadMySlots()
        syncLiveFromProfile()
        autoDeleteExpiredSlots()   // check on first open too
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            loadMySlotsInternal()
            autoDeleteExpiredSlots()   // silent cleanup every 30 sec
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { onRefresh() },
            modifier     = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F7FA))
            ) {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {

                    // ── Header ────────────────────────────────────────────────
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EduBlue)
                                .padding(horizontal = 20.dp, vertical = 20.dp)
                        ) {
                            Column {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier              = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text(
                                            "My Slots",
                                            color      = Color.White,
                                            fontSize   = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Manage your availability & sessions",
                                            color    = Color.White.copy(alpha = 0.8f),
                                            fontSize = 13.sp
                                        )
                                    }
                                    if (isTeacherLive) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(Color.Red)
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Row(
                                                verticalAlignment     = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                                                Text(
                                                    "LIVE",
                                                    fontSize   = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color      = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StatCard("Total",     totalSlots.toString(),     Color.White,       Modifier.weight(1f))
                                    StatCard("Available", availableCount.toString(), Color(0xFFE8F5E9), Modifier.weight(1f))
                                    StatCard("Booked",    bookedCount.toString(),    Color(0xFFE3F2FD), Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // ── Filter chips ──────────────────────────────────────────
                    item {
                        LazyRow(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filterOptions) { filter ->
                                val selected = selectedFilter == filter
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (selected) EduBlue else Color(0xFFEEF2F8))
                                        .clickable { selectedFilter = filter }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text       = filter,
                                        color      = if (selected) Color.White else EduGray,
                                        fontSize   = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = EduBorder)
                    }

                    // ── Content ───────────────────────────────────────────────
                    when {
                        isLoadingSlots -> item {
                            Box(
                                modifier         = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = EduBlue)
                                    Spacer(Modifier.height(10.dp))
                                    Text("Loading slots...", color = EduGray, fontSize = 13.sp)
                                }
                            }
                        }

                        apiError.isNotEmpty() -> item {
                            Box(
                                modifier         = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFE53935), modifier = Modifier.size(40.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text(apiError, color = Color(0xFFE53935), fontSize = 13.sp, textAlign = TextAlign.Center)
                                    Spacer(Modifier.height(14.dp))
                                    Button(
                                        onClick = { loadMySlots() },
                                        colors  = ButtonDefaults.buttonColors(containerColor = EduBlue)
                                    ) { Text("Retry", color = Color.White) }
                                }
                            }
                        }

                        displayedSlots.isEmpty() -> item {
                            Box(
                                modifier         = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.EventBusy, null, tint = EduGray, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text("No slots found", color = EduGray, fontSize = 14.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Tap \"Set Availability\" to create slots",
                                        color    = EduGray.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        else -> items(displayedSlots) { slot ->
                            SlotCard(
                                slot           = slot,
                                isDeletingThis = deletingSlotId == slot.id,
                                onDeleteClick  = {
                                    slotToDelete      = slot
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    }
                }

                // ── FAB ───────────────────────────────────────────────────────
                ExtendedFloatingActionButton(
                    onClick        = { showAddDialog = true },
                    containerColor = EduBlue,
                    contentColor   = Color.White,
                    modifier       = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp),
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Set Availability", fontWeight = FontWeight.SemiBold) }
                )
            }
        }
    }

    // ── Add Availability Dialog ───────────────────────────────────────────────
    if (showAddDialog) {
        AddAvailabilityDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { isoDate, start, end ->
                scope.launch {
                    try {
                        val token = TokenManager.getBearerToken(context)
                        if (token.isBlank() || token == "Bearer ") {
                            showAddDialog = false
                            snackbarHostState.showSnackbar("Session expired. Please login again.")
                            return@launch
                        }
                        val dateTrim = isoDate.trim()
                        if (dateTrim.isEmpty()) {
                            showAddDialog = false
                            snackbarHostState.showSnackbar("Please select a date.")
                            return@launch
                        }
                        val start24 = to24Hr(start)
                        val end24   = to24Hr(end)
                        if (!time24Regex.matches(start24.trim()) || !time24Regex.matches(end24.trim())) {
                            showAddDialog = false
                            snackbarHostState.showSnackbar("Invalid time format — pick start/end again.")
                            return@launch
                        }

                        // ── Validate: end - start must be >= 60 min (at least 1 full slot) ──
                        val startMins = minutesFrom12hLabel(start)
                        val endMins   = minutesFrom12hLabel(end)
                        if (startMins == null || endMins == null || endMins - startMins < SLOT_DURATION_MINUTES) {
                            showAddDialog = false
                            snackbarHostState.showSnackbar("Time range must be at least 1 hour to create a slot.")
                            return@launch
                        }

                        val json = JSONObject().apply {
                            put("date", dateTrim)
                            put("startTime", start24.trim())
                            put("endTime", end24.trim())
                            put("durationMinutes", SLOT_DURATION_MINUTES)  // always 60
                        }
                        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                        val resp = RetrofitClient.api.generateSlots(token, body)
                        if (resp.isSuccessful && resp.body()?.success == true) {
                            // ── Success toast: tell exactly how many 1-hour slots were made ──
                            val slotCount = (endMins!! - startMins!!) / SLOT_DURATION_MINUTES
                            showAddDialog = false
                            snackbarHostState.showSnackbar(
                                "$slotCount slot${if (slotCount > 1) "s" else ""} generated (1 hour each)!"
                            )
                            loadMySlots()
                            syncLiveFromProfile()
                        } else {
                            val code      = resp.code()
                            val serverMsg = slotApiErrorMessage(resp)
                            val errorMsg  = when (code) {
                                400  -> serverMsg ?: "Slots already exist or time has passed for this range."
                                403  -> serverMsg ?: "Profile not verified. Complete verification first."
                                409  -> serverMsg ?: "Slots already exist for this date/time."
                                else -> serverMsg ?: "Failed to generate slots (Error $code)"
                            }
                            showAddDialog = false
                            snackbarHostState.showSnackbar(errorMsg)
                        }
                    } catch (e: Exception) {
                        showAddDialog = false
                        snackbarHostState.showSnackbar("Network error: ${e.message}")
                    }
                }
            }
        )
    }

    // ── Delete Confirm Dialog ─────────────────────────────────────────────────
    if (showDeleteConfirm && slotToDelete != null) {
        val slot = slotToDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(16.dp),
            title = {
                Text("Delete Slot?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = EduTextDark)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to delete this slot?", fontSize = 13.sp, color = EduGray)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF5F5F5))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${slot.startTime} – ${slot.endTime}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EduTextDark)
                            Text(slot.date, fontSize = 12.sp, color = EduGray)
                            Text("₹${slot.price} / slot", fontSize = 12.sp, color = EduGray)
                        }
                    }
                    if (slot.status == SlotStatus.BOOKED) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFCE4EC))
                                .padding(10.dp)
                        ) {
                            Text(
                                "⚠ This slot is booked. Ask the student to cancel first.",
                                fontSize = 12.sp,
                                color    = Color(0xFFB71C1C)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick  = { showDeleteConfirm = false; deleteSlot(slot) },
                    enabled  = slot.status != SlotStatus.BOOKED,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = Color(0xFFE53935),
                        disabledContainerColor = Color(0xFFBDBDBD)
                    ),
                    shape    = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete Slot", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick  = { showDeleteConfirm = false; slotToDelete = null },
                    shape    = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("Cancel", color = EduGray) }
            }
        )
    }
}

// ── Slot Card ─────────────────────────────────────────────────────────────────

@Composable
fun SlotCard(
    slot:           SlotItem,
    isDeletingThis: Boolean = false,
    onDeleteClick:  (() -> Unit)? = null
) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(EduBlueLight)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(slot.startTime, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EduBlue)
                    Text("to", fontSize = 10.sp, color = EduGray)
                    Text(slot.endTime, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EduBlue)
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(slot.date, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = EduTextDark)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CurrencyRupee, null, tint = EduGray, modifier = Modifier.size(13.dp))
                        Text("${slot.price} / slot", fontSize = 12.sp, color = EduGray)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(slot.status.color.copy(alpha = 0.12f))
                        .border(1.dp, slot.status.color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(slot.status.label, color = slot.status.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                if (onDeleteClick != null && slot.status == SlotStatus.AVAILABLE) {
                    Spacer(Modifier.width(8.dp))
                    if (isDeletingThis) {
                        CircularProgressIndicator(
                            color       = Color(0xFFE53935),
                            strokeWidth = 2.dp,
                            modifier    = Modifier.size(20.dp)
                        )
                    } else {
                        IconButton(
                            onClick  = { onDeleteClick() },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFCE4EC))
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete slot",
                                tint               = Color(0xFFE53935),
                                modifier           = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (slot.status == SlotStatus.BOOKED && !slot.studentName.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE3F2FD))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Booked by", fontSize = 10.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Person, null, tint = Color(0xFF1565C0), modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(slot.studentName, fontSize = 12.sp, color = EduTextDark, fontWeight = FontWeight.Medium)
                        }
                        if (!slot.studentEmail.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Email, null, tint = EduGray, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(slot.studentEmail, fontSize = 11.sp, color = EduGray)
                            }
                        }
                        if (!slot.studentPhone.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Phone, null, tint = EduGray, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(slot.studentPhone, fontSize = 11.sp, color = EduGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Stat Card ─────────────────────────────────────────────────────────────────

@Composable
fun StatCard(label: String, value: String, bgColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor.copy(alpha = 0.2f))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

// ── Add Availability Dialog ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAvailabilityDialog(
    onDismiss: () -> Unit,
    // duration parameter hata diya — always 60 min fixed
    onConfirm: (isoDate: String, start: String, end: String) -> Unit
) {
    val dateOptions = remember { generateDateOptions() }

    var selectedPair    by remember { mutableStateOf<Pair<String, String>?>(null) }
    var startTime       by remember { mutableStateOf("") }
    var endTime         by remember { mutableStateOf("") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker   by remember { mutableStateOf(false) }
    var errorMsg        by remember { mutableStateOf("") }

    val isToday = selectedPair?.first?.startsWith("Today") == true

    val availableStartTimes = remember(isToday, selectedPair?.second) {
        if (!isToday) {
            timeOptions
        } else {
            val now = currentISTMinutesFromMidnight()
            timeOptions.filter { (minutesFrom12hLabel(it) ?: -1) > now }
        }
    }

    // ── Slot count preview (always in 60-min blocks) ──────────────────────────
    val slotCountPreview = remember(startTime, endTime) {
        val sm = minutesFrom12hLabel(startTime)
        val em = minutesFrom12hLabel(endTime)
        if (sm != null && em != null && em > sm)
            (em - sm) / SLOT_DURATION_MINUTES
        else 0
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            LazyColumn(
                modifier            = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        Text("Set Availability", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = EduTextDark)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, null, tint = EduGray)
                        }
                    }
                }

                item {
                    SectionLabel("Select Date")
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(dateOptions) { (label, iso) ->
                            val selected = selectedPair?.second == iso
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) EduBlue else Color(0xFFEEF2F8))
                                    .clickable {
                                        selectedPair = Pair(label, iso)
                                        startTime = ""
                                        endTime   = ""
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text       = label,
                                    color      = if (selected) Color.White else EduGray,
                                    fontSize   = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                item {
                    SectionLabel("Time Range")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        TimePickerBox("Start Time", startTime, { showStartPicker = true }, Modifier.weight(1f))
                        TimePickerBox("End Time",   endTime,   { showEndPicker   = true }, Modifier.weight(1f))
                    }
                }

                // ── Slot Duration — fixed 60 min chip, no selection needed ───
                item {
                    SectionLabel("Slot Duration")
                    Spacer(Modifier.height(8.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(EduBlue)
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "60 min",
                            color      = Color.White,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // ── Preview info box ─────────────────────────────────────────
                if (startTime.isNotEmpty() && endTime.isNotEmpty() && slotCountPreview > 0) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(EduBlueLight)
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Info, null, tint = EduBlue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    // ✅ Shows correct slot count & confirms price is per 1-hour slot
                                    "$slotCountPreview slot${if (slotCountPreview > 1) "s" else ""} will be generated " +
                                            "(1 hour each) • Price = your hourly rate per slot",
                                    fontSize   = 12.sp,
                                    color      = EduBlue,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // ── Warning: range too small for even 1 slot ─────────────────
                if (startTime.isNotEmpty() && endTime.isNotEmpty() && slotCountPreview == 0) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFCE4EC))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, null, tint = Color(0xFFB71C1C), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Selected range is less than 1 hour — no slots can be created.",
                                    fontSize = 12.sp,
                                    color    = Color(0xFFB71C1C),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (errorMsg.isNotEmpty()) {
                    item { Text(errorMsg, color = Color.Red, fontSize = 12.sp) }
                }

                item {
                    Button(
                        onClick = {
                            val sm = minutesFrom12hLabel(startTime)
                            val em = minutesFrom12hLabel(endTime)
                            when {
                                selectedPair == null        -> errorMsg = "Please select a date"
                                startTime.isEmpty()         -> errorMsg = "Please select start time"
                                endTime.isEmpty()           -> errorMsg = "Please select end time"
                                sm == null || em == null    -> errorMsg = "Invalid time selection"
                                em <= sm                    -> errorMsg = "End time must be after start time"
                                //  Must have at least 60 min gap for 1 slot
                                em - sm < SLOT_DURATION_MINUTES -> errorMsg = "Need at least 1 hour gap to create a slot"
                                else -> {
                                    errorMsg = ""
                                    onConfirm(selectedPair!!.second, startTime, endTime)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = EduBlue)
                    ) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Generate Slots", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        TimePickerSheet(
            title     = "Select Start Time",
            times     = availableStartTimes,
            selected  = startTime,
            onSelect  = { startTime = it; showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }

    if (showEndPicker) {
        val startM = minutesFrom12hLabel(startTime) ?: -1
        TimePickerSheet(
            title     = "Select End Time",
            //  End time must be at least startTime + 60 min
            times     = if (startTime.isEmpty()) availableStartTimes
            else availableStartTimes.filter {
                (minutesFrom12hLabel(it) ?: -1) >= startM + SLOT_DURATION_MINUTES
            },
            selected  = endTime,
            onSelect  = { endTime = it; showEndPicker = false },
            onDismiss = { showEndPicker = false }
        )
    }
}

// ── Time Picker Box ───────────────────────────────────────────────────────────

@Composable
fun TimePickerBox(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, if (value.isNotEmpty()) EduBlue else EduBorder, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEEF2F8))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column {
            Text(label, fontSize = 10.sp, color = EduGray)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = EduBlue, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text       = if (value.isEmpty()) "-- : --" else value,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (value.isEmpty()) EduGray else EduTextDark
                )
            }
        }
    }
}

// ── Time Picker Bottom Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheet(
    title:     String,
    times:     List<String>,
    selected:  String,
    onSelect:  (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Text(
            text       = title,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Bold,
            color      = EduTextDark,
            modifier   = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        HorizontalDivider(color = EduBorder)
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            items(times) { time ->
                val isSelected = time == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(time) }
                        .background(if (isSelected) EduBlueLight else Color.White)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Schedule,
                            null,
                            tint     = if (isSelected) EduBlue else EduGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text       = time,
                            fontSize   = 14.sp,
                            color      = if (isSelected) EduBlue else EduTextDark,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Filled.Check, null, tint = EduBlue, modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = EduBorder, modifier = Modifier.padding(start = 48.dp))
            }
        }
    }
}

// ── Section Label ─────────────────────────────────────────────────────────────

@Composable
fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EduGray)
}