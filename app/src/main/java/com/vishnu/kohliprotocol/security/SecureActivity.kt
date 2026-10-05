package com.vishnu.kohliprotocol.security

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.vishnu.kohliprotocol.AppContainer
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.ui.components.LockedPanel
import com.vishnu.kohliprotocol.ui.theme.KohliTheme

/**
 * Base for every screen showing private data (food history, ratings, photos, settings, audit
 * log). Blocks screenshots/recordings with FLAG_SECURE and hides content behind a fingerprint
 * prompt until the session is unlocked (see [SessionLock]).
 *
 * Screens with [requiresOwnUnlock] ask again on every entry, even inside an unlocked session.
 */
abstract class SecureActivity : FragmentActivity() {

    /** Shown in the fingerprint prompt and the audit log, e.g. "Motivation gallery". */
    protected abstract val screenName: String

    /**
     * True for especially private screens (the Motivation gallery): each visit needs its own
     * fingerprint/PIN. The unlock lasts while this screen is open — including brief trips to
     * the photo picker — and ends when the screen is left or the session locks.
     */
    protected open val requiresOwnUnlock: Boolean = false

    /** This screen instance has been unlocked (only meaningful with [requiresOwnUnlock]). */
    private var screenUnlocked = false

    protected val container: AppContainer get() = (application as KohliApplication).container

    private var locked by mutableStateOf(true)
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        locked = !isAccessible()
    }

    override fun onResume() {
        super.onResume()
        // If the whole session locked (app backgrounded too long), this screen locks with it.
        if (!SessionLock.isUnlocked()) {
            screenUnlocked = false
            onSessionLocked()
        }
        if (isAccessible()) {
            locked = false
        } else {
            locked = true
            requestUnlock()
        }
    }

    /** Use instead of setContent: shows [content] only while the session is unlocked. */
    protected fun setSecureContent(content: @Composable () -> Unit) {
        setContent {
            KohliTheme {
                if (locked) {
                    LockedPanel(
                        title = "Kohli Protocol is locked",
                        subtitle = "Your fingerprint is required to view this.",
                        onUnlock = ::requestUnlock,
                    )
                } else {
                    content()
                }
            }
        }
    }

    /** Called from onResume when the whole session has locked (e.g. a tab should re-lock too). */
    protected open fun onSessionLocked() = Unit

    /** Called after a successful unlock of this screen. */
    protected open fun onSessionUnlocked() = Unit

    /**
     * Separate fingerprint/PIN check for one especially private section inside an unlocked
     * screen (e.g. the Motivation tab). [onUnlocked] runs only on success.
     */
    protected fun authenticateSection(reason: String, onDeclined: () -> Unit = {}, onUnlocked: () -> Unit) {
        if (prompting) return
        prompting = true
        container.biometric.authenticate(this, reason) { result ->
            prompting = false
            if (result == BiometricSecurityManager.Result.SUCCESS || result == BiometricSecurityManager.Result.UNAVAILABLE) {
                SessionLock.markUnlocked()
                onUnlocked()
            } else {
                onDeclined()
            }
        }
    }

    private fun isAccessible(): Boolean =
        SessionLock.isUnlocked() && (!requiresOwnUnlock || screenUnlocked)

    private fun requestUnlock() {
        if (prompting) return
        prompting = true
        container.biometric.authenticate(this, screenName) { result ->
            prompting = false
            if (result == BiometricSecurityManager.Result.SUCCESS || result == BiometricSecurityManager.Result.UNAVAILABLE) {
                SessionLock.markUnlocked()
                screenUnlocked = true
                locked = false
                onSessionUnlocked()
            }
        }
    }
}
