package com.example.tutorapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tutorapp.network.StudentProfileData
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ProfileUiState(
    val isLoading     : Boolean = false,
    val isSaveSuccess : Boolean = false,
    val profile       : StudentProfileData? = null,
    val errorMessage  : String? = null
)

class ProfileViewModel(
    private val repository: ProfileRepository = ProfileRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // ── Load Profile ──────────────────────────────────────────────────────────
    fun loadProfile(context: Context) {
        val token = TokenManager.getBearerToken(context)
        if (token == "Bearer ") {
            _uiState.update { it.copy(errorMessage = "Session expired. Please login again.") }
            return
        }

        //  Role check — sirf student hi student profile fetch kar sakta hai
        val role = TokenManager.getRole(context)
        if (role != null && role != "student") {
            _uiState.update { it.copy(errorMessage = "Login from student account.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getProfile(token)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(isLoading = false, profile = response.data)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message)
                    }
                }
        }
    }

    // ── Update Profile ────────────────────────────────────────────────────────
    fun updateProfile(
        context    : Context,
        fullName   : String,
        gradeLevel : String,
        goals      : List<String>,
        subjects   : List<String> = emptyList(),
        photoFile  : File? = null
    ) {
        val token = TokenManager.getBearerToken(context)
        if (token == "Bearer ") {
            _uiState.update { it.copy(errorMessage = "Session expired. Please login again.") }
            return
        }

        // Role check
        val role = TokenManager.getRole(context)
        if (role != null && role != "student") {
            _uiState.update { it.copy(errorMessage = "login from student account.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.updateProfile(token, fullName, gradeLevel, goals, subjects, photoFile)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isLoading     = false,
                            isSaveSuccess = true,
                            profile       = response.data
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message)
                    }
                }
        }
    }

    fun resetState() {
        _uiState.update { it.copy(isSaveSuccess = false, errorMessage = null) }
    }
}