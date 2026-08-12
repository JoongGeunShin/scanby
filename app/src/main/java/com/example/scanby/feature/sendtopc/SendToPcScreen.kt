package com.example.scanby.feature.sendtopc

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.scanby.core.designsystem.theme.ScanbyColor
import com.example.scanby.core.designsystem.theme.ScanbyTheme
import com.example.scanby.core.permission.rememberPermissionState
import com.example.scanby.feature.sendtopc.utils.LocalFileServer
import com.example.scanby.feature.sendtopc.utils.ScannedImage
import com.example.scanby.feature.sendtopc.utils.generateQrCodeBitmap
import com.example.scanby.feature.sendtopc.utils.getLocalIpv4Address
import com.example.scanby.feature.sendtopc.utils.loadScannedImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "PC로 전송" 화면 — Pictures/scanby에 저장된 스캔 중 보낼 것들을 고르면([SelectionContent]),
 * 같은 Wi-Fi(LAN)에서만 접근 가능한 [LocalFileServer]를 띄우고 QR/URL을 보여준다
 * ([ServingContent]). 화면을 벗어나면([DisposableEffect]) 서버를 반드시 내린다 — 계속
 * 켜둘 이유가 없고, 인증 없는 로컬 서버를 오래 열어두는 건 피해야 한다.
 */
@Composable
fun SendToPcScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // API 29+는 앱이 직접 저장한 미디어를 scoped storage "본인 파일" 예외로 권한 없이 읽을
    // 수 있다 — API 28 이하만 이 권한이 실제로 필요하다.
    val hasStoragePermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    } else {
        true
    }

    var images by remember { mutableStateOf<List<ScannedImage>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var server by remember { mutableStateOf<LocalFileServer?>(null) }
    var serverUrl by remember { mutableStateOf<String?>(null) }
    var sentCount by remember { mutableStateOf(0) }

    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission) {
            images = withContext(Dispatchers.IO) { loadScannedImages(context) }
        }
    }

    DisposableEffect(Unit) {
        onDispose { server?.stop() }
    }

    val isServing = serverUrl != null

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (isServing) {
                        server?.stop()
                        server = null
                        serverUrl = null
                    } else {
                        onBack()
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text(
                text = if (isServing) "PC에서 접속하세요" else "PC로 보낼 사진 선택",
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (isServing) {
            ServingContent(
                url = serverUrl.orEmpty(),
                count = sentCount,
                modifier = Modifier.weight(1f),
            )
        } else {
            SelectionContent(
                images = images,
                selectedIds = selectedIds,
                hasStoragePermission = hasStoragePermission,
                onToggle = { id ->
                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    val selected = images.filter { it.id in selectedIds }
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val newServer = LocalFileServer(context, selected)
                                newServer.start()
                                val ip = getLocalIpv4Address()
                                if (ip == null) {
                                    newServer.stop()
                                    error("Wi-Fi에 연결되어 있는지 확인해주세요")
                                }
                                newServer to "http://$ip:${newServer.listeningPort}"
                            }
                        }
                        result.onSuccess { (newServer, url) ->
                            server = newServer
                            serverUrl = url
                            sentCount = selected.size
                        }.onFailure { e ->
                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("선택한 ${selectedIds.size}장 보내기")
            }
        }
    }
}

@Composable
private fun SelectionContent(
    images: List<ScannedImage>,
    selectedIds: Set<Long>,
    hasStoragePermission: Boolean,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (hasStoragePermission) "저장된 스캔이 없습니다" else "저장소 접근 권한이 필요합니다",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        items(images, key = { it.id }) { image ->
            val isSelected = image.id in selectedIds
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggle(image.id) },
            ) {
                AsyncImage(
                    model = image.uri,
                    contentDescription = image.displayName,
                    contentScale = ContentScale.Crop,
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
}

@Composable
private fun ServingContent(
    url: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val qrBitmap = remember(url) { generateQrCodeBitmap(url, 512) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "${count}장 전송 준비 완료", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Image(
            bitmap = qrBitmap.asImageBitmap(),
            contentDescription = "QR 코드",
            modifier = Modifier
                .size(220.dp)
                .background(Color.White)
                .padding(8.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "같은 Wi-Fi에서 PC 브라우저로 접속하세요",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = url, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SendToPcScreenSelectionPreview() {
    ScanbyTheme {
        SelectionContent(
            images = emptyList(),
            selectedIds = emptySet(),
            hasStoragePermission = true,
            onToggle = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SendToPcScreenServingPreview() {
    ScanbyTheme {
        ServingContent(url = "http://192.168.0.5:54321", count = 3)
    }
}
