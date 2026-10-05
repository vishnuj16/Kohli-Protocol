package com.vishnu.kohliprotocol.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vishnu.kohliprotocol.analysis.AnalysisScheduler
import com.vishnu.kohliprotocol.data.reports.WeeklyReportWorker
import com.vishnu.kohliprotocol.weekly.WeeklyEvaluationWorker

/**
 * Re-engages enforcement after a reboot or an app update. The accessibility service itself is
 * rebound by the system; this brings back the foreground service and checks the clock.
 *
 * It also catches up on anything missed while the phone was off: pending day analyses, weeks
 * that ended in the meantime, and their PDF reports/emails. Those jobs wait for a network
 * connection, so a report missed at the week boundary goes out as soon as the phone is online.
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
                AnalysisScheduler.enqueueCatchUp(context)
                WeeklyEvaluationWorker.runNow(context)
                WeeklyReportWorker.enqueue(context)
            }
        }
    }

    companion object {
        private const val TAG = "KohliProtocol"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
