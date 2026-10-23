package com.example.tutorapp.teacher

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.tutorapp.SessionViewModel
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TeacherProfileData
import com.example.tutorapp.ui.theme.*
import android.widget.MediaController
import android.widget.VideoView
import androidx.annotation.RequiresApi
import androidx.compose.ui.tooling.preview.Preview
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun EditProfileScreen(
    navController: androidx.navigation.NavController,
    sessionViewModel: SessionViewModel
) {
    val context = LocalContext.current
    
    // Fetch existing profile
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        sessionViewModel.loadTeacherProfile(
            onSuccess = { isLoading = false },
            onError = { isLoading = false }
        )
    }
    
    val backendProfile by sessionViewModel.teacherProfile.collectAsStateWithLifecycle()
    
    // Form state
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var degree by remember { mutableStateOf("") }
    var chargePerHour by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    var languagesText by remember { mutableStateOf("") }
    var boardsText by remember { mutableStateOf("") }
    var subjectsText by remember { mutableStateOf("") }

    // Initialize from backend profile
    var existingVideoUrl by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(backendProfile) {
        backendProfile?.let { profile ->
            fullName = profile.fullName ?: ""
            email = profile.email ?: ""
            bio = profile.bio ?: ""
            gender = profile.gender ?: ""
            age = profile.age?.toString() ?: ""
            degree = profile.qualifications?.firstOrNull()?.degree ?: ""
            chargePerHour = profile.pricing?.hourlyRate?.toString() ?: ""
            experience = profile.experience?.toString() ?: ""
            languagesText = ""  // Backend doesn't have these fields yet
            boardsText = ""
            subjectsText = profile.subjects?.joinToString(", ") ?: ""
            existingVideoUrl = profile.introVideo?.let { RetrofitClient.fullUrl(it) }
        }
    }
    
    // Media files
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var introVideoUri by remember { mutableStateOf<Uri?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showVideoSourceDialog by remember { mutableStateOf(false) }
    
    // Camera/Gallery launchers
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val file = File(context.cacheDir, "profile_photo.jpg")
                val out = java.io.FileOutputStream(file)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
                out.close()
                photoUri = Uri.fromFile(file)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) cameraLauncher.launch(null) }
    
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> if (uri != null) photoUri = uri }
    
    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> if (uri != null) introVideoUri = uri }
    
    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) cameraLauncher.launch(null)
        else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }
    
    // Loading state
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = EduBlue)
        }
        return
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Edit Profile", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = EduBlue)
            )
        },
        containerColor = EduBackground
    ) { innerPadding ->
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            
            // ── Profile Photo ─────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Profile Photo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = EduTextDark
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDDE6F0))
                                .border(2.dp, EduBorder, CircleShape)
                        ) {
                            if (photoUri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(photoUri),
                                    contentDescription = "Profile photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val initials = fullName.trim().split(" ")
                                    .filter { it.isNotEmpty() }
                                    .take(2)
                                    .joinToString("") { it[0].uppercaseChar().toString() }
                                if (initials.isNotEmpty()) {
                                    Text(
                                        text = initials,
                                        color = EduBlue,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 32.sp
                                    )
                                } else {
                                    Icon(Icons.Filled.Person, contentDescription = null, tint = EduGray, modifier = Modifier.size(50.dp))
                                }
                            }
                        }
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(EduBlue)
                                .border(2.dp, Color.White, CircleShape)
                                .clickable { showImageSourceDialog = true }
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tap to upload photo (JPG, PNG)",
                        fontSize = 12.sp,
                        color = EduGray
                    )
                }
            }
            
            // ── Intro Video ───────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Intro Video (Optional)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = EduTextDark
                        )
                        // Show remove button if video exists
                        if (existingVideoUrl != null || introVideoUri != null) {
                            TextButton(
                                onClick = {
                                    existingVideoUrl = null
                                    introVideoUri = null
                                }
                            ) {
                                Text("Remove", color = Color(0xFFE53935), fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // Show existing video from backend or new upload
                    val currentVideoUrl = introVideoUri?.toString() ?: existingVideoUrl
                    
                    if (currentVideoUrl != null) {
                        // Video preview area - clickable to change
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .clickable { showVideoSourceDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (introVideoUri != null) {
                                // New video selected from device - show upload prompt
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "New video selected",
                                        fontSize = 14.sp,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Tap to change",
                                        fontSize = 12.sp,
                                        color = EduGray
                                    )
                                }
                            } else {
                                // Existing video from backend - show actual video player
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setVideoURI(android.net.Uri.parse(existingVideoUrl))
                                            // Add media controls (play/pause/seek)
                                            val mediaController = MediaController(ctx)
                                            mediaController.setAnchorView(this)
                                            setMediaController(mediaController)
                                            // Don't auto-start, let user tap play
                                            requestFocus()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                )
                                
                                // Overlay to indicate clickable
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.3f))
                                        .clickable { showVideoSourceDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.7f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Filled.Edit,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                "Tap to change video",
                                                fontSize = 13.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // No video - show upload prompt
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF5F5F5))
                                .clickable { showVideoSourceDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Videocam,
                                    contentDescription = null,
                                    tint = EduBlue,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Tap to upload intro video",
                                    fontSize = 13.sp,
                                    color = EduGray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Short introduction video (MP4, max 100MB)",
                        fontSize = 11.sp,
                        color = EduGray
                    )
                }
            }
            
            // ── Basic Info ────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Basic Information", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = EduTextDark)
                    
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name *", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduBlue,
                            unfocusedBorderColor = EduBorder
                        )
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email *", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduBlue,
                            unfocusedBorderColor = EduBorder
                        )
                    )

                    OutlinedTextField(
                        value = degree,
                        onValueChange = { degree = it },
                        label = { Text("Degree / Qualification", fontSize = 13.sp) },
                        placeholder = { Text("e.g. B.Tech, M.Sc, PhD", fontSize = 12.sp, color = EduGray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduBlue,
                            unfocusedBorderColor = EduBorder
                        )
                    )

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { if (it.length <= 300) bio = it },
                        label = { Text("Bio", fontSize = 13.sp) },
                        placeholder = { Text("Tell students about yourself...", fontSize = 12.sp, color = EduGray) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        maxLines = 4,
                        supportingText = { Text("${bio.length}/300", fontSize = 11.sp, color = EduGray) },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduBlue,
                            unfocusedBorderColor = EduBorder
                        )
                    )
                    
                    OutlinedTextField(
                        value = chargePerHour,
                        onValueChange = { chargePerHour = it.filter { c -> c.isDigit() } },
                        label = { Text("Charge per hour (₹)", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduBlue,
                            unfocusedBorderColor = EduBorder
                        )
                    )

                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it.filter { c -> c.isDigit() } },
                        label = { Text("Age (years)", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduBlue,
                            unfocusedBorderColor = EduBorder
                        )
                    )

                    OutlinedTextField(
                        value = experience,
                        onValueChange = { experience = it.filter { c -> c.isDigit() } },
                        label = { Text("Experience (years)", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduBlue,
                            unfocusedBorderColor = EduBorder
                        )
                    )
                }
            }
            
            // ── Teaching Details ──────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Teaching Details", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = EduTextDark)
                    
                    OutlinedTextField(
                        value = subjectsText,
                        onValueChange = { subjectsText = it },
                        label = { Text("Subjects (comma separated)", fontSize = 13.sp) },
                        placeholder = { Text("e.g. Maths, Physics, Chemistry", fontSize = 12.sp, color = EduGray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduBlue,
                            unfocusedBorderColor = EduBorder
                        )
                    )
                    
                    OutlinedTextField(
                        value = boardsText,
                        onValueChange = { boardsText = it },
                        label = { Text("Boards (comma separated)", fontSize = 13.sp) },
                        placeholder = { Text("e.g. CBSE, ICSE, State", fontSize = 12.sp, color = EduGray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduBlue,
                            unfocusedBorderColor = EduBorder
                        )
                    )
                    
                    OutlinedTextField(
                        value = languagesText,
                        onValueChange = { languagesText = it },
                        label = { Text("Languages (comma separated)", fontSize = 13.sp) },
                        placeholder = { Text("e.g. English, Hindi", fontSize = 12.sp, color = EduGray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduBlue,
                            unfocusedBorderColor = EduBorder
                        )
                    )
                }
            }
            
            // ── Save Button ───────────────────────────────────────────────────
            Button(
                onClick = {
                    // Validate required fields
                    if (fullName.trim().isEmpty()) {
                        android.util.Log.e("EditProfile", "Full name is required")
                        return@Button
                    }
                    if (email.trim().isEmpty()) {
                        android.util.Log.e("EditProfile", "Email is required")
                        return@Button
                    }
                    
                    // Upload to backend
                    sessionViewModel.updateTeacherProfileWithMedia(
                        context = context,
                        fullName = fullName.trim(),
                        email = email.trim(),
                        bio = bio.trim(),
                        gender = gender.trim(),
                        age = age.toIntOrNull() ?: 0,
                        degree = degree.trim(),
                        chargePerHour = chargePerHour.toDoubleOrNull() ?: 0.0,
                        experience = experience.toIntOrNull() ?: 0,
                        subjects = subjectsText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        boards = boardsText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        languages = languagesText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        photoUri = photoUri,
                        introVideoUri = introVideoUri,
                        onSuccess = {
                            navController.popBackStack()
                        },
                        onError = { error ->
                            // Show error (can add snackbar here)
                            android.util.Log.e("EditProfile", "Update failed: $error")
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EduBlue)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Profile", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(24.dp))
        }
    }
    
    // ── Image Source Dialog ───────────────────────────────────────────────────
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Select Photo Source", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showImageSourceDialog = false; launchCamera() }.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = EduBlue)
                        Text("Camera", fontSize = 14.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showImageSourceDialog = false; galleryLauncher.launch("image/*") }.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = EduBlue)
                        Text("Gallery", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImageSourceDialog = false }) { Text("Cancel") }
            }
        )
    }
    
    // ── Video Source Dialog ───────────────────────────────────────────────────
    if (showVideoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showVideoSourceDialog = false },
            title = { Text("Intro Video", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Option 1: Choose/Change video
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                showVideoSourceDialog = false
                                videoLauncher.launch("video/*")
                            }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Videocam,
                            contentDescription = null,
                            tint = EduBlue
                        )
                        Text(
                            text = if (introVideoUri != null || existingVideoUrl != null) "Change Video" else "Choose Video",
                            fontSize = 14.sp
                        )
                    }
                    
                    // Option 2: Remove video (only if exists)
                    if (introVideoUri != null || existingVideoUrl != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    introVideoUri = null
                                    existingVideoUrl = null
                                    showVideoSourceDialog = false
                                }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = Color.Red
                            )
                            Text(
                                "Remove Video",
                                fontSize = 14.sp,
                                color = Color.Red
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVideoSourceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
