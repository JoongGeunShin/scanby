package com.example.scanby.core.permission

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * [permission] 허용 여부를 반환한다. 아직 허용되지 않았다면 화면에 처음 나타날 때 시스템
 * 권한 요청 다이얼로그를 띄우고, 사용자가 응답하면(허용/거부) 반환값이 그에 맞춰 갱신된다.
 */
@Composable
fun rememberPermissionState(permission: String): Boolean {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(permission) {
        if (!hasPermission) {
            permissionLauncher.launch(permission)
        }
    }

    return hasPermission
}
