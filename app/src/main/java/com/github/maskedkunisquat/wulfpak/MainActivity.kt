package com.github.maskedkunisquat.wulfpak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.maskedkunisquat.wulfpak.biometric.BiometricGate
import com.github.maskedkunisquat.wulfpak.navigation.AppNavHost
import com.github.maskedkunisquat.wulfpak.ui.LockScreen
import com.github.maskedkunisquat.wulfpak.ui.theme.WulfPakTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WulfPakTheme {
                AppRoot(activity = this)
            }
        }
    }
}

@Composable
private fun AppRoot(activity: ComponentActivity) {
    var isUnlocked by remember { mutableStateOf(false) }

    val gate = remember {
        BiometricGate(
            activity = activity,
            title = "Unlock WulfPak",
            subtitle = "Use biometrics or PIN to access your contacts",
        )
    }

    // Re-lock whenever the app goes to background (ON_STOP)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) isUnlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun tryUnlock() {
        if (gate.canAuthenticate()) {
            gate.authenticate(onSuccess = { isUnlocked = true })
        } else {
            isUnlocked = true // no lock screen configured — run ungated
        }
    }

    // Auto-prompt on every transition to locked state
    DisposableEffect(isUnlocked) {
        if (!isUnlocked) tryUnlock()
        onDispose { }
    }

    if (isUnlocked) {
        AppNavHost()
    } else {
        LockScreen(onUnlockClick = { tryUnlock() })
    }
}
