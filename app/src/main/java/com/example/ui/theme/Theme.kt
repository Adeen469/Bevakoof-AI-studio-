package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = CyanNeon,
    onPrimary = Slate950,
    primaryContainer = Slate800,
    onPrimaryContainer = CyanNeon,
    secondary = IndigoNeon,
    onSecondary = Color.White,
    tertiary = EmeraldSafe,
    error = CrimsonCritical,
    background = Slate950,
    onBackground = Slate200,
    surface = Slate900,
    onSurface = Slate200,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400
)

private val LightColorScheme = lightColorScheme(
    primary = CyanNeonDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = IndigoNeon,
    onSecondary = Color.White,
    tertiary = EmeraldSafe,
    error = CrimsonCritical,
    background = Color(0xFFF8FAFC),
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Slate700
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek cyber dark theme for Bewakoof AI
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
