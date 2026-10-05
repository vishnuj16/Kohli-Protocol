package com.vishnu.kohliprotocol.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vishnu.kohliprotocol.ui.components.PrimaryButton
import com.vishnu.kohliprotocol.ui.components.StatusPill
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import com.vishnu.kohliprotocol.ui.theme.KohliTheme
import com.vishnu.kohliprotocol.ui.theme.KohliType

/**
 * Full-screen, opaque accessibility overlay drawn above a restricted app. It swallows every touch
 * and the back key, so the app underneath cannot be used. The only way out is home.
 *
 * Uses TYPE_ACCESSIBILITY_OVERLAY, which needs no extra permission and is not subject to
 * background-activity-launch restrictions. The content is Compose, hosted in a ComposeView with
 * its own lifecycle (a service has none of its own).
 */
class RestrictionOverlay(
    private val service: AccessibilityService,
    private val onReturnHome: () -> Unit,
) {
    private data class Content(val appName: String, val reason: RestrictionReason, val detail: String)

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var shown: Triple<String, RestrictionReason, String?>? = null

    /** Read by the composition, so changing it re-renders the overlay in place. */
    private var content by mutableStateOf<Content?>(null)

    val isShowing: Boolean get() = root != null

    /** [detail] replaces the reason's default explanation when given. */
    fun show(pkg: String, reason: RestrictionReason, detail: String? = null) {
        val key = Triple(pkg, reason, detail)
        if (shown == key) return
        content = Content(appLabel(pkg), reason, detail ?: detailFor(reason))
        if (root == null) {
            val owner = OverlayLifecycleOwner().also { it.create() }
            val container = createRoot()
            container.setViewTreeLifecycleOwner(owner)
            container.setViewTreeSavedStateRegistryOwner(owner)
            container.addView(
                ComposeView(service).apply {
                    setContent {
                        KohliTheme {
                            content?.let { RestrictionScreen(it.appName, it.reason, it.detail, onReturnHome) }
                        }
                    }
                },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            windowManager.addView(container, layoutParams())
            owner.resume()
            root = container
            lifecycleOwner = owner
        }
        shown = key
    }

    fun hide() {
        root?.let { windowManager.removeView(it) }
        lifecycleOwner?.destroy()
        root = null
        lifecycleOwner = null
        shown = null
        content = null
    }

    private fun createRoot() = object : FrameLayout(service) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onReturnHome()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }.apply {
        setBackgroundColor(BACKGROUND_ARGB)
        isClickable = true   // consume every touch that misses the button
        isFocusable = true
        isFocusableInTouchMode = true
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.OPAQUE,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
        }
    }

    private fun detailFor(reason: RestrictionReason): String = when (reason) {
        RestrictionReason.FOOD_DELIVERY ->
            "Food-delivery apps are blocked at all times."
        RestrictionReason.NIGHTTIME_PAYMENT ->
            "Payments are locked from 21:00 to 09:00. This app unlocks at 09:00."
        RestrictionReason.UNTRUSTED_CLOCK ->
            "The system clock or timezone appears to have been changed. Payment apps stay " +
                "locked until it can be trusted again — turn on automatic date & time."
        RestrictionReason.GAMES_LOCKED ->
            "Games unlock only after a successful week: complete logs and an average at or " +
                "above the Biryani Parameter."
    }

    private fun appLabel(pkg: String): String = try {
        val pm = service.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    private companion object {
        /** #0D0D10 — matches KohliColors.Background behind the composition. */
        const val BACKGROUND_ARGB = 0xFF0D0D10.toInt()
    }
}

@Composable
private fun RestrictionScreen(appName: String, reason: RestrictionReason, detail: String, onReturnHome: () -> Unit) {
    val games = reason == RestrictionReason.GAMES_LOCKED
    Box(Modifier.fillMaxSize().background(KohliColors.Background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(KohliColors.Warning.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🔒", fontSize = 48.sp)
            }
            StatusPill(if (games) "Games locked" else "Blocked", KohliColors.Warning)
            Text(
                if (games) "GAMES LOCKED" else "APPLICATION RESTRICTED",
                style = MaterialTheme.typography.headlineMedium,
                color = KohliColors.Text,
                textAlign = TextAlign.Center,
            )
            Text(appName, style = MaterialTheme.typography.titleMedium, color = KohliColors.Muted, textAlign = TextAlign.Center)
            Text(
                "This application is currently blocked by your personal discipline rules.",
                style = MaterialTheme.typography.bodyLarge,
                color = KohliColors.Text,
                textAlign = TextAlign.Center,
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = KohliColors.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Rational Vishnu sets the rules.",
                style = KohliType.Eyebrow.copy(fontStyle = FontStyle.Italic, letterSpacing = 1.sp),
                color = KohliColors.Accent,
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Return to Home", onClick = onReturnHome)
        }
    }
}

/** Minimal lifecycle + saved-state owner so a ComposeView can live in a service's overlay window. */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun create() {
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun resume() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
