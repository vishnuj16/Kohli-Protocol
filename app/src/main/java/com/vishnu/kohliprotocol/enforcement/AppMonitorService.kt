package com.vishnu.kohliprotocol.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.data.repository.EnforcementSnapshot
import com.vishnu.kohliprotocol.data.restrictions.RestrictionLists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Watches the app windows on screen and covers any restricted app with [RestrictionOverlay]
 * for as long as it stays visible.
 *
 * Decisions come from the live window list, not from individual launch events, so a missed or
 * coalesced event (e.g. a warm app resuming from the background) cannot let an app through.
 */
class AppMonitorService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val recheck = Runnable { checkScreen() }
    private var overlay: RestrictionOverlay? = null
    private var blockedPackage: String? = null
    private var receiverRegistered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Latest lists / game access / override from DataStore and Room; null until first loaded. */
    @Volatile
    private var snapshot: EnforcementSnapshot? = null

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // PhonePe opened at 20:59 must lock at 21:00; clock/timezone changes apply at once.
            checkScreen()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "App monitor connected")
        overlay = RestrictionOverlay(this) { performGlobalAction(GLOBAL_ACTION_HOME) }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, timeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        EnforcementForegroundService.start(this)
        scope.launch {
            (application as KohliApplication).container.enforcementRepository.snapshot.collect {
                snapshot = it
                checkScreen()
            }
        }
        checkScreen()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        checkScreen()
        // Windows are still settling during launch animations; look again once they have.
        handler.postDelayed(recheck, SETTLE_MS)
    }

    private fun checkScreen() {
        handler.removeCallbacks(recheck)
        val overlay = overlay ?: return
        val clock = TrustedClock.get(this)

        // Fail closed: until the stored state has loaded, the built-in lists apply, games are
        // locked and no override is active.
        val current = snapshot
        val lists = current?.lists ?: RestrictionLists.DEFAULTS
        val gamesLocked = current?.games?.isUnlocked() != true
        val suspended = current?.emergencyOverride?.suspended().orEmpty()

        val verdict = visibleAppPackages().firstNotNullOfOrNull { pkg ->
            RestrictionPolicy.evaluate(pkg, clock, lists, gamesLocked, suspended)?.let { pkg to it }
        }

        if (verdict != null) {
            val (pkg, reason) = verdict
            if (pkg != blockedPackage) Log.w(TAG, "Blocking $pkg ($reason)")
            blockedPackage = pkg
            val detail = if (reason == RestrictionReason.GAMES_LOCKED) current?.games?.lockReason() else null
            overlay.show(pkg, reason, detail)
            // Keep watching while blocked, so the overlay leaves as soon as the app does.
            handler.postDelayed(recheck, WATCHDOG_MS)
        } else if (overlay.isShowing) {
            Log.i(TAG, "Restricted app left the screen — overlay removed")
            blockedPackage = null
            overlay.hide()
        }
    }

    /** Packages owning an application window currently on screen (includes split-screen/PiP). */
    private fun visibleAppPackages(): List<String> {
        val fromWindows = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root?.packageName?.toString() }
        if (fromWindows.isNotEmpty()) return fromWindows
        return listOfNotNull(rootInActiveWindow?.packageName?.toString())
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        Log.w(TAG, "App monitor destroyed — enforcement is offline")
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        overlay?.hide()
        overlay = null
        if (receiverRegistered) {
            unregisterReceiver(timeReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KohliProtocol"
        private const val SETTLE_MS = 400L
        private const val WATCHDOG_MS = 1_000L

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val self = ComponentName(context, AppMonitorService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
        }
    }
}
