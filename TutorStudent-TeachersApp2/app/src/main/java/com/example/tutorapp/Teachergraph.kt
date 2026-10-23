package com.example.tutorapp.teacher

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.tutorapp.SessionViewModel
import com.example.tutorapp.TeacherSplashScreen
import com.example.tutorapp.ui.screen.CallScreen
import com.example.tutorapp.wallet.TeacherDashboardScreen
import com.example.tutorapp.wallet.TeacherWithdrawScreen

@RequiresApi(Build.VERSION_CODES.O)
fun NavGraphBuilder.teacherGraph(
    navController:    NavHostController,
    prefs:            SharedPreferences,
    sessionViewModel: SessionViewModel,
    context:          Context
) {
    composable("TeacherSplashScreen") { TeacherSplashScreen(navController) }
    composable(Routes.TEACHER_LOGIN)    { TeacherLoginScreen(navController, prefs) }
    composable(Routes.TEACHER_REGISTER) { TeacherRegisterScreen(navController, prefs) }

    composable(Routes.MAIN) {
        MainScreen(
            navController    = navController,
            sessionViewModel = sessionViewModel
        )
    }

    composable(
        route     = "CallScreen/{roomId}/{role}",
        arguments = listOf(
            navArgument("roomId") { type = NavType.StringType },
            navArgument("role")   { type = NavType.StringType }
        )
    ) { backStack ->
        val roomId = backStack.arguments?.getString("roomId") ?: ""
        val role   = backStack.arguments?.getString("role")   ?: "teacher"
        CallScreen(
            roomId  = roomId,
            role    = role,
            onLeave = { navController.popBackStack() }
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
            navController = navController,
            contactName   = contactName,
            contactUserId = contactUserId,
            roomId        = roomId,
            onVoiceCall   = { navController.navigate("CallScreen/$roomId/teacher") },
            onVideoCall   = { navController.navigate("CallScreen/$roomId/teacher") }
        )
    }

    composable(Routes.WALLET) {
        TeacherDashboardScreen(
            onWithdrawClick = { navController.navigate(Routes.WALLET_WITHDRAW) }
        )
    }

    composable(Routes.WALLET_WITHDRAW) {
        TeacherWithdrawScreen(onBack = { navController.popBackStack() })
    }

    composable(Routes.NOTIFICATIONS)   { NotificationsScreen(navController) }
    composable(Routes.PROFILE)         { ProfileScreen(navController) }
    composable(Routes.PERSONAL_INFO)   {
        val viewModel: SessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        PersonalInfoScreen(navController, prefs, viewModel)
    }
    composable(Routes.COURSES)         { CoursesScreen(navController) }
    composable(Routes.PAYMENT)         { PaymentScreen(navController) }

    // ✅ FIXED: viewModel aur context pass kar rahe hain real reviews ke liye
    composable(Routes.REVIEWS) {
        ReviewsScreen(
            navController = navController,
            viewModel     = sessionViewModel,
            context       = context
        )
    }

    composable(Routes.YOUR_RANKING)    { YourRankingScreen(navController) }
    composable(Routes.SUPPORT)         { SupportScreen(navController) }
    composable(Routes.ACCOUNT_CONTROL) { AccountControlScreen(navController) }
    composable(Routes.CLASS)           { ClassScreen(navController) }
}