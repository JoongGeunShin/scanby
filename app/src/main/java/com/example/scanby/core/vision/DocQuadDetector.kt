package com.example.scanby.core.vision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * DocQuadNet-256 (Apache 2.0, https://github.com/egdels/makeacopy) 기반 문서 모서리 검출기.
 *
 * 모델은 256x256 입력을 받아 64x64 코너 히트맵 4장(TL/TR/BR/BL 순서) + 64x64 마스크 로짓
 * 1장을 출력한다. 좌표는 채널별 3×3 지수가중 중심(soft-argmax, [refinedPeak] 참고)으로
 * 서브픽셀 정밀도까지 구한다 — 순수 argmax만 쓰면 64격자 단위로 좌표가 끊겨서(4px/셀)
 * 화면에서 딱딱하게 잡히는 문제가 있었음 (`DOCS/corner_detection_tuning.md` 트랙 A).
 *
 * 아래 신뢰도 체크(트랙 C)를 하나라도 통과하지 못하면 [detectCorners]는 null을 반환한다:
 * - 4개 좌표가 볼록사각형 + TL→TR→BR→BL 시계방향을 이루는지
 * - 좌표가 프레임 경계에서 너무 크게 벗어나지 않는지
 *
 * 처음엔 여기에 "히트맵 피크가 뚜렷한지"(peak sigma)와 "마스크가 너무 뭉개져 있지 않은지"
 * (mask 평균 확률) 체크도 하드 게이트로 추가했었는데, 실기기에서 거의 모든 프레임이
 * 거부되어 오버레이가 아예 사라지는 문제가 있었다. 원인을 MakeACopy 원본 소스에서 찾아보니
 * — 그쪽도 동일한 개념의 "suspicious frame" 체크(`evaluateSuspicious`)를 만들어뒀지만,
 * 실제 production 코드(`DocQuadDetector.detect()`)에서는 그 판정으로 프레임을 버리는
 * 코드가 **주석 처리되어 비활성 상태**였다 — 실사용에서 오탐이 너무 많아서 만든 사람들도
 * 결국 안 쓰기로 한 것으로 보임. 실제로 활성화되어 있던 건 볼록성+경계 체크
 * (`isValidQuad`)뿐이었다. 그래서 우리도 그 두 하드 게이트는 제거하고 볼록성+경계 체크만
 * 남겼다 (`DOCS/corner_detection_tuning.md` 진행 로그 2026-08-05 참고).
 *
 * 시간축 스무딩(One Euro Filter, 트랙 B)은 이 클래스가 아니라 [CornerSmoother]가 담당한다
 * — 이 클래스는 "한 프레임을 믿을 수 있는가/좌표가 무엇인가"만 책임지고, 여러 프레임에
 * 걸친 상태(이전 값, 속도 등)는 갖지 않는다.
 *
 * [mask_logits] 출력을 코너 히트맵에 픽셀 단위로 곱하는 가중치도 시도했다가 **되돌림**
 * (트랙 E, 2026-08-05) — 코너는 정의상 문서 마스크의 "경계" 위에 있는 점이라, 세그멘테이션
 * 마스크가 경계 부근에서 확신도가 가장 낮게 나오는 통상적인 특성 때문에 정작 찾아야 할
 * 코너 피크 자체가 억제되는 역효과가 남. 실기기에서 개선 없이 오히려 초기 검출조차 잘 안
 * 되는 회귀만 발생해서 제거함 (`DOCS/corner_detection_tuning.md` 트랙 E 참고).
 *
 * [mask_logits]는 트랙 F(CASE_C 대응)에서 다른 용도로 다시 씀 — 코너 좌표 계산에는 전혀
 * 관여하지 않고, "마스크(문서) 확률이 실제 이미지 경계(letterbox 패딩이 아닌 쪽)에서도
 * 높게 나오는가"만 확인해 [DocQuadDetection.touchesFrameEdge]로 노출한다. 문서가 카메라
 * 프레임 자체를 벗어나 잘린 경우(`Cases/CASE_C`) 물리적 코너 자체가 화면 밖에 있어서
 * 코너 히트맵만으로는 원리적으로 표현이 안 되는데, 마스크는 잘려도 여전히 "경계에 문서가
 * 있다"는 신호를 주므로 이 판정에 적합하다. 트랙 E가 실패했던 지점(문서/배경이 갈리는
 * "마스크 경계")과 여기서 보는 지점(카메라 "프레임 테두리")은 서로 다른 위치라 같은 함정에
 * 걸리지 않는다 — 프레임 테두리는 대개 완전히 문서 안이거나 완전히 배경이라, 마스크가
 * 애매해지는 "문서/배경 전환 지점"이 아니다.
 */
class DocQuadDetector private constructor(
    private val session: OrtSession,
) : AutoCloseable {

    // detectCorners()와 close()가 같은 session에 동시에 접근하지 못하게 막는 락.
    // close()가 session을 파괴하는 도중에 다른 스레드가 여전히 session.run()을 부르면
    // (예: 화면을 벗어나 close()가 호출된 순간에도 이전 프레임의 코루틴이 아직
    // detectCorners 안에 있는 경우) 이미 파괴된 네이티브 뮤텍스를 잠그려다 프로세스 전체가
    // SIGABRT로 죽는 크래시가 났었다 — 코루틴이 취소돼도 이미 진입한 session.run() 같은
    // 블로킹 네이티브 호출은 멈추지 않기 때문. detectCorners()는 read lock(여러 프레임이
    // 겹쳐도 동시에 허용), close()는 write lock(진행 중인 detectCorners가 끝날 때까지 대기
    // 후 배타적으로 닫음)으로 분리해서 이 경쟁을 없앤다.
    private val lock = ReentrantReadWriteLock()
    @Volatile private var closed = false

    /**
     * 성공 시 TL, TR, BR, BL 순서의 4개 좌표(입력 [source] 픽셀 기준)를 [DocQuadDetection.corners]에
     * 담아 반환한다. 추론 실패(형식 불일치 등)나 신뢰도 체크(트랙 C) 실패 시 `corners`는
     * null이지만, [DocQuadDetection.touchesFrameEdge]는 코너 검출 성패와 무관하게 별도로
     * 계산된다 — 코너가 프레임 밖으로 나가 신뢰도 체크에서 걸러진 바로 그 상황이야말로
     * "문서가 화면을 벗어났다"는 신호가 가장 필요한 순간이기 때문(위 클래스 문서 주석
     * 참고). 예외를 던지지 않고 항상 값을 반환한다 — 호출 측(카메라 분석 콜백)이 매 프레임
     * try/catch를 하지 않아도 되게. [close]가 이미 호출된 뒤라면 session을 건드리지 않고
     * 즉시 빈 결과를 반환한다.
     */
    fun detectCorners(source: Bitmap): DocQuadDetection = lock.read {
        if (closed) return@read DocQuadDetection(null, false)
        try {
            val letterbox = Letterbox.create(source.width, source.height, INPUT_SIZE, INPUT_SIZE)
            val letterboxed = renderLetterbox(source, letterbox)
            val inputData = bitmapToNchwFloat01(letterboxed)
            letterboxed.recycle()

            val env = OrtEnvironment.getEnvironment()
            val inputShape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), inputShape).use { inputTensor ->
                val inferenceStartMs = SystemClock.elapsedRealtime()
                val rawResults = session.run(mapOf(INPUT_NAME to inputTensor))
                Log.d(TAG, "ONNX 추론 소요시간: ${SystemClock.elapsedRealtime() - inferenceStartMs}ms")
                rawResults.use { results ->
                    val cornerHeatmaps = extractChannels(results, CORNER_HEATMAPS_NAME, CORNER_COUNT)
                        ?: return@read DocQuadDetection(null, false)

                    val cellSize = INPUT_SIZE.toFloat() / OUTPUT_SIZE
                    val corners = cornerHeatmaps.map { heatmap ->
                        val (x64, y64) = refinedPeak(heatmap)
                        letterbox.inverse(x64 * cellSize, y64 * cellSize)
                    }

                    // 트랙 C: 기하학적으로 말이 안 되는 사각형이거나 화면 밖으로 크게 벗어나면 버림.
                    // (MakeACopy production 코드에서 실제로 살아있던 체크는 볼록성+경계 둘뿐이었으나
                    // — 위 클래스 문서 주석 참고 — 실기기 영상(Cases/SCENARIO_VIDEO) 테스트에서 그
                    // 둘만으로는 못 거르는 오검출을 하나 더 발견해서 추가함: 인접한 두 꼭짓점이 거의
                    // 겹쳐서 "찌그러진 나비넥타이" 모양이 되는 경우 — 방향은 일관되어 볼록성 체크를
                    // 통과하지만 명백히 잘못된 사각형이다. [isReasonableQuad] 참고.
                    val validCorners = corners.takeIf {
                        isConvexClockwise(it) &&
                            isWithinBounds(it, source.width, source.height) &&
                            isReasonableQuad(it)
                    }

                    // 트랙 F: 코너 신뢰도와 무관하게, 마스크가 실제 이미지 경계(letterbox
                    // 패딩이 아닌 쪽)에서도 "문서"로 나오는지 확인.
                    val maskChannel = extractChannels(results, MASK_LOGITS_NAME, 1)?.get(0)
                    val touchesFrameEdge = maskChannel?.let { maskTouchesFrameEdge(it, letterbox, cellSize) }
                        ?: false

                    DocQuadDetection(validCorners, touchesFrameEdge)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "코너 검출 실패: ${e.message}")
            DocQuadDetection(null, false)
        }
    }

    /**
     * [detectCorners] 한 번의 결과. [corners]는 트랙 C 신뢰도 체크까지 통과한 4점(없으면
     * null), [touchesFrameEdge]는 마스크 기준으로 문서가 카메라 프레임 경계에 닿아 있는지
     * — 둘은 서로 독립적으로 계산되므로 `corners`가 null이어도 `touchesFrameEdge`는 true일
     * 수 있다(오히려 CASE_C처럼 코너가 화면 밖에 있어 신뢰도 체크에서 막 걸러진 경우가
     * 전형적인 예).
     */
    data class DocQuadDetection(
        val corners: List<PointF>?,
        val touchesFrameEdge: Boolean,
    )

    override fun close() {
        lock.write {
            if (closed) return@write
            closed = true
            session.close()
        }
    }

    /** ONNX 출력 텐서를 [Array]<[Array]<[FloatArray]>>(채널×높이×너비)로 꺼낸다. */
    private fun extractChannels(
        results: OrtSession.Result,
        outputName: String,
        expectedChannelCount: Int,
    ): Array<Array<FloatArray>>? {
        val value = results.get(outputName).orElse(null)?.value as? Array<*> ?: return null
        val batch0 = value.getOrNull(0) as? Array<*> ?: return null
        if (batch0.size != expectedChannelCount) return null
        @Suppress("UNCHECKED_CAST")
        return batch0 as Array<Array<FloatArray>>
    }

    /**
     * 마스크(문서 확률)가 letterbox 패딩이 아닌 "실제 카메라 이미지가 있는" 4면 경계 각각에서
     * 높게 나오는지 확인한다 — 문서가 카메라 프레임 자체를 벗어나 잘렸는지(CASE_C) 판정하는
     * 용도. [letterbox]는 정사각형(256×256) 입력 중 실제 [source] 이미지가 차지하는 영역의
     * 오프셋을 담고 있다.
     *
     * (이전 버전 버그, 실측으로 발견) 예전엔 "오프셋이 없는 축만 검사, 있는 축은 통째로
     * 스킵"했는데, 이 앱처럼 항상 세로(9:16)로 촬영하는 경우 letterbox는 항상 가로축에
     * 패딩이 생겨서(`offsetXCells`가 항상 0보다 큼) 좌/우 경계를 **절대** 검사하지 못하는
     * 구조적 사각지대가 있었다 — 정작 실사용에서 카메라를 문서에 가까이 댈 때 프레임을
     * 벗어나는 건 주로 좌/우인데도. `Cases/SCENARIO_VIDEO`로 찍은 실측 영상 210프레임 중
     * 단 한 번도 true가 나오지 않아 발견함. 이제는 패딩 유무로 축 자체를 건너뛰지 않고,
     * letterbox 오프셋으로 실제 콘텐츠 영역의 4면 경계 인덱스를 구해서 항상 다 검사한다
     * (오프셋이 0인 축은 자연히 경계가 격자 0/last가 되어 이전과 동일하게 동작).
     *
     * (이전 버전 버그 2) 4면의 확률을 하나의 리스트로 합쳐 평균 냈었는데, 문서가 한쪽
     * 변으로만 넘칠 때 나머지 세 변의 "안 닿음"(0에 가까운) 값에 신호가 희석되어 평균이
     * 임계값을 못 넘는 문제가 있었다. 지금은 변마다 따로 평균 내고, 네 변 중 하나라도
     * 임계값을 넘으면 true — "닿아 있다"는 판정은 OR이지 평균이 아니다.
     */
    private fun maskTouchesFrameEdge(
        mask: Array<FloatArray>,
        letterbox: Letterbox,
        cellSize: Float,
    ): Boolean {
        val last = OUTPUT_SIZE - 1
        val rawLeft = Math.round(letterbox.offsetX / cellSize)
        val rawTop = Math.round(letterbox.offsetY / cellSize)
        val left = rawLeft.coerceIn(0, last)
        val right = (last - rawLeft).coerceIn(0, last)
        val top = rawTop.coerceIn(0, last)
        val bottom = (last - rawTop).coerceIn(0, last)

        fun sideProb(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.average()

        val leftProb = sideProb((top..bottom).map { y -> sigmoid(mask[y][left].toDouble()) })
        val rightProb = sideProb((top..bottom).map { y -> sigmoid(mask[y][right].toDouble()) })
        val topProb = sideProb((left..right).map { x -> sigmoid(mask[top][x].toDouble()) })
        val bottomProb = sideProb((left..right).map { x -> sigmoid(mask[bottom][x].toDouble()) })

        return maxOf(leftProb, rightProb, topProb, bottomProb) >= MASK_BORDER_PROB_THRESHOLD
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + kotlin.math.exp(-x))

    /** TL→TR→BR→BL이 볼록사각형 + 시계방향(이미지 좌표계, y가 아래로 증가)을 이루는지 확인. */
    private fun isConvexClockwise(corners: List<PointF>): Boolean {
        if (corners.size != 4) return false
        var prevSign = 0.0
        for (i in 0 until 4) {
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            val d = corners[(i + 2) % 4]
            val abx = (b.x - a.x).toDouble()
            val aby = (b.y - a.y).toDouble()
            val bdx = (d.x - b.x).toDouble()
            val bdy = (d.y - b.y).toDouble()
            val cross = abx * bdy - aby * bdx
            if (!cross.isFinite() || cross == 0.0) return false
            val sign = kotlin.math.sign(cross)
            if (i == 0) {
                if (sign < 0.0) return false
                prevSign = sign
            } else if (sign != prevSign) {
                return false
            }
        }
        return true
    }

    /** 좌표가 프레임 경계에서 [OUT_OF_BOUNDS_MARGIN_FRACTION] 이상 벗어나지 않았는지 확인. */
    private fun isWithinBounds(corners: List<PointF>, width: Int, height: Int): Boolean {
        val marginX = width * OUT_OF_BOUNDS_MARGIN_FRACTION
        val marginY = height * OUT_OF_BOUNDS_MARGIN_FRACTION
        return corners.all { p ->
            p.x.isFinite() && p.y.isFinite() &&
                p.x >= -marginX && p.x <= width + marginX &&
                p.y >= -marginY && p.y <= height + marginY
        }
    }

    /**
     * 네 변의 길이 중 가장 짧은 변이 가장 긴 변의 [MIN_SIDE_RATIO_THRESHOLD] 미만이면 버린다.
     * 인접한 두 꼭짓점이 실제로는 거의 같은 지점(예: 화면 밖으로 나가 신뢰도가 낮은 코너가
     * 옆 코너 쪽으로 끌려온 경우)인데도 [isConvexClockwise]는 "회전 방향이 일관되는가"만
     * 보기 때문에 이런 찌그러진 사각형을 걸러내지 못한다. 실측(Cases/SCENARIO_VIDEO, 9개
     * 시나리오 210프레임)으로 경계값을 잡았음 — 실제 오검출(코너 붕괴)은 min/max ≤ 0.079,
     * 정상 검출(카메라를 매우 눕혀 찍은 극단적 원근) 중 가장 낮았던 값은 0.135였고, 그 사이인
     * 0.12를 경계로 둠.
     */
    private fun isReasonableQuad(corners: List<PointF>): Boolean {
        val sides = (0 until 4).map { i ->
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            kotlin.math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        }
        val maxSide = sides.max()
        if (maxSide <= 0.0) return false
        return (sides.min() / maxSide) >= MIN_SIDE_RATIO_THRESHOLD
    }

    /**
     * argmax 픽셀 주변 3×3 창의 지수가중 중심(soft-argmax)으로 서브픽셀 좌표를 구한다.
     * 반환값은 64×64 격자 좌표계(픽셀 중심 보정 포함) 기준이라, 그대로 4를 곱하면 256
     * 좌표계가 된다 — [DocQuadDetector] 상단 문서 주석 참고, MakeACopy의 REFINE_3X3 방식과 동일.
     *
     * 순수 argmax(격자칸 중심에만 스냅)만 쓰던 이전 버전은 카메라 프레임처럼 스케일업된
     * 화면에서 모서리가 "딱딱하게" 격자 단위로 끊겨 잡히는 문제가 있었음
     * (`DOCS/corner_detection_tuning.md` 트랙 A).
     */
    private fun refinedPeak(heatmap: Array<FloatArray>): Pair<Float, Float> {
        var best = Float.NEGATIVE_INFINITY
        var bestX = 0
        var bestY = 0
        for (y in heatmap.indices) {
            val row = heatmap[y]
            for (x in row.indices) {
                val v = row[x]
                if (v > best) {
                    best = v
                    bestX = x
                    bestY = y
                }
            }
        }

        val x0 = (bestX - 1).coerceAtLeast(0)
        val x1 = (bestX + 1).coerceAtMost(OUTPUT_SIZE - 1)
        val y0 = (bestY - 1).coerceAtLeast(0)
        val y1 = (bestY + 1).coerceAtMost(OUTPUT_SIZE - 1)

        var maxLogit = Double.NEGATIVE_INFINITY
        for (y in y0..y1) {
            val row = heatmap[y]
            for (x in x0..x1) {
                val v = row[x].toDouble()
                if (v > maxLogit) maxLogit = v
            }
        }

        var sumW = 0.0
        var sumX = 0.0
        var sumY = 0.0
        for (y in y0..y1) {
            val row = heatmap[y]
            for (x in x0..x1) {
                // maxLogit을 빼고 exp — 오버플로 없이 안정적으로 계산하기 위한 softmax 관용구.
                val w = kotlin.math.exp(row[x].toDouble() - maxLogit)
                sumW += w
                sumX += w * (x + 0.5)
                sumY += w * (y + 0.5)
            }
        }

        return if (sumW == 0.0 || !sumW.isFinite()) {
            (bestX + 0.5f) to (bestY + 0.5f)
        } else {
            (sumX / sumW).toFloat() to (sumY / sumW).toFloat()
        }
    }

    /** RGB, 0..1 정규화, NCHW(채널 우선) 순서로 펼친 float 배열. 학습 시 전처리와 동일하게 맞춤. */
    private fun bitmapToNchwFloat01(bitmap: Bitmap): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val hw = w * h
        val pixels = IntArray(hw)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 흰 종이 위의 흰 배경처럼 명암 차이가 좁은 구간에 몰려 있는 장면은 경계 신호가
        // 약해서 검출이 잘 안 되는 경우가 있었음 — 모델에 넣기 전에 채널별로 대비를
        // 늘려서 그 신호를 조금이라도 더 도드라지게 함 (`DOCS/corner_detection_tuning.md`).
        // 레터박스 여백(중간 회색)도 히스토그램에 같이 섞이지만, 여백이 전체 프레임을
        // 거의 다 차지하는 극단적인 경우가 아니면 1%/99% 컷오프에 큰 영향은 없다고 보고
        // 일단은 단순하게 전체 픽셀 기준으로 계산 — 실기기에서 문제되면 letterbox 내부
        // 콘텐츠 영역만 골라 계산하도록 다듬을 것.
        stretchChannelContrast(pixels, 16) // R
        stretchChannelContrast(pixels, 8)  // G
        stretchChannelContrast(pixels, 0)  // B

        val out = FloatArray(3 * hw)
        for (i in 0 until hw) {
            val c = pixels[i]
            out[i] = ((c shr 16) and 0xFF) / 255f
            out[hw + i] = ((c shr 8) and 0xFF) / 255f
            out[2 * hw + i] = (c and 0xFF) / 255f
        }
        return out
    }

    /**
     * [channelShift]로 지정한 채널(R=16/G=8/B=0)의 1~99 퍼센타일 구간을 0~255 전체로
     * 늘린다(min-max 대신 퍼센타일 컷오프를 쓰는 이유: 튀는 픽셀 한두 개 때문에 대비가
     * 전혀 늘어나지 않는 걸 방지). 이미 대비 폭이 충분히 넓으면 건드리지 않는다.
     */
    private fun stretchChannelContrast(pixels: IntArray, channelShift: Int) {
        val histogram = IntArray(256)
        for (p in pixels) {
            histogram[(p shr channelShift) and 0xFF]++
        }

        val clipCount = (pixels.size * CONTRAST_CLIP_PERCENTILE).toInt()

        var cumulative = 0
        var lowBound = 0
        for (v in 0..255) {
            cumulative += histogram[v]
            if (cumulative > clipCount) {
                lowBound = v
                break
            }
        }

        cumulative = 0
        var highBound = 255
        for (v in 255 downTo 0) {
            cumulative += histogram[v]
            if (cumulative > clipCount) {
                highBound = v
                break
            }
        }

        if (highBound <= lowBound) return // 이미 대비가 충분히 넓으면 그대로 둠

        val scale = 255f / (highBound - lowBound)
        val clearMask = (0xFF shl channelShift).inv()
        for (i in pixels.indices) {
            val value = (pixels[i] shr channelShift) and 0xFF
            val stretched = (((value - lowBound) * scale)).coerceIn(0f, 255f).toInt()
            pixels[i] = (pixels[i] and clearMask) or (stretched shl channelShift)
        }
    }

    /** 종횡비를 유지하며 [INPUT_SIZE]x[INPUT_SIZE]로 레터박스 리사이즈. 여백은 중간 회색으로 채운다. */
    private fun renderLetterbox(source: Bitmap, letterbox: Letterbox): Bitmap {
        val out = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(LETTERBOX_PAD_COLOR)

        val dst = RectF(
            letterbox.offsetX,
            letterbox.offsetY,
            letterbox.offsetX + source.width * letterbox.scale,
            letterbox.offsetY + source.height * letterbox.scale,
        )
        val paint = Paint().apply {
            isFilterBitmap = true
            isDither = true
            isAntiAlias = true
        }
        canvas.drawBitmap(source, null, dst, paint)
        return out
    }

    /** 원본 이미지를 [dstW]x[dstH]에 종횡비 유지한 채 맞추기 위한 스케일/오프셋. */
    private class Letterbox private constructor(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
    ) {
        fun inverse(x: Float, y: Float): PointF =
            PointF((x - offsetX) / scale, (y - offsetY) / scale)

        companion object {
            fun create(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Letterbox {
                val scale = minOf(dstW.toFloat() / srcW, dstH.toFloat() / srcH)
                val offsetX = (dstW - srcW * scale) / 2f
                val offsetY = (dstH - srcH * scale) / 2f
                return Letterbox(scale, offsetX, offsetY)
            }
        }
    }

    companion object {
        const val INPUT_SIZE = 256
        const val OUTPUT_SIZE = 64
        private const val CORNER_COUNT = 4
        private const val INPUT_NAME = "input"
        private const val CORNER_HEATMAPS_NAME = "corner_heatmaps"
        private const val MASK_LOGITS_NAME = "mask_logits"
        private const val MODEL_ASSET_PATH = "docquad/docquadnet256_trained_opset17.ort"

        // MakeACopy production 코드에서 실제로 활성화되어 있던 것과 동일한 값.
        private const val OUT_OF_BOUNDS_MARGIN_FRACTION = 0.25

        // isReasonableQuad 판정 경계값. 실측 근거는 해당 함수 문서 주석 참고.
        private const val MIN_SIDE_RATIO_THRESHOLD = 0.12f

        // 프레임 경계 격자 줄의 마스크(문서) 확률 평균이 이 값 이상이면 "문서가 화면
        // 경계에 닿아 있다"고 판단한다 (트랙 F). 코너 히트맵 게이트(트랙 C)처럼 하드
        // 게이트로 프레임 자체를 버리는 게 아니라 별도 신호로만 쓰이므로, 오탐이 있어도
        // 최악의 경우 안내 문구가 한 번 잘못 뜨는 정도라 트랙 C만큼 보수적일 필요는 없음 —
        // 실기기 확인 후 오탐/누락 비율을 보고 조정할 값.
        private const val MASK_BORDER_PROB_THRESHOLD = 0.5

        // 채널별 대비 강화 시 위/아래로 잘라낼 비율(1%). 너무 크면 정상적인 장면에서도
        // 과도하게 대비가 튀고, 너무 작으면 노이즈 픽셀 하나에 전체 스케일이 흔들린다.
        private const val CONTRAST_CLIP_PERCENTILE = 0.01f

        // 레터박스 여백을 중간 회색(RGB 128,128,128)으로 채우면 순검정 대비 경계에서
        // 생기는 가짜 엣지가 줄어든다 (원본 MakeACopy 구현에서 확인된 경험적 근거).
        private val LETTERBOX_PAD_COLOR = 0xFF808080.toInt()

        private const val TAG = "DocQuadDetector"

        /** 모델 로딩(에셋 복사 + 세션 생성)은 블로킹 I/O이므로 반드시 백그라운드 스레드에서 호출할 것. */
        fun create(context: Context): DocQuadDetector {
            val env = OrtEnvironment.getEnvironment()
            val modelFile = copyAssetToCache(context, MODEL_ASSET_PATH)
            val session = createSession(env, modelFile.absolutePath)
            return DocQuadDetector(session)
        }

        /**
         * XNNPACK(ARM CPU 가속 커널)을 켜서 추론 속도를 높인다. 일부 기기에서 XNNPACK
         * 초기화가 실패할 수 있어, 실패 시엔 가속 없는 기본 CPU 세션으로 폴백한다.
         * NNAPI는 일부 API 레벨에서 네이티브 크래시 이슈가 알려져 있어(MakeACopy 원본
         * 참고) 지금 단계에서는 시도하지 않음 — 필요해지면 추가 검토.
         */
        private fun createSession(env: OrtEnvironment, modelPath: String): OrtSession {
            return try {
                OrtSession.SessionOptions().use { options ->
                    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    options.setIntraOpNumThreads(
                        (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                    )
                    options.addXnnpack(emptyMap())
                    env.createSession(modelPath, options)
                }
            } catch (e: Exception) {
                Log.w(TAG, "XNNPACK 세션 생성 실패, 가속 없는 CPU 세션으로 대체: ${e.message}")
                OrtSession.SessionOptions().use { options ->
                    env.createSession(modelPath, options)
                }
            }
        }

        private fun copyAssetToCache(context: Context, assetPath: String): File {
            val outFile = File(context.cacheDir, assetPath.substringAfterLast('/'))
            if (!outFile.exists()) {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
            }
            return outFile
        }
    }
}
