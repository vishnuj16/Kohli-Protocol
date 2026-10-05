package com.vishnu.kohliprotocol.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock

/**
 * One unlock per app session. The session locks again once the app has been in the
 * background for longer than [GRACE_MILLIS] (measured on the monotonic clock, so changing the
 * system time doesn't extend it). Registered in [com.vishnu.kohliprotocol.KohliApplication].
 */
object SessionLock : Application.ActivityLifecycleCallbacks {

    private const val GRACE_MILLIS = 30_000L

    @Volatile
    private var unlocked = false
    private var startedActivities = 0
    private var backgroundedAt = 0L

    fun isUnlocked(): Boolean = unlocked

    fun markUnlocked() {
        unlocked = true
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities == 0 && unlocked && backgroundedAt > 0 &&
            SystemClock.elapsedRealtime() - backgroundedAt > GRACE_MILLIS
        ) {
            unlocked = false
        }
        startedActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
