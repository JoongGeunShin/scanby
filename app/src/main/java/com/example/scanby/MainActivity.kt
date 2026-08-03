package com.example.scanby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.scanby.core.designsystem.theme.ScanbyTheme
import com.example.scanby.feature.onboarding.OnboardingScreen
import com.example.scanby.navigation.OnboardingRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanbyTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = OnboardingRoute.route) {
                    composable(OnboardingRoute.route) {
                        OnboardingScreen(
                            onFinished = {
                                // TODO: navigate to feature.login / feature.home once they exist
                            },
                        )
                    }
                }
            }
        }
    }
}
