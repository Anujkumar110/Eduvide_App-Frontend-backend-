package com.example.tutorapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── App Colors ─────────────────────────────────────────────────────────────────
val PrimaryBlue = Color(0xFF1A73E8)
val OrangeBadge = Color(0xFFE8572A)
val LightBg     = Color(0xFFF5F7FA)
val CardWhite   = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1A1A2E)
val TextGray    = Color(0xFF6B7280)
val StudentBg   = Color(0xFFEEF2FF)
val TeacherBg   = Color(0xFFFFF8F0)

// ── Material Color Scheme ──────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary   = Purple80,
    secondary = PurpleGrey80,
    tertiary  = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary      = EduBlue,
    onPrimary    = EduWhite,
    secondary    = EduBlueDark,
    background   = EduBackground,
    surface      = EduWhite,
    onBackground = EduTextDark,
    onSurface    = EduTextDark,
    tertiary = Pink80
)

@Composable
fun TutorAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}