package com.example.tutorapp.repository



import retrofit2.Response

// ── Har repository mein use hone wala helper ──────────────────────────────────
suspend fun <T> safeCall(call: suspend () -> Response<T>): Result<T> {
    return try {
        val response = call()
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!)
        } else {
            Result.failure(Exception("Error ${response.code()}: ${response.message()}"))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Network error: ${e.localizedMessage ?: "Unknown error"}"))
    }
}