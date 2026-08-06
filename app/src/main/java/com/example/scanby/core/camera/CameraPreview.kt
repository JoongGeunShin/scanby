package com.example.scanby.core.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onImageCaptureReady: (ImageCapture) -> Unit = {},
    onFrameAnalyzed: (Bitmap) -> Unit = {},
    analysisIntervalMs: Long = 400L,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // CameraPreview 컴포저블 하나당 하나만 만들고, 컴포지션에서 빠질 때 반드시 종료한다.
    // 이전에는 AndroidView의 factory 람다 안에서 매번 새로 만들었는데, factory는 이
    // 컴포저블이 재진입할 때(예: 카메라 권한 상태에 따라 조건부로 나타났다 사라졌다 할 때)
    // 다시 실행될 수 있어 이전 executor가 종료되지 않고 스레드가 계속 쌓이는 누수가 있었다.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val previewView = PreviewView(context)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val imageCapture = ImageCapture.Builder().build()

                    // 프리뷰(고화질)와 분석용 프레임(저화질/저빈도)을 분리 — 매 프레임 분석하면
                    // 배터리/발열 부담이 커서 analysisIntervalMs 간격으로만 실제 분석을 수행한다.
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    var lastAnalyzedAtMs = 0L
                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        val now = System.currentTimeMillis()
                        if (now - lastAnalyzedAtMs < analysisIntervalMs) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        lastAnalyzedAtMs = now

                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        val bitmap = imageProxy.toBitmap()
                        imageProxy.close()

                        // 센서 프레임은 기기 회전과 무관하게 옆으로 누워 나올 수 있어, 화면에
                        // 보이는 방향과 맞춰야 이후 좌표 오버레이가 어긋나지 않는다.
                        onFrameAnalyzed(uprightBitmap(bitmap, rotationDegrees))
                    }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                        imageAnalysis,
                    )
                    onImageCaptureReady(imageCapture)
                },
                ContextCompat.getMainExecutor(context),
            )
            previewView
        },
    )
}

/**
 * 센서에서 나온 원본 [bitmap]을 [rotationDegrees]만큼 회전시켜 화면에 보이는 방향과 맞춘
 * "똑바로 선" 비트맵으로 반환한다. 회전이 필요 없으면([rotationDegrees]가 0이면) 원본을
 * 그대로 반환한다. `ImageAnalysis`(위 analyzer)와 `ImageCapture`(촬영, [feature.home.HomeScreen]
 * 참고) 모두 CameraX의 `ImageInfo.rotationDegrees`로 동일한 방식의 보정이 필요해서
 * `core.camera` 레벨에서 공용으로 둔다.
 */
fun uprightBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    bitmap.recycle()
    return rotated
}
