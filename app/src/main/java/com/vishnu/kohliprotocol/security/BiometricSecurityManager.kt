package com.vishnu.kohliprotocol.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vishnu.kohliprotocol.data.local.entity.AuditAction
import com.vishnu.kohliprotocol.data.repository.AuditRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * "Is the person holding the phone the owner?" — fingerprint (or the device PIN/pattern as
 * fallback) via BiometricPrompt. Every outcome is written to the audit log.
 */
class BiometricSecurityManager(
    private val audit: AuditRepository,
    private val scope: CoroutineScope,
) {
    enum class Result { SUCCESS, FAILED, CANCELLED, UNAVAILABLE }

    /** Strong biometrics or device credential; API 28–29 can't combine STRONG with a credential. */
    private val authenticators: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun isAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, reason: String, onResult: (Result) -> Unit) {
        if (!isAvailable(activity)) {
            // No fingerprint or screen lock set up: the app can't verify anyone. It stays usable
            // (otherwise it would be bricked) but the gap is recorded.
            log(AuditAction.BIOMETRIC_UNAVAILABLE, "No fingerprint or screen lock available — $reason opened unprotected")
            onResult(Result.UNAVAILABLE)
            return
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                log(AuditAction.BIOMETRIC_AUTH_SUCCEEDED, reason)
                onResult(Result.SUCCESS)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                log(AuditAction.BIOMETRIC_AUTH_FAILED, "$reason — ${if (cancelled) "cancelled" else errString}")
                onResult(if (cancelled) Result.CANCELLED else Result.FAILED)
            }

            override fun onAuthenticationFailed() {
                // A single unrecognised finger; the prompt stays open for another try.
                log(AuditAction.BIOMETRIC_AUTH_FAILED, "$reason — fingerprint not recognised")
            }
        }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Kohli Protocol")
                .setSubtitle(reason)
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private fun log(action: AuditAction, description: String) {
        scope.launch { audit.log(action, description) }
    }
}
