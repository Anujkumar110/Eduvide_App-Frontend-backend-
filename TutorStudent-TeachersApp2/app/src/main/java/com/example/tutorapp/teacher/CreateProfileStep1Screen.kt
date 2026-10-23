package com.example.tutorapp.teacher

import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TeacherProfileRequest
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CreateProfileStep1Preview() {
    val context = LocalContext.current
    CompleteProfileStep1Screen(
        navController = rememberNavController(),
        prefs         = context.getSharedPreferences(
            "teacher_profile",
            android.content.Context.MODE_PRIVATE
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteProfileStep1Screen(
    navController: NavController,
    prefs:         SharedPreferences
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Pre-fill from SharedPreferences ───────────────────────────────────────
    var fullName      by remember { mutableStateOf(prefs.getString("fullName",      "") ?: "") }
    var email         by remember { mutableStateOf(prefs.getString("email",         "") ?: "") }
    var gender        by remember { mutableStateOf(prefs.getString("gender",        "") ?: "") }
    var age           by remember { mutableStateOf(prefs.getString("age",           "") ?: "") }
    var chargePerHour by remember { mutableStateOf(prefs.getString("chargePerHour", "") ?: "") }
    var degree        by remember { mutableStateOf(prefs.getString("degree",        "") ?: "") }
    var bio           by remember { mutableStateOf(prefs.getString("bio",           "") ?: "") }
    var experience    by remember { mutableStateOf(prefs.getString("experience",    "") ?: "") }

    // ── Multi-select options for language, board, subject ─────────────────────
    var selectedLanguages by remember { mutableStateOf(setOf<String>()) }
    var selectedBoards by remember { mutableStateOf(setOf<String>()) }
    var selectedSubjects by remember { mutableStateOf(setOf<String>()) }

    val languageOptions = listOf("English", "Hindi", "Punjabi", "Tamil", "Telugu", "Bengali", "Marathi", "Gujarati", "Urdu", "Odia", "Kannada", "Malayalam")
    val boardOptions = listOf("CBSE", "ICSE", "State Board", "IB", "IGCSE", "A-Level")
    val subjectOptions = listOf("Maths", "Science", "English", "SST", "Hindi", "Biology", "Physics", "Chemistry", "Computer", "Economics", "History", "Geography", "Accounts", "Business Studies")

    // ── Pre-fill from SharedPreferences (for languages, boards, subjects) ────
    LaunchedEffect(Unit) {
        val savedLanguages = prefs.getString("languages", "")
            ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        val savedBoards = prefs.getString("boards", "")
            ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        val savedSubjects = prefs.getString("subjects", "")
            ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

        if (savedLanguages.isNotEmpty()) selectedLanguages = savedLanguages
        if (savedBoards.isNotEmpty()) selectedBoards = savedBoards
        if (savedSubjects.isNotEmpty()) selectedSubjects = savedSubjects
    }

    var showErrors by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var apiError by remember { mutableStateOf<String?>(null) }

    // ── Options ───────────────────────────────────────────────────────────────
    val genderOptions = listOf("Male", "Female", "Other", "Prefer not to say")

    // ── Validation ────────────────────────────────────────────────────────────
    fun isValidEmail(e: String) =
        android.util.Patterns.EMAIL_ADDRESS.matcher(e.trim()).matches()

    val fullNameError   = showErrors && fullName.isBlank()
    val emailError      = showErrors && !isValidEmail(email)
    val emailErrorMsg   = if (email.isEmpty()) "Email is required" else "Enter a valid email"
    val genderError     = showErrors && gender.isEmpty()
    val ageError        = showErrors && age.isBlank()
    val chargeError     = showErrors && chargePerHour.isBlank()
    val degreeError     = showErrors && degree.isBlank()
    val experienceError = showErrors && experience.isBlank()
    val languageError   = showErrors && selectedLanguages.isEmpty()
    val boardError      = showErrors && selectedBoards.isEmpty()
    val subjectError    = showErrors && selectedSubjects.isEmpty()

    fun validate() =
        fullName.isNotBlank()          &&
                isValidEmail(email)            &&
                gender.isNotEmpty()            &&
                age.isNotBlank()               &&
                chargePerHour.isNotBlank()     &&
                degree.isNotBlank()            &&
                experience.isNotBlank()        &&
                selectedLanguages.isNotEmpty() &&
                selectedBoards.isNotEmpty()    &&
                selectedSubjects.isNotEmpty()

    // ── Save to SharedPreferences and Backend ─────────────────────────────────
    fun saveProfile() {
        val cleanLanguages = selectedLanguages.joinToString(",")
        val cleanBoards    = selectedBoards.joinToString(",")
        val cleanSubjects  = selectedSubjects.joinToString(",")

        prefs.edit()
            .putString("fullName",      fullName.trim())
            .putString("email",         email.trim())
            .putString("gender",        gender)
            .putString("age",           age.trim())
            .putString("chargePerHour", chargePerHour.trim())
            .putString("degree",        degree.trim())
            .putString("bio",           bio.trim())
            .putString("experience",    experience.trim())
            .putString("languages",     cleanLanguages)
            .putString("boards",        cleanBoards)
            .putString("subjects",      cleanSubjects)
            .apply()

        scope.launch {
            isLoading = true
            apiError = null
            try {
                val token = TokenManager.getBearerToken(context)
                if (token.isBlank()) {
                    apiError = "Session expired. Please login again."
                    isLoading = false
                    return@launch
                }

                val request = TeacherProfileRequest(
                    fullName      = fullName.trim(),
                    email         = email.trim(),
                    gender        = gender,
                    age           = age.trim().toIntOrNull() ?: 0,
                    chargePerHour = chargePerHour.trim().toIntOrNull() ?: 0,
                    degree        = degree.trim(),
                    bio           = bio.trim(),
                    experience    = experience.trim().filter { it.isDigit() }.toIntOrNull() ?: 0,
                    subjects      = cleanSubjects.split(",").filter { it.isNotEmpty() },
                    boards        = cleanBoards.split(",").filter { it.isNotEmpty() },
                    languages     = cleanLanguages.split(",").filter { it.isNotEmpty() }
                )

                val response = RetrofitClient.api.createTeacherProfile(token, request)

                android.util.Log.d("Step1", "API Response: code=${response.code()}, body=${response.body()?.success}")

                if (response.isSuccessful || response.code() == 409) {
                    isLoading = false
                    navController.navigate(Routes.COMPLETE_PROFILE_2)
                    return@launch
                }

                val errorBody = response.errorBody()?.string()
                android.util.Log.e("Step1", "Error: code=${response.code()}, body=$errorBody")

                android.util.Log.d("Step1", "Retrying with empty arrays for subjects/boards/languages")

                val fallbackRequest = TeacherProfileRequest(
                    fullName      = fullName.trim(),
                    email         = email.trim(),
                    gender        = gender,
                    age           = age.trim().toIntOrNull() ?: 0,
                    chargePerHour = chargePerHour.trim().toIntOrNull() ?: 0,
                    degree        = degree.trim(),
                    bio           = bio.trim(),
                    experience    = experience.trim().filter { it.isDigit() }.toIntOrNull() ?: 0,
                    subjects      = emptyList(),
                    boards        = emptyList(),
                    languages     = emptyList()
                )

                val fallbackResponse = RetrofitClient.api.createTeacherProfile(token, fallbackRequest)

                android.util.Log.d("Step1", "Fallback response: code=${fallbackResponse.code()}")

                if (fallbackResponse.isSuccessful || fallbackResponse.code() == 409) {
                    android.util.Log.d("Step1", "✅ Fallback success")
                    isLoading = false
                    navController.navigate(Routes.COMPLETE_PROFILE_2)
                } else {
                    val fallbackError = fallbackResponse.errorBody()?.string()
                    android.util.Log.e("Step1", "❌ Fallback failed: $fallbackError")
                    apiError = "Profile save failed (${fallbackResponse.code()})"
                    isLoading = false
                }

            } catch (e: Exception) {
                android.util.Log.e("Step1", "Exception: ${e.message}", e)
                apiError = "Error: ${e.message}"
                isLoading = false
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text       = "Complete your profile",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            color      = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        StepIndicator(currentStep = 1, totalSteps = 2)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = EduBlue
                )
            )
        },
        containerColor = EduBackground
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text(
                text       = "Tell us about yourself:",
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color      = EduTextDark
            )

            EduTextField(
                value         = fullName,
                onValueChange = { fullName = it },
                label         = "Full Name",
                isError       = fullNameError,
                errorMessage  = "Full name is required"
            )

            EduTextField(
                value         = email,
                onValueChange = { email = it },
                label         = "Personal Email",
                isError       = emailError,
                errorMessage  = emailErrorMsg
            )

            EduDropdown(
                label           = "Gender",
                selectedValue   = gender,
                options         = genderOptions,
                onValueSelected = { gender = it },
                isError         = genderError,
                errorMessage    = "Please select your gender"
            )

            // ── Age — manual text input ───────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Age *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = EduTextDark)
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) },
                    placeholder = { Text("Enter your age", fontSize = 13.sp, color = EduGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    isError = ageError,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = EduBlue,
                        unfocusedBorderColor = EduBorder,
                        focusedTextColor     = EduTextDark,
                        unfocusedTextColor   = EduTextDark
                    )
                )
                if (ageError) Text("Please enter your age", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }

            EduTextField(
                value         = chargePerHour,
                onValueChange = { chargePerHour = it.filter { c -> c.isDigit() } },
                label         = "Charge per hour (₹)",
                isError       = chargeError,
                errorMessage  = "Please enter your hourly charge"
            )

            // ── Degree — manual text input ────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Most recent degree *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = EduTextDark)
                OutlinedTextField(
                    value = degree,
                    onValueChange = { degree = it },
                    placeholder = { Text("e.g. B.Ed, M.Sc, Ph.D", fontSize = 13.sp, color = EduGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    isError = degreeError,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = EduBlue,
                        unfocusedBorderColor = EduBorder,
                        focusedTextColor     = EduTextDark,
                        unfocusedTextColor   = EduTextDark
                    )
                )
                if (degreeError) Text("Please enter your degree", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }

            // Bio
            OutlinedTextField(
                value         = bio,
                onValueChange = { if (it.length <= 300) bio = it },
                label         = { Text("Short Bio (optional)", fontSize = 13.sp) },
                placeholder   = { Text("Tell students about yourself...", color = EduGray, fontSize = 13.sp) },
                modifier      = Modifier.fillMaxWidth().height(110.dp),
                maxLines      = 5,
                supportingText = { Text("${bio.length}/300", fontSize = 11.sp, color = EduGray) },
                shape  = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = EduBlue,
                    unfocusedBorderColor = EduBorder,
                    focusedTextColor     = EduTextDark,
                    unfocusedTextColor   = EduTextDark
                )
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = EduBorder)

            Text(
                text       = "Teaching details:",
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color      = EduTextDark
            )

            // ── Languages ─────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Languages you teach in *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = EduTextDark)
                if (languageError) Text("Please select at least one language", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                if (selectedLanguages.isNotEmpty()) {
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedLanguages.forEach { lang ->
                            Surface(shape = RoundedCornerShape(16.dp), color = EduBlue.copy(alpha = 0.1f), border = BorderStroke(1.dp, EduBlue)) {
                                Row(modifier = Modifier.clickable { selectedLanguages = selectedLanguages - lang }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(lang, fontSize = 12.sp, color = EduBlue, fontWeight = FontWeight.Medium)
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = EduBlue, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    languageOptions.chunked(3).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowItems.forEach { lang ->
                                val isSelected = lang in selectedLanguages
                                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), color = if (isSelected) EduBlue else Color.White, border = BorderStroke(1.dp, if (isSelected) EduBlue else EduBorder)) {
                                    Box(modifier = Modifier.clickable { selectedLanguages = if (isSelected) selectedLanguages - lang else selectedLanguages + lang }.padding(vertical = 10.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(lang, fontSize = 11.sp, color = if (isSelected) Color.White else EduTextDark, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Boards ────────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("School boards you teach for *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = EduTextDark)
                if (boardError) Text("Please select at least one board", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                if (selectedBoards.isNotEmpty()) {
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedBoards.forEach { board ->
                            Surface(shape = RoundedCornerShape(16.dp), color = EduBlue.copy(alpha = 0.1f), border = BorderStroke(1.dp, EduBlue)) {
                                Row(modifier = Modifier.clickable { selectedBoards = selectedBoards - board }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(board, fontSize = 12.sp, color = EduBlue, fontWeight = FontWeight.Medium)
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = EduBlue, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    boardOptions.chunked(3).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowItems.forEach { board ->
                                val isSelected = board in selectedBoards
                                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), color = if (isSelected) EduBlue else Color.White, border = BorderStroke(1.dp, if (isSelected) EduBlue else EduBorder)) {
                                    Box(modifier = Modifier.clickable { selectedBoards = if (isSelected) selectedBoards - board else selectedBoards + board }.padding(vertical = 10.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(board, fontSize = 11.sp, color = if (isSelected) Color.White else EduTextDark, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Subjects ──────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Subjects you teach *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = EduTextDark)
                if (subjectError) Text("Please select at least one subject", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                if (selectedSubjects.isNotEmpty()) {
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedSubjects.forEach { subject ->
                            Surface(shape = RoundedCornerShape(16.dp), color = EduBlue.copy(alpha = 0.1f), border = BorderStroke(1.dp, EduBlue)) {
                                Row(modifier = Modifier.clickable { selectedSubjects = selectedSubjects - subject }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(subject, fontSize = 12.sp, color = EduBlue, fontWeight = FontWeight.Medium)
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = EduBlue, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    subjectOptions.chunked(3).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowItems.forEach { subject ->
                                val isSelected = subject in selectedSubjects
                                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), color = if (isSelected) EduBlue else Color.White, border = BorderStroke(1.dp, if (isSelected) EduBlue else EduBorder)) {
                                    Box(modifier = Modifier.clickable { selectedSubjects = if (isSelected) selectedSubjects - subject else selectedSubjects + subject }.padding(vertical = 10.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(subject, fontSize = 11.sp, color = if (isSelected) Color.White else EduTextDark, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Experience — manual text input ────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Total teaching experience (years) *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = EduTextDark)
                OutlinedTextField(
                    value = experience,
                    onValueChange = { experience = it.filter { c -> c.isDigit() }.take(2) },
                    placeholder = { Text("e.g. 5", fontSize = 13.sp, color = EduGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    isError = experienceError,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = EduBlue,
                        unfocusedBorderColor = EduBorder,
                        focusedTextColor     = EduTextDark,
                        unfocusedTextColor   = EduTextDark
                    )
                )
                if (experienceError) Text("Please enter your experience", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }

            Spacer(Modifier.height(8.dp))

            apiError?.let {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color(0xFFFFEBEE)) {
                    Text(it, color = Color(0xFFD32F2F), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
                }
            }

            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = EduBlue)
                    Spacer(Modifier.height(8.dp))
                    Text("Saving profile...", fontSize = 12.sp, color = EduGray)
                }
            } else {
                EduButton(
                    text = "Save & Continue",
                    onClick = {
                        showErrors = true
                        if (validate()) saveProfile()
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}