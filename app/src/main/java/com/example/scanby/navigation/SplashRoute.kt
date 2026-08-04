package com.example.scanby.navigation

/**
 * 앱 실행 시 항상 가장 먼저 보여주는 브랜드 스플래시 화면의 route.
 * 스플래시가 끝나면 OnboardingRoute로 갈지, 온보딩을 이미 마쳤다면 다음 실제
 * 목적지(feature.login / feature.home)로 갈지는 SplashViewModel의 상태를 보고
 * MainActivity에서 분기한다.
 */
object SplashRoute {
    const val route = "splash"
}
