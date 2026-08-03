package com.example.scanby.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Fixed light palette regardless of system theme: onboarding is branded marketing
// content (navy/paper), not a surface that should invert in system dark mode.
private val ScanbyColorScheme = lightColorScheme(
    primary = ScanbyColor.Accent,
    onPrimary = Color.White,
    background = ScanbyColor.Paper,
    onBackground = ScanbyColor.Ink,
    surface = ScanbyColor.Paper,
    onSurface = ScanbyColor.Ink,
)

private val ScanbyMaterialTypography = Typography(
    titleLarge = ScanbyTypography.CaptionTitle,
    bodyLarge = ScanbyTypography.CaptionSub,
    labelLarge = ScanbyTypography.Badge,
)

@Composable
fun ScanbyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScanbyColorScheme,
        typography = ScanbyMaterialTypography,
        content = content,
    )
}
