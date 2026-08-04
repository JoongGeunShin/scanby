package com.example.scanby.feature.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.scanby.core.designsystem.theme.ScanbyColor
import com.example.scanby.core.designsystem.theme.ScanbyTheme
import com.example.scanby.core.designsystem.theme.ScanbyTypography

/**
 * Title + subtitle (+ optional badge, e.g. "Beta") shown below the phone panel
 * on each content scene.
 */
@Composable
fun CaptionBar(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = ScanbyTypography.CaptionTitle,
                color = ScanbyColor.Ink,
                textAlign = TextAlign.Center,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = ScanbyTypography.CaptionSub,
                color = ScanbyColor.CaptionSub,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun CaptionBarPreview() {
    ScanbyTheme {
        CaptionBar(
            title = "손가락도 깔끔하게 지워드려요",
            subtitle = "촬영한 페이지를 AI가 스스로 다듬어요",
        )
    }
}
