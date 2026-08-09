package com.example.scanby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.scanby.core.designsystem.theme.ScanbyTheme
import com.example.scanby.feature.home.HomeScreen
import com.example.scanby.feature.onboarding.OnboardingScreen
import com.example.scanby.feature.onboarding.OnboardingViewModel
import com.example.scanby.feature.sendtopc.SendToPcScreen
import com.example.scanby.feature.splash.SplashScreen
import com.example.scanby.feature.splash.SplashViewModel
import com.example.scanby.navigation.HomeRoute
import com.example.scanby.navigation.OnboardingRoute
import com.example.scanby.navigation.SendToPcRoute
import com.example.scanby.navigation.SplashRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanbyTheme {
                ScanbyApp()
            }
        }
    }
}

/**
 * Splash는 항상 먼저 보여주는 브랜드 화면. 자동으로 끝나면(SplashScreen 내부 타이머)
 * SplashViewModel이 읽어둔 온보딩 완료 여부를 보고 다음 화면을 정한다.
 */
@Composable
private fun ScanbyApp() {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = SplashRoute.route) {
        composable(SplashRoute.route) {
            val splashViewModel: SplashViewModel = hiltViewModel()
            val onboardingCompleted by splashViewModel.onboardingCompleted.collectAsState()
            SplashScreen(
                onFinished = {
                    if (onboardingCompleted == true) {
                        navController.navigate(HomeRoute.route) {
                            popUpTo(SplashRoute.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(OnboardingRoute.route) {
                            popUpTo(SplashRoute.route) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(OnboardingRoute.route) {
            val onboardingViewModel: OnboardingViewModel = hiltViewModel()
            OnboardingScreen(
                onFinished = {
                    onboardingViewModel.completeOnboarding()
                    navController.navigate(HomeRoute.route) {
                        popUpTo(OnboardingRoute.route) { inclusive = true }
                    }
                },
            )
        }
        composable(HomeRoute.route) {
            HomeScreen(
                onSendToPcClick = { navController.navigate(SendToPcRoute.route) },
            )
        }
        composable(SendToPcRoute.route) {
            SendToPcScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
