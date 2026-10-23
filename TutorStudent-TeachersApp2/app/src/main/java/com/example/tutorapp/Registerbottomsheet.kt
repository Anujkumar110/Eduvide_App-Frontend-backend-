package com.example.tutorapp

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.tutorapp.network.*
import com.example.tutorapp.teacher.parseISODateToReadable
import com.example.tutorapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterBottomSheet(
    mentor: TeacherItem,
    sheetState: SheetState,
    navController: NavHostController,
    onDismiss: () -> Unit,
    preSelectedSlotId: String? = null,
    prefetchedSlots: List<SlotData> = emptyList()
) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var fullName by remember { mutableStateOf("") }
    var email    by remember { mutableStateOf("") }
    var phone    by remember { mutableStateOf("") }

    // ✅ KEY FIX: seedha prefetchedSlots se initialize karo — LaunchedEffect ka wait nahi
    var availableSlots by remember { mutableStateOf(prefetchedSlots) }
    var slotsLoading   by remember { mutableStateOf(prefetchedSlots.isEmpty()) }
    var selectedSlotId by remember { mutableStateOf<String?>(preSelectedSlotId) }

    var walletBalance    by remember { mutableStateOf<Double?>(null) }
    var isLoadingBalance by remember { mutableStateOf(true) }

    var isPayLoading by remember { mutableStateOf(false) }
    var payError     by remember { mutableStateOf<String?>(null) }
    var paySuccess   by remember { mutableStateOf(false) }

    var isVideoReady  by remember { mutableStateOf(false) }
    var hasVideoError by remember { mutableStateOf(false) }
    var isPlaying     by remember { mutableStateOf(false) }
    var videoViewRef  by remember { mutableStateOf<VideoView?>(null) }

    val rate   = mentor.pricing?.hourlyRate ?: 399
    val amount = rate.toDouble()

    val hasSufficientBalance = (walletBalance ?: 0.0) >= amount

    val videoUrl = RetrofitClient.fullUrl(mentor.introVideo)

    // ── Slots: prefetchedSlots hon toh seedha use karo, warna API se fetch ────
    LaunchedEffect(Unit) {
        // ✅ SlotBookingSheet se rawSlots pass hui hain — seedha set karo, fast!
        if (prefetchedSlots.isNotEmpty()) {
            availableSlots = prefetchedSlots
            slotsLoading   = false
            return@LaunchedEffect
        }

        // Koi prefetched slots nahi — API se khud fetch karo
        val slotTeacherId = listOf(mentor.userId, mentor.id)
            .firstOrNull { it.isNotBlank() } ?: return@LaunchedEffect

        slotsLoading = true
        try {
            val token  = TokenManager.getBearerToken(context)
                .takeIf { it.isNotBlank() && it != "Bearer " } ?: ""
            val ist    = TimeZone.getTimeZone("Asia/Kolkata")
            val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply { timeZone = ist }
            val base   = Calendar.getInstance(ist)
            val merged = mutableListOf<SlotData>()

            for (day in 0 until 5) {
                val c = base.clone() as Calendar
                c.add(Calendar.DAY_OF_YEAR, day)
                val dateStr = isoFmt.format(c.time)
                try {
                    val resp = RetrofitClient.api.getTeacherSlots(
                        token     = token,
                        teacherId = slotTeacherId,
                        date      = dateStr
                    )
                    if (resp.isSuccessful && resp.body()?.success == true) {
                        merged += resp.body()!!.data  // saare slots — available + booked
                    }
                } catch (_: Exception) {}
            }
            availableSlots = merged.distinctBy { it.id }
        } catch (_: Exception) {
        } finally {
            slotsLoading = false
        }
    }

    // ── Wallet balance ────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        val token = TokenManager.getToken(context)
        if (!token.isNullOrBlank()) {
            try {
                val resp = RetrofitClient.api.getWallet("Bearer $token")
                if (resp.isSuccessful) {
                    walletBalance = resp.body()?.data?.balance
                }
            } catch (_: Exception) {}
        }
        isLoadingBalance = false
    }

    // ── Auto close after success ──────────────────────────────────────────────
    LaunchedEffect(paySuccess) {
        if (paySuccess) {
            delay(1500)
            sheetState.hide()
            onDismiss()
        }
    }

    fun payAndBook() {
        val token = TokenManager.getToken(context)
        if (token.isNullOrBlank()) { payError = "Please login first"; return }
        if (selectedSlotId.isNullOrBlank()) { payError = "Please select a slot first"; return }
        if (fullName.isBlank()) { payError = "Please enter your full name"; return }

        val payTeacherId = mentor.userId.takeIf { it.isNotBlank() }
            ?: mentor.id.takeIf { it.isNotBlank() }
            ?: run { payError = "Teacher ID missing"; return }

        android.util.Log.e("PAY_DEBUG", "teacherId=$payTeacherId slotId=$selectedSlotId")

        isPayLoading = true
        payError     = null

        coroutineScope.launch {
            try {
                val auth = TokenManager.getBearerToken(context)

                val jsonBody = org.json.JSONObject().apply {
                    put("teacherId",   payTeacherId)
                    put("amount",      amount)
                    put("slotId",      selectedSlotId)
                    put("studentName", fullName.trim())
                    if (email.isNotBlank()) put("studentEmail", email.trim())
                    if (phone.isNotBlank()) put("studentPhone", phone.trim())
                }.toString()

                android.util.Log.e("PAY_DEBUG", "JSON body = $jsonBody")

                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                val payResp = RetrofitClient.api.payTeacherFromWallet(
                    token   = auth,
                    request = requestBody
                )

                if (payResp.isSuccessful && payResp.body()?.success == true) {
                    walletBalance = payResp.body()?.data?.balance
                    paySuccess    = true
                } else {
                    val raw = payResp.errorBody()?.string() ?: ""
                    payError = try {
                        org.json.JSONObject(raw).optString("message", "Payment failed")
                    } catch (_: Exception) { "Payment failed (${payResp.code()})" }
                }

            } catch (e: Exception) {
                payError = e.message ?: "Network error"
            } finally {
                isPayLoading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState       = sheetState,
        containerColor   = CardWhite,
        shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Teacher mini profile ───────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model              = mentor.profilePhoto,
                    contentDescription = mentor.fullName,
                    modifier           = Modifier.size(64.dp).clip(CircleShape),
                    contentScale       = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = mentor.fullName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        text       = mentor.bio.ifBlank { "Expert Tutor" },
                        fontSize   = 13.sp,
                        color      = PrimaryBlue,
                        fontWeight = FontWeight.Medium,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    val rating = mentor.rating.average.toInt().coerceIn(0, 5)
                    Row {
                        repeat(5) { i ->
                            Text(
                                text     = if (i < rating) "★" else "☆",
                                fontSize = 13.sp,
                                color    = if (i < rating) Color(0xFFFFC107) else Color(0xFFDDDDDD)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(text = "(${mentor.rating.count} reviews)", fontSize = 11.sp, color = TextGray)
                    }
                }
                Text(
                    text       = "₹ $rate/-\nhour",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFFE8572A),
                    lineHeight = 17.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Intro Video ───────────────────────────────────────────────────
            if (!videoUrl.isNullOrBlank()) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Intro Video", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A1A2E)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val mc = MediaController(ctx)
                                mc.setAnchorView(this)
                                setMediaController(mc)
                                setVideoPath(videoUrl)
                                setOnPreparedListener { mp -> isVideoReady = true; mp.isLooping = false }
                                setOnErrorListener { _, _, _ -> hasVideoError = true; true }
                                setOnCompletionListener { isPlaying = false }
                                videoViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update   = { vv -> videoViewRef = vv }
                    )
                    DisposableEffect(Unit) { onDispose { videoViewRef?.stopPlayback() } }
                    if (!isVideoReady && !hasVideoError) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                    }
                    if (hasVideoError) {
                        Text("Video load nahi hua", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    if (isVideoReady && !isPlaying) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue.copy(alpha = 0.9f))
                                    .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                    .clickable { videoViewRef?.start(); isPlaying = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Tap to play", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("▶", fontSize = 16.sp, color = Color.White.copy(alpha = 0.4f))
                        Spacer(Modifier.width(8.dp))
                        Text("No Intro Video", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Slot selector ──────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Select a Slot", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                if (slotsLoading) {
                    CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))

            if (!slotsLoading && availableSlots.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFF3E0))
                        .padding(12.dp)
                ) {
                    Text(
                        text     = "No slots available in the next few days.",
                        fontSize = 12.sp,
                        color    = Color(0xFFE65100)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableSlots.forEach { slot ->
                        val isSelected  = selectedSlotId == slot.id
                        val isAvailable = slot.status == "available"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.5.dp,
                                    when {
                                        isSelected   -> PrimaryBlue
                                        !isAvailable -> Color(0xFFEEEEEE)
                                        else         -> Color(0xFFDDDDDD)
                                    },
                                    RoundedCornerShape(10.dp)
                                )
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isSelected   -> Color(0xFFE8F0FE)
                                        !isAvailable -> Color(0xFFF5F5F5)
                                        else         -> Color.White
                                    }
                                )
                                .then(
                                    if (isAvailable) Modifier.clickable { selectedSlotId = slot.id }
                                    else Modifier
                                )
                                .padding(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = when {
                                        isSelected   -> PrimaryBlue
                                        !isAvailable -> Color(0xFFBDBDBD)
                                        else         -> TextGray
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text       = parseISODateToReadable(slot.date),
                                        fontSize   = 11.sp,
                                        color      = TextGray,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text       = "${slot.startTime}  –  ${slot.endTime}",
                                        fontSize   = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color      = when {
                                            isSelected   -> PrimaryBlue
                                            !isAvailable -> Color(0xFFBDBDBD)
                                            else         -> TextPrimary
                                        }
                                    )
                                    Text(
                                        text     = "₹${slot.priceInRupees}",
                                        fontSize = 11.sp,
                                        color    = TextGray
                                    )
                                }
                            }

                            when {
                                isSelected -> Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(PrimaryBlue)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text       = "Selected",
                                        fontSize   = 10.sp,
                                        color      = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                !isAvailable -> Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0xFFFCE4EC))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text       = "Booked",
                                        fontSize   = 10.sp,
                                        color      = Color(0xFFC62828),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Form fields ────────────────────────────────────────────────────
            OutlinedTextField(
                value         = fullName,
                onValueChange = { fullName = it },
                placeholder   = { Text("Full Name *", color = TextGray, fontSize = 13.sp) },
                leadingIcon   = { Icon(Icons.Default.Person, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                modifier      = Modifier.fillMaxWidth().height(52.dp),
                shape         = RoundedCornerShape(8.dp),
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = PrimaryBlue,
                    unfocusedBorderColor = Color(0xFFDDDDDD)
                )
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value           = email,
                onValueChange   = { email = it },
                placeholder     = { Text("Email", color = TextGray, fontSize = 13.sp) },
                leadingIcon     = { Icon(Icons.Default.Email, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                modifier        = Modifier.fillMaxWidth().height(52.dp),
                shape           = RoundedCornerShape(8.dp),
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = PrimaryBlue,
                    unfocusedBorderColor = Color(0xFFDDDDDD)
                )
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier          = Modifier.fillMaxWidth().height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier         = Modifier
                        .height(52.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(LightBg)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🇮🇳", fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(text = "+91 India", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.width(2.dp))
                OutlinedTextField(
                    value           = phone,
                    onValueChange   = { if (it.length <= 10) phone = it },
                    placeholder     = { Text("Phone Number", color = TextGray, fontSize = 12.sp) },
                    modifier        = Modifier.weight(1f).height(52.dp),
                    shape           = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = PrimaryBlue,
                        unfocusedBorderColor = Color(0xFFDDDDDD)
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Wallet balance ─────────────────────────────────────────────────
            if (isLoadingBalance) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(8.dp))
            } else {
                Surface(
                    shape    = RoundedCornerShape(8.dp),
                    color    = if (hasSufficientBalance) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                tint     = if (hasSufficientBalance) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text       = "Wallet: ₹${String.format("%.2f", walletBalance ?: 0.0)}",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color      = if (hasSufficientBalance) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                        if (!hasSufficientBalance) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch { sheetState.hide(); onDismiss() }
                                    navController.navigate("WalletScreen")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text       = "Add Money →",
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = Color(0xFF1565C0)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (payError != null) {
                Text(payError!!, color = Color.Red, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
            }

            if (paySuccess) {
                Text(
                    text       = "✓ Payment successful! Slot booked.",
                    color      = Color(0xFF2E7D32),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
            }

            Button(
                onClick  = { if (!isPayLoading && !paySuccess) payAndBook() },
                enabled  = !isPayLoading && !paySuccess && hasSufficientBalance
                        && !isLoadingBalance && !selectedSlotId.isNullOrBlank()
                        && fullName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(24.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = Color(0xFF22C55E),
                    disabledContainerColor = Color(0xFFBDBDBD)
                )
            ) {
                if (isPayLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            selectedSlotId.isNullOrBlank() -> "Select a slot first"
                            fullName.isBlank()             -> "Enter your name first"
                            !hasSufficientBalance          -> "Insufficient Balance"
                            else                           -> "Pay ₹$rate & Book Slot"
                        },
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                }
            }
        }
    }
}

// ── RegisterScreenWithSlot (deep link / notification se aane ke liye) ─────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreenWithSlot(
    navController: NavHostController,
    slotId:        String,
    teacherUserId: String
) {
    val context      = LocalContext.current
    var teacher      by remember { mutableStateOf<TeacherItem?>(null) }
    var isLoading    by remember { mutableStateOf(true) }
    var errorMsg     by remember { mutableStateOf("") }
    val sheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(teacherUserId) {
        isLoading = true
        errorMsg  = ""
        try {
            val token = TokenManager.getToken(context)
            val auth  = if (!token.isNullOrBlank()) "Bearer $token" else ""

            val resp = RetrofitClient.api.getTeacher(auth, teacherUserId)

            if (!resp.isSuccessful || resp.body()?.success != true) {
                val listResp = RetrofitClient.api.listTeachers(auth)
                if (listResp.isSuccessful) {
                    val found = listResp.body()?.data?.find {
                        it.userId == teacherUserId || it.id == teacherUserId
                    }
                    if (found != null) {
                        teacher      = found
                        sheetVisible = true
                        isLoading    = false
                        return@LaunchedEffect
                    }
                }
                errorMsg = "Could not load teacher info."
            } else {
                teacher      = resp.body()!!.data
                sheetVisible = true
            }
        } catch (e: Exception) {
            errorMsg = "Network error: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(sheetVisible) {
        if (sheetVisible) sheetState.show()
    }

    Box(
        modifier         = Modifier.fillMaxSize().background(Color(0xFFF5F7FA)),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> CircularProgressIndicator(color = PrimaryBlue)
            errorMsg.isNotEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(errorMsg, color = Color.Red, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { navController.popBackStack() }) { Text("Go Back") }
            }
        }
    }

    if (sheetVisible && teacher != null) {
        RegisterBottomSheet(
            mentor            = teacher!!,
            sheetState        = sheetState,
            navController     = navController,
            preSelectedSlotId = slotId,
            prefetchedSlots   = emptyList(),
            onDismiss         = { navController.popBackStack() }
        )
    }
}