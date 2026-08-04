package com.example.scanby.data.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.scanby.domain.onboarding.OnboardingRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")

class OnboardingRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OnboardingRepository {

    override suspend fun isOnboardingCompleted(): Boolean =
        dataStore.data.first()[ONBOARDING_COMPLETED_KEY] ?: false

    override suspend fun setOnboardingCompleted() {
        dataStore.edit { preferences -> preferences[ONBOARDING_COMPLETED_KEY] = true }
    }
}
