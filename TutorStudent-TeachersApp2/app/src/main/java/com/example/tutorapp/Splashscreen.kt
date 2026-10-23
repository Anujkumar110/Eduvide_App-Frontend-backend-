package com.example.tutorapp

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.tutorapp.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavHostController) {

    // 3 sec baad automatically LoginScreen pe
    LaunchedEffect(Unit) {
        delay(1000)
        navController.navigate("LoginScreen") {
            popUpTo("SplashScreen") { inclusive = true }
        }
    }

    Column(
        modifier            = Modifier.fillMaxSize().background(Color.White).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo — res/drawable/logo.png daalo
        Image(
            painter            = painterResource(id = R.drawable.logo),
            contentDescription = "EduVibe Logo",
            modifier           = Modifier.size(200.dp),
            contentScale       = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick  = {
                navController.navigate("LoginScreen") {
                    popUpTo("SplashScreen") { inclusive = true }
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