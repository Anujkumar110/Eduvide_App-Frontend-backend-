package com.example.tutorapp.repository

import com.example.tutorapp.network.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class TeacherProfileRepository {

    private val api by lazy { RetrofitClient.api }

    // ── Get Teacher Profile ───────────────────────────────────────────────────
    suspend fun getProfile(token: String): Result<TeacherProfileResponse> {
        return try {
            val response = api.getTeacherProfile(token)
            if (response.isSuccessful) {
                Result.success(response.body() ?: TeacherProfileResponse(success = true))
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("TeacherRepo", "getProfile failed: ${response.code()} - $errorBody")
                Result.failure(Exception("Failed: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            android.util.Log.e("TeacherRepo", "getProfile exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Update Teacher Profile ────────────────────────────────────────────────
    suspend fun updateProfile(
        token: String,
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
    ): Result<TeacherProfileResponse> {

        fun String.toReqBody() = this.toRequestBody("text/plain".toMediaTypeOrNull())

        val photoPart = photoFile?.let {
            MultipartBody.Part.createFormData("profilePhoto", it.name, it.asRequestBody("image/jpeg".toMediaTypeOrNull()))
        }

        val videoPart = introVideoFile?.let {
            MultipartBody.Part.createFormData("introVideo", it.name, it.asRequestBody("video/mp4".toMediaTypeOrNull()))
        }

        // Subjects — pad to exactly 7 slots
        val cleanSubjects = subjects.map { it.trim() }.filter { it.isNotEmpty() }
        val paddedSubjects = MutableList<RequestBody?>(7) { null }
        cleanSubjects.take(7).forEachIndexed { i, s -> paddedSubjects[i] = s.toReqBody() }

        // Boards — pad to exactly 3 slots
        val cleanBoards = boards.map { it.trim() }.filter { it.isNotEmpty() }
        val paddedBoards = MutableList<RequestBody?>(3) { null }
        cleanBoards.take(3).forEachIndexed { i, b -> paddedBoards[i] = b.toReqBody() }

        // Languages — pad to exactly 3 slots
        val cleanLanguages = languages.map { it.trim() }.filter { it.isNotEmpty() }
        val paddedLanguages = MutableList<RequestBody?>(3) { null }
        cleanLanguages.take(3).forEachIndexed { i, l -> paddedLanguages[i] = l.toReqBody() }

        android.util.Log.d("TeacherRepo", "===== UPDATE PROFILE =====")
        android.util.Log.d("TeacherRepo", "subjects in  : $subjects")
        android.util.Log.d("TeacherRepo", "subjects clean: $cleanSubjects")
        android.util.Log.d("TeacherRepo", "boards in    : $boards")
        android.util.Log.d("TeacherRepo", "boards clean : $cleanBoards")
        android.util.Log.d("TeacherRepo", "languages in : $languages")
        android.util.Log.d("TeacherRepo", "lang clean   : $cleanLanguages")
        android.util.Log.d("TeacherRepo", "age=$age exp=$experience charge=$chargePerHour")
        android.util.Log.d("TeacherRepo", "photo=${photoFile?.name} video=${introVideoFile?.name}")
        android.util.Log.d("TeacherRepo", "==========================")

        // Empty availability JSON (feature removed)
        val availabilityBody = "[]".toRequestBody("application/json".toMediaTypeOrNull())

        return try {
            val response = api.updateTeacherProfileWithMedia(
                token             = token,
                profilePhoto      = photoPart,
                introVideo        = videoPart,
                fullName          = fullName.toReqBody(),
                email             = email.toReqBody(),
                gender            = gender.toReqBody(),
                age               = age.toString().toReqBody(),
                degree            = degree.toReqBody(),
                bio               = bio.toReqBody(),
                experience        = experience.toString().toReqBody(),
                pricingHourlyRate = chargePerHour.toInt().toString().toReqBody(),
                pricingCurrency   = "INR".toReqBody(),
//                availability      = availabilityBody,
                subject0          = paddedSubjects[0],
                subject1          = paddedSubjects[1],
                subject2          = paddedSubjects[2],
                subject3          = paddedSubjects[3],
                subject4          = paddedSubjects[4],
                subject5          = paddedSubjects[5],
                subject6          = paddedSubjects[6],
                board0            = paddedBoards[0],
                board1            = paddedBoards[1],
                board2            = paddedBoards[2],
                language0         = paddedLanguages[0],
                language1         = paddedLanguages[1],
                language2         = paddedLanguages[2]
            )

            if (response.isSuccessful) {
                val body = response.body()
                android.util.Log.d("TeacherRepo", "✅ Update success")
                android.util.Log.d("TeacherRepo", "Response subjects : ${body?.data?.subjects}")
                android.util.Log.d("TeacherRepo", "Response boards   : ${body?.data?.boards}")
                android.util.Log.d("TeacherRepo", "Response languages: ${body?.data?.languages}")
                Result.success(body ?: TeacherProfileResponse(success = true))
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("TeacherRepo", "❌ Update failed: ${response.code()} - $errorBody")
                Result.failure(Exception("Update failed: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            android.util.Log.e("TeacherRepo", "❌ Network error: ${e.message}", e)
            Result.failure(e)
        }
    }
}