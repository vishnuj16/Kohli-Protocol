package com.vishnu.kohliprotocol.data.reports

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vishnu.kohliprotocol.KohliApplication
import java.util.concurrent.TimeUnit

/**
 * Builds and emails pending weekly reports. Waits for a network; if the AI summary fails
 * temporarily it retries with backoff (about 1h, then 2h), and the final attempt sends the
 * report without the summary rather than not at all.
 */
class WeeklyReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = (applicationContext as KohliApplication).container.weeklyReportManager
        val retry = manager.processPending(allowAiRetry = runAttemptCount < AI_RETRY_ATTEMPTS)
        return if (retry) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK = "weekly_report"
        private const val AI_RETRY_ATTEMPTS = 2

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WeeklyReportWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
        }
    }
}
