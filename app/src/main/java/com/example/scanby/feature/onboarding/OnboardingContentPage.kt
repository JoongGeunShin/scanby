package com.example.scanby.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanby.core.designsystem.theme.ScanbyColor
import com.example.scanby.core.designsystem.theme.ScanbyTheme

private const val ContentFadeInMs = 300
private const val ContentFadeOutMs = 150

/**
 * One of the 4 content scenes: header (with step dots) + phone frame + caption. The
 * phone frame and caption sit in a fixed position — only their content cross-fades
 * (out with the old scene, in with the new one) when [scene] changes, since the
 * pager driving this no longer physically slides the screen.
 */
@Composable
fun OnboardingContentPage(
    scene: OnboardingContentScene,
    stepIndex: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScanbyColor.Paper),
        verticalArrangement = Arrangement.Center,
    ) {
        PhonePanelFrame(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            AnimatedContent(
                targetState = scene,
                transitionSpec = {
                    fadeIn(tween(ContentFadeInMs)) togetherWith fadeOut(tween(ContentFadeOutMs))
                },
                modifier = Modifier.fillMaxSize(),
                label = "panelContent",
            ) { targetScene ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = targetScene.placeholderEmoji, fontSize = 64.sp, fontWeight = FontWeight.Normal)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        CurrentStep(currentStep = stepIndex)
        AnimatedContent(
            targetState = scene,
            transitionSpec = {
                (fadeIn(tween(ContentFadeInMs)) + slideInVertically(tween(ContentFadeInMs)) { it / 4 }) togetherWith
                    (fadeOut(tween(ContentFadeOutMs)) + slideOutVertically(tween(ContentFadeOutMs)) { -it / 4 })
            },
            modifier = Modifier.fillMaxWidth(),
            label = "captionContent",
        ) { targetScene ->
            CaptionBar(
                title = targetScene.title,
                subtitle = targetScene.subtitle,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun OnboardingContentPagePreview() {
    ScanbyTheme {
        OnboardingContentPage(scene = onboardingContentScenes[2], stepIndex = 2)
    }
}
