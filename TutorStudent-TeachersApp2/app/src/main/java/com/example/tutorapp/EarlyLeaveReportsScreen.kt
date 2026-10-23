package com.example.tutorapp.teacher

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.tutorapp.SessionViewModel
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

data class EarlyLeaveReportItem(
    val studentName:     String,
    val reason:          String,
    val minutesAttended: Int,
    val leftAt:          String,
    val classId:         String,
    val sessionId:       String
)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun EarlyLeaveReportsScreen(
    navController:    NavController,
    sessionViewModel: SessionViewModel
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val latestEvent by sessionViewModel.studentEarlyLeaveEvent.collectAsState()
    val reports      = remember { mutableStateListOf<EarlyLeaveReportItem>() }
    var isLoading   by remember { mutableStateOf(true) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }

    // Ticker jo har 30 seconds mein UI refresh kare (relative time update ke liye)
    var ticker by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)

            ticker++
        }
    }

    suspend fun fetchAll() {
        isLoading = true
        errorMsg  = null
        reports.clear()
        try {
            val token = TokenManager.getBearerToken(context)
            Log.e("EarlyLeave", "token blank=${token.isBlank()}, token=${token.take(30)}...")
            if (token.isBlank()) { errorMsg = "Please login again"; return }

            val allClassIds = mutableSetOf<String>()
            for (status in listOf("ongoing", "completed", "upcoming")) {
                try {
                    Log.e("EarlyLeave", "Fetching classes status=$status")
                    val resp = RetrofitClient.api.getTeacherClasses(token, mine = true, status = status)
                    Log.e("EarlyLeave", "status=$status code=${resp.code()} count=${resp.body()?.data?.size}")
                    if (resp.isSuccessful) {
                        resp.body()?.data?.forEach { allClassIds.add(it.id) }
                    }
                } catch (e: Exception) {
                    Log.e("EarlyLeave", "Exception status=$status: ${e.message}")
                }
            }

            Log.e("EarlyLeave", "Total classIds=${allClassIds.size} ids=$allClassIds")

            if (allClassIds.isEmpty()) {
                isLoading = false
                return
            }

            for (classId in allClassIds) {
                try {
                    Log.e("EarlyLeave", "Fetching early-leave for classId=$classId")
                    val rep = RetrofitClient.api.getEarlyLeaveReports(token, classId)
                    Log.e("EarlyLeave", "classId=$classId code=${rep.code()} count=${rep.body()?.data?.size}")
                    if (rep.isSuccessful) {
                        rep.body()?.data?.forEach { item ->
                            val alreadyExists = reports.any {
                                it.studentName == (item.studentName ?: "") &&
                                        it.leftAt      == (item.leftAt      ?: "")
                            }
                            if (!alreadyExists) {
                                reports.add(
                                    EarlyLeaveReportItem(
                                        studentName     = item.studentName     ?: "Unknown",
                                        reason          = item.reason          ?: "—",
                                        minutesAttended = item.minutesAttended ?: 0,
                                        leftAt          = item.leftAt          ?: "",
                                        classId         = item.classId         ?: classId,
                                        sessionId       = item.sessionId       ?: ""
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("EarlyLeave", "Exception classId=$classId: ${e.message}")
                }
            }

            Log.e("EarlyLeave", "Total reports found=${reports.size}")
            val sorted = reports.sortedByDescending { it.leftAt }
            reports.clear()
            reports.addAll(sorted)

        } catch (e: Exception) {
            Log.e("EarlyLeave", "fetchAll exception: ${e.message}")
            errorMsg = "Network error: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { fetchAll() }

    // Real-time WebSocket event
    LaunchedEffect(latestEvent) {
        latestEvent?.let { event ->
            val alreadyExists = reports.any {
                it.studentName == event.studentName && it.leftAt == event.leftAt
            }
            if (!alreadyExists) {
                reports.add(0, EarlyLeaveReportItem(
                    studentName     = event.studentName,
                    reason          = event.reason,
                    minutesAttended = event.minutesAttended,
                    leftAt          = event.leftAt,
                    classId         = event.classId,
                    sessionId       = event.sessionId
                ))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1565C0))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Early Leave Reports", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Students who left session early", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                if (reports.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFE53935))
                    ) {
                        Text("${reports.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = { scope.launch { fetchAll() } }) {
                    Icon(Icons.Filled.Refresh, null, tint = Color.White)
                }
            }
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1565C0))
            }
            errorMsg != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFE53935), modifier = Modifier.size(48.dp))
                    Text(errorMsg ?: "Error", color = Color(0xFF666666), fontSize = 14.sp, textAlign = TextAlign.Center)
                    Button(onClick = { scope.launch { fetchAll() } }) { Text("Retry") }
                }
            }
            reports.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF1565C0).copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF1565C0), modifier = Modifier.size(40.dp))
                    }
                    Text("All Good!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1A1A2E))
                    Text("No student has taken an early leave yet.", fontSize = 14.sp, color = Color(0xFF666666), textAlign = TextAlign.Center)
                }
            }
            else -> LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ticker pass karo taaki recomposition ho aur relative time update rahe
                items(reports) { EarlyLeaveCard(report = it, ticker = ticker) }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun EarlyLeaveCard(report: EarlyLeaveReportItem, ticker: Int) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935).copy(alpha = 0.12f))
                    ) {
                        Text(
                            report.studentName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            color = Color(0xFFE53935),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Column {
                        Text(report.studentName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1A1A2E))
                        Text("Left early", fontSize = 12.sp, color = Color(0xFFE53935))
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFEEEEEE))

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Info, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Column {
                    Text("Reason", fontSize = 11.sp, color = Color(0xFF999999), fontWeight = FontWeight.Medium)
                    Text(report.reason, fontSize = 14.sp, color = Color(0xFF333333))
                }
            }

            if (report.leftAt.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.AccessTime, null, tint = Color(0xFF999999), modifier = Modifier.size(14.dp))
                    // ticker use ho raha hai recomposition trigger karne ke liye
                    Text(
                        getRelativeTime(report.leftAt, ticker),
                        fontSize = 12.sp,
                        color = Color(0xFF999999)
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun getRelativeTime(iso: String, @Suppress("UNUSED_PARAMETER") ticker: Int): String {
    return try {
        val leftAtInstant = Instant.parse(iso)
        val nowInstant    = Instant.now()
        val diffSeconds   = nowInstant.epochSecond - leftAtInstant.epochSecond

        when {
            diffSeconds < 60       -> "Just now"
            diffSeconds < 3600     -> {
                val mins = (diffSeconds / 60).toInt()
                if (mins == 1) "1 min ago" else "$mins mins ago"
            }
            diffSeconds < 86400    -> {
                val hours = (diffSeconds / 3600).toInt()
                if (hours == 1) "1 hour ago" else "$hours hours ago"
            }
            diffSeconds < 86400 * 7 -> {
                val days = (diffSeconds / 86400).toInt()
                if (days == 1) "Yesterday" else "$days days ago"
            }
            else -> {
                // Bahut purana hai toh exact time dikhao
                val parts  = iso.split("T")
                if (parts.size < 2) return iso
                val time   = parts[1].substring(0, 5)
                val hour24 = time.split(":")[0].toInt()
                val min    = time.split(":")[1]
                val ampm   = if (hour24 < 12) "AM" else "PM"
                val hour12 = when { hour24 == 0 -> 12; hour24 > 12 -> hour24 - 12; else -> hour24 }
                "${parts[0]}  $hour12:$min $ampm"
            }
        }
    } catch (e: Exception) {
        iso
    }
}