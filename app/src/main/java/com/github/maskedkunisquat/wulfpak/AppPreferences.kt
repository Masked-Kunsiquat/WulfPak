package com.github.maskedkunisquat.wulfpak

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

object AppPrefsKeys {
    val BIOMETRIC_ENABLED  = booleanPreferencesKey("biometric_enabled")
    val SHOW_BIRTHDAY_AGE  = booleanPreferencesKey("show_birthday_age")
    val SORT_BY_LAST_NAME  = booleanPreferencesKey("sort_by_last_name")
    val CLOSENESS_ACTIVITY_BACKFILL_V1 = booleanPreferencesKey("closeness_activity_backfill_v1")
    val DEBUG_CAPTURE_ENABLED = booleanPreferencesKey("debug_capture_enabled")
}
