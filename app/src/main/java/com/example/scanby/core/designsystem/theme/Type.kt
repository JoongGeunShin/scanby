package com.example.scanby.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Original design uses Noto Sans KR from Google Fonts; using the system default
// font family for now since it already covers Hangul via system fallback fonts.
// Swap to a bundled/downloadable Noto Sans KR FontFamily here if pixel-accurate
// weights are needed later.
private val ScanbyFontFamily = FontFamily.Default

object ScanbyTypography {
    val Wordmark = TextStyle(
        fontFamily = ScanbyFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        letterSpacing = (-0.02).sp,
    )
    val Tagline = TextStyle(
        fontFamily = ScanbyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    )
    val CaptionTitle = TextStyle(
        fontFamily = ScanbyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    )
    val CaptionSub = TextStyle(
        fontFamily = ScanbyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    )
    val Badge = TextStyle(
        fontFamily = ScanbyFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
    )
    val Cta = TextStyle(
        fontFamily = ScanbyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    )
}
