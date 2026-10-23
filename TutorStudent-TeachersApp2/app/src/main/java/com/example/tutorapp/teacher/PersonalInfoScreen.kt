package com.example.tutorapp.teacher

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.example.tutorapp.ui.theme.*

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PersonalInfoPreview() {
    val context = LocalContext.current
    PersonalInfoScreen(
        navController = rememberNavController(),
        prefs         = context.getSharedPreferences(
            "preview_prefs",
            android.content.Context.MODE_PRIVATE
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInfoScreen(
    navController: NavController,
    prefs:         SharedPreferences,
    sessionViewModel: com.example.tutorapp.SessionViewModel? = null
) {
    val context = LocalContext.current
    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // Load profile from backend on screen open
    LaunchedEffect(Unit) {
        sessionViewModel?.loadTeacherProfile(
            onSuccess = { isLoading = false },
            onError = { isLoading = false }
        ) ?: run {
            isLoading = false
        }
    }

    val backendProfileState = sessionViewModel?.teacherProfile?.collectAsStateWithLifecycle()
    val backendProfile = backendProfileState?.value

    // Pre-fill from backend profile - no SharedPreferences fallback
    var name          by remember { mutableStateOf("") }
    var email         by remember { mutableStateOf("") }
    var degree        by remember { mutableStateOf("") }
    var chargePerHour by remember { mutableStateOf("") }
    var bio           by remember { mutableStateOf("") }
    var experience    by remember { mutableStateOf("") }
    var gender        by remember { mutableStateOf("") }
    var age           by remember { mutableStateOf("") }
    var languagesText by remember { mutableStateOf("") }
    var boardsText    by remember { mutableStateOf("") }
    var subjectsText  by remember { mutableStateOf("") }
    var imageUri      by remember { mutableStateOf<android.net.Uri?>(null) }

    // Initialize from backend profile
    LaunchedEffect(backendProfile) {
        backendProfile?.let { profile ->
            name = profile.fullName ?: ""
            email = profile.email ?: ""
            degree = profile.qualifications?.firstOrNull()?.degree ?: ""
            chargePerHour = profile.pricing?.hourlyRate?.toString() ?: ""
            bio = profile.bio ?: ""
            experience = profile.experience?.toString() ?: ""
            gender = profile.gender ?: ""
            age = profile.age?.toString() ?: ""
            
            // ✅ Load subjects/boards/languages from backend with fallback
            subjectsText = profile.subjects?.joinToString(", ") ?: ""
            boardsText = profile.boards?.joinToString(", ") ?: ""
            languagesText = profile.languages?.joinToString(", ") ?: ""
            
            android.util.Log.d("PersonalInfo", "===== PROFILE LOADED =====")
            android.util.Log.d("PersonalInfo", "Name: $name")
            android.util.Log.d("PersonalInfo", "Email: $email")
            android.util.Log.d("PersonalInfo", "Bio: '$bio'")
            android.util.Log.d("PersonalInfo", "Age: '$age' (raw: ${profile.age})")
            android.util.Log.d("PersonalInfo", "Experience: '$experience' (raw: ${profile.experience})")
            android.util.Log.d("PersonalInfo", "Subjects: $subjectsText")
            android.util.Log.d("PersonalInfo", "Boards: $boardsText")
            android.util.Log.d("PersonalInfo", "Languages: $languagesText")
            android.util.Log.d("PersonalInfo", "===========================")
        }
    }

    // ── Image state ───────────────────────────────────────────────────────────
    var showImageSourceDialog by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val file = java.io.File(context.cacheDir, "profile_photo.jpg")
                val out  = java.io.FileOutputStream(file)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
                out.close()
                imageUri = Uri.fromFile(file)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) cameraLauncher.launch(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> if (uri != null) imageUri = uri }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) cameraLauncher.launch(null)
        else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    val genderOptions     = listOf("Male", "Female", "Other", "Prefer not to say")
    val ageOptions        = (18..70).map { it.toString() }
    val degreeOptions     = listOf("B.Ed", "M.Ed", "B.Sc", "M.Sc", "B.A", "M.A", "Ph.D", "Diploma")
    val experienceOptions = listOf("0 years") +
            (1..30).map { "$it year${if (it > 1) "s" else ""}" }

    var showErrors by remember { mutableStateOf(false) }

    fun isValidEmail(e: String) =
        android.util.Patterns.EMAIL_ADDRESS.matcher(e.trim()).matches()

    val nameError       = showErrors && name.isBlank()
    val emailError      = showErrors && !isValidEmail(email)
    val emailErrorMsg   = if (email.isEmpty()) "Email is required" else "Enter a valid email"
    val degreeError     = showErrors && degree.isEmpty()
    val experienceError = showErrors && experience.isEmpty()

    fun validate() =
        name.isNotBlank()   &&
                isValidEmail(email) &&
                degree.isNotEmpty() &&
                experience.isNotEmpty()

    // ── Save updated data ─────────────────────────────────────────────────────
    //  FIX: Now syncs with backend only - no SharedPreferences
    fun saveChanges() {
        if (!validate()) return
        
        isSaving = true
        
        val cleanLanguages = languagesText.split(",")
            .map { it.trim() }.filter { it.isNotEmpty() }
        val cleanBoards = boardsText.split(",")
            .map { it.trim() }.filter { it.isNotEmpty() }
        val cleanSubjects = subjectsText.split(",")
            .map { it.trim() }.filter { it.isNotEmpty() }

        // Sync with backend
        sessionViewModel?.updateTeacherProfileWithMedia(
            context = context,
            fullName = name.trim(),
            email = email.trim(),
            bio = bio.trim(),
            gender = gender,
            age = age.toIntOrNull() ?: 0,
            degree = degree,
            chargePerHour = chargePerHour.trim().toDoubleOrNull() ?: 0.0,
            experience = experience.replace(" years", "").toIntOrNull() ?: 0,
            subjects = cleanSubjects,
            boards = cleanBoards,
            languages = cleanLanguages,
            photoUri = imageUri,
            introVideoUri = null, // Don't change video from personal info screen
            onSuccess = {
                isSaving = false
                navController.popBackStack()
            },
            onError = { error ->
                isSaving = false
                android.util.Log.e("PersonalInfo", "Backend sync failed: $error")
                // Still navigate back since validation succeeded
                navController.popBackStack()
            }
        ) ?: run {
            // If no sessionViewModel, just navigate back
            isSaving = false
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Personal Information",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
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

        // Show loading state
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EduBlue)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Profile image ─────────────────────────────────────────────────
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDDE6F0))
                            .border(2.dp, EduBorder, CircleShape)
                    ) {
                        if (imageUri != null) {
                            Image(
                                painter            = rememberAsyncImagePainter(imageUri),
                                contentDescription = "Profile photo",
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize()
                            )
                        } else {
                            val initials = name.trim().split(" ")
                                .filter { it.isNotEmpty() }
                                .take(2)
                                .joinToString("") { it[0].uppercaseChar().toString() }
                            if (initials.isNotEmpty()) {
                                Text(
                                    text       = initials,
                                    color      = EduBlue,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize   = 32.sp
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint     = EduGray,
                                    modifier = Modifier.size(50.dp)
                                )
                            }
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(EduBlue)
                            .border(2.dp, Color.White, CircleShape)
                            .clickable { showImageSourceDialog = true }
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "Change photo",
                            tint     = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text     = "Tap camera icon to change photo",
                    fontSize = 12.sp,
                    color    = EduGray
                )
            }

            HorizontalDivider(color = EduBorder)

            // ── Personal fields ───────────────────────────────────────────────
            EduTextField(
                value         = name,
                onValueChange = { name = it },
                label         = "Full Name",
                isError       = nameError,
                errorMessage  = "Name is required"
            )

            EduTextField(
                value         = email,
                onValueChange = { email = it },
                label         = "Email",
                isError       = emailError,
                errorMessage  = emailErrorMsg
            )

            EduDropdown(
                label           = "Gender",
                selectedValue   = gender,
                options         = genderOptions,
                onValueSelected = { gender = it },
                isError         = showErrors && gender.isEmpty(),
                errorMessage    = "Please select your gender"
            )

            EduDropdown(
                label           = "Age",
                selectedValue   = age,
                options         = ageOptions,
                onValueSelected = { age = it },
                isError         = showErrors && age.isEmpty(),
                errorMessage    = "Please select your age"
            )

            EduTextField(
                value         = chargePerHour,
                onValueChange = { chargePerHour = it },
                label         = "Charge per hour (₹)",
                isError       = false,
                errorMessage  = ""
            )

            EduDropdown(
                label           = "Most recent degree",
                selectedValue   = degree,
                options         = degreeOptions,
                onValueSelected = { degree = it },
                isError         = degreeError,
                errorMessage    = "Please select your degree"
            )

            EduDropdown(
                label           = "Total teaching experience",
                selectedValue   = experience,
                options         = experienceOptions,
                onValueSelected = { experience = it },
                isError         = experienceError,
                errorMessage    = "Please select your experience"
            )

            // Bio
            OutlinedTextField(
                value         = bio,
                onValueChange = { if (it.length <= 300) bio = it },
                label         = { Text("Short Bio (optional)", fontSize = 13.sp) },
                placeholder   = {
                    Text("Tell students about yourself...", color = EduGray, fontSize = 13.sp)
                },
                modifier      = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                maxLines      = 5,
                supportingText = {
                    Text("${bio.length}/300", fontSize = 11.sp, color = EduGray)
                },
                shape  = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = EduBlue,
                    unfocusedBorderColor = EduBorder,
                    focusedTextColor     = EduTextDark,
                    unfocusedTextColor   = EduTextDark
                )
            )

            HorizontalDivider(color = EduBorder)

            // ── Teaching fields ───────────────────────────────────────────────

            // Languages
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                OutlinedTextField(
                    value         = languagesText,
                    onValueChange = { languagesText = it },
                    label         = { Text("Languages you teach in", fontSize = 13.sp) },
                    placeholder   = {
                        Text("e.g. English, Hindi, Punjabi", color = EduGray, fontSize = 13.sp)
                    },
                    modifier   = Modifier.fillMaxWidth(),
                    shape      = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors     = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = EduBlue,
                        unfocusedBorderColor = EduBorder,
                        focusedTextColor     = EduTextDark,
                        unfocusedTextColor   = EduTextDark
                    )
                )
                Text(
                    text     = "Separate multiple values with commas",
                    color    = EduGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // Boards
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                OutlinedTextField(
                    value         = boardsText,
                    onValueChange = { boardsText = it },
                    label         = { Text("School boards you teach for", fontSize = 13.sp) },
                    placeholder   = {
                        Text("e.g. CBSE, ICSE, State Board", color = EduGray, fontSize = 13.sp)
                    },
                    modifier   = Modifier.fillMaxWidth(),
                    shape      = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors     = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = EduBlue,
                        unfocusedBorderColor = EduBorder,
                        focusedTextColor     = EduTextDark,
                        unfocusedTextColor   = EduTextDark
                    )
                )
                Text(
                    text     = "Separate multiple values with commas",
                    color    = EduGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // Subjects
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                OutlinedTextField(
                    value         = subjectsText,
                    onValueChange = { subjectsText = it },
                    label         = { Text("Subjects you teach", fontSize = 13.sp) },
                    placeholder   = {
                        Text("e.g. Maths, Science, English", color = EduGray, fontSize = 13.sp)
                    },
                    modifier   = Modifier.fillMaxWidth(),
                    shape      = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors     = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = EduBlue,
                        unfocusedBorderColor = EduBorder,
                        focusedTextColor     = EduTextDark,
                        unfocusedTextColor   = EduTextDark
                    )
                )
                Text(
                    text     = "Separate multiple values with commas",
                    color    = EduGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            EduButton(
                text    = if (isSaving) "Saving..." else "Save Changes",
                onClick = {
                    showErrors = true
                    saveChanges()
                },
                enabled = !isSaving
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Image source dialog ───────────────────────────────────────────────────
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text       = "Change Profile Photo",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                    color      = EduTextDark
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Camera
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(EduBlueLight)
                            .clickable {
                                showImageSourceDialog = false
                                launchCamera()
                            }
                            .padding(14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(EduBlue)
                        ) {
                            Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Take a photo",  fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = EduTextDark)
                            Text("Open camera",   fontSize = 12.sp, color = EduGray)
                        }
                    }

                    // Gallery
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(EduBlueLight)
                            .clickable {
                                showImageSourceDialog = false
                                galleryLauncher.launch("image/*")
                            }
                            .padding(14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(EduBlue)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Choose from gallery",   fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = EduTextDark)
                            Text("Pick from your photos", fontSize = 12.sp, color = EduGray)
                        }
                    }

                    // Remove
                    if (imageUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFFEBEE))
                                .clickable {
                                    imageUri = null
                                    showImageSourceDialog = false
                                }
                                .padding(14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier         = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE53935))
                            ) {
                                Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Remove photo",     fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFFE53935))
                                Text("Reset to default", fontSize = 12.sp, color = EduGray)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImageSourceDialog = false }) {
                    Text("Cancel", color = EduGray)
                }
            }
        )
    }
}