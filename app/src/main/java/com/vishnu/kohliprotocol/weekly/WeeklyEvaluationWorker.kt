package com.vishnu.kohliprotocol.weekly

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.data.reports.WeeklyReportWorker
import java.util.concurrent.TimeUnit

/** Evaluates finished weeks. Purely local — needs no network. */
class WeeklyEvaluationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        (applicationContext as KohliApplication).container.weeklyEvaluationManager.evaluatePendingWeeks()
        // Also picks up reports whose PDF or email is still outstanding (e.g. email set up later).
        WeeklyReportWorker.enqueue(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK = "weekly_evaluation_periodic"
        private const val NOW_WORK = "weekly_evaluation_now"

        /**
         * Checks every 6 hours: a week is evaluated soon after it ends, or as soon as its last
         * pending AI analysis arrives.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyEvaluationWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WeeklyEvaluationWorker>().build(),
            )
        }
    }
}
