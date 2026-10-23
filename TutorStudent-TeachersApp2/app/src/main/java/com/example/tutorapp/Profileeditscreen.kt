package com.example.tutorapp

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
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.ui.theme.*
import com.example.tutorapp.viewmodel.ProfileViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(navController: NavHostController) {

    val viewModel: ProfileViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var fullName             by remember { mutableStateOf("") }
    var gradeLevel           by remember { mutableStateOf("") }
    var selectedGoals        by remember { mutableStateOf(setOf<String>()) }
    var selectedSubjects     by remember { mutableStateOf(setOf<String>()) }
    var profileImageUri      by remember { mutableStateOf<Uri?>(null) }
    var photoFile            by remember { mutableStateOf<File?>(null) }
    var showImageDialog      by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionMsg        by remember { mutableStateOf("") }

    val gradeOptions   = listOf("6th Grade","7th Grade","8th Grade","9th Grade","10th Grade","11th Grade","12th Grade")
    val goalOptions    = listOf("JEE","NEET","CBSE","ICSE","State Board")
    val subjectOptions = listOf("Maths","Science","English","SST","Hindi","Biology","Physics")

    LaunchedEffect(Unit) { viewModel.loadProfile(context) }

    LaunchedEffect(uiState.profile) {
        uiState.profile?.let { p ->
            fullName         = p.fullName   ?: ""
            gradeLevel       = p.gradeLevel ?: ""
            selectedGoals    = p.goals?.map { it.title }?.toSet() ?: setOf()
            selectedSubjects = p.subjects?.map { it.title }?.toSet() ?: setOf()
        }
    }

    LaunchedEffect(uiState.isSaveSuccess) {
        if (uiState.isSaveSuccess) {
            viewModel.resetState()
            navController.navigate("SettingsScreen") {
                popUpTo("ProfileEditScreen") { inclusive = true }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            profileImageUri = uri
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { inputStream?.copyTo(it) }
            photoFile = file
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            val out  = java.io.FileOutputStream(file)
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
            dismissButton = { TextButton(onClick = { showPermissionDialog = false }) { Text("Cancel", color = TextGray) } },
            shape = RoundedCornerShape(16.dp), containerColor = CardWhite
        )
    }

    if (showImageDialog) {
        AlertDialog(
            onDismissRequest = { showImageDialog = false },
            title = { Text("Change Profile Photo", fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        showImageDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            galleryPermLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                        else galleryPermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(id = R.drawable.gallery), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Choose from Gallery", fontSize = 14.sp, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        showImageDialog = false
                        cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(id = R.drawable.cameras), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Take a Photo", fontSize = 14.sp, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        profileImageUri = null; photoFile = null; showImageDialog = false
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Delete Photo", fontSize = 14.sp, color = Color(0xFFE53935))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showImageDialog = false }) { Text("Cancel", color = TextGray) } },
            shape = RoundedCornerShape(16.dp), containerColor = CardWhite
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(LightBg).verticalScroll(rememberScrollState())) {

        Box(modifier = Modifier.fillMaxWidth().background(CardWhite).padding(horizontal = 4.dp, vertical = 8.dp)) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
            }
            Text("Edit Profile", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.align(Alignment.Center))
        }

        if (uiState.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PrimaryBlue)

        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.align(Alignment.CenterHorizontally), contentAlignment = Alignment.BottomEnd) {
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
                    // ✅ FIX 1: Locally picked image (gallery/camera) - seedha Uri use karo
                    profileImageUri != null -> AsyncImage(
                        model = profileImageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    // ✅ FIX 2: Backend se aayi photo - fullUrl() se complete URL banao
                    uiState.profile?.profilePhoto != null -> AsyncImage(
                        model = RetrofitClient.fullUrl(uiState.profile!!.profilePhoto),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    // Koi photo nahi - default icon dikhao
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
        Text("Change Photo", fontSize = 12.sp, color = PrimaryBlue, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { showImageDialog = true })
        Spacer(modifier = Modifier.height(28.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            Text("Full Name", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = fullName, onValueChange = { fullName = it },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = Color(0xFFDDDDDD))
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Grade Level", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                items(gradeOptions.size) { i ->
                    val grade    = gradeOptions[i]
                    val selected = gradeLevel == grade
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (selected) PrimaryBlue else Color.White)
                            .border(1.dp, if (selected) PrimaryBlue else Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                            .clickable { gradeLevel = grade }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(grade, fontSize = 12.sp, color = if (selected) Color.White else TextPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Subjects", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                items(subjectOptions.size) { i ->
                    val subject  = subjectOptions[i]
                    val selected = selectedSubjects.contains(subject)
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
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

            Text("Goals", fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                items(goalOptions.size) { i ->
                    val goal     = goalOptions[i]
                    val selected = selectedGoals.contains(goal)
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (selected) PrimaryBlue else Color.White)
                            .border(1.dp, if (selected) PrimaryBlue else Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                            .clickable { selectedGoals = if (selected) selectedGoals - goal else selectedGoals + goal }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(goal, fontSize = 12.sp, color = if (selected) Color.White else TextPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier  = Modifier.fillMaxWidth().clickable { navController.navigate("ResetCredentialsScreen") },
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                elevation = CardDefaults.cardElevation(0.dp),
                border    = androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlue)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(PrimaryBlue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reset Email & Password", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                        Text("Update your login credentials", fontSize = 11.sp, color = TextGray)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                }
            }

            uiState.errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text("⚠ $it", fontSize = 12.sp, color = Color.Red)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    viewModel.updateProfile(
                        context    = context,
                        fullName   = fullName,
                        gradeLevel = gradeLevel,
                        goals      = selectedGoals.toList(),
                        subjects   = selectedSubjects.toList(),
                        photoFile  = photoFile
                    )
                },
                enabled  = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (uiState.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Save Changes", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}