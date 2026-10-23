package com.example.tutorapp.repository

import com.example.tutorapp.network.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ProfileRepository {

    private val api by lazy { RetrofitClient.api }

    // ── Get Profile ───────────────────────────────────────────────────────────
    // token already "Bearer xyz" format mein aayega ViewModel se
    suspend fun getProfile(token: String): Result<StudentProfileResponse> {
        return safeCall { api.getProfile(token) }  //  Double "Bearer" hataya
    }

    // ── Update Profile ────────────────────────────────────────────────────────
    // token already "Bearer xyz" format mein aayega ViewModel se
    suspend fun updateProfile(
        token      : String,
        fullName   : String,
        gradeLevel : String,
        goals      : List<String>,
        subjects   : List<String> = emptyList(),
        photoFile  : File? = null
    ): Result<StudentProfileResponse> {

        val photoPart = photoFile?.let {
            val requestBody = it.asRequestBody("image/jpeg".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("profilePhoto", it.name, requestBody)
        }

        fun String.toReqBody() = this.toRequestBody("text/plain".toMediaTypeOrNull())
        fun goalOrNull(index: Int)    = goals.getOrNull(index)?.toReqBody()
        fun subjectOrNull(index: Int) = subjects.getOrNull(index)?.toReqBody()

        return safeCall {
            api.updateProfile(
                token        = token,
                profilePhoto = photoPart,
                fullName     = fullName.toReqBody(),
                gradeLevel   = gradeLevel.toReqBody(),
                goal0        = goalOrNull(0),
                goal1        = goalOrNull(1),
                goal2        = goalOrNull(2),
                goal3        = goalOrNull(3),
                goal4        = goalOrNull(4),
                subject0     = subjectOrNull(0),
                subject1     = subjectOrNull(1),
                subject2     = subjectOrNull(2),
                subject3     = subjectOrNull(3),
                subject4     = subjectOrNull(4),
                subject5     = subjectOrNull(5),
                subject6     = subjectOrNull(6)
            )
        }
    }
}