package com.example.tutorapp

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.tutorapp.teacher.Routes
import com.example.tutorapp.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay

@Composable
fun TeacherSplashScreen(navController: NavHostController) {

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        delay(1000)

        val prefs = context.getSharedPreferences("eduvibe_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("teacher_logged_in", false)

        if (isLoggedIn) {
            navController.navigate(Routes.MAIN) {
                popUpTo("TeacherSplashScreen") { inclusive = true }
            }
        } else {
            navController.navigate(Routes.TEACHER_LOGIN) {
                popUpTo("TeacherSplashScreen") { inclusive = true }
            }
        }
    }
    Column(
        modifier            = Modifier.fillMaxSize().background(Color.White).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter            = painterResource(id = R.drawable.logo),
            contentDescription = "EduVibe Logo",
            modifier           = Modifier.size(200.dp),
            contentScale       = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick  = {
                navController.navigate(Routes.TEACHER_LOGIN) {
                    popUpTo("TeacherSplashScreen") { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth(0.65f).height(50.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("Get Started", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Icon(
            imageVector        = Icons.Default.ArrowForward,
            contentDescription = null,
            tint               = Color.Black,
            modifier           = Modifier.size(32.dp)
        )
    }
}