package com.example.tutorapp

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tutorapp.teacher.*
import com.example.tutorapp.network.FcmTokenManager
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.network.RazorpayManager
import com.example.tutorapp.network.WebSocketManager
import com.example.tutorapp.ui.screen.CallScreen
import com.example.tutorapp.wallet.AddMoneyScreen
import com.example.tutorapp.wallet.CoursePurchaseScreen
import com.example.tutorapp.wallet.StudentWalletScreen
import com.example.tutorapp.wallet.TeacherDashboardScreen
import com.example.tutorapp.wallet.TeacherWithdrawScreen
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    private lateinit var wsManager: WebSocketManager
    private lateinit var sessionViewModel: SessionViewModel
    lateinit var razorpayManager: RazorpayManager

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        razorpayManager = RazorpayManager(
            activity       = this,
            apiService     = RetrofitClient.api,
            coroutineScope = lifecycleScope
        ).apply { initCheckout() }

        wsManager = WebSocketManager(
            context       = this,
            tokenProvider = { TokenManager.getBearerToken(this) }
        )

        sessionViewModel = ViewModelProvider(
            this,
            SessionViewModelFactory(context = this, wsManager = wsManager)
        )[SessionViewModel::class.java]

        val existingAuthToken = TokenManager.getToken(this)
        if (!existingAuthToken.isNullOrBlank()) {
            FcmTokenManager.syncTokenWithBackend(
                context     = this,
                bearerToken = "Bearer $existingAuthToken",
                api         = RetrofitClient.api
            )
        }

        setContent {
            val navController = rememberNavController()
            val prefs         = getSharedPreferences("eduvibe_prefs", Context.MODE_PRIVATE)

            val isTeacherLoggedIn = prefs.getBoolean("teacher_logged_in", false)
            val isStudentLoggedIn = prefs.getBoolean("is_logged_in", false)

            if (isTeacherLoggedIn || isStudentLoggedIn) {
                wsManager.connect()
            }

            val startDestination = when {
                isTeacherLoggedIn -> Routes.MAIN
                isStudentLoggedIn -> "MentorScreen"
                else              -> "WelcomeScreen"
            }

            NavHost(navController = navController, startDestination = startDestination) {

                composable("WelcomeScreen")          { WelcomeScreen(navController) }
                composable("SplashScreen")           { SplashScreen(navController) }
                composable("LoginScreen")            { LoginScreen(navController) }
                composable("OnboardingScreen")       { OnboardingScreen(navController) }
                composable("MentorScreen")           { MentorScreen(navController) }
                composable("SettingsScreen")         { SettingsScreen(navController) }
                composable("StatusScreen")           { StatusScreen(navController, sessionViewModel) }
                composable("ProfileEditScreen")      { ProfileEditScreen(navController) }
                composable("NotificationScreen")     { NotificationScreen(navController) }
                composable("SupportScreen")          { SupportScreen(navController) }
                composable("LiveChatScreen")         { LiveChatScreen(navController) }
                composable("ClassGoalScreen")        { ClassGoalScreen(navController) }
                composable("ResetCredentialsScreen") { ResetCredentialsScreen(navController) }

                composable("RegisterScreen") {
                    RegisterScreen(navController)
                }

                composable(
                    route     = "RegisterScreen/{slotId}/{teacherUserId}",
                    arguments = listOf(
                        navArgument("slotId")        { type = NavType.StringType },
                        navArgument("teacherUserId") { type = NavType.StringType }
                    )
                ) { backStack ->
                    val slotId        = backStack.arguments?.getString("slotId")        ?: ""
                    val teacherUserId = backStack.arguments?.getString("teacherUserId") ?: ""
                    RegisterScreenWithSlot(
                        navController = navController,
                        slotId        = slotId,
                        teacherUserId = teacherUserId
                    )
                }

                composable("WalletScreen") { WalletScreen(navController) }

                composable("PaymentScreen") {
                    PaymentScreen(navController, razorpayManager = razorpayManager)
                }

                composable(
                    route     = "PaymentScreen/{teacherId}/{amount}",
                    arguments = listOf(
                        navArgument("teacherId") { type = NavType.StringType },
                        navArgument("amount")    { type = NavType.StringType }
                    )
                ) { backStack ->
                    val teacherId = backStack.arguments?.getString("teacherId") ?: ""
                    val amountStr = backStack.arguments?.getString("amount") ?: "399"
                    val amount    = amountStr.toDoubleOrNull() ?: 399.0
                    PaymentScreen(
                        navController   = navController,
                        amount          = amount,
                        teacherId       = teacherId,
                        razorpayManager = razorpayManager
                    )
                }

                composable("RoleSelectionScreen") { RoleSelectionScreen(navController) }

                composable("MentorDetailScreen/{teacherId}") { backStack ->
                    val teacherId = backStack.arguments?.getString("teacherId") ?: ""
                    MentorDetailScreen(navController, teacherId)
                }

                composable("TeacherSplashScreen")   { TeacherSplashScreen(navController) }
                composable(Routes.TEACHER_LOGIN)    { TeacherLoginScreen(navController, prefs) }
                composable(Routes.TEACHER_REGISTER) { TeacherRegisterScreen(navController, prefs) }

                composable(Routes.COMPLETE_PROFILE_1) {
                    CompleteProfileStep1Screen(
                        navController = navController,
                        prefs         = getSharedPreferences("teacher_profile", Context.MODE_PRIVATE)
                    )
                }
                composable(Routes.COMPLETE_PROFILE_2) {
                    CompleteProfileStep2Screen(navController = navController)
                }
                composable(Routes.MAIN) {
                    MainScreen(navController, sessionViewModel)
                }
                composable(Routes.PROFILE) {
                    ProfileScreen(navController = navController, sessionViewModel = sessionViewModel)
                }
                composable(Routes.EDIT_PROFILE) {
                    EditProfileScreen(navController = navController, sessionViewModel = sessionViewModel)
                }
                composable("teacher_profile_edit") {
                    TeacherProfileEditScreen(navController = navController)
                }
                composable(Routes.PERSONAL_INFO) {
                    val vm: SessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    PersonalInfoScreen(
                        navController    = navController,
                        prefs            = getSharedPreferences("teacher_profile", Context.MODE_PRIVATE),
                        sessionViewModel = vm
                    )
                }
                composable(Routes.NOTIFICATIONS) { NotificationsScreen(navController = navController) }
                composable(Routes.COURSES)       { CoursesScreen(navController = navController) }
                composable(Routes.PAYMENT)       { PaymentScreen(navController = navController, razorpayManager = razorpayManager) }

                composable(Routes.REVIEWS) {
                    ReviewsScreen(
                        navController = navController,
                        viewModel     = sessionViewModel,
                        context       = this@MainActivity
                    )
                }

                composable(Routes.YOUR_RANKING)    { YourRankingScreen(navController = navController) }
                composable(Routes.SUPPORT)         { SupportScreen(navController = navController) }
                composable(Routes.ACCOUNT_CONTROL) { AccountControlScreen(navController = navController) }
                composable(Routes.CLASS)           { ClassScreen(navController = navController) }

                composable("TeacherWalletScreen") {
                    TeacherWalletScreen(navController = navController)
                }

                composable("EarlyLeaveReportsScreen") {
                    EarlyLeaveReportsScreen(
                        navController    = navController,
                        sessionViewModel = sessionViewModel
                    )
                }

                composable(
                    route     = "${Routes.CHAT}/{contactName}/{contactUserId}/{roomId}",
                    arguments = listOf(
                        navArgument("contactName")   { type = NavType.StringType },
                        navArgument("contactUserId") { type = NavType.StringType },
                        navArgument("roomId")        { type = NavType.StringType }
                    )
                ) { backStack ->
                    val contactName   = backStack.arguments?.getString("contactName")   ?: "Unknown"
                    val contactUserId = backStack.arguments?.getString("contactUserId") ?: ""
                    val roomId        = backStack.arguments?.getString("roomId")        ?: ""
                    ChatScreen(
                        navController   = navController,
                        contactName     = contactName,
                        contactUserId   = contactUserId,
                        roomId          = roomId,
                        onVoiceCall     = { navController.navigate("CallScreen/$roomId/teacher") },
                        onVideoCall     = { navController.navigate("CallScreen/$roomId/teacher") }
                    )
                }

                // StudentLiveScreen → CallScreen (student role)
                composable(
                    route     = "StudentLiveScreen/{roomId}",
                    arguments = listOf(navArgument("roomId") { type = NavType.StringType })
                ) { backStack ->
                    val roomId      = backStack.arguments?.getString("roomId") ?: ""
                    val studentName = getSharedPreferences("eduvibe_prefs", Context.MODE_PRIVATE)
                        .getString("user_name", "Student") ?: "Student"

                    CallScreen(
                        roomId       = roomId,
                        role         = "student",
                        classId      = roomId,
                        onLeave      = { navController.popBackStack() },
                        onEarlyLeave = { reason, minutesAttended ->
                            sessionViewModel.sendEarlyLeaveReason(
                                classId         = roomId,
                                sessionId       = roomId,
                                reason          = reason,
                                studentName     = studentName,
                                minutesAttended = minutesAttended
                            )
                        }
                    )
                }

                // TeacherLiveScreen → CallScreen (teacher role)
                composable(
                    route     = "TeacherLiveScreen/{roomId}",
                    arguments = listOf(navArgument("roomId") { type = NavType.StringType })
                ) { backStack ->
                    val roomId = backStack.arguments?.getString("roomId") ?: ""
                    CallScreen(
                        roomId       = roomId,
                        role         = "teacher",
                        classId      = roomId,
                        onLeave      = { navController.popBackStack() },
                        onEarlyLeave = null
                    )
                }

                // LiveClassScreen → CallScreen (both roles)
                composable(
                    route     = "LiveClassScreen/{roomId}/{role}",
                    arguments = listOf(
                        navArgument("roomId") { type = NavType.StringType },
                        navArgument("role")   { type = NavType.StringType }
                    )
                ) { backStack ->
                    val roomId      = backStack.arguments?.getString("roomId") ?: ""
                    val role        = backStack.arguments?.getString("role")   ?: "teacher"
                    val studentName = getSharedPreferences("eduvibe_prefs", Context.MODE_PRIVATE)
                        .getString("user_name", "Student") ?: "Student"

                    CallScreen(
                        roomId       = roomId,
                        role         = role,
                        classId      = roomId,
                        onLeave      = { navController.popBackStack() },
                        onEarlyLeave = if (role == "student") {
                            { reason, minutesAttended ->
                                sessionViewModel.sendEarlyLeaveReason(
                                    classId         = roomId,
                                    sessionId       = roomId,
                                    reason          = reason,
                                    studentName     = studentName,
                                    minutesAttended = minutesAttended
                                )
                            }
                        } else null
                    )
                }

                // CallScreen direct route
                composable(
                    route     = "CallScreen/{roomId}/{role}",
                    arguments = listOf(
                        navArgument("roomId") { type = NavType.StringType },
                        navArgument("role")   { type = NavType.StringType }
                    )
                ) { backStack ->
                    val roomId      = backStack.arguments?.getString("roomId") ?: ""
                    val role        = backStack.arguments?.getString("role")   ?: "teacher"
                    val studentName = getSharedPreferences("eduvibe_prefs", Context.MODE_PRIVATE)
                        .getString("user_name", "Student") ?: "Student"

                    CallScreen(
                        roomId       = roomId,
                        role         = role,
                        classId      = roomId,
                        onLeave      = { navController.popBackStack() },
                        onEarlyLeave = if (role == "student") {
                            { reason, minutesAttended ->
                                sessionViewModel.sendEarlyLeaveReason(
                                    classId         = roomId,
                                    sessionId       = roomId,
                                    reason          = reason,
                                    studentName     = studentName,
                                    minutesAttended = minutesAttended
                                )
                            }
                        } else null
                    )
                }

                composable(
                    route     = "SlotBookingScreen/{teacherId}/{teacherName}",
                    arguments = listOf(
                        navArgument("teacherId")   { type = NavType.StringType },
                        navArgument("teacherName") { type = NavType.StringType }
                    )
                ) { backStack ->
                    val teacherId   = backStack.arguments?.getString("teacherId")   ?: ""
                    val teacherName = backStack.arguments?.getString("teacherName") ?: ""
                    SlotBookingScreen(
                        navController   = navController,
                        teacherId       = teacherId,
                        teacherName     = teacherName,
                        viewModel       = sessionViewModel,
                        razorpayManager = razorpayManager
                    )
                }

                composable("StudentWalletScreen") {
                    StudentWalletScreen(
                        onAddMoneyClick       = { navController.navigate("AddMoneyScreen") },
                        onCoursePurchaseClick = { navController.navigate("CoursePurchaseScreen") }
                    )
                }
                composable("AddMoneyScreen")       { AddMoneyScreen(onBack = { navController.popBackStack() }) }
                composable("CoursePurchaseScreen") { CoursePurchaseScreen(onBack = { navController.popBackStack() }) }
                composable("TeacherDashboardScreen") {
                    TeacherDashboardScreen(onWithdrawClick = { navController.navigate("TeacherWithdrawScreen") })
                }
                composable("TeacherWithdrawScreen") {
                    TeacherWithdrawScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.WALLET) {
                    TeacherDashboardScreen(onWithdrawClick = { navController.navigate(Routes.WALLET_WITHDRAW) })
                }
                composable(Routes.WALLET_WITHDRAW) {
                    TeacherWithdrawScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs      = getSharedPreferences("eduvibe_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("teacher_logged_in", false) ||
                prefs.getBoolean("is_logged_in", false)
        if (isLoggedIn) wsManager.connect()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        wsManager.disconnect()
    }

    override fun onPaymentError(errorCode: Int, response: String?, data: PaymentData?) {
        Log.e("MainActivity", "Razorpay error: code=$errorCode, response=$response")
        razorpayManager.handlePaymentResult(
            paymentId = null,
            data      = data,
            error     = Pair(errorCode, response ?: "Unknown error")
        )
    }

    override fun onPaymentSuccess(paymentId: String?, data: PaymentData?) {
        Log.d("MainActivity", "Razorpay success: paymentId=$paymentId")
        razorpayManager.handlePaymentResult(
            paymentId = paymentId,
            data      = data,
            error     = null
        )
    }
}