package com.example.tutorapp

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.ui.theme.*
import com.example.tutorapp.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {

    var selectedTab by remember { mutableIntStateOf(2) }
    val profileViewModel: ProfileViewModel = viewModel()
    val profileUiState by profileViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Unread notification count ─────────────────────────────────────────────
    var unreadNotifCount by remember { mutableIntStateOf(0) }

    // ── Wallet balance ────────────────────────────────────────────────────────
    var walletBalance by remember { mutableStateOf<Double?>(null) }
    var isWalletLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        profileViewModel.loadProfile(context)

        val token = TokenManager.getToken(context) ?: return@LaunchedEffect
        val bearer = "Bearer $token"

        // Fetch unread notifications
        try {
            val resp = RetrofitClient.api.getNotifications(bearer)
            if (resp.isSuccessful) {
                unreadNotifCount = resp.body()?.data?.count { !it.isRead } ?: 0
            }
        } catch (_: Exception) {}

        // Fetch wallet
        try {
            val walletResp = RetrofitClient.api.getWallet(bearer)
            if (walletResp.isSuccessful) {
                walletBalance = walletResp.body()?.data?.balance ?: 0.0
            } else {
                walletBalance = 0.0
            }
        } catch (_: Exception) {
            walletBalance = 0.0
        } finally {
            isWalletLoading = false
        }
    }

    val profile = profileUiState.profile

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(PrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("E", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("EduVibe", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf(
                    "Home"     to Icons.Default.Home,
                    "Status"   to Icons.Outlined.DateRange,
                    "Settings" to Icons.Default.Settings
                ).forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            when (index) {
                                0 -> navController.navigate("MentorScreen")
                                1 -> navController.navigate("StatusScreen")
                                2 -> {}
                            }
                        },
                        icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            unselectedIconColor = TextGray,
                            unselectedTextColor = TextGray,
                            indicatorColor = Color(0xFFE8F0FE)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBg)
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Profile Card ───────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("ProfileEditScreen") }
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFE0E0E0)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (profile?.profilePhoto != null) {
                                AsyncImage(
                                    model = profile.profilePhoto,
                                    contentDescription = "Profile",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = "Profile",
                                    tint = Color(0xFF9E9E9E),
                                    modifier = Modifier.size(60.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile?.fullName ?: "Loading...",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (!profile?.gradeLevel.isNullOrEmpty()) {
                                Text(text = profile?.gradeLevel ?: "", fontSize = 12.sp, color = TextGray)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray, modifier = Modifier.size(22.dp))
                    }

                    val goals = profile?.goals
                    if (!goals.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Goals", fontSize = 11.sp, color = TextGray, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            goals.forEach { goal ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0xFFEEF2FF))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(goal.title, fontSize = 11.sp, color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    val subjects = profile?.subjects
                    if (!subjects.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Subjects", fontSize = 11.sp, color = TextGray, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        subjects.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                                row.forEach { subject ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFFF0FFF4))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(subject.title, fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Wallet Card ────────────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("WalletScreen") },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("My Wallet", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        if (isWalletLoading) {
                            Text("Loading...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        } else {
                            Text(
                                text = "₹ ${String.format("%.2f", walletBalance ?: 0.0)}",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Menu Card ──────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column {
                    SettingsMenuItem(
                        icon = Icons.Outlined.Notifications,
                        label = "Notifications",
                        badgeCount = unreadNotifCount,
                        onClick = { navController.navigate("NotificationScreen") }
                    )
                    MenuDivider()
                    SettingsMenuItem(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        label = "Wallet & Transactions",
                        onClick = { navController.navigate("WalletScreen") }
                    )
                    MenuDivider()
                    SettingsMenuItem(
                        icon = Icons.Outlined.Info,
                        label = "Support",
                        onClick = { navController.navigate("SupportScreen") }
                    )
                    MenuDivider()
                    SettingsMenuItem(
                        icon = Icons.Outlined.ExitToApp,
                        label = "Logout",
                        labelColor = Color(0xFFE53935),
                        iconColor = Color(0xFFE53935),
                        onClick = {
                            TokenManager.clearAll(context)
                            val prefs = context.getSharedPreferences("eduvibe_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit()
                                .putBoolean("is_logged_in", false)
                                .putBoolean("needs_role_selection", false)
                                .remove("google_user_name")
                                .remove("google_user_email")
                                .apply()
                            val activity = context as? Activity
                            activity?.finish()
                            activity?.startActivity(activity.intent)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Shared composables ─────────────────────────────────────────────────────────

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    label: String,
    labelColor: Color = TextPrimary,
    iconColor: Color = TextGray,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = label, fontSize = 14.sp, color = labelColor, modifier = Modifier.weight(1f))

        // Badge
        if (badgeCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else "$badgeCount",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MenuDivider() {
    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFFF0F0F0), thickness = 1.dp)
}