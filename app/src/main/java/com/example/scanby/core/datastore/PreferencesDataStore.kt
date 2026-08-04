package com.example.scanby.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val PREFERENCES_NAME = "scanby_preferences"
val Context.scanbyDataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_NAME)
