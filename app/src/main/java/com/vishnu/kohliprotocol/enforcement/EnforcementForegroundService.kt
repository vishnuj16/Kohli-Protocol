package com.vishnu.kohliprotocol.enforcement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.ui.setup.SetupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Persistent foreground service that keeps the process alive against battery optimization,
 * heartbeats [TrustedClock] every minute, and watches that the app monitor stays enabled.
 */
class EnforcementForegroundService : Service() {

    private var receiverRegistered = false
    private var lastStatusText: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, tickReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(statusText())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.i(TAG, "Enforcement foreground service running")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.w(TAG, "Enforcement foreground service destroyed")
        scope.cancel()
        if (receiverRegistered) {
            unregisterReceiver(tickReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun refresh() {
        // Expire stale Guardian Gate requests and end emergency overrides on time (audited).
        scope.launch { runCatching { (application as KohliApplication).container.guardianGate.housekeeping() } }
        val text = statusText()
        if (text != lastStatusText) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    /** Also serves as the clock heartbeat: every call persists the latest trusted time. */
    private fun statusText(): String {
        val clock = TrustedClock.get(this).now()
        return when {
            !AppMonitorService.isEnabled(this) ->
                "⚠ App monitor is OFF — enable the Kohli Protocol accessibility service"
            clock.isTampered ->
                "⚠ Clock untrusted — payment apps locked until automatic date & time is on"
            else ->
                "Food delivery blocked · PhonePe locked 21:00–09:00"
        }
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Kohli Protocol — Personal Discipline Enforcement Active")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, SetupActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
            .also { lastStatusText = text }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Enforcement",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Persistent notice that restrictions are being enforced" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "KohliProtocol"
        private const val CHANNEL_ID = "enforcement"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, EnforcementForegroundService::class.java),
                )
            } catch (e: IllegalStateException) {
                // Android 12+ refuses background FGS starts outside the exempt cases.
                Log.e(TAG, "Could not start enforcement service", e)
            }
        }
    }
}
