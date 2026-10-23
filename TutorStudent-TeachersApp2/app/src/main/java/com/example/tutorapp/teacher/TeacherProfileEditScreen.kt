package com.example.tutorapp.teacher

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.tutorapp.R
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.ui.theme.*
import com.example.tutorapp.viewmodel.TeacherProfileViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherProfileEditScreen(
    navController: NavHostController,
    viewModel: TeacherProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var fullName      by remember { mutableStateOf("") }
    var email         by remember { mutableStateOf("") }
    var bio           by remember { mutableStateOf("") }
    var gender        by remember { mutableStateOf("") }
    var age           by remember { mutableStateOf("") }
    var degree        by remember { mutableStateOf("") }
    var chargePerHour by remember { mutableStateOf("") }
    var experience    by remember { mutableStateOf("") }
    var selectedSubjects  by remember { mutableStateOf(setOf<String>()) }
    var selectedBoards    by remember { mutableStateOf(setOf<String>()) }
    var selectedLanguages by remember { mutableStateOf(setOf<String>()) }
    var profileImageUri   by remember { mutableStateOf<Uri?>(null) }
    var photoFile         by remember { mutableStateOf<File?>(null) }
    var introVideoUri     by remember { mutableStateOf<Uri?>(null) }
    var introVideoFile    by remember { mutableStateOf<File?>(null) }
    var existingVideoUrl  by remember { mutableStateOf<String?>(null) }
    var showImageDialog      by remember { mutableStateOf(false) }
    var showVideoDialog      by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionMsg        by remember { mutableStateOf("") }

    // ── Static option lists ───────────────────────────────────────────────────
    val subjectOptions  = listOf("Maths", "Science", "English", "SST", "Hindi", "Biology", "Physics", "Chemistry", "Computer")
    val boardOptions    = listOf("CBSE", "ICSE", "State Board", "IB", "IGCSE", "A-Level")
    val languageOptions = listOf("English", "Hindi", "Punjabi", "Tamil", "Telugu", "Bengali", "Marathi", "Gujarati")

    // ── Backend list ko UI options se match karo (case-insensitive) ───────────
    fun matchToOptions(backendList: List<String>?, uiOptions: List<String>): Set<String> {
        if (backendList.isNullOrEmpty()) return emptySet()
        return uiOptions.filter { option ->
            backendList.any { it.trim().equals(option.trim(), ignoreCase = true) }
        }.toSet()
    }

    // ── Load profile once ─────────────────────────────────────────────────────
    LaunchedEffect(Unit) { viewModel.loadProfile(context) }

    // ── Populate form when backend data arrives ────────────────────────────────
    LaunchedEffect(uiState.profile) {
        uiState.profile?.let { p ->
            fullName      = p.fullName ?: ""
            email         = p.email    ?: ""
            bio           = p.bio      ?: ""
            gender        = p.gender   ?: ""
            age           = p.age?.toString() ?: ""
            chargePerHour = p.pricing?.hourlyRate?.toString() ?: ""
            experience    = p.experience?.toString() ?: ""
            degree        = p.qualifications?.firstOrNull()?.degree?.trim() ?: ""

            selectedSubjects  = matchToOptions(p.subjects,  subjectOptions)
            selectedBoards    = matchToOptions(p.boards,    boardOptions)
            selectedLanguages = matchToOptions(p.languages, languageOptions)

            existingVideoUrl = p.introVideo?.let {
                if (it.startsWith("http")) it else RetrofitClient.fullUrl(it)
            }

            android.util.Log.d("EditProfile", "===== FORM POPULATED =====")
            android.util.Log.d("EditProfile", "Name=$fullName | Email=$email | Gender=$gender")
            android.util.Log.d("EditProfile", "Age=$age | Exp=$experience | Degree=$degree")
            android.util.Log.d("EditProfile", "Charge=$chargePerHour | Bio=$bio")
            android.util.Log.d("EditProfile", "Backend subjects=${p.subjects}   → selected=$selectedSubjects")
            android.util.Log.d("EditProfile", "Backend boards=${p.boards}       → selected=$selectedBoards")
            android.util.Log.d("EditProfile", "Backend languages=${p.languages} → selected=$selectedLanguages")
            android.util.Log.d("EditProfile", "Video=$existingVideoUrl")
            android.util.Log.d("EditProfile", "==========================")
        }
    }

    LaunchedEffect(uiState.isSaveSuccess) {
        if (uiState.isSaveSuccess) {
            viewModel.resetState()
            navController.navigate("teacher_main") {
                popUpTo("teacher_profile_edit") { inclusive = true }
            }
        }
    }

    // ── Media launchers ───────────────────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            profileImageUri = uri
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { inputStream?.copyTo(it) }
            photoFile = file
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            introVideoUri = uri
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.cacheDir, "intro_video_${System.currentTimeMillis()}.mp4")
            file.outputStream().use { inputStream?.copyTo(it) }
            introVideoFile = file
            android.util.Log.d("EditProfile", "Video selected: ${file.length()} bytes")
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            val out = java.io.FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            out.flush(); out.close()
            profileImageUri = Uri.fromFile(file)
            photoFile = file
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
        else { permissionMsg = "Camera permission required."; showPermissionDialog = true }
    }

    val galleryPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) galleryLauncher.launch("image/*")
        else { permissionMsg = "Storage permission required."; showPermissionDialog = true }
    }

    // ── Permission Dialog ─────────────────────────────────────────────────────
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permission Required", fontWeight = FontWeight.Bold) },
            text  = { Text(permissionMsg, fontSize = 13.sp, color = TextGray) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                }) { Text("Open Settings", color = PrimaryBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("Cancel", color = TextGray) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = CardWhite
        )
    }

    // ── Image Source Dialog ───────────────────────────────────────────────────
    if (showImageDialog) {
        AlertDialog(
            onDismissRequest = { showImageDialog = false },
            title = { Text("Change Profile Photo", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showImageDialog = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                    galleryPermLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                                else
                                    galleryPermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.gallery),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Choose from Gallery", fontSize = 14.sp, color = TextPrimary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showImageDialog = false
                                cameraPermLauncher.launch(Manifest.permission.CAMERA)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.cameras),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Take a Photo", fontSize = 14.sp, color = TextPrimary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { profileImageUri = null; photoFile = null; showImageDialog = false }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Delete Photo", fontSize = 14.sp, color = Color(0xFFE53935))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImageDialog = false }) { Text("Cancel", color = TextGray) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = CardWhite
        )
    }

    // ── Video Source Dialog ───────────────────────────────────────────────────
    if (showVideoDialog) {
        AlertDialog(
            onDismissRequest = { showVideoDialog = false },
            title = { Text("Intro Video", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showVideoDialog = false; videoLauncher.launch("video/*") }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = EduBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Choose Video", fontSize = 14.sp, color = TextPrimary)
                    }
                    if (introVideoUri != null || existingVideoUrl != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    introVideoUri = null; introVideoFile = null
                                    existingVideoUrl = null; showVideoDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Remove Video", fontSize = 14.sp, color = Color(0xFFE53935))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showVideoDialog = false }) { Text("Cancel", color = TextGray) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = CardWhite
        )
    }

    // ── Main UI ───────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .verticalScroll(rememberScrollState())
    ) {

        // Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
            }
            Text(
                "Edit Profile",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (uiState.isLoading) LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = PrimaryBlue
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Profile Photo ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0))
                    .border(2.dp, PrimaryBlue, CircleShape)
                    .clickable { showImageDialog = true },
                contentAlignment = Alignment.Center
            ) {
                when {
                    profileImageUri != null -> AsyncImage(
                        model = profileImageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    uiState.profile?.profilePhoto != null -> AsyncImage(
                        model = RetrofitClient.fullUrl(uiState.profile!!.profilePhoto!!),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    else -> Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(96.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue)
                    .clickable { showImageDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Change Photo",
            fontSize = 12.sp,
            color = PrimaryBlue,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { showImageDialog = true }
        )
        Spacer(modifier = Modifier.height(28.dp))

        // ── Intro Video ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE0E0E0))
                        .border(2.dp, PrimaryBlue, RoundedCornerShape(12.dp))
                        .clickable { showVideoDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        introVideoUri != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("New video selected", fontSize = 10.sp, color = Color(0xFF4CAF50))
                        }
                        existingVideoUrl != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = EduBlue, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Video exists", fontSize = 10.sp, color = EduBlue)
                        }
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("No video", fontSize = 10.sp, color = TextGray)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Intro Video (Click to change)",
                    fontSize = 12.sp,
                    color = PrimaryBlue,
                    modifier = Modifier.clickable { showVideoDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // Full Name
            Text("Full Name", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = Color(0xFFDDDDDD))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Email
            Text("Email", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = Color(0xFFDDDDDD))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Gender
            Text("Gender", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            val genderOptions = listOf("Male", "Female", "Other", "Prefer not to say")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genderOptions.forEach { g ->
                    val isSelected = gender == g
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) PrimaryBlue else Color.White)
                            .border(1.dp, if (isSelected) PrimaryBlue else Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                            .clickable { gender = g }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            g,
                            fontSize = 11.sp,
                            color = if (isSelected) Color.White else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Age — manual text input ───────────────────────────────────────
            Text("Age", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = age,
                onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) },
                placeholder = { Text("Enter your age", fontSize = 13.sp, color = TextGray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = Color(0xFFDDDDDD))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Degree — manual text input ────────────────────────────────────
            Text("Degree", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = degree,
                onValueChange = { degree = it },
                placeholder = { Text("e.g. B.Ed, M.Sc, Ph.D", fontSize = 13.sp, color = TextGray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = Color(0xFFDDDDDD))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Charge per hour
            Text("Charge per hour (₹)", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = chargePerHour,
                onValueChange = { chargePerHour = it.filter { c -> c.isDigit() } },
                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = Color(0xFFDDDDDD))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Experience — manual text input ────────────────────────────────
            Text("Experience (years)", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = experience,
                onValueChange = { experience = it.filter { c -> c.isDigit() }.take(2) },
                placeholder = { Text("e.g. 5", fontSize = 13.sp, color = TextGray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = Color(0xFFDDDDDD))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bio
            Text("Bio", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 300) bio = it },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                placeholder = { Text("Tell students about yourself...", fontSize = 12.sp, color = TextGray) },
                maxLines = 4,
                supportingText = { Text("${bio.length}/300", fontSize = 11.sp, color = TextGray) },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = Color(0xFFDDDDDD))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Subjects ──────────────────────────────────────────────────────
            Text("Subjects", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subjectOptions.size) { i ->
                    val subject = subjectOptions[i]
                    val selected = selectedSubjects.contains(subject)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) PrimaryBlue else Color.White)
                            .border(1.dp, if (selected) PrimaryBlue else Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                            .clickable { selectedSubjects = if (selected) selectedSubjects - subject else selectedSubjects + subject }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(subject, fontSize = 12.sp, color = if (selected) Color.White else TextPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Boards ────────────────────────────────────────────────────────
            Text("Boards", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(boardOptions.size) { i ->
                    val board = boardOptions[i]
                    val selected = selectedBoards.contains(board)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) PrimaryBlue else Color.White)
                            .border(1.dp, if (selected) PrimaryBlue else Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                            .clickable { selectedBoards = if (selected) selectedBoards - board else selectedBoards + board }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(board, fontSize = 12.sp, color = if (selected) Color.White else TextPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Languages ─────────────────────────────────────────────────────
            Text("Languages", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(languageOptions.size) { i ->
                    val lang = languageOptions[i]
                    val selected = selectedLanguages.contains(lang)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) PrimaryBlue else Color.White)
                            .border(1.dp, if (selected) PrimaryBlue else Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                            .clickable { selectedLanguages = if (selected) selectedLanguages - lang else selectedLanguages + lang }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(lang, fontSize = 12.sp, color = if (selected) Color.White else TextPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error message
            uiState.errorMessage?.let {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color(0xFFFFEBEE)) {
                    Text("⚠ $it", color = Color(0xFFD32F2F), fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save Button
            Button(
                onClick = {
                    android.util.Log.d("EditProfile", "Saving → subjects=$selectedSubjects boards=$selectedBoards languages=$selectedLanguages")
                    viewModel.updateProfile(
                        context        = context,
                        fullName       = fullName,
                        email          = email,
                        bio            = bio,
                        gender         = gender,
                        age            = age.toIntOrNull() ?: 0,
                        degree         = degree,
                        chargePerHour  = chargePerHour.toDoubleOrNull() ?: 0.0,
                        experience     = experience.toIntOrNull() ?: 0,
                        subjects       = selectedSubjects.toList(),
                        boards         = selectedBoards.toList(),
                        languages      = selectedLanguages.toList(),
                        photoFile      = photoFile,
                        introVideoFile = introVideoFile
                    )
                },
                enabled  = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (uiState.isLoading)
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else
                    Text("Save Changes", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}