package com.example.scanby.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.scanby.core.designsystem.theme.ScanbyColor
import com.example.scanby.core.designsystem.theme.ScanbyTheme

@Composable
fun PhonePanelFrame(
    modifier: Modifier = Modifier,
    backgroundColor: Color = ScanbyColor.Paper,
    cornerRadius: Dp = 44.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .shadow(elevation = 24.dp, shape = shape, clip = false)
            .clip(shape)
            .background(backgroundColor),
        content = content,
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PhonePanelFramePreview() {
    ScanbyTheme {
        PhonePanelFrame(modifier = Modifier.padding(24.dp))
    }
}
