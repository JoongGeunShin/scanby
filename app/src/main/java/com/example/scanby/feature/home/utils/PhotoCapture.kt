package com.example.scanby.feature.home.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import com.example.scanby.core.camera.uprightBitmap
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
 * [bitmap]을 `MediaStore`를 통해 시스템 갤러리(Pictures/scanby)에 저장한다. `context.filesDir`
 * (앱 전용 내부 저장소)와 달리 폰 자체의 갤러리/사진 앱에서 adb 없이 바로 확인할 수 있다.
 * Stage 8(앱 안의 라이브러리 화면)이 아직 없는 지금은 이 방식이 실기기에서 결과를 확인할
 * 수 있는 사실상 유일한 방법이라 채택함.
 *
 * API 29(Android 10) 미만은 공용 저장소 쓰기에 별도 런타임 권한(`WRITE_EXTERNAL_STORAGE`)
 * 요청이 필요한데, 지금 단계에서 그 권한 플로우까지 새로 만드는 건 과함 — 대신 false를
 * 반환해서 호출 측이 기존 [context.filesDir] 저장으로 폴백하게 한다.
 */
private fun saveToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/scanby")
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false

    return try {
        val stream = resolver.openOutputStream(uri) ?: return false
        stream.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        true
    } catch (e: Exception) {
//        resolver.delete(uri, null, null)
        false
    }
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
