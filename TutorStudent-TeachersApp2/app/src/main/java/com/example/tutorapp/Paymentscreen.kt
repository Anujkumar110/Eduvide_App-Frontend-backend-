package com.example.tutorapp

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.tutorapp.network.RazorpayManager
import com.example.tutorapp.ui.theme.CardWhite
import com.example.tutorapp.ui.theme.LightBg
import com.example.tutorapp.ui.theme.PrimaryBlue
import com.example.tutorapp.ui.theme.TextGray
import com.example.tutorapp.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PaymentScreen(
    navController: NavHostController,
    amount: Double = 399.0,
    teacherId: String? = null,
    classId: String? = null,
    bookingId: String? = null,
    // ✅ FIX: razorpayManager is now required (non-nullable)
    //         Always injected from MainActivity — screen never creates its own instance
    //         This ensures onPaymentSuccess / onPaymentError callbacks from MainActivity
    //         always reach the correct RazorpayManager instance
    razorpayManager: RazorpayManager
) {
    val coroutineScope = rememberCoroutineScope() // ✅ FIX: rememberCoroutineScope instead of MainScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSuccess by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // ✅ FIX: Removed local RazorpayManager fallback creation entirely.
    //         No more orphaned instances, no more MainScope() leak,
    //         no more risky `activity!!` null crash.

    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(2000)
            navController.navigate("MentorScreen") {
                popUpTo("MentorScreen") { inclusive = false }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBg)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Top bar ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text(
                    "Payment",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Amount card ────────────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Amount to Pay",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "₹ ${amount.toInt()}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        "Session Registration Fee",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Razorpay Pay Button ────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Pay Securely via Razorpay",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        "UPI • Cards • Net Banking • Wallets",
                        fontSize = 11.sp,
                        color = TextGray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (!isProcessing) {
                                isProcessing = true
                                Log.d("PaymentScreen", "🚀 Starting Razorpay payment for ₹$amount")

                                razorpayManager.startPayment(
                                    amount = amount,
                                    teacherId = teacherId,
                                    classId = classId,
                                    bookingId = bookingId,
                                    callback = object : RazorpayManager.PaymentCallback {
                                        fun onSuccess(paymentId: String, orderId: String) {
                                            Log.d("PaymentScreen", "✅ Payment successful: $paymentId")
                                            isProcessing = false
                                            showSuccess = true
                                        }

                                        override fun onSuccess(
                                            paymentId: String,
                                            orderId: String,
                                            signature: String
                                        ) {
                                            TODO("Not yet implemented")
                                        }

                                        override fun onFailure(errorCode: String, errorMessage: String) {
                                            Log.e("PaymentScreen", "❌ Payment failed: $errorMessage")
                                            isProcessing = false
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(errorMessage)
                                            }
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22C55E)
                        ),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Processing...", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Text(
                                "Pay ₹${amount.toInt()} Now",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Secure badge ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔒", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    "Payments are 100% secure & encrypted",
                    fontSize = 11.sp,
                    color = TextGray
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // ── Success Overlay ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showSuccess,
            enter = fadeIn(animationSpec = tween(300)) +
                    scaleIn(initialScale = 0.6f, animationSpec = tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(96.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "REGISTERED",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF22C55E),
                        letterSpacing = 3.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Payment Successful!",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // ── Snackbar ───────────────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}