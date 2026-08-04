package com.example.scanby.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import com.example.scanby.core.designsystem.theme.ScanbyTheme
import kotlinx.coroutines.launch

/**
 * Onboarding: 4 content scenes (0..3). There's no separate "closing" page/file — the
 * last content page doubles as the closing step, so tapping or swiping forward while
 * already on it calls [onFinished] instead of advancing.
 *
 * The [HorizontalPager] here only drives swipe/snap gestures and the current page
 * index — its own page content is empty, so paging never physically slides the screen.
 * The actual UI ([OnboardingContentPage]) sits in a fixed position on top and only its
 * *content* (step dots, panel emoji, caption) cross-fades when the page changes.
 * 스플래시(feature.splash.SplashScreen)는 여기 속하지 않고 별도 route로 먼저 보여준 뒤 이 화면으로 넘어온다.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pageCount = onboardingContentScenes.size
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

    // Guards against onFinished() firing repeatedly while a single overscroll drag
    // keeps reporting leftover delta; reset the moment the user leaves the last page.
    val finishTriggeredByDrag = remember { mutableStateOf(false) }
    val nestedScrollConnection = remember(pagerState, pageCount) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val isLastPage = pagerState.currentPage == pageCount - 1
                if (isLastPage && available.x < 0f) {
                    if (!finishTriggeredByDrag.value) {
                        finishTriggeredByDrag.value = true
                        onFinished()
                    }
                } else {
                    finishTriggeredByDrag.value = false
                }
                return Offset.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .clickable {
                if (pagerState.currentPage == pageCount - 1) {
                    onFinished()
                } else {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Intentionally empty: this pager exists for its gesture/snap behavior,
            // not to render anything — see the class-level doc comment above.
            Spacer(modifier = Modifier.fillMaxSize())
        }

        OnboardingContentPage(
            scene = onboardingContentScenes[pagerState.currentPage],
            stepIndex = pagerState.currentPage,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = false, widthDp = 360, heightDp = 720)
@Composable
private fun OnboardingScreenPreview() {
    ScanbyTheme {
        OnboardingScreen()
    }
}
