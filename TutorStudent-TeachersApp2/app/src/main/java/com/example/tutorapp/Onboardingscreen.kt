package com.example.tutorapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.tutorapp.ui.theme.*
import com.example.tutorapp.viewmodel.ProfileViewModel

@Composable
fun OnboardingScreen(navController: NavHostController) {

    val profileViewModel: ProfileViewModel = viewModel()
    val profileUiState by profileViewModel.uiState.collectAsState()
    val context = LocalContext.current

    var currentStep      by remember { mutableIntStateOf(1) }
    var fullName         by remember { mutableStateOf("") }
    var selectedClass    by remember { mutableStateOf("") }
    var selectedSubjects by remember { mutableStateOf(setOf<String>()) }
    var selectedGoal     by remember { mutableStateOf("") }

    val totalSteps = 4

    //  d ProfileEditScreen ke saath same format — "6th Grade" style
    val classOptions   = listOf("6th Grade","7th Grade","8th Grade","9th Grade","10th Grade","11th Grade","12th Grade")
    val subjectOptions = listOf("Maths","Science","English","SST","Hindi","Biology","Physics")
    val goalOptions    = listOf("JEE","NEET","CBSE","ICSE","State Board")

    // Onboarding complete → MentorScreen
    LaunchedEffect(profileUiState.isSaveSuccess) {
        if (profileUiState.isSaveSuccess) {
            profileViewModel.resetState()
            navController.navigate("MentorScreen") {
                popUpTo("OnboardingScreen") { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
    ) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            if (currentStep > 1) {
                IconButton(onClick = { currentStep-- }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
                }
            }
            Text(
                text = when (currentStep) {
                    1    -> "Your Name"
                    2    -> "Your Class"
                    3    -> "Your Subjects"
                    else -> "Your Goal"
                },
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                modifier   = Modifier.align(Alignment.Center)
            )
        }

        // Step indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(totalSteps) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < currentStep) PrimaryBlue else Color(0xFFDDDDDD))
                )
            }
        }

        if (profileUiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PrimaryBlue)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            when (currentStep) {

                1 -> {
                    Text("What's your name?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("We'll use this to personalize your experience", fontSize = 13.sp, color = TextGray)
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value         = fullName,
                        onValueChange = { fullName = it },
                        label         = { Text("Full Name") },
                        placeholder   = { Text("Enter your full name") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = PrimaryBlue,
                            unfocusedBorderColor = Color(0xFFDDDDDD)
                        )
                    )
                }

                2 -> {
                    Text("Which class are you in?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Select your current grade", fontSize = 13.sp, color = TextGray)
                    Spacer(modifier = Modifier.height(24.dp))
                    classOptions.forEach { cls ->
                        val selected = selectedClass == cls
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) PrimaryBlue else CardWhite)
                                .border(1.5.dp, if (selected) PrimaryBlue else Color(0xFFDDDDDD), RoundedCornerShape(12.dp))
                                .clickable { selectedClass = cls }
                                .padding(vertical = 14.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                text       = cls,
                                fontSize   = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (selected) Color.White else TextPrimary
                            )
                        }
                    }
                }

                3 -> {
                    Text("Which subjects?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("You can select multiple subjects", fontSize = 13.sp, color = TextGray)
                    Spacer(modifier = Modifier.height(24.dp))
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns               = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                        modifier              = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(8.dp)
                    ) {
                        items(subjectOptions.size) { i ->
                            val subject  = subjectOptions[i]
                            val selected = selectedSubjects.contains(subject)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) PrimaryBlue else CardWhite)
                                    .border(1.dp, if (selected) PrimaryBlue else Color(0xFFDDDDDD), RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedSubjects = if (selected)
                                            selectedSubjects - subject
                                        else
                                            selectedSubjects + subject
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text       = subject,
                                    fontSize   = 13.sp,
                                    color      = if (selected) Color.White else TextPrimary,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign  = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (selectedSubjects.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Selected:", fontSize = 12.sp, color = TextGray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            selectedSubjects.forEach { sub ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0xFFEEF2FF))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(sub, fontSize = 11.sp, color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                4 -> {
                    Text("What's your goal?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Choose your target exam or board", fontSize = 13.sp, color = TextGray)
                    Spacer(modifier = Modifier.height(24.dp))
                    goalOptions.forEach { goal ->
                        val selected = selectedGoal == goal
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) PrimaryBlue else CardWhite)
                                .border(1.5.dp, if (selected) PrimaryBlue else Color(0xFFDDDDDD), RoundedCornerShape(12.dp))
                                .clickable { selectedGoal = goal }
                                .padding(vertical = 14.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                text       = goal,
                                fontSize   = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (selected) Color.White else TextPrimary
                            )
                        }
                    }

                    profileUiState.errorMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("⚠ $it", fontSize = 12.sp, color = Color.Red)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Next / Get Started button
        val buttonEnabled = when (currentStep) {
            1    -> fullName.trim().isNotEmpty()
            2    -> selectedClass.isNotEmpty()
            3    -> selectedSubjects.isNotEmpty()
            4    -> selectedGoal.isNotEmpty() && !profileUiState.isLoading
            else -> false
        }

        Button(
            onClick = {
                if (currentStep < totalSteps) {
                    currentStep++
                } else {
                    profileViewModel.updateProfile(
                        context    = context,
                        fullName   = fullName.trim(),
                        gradeLevel = selectedClass.trim(),
                        goals      = listOf(selectedGoal.trim()),
                        subjects   = selectedSubjects.map { it.trim() },
                        photoFile  = null
                    )
                }
            },
            enabled  = buttonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .height(52.dp),
            shape  = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor         = PrimaryBlue,
                disabledContainerColor = Color(0xFFB0C4DE)
            )
        ) {
            if (profileUiState.isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text       = if (currentStep < totalSteps) "Next" else "Get Started",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }
        }
    }
}