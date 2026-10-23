package com.example.tutorapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.tutorapp.network.TeacherItem
import com.example.tutorapp.ui.theme.PrimaryBlue
import com.example.tutorapp.ui.theme.TextGray
import com.example.tutorapp.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherRegisterBottomSheet(
    teacher: TeacherItem,
    sheetState: SheetState,
    navController: NavHostController,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text       = "Register with ${teacher.fullName}",
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text     = teacher.bio.ifBlank { "Expert tutor ready to help you excel." },
                fontSize = 13.sp,
                color    = TextGray
            )
            Spacer(modifier = Modifier.height(4.dp))
            val hourlyRate = teacher.pricing?.hourlyRate
            if (hourlyRate != null) {
                Text(
                    text       = "₹ $hourlyRate / hour",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = PrimaryBlue
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick  = {
                    onDismiss()
                    // Navigate or handle enrollment here
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Confirm Registration", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}