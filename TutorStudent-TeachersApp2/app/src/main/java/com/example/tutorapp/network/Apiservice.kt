package com.example.tutorapp.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonQualifier
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import kotlin.annotation.AnnotationRetention
import kotlin.annotation.Retention

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class ReviewClassId

// ══════════════════════════════════════════════════════════════════════════════
// REQUEST MODELS
// ══════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    @Json(name = "fullName") val fullName: String,
    @Json(name = "email")    val email: String,
    @Json(name = "password") val password: String,
    @Json(name = "role")     val role: String = "student"
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "email")    val email: String,
    @Json(name = "password") val password: String,
    @Json(name = "role")     val role: String = "student"
)

@JsonClass(generateAdapter = true)
data class FirebaseAuthRequest(
    @Json(name = "token") val idToken: String,
    @Json(name = "role")  val role: String = "student"
)

@JsonClass(generateAdapter = true)
data class TeacherProfileRequest(
    @Json(name = "fullName")      val fullName: String,
    @Json(name = "email")         val email: String,
    @Json(name = "gender")        val gender: String,
    @Json(name = "age")           val age: Int,
    @Json(name = "chargePerHour") val chargePerHour: Int,
    @Json(name = "degree")        val degree: String,
    @Json(name = "experience")    val experience: Int,
    @Json(name = "subjects")      val subjects: List<String>,
    @Json(name = "boards")        val boards: List<String>,
    @Json(name = "languages")     val languages: List<String>,
    @Json(name = "bio")           val bio: String = "",
    @Json(name = "availability")  val availability: List<AvailabilityRequest> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AvailabilityRequest(
    @Json(name = "dayOfWeek") val dayOfWeek: Int,
    @Json(name = "startTime") val startTime: String,
    @Json(name = "endTime")   val endTime: String
)

@JsonClass(generateAdapter = true)
data class ScheduleSlotRequest(
    @Json(name = "startTime")      val startTime: String,
    @Json(name = "endTime")        val endTime: String,
    @Json(name = "isRecurring")    val isRecurring: Boolean = false,
    @Json(name = "recurrenceRule") val recurrenceRule: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateClassRequest(
    @Json(name = "title")       val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "price")       val price: Double,
    @Json(name = "currency")    val currency: String = "INR",
    @Json(name = "maxStudents") val maxStudents: Int? = null,
    @Json(name = "date")        val date: String,
    @Json(name = "time")        val time: String,
    @Json(name = "durationMin") val durationMin: Int,
    @Json(name = "status")      val status: String = "upcoming",
    @Json(name = "studentId")   val studentId: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateClassRequest(
    @Json(name = "title")       val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "price")       val price: Double? = null,
    @Json(name = "currency")    val currency: String? = null,
    @Json(name = "maxStudents") val maxStudents: Int? = null,
    @Json(name = "status")      val status: String? = null
)
@JsonClass(generateAdapter = true)
data class LeaveSessionRequest(
    @Json(name = "reason") val reason: String
)

// ══════════════════════════════════════════════════════════════════════════════
// EARLY LEAVE MODELS
// ══════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class EarlyLeaveRequest(
    @Json(name = "roomId")          val roomId:          String,
    @Json(name = "studentId")       val studentId:       String,
    @Json(name = "reason")          val reason:          String,
    @Json(name = "minutesAttended") val minutesAttended: Int
)

@JsonClass(generateAdapter = true)
data class EarlyLeaveResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String
)

@JsonClass(generateAdapter = true)
data class EarlyLeaveReportData(
    @Json(name = "studentId")       val studentId:       String? = null,
    @Json(name = "studentName")     val studentName:     String? = null,
    @Json(name = "reason")          val reason:          String? = null,
    @Json(name = "minutesAttended") val minutesAttended: Int?    = null,
    @Json(name = "leftAt")          val leftAt:          String? = null,
    @Json(name = "classId")         val classId:         String? = null,
    @Json(name = "sessionId")       val sessionId:       String? = null
)

@JsonClass(generateAdapter = true)
data class EarlyLeaveListResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: List<EarlyLeaveReportData> = emptyList()
)

// ══════════════════════════════════════════════════════════════════════════════
// REVIEW MODELS
// ══════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class ReviewRequest(
    @Json(name = "classId") val classId: String,
    @Json(name = "rating")  val rating: Int,
    @Json(name = "comment") val comment: String = ""
)

@JsonClass(generateAdapter = true)
data class ReviewData(
    @Json(name = "_id")       val id: String? = null,
    @Json(name = "classId")   val classId: String? = null,
    @Json(name = "studentId") val studentId: String? = null,
    @Json(name = "teacherId") val teacherId: String? = null,
    @Json(name = "rating")    val rating: Int = 0,
    @Json(name = "comment")   val comment: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ReviewResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: ReviewData? = null
)

@JsonClass(generateAdapter = true)
data class ReviewStudentRef(
    @Json(name = "_id")          val id: String? = null,
    @Json(name = "fullName")     val fullName: String? = null,
    @Json(name = "profilePhoto") val profilePhoto: String? = null
)

@JsonClass(generateAdapter = true)
data class ReviewWithStudent(
    @Json(name = "_id")       val id: String? = null,
    @Json(name = "studentId") val studentId: ReviewStudentRef? = null,
    @ReviewClassId @Json(name = "classId") val classId: String? = null,
    @Json(name = "teacherId") val teacherId: String? = null,
    @Json(name = "rating")    val rating: Int = 0,
    @Json(name = "comment")   val comment: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)

fun ReviewWithStudent.resolvedStudentDisplayName(): String {
    val ref = studentId ?: return "Anonymous"
    ref.fullName?.takeIf { it.isNotBlank() }?.let { return it }
    if (!ref.id.isNullOrBlank()) return "Student"
    return "Anonymous"
}

@JsonClass(generateAdapter = true)
data class TeacherReviewsData(
    @Json(name = "reviews") val reviews: List<ReviewWithStudent> = emptyList(),
    @Json(name = "total")   val total: Int = 0
)

@JsonClass(generateAdapter = true)
data class TeacherReviewsResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "data")    val data: TeacherReviewsData? = null
)

@JsonClass(generateAdapter = true)
data class CheckReviewedData(
    @Json(name = "hasReviewed") val hasReviewed: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CheckReviewedResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "data")    val data: CheckReviewedData? = null
)

// ══════════════════════════════════════════════════════════════════════════════
// WALLET REQUEST MODELS
// ══════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class WalletTopUpRequest(
    @Json(name = "razorpay_order_id")   val razorpayOrderId: String,
    @Json(name = "razorpay_payment_id") val razorpayPaymentId: String,
    @Json(name = "razorpay_signature")  val razorpaySignature: String,
    @Json(name = "amount")              val amount: Double
)

@JsonClass(generateAdapter = true)
data class WalletPayTeacherRequest(
    @Json(name = "teacherId")    val teacherId: String,
    @Json(name = "amount")       val amount: Double,
    @Json(name = "classId")      val classId: String? = null,
    @Json(name = "slotId")       val slotId: String? = null,
    @Json(name = "studentName")  val studentName: String? = null,
    @Json(name = "studentEmail") val studentEmail: String? = null,
    @Json(name = "studentPhone") val studentPhone: String? = null
)

// ══════════════════════════════════════════════════════════════════════════════
// RESPONSE MODELS
// ══════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: AuthData? = null
)

@JsonClass(generateAdapter = true)
data class AuthData(
    @Json(name = "accessToken")  val accessToken: String? = null,
    @Json(name = "refreshToken") val refreshToken: String? = null,
    @Json(name = "user")         val user: UserResponse? = null
)

@JsonClass(generateAdapter = true)
data class UserResponse(
    @Json(name = "id")       val id: Int? = null,
    @Json(name = "_id")      val _id: String? = null,
    @Json(name = "fullName") val fullName: String? = null,
    @Json(name = "email")    val email: String? = null,
    @Json(name = "role")     val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GoalItem(
    @Json(name = "title")       val title: String,
    @Json(name = "isCompleted") val isCompleted: Boolean = false,
    @Json(name = "progress")    val progress: Int = 0,
    @Json(name = "_id")         val _id: String? = null
)

@JsonClass(generateAdapter = true)
data class SubjectItem(
    @Json(name = "title")       val title: String,
    @Json(name = "isCompleted") val isCompleted: Boolean = false,
    @Json(name = "progress")    val progress: Int = 0,
    @Json(name = "_id")         val _id: String? = null
)

@JsonClass(generateAdapter = true)
data class StudentProfileResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: StudentProfileData? = null
)

@JsonClass(generateAdapter = true)
data class StudentProfileData(
    @Json(name = "_id")                val _id: String? = null,
    @Json(name = "userId")             val userId: String? = null,
    @Json(name = "fullName")           val fullName: String? = null,
    @Json(name = "gradeLevel")         val gradeLevel: String? = null,
    @Json(name = "goals")              val goals: List<GoalItem>? = null,
    @Json(name = "subjects")           val subjects: List<SubjectItem>? = null,
    @Json(name = "profilePhoto")       val profilePhoto: String? = null,
    @Json(name = "profilePhotoFileId") val profilePhotoFileId: String? = null,
    @Json(name = "createdAt")          val createdAt: String? = null,
    @Json(name = "updatedAt")          val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AdminOptionsResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "data")    val data: AdminOptionsData? = null
)

@JsonClass(generateAdapter = true)
data class AdminOptionsData(
    @Json(name = "boards")    val boards: List<String>? = null,
    @Json(name = "subjects")  val subjects: List<String>? = null,
    @Json(name = "languages") val languages: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class TeacherProfileResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: TeacherProfileData? = null
)

@JsonClass(generateAdapter = true)
data class TeacherProfileData(
    @Json(name = "_id")            val id: String? = null,
    @Json(name = "userId")         val userId: String? = null,
    @Json(name = "fullName")       val fullName: String? = null,
    @Json(name = "email")          val email: String? = null,
    @Json(name = "gender")         val gender: String? = null,
    @Json(name = "age")            val age: Int? = null,
    @Json(name = "bio")            val bio: String? = null,
    @Json(name = "subjects")       val subjects: List<String>? = null,
    @Json(name = "boards")         val boards: List<String>? = null,
    @Json(name = "languages")      val languages: List<String>? = null,
    @Json(name = "experience")     val experience: Int? = null,
    @Json(name = "pricing")        val pricing: PricingData? = null,
    @Json(name = "qualifications") val qualifications: List<QualificationData>? = null,
    @Json(name = "profilePhoto")   val profilePhoto: String? = null,
    @Json(name = "introVideo")     val introVideo: String? = null,
    @Json(name = "isLive")         val isLive: Boolean? = false,
    @Json(name = "availability")   val availability: List<AvailabilityData>? = null
)

@JsonClass(generateAdapter = true)
data class AvailabilityData(
    @Json(name = "dayOfWeek") val dayOfWeek: Int = 0,
    @Json(name = "startTime") val startTime: String = "",
    @Json(name = "endTime")   val endTime: String = ""
)

@JsonClass(generateAdapter = true)
data class PricingData(
    @Json(name = "hourlyRate") val hourlyRate: Int? = null
)

@JsonClass(generateAdapter = true)
data class QualificationData(
    @Json(name = "degree")      val degree: String? = null,
    @Json(name = "institution") val institution: String? = null,
    @Json(name = "year")        val year: Int? = null
)

@JsonClass(generateAdapter = true)
data class NotificationItem(
    @Json(name = "_id")       val id: String? = null,
    @Json(name = "title")     val title: String? = null,
    @Json(name = "message")   val message: String? = null,
    @Json(name = "body")      val body: String? = null,
    @Json(name = "type")      val type: String? = null,
    @Json(name = "isRead")    val isRead: Boolean = false,
    @Json(name = "createdAt") val createdAt: String? = null
) {
    val safeBody:    String get() = message?.takeIf { it.isNotBlank() } ?: body?.takeIf { it.isNotBlank() } ?: ""
    val displayBody: String get() = safeBody
    val safeTitle:   String get() = title?.takeIf { it.isNotBlank() } ?: "EduVibe"
}

@JsonClass(generateAdapter = true)
data class SendNotificationRequest(
    @Json(name = "userId")   val userId: String,
    @Json(name = "type")     val type: String,
    @Json(name = "title")    val title: String,
    @Json(name = "message")  val message: String,
    @Json(name = "metadata") val metadata: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class NotificationListResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: List<NotificationItem>? = null
)

@JsonClass(generateAdapter = true)
data class GenericResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null
)

// ══════════════════════════════════════════════════════════════════════════════
// TEACHER LIST MODELS
// ══════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class TeacherRating(
    @Json(name = "average") val average: Double = 0.0,
    @Json(name = "count")   val count: Int = 0
)

@JsonClass(generateAdapter = true)
data class TeacherItem(
    @Json(name = "_id")          val id: String = "",
    @Json(name = "userId")       val userId: String = "",
    @Json(name = "fullName")     val fullName: String = "",
    @Json(name = "bio")          val bio: String = "",
    @Json(name = "subjects")     val subjects: List<String> = emptyList(),
    @Json(name = "experience")   val experience: Int = 0,
    @Json(name = "profilePhoto") val profilePhoto: String? = null,
    @Json(name = "introVideo")   val introVideo: String? = null,
    @Json(name = "rating")       val rating: TeacherRating = TeacherRating(),
    @Json(name = "pricing")      val pricing: PricingData? = null,
    @Json(name = "isLive")       val isLive: Boolean = false
)

@JsonClass(generateAdapter = true)
data class TeacherListResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "data")    val data: List<TeacherItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TeacherDetailResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "data")    val data: TeacherItem? = null
)

// ══════════════════════════════════════════════════════════════════════════════
// CLASS / SESSION MODELS
// ══════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class ScheduleSlotResponse(
    @Json(name = "_id")            val id: String? = null,
    @Json(name = "startTime")      val startTime: String? = null,
    @Json(name = "endTime")        val endTime: String? = null,
    @Json(name = "isRecurring")    val isRecurring: Boolean = false,
    @Json(name = "recurrenceRule") val recurrenceRule: String? = null
)

@JsonClass(generateAdapter = true)
data class TeacherRef(
    @Json(name = "_id")          val id: String? = null,
    @Json(name = "fullName")     val fullName: String? = null,
    @Json(name = "email")        val email: String? = null,
    @Json(name = "profilePhoto") val profilePhoto: String? = null
)

@JsonClass(generateAdapter = true)
data class ClassData(
    @Json(name = "_id")             val id: String = "",
    @Json(name = "teacherId")       val teacherIdRaw: Any? = null,
    @Json(name = "studentId")       val studentIdRaw: Any? = null,
    @Json(name = "title")           val title: String = "",
    @Json(name = "description")     val description: String = "",
    @Json(name = "price")           val price: Double = 0.0,
    @Json(name = "currency")        val currency: String = "INR",
    @Json(name = "maxStudents")     val maxStudents: Int? = null,
    @Json(name = "schedule")        val schedule: List<ScheduleSlotResponse> = emptyList(),
    @Json(name = "status")          val status: String = "upcoming",
    @Json(name = "enrollmentCount") val enrollmentCount: Int = 0,
    @Json(name = "videoUrl")        val videoUrl: String? = null,
    @Json(name = "createdAt")       val createdAt: String? = null,
    @Json(name = "updatedAt")       val updatedAt: String? = null
) {
    // Teacher ka populated object (student side ke cards ke liye)
    val parsedTeacherRef: TeacherRef?
        get() = when (teacherIdRaw) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = teacherIdRaw as Map<String, Any?>
                TeacherRef(
                    id           = map["_id"] as? String,
                    fullName     = map["fullName"] as? String,
                    email        = map["email"] as? String,
                    profilePhoto = map["profilePhoto"] as? String
                )
            }
            else -> null
        }

    // Student ka populated object (teacher side ke cards ke liye)
    // Agar backend String bheje toh null milega — ViewModel mein alag fetch hoga
    val parsedStudentRef: TeacherRef?
        get() = when (studentIdRaw) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = studentIdRaw as Map<String, Any?>
                TeacherRef(
                    id           = map["_id"] as? String,
                    fullName     = map["fullName"] as? String,
                    email        = map["email"] as? String,
                    profilePhoto = map["profilePhoto"] as? String
                )
            }
            else -> null
        }
}

@JsonClass(generateAdapter = true)
data class ClassResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: ClassData? = null
)

@JsonClass(generateAdapter = true)
data class ClassListResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: List<ClassData> = emptyList()
)

// ══════════════════════════════════════════════════════════════════════════════
// WebRTC ICE SERVER CONFIGURATION
// ══════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class IceServerConfig(
    @Json(name = "urls")       val urls: String,
    @Json(name = "username")   val username: String? = null,
    @Json(name = "credential") val credential: String? = null
)

@JsonClass(generateAdapter = true)
data class WebRTCConfigResponse(
    @Json(name = "success")    val success: Boolean = false,
    @Json(name = "iceServers") val iceServers: List<IceServerConfig> = emptyList()
)

// ══════════════════════════════════════════════════════════════════════════════
// CHAT / MESSAGING MODELS
// ══════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class ChatMessageData(
    @Json(name = "_id")            val id: String? = null,
    @Json(name = "conversationId") val conversationId: String = "",
    @Json(name = "senderId")       val senderId: String = "",
    @Json(name = "receiverId")     val receiverId: String = "",
    @Json(name = "content")        val content: String = "",
    @Json(name = "readAt")         val readAt: String? = null,
    @Json(name = "createdAt")      val createdAt: String? = null,
    @Json(name = "updatedAt")      val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ChatMessageResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: List<ChatMessageData> = emptyList()
)

// ══════════════════════════════════════════════════════════════════════════════
// SESSION MODELS
// ══════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class JoinedUserData(
    @Json(name = "userId")   val userId: String = "",
    @Json(name = "joinedAt") val joinedAt: String? = null,
    @Json(name = "leftAt")   val leftAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SessionData(
    @Json(name = "_id")              val id: String = "",
    @Json(name = "classId")          val classId: String? = null,
    @Json(name = "teacherId")        val teacherId: String? = null,
    @Json(name = "startTime")        val startTime: String? = null,
    @Json(name = "endTime")          val endTime: String? = null,
    @Json(name = "status")           val status: String = "upcoming",
    @Json(name = "actualStartTime")  val actualStartTime: String? = null,
    @Json(name = "actualEndTime")    val actualEndTime: String? = null,
    @Json(name = "joinedUsers")      val joinedUsers: List<JoinedUserData> = emptyList(),
    @Json(name = "attendedStudents") val attendedStudents: List<JoinedUserData> = emptyList(),
    @Json(name = "createdAt")        val createdAt: String? = null,
    @Json(name = "updatedAt")        val updatedAt: String? = null
) {
    fun hasUserJoined(userId: String): Boolean = joinedUsers.any { it.userId == userId }

    val statusLabel: String get() = when (status) {
        "upcoming"  -> "Upcoming"
        "ongoing"   -> "Live"
        "completed" -> "Completed"
        "missed"    -> "Missed"
        else        -> status.replaceFirstChar { it.uppercase() }
    }

    val isLive: Boolean get() = status == "ongoing"
}

@JsonClass(generateAdapter = true)
data class SessionResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: SessionData? = null
)

@JsonClass(generateAdapter = true)
data class SessionListResponse(
    @Json(name = "success")    val success: Boolean = false,
    @Json(name = "message")    val message: String? = null,
    @Json(name = "data")       val data: List<SessionData> = emptyList(),
    @Json(name = "pagination") val pagination: PaginationData? = null
)

@JsonClass(generateAdapter = true)
data class PaginationData(
    @Json(name = "total") val total: Int = 0,
    @Json(name = "page")  val page: Int = 1,
    @Json(name = "limit") val limit: Int = 50
)

@JsonClass(generateAdapter = true)
data class EnrollRequest(
    @Json(name = "classId") val classId: String
)

@JsonClass(generateAdapter = true)
data class LiveStatusRequest(
    @Json(name = "isLive") val isLive: Boolean
)

@JsonClass(generateAdapter = true)
data class GenerateSlotsRequest(
    @Json(name = "date")            val date: String,
    @Json(name = "startTime")       val startTime: String,
    @Json(name = "endTime")         val endTime: String,
    @Json(name = "durationMinutes") val durationMinutes: Int = 60
)

@JsonClass(generateAdapter = true)
data class SlotData(
    @Json(name = "_id")           val id:           String = "",
    @Json(name = "teacherId")     val teacherIdRaw: Any? = null,
    @Json(name = "date")          val date:         String = "",
    @Json(name = "startTime")     val startTime:    String = "",
    @Json(name = "endTime")       val endTime:      String = "",
    @Json(name = "status")        val status:       String = "available",
    @Json(name = "priceInPaise")  val priceInPaise: Int = 0,
    @Json(name = "bookedBy")      val bookedBy:     String? = null,
    @Json(name = "paymentId")     val paymentId:    String? = null,
    @Json(name = "classId")       val classId:      String? = null,
    @Json(name = "studentName")   val studentName:  String? = null,
    @Json(name = "studentEmail")  val studentEmail: String? = null,
    @Json(name = "studentPhone")  val studentPhone: String? = null,
    @Json(name = "studentPhoto")  val studentPhoto: String? = null
) {
    val priceInRupees: Int get() = priceInPaise / 100
    val teacherId: String get() = when (teacherIdRaw) {
        is String    -> teacherIdRaw
        is Map<*, *> -> (teacherIdRaw["_id"] as? String) ?: ""
        else         -> ""
    }
}

@JsonClass(generateAdapter = true)
data class SlotListResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: List<SlotData> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BookSlotResponseData(
    @Json(name = "booking")              val booking: BookingData? = null,
    @Json(name = "slot")                 val slot: SlotData? = null,
    @Json(name = "walletDeductedInRs")   val walletDeductedInRs: Double = 0.0,
    @Json(name = "newWalletBalanceInRs") val newWalletBalanceInRs: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class BookingData(
    @Json(name = "_id")           val id: String = "",
    @Json(name = "slotId")        val slotId: String = "",
    @Json(name = "studentId")     val studentId: String = "",
    @Json(name = "teacherId")     val teacherId: String = "",
    @Json(name = "amountInPaise") val amountInPaise: Int = 0,
    @Json(name = "status")        val status: String = "upcoming"
)

@JsonClass(generateAdapter = true)
data class BookSlotResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: BookSlotResponseData? = null
)

@JsonClass(generateAdapter = true)
data class BookSlotRequest(
    @Json(name = "paymentId")    val paymentId:    String? = null,
    @Json(name = "studentName")  val studentName:  String? = null,
    @Json(name = "studentEmail") val studentEmail: String? = null,
    @Json(name = "studentPhone") val studentPhone: String? = null
)

@JsonClass(generateAdapter = true)
data class DeleteSlotResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null
)

// ══════════════════════════════════════════════════════════════════════════════
// PAYMENT MODELS (Razorpay)
// ══════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class CreateOrderRequest(
    @Json(name = "amount")    val amount: Double,
    @Json(name = "currency")  val currency: String = "INR",
    @Json(name = "receipt")   val receipt: String? = null,
    @Json(name = "classId")   val classId: String? = null,
    @Json(name = "sessionId") val sessionId: String? = null,
    @Json(name = "bookingId") val bookingId: String? = null,
    @Json(name = "teacherId") val teacherId: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateOrderResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: OrderData? = null
)

@JsonClass(generateAdapter = true)
data class OrderData(
    @Json(name = "orderId")   val orderId: String? = null,
    @Json(name = "amount")    val amount: Int? = null,
    @Json(name = "currency")  val currency: String? = null,
    @Json(name = "keyId")     val keyId: String? = null,
    @Json(name = "receipt")   val receipt: String? = null,
    @Json(name = "paymentId") val paymentId: String? = null,
    @Json(name = "isDummy")   val isDummy: Boolean? = false
)

@JsonClass(generateAdapter = true)
data class VerifyPaymentRequest(
    @Json(name = "razorpay_order_id")   val razorpayOrderId: String,
    @Json(name = "razorpay_payment_id") val razorpayPaymentId: String,
    @Json(name = "razorpay_signature")  val razorpaySignature: String
)

@JsonClass(generateAdapter = true)
data class RazorpayKeyResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: RazorpayKeyData? = null
)

@JsonClass(generateAdapter = true)
data class RazorpayKeyData(
    @Json(name = "keyId") val keyId: String? = null
)

@JsonClass(generateAdapter = true)
data class FcmTokenRequest(
    @Json(name = "token") val token: String
)

// ══════════════════════════════════════════════════════════════════════════════
// WALLET MODELS
// ══════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class WalletData(
    @Json(name = "_id")                  val id: String = "",
    @Json(name = "userId")               val userId: String = "",
    @Json(name = "balance")              val balance: Double = 0.0,
    @Json(name = "currency")             val currency: String = "INR",
    @Json(name = "walletDeductedInRs")   val walletDeductedInRs: Double = 0.0,
    @Json(name = "newWalletBalanceInRs") val newWalletBalanceInRs: Double = 0.0,
    @Json(name = "createdAt")            val createdAt: String? = null,
    @Json(name = "updatedAt")            val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class WalletResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: WalletData? = null
)

@JsonClass(generateAdapter = true)
data class TransactionData(
    @Json(name = "_id")          val id: String = "",
    @Json(name = "fromWalletId") val fromWalletId: String = "",
    @Json(name = "toWalletId")   val toWalletId: String = "",
    @Json(name = "amount")       val amount: Double = 0.0,
    @Json(name = "type")         val type: String = "",
    @Json(name = "status")       val status: String = "success",
    @Json(name = "createdAt")    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class TransactionListResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data")    val data: List<TransactionData> = emptyList()
)

// ══════════════════════════════════════════════════════════════════════════════
// API SERVICE
// ══════════════════════════════════════════════════════════════════════════════

interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────
    @POST("auth/signup")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/signup")
    suspend fun registerRaw(@Body body: RequestBody): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/firebaseAuth")
    suspend fun firebaseAuth(@Body request: FirebaseAuthRequest): Response<AuthResponse>

    // ── Student Profile ───────────────────────────────────────────────────────
    @GET("students/profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<StudentProfileResponse>

    // ── NEW: Teacher ke liye student ki profile fetch karo (photo ke liye) ──
    // Backend endpoint: GET students/{userId}/profile
    // Agar yeh endpoint nahi hai toh backend developer se banwao
    @GET("students/{userId}/profile")
    suspend fun getStudentProfileById(
        @Header("Authorization") token: String,
        @Path("userId") userId: String
    ): Response<StudentProfileResponse>

    @Multipart
    @PATCH("students/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Part profilePhoto: MultipartBody.Part?,
        @Part("fullName") fullName: RequestBody,
        @Part("gradeLevel") gradeLevel: RequestBody,
        @Part("goals[0][title]") goal0: RequestBody?,
        @Part("goals[1][title]") goal1: RequestBody?,
        @Part("goals[2][title]") goal2: RequestBody?,
        @Part("goals[3][title]") goal3: RequestBody?,
        @Part("goals[4][title]") goal4: RequestBody?,
        @Part("subjects[0]") subject0: RequestBody?,
        @Part("subjects[1]") subject1: RequestBody?,
        @Part("subjects[2]") subject2: RequestBody?,
        @Part("subjects[3]") subject3: RequestBody?,
        @Part("subjects[4]") subject4: RequestBody?,
        @Part("subjects[5]") subject5: RequestBody?,
        @Part("subjects[6]") subject6: RequestBody?
    ): Response<StudentProfileResponse>

    // ── Teacher Profile ───────────────────────────────────────────────────────
    @GET("teachers/me")
    suspend fun getTeacherProfile(@Header("Authorization") token: String): Response<TeacherProfileResponse>

    @PUT("teachers/profile")
    suspend fun createTeacherProfile(
        @Header("Authorization") token: String,
        @Body request: TeacherProfileRequest
    ): Response<TeacherProfileResponse>

    @Multipart
    @PUT("teachers/profile")
    suspend fun updateTeacherProfileWithMedia(
        @Header("Authorization") token: String,
        @Part profilePhoto: MultipartBody.Part?,
        @Part introVideo: MultipartBody.Part?,
        @Part("fullName") fullName: RequestBody? = null,
        @Part("email") email: RequestBody? = null,
        @Part("gender") gender: RequestBody? = null,
        @Part("age") age: RequestBody? = null,
        @Part("qualifications[0][degree]") degree: RequestBody? = null,
        @Part("bio") bio: RequestBody,
        @Part("experience") experience: RequestBody,
        @Part("pricing[hourlyRate]") pricingHourlyRate: RequestBody,
        @Part("pricing[currency]") pricingCurrency: RequestBody,
        @Part("subjects[0]") subject0: RequestBody? = null,
        @Part("subjects[1]") subject1: RequestBody? = null,
        @Part("subjects[2]") subject2: RequestBody? = null,
        @Part("subjects[3]") subject3: RequestBody? = null,
        @Part("subjects[4]") subject4: RequestBody? = null,
        @Part("subjects[5]") subject5: RequestBody? = null,
        @Part("subjects[6]") subject6: RequestBody? = null,
        @Part("boards[0]") board0: RequestBody? = null,
        @Part("boards[1]") board1: RequestBody? = null,
        @Part("boards[2]") board2: RequestBody? = null,
        @Part("languages[0]") language0: RequestBody? = null,
        @Part("languages[1]") language1: RequestBody? = null,
        @Part("languages[2]") language2: RequestBody? = null
    ): Response<TeacherProfileResponse>

    // ── Classes ───────────────────────────────────────────────────────────────
    @POST("classes")
    suspend fun createClass(
        @Header("Authorization") token: String,
        @Body request: CreateClassRequest
    ): Response<ClassResponse>

    @GET("classes")
    suspend fun getTeacherClasses(
        @Header("Authorization") token: String,
        @Query("mine") mine: Boolean = true,
        @Query("status") status: String? = null
    ): Response<ClassListResponse>

    @GET("classes")
    suspend fun getStudentClasses(
        @Header("Authorization") token: String,
        @Query("enrolled") enrolled: Boolean = true,
        @Query("status") status: String? = null
    ): Response<ClassListResponse>

    @POST("classes/cleanup-stuck-sessions")
    suspend fun cleanupStuckSessions(@Header("Authorization") token: String): Response<GenericResponse>

    @PUT("classes/{id}")
    suspend fun updateClass(
        @Header("Authorization") token: String,
        @Path("id") classId: String,
        @Body request: UpdateClassRequest
    ): Response<ClassResponse>

    @DELETE("classes/{id}")
    suspend fun deleteClass(
        @Header("Authorization") token: String,
        @Path("id") classId: String
    ): Response<GenericResponse>

    @GET("classes/{id}")
    suspend fun getClassById(
        @Header("Authorization") token: String,
        @Path("id") classId: String
    ): Response<ClassResponse>

    // ── Reviews ───────────────────────────────────────────────────────────────
    @POST("reviews")
    suspend fun submitReview(
        @Header("Authorization") token: String,
        @Body request: ReviewRequest
    ): Response<ReviewResponse>

    @GET("reviews/teacher/{teacherId}")
    suspend fun getTeacherReviews(@Path("teacherId") teacherId: String): Response<TeacherReviewsResponse>

    @GET("reviews/class/{classId}")
    suspend fun getClassReviews(
        @Header("Authorization") token: String,
        @Path("classId") classId: String
    ): Response<TeacherReviewsResponse>

    @GET("reviews/check/{classId}")
    suspend fun checkReviewed(
        @Header("Authorization") token: String,
        @Path("classId") classId: String
    ): Response<CheckReviewedResponse>

    // ── Teachers (Student side) ───────────────────────────────────────────────
    @GET("teachers")
    suspend fun listTeachers(@Header("Authorization") token: String): Response<TeacherListResponse>

    @GET("teachers/{id}")
    suspend fun getTeacher(
        @Header("Authorization") token: String,
        @Path("id") teacherId: String
    ): Response<TeacherDetailResponse>

    // ── Notifications ─────────────────────────────────────────────────────────
    @GET("notifications")
    suspend fun getNotifications(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<NotificationListResponse>

    @PATCH("notifications/{id}/read")
    suspend fun markAsRead(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<GenericResponse>

    @PATCH("notifications/read-all")
    suspend fun markAllAsRead(@Header("Authorization") token: String): Response<GenericResponse>

    // ── Admin ─────────────────────────────────────────────────────────────────
    @GET("admin/options")
    suspend fun getAdminOptions(): Response<AdminOptionsResponse>

    // ── WebRTC Config ─────────────────────────────────────────────────────────
    @GET("config/webrtc")
    suspend fun getWebRTCConfig(): Response<WebRTCConfigResponse>

    // ── Chat ──────────────────────────────────────────────────────────────────
    @GET("chat/conversations/{conversationId}/messages")
    suspend fun getChatMessages(
        @Header("Authorization") token: String,
        @Path("conversationId") conversationId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<ChatMessageResponse>

    // ── Session Management ────────────────────────────────────────────────────
    @POST("classes/{id}/sessions/start")
    suspend fun startSession(
        @Header("Authorization") token: String,
        @Path("id") classId: String
    ): Response<GenericResponse>

    @POST("sessions/{id}/end")
    suspend fun endSession(
        @Header("Authorization") token: String,
        @Path("id") sessionId: String
    ): Response<GenericResponse>

    @GET("sessions/{id}")
    suspend fun getSessionById(
        @Header("Authorization") token: String,
        @Path("id") sessionId: String
    ): Response<SessionResponse>

    @GET("classes/{id}/sessions")
    suspend fun getClassSessions(
        @Header("Authorization") token: String,
        @Path("id") classId: String,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<SessionListResponse>

    @POST("sessions/{id}/join")
    suspend fun joinSession(
        @Header("Authorization") token: String,
        @Path("id") sessionId: String
    ): Response<GenericResponse>

    @POST("sessions/{id}/leave")
    suspend fun leaveSession(
        @Header("Authorization") token: String,
        @Path("id") sessionId: String,
        @Body body: LeaveSessionRequest
    ): Response<GenericResponse>

    @POST("sessions/{id}/attend")
    suspend fun attendSession(
        @Header("Authorization") token: String,
        @Path("id") sessionId: String
    ): Response<GenericResponse>

    @GET("students/my-sessions")
    suspend fun getStudentSessions(
        @Header("Authorization") token: String,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<SessionListResponse>

    @GET("teachers/my-sessions")
    suspend fun getTeacherSessions(
        @Header("Authorization") token: String,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<SessionListResponse>

    // ── Enrollment ────────────────────────────────────────────────────────────
    @POST("enrollments")
    suspend fun enrollInClass(
        @Header("Authorization") token: String,
        @Body request: EnrollRequest
    ): Response<GenericResponse>

    @DELETE("enrollments/{id}")
    suspend fun unenrollFromClass(
        @Header("Authorization") token: String,
        @Path("id") enrollmentId: String
    ): Response<GenericResponse>

    // ── Teacher Live & Slot Booking ───────────────────────────────────────────
    @PUT("slots/teachers/live")
    suspend fun toggleLiveStatus(
        @Header("Authorization") token: String,
        @Body request: LiveStatusRequest
    ): Response<GenericResponse>

    @GET("slots/teachers/live")
    suspend fun getLiveTeachers(@Header("Authorization") token: String = ""): Response<TeacherListResponse>

    @POST("slots/teachers/slots/generate")
    @Headers("Content-Type: application/json; charset=UTF-8")
    suspend fun generateSlots(
        @Header("Authorization") token: String,
        @Body body: RequestBody
    ): Response<GenericResponse>

    @GET("slots/teachers/{id}/slots")
    suspend fun getTeacherSlots(
        @Header("Authorization") token: String = "",
        @Path("id") teacherId: String,
        @Query("date") date: String
    ): Response<SlotListResponse>

    @POST("slots/slots/{id}/book")
    suspend fun bookSlot(
        @Header("Authorization") token: String,
        @Path("id") slotId: String,
        @Body request: BookSlotRequest = BookSlotRequest()
    ): Response<BookSlotResponse>

    @POST("slots/slots/{id}/cancel")
    suspend fun cancelSlot(
        @Header("Authorization") token: String,
        @Path("id") slotId: String
    ): Response<GenericResponse>

    @DELETE("slots/slots/{id}")
    suspend fun deleteSlot(
        @Header("Authorization") token: String,
        @Path("id") slotId: String
    ): Response<DeleteSlotResponse>

    @GET("slots/students/my-slots")
    suspend fun getStudentSlots(@Header("Authorization") token: String): Response<SlotListResponse>

    @GET("teachers/my-slots")
    suspend fun getTeacherMySlots(
        @Header("Authorization") token: String,
        @Query("date") date: String? = null
    ): Response<SlotListResponse>

    // ── Payment (Razorpay) ────────────────────────────────────────────────────
    @POST("payments/create-order")
    suspend fun createPaymentOrder(
        @Header("Authorization") token: String,
        @Body request: CreateOrderRequest
    ): Response<CreateOrderResponse>

    @POST("payments/verify")
    suspend fun verifyPayment(
        @Header("Authorization") token: String,
        @Body request: VerifyPaymentRequest
    ): Response<GenericResponse>

    @GET("payments/razorpay-key")
    suspend fun getRazorpayKey(@Header("Authorization") token: String): Response<RazorpayKeyResponse>

    // ── FCM Token ─────────────────────────────────────────────────────────────
    @POST("device-token")
    suspend fun saveFcmToken(
        @Header("Authorization") token: String,
        @Body request: FcmTokenRequest
    ): Response<GenericResponse>

    // ── Notifications (Send) ──────────────────────────────────────────────────
    @POST("notification/send")
    suspend fun sendNotification(
        @Header("Authorization") token: String,
        @Body request: SendNotificationRequest
    ): Response<GenericResponse>

    // ── Wallet ────────────────────────────────────────────────────────────────
    @GET("wallet")
    suspend fun getWallet(@Header("Authorization") token: String): Response<WalletResponse>

    @GET("wallet/transactions")
    suspend fun getWalletTransactions(@Header("Authorization") token: String): Response<TransactionListResponse>

    @POST("wallet/create-topup-order")
    suspend fun createWalletTopUpOrder(
        @Header("Authorization") token: String,
        @Body request: CreateOrderRequest
    ): Response<CreateOrderResponse>

    @POST("wallet/topup")
    suspend fun topUpWalletRaw(
        @Header("Authorization") token: String,
        @Body body: RequestBody
    ): Response<WalletResponse>

    @POST("wallet/pay-teacher")
    suspend fun payTeacherFromWallet(
        @Header("Authorization") token: String,
        @Body request: RequestBody
    ): Response<WalletResponse>

    @POST("classes")
    suspend fun createClassRaw(
        @Header("Authorization") token: String,
        @Body body: RequestBody
    ): Response<ClassResponse>

    @POST("reviews")
    suspend fun submitReviewRaw(
        @Header("Authorization") token: String,
        @Body body: RequestBody
    ): Response<ReviewResponse>

    // ── Early Leave ───────────────────────────────────────────────────────────
    @POST("sessions/early-leave")
    suspend fun reportEarlyLeave(
        @Header("Authorization") token: String,
        @Body body: EarlyLeaveRequest
    ): Response<EarlyLeaveResponse>

    @GET("early-leave/{classId}")
    suspend fun getEarlyLeaveReports(
        @Header("Authorization") token: String,
        @Path("classId") classId: String
    ): Response<EarlyLeaveListResponse>

    data class ClassStatusResponse(
        val success: Boolean,
        val data: ClassStatusData
    )

    data class ClassStatusData(
        val status: String
    )

    @GET("classes/{classId}")
    suspend fun getClassById(
        @Path("classId") classId: String
    ): Response<ClassStatusResponse>
}