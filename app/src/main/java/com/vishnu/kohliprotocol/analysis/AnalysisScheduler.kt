package com.vishnu.kohliprotocol.analysis

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Background analysis:
 * - a nightly trigger at 23:00 that re-schedules itself for the next day (like the reminders);
 * - an analysis run that needs network and retries with exponential backoff on
 *   rate limits / outages. Every run also catches up on older pending days, which gives
 *   failed days the "retry about once a day" behaviour.
 */
object AnalysisScheduler {

    val NIGHTLY_TIME: LocalTime = LocalTime.of(23, 0)

    internal const val KEY_THROUGH = "analyze_through"

    private const val NIGHTLY_WORK = "daily_analysis_nightly"
    private const val RUN_WORK = "daily_analysis_run"

    /** Safe to call on every app start: a pending nightly trigger is kept. */
    fun scheduleNightly(context: Context) = scheduleNightly(context, ExistingWorkPolicy.KEEP)

    internal fun scheduleNextNightly(context: Context) =
        scheduleNightly(context, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun scheduleNightly(context: Context, policy: ExistingWorkPolicy) {
        val now = ZonedDateTime.now()
        var next = now.with(NIGHTLY_TIME).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val request = OneTimeWorkRequestBuilder<NightlyAnalysisTrigger>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_THROUGH to next.toLocalDate().toString()))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NIGHTLY_WORK, policy, request)
    }

    /**
     * Analyzes every pending day up to [through]. [replace] restarts a run already waiting
     * on backoff (used by the nightly trigger so today is included); otherwise it is kept.
     */
    fun enqueueRun(context: Context, through: LocalDate, replace: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<AnalysisRunWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_THROUGH to through.toString()))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RUN_WORK,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** On app open: pick up yesterday (and older) if the nightly run missed or failed. */
    fun enqueueCatchUp(context: Context) = enqueueRun(context, LocalDate.now().minusDays(1))
}
