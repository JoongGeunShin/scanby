package com.example.scanby.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.scanby.core.designsystem.theme.ScanbyColor

import com.example.scanby.core.designsystem.theme.ScanbyTheme
/**
 * 촬영 화면 아이콘 툴바의 "설정" 버튼 + 눌렀을 때 뜨는 [SettingsDialog]를 함께 관리한다.
 * 열림/닫힘 상태와 토글 값들을 아직 다른 화면이 쓸 일이 없어서 여기서 직접 들고 있음 —
 * 나중에 실제로 촬영 로직(색상 보정, 손가락 지우기 등)에 이 값들이 필요해지면, 이미 있는
 * `data.onboarding` / `OnboardingRepositoryImpl` 패턴처럼 DataStore 기반 저장소로 옮기고
 * 이 컴포저블은 그 값을 파라미터로 받도록 바꾸면 된다.
 */
@Composable
fun SettingsMenuButton(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var isShutterSoundOn by remember { mutableStateOf(true) }
    var isColorCorrectionOn by remember { mutableStateOf(true) }
    var isEraseFingerOn by remember { mutableStateOf(true) }
    var isFlattenMoreOn by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(imageVector = Icons.Default.Settings, contentDescription = "설정")
    }

    // Dialog는 `if (expanded)`로 껐다 켰다 하면 사라지는 순간 즉시 컴포지션에서 빠져서
    // 퇴장 애니메이션을 걸 방법이 없다. MutableTransitionState로 "지금 보이는 상태(currentState)"와
    // "목표 상태(targetState)"를 따로 들고 있으면, 둘 중 하나라도 true인 동안(= 아직 퇴장
    // 애니메이션이 끝나기 전까지) Dialog를 계속 마운트해둘 수 있다.
    val dialogVisibleState = remember { MutableTransitionState(false) }
    dialogVisibleState.targetState = expanded

    if (dialogVisibleState.currentState || dialogVisibleState.targetState) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // Dialog가 만드는 시스템 창에는 테마의 기본 다이얼로그 애니메이션(여기선
            // 아래→위 슬라이드로 보임)이 기본으로 걸려 있다. 페이드만 남기려면 창
            // 애니메이션 자체를 꺼야 한다 — DialogProperties엔 이 옵션이 없어서
            // DialogWindowProvider로 실제 Window에 접근해서 직접 끈다.
            val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { dialogWindow?.setWindowAnimations(0) }

            AnimatedVisibility(
                visibleState = dialogVisibleState,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        // 반투명 배경(scrim) 아무 곳이나 누르면 닫힘. indication = null로
                        // 화면 전체에 물결 효과가 번지는 걸 막는다.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { expanded = false }
                        )
                ) {
                    SettingsDialog(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        isShutterSoundOn = isShutterSoundOn,
                        onShutterSoundClick = { isShutterSoundOn = !isShutterSoundOn },
                        onOpenAdvancedSettings = {
                            // TODO: 전체 설정 화면으로 이동
                        },
                        onSendToPcClick = {
                            // TODO: PC 전송 플로우 연결
                        },
                        isColorCorrectionOn = isColorCorrectionOn,
                        onColorCorrectionChange = { isColorCorrectionOn = it },
                        isEraseFingerOn = isEraseFingerOn,
                        onEraseFingerChange = { isEraseFingerOn = it },
                        isFlattenMoreOn = isFlattenMoreOn,
                        onFlattenMoreChange = { isFlattenMoreOn = it },
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    isShutterSoundOn: Boolean,
    onShutterSoundClick: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onSendToPcClick: () -> Unit,
    isColorCorrectionOn: Boolean,
    onColorCorrectionChange: (Boolean) -> Unit,
    isEraseFingerOn: Boolean,
    onEraseFingerChange: (Boolean) -> Unit,
    isFlattenMoreOn: Boolean,
    onFlattenMoreChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ScanbyColor.Navy,
        contentColor = Color.White,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SettingsIconLabel(
                    icon = Icons.AutoMirrored.Filled.VolumeOff,
                    label = "셔터 소리",
                    onClick = onShutterSoundClick
                )
                SettingsIconLabel(
                    icon = Icons.Default.Settings,
                    label = "설정",
                    onClick = onOpenAdvancedSettings
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                thickness = 1.dp,
                color = Color.White.copy(alpha = 0.15f)
            )
            SettingsNavigationRow(
                title = "PC로 전송",
                subtitle = "스캔 파일을 PC로 바로 저장하기",
                onClick = onSendToPcClick
            )
            SettingsSwitchRow(
                title = "색상 보정",
                subtitle = "색상 보정 기능을 끄면 스캔한 페이지의 본래색을 유지합니다.",
                checked = isColorCorrectionOn,
                onCheckedChange = onColorCorrectionChange
            )
            SettingsSwitchRow(
                title = "손가락 지우기",
                subtitle = "스캔 결과물에서 손가락을 지웁니다.",
                checked = isEraseFingerOn,
                onCheckedChange = onEraseFingerChange
            )
            SettingsSwitchRow(
                title = "더 펴기",
                subtitle = "스캔 결과물이 더 반듯해지도록 보정합니다.",
                checked = isFlattenMoreOn,
                onCheckedChange = onFlattenMoreChange,
                isPremium = true
            )
        }
    }
}

@Composable
private fun SettingsIconLabel(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Text(text = label, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, fontSize = 12.sp, color = ScanbyColor.OnNavySecondary)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isPremium: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (isPremium) {
                    Icon(
                        imageVector = Icons.Default.Paid,
                        contentDescription = "프리미엄",
                        tint = ScanbyColor.Accent,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(16.dp)
                    )
                }
            }
            Text(text = subtitle, fontSize = 12.sp, color = ScanbyColor.OnNavySecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 480, backgroundColor = 0xFF808080)
@Composable
private fun SettingsDialogPreview() {
    ScanbyTheme {
        SettingsDialog(
            modifier = Modifier,
            isShutterSoundOn = true,
            onShutterSoundClick = {},
            onOpenAdvancedSettings = {},
            onSendToPcClick = {},
            isColorCorrectionOn = true,
            onColorCorrectionChange = {},
            isEraseFingerOn = true,
            onEraseFingerChange = {},
            isFlattenMoreOn = false,
            onFlattenMoreChange = {},
        )
    }
}
