package com.vishnu.kohliprotocol.enforcement

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Tamper-aware clock for time-based restrictions.
 *
 * The wall clock is anchored to [SystemClock.elapsedRealtime], which is monotonic, keeps counting
 * through deep sleep and cannot be changed by the user. While an anchor holds, trusted time is
 * `anchorWall + (elapsedNow - anchorElapsed)`; the system clock is only compared against it.
 *
 * The anchor is refreshed from the system clock only when that clock can be trusted: on first
 * run, whenever Android's automatic (network) date & time is on, or after a clean reboot. A
 * reboot cannot launder a clock that was already flagged as tampered.
 */
class TrustedClock private constructor(context: Context) {

    data class Reading(
        val epochMillis: Long,
        val zone: ZoneId,
        val clockTampered: Boolean,
        val zoneTampered: Boolean,
    ) {
        val isTampered: Boolean get() = clockTampered || zoneTampered
        val localTime: LocalTime get() = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun now(): Reading {
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val bootCount = globalInt(Settings.Global.BOOT_COUNT, -1)
        val systemZone = ZoneId.systemDefault()
        val autoTime = globalInt(Settings.Global.AUTO_TIME, 1) == 1
        val autoZone = globalInt(Settings.Global.AUTO_TIME_ZONE, 1) == 1

        if (!prefs.contains(KEY_ANCHOR_WALL)) {
            return persist(wall, elapsed, bootCount, systemZone, false, false, reanchor = true)
        }

        val anchorWall = prefs.getLong(KEY_ANCHOR_WALL, wall)
        val anchorElapsed = prefs.getLong(KEY_ANCHOR_ELAPSED, elapsed)
        val anchorBootCount = prefs.getInt(KEY_ANCHOR_BOOT_COUNT, bootCount)
        val lastTrusted = prefs.getLong(KEY_LAST_TRUSTED, anchorWall)
        val wasClockTampered = prefs.getBoolean(KEY_CLOCK_TAMPERED, false)
        val rebooted = bootCount != anchorBootCount || elapsed < anchorElapsed

        val trusted: Long
        val clockTampered: Boolean
        val reanchor: Boolean
        when {
            autoTime -> {
                // Network-synced time cannot be set by hand.
                trusted = wall
                clockTampered = false
                reanchor = true
            }
            rebooted -> {
                // At least the time since boot has passed since the last heartbeat.
                val floor = lastTrusted + elapsed
                clockTampered = wasClockTampered || wall < floor - TOLERANCE_MS
                trusted = if (clockTampered) floor else wall
                reanchor = true
            }
            else -> {
                trusted = anchorWall + (elapsed - anchorElapsed)
                clockTampered = abs(wall - trusted) > TOLERANCE_MS
                reanchor = false
            }
        }

        val anchorZone = prefs.getString(KEY_ANCHOR_ZONE, null)
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: systemZone
        val zone = if (autoZone) systemZone else anchorZone
        val zoneTampered = !autoZone && systemZone != anchorZone

        return persist(trusted, elapsed, bootCount, zone, clockTampered, zoneTampered, reanchor)
    }

    private fun persist(
        trusted: Long,
        elapsed: Long,
        bootCount: Int,
        zone: ZoneId,
        clockTampered: Boolean,
        zoneTampered: Boolean,
        reanchor: Boolean,
    ): Reading {
        if (clockTampered != prefs.getBoolean(KEY_CLOCK_TAMPERED, false)) {
            if (clockTampered) Log.w(TAG, "Clock manipulation detected — using monotonic trusted time")
            else Log.i(TAG, "System clock trusted again")
        }
        if (zoneTampered != prefs.getBoolean(KEY_ZONE_TAMPERED, false)) {
            if (zoneTampered) Log.w(TAG, "Manual timezone change detected — keeping ${zone.id}")
            else Log.i(TAG, "Timezone trusted again")
        }

        prefs.edit().apply {
            if (reanchor) {
                putLong(KEY_ANCHOR_WALL, trusted)
                putLong(KEY_ANCHOR_ELAPSED, elapsed)
                putInt(KEY_ANCHOR_BOOT_COUNT, bootCount)
            }
            putString(KEY_ANCHOR_ZONE, zone.id)
            putLong(KEY_LAST_TRUSTED, trusted)
            putBoolean(KEY_CLOCK_TAMPERED, clockTampered)
            putBoolean(KEY_ZONE_TAMPERED, zoneTampered)
        }.apply()

        return Reading(trusted, zone, clockTampered, zoneTampered)
    }

    private fun globalInt(name: String, default: Int): Int =
        Settings.Global.getInt(appContext.contentResolver, name, default)

    companion object {
        private const val TAG = "KohliProtocol"
        private const val PREFS_NAME = "trusted_clock"
        private const val TOLERANCE_MS = 2 * 60 * 1000L

        private const val KEY_ANCHOR_WALL = "anchor_wall"
        private const val KEY_ANCHOR_ELAPSED = "anchor_elapsed"
        private const val KEY_ANCHOR_BOOT_COUNT = "anchor_boot_count"
        private const val KEY_ANCHOR_ZONE = "anchor_zone"
        private const val KEY_LAST_TRUSTED = "last_trusted"
        private const val KEY_CLOCK_TAMPERED = "clock_tampered"
        private const val KEY_ZONE_TAMPERED = "zone_tampered"

        @Volatile
        private var instance: TrustedClock? = null

        fun get(context: Context): TrustedClock =
            instance ?: synchronized(this) {
                instance ?: TrustedClock(context).also { instance = it }
            }
    }
}
