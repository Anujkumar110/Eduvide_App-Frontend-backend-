package com.example.tutorapp

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.tutorapp.network.RazorpayManager
import com.example.tutorapp.network.SlotData
import com.example.tutorapp.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotBookingScreen(
    navController: NavHostController,
    teacherId: String,
    teacherName: String,
    viewModel: SessionViewModel,
    razorpayManager: RazorpayManager
) {
    var selectedDate  by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) }
    var slots         by remember { mutableStateOf<List<SlotData>>(emptyList()) }
    var isLoading     by remember { mutableStateOf(true) }
    var bookingSlotId by remember { mutableStateOf<String?>(null) }

    //  SnackbarHostState
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    // helper —
    fun showSnackbar(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun loadSlots() {
        isLoading = true
        viewModel.getSlotsForTeacher(
            teacherId = teacherId,
            date      = selectedDate,
            onSuccess = { fetchedSlots ->
                slots     = fetchedSlots
                isLoading = false
            },
            onError   = { err ->
                isLoading = false
                showSnackbar("Failed to load slots: $err")
            }
        )
    }

    // Date change hone par slots reload karo
    LaunchedEffect(selectedDate) { loadSlots() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Book Slot - $teacherName",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EduBlue)
            )
        },
        containerColor = EduBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // ── Date Selector ──────────────────────────────────────────────
            Row(
                modifier            = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val today = LocalDate.now()
                listOf(0, 1, 2, 3, 4).forEach { daysOffset ->
                    val date      = today.plusDays(daysOffset.toLong())
                    val dateStr   = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    val isSelected = dateStr == selectedDate

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) EduBlue else EduBorder)
                            .clickable { selectedDate = dateStr }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text      = date.format(DateTimeFormatter.ofPattern("EEE")),
                                fontSize  = 11.sp,
                                color     = if (isSelected) Color.White else EduGray
                            )
                            Text(
                                text       = date.dayOfMonth.toString(),
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color      = if (isSelected) Color.White else EduTextDark
                            )
                        }
                    }
                }
            }

            // ── Slots List ─────────────────────────────────────────────────
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EduBlue)
                    }
                }

                slots.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint     = EduGray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No slots available for this date", fontSize = 14.sp, color = EduGray)
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding      = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(slots) { slot ->
                            SlotItem(
                                slot          = slot,
                                isBooking     = bookingSlotId == slot.id,
                                onBook        = {
                                    bookingSlotId = slot.id

                                    //  Fixed: callbacks ab lambdas ke andar hain
                                    // anonymous object ke andar state mutate nahi ho raha
                                    razorpayManager.startPayment(
                                        amount    = 200.0,
                                        teacherId = teacherId,
                                        bookingId = slot.id,
                                        callback  = object : RazorpayManager.PaymentCallback {

                                            override fun onSuccess(
                                                paymentId: String,
                                                orderId: String,
                                                signature: String
                                            ) {
                                                //  scope.launch se main thread pe aao
                                                scope.launch {
                                                    viewModel.bookSlot(
                                                        slotId    = slot.id,
                                                        paymentId = paymentId,
                                                        onSuccess = {
                                                            bookingSlotId = null
                                                            showSnackbar("Slot booked successfully! ")
                                                            loadSlots() // list refresh karo
                                                        },
                                                        onError   = { error ->
                                                            bookingSlotId = null
                                                            showSnackbar("Booking failed: $error")
                                                        }
                                                    )
                                                }
                                            }

                                            override fun onFailure(errorCode: String, errorMessage: String) {
                                                //  scope.launch se main thread pe aao
                                                scope.launch {
                                                    bookingSlotId = null
                                                    showSnackbar("Payment failed: $errorMessage")
                                                }
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Slot Item ──────────────────────────────────────────────────────────────────
@Composable
private fun SlotItem(
    slot      : SlotData,
    isBooking : Boolean,
    onBook    : () -> Unit
) {
    val (statusText, statusColor, bgColor) = when (slot.status) {
        "available" -> Triple("Available",            Color(0xFF4CAF50), Color(0xFF4CAF50).copy(alpha = 0.1f))
        "booked"    -> Triple("Booked",               Color(0xFFE53935), Color(0xFFE53935).copy(alpha = 0.1f))
        "ongoing"   -> Triple("Ongoing - Live Now!",  Color(0xFF2196F3), Color(0xFF2196F3).copy(alpha = 0.15f))
        "completed" -> Triple("Completed",            EduGray,           EduGray.copy(alpha = 0.1f))
        "missed"    -> Triple("Missed",               Color(0xFFFF9800), Color(0xFFFF9800).copy(alpha = 0.1f))
        "scheduled" -> Triple("Scheduled",            EduBlue,           EduBlue.copy(alpha = 0.1f))
        else        -> Triple(
            slot.status.replaceFirstChar { it.uppercase() },
            EduGray,
            EduGray.copy(alpha = 0.1f)
        )
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = when (slot.status) {
                            "ongoing"   -> Color(0xFF2196F3)
                            "available" -> Color(0xFF4CAF50)
                            "booked"    -> Color(0xFFE53935)
                            else        -> EduGray
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text       = "${slot.startTime} - ${slot.endTime}",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = EduTextDark
                    )
                }

                Spacer(Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (slot.status == "ongoing") {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text       = statusText,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = statusColor
                        )
                    }
                }
            }

            when (slot.status) {
                "available" -> {
                    Button(
                        onClick  = onBook,
                        enabled  = !isBooking,
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = EduBlue),
                        modifier = Modifier.height(40.dp)
                    ) {
                        if (isBooking) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = Color.White
                            )
                        } else {
                            Text("Book", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "ongoing" -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2196F3))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Join Live", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}