package com.example.scanby.navigation

import android.net.Uri
import androidx.navigation.NavBackStackEntry

object ImageDetailRoute {
    private const val BASE = "image_detail"
    private const val ARG_ENCODED_URI = "encodedUri"
    const val route = "$BASE/{$ARG_ENCODED_URI}"

    // content:// URI를 route의 path segment로 넣으려면 "/" 같은 문자를 인코딩해야 한다.
    // Navigation-Compose가 {encodedUri} 자리에서 값을 꺼낼 때 자동으로 디코딩해주므로,
    // 받는 쪽([uriFrom])에서 다시 디코딩하면 안 된다(이중 디코딩 버그).
    fun createRoute(uri: Uri): String = "$BASE/${Uri.encode(uri.toString())}"

    fun uriFrom(backStackEntry: NavBackStackEntry): Uri? =
        backStackEntry.arguments?.getString(ARG_ENCODED_URI)?.let(Uri::parse)
}
