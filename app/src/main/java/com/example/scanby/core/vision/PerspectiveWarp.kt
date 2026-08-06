package com.example.scanby.core.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.hypot
import androidx.core.graphics.createBitmap

/**
 * 코너 4개(TL, TR, BR, BL 순서, [source] 픽셀 좌표 기준)로 정의된 사각형 영역을 [source]에서
 * "위에서 내려다본 것처럼" 평평하게 펴서 새 비트맵으로 반환한다 — Stage 5(원근 보정)의
 * 핵심 로직. `Bitmap.createBitmap(w, h, ...)` + `Canvas.drawBitmap(source, matrix, paint)`로
 * `Matrix.setPolyToPoly()`가 계산한 4점 원근 변환(단순 어파인이 아니라 진짜 perspective)을
 * 적용한다.
 *
 * 결과 크기(가로/세로)는 코너 사이 거리로부터 추정한다 — 문서가 카메라에 비스듬히 잡혀
 * 있어도 그 비율을 그대로 살리기 위해(고정 비율로 늘리거나 줄이지 않음). 위/아래 변, 왼쪽/
 * 오른쪽 변의 길이가 원근 때문에 서로 다를 수 있어 더 긴 쪽을 기준으로 잡는다(잘림 방지) —
 * OpenCV/pyimagesearch의 표준 four-point-transform과 동일한 방식.
 *
 * [corners]가 기하학적으로 말이 안 되는 경우(네 점이 한 직선에 가까워 폭/높이 추정이 0이 되는
 * 등)엔 원근 변환을 계산할 수 없어 null을 반환한다 — 호출 측이 이 경우 원본 이미지를 그대로
 * 쓸지 결정하면 된다.
 */
fun warpToFlatDocument(source: Bitmap, corners: List<PointF>): Bitmap? {
    if (corners.size != 4) return null
    val tl = corners[0]
    val tr = corners[1]
    val br = corners[2]
    val bl = corners[3]

    val outWidth = maxOf(distance(tl, tr), distance(bl, br)).toInt().coerceAtLeast(1)
    val outHeight = maxOf(distance(tl, bl), distance(tr, br)).toInt().coerceAtLeast(1)

    val src = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
    val dst = floatArrayOf(
        0f, 0f,
        outWidth.toFloat(), 0f,
        outWidth.toFloat(), outHeight.toFloat(),
        0f, outHeight.toFloat(),
    )

    val matrix = Matrix()
    if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return null

    val output = createBitmap(outWidth, outHeight)
    val paint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
    }
    Canvas(output).drawBitmap(source, matrix, paint)
    return output
}

private fun distance(a: PointF, b: PointF): Float = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
