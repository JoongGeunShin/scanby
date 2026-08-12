package com.example.scanby.feature.home.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import com.example.scanby.core.camera.uprightBitmap
import com.example.scanby.core.media.saveToGallery
import com.example.scanby.core.vision.warpToFlatDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Stage 5(원근 보정) — 촬영 시점의 마지막 검출 코너([corners], [analyzedFrameSize] 픽셀
 * 좌표 기준)를 실제 촬영된 고해상도 사진 좌표계로 스케일링한 뒤 [warpToFlatDocument]로 편
 * 이미지를 저장한다. 코너 검출이 아직 정확하지 않은 케이스(문서와 비슷한 색상의 배경 등)가
 * 있다는 걸 알고 있지만, 이번 단계 범위 밖이라 검출값을 그대로 신뢰하고 진행한다 — 코너가
 * 아예 없거나(문서 미검출) 기하학적으로 계산이 안 되는 경우엔 원본을 그대로 저장해 최소한
 * "촬영 자체는 실패하지 않게" 한다.
 *
 * `ImageCapture.OnImageCapturedCallback`(파일 저장이 아니라 메모리 상의 [ImageProxy]를
 * 받는 변형)으로 바꿔서 비트맵에 직접 접근한다 — 디코드/회전 보정/원근 변환/JPEG 인코딩이
 * 전부 무거운 작업이라 콜백 실행기를 메인이 아니라 [Dispatchers.Default]로 지정해 메인
 * 스레드를 막지 않는다. 토스트만 마지막에 메인 executor로 넘겨서 띄운다.
 */
fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    corners: List<PointF>?,
    analyzedFrameSize: IntSize?,
) {
    imageCapture.takePicture(
        Dispatchers.Default.asExecutor(),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotationDegrees = image.imageInfo.rotationDegrees
                val captured = image.toBitmap()
                image.close()
                val upright = uprightBitmap(captured, rotationDegrees)

                val output = if (corners != null && corners.size == 4 && analyzedFrameSize != null) {
                    val scaled = scaleCorners(
                        corners,
                        from = analyzedFrameSize,
                        to = IntSize(upright.width, upright.height),
                    )
                    warpToFlatDocument(upright, scaled) ?: upright
                } else {
                    upright
                }

                val fileName = "scanby_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA)
                    .format(System.currentTimeMillis()) + ".jpg"
                val savedToGallery = saveToGallery(context, output, fileName)
                if (!savedToGallery) {
                    // API 29 미만이거나 MediaStore insert가 실패한 경우의 폴백 — 갤러리엔
                    // 안 보이지만 최소한 촬영 자체는 유실되지 않게.
                    File(context.filesDir, fileName).let { photoFile ->
                        FileOutputStream(photoFile).use { out ->
                            output.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                    }
                }
                if (output !== upright) upright.recycle()
                output.recycle()

                val message = if (savedToGallery) "갤러리에 저장됨: $fileName" else "저장됨: $fileName"
                ContextCompat.getMainExecutor(context).execute {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                ContextCompat.getMainExecutor(context).execute {
                    Toast.makeText(context, "촬영 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
}

/**
 * 분석 프레임([from]) 좌표계에서 검출된 코너를 실제 촬영된 사진([to]) 좌표계로 비율
 * 스케일링한다. `DocumentCornerOverlay`가 화면 표시에 쓰는 것과 동일한 근사(가로/세로
 * 비율로 늘려 맞추기)다 — 분석/촬영 스트림의 실제 해상도는 다르지만 같은 카메라에서 동시에
 * 바인딩되므로 화각/종횡비는 대체로 일치한다고 가정한다.
 */
private fun scaleCorners(corners: List<PointF>, from: IntSize, to: IntSize): List<PointF> {
    val scaleX = to.width.toFloat() / from.width
    val scaleY = to.height.toFloat() / from.height
    return corners.map { PointF(it.x * scaleX, it.y * scaleY) }
}
