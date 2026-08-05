package com.example.scanby.feature.home

import android.R.attr.onClick
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.scanby.core.designsystem.theme.ScanbyColor
import com.example.scanby.core.designsystem.theme.ScanbyTheme

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
){
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxWidth()
                .background(Color.Gray)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                modifier = Modifier.size(14.dp),
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
                    .clip(CircleShape),
//                    .clickable(), // 터치 영역 및 원형 Ripple 효과
                contentAlignment = Alignment.Center
            ) {
                // 2. 안쪽 채워진 원
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(color = ScanbyColor.AccentGradientEnd, shape = CircleShape)
                )
            }
            IconButton(
                modifier = Modifier.size(14.dp),
                onClick = {
                    // TODO(Stage 8): ActivityResultContracts.PickVisualMedia로 갤러리에서 사진 선택
                }
            ) {
                Icon(imageVector = Icons.Default.Image, contentDescription = "갤러리")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun HomeScreenPreview(){
    ScanbyTheme {
        HomeScreen()
    }
}