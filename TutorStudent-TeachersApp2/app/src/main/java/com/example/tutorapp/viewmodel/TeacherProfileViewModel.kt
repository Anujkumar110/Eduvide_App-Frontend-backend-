package com.example.tutorapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tutorapp.network.TeacherProfileData
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.repository.TeacherProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class TeacherProfileUiState(
    val isLoading: Boolean = false,
    val isSaveSuccess: Boolean = false,
    val profile: TeacherProfileData? = null,
    val errorMessage: String? = null
)

class TeacherProfileViewModel(
    private val repository: TeacherProfileRepository = TeacherProfileRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeacherProfileUiState())
    val uiState: StateFlow<TeacherProfileUiState> = _uiState.asStateFlow()

    // ── Load Profile ──────────────────────────────────────────────────────────
    fun loadProfile(context: Context) {
        val token = TokenManager.getBearerToken(context)
        val role = TokenManager.getRole(context)

        android.util.Log.d("ProfileVM", "===== LOAD PROFILE CALLED =====")
        android.util.Log.d("ProfileVM", "Token present: ${token.isNotEmpty() && token != "Bearer "}")
        android.util.Log.d("ProfileVM", "Token preview: ${token.take(20)}...")
        android.util.Log.d("ProfileVM", "Role: $role")
        android.util.Log.d("ProfileVM", "===============================")

        if (token.isBlank() || token == "Bearer ") {
            android.util.Log.e("ProfileVM", "❌ ERROR: Token is missing or empty")
            _uiState.update { it.copy(errorMessage = "Session expired. Please login again.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getProfile(token)
                .onSuccess { response ->
                    android.util.Log.d("ProfileVM", "===== BACKEND RESPONSE =====")
                    android.util.Log.d("ProfileVM", "Success: ${response.success}")
                    android.util.Log.d("ProfileVM", "Message: ${response.message}")
                    response.data?.let { data ->
                        android.util.Log.d("ProfileVM", "fullName: ${data.fullName}")
                        android.util.Log.d("ProfileVM", "email: ${data.email}")
                        android.util.Log.d("ProfileVM", "gender: ${data.gender}")
                        android.util.Log.d("ProfileVM", "age: ${data.age}")
                        android.util.Log.d("ProfileVM", "bio: ${data.bio}")
                        android.util.Log.d("ProfileVM", "subjects: ${data.subjects}")
                        android.util.Log.d("ProfileVM", "boards: ${data.boards}")
                        android.util.Log.d("ProfileVM", "languages: ${data.languages}")
                        android.util.Log.d("ProfileVM", "experience: ${data.experience}")
                        android.util.Log.d("ProfileVM", "pricing: ${data.pricing}")
                        android.util.Log.d("ProfileVM", "qualifications: ${data.qualifications}")
                        android.util.Log.d("ProfileVM", "profilePhoto: ${data.profilePhoto}")
                        android.util.Log.d("ProfileVM", "introVideo: ${data.introVideo}")
                    }
                    android.util.Log.d("ProfileVM", "============================")

                    _uiState.update {
                        it.copy(isLoading = false, profile = response.data)
                    }
                }
                .onFailure { error ->
                    android.util.Log.e("ProfileVM", "❌ Profile load FAILED: ${error.message}", error)
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Failed to load profile")
                    }
                }
        }
    }

    // ── Update Profile ────────────────────────────────────────────────────────
    fun updateProfile(
        context: Context,
        fullName: String,
        email: String,
        bio: String,
        gender: String,
        age: Int,
        degree: String,
        chargePerHour: Double,
        experience: Int,
        subjects: List<String>,
        boards: List<String>,
        languages: List<String>,
        photoFile: File? = null,
        introVideoFile: File? = null
    ) {
        val token = TokenManager.getBearerToken(context)

        android.util.Log.d("ProfileVM", "===== UPDATE PROFILE CALLED =====")
        android.util.Log.d("ProfileVM", "Token present: ${token.isNotEmpty() && token != "Bearer "}")
        android.util.Log.d("ProfileVM", "================================")

        if (token.isBlank() || token == "Bearer ") {
            android.util.Log.e("ProfileVM", "❌ ERROR: Token is missing or empty")
            _uiState.update { it.copy(errorMessage = "Session expired. Please login again.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.updateProfile(
                token         = token,
                fullName      = fullName,
                email         = email,
                bio           = bio,
                gender        = gender,
                age           = age,
                degree        = degree,
                chargePerHour = chargePerHour,
                experience    = experience,
                subjects      = subjects,
                boards        = boards,
                languages     = languages,
                photoFile     = photoFile,
                introVideoFile= introVideoFile
            )
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isLoading    = false,
                            isSaveSuccess= true,
                            profile      = response.data
                        )
                    }
                }
                .onFailure { error ->
                    android.util.Log.e("ProfileVM", "❌ Profile update FAILED: ${error.message}", error)
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Update failed")
                    }
                }
        }
    }

    fun resetState() {
        _uiState.update { it.copy(isSaveSuccess = false, errorMessage = null) }
    }
}