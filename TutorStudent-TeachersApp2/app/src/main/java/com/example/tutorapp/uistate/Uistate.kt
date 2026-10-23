package com.example.tutorapp.ui

import com.example.tutorapp.network.*

// ══════════════════════════════════════════════════════════════════════════════
// GENERIC UI STATE
// ══════════════════════════════════════════════════════════════════════════════

sealed class UiState<out T> {
    object Idle                            : UiState<Nothing>()
    object Loading                         : UiState<Nothing>()
    data class Success<T>(val data: T)     : UiState<T>()
    data class Error(val message: String)  : UiState<Nothing>()
}

// ══════════════════════════════════════════════════════════════════════════════
// AUTH
// ══════════════════════════════════════════════════════════════════════════════

data class AuthUiState(
    val isLoading       : Boolean = false,
    val isSuccess       : Boolean = false,
    val errorMessage    : String? = null,
    val user            : UserResponse? = null,
    val token           : String? = null
)

// ══════════════════════════════════════════════════════════════════════════════
// MENTOR
// ══════════════════════════════════════════════════════════════════════════════

//data class MentorUiState(
//    val isLoading       : Boolean = false,
//    val mentors         : List<MentorResponse> = emptyList(),
//    val filteredMentors : List<MentorResponse> = emptyList(),
//    val selectedMentor  : MentorResponse? = null,
//    val errorMessage    : String? = null,
//    val searchQuery     : String = "",
//    val subjectFilter   : String = ""
//)

// ══════════════════════════════════════════════════════════════════════════════
// BOOKING
// ══════════════════════════════════════════════════════════════════════════════

data class BookingUiState(
    val isLoading       : Boolean = false,
    val isSuccess       : Boolean = false,
    val bookingId       : Int? = null,
    val errorMessage    : String? = null
)

// ══════════════════════════════════════════════════════════════════════════════
// SESSION / STATUS
// ══════════════════════════════════════════════════════════════════════════════

//data class SessionUiState(
//    val isLoading         : Boolean = false,
//    val ongoingSessions   : List<SessionResponse> = emptyList(),
//    val pendingSessions   : List<SessionResponse> = emptyList(),
//    val completedSessions : List<SessionResponse> = emptyList(),
//    val errorMessage      : String? = null
//)

// ══════════════════════════════════════════════════════════════════════════════
// PROFILE
// ══════════════════════════════════════════════════════════════════════════════

data class ProfileUiState(
    val isLoading       : Boolean = false,
    val isUpdateSuccess : Boolean = false,
    val user            : UserResponse? = null,
    val errorMessage    : String? = null
)

// ══════════════════════════════════════════════════════════════════════════════
// NOTIFICATION
// ══════════════════════════════════════════════════════════════════════════════

//data class NotificationUiState(
//    val isLoading       : Boolean = false,
//    val notifications   : List<NotificationResponse> = emptyList(),
//    val errorMessage    : String? = null
//)

// ══════════════════════════════════════════════════════════════════════════════
// CHAT
// ══════════════════════════════════════════════════════════════════════════════

//data class ChatUiState(
//    val isLoading       : Boolean = false,
//    val messages        : List<ChatMessageResponse> = emptyList(),
//    val isSending       : Boolean = false,
//    val errorMessage    : String? = null
//)