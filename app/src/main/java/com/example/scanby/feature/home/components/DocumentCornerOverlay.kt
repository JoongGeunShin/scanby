package com.example.scanby.feature.home.components

import android.graphics.PointF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.scanby.core.designsystem.theme.ScanbyColor
import kotlinx.coroutines.launch

// 오버레이 사각형이 새 좌표로 이동하는 애니메이션 길이. 분석 간격(HomeScreen의
// ANALYSIS_INTERVAL_MS)보다 살짝 짧게 잡아서, 다음 업데이트가 오기 직전에 애니메이션이 끝나
// 있도록 함 — 너무 길면 계속 "쫓아가는" 느낌이 나고, 너무 짧으면 다시 딱딱하게 보임.
private const val CORNER_ANIMATION_MS = 200

/**
 * 검출된 4개 모서리(TL, TR, BR, BL, [frameSize] 픽셀 기준)를 현재 오버레이 크기에 맞게
 * 스케일링해서 사각형 윤곽선으로 그린다. 분석 프레임과 프리뷰 화면의 실제 표시 해상도가
 * 완전히 같지는 않을 수 있어 Stage 4 단계에서는 "같은 비율로 늘려 맞추는" 근사만 한다 —
 * 정밀한 좌표 매핑은 Stage 5(원근 보정)에서 다시 다룰 예정.
 *
 * [corners]는 분석 간격마다 한 번씩만 갱신되는데, 그 값을 그대로 그리면 업데이트 사이 간격이
 * 눈에 보일 만큼 뚝뚝 끊겨 보인다(`DOCS/corner_detection_tuning.md` 진행 로그). 그래서 실제
 * 그리는 좌표는 [corners]로 바로 점프하지 않고, 코너별 [Animatable]로 이전 위치→새 위치를
 * [CORNER_ANIMATION_MS] 동안 부드럽게 보간한다 — 검출 자체는 여전히 초당 몇 번뿐이지만 화면
 * 표시는 프레임마다(최대 디스플레이 주사율로) 매끄럽게 움직인다.
 */
@Composable
fun DocumentCornerOverlay(
    corners: List<PointF>?,
    frameSize: IntSize?,
    modifier: Modifier = Modifier
) {
    val animatedCorners = remember {
        List(4) { Animatable(Offset.Zero, Offset.VectorConverter) }
    }
    var hasTarget by remember { mutableStateOf(false) }

    LaunchedEffect(corners) {
        if (corners != null && corners.size == 4) {
            val isFirstAppearance = !hasTarget
            hasTarget = true
            corners.forEachIndexed { i, p ->
                val target = Offset(p.x, p.y)
                if (isFirstAppearance) {
                    // 처음 나타날 땐 (0,0)에서 슬라이드해오면 어색하니 바로 그 자리에 배치.
                    animatedCorners[i].snapTo(target)
                } else {
                    launch { animatedCorners[i].animateTo(target, tween(CORNER_ANIMATION_MS)) }
                }
            }
        } else {
            hasTarget = false
        }
    }

    Canvas(modifier = modifier) {
        if (!hasTarget || frameSize == null || frameSize.width <= 0 || frameSize.height <= 0) {
            return@Canvas
        }
        // CameraPreview의 PreviewView는 scaleType을 따로 안 정해서 기본값인 FILL_CENTER를
        // 쓴다 — 프레임 비율과 뷰 비율이 다르면 "등배율로 확대해서 넘치는 쪽을 잘라내는"
        // 방식으로 뷰를 꽉 채운다. 예전엔 가로/세로를 각각 다른 배율로 늘려 딱 맞추기만
        // 했는데(scaleX ≠ scaleY 가능), 이러면 FILL_CENTER가 실제로 화면에 그리는 것과
        // 다른 변환이 되어 frameSize 비율이 캔버스 비율과 다를 때 오버레이가 실제 문서
        // 위치에서 어긋난다(실기기 영상에서 확인). 반드시 두 축에 같은 배율을 쓰고, 그
        // 배율로 넘치는 만큼을 중앙 기준으로 빼줘야 FILL_CENTER와 같은 자리에 그려진다.
        val scale = maxOf(size.width / frameSize.width, size.height / frameSize.height)
        val cropOffsetX = (frameSize.width * scale - size.width) / 2f
        val cropOffsetY = (frameSize.height * scale - size.height) / 2f
        val points = animatedCorners.map {
            Offset(it.value.x * scale - cropOffsetX, it.value.y * scale - cropOffsetY)
        }

        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1..3) lineTo(points[i].x, points[i].y)
            close()
        }
        drawPath(path, color = ScanbyColor.Accent, style = Stroke(width = 4.dp.toPx()))
    }
}
