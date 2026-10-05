package com.vishnu.kohliprotocol.security

import android.os.Build
import android.os.SystemClock
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

    /** The prompt currently on screen (and why), so a leaving screen can cancel only its own. */
    private var activePrompt: BiometricPrompt? = null
    private var activeReason: String? = null

    /**
     * When the Motivation gallery was last unlocked, on the monotonic clock (changing the system
     * time can't extend it). In memory only: a process restart always asks again.
     */
    private var lastMotivationAuthTime: Long = 0L

    /** True within [MOTIVATION_AUTH_TIMEOUT_MS] of the last Motivation unlock. */
    fun isMotivationSessionValid(): Boolean =
        lastMotivationAuthTime > 0L &&
            SystemClock.elapsedRealtime() - lastMotivationAuthTime < MOTIVATION_AUTH_TIMEOUT_MS

    fun markMotivationAuthenticated() {
        lastMotivationAuthTime = SystemClock.elapsedRealtime()
    }

    /** Cancels the on-screen prompt, but only if it was opened for [reason]. */
    fun cancelAuthentication(reason: String) {
        if (activeReason != reason) return
        activePrompt?.cancelAuthentication()
        activePrompt = null
        activeReason = null
    }

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
                clearActive(reason)
                log(AuditAction.BIOMETRIC_AUTH_SUCCEEDED, reason)
                onResult(Result.SUCCESS)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                clearActive(reason)
                log(AuditAction.BIOMETRIC_AUTH_FAILED, "$reason — ${if (cancelled) "cancelled" else errString}")
                onResult(if (cancelled) Result.CANCELLED else Result.FAILED)
            }

            override fun onAuthenticationFailed() {
                // A single unrecognised finger; the prompt stays open for another try.
                log(AuditAction.BIOMETRIC_AUTH_FAILED, "$reason — fingerprint not recognised")
            }
        }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        activePrompt = prompt
        activeReason = reason
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Kohli Protocol")
                .setSubtitle(reason)
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private fun clearActive(reason: String) {
        if (activeReason == reason) {
            activePrompt = null
            activeReason = null
        }
    }

    private fun log(action: AuditAction, description: String) {
        scope.launch { audit.log(action, description) }
    }

    companion object {
        /** The Motivation gallery stays unlocked this long after a successful fingerprint/PIN. */
        const val MOTIVATION_AUTH_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
