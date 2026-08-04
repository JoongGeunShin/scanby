package com.example.scanby.domain.onboarding

interface OnboardingRepository {
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted()
}
