package com.example.scanby.feature.onboarding

/**
 * The 6 content scenes shown between Intro and Closing (Home ~ AutoCapture).
 * placeholderEmoji stands in for the real scan-feature screenshots/illustrations,
 * which don't exist yet — see DOCS/design_stage.txt Stage 5.
 */
data class OnboardingContentScene(
    val title: String,
    val subtitle: String? = null,
    val badgeText: String? = null,
    val placeholderEmoji: String,
)

val onboardingContentScenes = listOf(
    OnboardingContentScene(
        title = "원본 이미지",
        subtitle = "카메라로 원본 이미지를 촬영하세요",
        placeholderEmoji = "TEST",
    ),
    OnboardingContentScene(
        title = "곡면 보정",
        subtitle = "촬영한 이미지의 곡면을 판단해요",
        placeholderEmoji = "TEST",
    ),
    OnboardingContentScene(
        title = "색상 보정",
        subtitle = "이미지의 색상을 보정해요",
        placeholderEmoji = "TEST",
    ),
    OnboardingContentScene(
        title = "불필요 이미지 제거",
        subtitle = "서류와 무관한 이미지 정보를 제거해요",
        placeholderEmoji = "TEST",
    ),
)
