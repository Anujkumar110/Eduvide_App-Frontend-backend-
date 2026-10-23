package com.example.tutorapp.repository

import com.example.tutorapp.network.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AuthRepository {

    private val api by lazy { RetrofitClient.api }

    suspend fun register(name: String, email: String, password: String, role: String = "student"): Result<AuthResponse> {
        val json = """{"fullName":"$name","email":"$email","password":"$password","role":"$role"}"""
        val body = json.toRequestBody("application/json".toMediaType())
        return safeCall { api.registerRaw(body) }
    }

    suspend fun login(email: String, password: String, role: String = "student"): Result<AuthResponse> {
        return safeCall { api.login(LoginRequest(email = email, password = password, role = role)) }
    }

    suspend fun firebaseAuth(idToken: String, role: String = "student"): Result<AuthResponse> {
        return safeCall { api.firebaseAuth(FirebaseAuthRequest(idToken = idToken, role = role)) }
    }
}