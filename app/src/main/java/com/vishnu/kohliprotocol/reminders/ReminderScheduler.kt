package com.vishnu.kohliprotocol.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * End-of-day log reminders at 20:00 and 22:30. Each time runs as its own one-shot work that
 * re-schedules the next day's run when it finishes, so the reminders keep firing at the same wall-clock
 * time (a 24h periodic job would drift). WorkManager persists them across reboots.
 */
object ReminderScheduler {

    val REMINDER_TIMES: List<LocalTime> = listOf(LocalTime.of(20, 0), LocalTime.of(22, 30))

    internal const val KEY_TIME = "reminder_time"
    internal const val KEY_DATE = "reminder_date"

    /** Safe to call on every app start: existing pending reminders are kept. */
    fun scheduleAll(context: Context) {
        REMINDER_TIMES.forEach { schedule(context, it, ExistingWorkPolicy.KEEP) }
    }

    /** Called by the worker itself; appends so the running work is not cancelled. */
    internal fun scheduleNext(context: Context, time: LocalTime) {
        schedule(context, time, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun schedule(context: Context, time: LocalTime, policy: ExistingWorkPolicy) {
        val now = ZonedDateTime.now()
        var next = now.with(time).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    KEY_TIME to time.toString(),
                    KEY_DATE to next.toLocalDate().toString(),
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(workName(time), policy, request)
    }

    private fun workName(time: LocalTime) = "log_reminder_${time.hour}_${time.minute}"
}
