package com.example.tutorapp

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tutorapp.network.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ────────────── Session + Status ─────────────────
enum class ClassStatus { SCHEDULED, ONGOING, COMPLETED, MISSED }

data class SharedSession(
    val roomId:       String,
    val studentName:  String,
    val subject:      String,
    val topic:        String,
    val date:         String,
    val time:         String,
    val duration:     String,
    val mode:         String,
    val price:        Double = 0.0,
    val currency:     String = "INR",
    val status:       ClassStatus,
    val rating:       Int = 0,
    val avatarColor:  Color   = Color(0xFF6366F1),
    val teacherPhoto: String? = null,
    val studentPhoto: String? = null,
    val studentId:    String? = null
)

data class ChatMessage(
    val sender:    String,
    val text:      String,
    val isMe:      Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class VideoCallSession(
    val roomId:       String,
    val participants: MutableList<String> = mutableListOf(),
    val isActive:     Boolean = false
)

sealed class ClassUiState {
    object Idle    : ClassUiState()
    object Loading : ClassUiState()
    data class Success(val message: String) : ClassUiState()
    data class Error(val message: String)   : ClassUiState()
}

data class StudentEarlyLeaveEvent(
    val studentId:       String,
    val studentName:     String,
    val reason:          String,
    val minutesAttended: Int,
    val leftAt:          String,
    val classId:         String,
    val sessionId:       String
)

// ────────────── SessionViewModel ────────────────
@RequiresApi(Build.VERSION_CODES.O)
class SessionViewModel(
    private val apiService:    ApiService,
    private val tokenProvider: () -> String,
    private val wsManager:     WebSocketManager? = null
) : ViewModel() {

    init {
        wsManager?.let { ws ->
            viewModelScope.launch {
                ws.events.collect { event ->
                    when (event) {
                        is WebSocketEvent.SessionStarted -> {
                            Log.d("SessionVM", "WS: Session started - ${event.classId}")
                            updateStatus(event.classId, ClassStatus.ONGOING)
                            loadTeacherSessions(forceRefresh = true)
                            loadStudentSessions(forceRefresh = true)
                        }
                        is WebSocketEvent.SessionEnded -> {
                            Log.d("SessionVM", "WS: Session ended - ${event.classId}")
                            updateStatus(event.classId, ClassStatus.COMPLETED)
                            loadTeacherSessions(forceRefresh = true)
                            loadStudentSessions(forceRefresh = true)
                        }
                        is WebSocketEvent.StudentJoinedSession -> {
                            Log.d("SessionVM", "WS: Student joined - ${event.studentId}")
                            loadTeacherSessions(forceRefresh = true)
                        }
                        is WebSocketEvent.RoomJoined -> {
                            Log.d("SessionVM", "WS: Joined room - ${event.classId}")
                        }
                        is WebSocketEvent.PeerJoinedRoom -> {
                            Log.d("SessionVM", "WS: Peer joined - ${event.peerId}")
                        }
                        is WebSocketEvent.PeerLeftRoom -> {
                            Log.d("SessionVM", "WS: Peer left - ${event.peerId}")
                        }
                        is WebSocketEvent.StudentLeftEarly -> {
                            Log.d("SessionVM", "WS: Student left early — ${event.studentName}: ${event.reason}")
                            _studentEarlyLeaveEvent.value = StudentEarlyLeaveEvent(
                                studentId       = event.studentId,
                                studentName     = event.studentName,
                                reason          = event.reason,
                                minutesAttended = event.minutesAttended,
                                leftAt          = event.leftAt,
                                classId         = event.classId,
                                sessionId       = event.sessionId
                            )
                            if (event.sessionCompleted) {
                                updateStatus(event.classId, ClassStatus.COMPLETED)
                                Log.d("SessionVM", "Session auto-completed after student early leave: ${event.classId}")
                            }
                        }
                        is WebSocketEvent.WebRtcOffer,
                        is WebSocketEvent.WebRtcAnswer,
                        is WebSocketEvent.WebRtcIceCandidate,
                        is WebSocketEvent.NewViewer -> {
                            // CallViewModel handles these
                        }

                        else -> {

                        }
                    }
                }
            }
        }
    }

    // ─── Core State ───────────────────────────────────────────────────────────

    private val _sessions      = MutableStateFlow<List<SharedSession>>(emptyList())
    val sessions: StateFlow<List<SharedSession>> = _sessions.asStateFlow()

    private val _uiState       = MutableStateFlow<ClassUiState>(ClassUiState.Idle)
    val uiState: StateFlow<ClassUiState> = _uiState.asStateFlow()

    private val _isLoading     = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _chatMessages  = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val chatMessages: StateFlow<Map<String, List<ChatMessage>>> = _chatMessages.asStateFlow()

    private val _videoSessions = MutableStateFlow<Map<String, VideoCallSession>>(emptyMap())
    val videoSessions: StateFlow<Map<String, VideoCallSession>> = _videoSessions.asStateFlow()

    private val _teacherProfile = MutableStateFlow<TeacherProfileData?>(null)
    val teacherProfile: StateFlow<TeacherProfileData?> = _teacherProfile.asStateFlow()

    private val _studentEarlyLeaveEvent = MutableStateFlow<StudentEarlyLeaveEvent?>(null)
    val studentEarlyLeaveEvent: StateFlow<StudentEarlyLeaveEvent?> = _studentEarlyLeaveEvent.asStateFlow()

    // ─── Review State ─────────────────────────────────────────────────────────

    private val _pendingReviewSession = MutableStateFlow<SharedSession?>(null)
    val pendingReviewSession: StateFlow<SharedSession?> = _pendingReviewSession.asStateFlow()

    private val _reviewedClassIds = MutableStateFlow<Set<String>>(emptySet())
    val reviewedClassIds: StateFlow<Set<String>> = _reviewedClassIds.asStateFlow()

    private var reviewCheckDoneForIds = mutableSetOf<String>()

    private val _pendingStudents = MutableStateFlow<List<SlotData>>(emptyList())
    val pendingStudents: StateFlow<List<SlotData>> = _pendingStudents.asStateFlow()

    // ─── Student Photo Cache ──────────────────────────────────────────────────
    private val studentPhotoCache = mutableMapOf<String, String?>()

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private val avatarColors = listOf(
        Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFF06B6D4),
        Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444)
    )

    private var lastTeacherFetchTime = 0L
    private var lastStudentFetchTime = 0L
    private val CACHE_DURATION       = 30_000L

    // ─── FCM Token ────────────────────────────────────────────────────────────

    fun saveFcmToken() {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                if (token.isBlank()) return@launch
                FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
                    viewModelScope.launch {
                        try {
                            val response = apiService.saveFcmToken(token, FcmTokenRequest(token = fcmToken))
                            if (response.isSuccessful) {
                                Log.d("SessionVM", "FCM token saved")
                            } else {
                                Log.e("SessionVM", "FCM save failed: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            Log.e("SessionVM", "FCM API error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SessionVM", "saveFcmToken error: ${e.message}")
            }
        }
    }

    // ─── Student Photo Fetcher ────────────────────────────────────────────────

    private suspend fun fetchStudentPhotoById(studentId: String): String? {
        if (studentPhotoCache.containsKey(studentId)) {
            return studentPhotoCache[studentId]
        }

        return try {
            val token = tokenProvider()
            if (token.isBlank()) return null

            val response = apiService.getStudentProfileById(token, studentId)
            val photo = if (response.isSuccessful) {
                response.body()?.data?.profilePhoto
            } else {
                Log.w("PHOTO_DEBUG", "getStudentProfileById failed: ${response.code()} for studentId=$studentId")
                null
            }

            studentPhotoCache[studentId] = photo
            Log.d("PHOTO_DEBUG", "Fetched photo for studentId=$studentId → $photo")
            photo
        } catch (e: Exception) {
            Log.e("PHOTO_DEBUG", "fetchStudentPhotoById error for $studentId: ${e.message}")
            studentPhotoCache[studentId] = null
            null
        }
    }

    // ─── PUBLIC: Card se direct photo fetch ke liye ───────────────────────────
    fun getStudentPhoto(studentId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val photo = fetchStudentPhotoById(studentId)
            onResult(photo)
        }
    }

    // ─── Teacher Sessions ─────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    fun loadTeacherSessions(forceRefresh: Boolean = false) {
        val now          = System.currentTimeMillis()
        val isCacheValid = !forceRefresh &&
                _sessions.value.isNotEmpty() &&
                (now - lastTeacherFetchTime) < CACHE_DURATION
        if (isCacheValid) return

        viewModelScope.launch {
            _isLoading.value = true
            _uiState.value   = ClassUiState.Loading
            try {
                val token = tokenProvider()
                if (token.isBlank() || token == "Bearer ") {
                    _uiState.value = ClassUiState.Error("Session expired — please login again")
                    return@launch
                }

                val allClasses = mutableListOf<ClassData>()
                for (status in listOf("upcoming", "ongoing", "completed", "missed")) {
                    try {
                        val resp = apiService.getTeacherClasses(token, mine = true, status = status)
                        if (resp.isSuccessful && resp.body()?.success == true) {
                            allClasses.addAll(resp.body()!!.data)
                            Log.d("SessionVM", "Teacher $status: ${resp.body()!!.data.size}")
                        } else {
                            Log.w("SessionVM", "Teacher $status: ${resp.code()} ${resp.errorBody()?.string()}")
                        }
                    } catch (e: Exception) {
                        Log.w("SessionVM", "Teacher $status fetch failed: ${e.message}")
                    }
                }

                allClasses.forEach { cls ->
                    Log.e("PHOTO_DEBUG", "classId=${cls.id} studentIdRaw type=${cls.studentIdRaw?.javaClass?.simpleName} val=${cls.studentIdRaw}")
                }

                val rawSessions = allClasses.distinctBy { it.id }.mapIndexed { i, cls ->
                    cls.toSharedSession(i, avatarColors, isStudent = false)
                }

                val enrichedSessions = rawSessions.map { session ->
                    if (!session.studentPhoto.isNullOrBlank()) return@map session

                    val studentId = session.studentId
                    if (studentId.isNullOrBlank()) {
                        Log.w("PHOTO_DEBUG", "roomId=${session.roomId} — studentId blank, skip")
                        return@map session
                    }

                    val photo = fetchStudentPhotoById(studentId)
                    session.copy(studentPhoto = photo)
                }

                _sessions.value      = enrichedSessions
                lastTeacherFetchTime = System.currentTimeMillis()
                _uiState.value       = ClassUiState.Success("Loaded ${enrichedSessions.size} sessions")
                Log.d("SessionVM", "Teacher breakdown: ${enrichedSessions.groupBy { it.status }.mapValues { it.value.size }}")

            } catch (e: Exception) {
                _uiState.value = ClassUiState.Error(e.message ?: "Network error")
                Log.e("SessionVM", "loadTeacherSessions error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── loadPendingStudents ──────────────────────────────────────────────────

    fun loadPendingStudents() {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                if (token.isBlank()) return@launch

                val response = apiService.getTeacherMySlots(token)
                if (response.isSuccessful && response.body()?.success == true) {
                    val allSlots = response.body()!!.data

                    Log.e("SLOT_DEBUG", "=== Total slots from API: ${allSlots.size} ===")
                    allSlots.forEachIndexed { i, slot ->
                        Log.e("SLOT_DEBUG", "[$i] id=${slot.id} status=${slot.status} " +
                                "classId=${slot.classId} bookedBy=${slot.bookedBy} " +
                                "name=${slot.studentName} email=${slot.studentEmail}")
                    }

                    // ─── Slot se student photo cache banao ────────────────────
                    allSlots.forEach { slot ->
                        val id    = slot.bookedBy?.takeIf { it.isNotBlank() } ?: return@forEach
                        val photo = slot.studentPhoto?.takeIf { it.isNotBlank() } ?: return@forEach
                        studentPhotoCache[id] = photo
                        Log.d("PHOTO_DEBUG", "Slot cache: bookedBy=$id photo=$photo")
                    }

                    val pending = allSlots.filter { slot ->
                        slot.status == "booked" &&
                                slot.classId.isNullOrBlank() &&
                                (!slot.bookedBy.isNullOrBlank() || slot.id.isNotBlank())
                    }

                    Log.e("SLOT_DEBUG", "=== After filter — pending students: ${pending.size} ===")
                    pending.forEach { slot ->
                        val usableId = slot.bookedBy?.takeIf { it.isNotBlank() } ?: slot.id
                        Log.e("SLOT_DEBUG", "  PENDING: name=${slot.studentName} usableId=$usableId")
                    }

                    _pendingStudents.value = pending
                } else {
                    Log.e("SLOT_DEBUG", "getTeacherMySlots failed: ${response.code()} ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("SessionVM", "loadPendingStudents: ${e.message}")
            }
        }
    }

    // ─── Student Sessions ─────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    fun loadStudentSessions(forceRefresh: Boolean = false) {
        val now          = System.currentTimeMillis()
        val isCacheValid = !forceRefresh &&
                _sessions.value.isNotEmpty() &&
                (now - lastStudentFetchTime) < CACHE_DURATION
        if (isCacheValid) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = tokenProvider()
                if (token.isBlank() || token == "Bearer ") {
                    Log.e("SessionVM", "Token blank for student")
                    return@launch
                }

                val allClasses = mutableListOf<ClassData>()
                for (status in listOf("upcoming", "ongoing", "completed", "missed", "cancelled")) {
                    try {
                        val resp = apiService.getStudentClasses(token, enrolled = true, status = status)
                        if (resp.isSuccessful && resp.body()?.success == true) {
                            allClasses.addAll(resp.body()!!.data)
                            Log.d("SessionVM", "Student enrolled $status: ${resp.body()!!.data.size}")
                        }
                    } catch (e: Exception) {
                        Log.w("SessionVM", "Student $status fetch failed: ${e.message}")
                    }
                }

                val classSessions = allClasses.distinctBy { it.id }.mapIndexed { i, cls ->
                    cls.toSharedSession(i, avatarColors, isStudent = true)
                }

                _sessions.value      = classSessions.distinctBy { it.roomId }
                lastStudentFetchTime = System.currentTimeMillis()

                Log.d("SessionVM", "Student total: ${_sessions.value.size} (classes=${classSessions.size})")
                Log.d("SessionVM", "Breakdown: ${_sessions.value.groupBy { it.status }.mapValues { it.value.size }}")

                checkAndTriggerPendingReview()

            } catch (e: Exception) {
                Log.e("SessionVM", "loadStudentSessions error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun startStudentPolling() {
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                loadStudentSessions(forceRefresh = true)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun startTeacherPolling() {
        viewModelScope.launch {
            while (true) {
                delay(10_000)
                loadTeacherSessions(forceRefresh = true)
            }
        }
    }

    // ─── Backend Status Update ────────────────────────────────────────────────

    fun updateStatusOnBackend(roomId: String, status: String) {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                if (token.isBlank()) return@launch
                val response = apiService.updateClass(token, roomId, UpdateClassRequest(status = status))
                if (response.isSuccessful) {
                    val newStatus = when (status) {
                        "ongoing"             -> ClassStatus.ONGOING
                        "completed"           -> ClassStatus.COMPLETED
                        "cancelled", "missed" -> ClassStatus.MISSED
                        else                  -> ClassStatus.SCHEDULED
                    }
                    updateStatus(roomId, newStatus)
                    Log.d("SessionVM", "Status updated: $roomId → $status")
                } else {
                    Log.e("SessionVM", "Status update failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("SessionVM", "updateStatusOnBackend error: ${e.message}", e)
            }
        }
    }

    // ─── Create Class ─────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    fun createClass(
        title:       String,
        description: String,
        price:       Double,
        date:        String,
        time:        String,
        durationMin: Int,
        currency:    String = "INR",
        maxStudents: Int? = null,
        studentId:   String,
        onSuccess:   (SharedSession) -> Unit = {},
        onError:     (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.value = ClassUiState.Loading
            try {
                val normalizedDate = normalizeDate(date)
                val normalizedTime = normalizeTime(time)

                Log.e("CREATE_CLASS", "=== createClass called ===")
                Log.e("CREATE_CLASS", "studentId = '$studentId'")
                Log.e("CREATE_CLASS", "date = '$normalizedDate'  time = '$normalizedTime'")
                Log.e("CREATE_CLASS", "title = '$title'  price = $price")

                if (studentId.isBlank()) {
                    val msg = "studentId blank hai — student select karo"
                    _uiState.value = ClassUiState.Error(msg)
                    onError(msg)
                    return@launch
                }

                val json = JSONObject().apply {
                    put("title",       title)
                    put("description", description)
                    put("price",       price)
                    put("currency",    currency)
                    put("date",        normalizedDate)
                    put("time",        normalizedTime)
                    put("durationMin", durationMin)
                    put("status",      "upcoming")
                    put("studentId",   studentId)
                    if (maxStudents != null) put("maxStudents", maxStudents)
                }

                val rawBody = json.toString()
                    .toRequestBody("application/json; charset=UTF-8".toMediaType())

                Log.e("CREATE_CLASS", "raw JSON body: $json")

                val token = tokenProvider()
                Log.e("CREATE_CLASS", "token prefix: ${token.take(20)}")

                val response = apiService.createClassRaw(token, rawBody)
                Log.e("CREATE_CLASS", "response code: ${response.code()}")
                Log.e("CREATE_CLASS", "response body: ${response.body()}")
                Log.e("CREATE_CLASS", "error body: ${response.errorBody()?.string()}")

                if (response.isSuccessful && response.body()?.success == true) {
                    val classData = response.body()?.data
                    if (classData != null) {
                        var session = classData.toSharedSession(_sessions.value.size, avatarColors, isStudent = false)

                        if (session.studentPhoto.isNullOrBlank() && !session.studentId.isNullOrBlank()) {
                            val photo = fetchStudentPhotoById(session.studentId!!)
                            session = session.copy(studentPhoto = photo)
                        }

                        _sessions.value = _sessions.value + session

                        _pendingStudents.value = _pendingStudents.value.filter { slot ->
                            slot.bookedBy != studentId
                        }

                        _uiState.value       = ClassUiState.Success("Session created!")
                        lastTeacherFetchTime = 0
                        loadTeacherSessions(forceRefresh = true)
                        loadPendingStudents()
                        onSuccess(session)
                    } else {
                        val msg = "Session created but no data returned"
                        _uiState.value = ClassUiState.Error(msg)
                        onError(msg)
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val msg       = response.body()?.message ?: "Failed (${response.code()}) — $errorBody"
                    _uiState.value = ClassUiState.Error(msg)
                    onError(msg)
                    Log.e("CREATE_CLASS", "createClass failed: $msg")
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Network error"
                _uiState.value = ClassUiState.Error(msg)
                onError(msg)
                Log.e("CREATE_CLASS", "createClass exception: $msg", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun normalizeDate(raw: String): String {
        val trimmed = raw.trim()
        val shortMonthPattern = Regex("""^\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4}$""")
        if (shortMonthPattern.matches(trimmed)) {
            return try {
                val parsed = LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
                parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
            } catch (e: Exception) {
                try {
                    val parsed = LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
                    parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
                } catch (e2: Exception) { trimmed }
            }
        }
        val isoPattern = Regex("""^\d{4}-\d{2}-\d{2}$""")
        if (isoPattern.matches(trimmed)) {
            return try {
                val parsed = LocalDate.parse(trimmed)
                parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
            } catch (e: Exception) { trimmed }
        }
        val slashPattern = Regex("""^\d{1,2}/\d{1,2}/\d{4}$""")
        if (slashPattern.matches(trimmed)) {
            return try {
                val parsed = LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("d/M/yyyy"))
                parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
            } catch (e: Exception) { trimmed }
        }
        Log.w("SessionVM", "normalizeDate: unrecognized format '$raw'")
        return trimmed
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun normalizeTime(raw: String): String {
        val trimmed = raw.trim().replace(Regex(""":\s+"""), ":")
        val ampmPattern = Regex("""^\d{1,2}:\d{2}\s*(AM|PM|am|pm)$""", RegexOption.IGNORE_CASE)
        if (ampmPattern.matches(trimmed)) {
            return try {
                val parsed = LocalTime.parse(trimmed.uppercase(), DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
                parsed.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
            } catch (e: Exception) {
                try {
                    val parsed = LocalTime.parse(trimmed.uppercase(), DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
                    parsed.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
                } catch (e2: Exception) { trimmed }
            }
        }
        val h24Pattern = Regex("""^\d{1,2}:\d{2}$""")
        if (h24Pattern.matches(trimmed)) {
            return try {
                val parsed = LocalTime.parse(trimmed, DateTimeFormatter.ofPattern("H:mm"))
                parsed.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
            } catch (e: Exception) { trimmed }
        }
        Log.w("SessionVM", "normalizeTime: unrecognized format '$raw'")
        return trimmed
    }

    // ─── Delete Class ─────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    fun deleteClass(roomId: String) {
        viewModelScope.launch {
            try {
                val token    = tokenProvider()
                val response = apiService.deleteClass(token, roomId)
                if (response.isSuccessful) {
                    _sessions.value      = _sessions.value.filter { it.roomId != roomId }
                    lastTeacherFetchTime = 0
                    loadTeacherSessions(forceRefresh = true)
                } else {
                    Log.e("SessionVM", "Delete failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("SessionVM", "deleteClass error: ${e.message}", e)
            }
        }
    }

    // ─── Local State Helpers ──────────────────────────────────────────────────

    fun updateStatus(roomId: String, newStatus: ClassStatus) {
        _sessions.value = _sessions.value.map { s ->
            if (s.roomId == roomId) s.copy(status = newStatus) else s
        }
    }

    fun addSession(session: SharedSession) {
        _sessions.value = _sessions.value + session
    }

    fun autoCompleteExpiredSessions() {
        Log.d("SessionVM", "autoCompleteExpiredSessions: backend cron handles this")
    }

    // ─── Review Functions ─────────────────────────────────────────────────────

    fun triggerReviewIfNeeded(session: SharedSession) {
        if (_reviewedClassIds.value.contains(session.roomId)) return
        if (reviewCheckDoneForIds.contains(session.roomId)) return
        if (session.status != ClassStatus.COMPLETED && session.status != ClassStatus.MISSED) return
        _pendingReviewSession.value = session
    }

    fun checkAndTriggerPendingReview() {
        if (_pendingReviewSession.value != null) return

        val target = _sessions.value.firstOrNull { session ->
            (session.status == ClassStatus.COMPLETED || session.status == ClassStatus.MISSED) &&
                    !_reviewedClassIds.value.contains(session.roomId) &&
                    !reviewCheckDoneForIds.contains(session.roomId)
        }

        if (target != null) {
            Log.d("SessionVM", "Review trigger: ${target.roomId} (${target.status})")
            _pendingReviewSession.value = target
        }
    }

    fun isReviewSkipped(roomId: String): Boolean {
        return reviewCheckDoneForIds.contains(roomId)
    }

    fun skipReview(roomId: String) {
        reviewCheckDoneForIds.add(roomId)
        _pendingReviewSession.value = null
    }

    // ─── Submit Review ────────────────────────────────────────────────────────

    fun submitReview(
        classId:   String,
        rating:    Int,
        comment:   String,
        onSuccess: () -> Unit = {},
        onError:   (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                if (token.isBlank() || token == "Bearer ") {
                    onError("Please login again")
                    return@launch
                }

                Log.e("REVIEW", "Submitting: classId='$classId' rating=$rating")

                if (classId.isBlank()) {
                    onError("Class ID missing — please try again")
                    return@launch
                }

                val json = JSONObject().apply {
                    put("classId", classId)
                    put("rating",  rating)
                    put("comment", comment.trim())
                }
                val body = json.toString()
                    .toRequestBody("application/json; charset=UTF-8".toMediaType())

                Log.e("REVIEW", "Raw body: $json")

                val response = apiService.submitReviewRaw(token, body)

                if (response.isSuccessful && response.body()?.success == true) {
                    reviewCheckDoneForIds.add(classId)
                    _reviewedClassIds.value     = _reviewedClassIds.value + classId
                    _pendingReviewSession.value = null
                    Log.d("SessionVM", "Review submitted: $classId → $rating stars")
                    onSuccess()
                } else {
                    if (response.code() == 409) {
                        reviewCheckDoneForIds.add(classId)
                        _reviewedClassIds.value     = _reviewedClassIds.value + classId
                        _pendingReviewSession.value = null
                        onSuccess()
                        return@launch
                    }
                    val errorBody = response.errorBody()?.string()
                    val msg = try {
                        JSONObject(errorBody ?: "").optString("message", "Review failed (${response.code()})")
                    } catch (e: Exception) {
                        errorBody ?: "Review failed (${response.code()})"
                    }
                    Log.e("SessionVM", "submitReview failed: $msg | classId='$classId'")
                    onError(msg)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Network error"
                Log.e("SessionVM", "submitReview error: $msg", e)
                onError(msg)
            }
        }
    }

    // ─── Chat Helpers ─────────────────────────────────────────────────────────

    fun sendChatMessage(roomId: String, message: String, sender: String, isMe: Boolean = true) {
        val current  = _chatMessages.value.toMutableMap()
        val roomList = current[roomId]?.toMutableList() ?: mutableListOf()
        roomList.add(ChatMessage(sender, message, isMe))
        current[roomId] = roomList
        _chatMessages.value = current
    }

    // ─── Video Call Helpers ───────────────────────────────────────────────────

    fun startVideoCall(roomId: String, userId: String) {
        val session = _videoSessions.value[roomId] ?: VideoCallSession(roomId, mutableListOf(), true)
        if (!session.participants.contains(userId)) session.participants.add(userId)
        _videoSessions.value = _videoSessions.value + (roomId to session)
    }

    fun endVideoCall(roomId: String, userId: String) {
        val session = _videoSessions.value[roomId] ?: return
        session.participants.remove(userId)
        _videoSessions.value = if (session.participants.isEmpty())
            _videoSessions.value - roomId
        else
            _videoSessions.value + (roomId to session)
    }

    // ─── WebSocket Session Management ─────────────────────────────────────────

    fun startSessionWithWebSocket(classId: String, className: String) {
        viewModelScope.launch {
            try {
                updateStatus(classId, ClassStatus.ONGOING)
                updateStatusOnBackend(classId, "ongoing")
                wsManager?.sendSessionStarted(classId, className)
                wsManager?.sendJoinRoom(classId, "teacher")
            } catch (e: Exception) {
                Log.e("SessionVM", "Error starting session: ${e.message}")
            }
        }
    }

    fun endSessionWithWebSocket(classId: String, sessionId: String) {
        viewModelScope.launch {
            try {
                updateStatus(classId, ClassStatus.COMPLETED)
                updateStatusOnBackend(classId, "completed")
                wsManager?.sendSessionEnded(classId, sessionId)
                wsManager?.sendLeaveRoom(classId)
                Log.d("SessionVM", "endSession: classId=$classId marked completed on backend")
            } catch (e: Exception) {
                Log.e("SessionVM", "Error ending session: ${e.message}")
            }
        }
    }

    fun joinSessionWithAttend(classId: String, sessionId: String) {
        viewModelScope.launch {
            try {
                wsManager?.sendStudentJoined(classId, classId)
                wsManager?.sendJoinRoom(classId, "student")
                Log.d("SessionVM", "joinSession: classId=$classId")
            } catch (e: Exception) {
                Log.e("SessionVM", "Error joining session: ${e.message}")
            }
        }
    }

    fun leaveSessionRoom(classId: String) {
        wsManager?.sendLeaveRoom(classId)
    }

    fun sendEarlyLeaveReason(
        classId:         String,
        sessionId:       String,
        reason:          String,
        studentName:     String,
        minutesAttended: Int
    ) {
        Log.e("EARLY_LEAVE", "=== CALLED classId=$classId reason=$reason ===")
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                if (token.isBlank()) return@launch

                val sessionResp     = apiService.getClassSessions(token, classId, status = "ongoing")
                val actualSessionId = sessionResp.body()?.data?.firstOrNull()?.id

                if (actualSessionId.isNullOrBlank()) {
                    Log.e("SessionVM", "No ongoing session found for classId=$classId")
                    updateStatus(classId, ClassStatus.COMPLETED)
                    return@launch
                }

                val response = apiService.leaveSession(
                    token     = token,
                    sessionId = actualSessionId,
                    body      = LeaveSessionRequest(reason = reason)
                )

                if (response.isSuccessful) {
                    Log.d("SessionVM", "Early leave API success: sessionId=$actualSessionId reason=$reason")
                    updateStatus(classId, ClassStatus.COMPLETED)
                } else {
                    Log.e("SessionVM", "leaveSession failed: ${response.code()} ${response.errorBody()?.string()}")
                    updateStatus(classId, ClassStatus.COMPLETED)
                }

            } catch (e: Exception) {
                Log.e("SessionVM", "sendEarlyLeaveReason error: ${e.message}", e)
                updateStatus(classId, ClassStatus.COMPLETED)
            }
        }
    }

    fun dismissEarlyLeaveEvent() {
        _studentEarlyLeaveEvent.value = null
    }

    // ─── Enrollment ───────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    fun enrollInClass(classId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                if (token.isBlank()) { onError("Please login first"); return@launch }
                val response = apiService.enrollInClass(token, EnrollRequest(classId = classId))
                if (response.isSuccessful) {
                    lastStudentFetchTime = 0
                    loadStudentSessions(forceRefresh = true)
                    onSuccess()
                } else {
                    onError(response.errorBody()?.string() ?: "Enrollment failed (${response.code()})")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Network error")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun unenrollFromClass(enrollmentId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                if (token.isBlank()) { onError("Please login first"); return@launch }
                val response = apiService.unenrollFromClass(token, enrollmentId)
                if (response.isSuccessful) { loadStudentSessions(); onSuccess() }
                else onError(response.errorBody()?.string() ?: "Unenrollment failed (${response.code()})")
            } catch (e: Exception) {
                onError(e.message ?: "Network error")
            }
        }
    }

    // ─── Teacher Profile ──────────────────────────────────────────────────────

    private fun uriToFile(context: android.content.Context, uri: android.net.Uri): File? {
        return try {
            val inputStream  = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile     = File.createTempFile("upload_${System.currentTimeMillis()}", ".jpg", context.cacheDir)
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close(); outputStream.close()
            tempFile
        } catch (e: Exception) {
            Log.e("SessionVM", "uriToFile failed: ${e.message}", e)
            null
        }
    }

    fun loadTeacherProfile(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                if (token.isBlank()) { onError("Please login first"); return@launch }
                val response = apiService.getTeacherProfile(token)
                if (response.isSuccessful && response.body()?.success == true) {
                    _teacherProfile.value = response.body()!!.data
                    onSuccess()
                } else {
                    val msg = when (response.code()) {
                        401  -> "Session expired. Please login again."
                        403  -> "Access denied."
                        404  -> "Profile not found."
                        500  -> "Server error."
                        else -> response.errorBody()?.string() ?: "Failed (${response.code()})"
                    }
                    onError(msg)
                }
            } catch (e: Exception) {
                onError("Network error: ${e.message}")
            }
        }
    }

    fun updateTeacherProfileWithMedia(
        context:       android.content.Context,
        fullName:      String,
        email:         String,
        bio:           String,
        gender:        String,
        age:           Int,
        degree:        String,
        chargePerHour: Double,
        experience:    Int,
        subjects:      List<String>,
        boards:        List<String>,
        languages:     List<String>,
        photoUri:      android.net.Uri?,
        introVideoUri: android.net.Uri?,
        onSuccess:     () -> Unit = {},
        onError:       (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                if (token.isBlank()) { onError("Please login first"); return@launch }

                val toBody: (String) -> RequestBody = { it.toRequestBody("text/plain".toMediaType()) }

                val photoPart = photoUri?.let { uri ->
                    val file = uriToFile(context, uri) ?: run { onError("Failed to process photo"); return@launch }
                    MultipartBody.Part.createFormData("profilePhoto", file.name, file.asRequestBody("image/jpeg".toMediaType()))
                }
                val videoPart = introVideoUri?.let { uri ->
                    val file = uriToFile(context, uri) ?: run { onError("Failed to process video"); return@launch }
                    MultipartBody.Part.createFormData("introVideo", file.name, file.asRequestBody("video/mp4".toMediaType()))
                }

                fun List<String>.toPaddedParts(max: Int): List<RequestBody?> {
                    val parts = filter { it.isNotEmpty() }.map { it.toRequestBody("text/plain".toMediaType()) as RequestBody? }.toMutableList()
                    while (parts.size < max) parts.add(null)
                    return parts
                }

                val paddedSubjects  = subjects.toPaddedParts(7)
                val paddedBoards    = boards.toPaddedParts(3)
                val paddedLanguages = languages.toPaddedParts(3)

                val response = apiService.updateTeacherProfileWithMedia(
                    token             = token,
                    profilePhoto      = photoPart,
                    introVideo        = videoPart,
                    fullName          = toBody(fullName),
                    email             = toBody(email),
                    gender            = toBody(gender),
                    age               = toBody(age.toString()),
                    degree            = toBody(degree),
                    bio               = toBody(bio),
                    experience        = toBody(experience.toString()),
                    pricingHourlyRate = toBody(chargePerHour.toString()),
                    pricingCurrency   = toBody("INR"),
                    subject0 = paddedSubjects[0], subject1 = paddedSubjects[1],
                    subject2 = paddedSubjects[2], subject3 = paddedSubjects[3],
                    subject4 = paddedSubjects[4], subject5 = paddedSubjects[5],
                    subject6 = paddedSubjects[6],
                    board0   = paddedBoards[0],   board1   = paddedBoards[1],
                    board2   = paddedBoards[2],
                    language0 = paddedLanguages[0], language1 = paddedLanguages[1],
                    language2 = paddedLanguages[2]
                )

                if (response.isSuccessful) { loadTeacherProfile(); onSuccess() }
                else {
                    val msg = when (response.code()) {
                        400  -> "Invalid data."
                        401  -> "Session expired."
                        413  -> "File too large."
                        500  -> "Server error."
                        else -> response.errorBody()?.string() ?: "Update failed (${response.code()})"
                    }
                    onError(msg)
                }
            } catch (e: Exception) {
                onError("Network error: ${e.message}")
            }
        }
    }

    // ─── Live Status & Slots ──────────────────────────────────────────────────

    fun toggleLiveStatus(isLive: Boolean, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                if (token.isBlank()) { onError("Please login first"); return@launch }
                val response = apiService.toggleLiveStatus(token, LiveStatusRequest(isLive))
                if (response.isSuccessful) {
                    _teacherProfile.value = _teacherProfile.value?.copy(isLive = isLive)
                    onSuccess()
                } else onError(response.errorBody()?.string() ?: "Failed (${response.code()})")
            } catch (e: Exception) { onError(e.message ?: "Network error") }
        }
    }

    fun getSlotsForTeacher(teacherId: String, date: String, onSuccess: (List<SlotData>) -> Unit, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                if (token.isBlank()) { onError("Please login first"); return@launch }
                val response = apiService.getTeacherSlots(token, teacherId, date)
                if (response.isSuccessful && response.body()?.success == true) onSuccess(response.body()!!.data)
                else onError(response.errorBody()?.string() ?: "Failed to fetch slots")
            } catch (e: Exception) { onError(e.message ?: "Network error") }
        }
    }

    fun bookSlot(slotId: String, paymentId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val token = tokenProvider()
                if (token.isBlank()) { onError("Please login first"); return@launch }
                val response = apiService.bookSlot(token, slotId, BookSlotRequest(paymentId = paymentId))
                if (response.isSuccessful) onSuccess()
                else onError(response.errorBody()?.string() ?: "Booking failed")
            } catch (e: Exception) { onError(e.message ?: "Network error") }
        }
    }
}

// ────────────── Mapper ───────────────────────────

private data class DisplayTime(val date: String, val time: String, val duration: String)

@RequiresApi(Build.VERSION_CODES.O)
private fun parseScheduleDisplay(startIso: String?, endIso: String?): DisplayTime {
    return try {
        if (startIso == null) return DisplayTime("TBD", "TBD", "—")
        val istZone = ZoneId.of("Asia/Kolkata")

        fun parseZdt(raw: String): ZonedDateTime? {
            return try {
                ZonedDateTime.parse(raw).withZoneSameInstant(istZone)
            } catch (e1: Exception) {
                try {
                    val cleaned = raw.replace(Regex("""\s*\(.*?\)\s*$"""), "").trim()
                    val fmt = DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.ENGLISH)
                    ZonedDateTime.parse(cleaned, fmt).withZoneSameInstant(istZone)
                } catch (e2: Exception) {
                    Log.e("SessionVM", "parseScheduleDisplay: cannot parse '$raw'")
                    null
                }
            }
        }

        val start = parseZdt(startIso) ?: return DisplayTime("TBD", "TBD", "—")
        val date  = start.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
        val time  = start.format(DateTimeFormatter.ofPattern("h:mm a",     Locale.ENGLISH))

        val duration = if (endIso != null) {
            val end = parseZdt(endIso)
            if (end != null) {
                val minutes = java.time.Duration.between(start, end).toMinutes()
                "$minutes min"
            } else "—"
        } else "—"

        DisplayTime(date, time, duration)
    } catch (e: Exception) {
        Log.e("SessionVM", "parseScheduleDisplay error: ${e.message}")
        DisplayTime("TBD", "TBD", "—")
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun ClassData.toSharedSession(
    index:        Int,
    avatarColors: List<Color>,
    isStudent:    Boolean = false
): SharedSession {
    val slot = schedule.firstOrNull()
    val (dispDate, dispTime, dispDuration) = parseScheduleDisplay(slot?.startTime, slot?.endTime)

    val classStatus = when (status) {
        "upcoming"  -> ClassStatus.SCHEDULED
        "published" -> ClassStatus.SCHEDULED
        "ongoing"   -> ClassStatus.ONGOING
        "completed" -> ClassStatus.COMPLETED
        "cancelled", "missed" -> ClassStatus.MISSED
        else        -> ClassStatus.SCHEDULED
    }

    val displayName = if (isStudent)
        parsedTeacherRef?.fullName?.ifBlank { title } ?: title
    else
        parsedStudentRef?.fullName?.ifBlank { title } ?: title

    val rawStudentId = when (val raw = studentIdRaw) {
        is String    -> raw.takeIf { it.isNotBlank() }
        is Map<*, *> -> (raw["_id"] as? String)?.takeIf { it.isNotBlank() }
        else         -> null
    }

    return SharedSession(
        roomId       = id,
        studentName  = displayName,
        subject      = title,
        topic        = description.take(60).ifBlank { title },
        date         = dispDate,
        time         = dispTime,
        duration     = dispDuration,
        mode         = "Online",
        price        = price,
        currency     = currency,
        status       = classStatus,
        avatarColor  = avatarColors[index % avatarColors.size],
        teacherPhoto = parsedTeacherRef?.profilePhoto,
        studentPhoto = parsedStudentRef?.profilePhoto,
        studentId    = rawStudentId
    )
}