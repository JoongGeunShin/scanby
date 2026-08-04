package com.example.scanby.feature.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.scanby.core.designsystem.theme.ScanbyColor
import com.example.scanby.core.designsystem.theme.ScanbyTheme
import com.example.scanby.core.designsystem.theme.ScanbyTypography
import kotlinx.coroutines.delay

/** 워드마크·태그라인 페이드인(650ms)이 끝난 뒤 이 화면을 얼마나 더 붙잡아둘지. */
private const val SPLASH_DISPLAY_DURATION_MS = 1_800L

/**
 * 앱 실행 시 항상 가장 먼저 뜨는 브랜드 스플래시 화면. 탭 없이 일정 시간 후
 * 자동으로 onFinished가 호출된다. onFinished 다음에 어디로 갈지(온보딩을
 * 이미 마쳤는지 등)는 이 화면이 알 필요 없이 호출부(SplashViewModel/NavHost)가 결정한다.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit = {}, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        delay(SPLASH_DISPLAY_DURATION_MS)
        onFinished()
    }
    val wordmarkAlpha by animateFloatAsState(
        if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "wordmarkAlpha")
    val taglineAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, delayMillis = 150),
        label = "taglineAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScanbyColor.Navy),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "scanby",
                style = ScanbyTypography.Wordmark,
                color = Color.White,
                modifier = Modifier.alpha(wordmarkAlpha),
            )
            Text(
                text = "정확하고 빠르게, 문서를 스캔하다",
                style = ScanbyTypography.Tagline,
                color = ScanbyColor.OnNavySecondary,
                modifier = Modifier.alpha(taglineAlpha),
            )
        }
    }
}

@Preview(showBackground = false, widthDp = 360, heightDp = 720)
@Composable
private fun SplashScreenPreview() {
    ScanbyTheme {
        SplashScreen()
    }
}
