package com.example.tutorapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TeacherItem
import com.example.tutorapp.network.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class TeacherListState {
    object Loading : TeacherListState()
    data class Success(val teachers: List<TeacherItem>) : TeacherListState()
    data class Error(val message: String) : TeacherListState()
}

class TeacherViewModel : ViewModel() {

    private val _state = MutableStateFlow<TeacherListState>(TeacherListState.Loading)
    val state: StateFlow<TeacherListState> = _state

    fun loadTeachers(context: Context) {
        viewModelScope.launch {
            _state.value = TeacherListState.Loading
            try {
                val token    = TokenManager.getBearerToken(context)
                val response = RetrofitClient.api.listTeachers(token)
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.value = TeacherListState.Success(response.body()!!.data)
                } else {
                    _state.value = TeacherListState.Error("Failed to load teachers")
                }
            } catch (e: Exception) {
                _state.value = TeacherListState.Error(e.message ?: "Unknown error")
            }
        }
    }
}