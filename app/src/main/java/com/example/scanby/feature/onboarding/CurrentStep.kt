package com.example.scanby.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.scanby.core.designsystem.theme.ScanbyColor
import com.example.scanby.core.designsystem.theme.ScanbyTheme

private val DotRadius = 5.dp
private val RingRadius = 10.dp
private val RingStrokeWidth = 1.5.dp
private val LineStrokeWidth = 1.dp
private val DotSpacing = 50.dp
private val LineGap = 12.dp
private const val FillAnimationDurationMs = 750

@Composable
fun CurrentStep(
    currentStep: Int,
    totalSteps: Int = 4,
    modifier: Modifier = Modifier,
) {
     val progress = remember { Animatable(0f) }
    LaunchedEffect(currentStep) {
        progress.snapTo(0f)
        progress.animateTo(1f,
            animationSpec =
                tween(FillAnimationDurationMs,
                    easing = FastOutSlowInEasing)
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp),
    ) {
        val dotSpacingPx = DotSpacing.toPx()
        val dotRadiusPx = DotRadius.toPx()
        val ringRadiusPx = RingRadius.toPx()
        val lineStrokePx = LineStrokeWidth.toPx()
        val ringStrokePx = RingStrokeWidth.toPx()
        val lineGapPx = LineGap.toPx()

        val clusterWidth = dotSpacingPx * (totalSteps - 1)
        val startX = (size.width - clusterWidth) / 2f
        val centerY = size.height / 2f
        fun centerXOf(index: Int) = startX + dotSpacingPx * index

        for (i in 0 until totalSteps - 1) {
            val fromX = centerXOf(i) + dotRadiusPx + lineGapPx
            val toX = centerXOf(i + 1) - dotRadiusPx - lineGapPx
            when {
                i < currentStep - 1 -> drawLine(
                    color = ScanbyColor.Accent,
                    start = Offset(fromX, centerY),
                    end = Offset(toX, centerY),
                    strokeWidth = lineStrokePx,
                    cap = StrokeCap.Round,
                )
                i == currentStep - 1 -> {
                    drawLine(
                        color = ScanbyColor.StepDotInactive,
                        start = Offset(fromX, centerY),
                        end = Offset(toX, centerY),
                        strokeWidth = lineStrokePx,
                        cap = StrokeCap.Round,
                    )
                    val filledToX = fromX + (toX - fromX) * progress.value
                    drawLine(
                        color = ScanbyColor.Accent,
                        start = Offset(fromX, centerY),
                        end = Offset(filledToX, centerY),
                        strokeWidth = lineStrokePx,
                        cap = StrokeCap.Round,
                    )
                }
                else -> drawLine(
                    color = ScanbyColor.StepDotInactive,
                    start = Offset(fromX, centerY),
                    end = Offset(toX, centerY),
                    strokeWidth = lineStrokePx,
                    cap = StrokeCap.Round,
                )
            }
        }

        for (j in 0 until totalSteps) {
            val cx = centerXOf(j)
            when {
                j < currentStep -> drawCircle(ScanbyColor.Accent, dotRadiusPx, Offset(cx, centerY))
                j == currentStep -> {
                    val dotColor = lerp(ScanbyColor.StepDotInactive, ScanbyColor.Accent, progress.value)
                    drawCircle(dotColor, dotRadiusPx, Offset(cx, centerY))
                    drawArc(
                        color = ScanbyColor.Accent,
                        startAngle = -90f,
                        sweepAngle = progress.value * 360f,
                        useCenter = false,
                        topLeft = Offset(cx - ringRadiusPx, centerY - ringRadiusPx),
                        size = Size(ringRadiusPx * 2, ringRadiusPx * 2),
                        style = Stroke(width = ringStrokePx, cap = StrokeCap.Round),
                    )
                }
                else -> drawCircle(ScanbyColor.StepDotInactive, dotRadiusPx, Offset(cx, centerY))
            }
        }
    }
}

@Composable
@Preview(showBackground = true, widthDp = 360, heightDp = 720)
private fun CurrentStepPreview() {
    ScanbyTheme {
        CurrentStep(currentStep = 1)
    }
}
