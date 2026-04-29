package com.github.maskedkunisquat.wulfpak

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

object AppPrefsKeys {
    val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
}
