package com.example.tutorapp.teacher

import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.tutorapp.SessionViewModel
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TeacherProfileData
import com.example.tutorapp.ui.theme.*

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ProfileScreenPreview() {
    ProfileScreen(navController = rememberNavController())
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController:    NavController,
    sessionViewModel: SessionViewModel? = null
) {
    val context   = LocalContext.current
    val isPreview = LocalInspectionMode.current

    var isFetchingProfile by remember { mutableStateOf(true) }
    var profileError      by remember { mutableStateOf<String?>(null) }

    if (!isPreview) {
        LaunchedEffect(Unit) {
            try {
                sessionViewModel?.loadTeacherProfile(
                    onSuccess = { isFetchingProfile = false },
                    onError   = { error ->
                        profileError      = error
                        isFetchingProfile = false
                    }
                )
            } catch (e: Exception) {
                profileError      = e.message
                isFetchingProfile = false
            }
        }
    } else {
        isFetchingProfile = false
    }

    val profilePrefs = context.getSharedPreferences("teacher_profile", android.content.Context.MODE_PRIVATE)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val teacherProfile by sessionViewModel?.teacherProfile?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<TeacherProfileData?>(null) }

    LaunchedEffect(teacherProfile) {
        teacherProfile?.let { p ->
            android.util.Log.d("ProfileScreen", "fullName: '${p.fullName}'")
            android.util.Log.d("ProfileScreen", "subjects: ${p.subjects}")
        }
    }

    val fullName      = teacherProfile?.fullName       ?: remember(navBackStackEntry) { profilePrefs.getString("fullName",      "") ?: "" }
    val email         = teacherProfile?.email          ?: remember(navBackStackEntry) { profilePrefs.getString("email",         "") ?: "" }
    val gender        = teacherProfile?.gender         ?: remember(navBackStackEntry) { profilePrefs.getString("gender",        "") ?: "" }
    val age           = teacherProfile?.age?.toString()?: remember(navBackStackEntry) { profilePrefs.getString("age",           "") ?: "" }
    val chargePerHour = teacherProfile?.pricing?.hourlyRate?.toString() ?: remember(navBackStackEntry) { profilePrefs.getString("chargePerHour", "") ?: "" }
    val degree        = teacherProfile?.qualifications?.firstOrNull()?.degree ?: remember(navBackStackEntry) { profilePrefs.getString("degree", "") ?: "" }
    val bio           = teacherProfile?.bio            ?: remember(navBackStackEntry) { profilePrefs.getString("bio",           "") ?: "" }
    val experience    = teacherProfile?.experience?.toString() ?: remember(navBackStackEntry) { profilePrefs.getString("experience", "") ?: "" }
    val subjects      = teacherProfile?.subjects?.joinToString(",")  ?: remember(navBackStackEntry) { profilePrefs.getString("subjects",  "") ?: "" }
    val boards        = teacherProfile?.boards?.joinToString(",")    ?: remember(navBackStackEntry) { profilePrefs.getString("boards",    "") ?: "" }
    val languages     = teacherProfile?.languages?.joinToString(",") ?: remember(navBackStackEntry) { profilePrefs.getString("languages", "") ?: "" }
    val profilePhoto  = RetrofitClient.fullUrl(teacherProfile?.profilePhoto) ?: remember(navBackStackEntry) { profilePrefs.getString("profilePhoto", "") ?: "" }

    val boardsList    = boards.split(",").filter    { it.isNotBlank() }
    val subjectsList  = subjects.split(",").filter  { it.isNotBlank() }
    val languagesList = languages.split(",").filter { it.isNotBlank() }

    val initials = fullName.trim().split(" ")
        .filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it[0].uppercaseChar().toString() }
        .ifEmpty { "?" }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                title  = { Text("My Profile", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = EduBlue)
            )
        },
        containerColor = EduBackground
    ) { innerPadding ->

        if (isFetchingProfile) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            return@Scaffold
        }

        if (profileError != null && teacherProfile == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️ Failed to load profile", color = Color.Red, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(profileError ?: "Unknown error", color = EduGray, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        profileError      = null
                        isFetchingProfile = true
                        sessionViewModel?.loadTeacherProfile(
                            onSuccess = { isFetchingProfile = false },
                            onError   = { error -> profileError = error; isFetchingProfile = false }
                        )
                    }) { Text("Retry") }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Hero banner ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EduBlue)
                    .padding(bottom = 28.dp)
            ) {
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (profilePhoto.isNotEmpty()) {
                        AsyncImage(
                            model              = profilePhoto,
                            contentDescription = "Profile photo",
                            modifier           = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .border(3.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                            contentScale       = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                                .border(3.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Text(initials, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        fullName.ifEmpty { "Your Name" },
                        color      = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 22.sp
                    )
                    if (degree.isNotEmpty())
                        Text(degree, color = Color.White.copy(alpha = 0.80f), fontSize = 13.sp)
                    if (email.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            email,
                            color     = Color.White.copy(alpha = 0.70f),
                            fontSize  = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    // Stats row (Students / Sessions / Reviews / Ranking) removed
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-16).dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(EduBackground)
                    .height(16.dp)
            )

            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-8).dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── About Me ──────────────────────────────────────────────────
                if (bio.isNotEmpty()) {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("About Me", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = EduTextDark)
                            Spacer(Modifier.height(6.dp))
                            Text(bio, fontSize = 13.sp, color = EduGray, lineHeight = 20.sp)
                        }
                    }
                }

                // ── Intro Video with actual VideoView ─────────────────────────
                val introVideoUrl = RetrofitClient.fullUrl(teacherProfile?.introVideo)
                if (!introVideoUrl.isNullOrEmpty()) {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Intro Video",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize   = 15.sp,
                                    color      = EduTextDark
                                )
                                Icon(
                                    Icons.Filled.PlayCircle,
                                    contentDescription = null,
                                    tint               = EduBlue,
                                    modifier           = Modifier.size(24.dp)
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            var isVideoReady  by remember { mutableStateOf(false) }
                            var hasVideoError by remember { mutableStateOf(false) }
                            var isPlaying     by remember { mutableStateOf(false) }
                            var videoViewRef  by remember { mutableStateOf<VideoView?>(null) }

                            Box(
                                modifier         = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            val mc = MediaController(ctx)
                                            mc.setAnchorView(this)
                                            setMediaController(mc)
                                            setVideoPath(introVideoUrl)
                                            setOnPreparedListener { mp ->
                                                isVideoReady = true
                                                mp.isLooping  = false
                                            }
                                            setOnErrorListener { _, _, _ ->
                                                hasVideoError = true
                                                true
                                            }
                                            setOnCompletionListener {
                                                isPlaying = false
                                            }
                                            videoViewRef = this
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    update   = { vv -> videoViewRef = vv }
                                )

                                DisposableEffect(Unit) {
                                    onDispose { videoViewRef?.stopPlayback() }
                                }

                                // Loading
                                if (!isVideoReady && !hasVideoError) {
                                    CircularProgressIndicator(
                                        color       = Color.White,
                                        modifier    = Modifier.size(40.dp),
                                        strokeWidth = 3.dp
                                    )
                                }

                                // Error
                                if (hasVideoError) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint     = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Video load nahi hua",
                                            color    = Color.White.copy(alpha = 0.8f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                // Play button
                                if (isVideoReady && !isPlaying) {
                                    Surface(
                                        shape    = CircleShape,
                                        color    = EduBlue.copy(alpha = 0.90f),
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clickable {
                                                videoViewRef?.start()
                                                isPlaying = true
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Filled.PlayArrow,
                                                contentDescription = "Play video",
                                                tint               = Color.White,
                                                modifier           = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Students can see your intro video",
                                fontSize = 11.sp,
                                color    = EduGray
                            )
                        }
                    }
                }

                // ── Teaching Details ──────────────────────────────────────────
                ProfileInfoCard(title = "Teaching Details") {
                    ProfileInfoRow(icon = Icons.Filled.School,      label = "Degree",     value = degree.ifEmpty { "—" },      isEmpty = degree.isEmpty())
                    ProfileInfoRow(icon = Icons.Filled.AttachMoney, label = "Charge/hr",  value = if (chargePerHour.isNotEmpty()) "₹$chargePerHour" else "—", isEmpty = chargePerHour.isEmpty())
                    ProfileInfoRow(icon = Icons.Filled.WorkHistory, label = "Experience", value = experience.ifEmpty { "—" },   isEmpty = experience.isEmpty())
                    if (languagesList.isNotEmpty()) { Spacer(Modifier.height(6.dp)); ProfileChipRow(icon = Icons.Filled.Language,       label = "Languages", items = languagesList) }
                    else ProfileInfoRow(icon = Icons.Filled.Language,       label = "Languages", value = "—", isEmpty = true)
                    if (boardsList.isNotEmpty())    { Spacer(Modifier.height(6.dp)); ProfileChipRow(icon = Icons.Filled.AccountBalance,  label = "Boards",    items = boardsList) }
                    else ProfileInfoRow(icon = Icons.Filled.AccountBalance,  label = "Boards",    value = "—", isEmpty = true)
                    if (subjectsList.isNotEmpty())  { Spacer(Modifier.height(6.dp)); ProfileChipRow(icon = Icons.Filled.MenuBook,        label = "Subjects",  items = subjectsList) }
                    else ProfileInfoRow(icon = Icons.Filled.MenuBook,        label = "Subjects",  value = "—", isEmpty = true)
                }

                // ── Personal Details ──────────────────────────────────────────
                ProfileInfoCard(title = "Personal Details") {
                    ProfileInfoRow(icon = Icons.Filled.Person, label = "Gender", value = gender.ifEmpty { "—" }, isEmpty = gender.isEmpty())
                    ProfileInfoRow(icon = Icons.Filled.Cake,   label = "Age",    value = age.ifEmpty { "—" },    isEmpty = age.isEmpty())
                    ProfileInfoRow(icon = Icons.Filled.Email,  label = "Email",  value = email.ifEmpty { "—" },  isEmpty = email.isEmpty())
                }

                EduButton(
                    text     = "Edit Profile",
                    onClick  = { navController.navigate("teacher_profile_edit") },
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProfileChipRow(icon: ImageVector, label: String, items: List<String>) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = EduBlue, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = label, fontSize = 13.sp, color = EduGray, modifier = Modifier.width(80.dp))
        FlowRow(
            modifier              = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement   = Arrangement.spacedBy(6.dp)
        ) {
            items.forEach { item ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(EduBlueLight)
                        .border(1.dp, EduBlue.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(item, fontSize = 12.sp, color = EduBlue, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = EduTextDark)
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = EduBorder)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ProfileInfoRow(icon: ImageVector, label: String, value: String, isEmpty: Boolean = false) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (isEmpty) EduGray.copy(alpha = 0.5f) else EduBlue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = label, fontSize = 13.sp, color = EduGray, modifier = Modifier.width(80.dp))
        Text(
            text       = value,
            fontSize   = 13.sp,
            fontWeight = if (isEmpty) FontWeight.Normal else FontWeight.Medium,
            color      = if (isEmpty) EduGray.copy(alpha = 0.5f) else EduTextDark,
            modifier   = Modifier.weight(1f)
        )
    }
}