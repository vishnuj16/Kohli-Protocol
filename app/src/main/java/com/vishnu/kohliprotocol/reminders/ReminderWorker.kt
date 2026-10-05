package com.vishnu.kohliprotocol.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.MainActivity
import com.vishnu.kohliprotocol.data.local.entity.MealType
import java.time.LocalDate
import java.time.LocalTime

/** Notifies if the scheduled day still has meal slots that are neither logged nor skipped. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val time = inputData.getString(ReminderScheduler.KEY_TIME)?.let(LocalTime::parse)
        // The day the reminder was scheduled for — a run delayed past midnight must not
        // check the wrong day.
        val date = inputData.getString(ReminderScheduler.KEY_DATE)?.let(LocalDate::parse) ?: LocalDate.now()
        try {
            val food = (applicationContext as KohliApplication).container.foodRepository
            val missing = food.missingSlots(date)
            if (missing.isNotEmpty() && !date.isBefore(LocalDate.now().minusDays(1))) {
                notify(missing)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reminder check failed", e)
        } finally {
            time?.let { ReminderScheduler.scheduleNext(applicationContext, it) }
        }
        return Result.success()
    }

    private fun notify(missing: List<MealType>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Reminder suppressed: notification permission not granted")
            return
        }
        val context = applicationContext
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Log reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "End-of-day reminders when meals are not logged"
            }
        )

        val text = "Not logged: ${missing.joinToString { it.label }}. Log each meal or mark it skipped."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Your day is incomplete")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "KohliProtocol"
        private const val CHANNEL_ID = "log_reminders"
        private const val NOTIFICATION_ID = 100
    }
}
