package com.example.tutorapp.teacher

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.tutorapp.SessionViewModel
import com.example.tutorapp.ui.theme.EduBackground
import com.example.tutorapp.ui.theme.EduBlue
import com.example.tutorapp.ui.theme.EduBlueLight
import com.example.tutorapp.ui.theme.EduGray

private data class BottomNavItem(val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController:    NavController,
    sessionViewModel: SessionViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("tutor_prefs", Context.MODE_PRIVATE)
    }

    var selectedTab by rememberSaveable { mutableStateOf(0) }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    val tabs = listOf(
        BottomNavItem("Home",     Icons.Filled.Home),
        BottomNavItem("Sessions", Icons.Filled.DateRange),
        BottomNavItem("Settings", Icons.Filled.Settings),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("EduVibe", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.NOTIFICATIONS) }) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = Color.White)
                    }
                    IconButton(onClick = { navController.navigate(Routes.PROFILE) }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = EduBlue,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 6.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        icon     = { Icon(tab.icon, contentDescription = tab.label) },
                        label    = { Text(tab.label, fontSize = 11.sp) },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor   = EduBlue,
                            selectedTextColor   = EduBlue,
                            indicatorColor      = EduBlueLight,
                            unselectedIconColor = EduGray,
                            unselectedTextColor = EduGray
                        )
                    )
                }
            }
        },
        containerColor = EduBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            KeepAliveTab(tabIndex = 0, selectedTab = selectedTab) { HomeTab() }
            KeepAliveTab(tabIndex = 1, selectedTab = selectedTab) {
                SessionsTab(
                    viewModel     = sessionViewModel,
                    isTabSelected = selectedTab == 1,
                    onStartLive   = { roomId, role ->
                        navController.navigate("CallScreen/$roomId/$role")
                    }
                )
            }
            KeepAliveTab(tabIndex = 2, selectedTab = selectedTab) {
                SettingsTab(
                    navController    = navController,
                    prefs            = prefs,
                    sessionViewModel = sessionViewModel
                )
            }
        }
    }
}

@Composable
private fun KeepAliveTab(
    tabIndex:    Int,
    selectedTab: Int,
    content:     @Composable () -> Unit
) {
    val isSelected   = tabIndex == selectedTab
    var hasBeenShown by remember { mutableStateOf(isSelected) }
    if (isSelected) hasBeenShown = true

    if (hasBeenShown) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isSelected)
                        Modifier.requiredSize(0.dp).clipToBounds()
                    else
                        Modifier
                )
        ) {
            content()
        }
    }
}