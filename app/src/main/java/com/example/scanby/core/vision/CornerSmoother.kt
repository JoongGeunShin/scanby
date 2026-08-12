package com.example.scanby.core.vision

import android.graphics.PointF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/**
 * One Euro Filter로 프레임 간 코너 좌표를 부드럽게 만든다. [DocQuadDetector]는 한 프레임만
 * 보고 판단하기 때문에(트랙 C에서 이미 신뢰 못 할 프레임은 걸러내지만) 신뢰할 만한 프레임들
 * 사이에서도 미세한 노이즈로 좌표가 흔들릴 수 있는데, 이 클래스가 그 흔들림을 완화한다
 * (`DOCS/corner_detection_tuning.md` 트랙 B).
 *
 * 4개 코너 × (x, y) = 8개의 독립적인 스칼라 채널에 각각 One Euro Filter를 적용한다
 * (좌표축끼리 섞이지 않게). 알고리즘은 Casiez et al.의 원 논문/MakeACopy의
 * `OneEuroCornerSmoother`와 동일한 구조 — 속도가 빠를수록 컷오프 주파수를 높여
 * (더 필터를 약하게 걸어) 정지해 있을 땐 부드럽게, 빠르게 움직일 땐 민첩하게 반응한다.
 *
 * @param minCutoff 값이 작을수록 정지 상태에서 더 부드럽지만(지터 감소) 움직임을 늦게 따라감
 * @param beta 값이 클수록 빠르게 움직일 때 필터가 더 민첩하게 반응함(속도에 비례해 컷오프 상승)
 * @param dCutoff 속도 추정 자체에 적용하는 저역통과 컷오프
 * @param resetDistanceFraction 프레임 대각선 대비 이 비율을 "튐"의 기준 스케일로 쓴다. 두
 *   군데에 쓰인다: (1) 이번 프레임의 원시 검출값이 바로 직전 프레임의 원시 검출값과 이만큼
 *   벌어지면 "불안정한 구간"(카메라 이동/블러/오검출)으로 보고 화면 갱신을 보류한다. (2)
 *   원시 검출값이 현재 화면에 표시 중인(스무딩된) 값과 이만큼 벌어지면 — (1)의 안정성 검사는
 *   통과한 상태에서 — 서서히 따라가지 않고 그 자리로 즉시 스냅한다(문서가 실제로 휙
 *   움직였을 때 계속 뒤처져 보이지 않도록).
 */
class CornerSmoother(
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.05,
    private val dCutoff: Double = 1.0,
    private val resetDistanceFraction: Double = 0.25,
) {
    private var initialized = false
    private var lastTimestampMs = 0L
    private val value = DoubleArray(CHANNEL_COUNT)
    private val derivative = DoubleArray(CHANNEL_COUNT)

    // 실기기 영상(Cases/SCENARIO_VIDEO/OUTCOMES)에서 확인한 두 단계의 문제:
    // (1, 2026-08-07 1차 수정) 한 프레임짜리 오검출(배경 물체에 낚이거나 코너가 붕괴된
    // 경우)도 "직전 스무딩 값과 크게 벌어짐" 하나만으로 곧바로 스냅되면서 화면에 순간적으로
    // 완전히 엉뚱한 사각형이 번쩍였음.
    // (2, 2026-08-07 2차 수정) 1차 수정(직전 raw와 비교해 "확정"되면 스냅)만으로는 카메라가
    // 실제로 움직이는 동안 — 연속 여러 프레임이 블러/불안정으로 계속 어긋나는 구간 — 은 여전히
    // 못 걸렀음. 연속된 두 오검출이 서로 "비슷하게 잘못" 잡히면 그 자체가 확정 조건을 만족해
    // 그대로 스냅해버렸기 때문.
    // → 지금 버전: "직전 스무딩 값과의 거리"가 아니라 "바로 직전 프레임의 raw와의 거리"를
    // 안정성 판단 기준으로 삼는다. 두 프레임 연속으로 raw끼리 서로 가까워야("안정됨") 그때
    // 비로소 화면 갱신(스무딩 또는 스냅)을 허용하고, 그렇지 않으면 — 즉 카메라가 움직이는
    // 중이든 오검출이든 raw 자체가 프레임마다 계속 흔들리는 동안은 — 화면을 이전 값 그대로
    // 고정한다. 이전에 시도했다가 되돌린 트랙 G(OCR 기반 검증, `DOCS/corner_detection_tuning.md`
    // 참고)와 달리 추가 추론이 전혀 없어 지연 문제가 재발할 걱정이 없다.
    private var lastRaw: List<PointF>? = null

    /** 새 장면을 시작할 때(문서가 사라졌다가 다시 인식되는 등) 호출 — 다음 [smooth] 호출이 기준점이 된다. */
    fun reset() {
        initialized = false
        lastTimestampMs = 0L
        lastRaw = null
    }

    /**
     * [raw]는 TL, TR, BR, BL 순서의 4개 좌표(한 프레임의 원시 검출값).
     * [frameDiagonal]은 튐 감지 기준 스케일(분석 프레임의 대각선 길이 정도면 충분).
     */
    fun smooth(raw: List<PointF>, timestampMs: Long, frameDiagonal: Float): List<PointF> {
        require(raw.size == 4) { "raw must have 4 corners" }

        if (!initialized || timestampMs <= lastTimestampMs) {
            snapTo(raw)
            lastTimestampMs = timestampMs
            lastRaw = raw
            return raw
        }

        val previousRaw = lastRaw
        lastRaw = raw
        val rawIsStable = previousRaw == null || frameDiagonal <= 0f ||
            maxDistanceBetween(raw, previousRaw) <= frameDiagonal * resetDistanceFraction
        if (!rawIsStable) {
            // 직전 프레임의 raw와도 크게 어긋남 — 카메라가 움직이는 중이거나 연속으로
            // 블러/오검출이 나는 구간으로 보고 이번 프레임은 화면에 반영하지 않는다.
            lastTimestampMs = timestampMs
            return currentSmoothedCorners()
        }

        if (frameDiagonal > 0f && maxDistanceFromCurrent(raw) > frameDiagonal * resetDistanceFraction) {
            snapTo(raw)
            lastTimestampMs = timestampMs
            return raw
        }

        val dtSeconds = (timestampMs - lastTimestampMs) / 1000.0
        lastTimestampMs = timestampMs

        return List(4) { corner ->
            val xi = corner * 2
            val yi = corner * 2 + 1
            val x = filterChannel(xi, raw[corner].x.toDouble(), dtSeconds)
            val y = filterChannel(yi, raw[corner].y.toDouble(), dtSeconds)
            PointF(x.toFloat(), y.toFloat())
        }
    }

    private fun currentSmoothedCorners(): List<PointF> =
        List(4) { corner -> PointF(value[corner * 2].toFloat(), value[corner * 2 + 1].toFloat()) }

    private fun maxDistanceBetween(a: List<PointF>, b: List<PointF>): Float {
        var maxDist = 0f
        for (corner in 0 until 4) {
            val dx = a[corner].x - b[corner].x
            val dy = a[corner].y - b[corner].y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dist > maxDist) maxDist = dist
        }
        return maxDist
    }

    private fun snapTo(raw: List<PointF>) {
        for (corner in 0 until 4) {
            value[corner * 2] = raw[corner].x.toDouble()
            value[corner * 2 + 1] = raw[corner].y.toDouble()
            derivative[corner * 2] = 0.0
            derivative[corner * 2 + 1] = 0.0
        }
        initialized = true
    }

    private fun maxDistanceFromCurrent(raw: List<PointF>): Float {
        var maxDist = 0f
        for (corner in 0 until 4) {
            val dx = raw[corner].x - value[corner * 2].toFloat()
            val dy = raw[corner].y - value[corner * 2 + 1].toFloat()
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dist > maxDist) maxDist = dist
        }
        return maxDist
    }

    private fun filterChannel(index: Int, rawValue: Double, dtSeconds: Double): Double {
        val rawDerivative = (rawValue - value[index]) / dtSeconds
        val smoothedDerivative = lowPass(rawDerivative, derivative[index], alpha(dCutoff, dtSeconds))
        derivative[index] = smoothedDerivative

        val cutoff = minCutoff + beta * abs(smoothedDerivative)
        val filtered = lowPass(rawValue, value[index], alpha(cutoff, dtSeconds))
        value[index] = filtered
        return filtered
    }

    private fun lowPass(current: Double, previous: Double, a: Double): Double =
        a * current + (1 - a) * previous

    private fun alpha(cutoff: Double, dtSeconds: Double): Double {
        val timeConstant = 1.0 / (2 * PI * cutoff)
        return 1.0 / (1.0 + timeConstant / dtSeconds)
    }

    companion object {
        private const val CHANNEL_COUNT = 8 // 4 corners * (x, y)
    }
}
