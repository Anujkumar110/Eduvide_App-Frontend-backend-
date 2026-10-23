package com.example.tutorapp.network

import android.content.Context
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

// ══════════════════════════════════════════════════════════════════════════════
// TOKEN MANAGER
// ══════════════════════════════════════════════════════════════════════════════

object TokenManager {

    private const val PREFS_NAME  = "eduvibe_prefs"
    private const val KEY_TOKEN   = "auth_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NAME    = "user_name"
    private const val KEY_EMAIL   = "user_email"
    private const val KEY_LOGGED  = "is_logged_in"
    private const val KEY_ROLE    = "user_role"

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)

    fun getBearerToken(context: Context): String {
        val token = getToken(context) ?: ""
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    fun saveRole(context: Context, role: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ROLE, role).apply()
    }

    fun getRole(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ROLE, null)

    fun saveUserInfo(context: Context, id: String, name: String, email: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_USER_ID, id)
            .putString(KEY_NAME, name)
            .putString(KEY_EMAIL, email)
            .putBoolean(KEY_LOGGED, true)
            .apply()
    }

    fun getUserId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_ID, null)
            .takeIf { !it.isNullOrBlank() }
    }
    fun getUserName(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NAME, "User") ?: "User"
    }

    fun isLoggedIn(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOGGED, false)

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// REVIEW / PRICING ADAPTERS
// ══════════════════════════════════════════════════════════════════════════════

/** Backend sometimes returns `studentId` as an ObjectId string instead of a populated user object. */
private class ReviewStudentRefAdapter {

    private val innerMoshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val delegate = innerMoshi.adapter(ReviewStudentRef::class.java)

    @FromJson
    fun fromJson(reader: JsonReader): ReviewStudentRef? {
        return when (reader.peek()) {
            JsonReader.Token.STRING -> {
                val id = reader.nextString()
                ReviewStudentRef(id = id, fullName = null, profilePhoto = null)
            }
            JsonReader.Token.BEGIN_OBJECT -> delegate.fromJson(reader)
            JsonReader.Token.NULL -> { reader.nextNull<Any>(); null }
            else -> { reader.skipValue(); null }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: ReviewStudentRef?) {
        if (value == null) writer.nullValue()
        else delegate.toJson(writer, value)
    }
}

/** `classId` kabhi string, kabhi populated `{ "_id", "title" }` object. */
private class ReviewClassIdAdapter {

    @FromJson
    @ReviewClassId
    fun fromJson(reader: JsonReader): String? {
        return when (reader.peek()) {
            JsonReader.Token.STRING -> reader.nextString()
            JsonReader.Token.BEGIN_OBJECT -> {
                var id: String? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "_id" -> id = reader.nextString()
                        else  -> reader.skipValue()
                    }
                }
                reader.endObject()
                id
            }
            JsonReader.Token.NULL -> {
                reader.nextNull<Any>()
                null
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }


    @ToJson
    fun toJson(writer: JsonWriter, @ReviewClassId value: String?) {
        writer.nullValue()
    }
}

private class PricingAdapter {

    private val innerMoshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val innerAdapter = innerMoshi.adapter(PricingData::class.java)

    @FromJson
    fun fromJson(reader: JsonReader): PricingData? {
        return when (reader.peek()) {
            JsonReader.Token.BEGIN_OBJECT -> innerAdapter.fromJson(reader)
            JsonReader.Token.STRING -> {
                val raw = reader.nextString()
                try { innerAdapter.fromJson(raw) } catch (e: Exception) { null }
            }
            JsonReader.Token.NULL -> { reader.nextNull<Unit>(); null }
            else -> { reader.skipValue(); null }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: PricingData?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.beginObject()
            writer.name("hourlyRate").value(value.hourlyRate)
            writer.endObject()
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// RETROFIT CLIENT
// ══════════════════════════════════════════════════════════════════════════════

object RetrofitClient {

    private const val BASE_URL = AppConfig.API_BASE
    const val MEDIA_BASE       = AppConfig.MEDIA_BASE

    fun fullUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return if (path.startsWith("https")) path else "$MEDIA_BASE$path"
    }

    private val moshi by lazy {
        Moshi.Builder()
            .add(ReviewStudentRefAdapter())
            .add(ReviewClassIdAdapter())
            .add(PricingAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ApiService::class.java)
    }
}