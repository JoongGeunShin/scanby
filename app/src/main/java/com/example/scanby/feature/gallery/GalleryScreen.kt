package com.example.scanby.feature.gallery

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.scanby.core.designsystem.theme.ScanbyColor
import com.example.scanby.core.designsystem.theme.ScanbyTheme
import com.example.scanby.core.permission.rememberPermissionState
import com.example.scanby.domain.gallery.GalleryImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// TO-DO list
// 1. 손가락 두개로 확대 축소로 단계별 사진 줌인/줌아웃 구현
// 2. longpress 진입 후 여러개 선택
// 추가기능 고려 PDF, 필요 텍스트만 추출하기(손가락으로 canvas를 통해 구역을 설정해서 그 텍스트만 추출하기)
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val hasGalleryPermission = rememberPermissionState(permission)
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(hasGalleryPermission) {
        if (hasGalleryPermission) {
            viewModel.loadImages()
        }
    }

    Column(modifier = modifier.fillMaxSize().navigationBarsPadding().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text(text = "갤러리", fontWeight = FontWeight.SemiBold)
        }

        GalleryScreenContent(
            hasGalleryPermission = hasGalleryPermission,
            uiState = uiState,
            onImageTapped = viewModel::onImageTapped,
            onImageSelectedChanged = viewModel::setImageSelected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GalleryScreenContent(
    hasGalleryPermission: Boolean,
    uiState: GalleryUiState,
    onImageTapped: (Long) -> Unit,
    onImageSelectedChanged: (id: Long, selected: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.images.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (hasGalleryPermission) "사진이 없습니다" else "갤러리 접근 권한이 필요합니다",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val selectedImages = uiState.images.filter { it.id in uiState.selectedIds }
    val gridState = rememberLazyGridState()
    val currentUiState by rememberUpdatedState(uiState)

    Column(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .pointerInput(uiState.isMultiSelectMode) {
                    var dragSelectMode = true
                    var lastDraggedId: Long? = null

                    // 이 시퀀스에서 처음 만지는 칸 — 모드를 새로 정하고 적용.
                    val selectStarting: (Long) -> Unit = { id ->
                        dragSelectMode = id !in currentUiState.selectedIds
                        lastDraggedId = id
                        onImageSelectedChanged(id, dragSelectMode)
                    }
                    // 이미 정해진 모드를 그대로 이어서 적용 — 같은 칸이면 건너뜀.
                    val continueOver: (Long) -> Unit = { id ->
                        if (id != lastDraggedId) {
                            lastDraggedId = id
                            onImageSelectedChanged(id, dragSelectMode)
                        }
                    }
                    val onDrag: (PointerInputChange, Offset) -> Unit = { change, _ ->
                        change.consume()
                        gridState.imageIdAt(currentUiState.images, change.position)
                            ?.let(continueOver)
                    }
                    val onDragEnd: () -> Unit = { lastDraggedId = null }
                    val onDragCancel: () -> Unit = { lastDraggedId = null }

                    coroutineScope {
                        launch {
                            detectTapGestures(
                                onTap = { offset ->
                                    gridState.imageIdAt(currentUiState.images, offset)
                                        ?.let(onImageTapped)
                                },
                                onLongPress = { offset ->
                                    if (!currentUiState.isMultiSelectMode) {
                                        gridState.imageIdAt(currentUiState.images, offset)
                                            ?.let(selectStarting)
                                    }
                                },
                            )
                        }
                        launch {
                            if (uiState.isMultiSelectMode) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        gridState.imageIdAt(currentUiState.images, offset)
                                            ?.let(selectStarting)
                                    },
                                    onDrag = onDrag,
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                )
                            } else {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        gridState.imageIdAt(currentUiState.images, offset)
                                            ?.let(continueOver)
                                    },
                                    onDrag = onDrag,
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                )
                            }
                        }
                    }
                },
        ) {
            items(uiState.images, key = { it.id }) { image ->
                val isSelected = image.id in uiState.selectedIds
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    GalleryThumbnail(
                        uri = image.uri,
                        contentDescription = image.displayName,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(ScanbyColor.Accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectedImages.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            SelectedImageInfoPanel(selectedImages = selectedImages)
        }
    }
}

private fun LazyGridState.imageIdAt(images: List<GalleryImage>, offset: Offset): Long? {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { info ->
        offset.x >= info.offset.x && offset.x <= info.offset.x + info.size.width &&
            offset.y >= info.offset.y && offset.y <= info.offset.y + info.size.height
    } ?: return null
    return images.getOrNull(item.index)?.id
}
@Composable
private fun GalleryThumbnail(
    uri: Uri,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(
                    uri,
                    Size(THUMBNAIL_SIZE_PX, THUMBNAIL_SIZE_PX),
                    null,
                )
            }.getOrNull()
        }
    }

    val bitmap = thumbnail
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

private const val THUMBNAIL_SIZE_PX = 300
@Composable
private fun SelectedImageInfoPanel(
    selectedImages: List<GalleryImage>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        val single = selectedImages.singleOrNull()
        if (single != null) {
            Text(text = single.displayName, fontWeight = FontWeight.SemiBold)
            Text(
                text = formatDateAdded(single.dateAdded),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        } else {
            Text(text = "${selectedImages.size}장 선택됨", fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatDateAdded(epochSeconds: Long): String =
    SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(epochSeconds * 1000))

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun GalleryScreenContentEmptyPreview() {
    ScanbyTheme {
        GalleryScreenContent(
            hasGalleryPermission = true,
            uiState = GalleryUiState(),
            onImageTapped = {},
            onImageSelectedChanged = { _, _ -> },
        )
    }
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun GalleryScreenContentPermissionDeniedPreview() {
    ScanbyTheme {
        GalleryScreenContent(
            hasGalleryPermission = false,
            uiState = GalleryUiState(),
            onImageTapped = {},
            onImageSelectedChanged = { _, _ -> },
        )
    }
}

private val previewImages = listOf(
    GalleryImage(
        id = 1L,
        uri = Uri.parse("content://media/external/images/media/1"),
        displayName = "IMG_20260810_101530.jpg",
        dateAdded = 1_755_000_000L,
    ),
    GalleryImage(
        id = 2L,
        uri = Uri.parse("content://media/external/images/media/2"),
        displayName = "IMG_20260809_091200.jpg",
        dateAdded = 1_754_900_000L,
    ),
)

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun GalleryScreenContentSingleSelectedPreview() {
    ScanbyTheme {
        GalleryScreenContent(
            hasGalleryPermission = true,
            uiState = GalleryUiState(images = previewImages, selectedIds = setOf(1L)),
            onImageTapped = {},
            onImageSelectedChanged = { _, _ -> },
        )
    }
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun GalleryScreenContentMultiSelectedPreview() {
    ScanbyTheme {
        GalleryScreenContent(
            hasGalleryPermission = true,
            uiState = GalleryUiState(
                images = previewImages,
                selectedIds = setOf(1L, 2L),
                isMultiSelectMode = true,
            ),
            onImageTapped = {},
            onImageSelectedChanged = { _, _ -> },
        )
    }
}

