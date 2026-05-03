package com.github.maskedkunisquat.wulfpak

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.wulfpak.biometric.BiometricGate
import com.github.maskedkunisquat.wulfpak.navigation.AppNavHost
import com.github.maskedkunisquat.wulfpak.ui.LockScreen
import com.github.maskedkunisquat.wulfpak.ui.theme.WulfPakTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            WulfPakTheme {
                AppRoot(activity = this)
            }
        }
    }
}

@Composable
private fun AppRoot(activity: FragmentActivity) {
    val app = activity.application as AppApplication

    // Seed demo DB from the bundled asset on first demo launch (no-op if already seeded).
    var seeding by remember { mutableStateOf(app.isDemoProfile) }
    LaunchedEffect(Unit) {
        if (app.isDemoProfile) {
            try {
                withContext(Dispatchers.IO) { app.seedDemoIfNeeded() }
            } finally {
                seeding = false
            }
        }
    }
    if (seeding) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var isUnlocked by remember { mutableStateOf(false) }

    // null = DataStore not yet loaded; avoid prompting before we know the setting
    val biometricEnabled by activity.application.appDataStore.data
        .map { it[AppPrefsKeys.BIOMETRIC_ENABLED] }
        .collectAsStateWithLifecycle(initialValue = null)

    val gate = remember {
        BiometricGate(
            activity = activity,
            title    = "Unlock WulfPak",
            subtitle = "Use biometrics or PIN to access your contacts",
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) isUnlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun tryUnlock() {
        val enabled = biometricEnabled ?: return  // wait for DataStore to emit
        if (enabled && gate.canAuthenticate()) {
            gate.authenticate(onSuccess = { isUnlocked = true })
        } else {
            isUnlocked = true
        }
    }

    // Re-run when isUnlocked changes OR when biometricEnabled loads from DataStore
    DisposableEffect(isUnlocked, biometricEnabled) {
        if (!isUnlocked) tryUnlock()
        onDispose { }
    }

    if (isUnlocked) {
        AppNavHost()
    } else {
        LockScreen(onUnlockClick = { tryUnlock() })
    }
}
