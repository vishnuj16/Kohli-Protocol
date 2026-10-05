package com.vishnu.kohliprotocol.analysis

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.data.ai.AiException
import java.time.LocalDate

/** Fires at 23:00: queues analysis of today (plus any backlog), then schedules tomorrow. */
class NightlyAnalysisTrigger(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val through = inputData.getString(AnalysisScheduler.KEY_THROUGH)?.let(LocalDate::parse) ?: LocalDate.now()
        AnalysisScheduler.enqueueRun(applicationContext, through, replace = true)
        AnalysisScheduler.scheduleNextNightly(applicationContext)
        return Result.success()
    }
}

/**
 * Analyzes every pending day up to the given date. Retryable failures (rate limit, network,
 * provider outage, malformed JSON) return [Result.retry] so WorkManager backs off; after
 * [MAX_ATTEMPTS] the days stay "Analysis Pending" for the next nightly run or "Analyze Now".
 */
class AnalysisRunWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val through = inputData.getString(AnalysisScheduler.KEY_THROUGH)?.let(LocalDate::parse)
            ?: LocalDate.now().minusDays(1)
        val manager = (applicationContext as KohliApplication).container.analysisManager

        var retry = false
        for (date in manager.pendingDates(through)) {
            val outcome = manager.analyze(date)
            if (outcome !is DailyAnalysisManager.Outcome.Failed) continue
            val error = outcome.error
            if (error is AiException.MissingApiKey || error is AiException.Auth) break  // nothing will work
            if (error.retryable) retry = true
            if (error is AiException.RateLimited) break  // don't hammer a rate-limited API
        }

        return if (retry && runAttemptCount + 1 < MAX_ATTEMPTS) {
            Log.i(TAG, "Analysis run will retry (attempt ${runAttemptCount + 1})")
            Result.retry()
        } else {
            Result.success()
        }
    }

    private companion object {
        const val TAG = "KohliProtocol"
        const val MAX_ATTEMPTS = 4
    }
}
