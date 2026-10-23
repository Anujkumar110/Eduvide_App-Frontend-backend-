@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.example.tutorapp.teacher

import android.Manifest
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TeacherProfileRequest
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.ui.theme.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CreateProfileStep2Preview() {
    CompleteProfileStep2Screen(navController = rememberNavController())
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CompleteProfileStep2Screen(navController: NavController) {

    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val prefs   = context.getSharedPreferences("teacher_profile", Context.MODE_PRIVATE)

    val token = TokenManager.getBearerToken(context)

    var photoUri        by remember { mutableStateOf<Uri?>(null) }
    var videoUri        by remember { mutableStateOf<Uri?>(null) }
    var isLoading       by remember { mutableStateOf(false) }
    var errorMsg        by remember { mutableStateOf<String?>(null) }
    var showPhotoDialog by remember { mutableStateOf(false) }

    // ── Camera FileProvider URI ───────────────────────────────────────────────
    val cameraPhotoFile = remember { File(context.cacheDir, "profile_photo.jpg") }
    val cameraPhotoUri  = remember {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            cameraPhotoFile
        )
    }

    // ── ExoPlayer ─────────────────────────────────────────────────────────────
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }
    LaunchedEffect(videoUri) {
        videoUri?.let {
            exoPlayer.setMediaItem(MediaItem.fromUri(it))
            exoPlayer.prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    // ── Launchers ─────────────────────────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) photoUri = cameraPhotoUri }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) cameraLauncher.launch(cameraPhotoUri)
        else errorMsg = "Camera permission denied. Please use gallery instead."
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            photoUri = it
            // ✅ Error clear karo jab photo select ho
            if (errorMsg?.contains("photo", ignoreCase = true) == true) errorMsg = null
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            videoUri = it
            // ✅ Error clear karo jab video select ho
            if (errorMsg?.contains("video", ignoreCase = true) == true) errorMsg = null
        }
    }

    // ── Photo dialog ──────────────────────────────────────────────────────────
    if (showPhotoDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoDialog = false },
            title = { Text("Select Photo", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showPhotoDialog = false
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("📷  Take Photo", fontSize = 15.sp, color = EduBlue) }

                    TextButton(
                        onClick = {
                            showPhotoDialog = false
                            photoLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("🖼️  Choose from Gallery", fontSize = 15.sp, color = EduBlue) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── Submit ────────────────────────────────────────────────────────────────
    fun submitProfile() {

        // ✅ VALIDATION: Photo aur Video dono zaroori hain
        if (photoUri == null) {
            errorMsg = "Please add a profile photo before continuing."
            return
        }
        if (videoUri == null) {
            errorMsg = "Please upload an intro video before continuing."
            return
        }

        val rawToken = TokenManager.getToken(context)
        if (rawToken.isNullOrBlank()) {
            errorMsg = "Session expired. Please login again."
            return
        }

        scope.launch {
            isLoading = true
            errorMsg  = null
            try {
                // ✅ Step 2 mein sirf Photo + Video upload hoga (Step 1 mein JSON already save ho chuka hai)
                val chargePerHour = prefs.getString("chargePerHour", "0")?.toIntOrNull() ?: 0
                val bio           = prefs.getString("bio", "") ?: ""
                val experience    = prefs.getString("experience", "0 years")
                    ?.filter { it.isDigit() }?.toIntOrNull() ?: 0

                // ✅ Photo — validation upar ho chuki hai, null nahi hoga yahan
                val photoPart = photoUri?.let { uri ->
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val ext      = when {
                        mimeType.contains("png")  -> "png"
                        mimeType.contains("webp") -> "webp"
                        else                      -> "jpg"
                    }
                    val file = uriToFile(context, uri, "photo.$ext")
                    android.util.Log.d("Step2", "Photo: ${file.length()} bytes, mime=$mimeType")
                    if (file.exists() && file.length() > 0) {
                        val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
                        MultipartBody.Part.createFormData("profilePhoto", file.name, body)
                    } else {
                        errorMsg = "Photo file could not be read. Please select again."
                        isLoading = false
                        return@launch
                    }
                }

                // ✅ Video — validation upar ho chuki hai, null nahi hoga yahan
                val videoPart = videoUri?.let { uri ->
                    val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
                    val file     = uriToFile(context, uri, "video.mp4")
                    android.util.Log.d("Step2", "Video: ${file.length()} bytes, mime=$mimeType")
                    if (file.exists() && file.length() > 0) {
                        val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
                        MultipartBody.Part.createFormData("introVideo", file.name, body)
                    } else {
                        errorMsg = "Video file could not be read. Please select again."
                        isLoading = false
                        return@launch
                    }
                }

                val bioBody               = bio.toRequestBody("text/plain".toMediaTypeOrNull())
                val experienceBody        = experience.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val pricingHourlyRateBody = chargePerHour.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val pricingCurrencyBody   = "INR".toRequestBody("text/plain".toMediaTypeOrNull())

                // ✅ Only send non-empty subject/board/language strings
                val subjects      = prefs.getString("subjects", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
                val boards        = prefs.getString("boards", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
                val languages     = prefs.getString("languages", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

                val mediaResp = RetrofitClient.api.updateTeacherProfileWithMedia(
                    token             = token,
                    profilePhoto      = photoPart,
                    introVideo        = videoPart,
                    bio               = bioBody,
                    experience        = experienceBody,
                    pricingHourlyRate = pricingHourlyRateBody,
                    pricingCurrency   = pricingCurrencyBody
                )

                android.util.Log.d("Step2", "Media upload code=${mediaResp.code()}, body=${mediaResp.body()}")

                if (!mediaResp.isSuccessful) {
                    val errorBody = mediaResp.errorBody()?.string()
                    errorMsg = "Media upload failed (${mediaResp.code()}): $errorBody"
                    isLoading = false
                    return@launch
                }

                // ✅ Teacher logged in mark karo
                context.getSharedPreferences("eduvibe_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("teacher_logged_in", true).apply()

                // ✅ Teacher profile prefs clear karo — app band kare toh fresh start mile
                prefs.edit().clear().apply()

                navController.navigate(Routes.MAIN) {
                    popUpTo(0) { inclusive = true }
                }

            } catch (e: Exception) {
                android.util.Log.e("Step2", "Exception: ${e.message}", e)
                errorMsg = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text       = "Complete your profile",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            color      = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        StepIndicator(currentStep = 3)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = EduBlue
                )
            )
        },
        containerColor = EduBackground
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Upload progress indicators ────────────────────────────────────
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Photo status chip
                Surface(
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp),
                    color    = if (photoUri != null) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    border   = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (photoUri != null) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                ) {
                    Row(
                        modifier            = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment   = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector        = if (photoUri != null) Icons.Default.CheckCircle
                            else Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint               = if (photoUri != null) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier           = Modifier.size(16.dp)
                        )
                        Text(
                            text      = if (photoUri != null) "Photo ✓" else "Photo needed",
                            fontSize  = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color     = if (photoUri != null) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }
                }

                // Video status chip
                Surface(
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp),
                    color    = if (videoUri != null) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    border   = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (videoUri != null) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                ) {
                    Row(
                        modifier            = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment   = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector        = if (videoUri != null) Icons.Default.CheckCircle
                            else Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint               = if (videoUri != null) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier           = Modifier.size(16.dp)
                        )
                        Text(
                            text      = if (videoUri != null) "Video ✓" else "Video needed",
                            fontSize  = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color     = if (videoUri != null) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE0E0E0))

            // ── Profile Photo ─────────────────────────────────────────────────
            Text(
                text       = "Add a profile picture *",
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color      = if (photoUri == null) Color(0xFFE65100) else EduTextDark,
                textAlign  = TextAlign.Center
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDDE6F0))
                    .border(
                        2.dp,
                        if (photoUri == null) Color(0xFFFF9800) else Color(0xFF4CAF50),
                        CircleShape
                    )
            ) {
                if (photoUri != null) {
                    Image(
                        painter            = rememberAsyncImagePainter(photoUri),
                        contentDescription = "Profile picture",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB0C4D8))
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                                .background(Color(0xFFB0C4D8))
                        )
                    }
                }
            }

            Button(
                onClick  = { showPhotoDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EduBlue)
            ) {
                Text(
                    text       = if (photoUri == null) "Add Profile Photo" else "Change Photo",
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp
                )
            }

            Text(
                text      = "1:1 ratio, max 512 KB. No cartoons or animals.",
                fontSize  = 11.sp,
                color     = EduGray,
                textAlign = TextAlign.Center
            )

            HorizontalDivider(color = Color(0xFFE0E0E0))

            // ── Intro Video ───────────────────────────────────────────────────
            Text(
                text       = "Add a 30-second intro video *",
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color      = if (videoUri == null) Color(0xFFE65100) else EduTextDark,
                textAlign  = TextAlign.Center
            )

            if (videoUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, Color(0xFF4CAF50), RoundedCornerShape(12.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player        = exoPlayer
                                useController = true
                                layoutParams  = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFDDE6F0))
                        .border(1.5.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint     = Color(0xFFB0C4D8),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("No video selected", color = EduGray, fontSize = 12.sp)
                        Text("Max 30 seconds", color = EduGray, fontSize = 11.sp)
                    }
                }
            }

            Button(
                onClick  = { videoLauncher.launch("video/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))
            ) {
                Text(
                    text       = if (videoUri == null) "Upload Intro Video" else "Change Video",
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp
                )
            }

            Text(
                text      = "Students will see this before booking you.",
                fontSize  = 11.sp,
                color     = EduGray,
                textAlign = TextAlign.Center
            )

            // ✅ Validation error message
            errorMsg?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(8.dp),
                    color    = Color(0xFFFFEBEE)
                ) {
                    Text(
                        text      = it,
                        color     = Color(0xFFD32F2F),
                        fontSize  = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = EduBlue)
                    Spacer(Modifier.height(8.dp))
                    Text("Uploading... please wait", fontSize = 12.sp, color = EduGray)
                }
            } else {
                //  Button disabled dikhta hai jab tak dono upload nahi
                EduButton(
                    text    = "Submit & Continue →",
                    onClick = { submitProfile() },
                    // Agar aapke EduButton mein enabled param hai toh yeh use karo:
                    // enabled = photoUri != null && videoUri != null
                )

                // Skip button hata diya — dono fields zaroori hain
                if (photoUri == null || videoUri == null) {
                    Text(
                        text      = "* Both photo and video are required",
                        fontSize  = 11.sp,
                        color     = Color(0xFFE65100),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── URI to File helper ────────────────────────────────────────────────────────
fun uriToFile(context: Context, uri: Uri, fileName: String): File {
    val file = File(context.cacheDir, fileName)
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        android.util.Log.d("UriToFile", "$fileName -> ${file.length()} bytes")
    } catch (e: Exception) {
        android.util.Log.e("UriToFile", "Failed to copy $fileName: ${e.message}")
    }
    return file
}