package com.github.maskedkunisquat.wulfpak.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Thin wrapper around [BiometricPrompt] for use in a single-Activity Compose app.
 *
 * Accepts Class 3 (strong) biometrics and falls back to device credential (PIN/pattern/password).
 * If the device has no lock screen, [canAuthenticate] returns false and the app runs ungated.
 *
 * Re-lock: reset the unlock flag in your Activity on Lifecycle.Event.ON_STOP so the gate
 * re-engages whenever the app leaves the foreground.
 */
class BiometricGate(
    private val activity: FragmentActivity,
    private val title: String,
    private val subtitle: String,
) {
    private val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    fun canAuthenticate(): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system biometric / credential prompt.
     * [onSuccess] is called on the main thread when authentication succeeds.
     * [onError] is called for unrecoverable errors. User-initiated cancels are silently ignored
     * so the lock screen can re-prompt via its Unlock button.
     */
    fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onError(errString.toString())
                    }
                }
            },
        )

        // NOTE: setNegativeButtonText must NOT be set when DEVICE_CREDENTIAL is in allowedAuthenticators
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(promptInfo)
    }
}
