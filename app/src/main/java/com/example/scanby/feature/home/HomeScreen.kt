package com.example.scanby.feature.home

import android.Manifest
import android.graphics.Bitmap
import android.graphics.PointF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.scanby.core.camera.CameraPreview
import com.example.scanby.core.designsystem.theme.ScanbyColor
import com.example.scanby.core.designsystem.theme.ScanbyTheme
import com.example.scanby.core.permission.rememberPermissionState
import com.example.scanby.core.vision.CornerSmoother
import com.example.scanby.core.vision.DocQuadDetector
import com.example.scanby.feature.home.components.DocumentCornerOverlay
import com.example.scanby.feature.home.components.SettingsMenuButton
import com.example.scanby.feature.home.utils.loadBitmapFromUri
import com.example.scanby.feature.home.utils.processPickedImage
import com.example.scanby.feature.home.utils.takePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

// 분석 간격 — 짧을수록 오버레이 업데이트가 촘촘해져 부드럽지만 발열/배터리 부담이 커진다.
// 400ms(초당 2.5회)는 업데이트 사이 간격이 육안에 보일 정도로 커서 "뚝뚝 끊기는" 느낌의
// 주범이었음 — 250ms로 낮춤 (`DOCS/corner_detection_tuning.md` 진행 로그 참고, 발열 체감
// 확인 필요).
private const val ANALYSIS_INTERVAL_MS = 250L

// 위 분석 간격 기준 이만큼 연속으로 신뢰 못 할 프레임이 나오면 "문서가 화면에서 사라졌다"고
// 보고 오버레이를 지운다 (약 1.25초).
private const val MAX_MISS_STREAK = 5

@Composable
fun HomeScreen(
    onSendToPcClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasCameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Stage 4: 문서 모서리 자동 인식. 모델 로딩(에셋 복사 + 세션 생성)은 시간이 걸리는
    // 블로킹 작업이라 IO 디스패처에서 한 번만 만들어두고 재사용한다.
    var docQuadDetector by remember { mutableStateOf<DocQuadDetector?>(null) }
    var detectedCorners by remember { mutableStateOf<List<PointF>?>(null) }
    var analyzedFrameSize by remember { mutableStateOf<IntSize?>(null) }
    // 트랙 F: 마스크 기준 "문서가 프레임 경계에 닿아 있는지" — 코너 검출 성패와 무관하게
    // 매 프레임 그대로 반영(별도 스무딩/hold 없음, 우선 가장 단순한 버전으로 실기기 확인).
    var touchesFrameEdge by remember { mutableStateOf(false) }
    // beta를 기본값(0.05)보다 크게 잡아서 움직일 때 필터가 덜 뒤처지게(lag 감소) 함 —
    // 화면 표시의 "부드러움" 자체는 이제 아래 DocumentCornerOverlay의 애니메이션이
    // 담당하므로, 이 필터는 검출 노이즈만 줄이고 실제 위치는 너무 늦게 쫓아가지 않도록.
    val cornerSmoother = remember { CornerSmoother(beta = 0.3) }
    var missStreak by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        docQuadDetector = withContext(Dispatchers.IO) { DocQuadDetector.create(context) }
    }

    DisposableEffect(Unit) {
        onDispose { docQuadDetector?.close() }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) {
        uri ->
        if(uri != null){
            coroutineScope.launch {
                val picked = withContext(Dispatchers.IO){
                    loadBitmapFromUri(context, uri)
                }
                withContext(Dispatchers.Default){
                    processPickedImage(context, picked, docQuadDetector)
                }
            }
        }
    }

    HomeScreenContent(
        modifier = modifier,
        hasCameraPermission = hasCameraPermission,
        detectedCorners = detectedCorners,
        analyzedFrameSize = analyzedFrameSize,
        touchesFrameEdge = touchesFrameEdge,
        analysisIntervalMs = ANALYSIS_INTERVAL_MS,
        onImageCaptureReady = { imageCapture = it },
        onFrameAnalyzed = { frame ->
            val detector = docQuadDetector
            val frameSize = IntSize(frame.width, frame.height)
            if (detector == null) {
                frame.recycle()
            } else {
                coroutineScope.launch {
                    val detection = withContext(Dispatchers.Default) {
                        detector.detectCorners(frame)
                    }
                    frame.recycle()
                    val raw = detection.corners
                    touchesFrameEdge = detection.touchesFrameEdge

                    if (raw == null) {
                        // 트랙 C에서 이 프레임을 신뢰 못 한다고 판단한 경우.
                        // 한두 프레임 정도는 이전(스무딩된) 사각형을 그대로 두고,
                        // 문서가 아예 사라진 것으로 보일 만큼 계속 실패하면 지운다.
                        missStreak++
                        if (missStreak >= MAX_MISS_STREAK) {
                            detectedCorners = null
                            cornerSmoother.reset()
                        }
                    } else {
                        missStreak = 0
                        val frameDiagonal = hypot(
                            frameSize.width.toFloat(),
                            frameSize.height.toFloat()
                        )
                        detectedCorners = cornerSmoother.smooth(
                            raw = raw,
                            timestampMs = System.currentTimeMillis(),
                            frameDiagonal = frameDiagonal
                        )
                        analyzedFrameSize = frameSize
                    }
                }
            }
        },
        onCaptureClick = {
            imageCapture?.let { capture ->
                takePhoto(context, capture, detectedCorners, analyzedFrameSize)
            }
        },
        onGalleryClick = {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onSendToPcClick = onSendToPcClick
    )
}

/**
 * [HomeScreen]의 상태 없는(stateless) 레이아웃 부분만 떼어낸 것. 권한 요청
 * ([rememberPermissionState]가 쓰는 `rememberLauncherForActivityResult`는
 * `@Preview` 환경에 `ActivityResultRegistryOwner`가 없어 렌더링 자체가 실패한다)와
 * 카메라/모델 로딩(CameraX, [DocQuadDetector])을 여기 분리해둔 덕분에, 프리뷰는 이
 * 컴포저블에 더미 값만 넘겨 실행하면 되고 저 두 가지를 건드릴 일이 없다.
 */
@Composable
private fun HomeScreenContent(
    hasCameraPermission: Boolean,
    detectedCorners: List<PointF>?,
    analyzedFrameSize: IntSize?,
    touchesFrameEdge: Boolean,
    analysisIntervalMs: Long,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onFrameAnalyzed: (Bitmap) -> Unit,
    onCaptureClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onSendToPcClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxWidth()
                .background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    analysisIntervalMs = analysisIntervalMs,
                    onImageCaptureReady = onImageCaptureReady,
                    onFrameAnalyzed = onFrameAnalyzed
                )
                DocumentCornerOverlay(
                    corners = detectedCorners,
                    frameSize = analyzedFrameSize,
                    modifier = Modifier.fillMaxSize()
                )
                if (touchesFrameEdge) {
                    // vFlat의 "더 멀리서 스캔해주세요"와 동일한 취지 — 문서가 프레임을
                    // 벗어나 코너 자체가 화면 밖에 있는 상황(CASE_C)에서는 코너를 억지로
                    // 추정하는 대신 사용자에게 물러나 달라고 안내한다.
                    Text(
                        text = "문서 전체가 보이도록 뒤로 물러나 주세요",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                Text(text = "문서를 스캔하려면 카메라 권한이 필요합니다", color = Color.White)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ){
            IconButton(
                onClick = {

                }
            ) {
                Icon(imageVector = Icons.Default.Book, contentDescription = "PageMode")
            }
            IconButton(
                onClick = {

                }
            ) {
                // 현재 FlashOff로만 단일 세팅 → 상태에 따라 FlashON, FlashLightON 3단계로 변경
                Icon(imageVector = Icons.Default.FlashOff, contentDescription = "FlashMode")
            }
            IconButton(
                onClick = {

                }
            ) {
                // 현재 Timer 단일 세팅 → Timer3, Timer10 으로 세팅 (default 이미지만 사용 예정)
                Icon(imageVector = Icons.Default.Timer, contentDescription = "TimerMode")
            }
            IconButton(
                onClick = {

                }
            ) {
                Icon(imageVector = Icons.Default.Paid, contentDescription = "premiumMode")
            }
            SettingsMenuButton(onSendToPcClick = onSendToPcClick)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    // TODO(Stage 8): 라이브러리(저장된 스캔 목록) 화면으로 이동
                }
            ) {
                Icon(imageVector = Icons.Default.Collections, contentDescription = "라이브러리")
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(width = 4.dp, color = ScanbyColor.Accent, shape = CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCaptureClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(color = ScanbyColor.AccentGradientEnd, shape = CircleShape)
                )
            }
            IconButton(onClick = onGalleryClick) {
                Icon(imageVector = Icons.Default.Image, contentDescription = "갤러리")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun HomeScreenPreview() {
    ScanbyTheme {
        HomeScreenContent(
            hasCameraPermission = false,
            detectedCorners = null,
            analyzedFrameSize = null,
            touchesFrameEdge = false,
            analysisIntervalMs = ANALYSIS_INTERVAL_MS,
            onImageCaptureReady = {},
            onFrameAnalyzed = {},
            onCaptureClick = {},
            onGalleryClick = {},
            onSendToPcClick = {}
        )
    }
}
