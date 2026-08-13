package com.abhijit.shutupNstudy.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = NeuTextPrimary,
    secondary = NeuTextSecondary,
    background = NeuBg,
    surface = NeuBg,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = NeuTextPrimary,
    onSurface = NeuTextPrimary
)

@Composable
fun ShutupStudyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // Keeping the app light/neumorphic as requested by theme
    content: @Composable () -> Unit
) {
    // Neumorphic styling is typically light-themed, so we force light colors to match the web app theme
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
