package com.vishnu.kohliprotocol.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Re-engages enforcement after a reboot or an app update. The accessibility service itself is
 * rebound by the system; this brings back the foreground service and checks the clock.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_QUICKBOOT_POWERON,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Restoring enforcement after ${intent.action}")
                TrustedClock.get(context).now()
                EnforcementForegroundService.start(context)
            }
        }
    }

    companion object {
        private const val TAG = "KohliProtocol"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
