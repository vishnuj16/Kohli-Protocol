package com.vishnu.kohliprotocol.ui.setup

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.vishnu.kohliprotocol.enforcement.AppMonitorService
import com.vishnu.kohliprotocol.enforcement.EnforcementForegroundService
import com.vishnu.kohliprotocol.enforcement.TrustedClock
import com.vishnu.kohliprotocol.ui.components.InlineMessage
import com.vishnu.kohliprotocol.ui.components.KohliCard
import com.vishnu.kohliprotocol.ui.components.MessageTone
import com.vishnu.kohliprotocol.ui.components.MutedText
import com.vishnu.kohliprotocol.ui.components.PageColumn
import com.vishnu.kohliprotocol.ui.components.PageScaffold
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import com.vishnu.kohliprotocol.ui.theme.KohliTheme

/**
 * Enforcement setup: shows enforcement status and links to the permissions it needs.
 * Deliberately not fingerprint-gated, so enforcement can always be repaired.
 */
class SetupActivity : ComponentActivity() {

    private data class SetupStatus(
        val accessibility: Boolean,
        val notifications: Boolean,
        val batteryExempt: Boolean,
        val clockTrusted: Boolean,
    ) {
        val allGood get() = accessibility && notifications && batteryExempt && clockTrusted
    }

    private var status by mutableStateOf<SetupStatus?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            KohliTheme {
                PageScaffold(title = "Enforcement setup", onBack = ::finish) { padding ->
                    PageColumn(padding) {
                        val current = status
                        if (current != null) {
                            InlineMessage(
                                if (current.allGood) "Enforcement is fully armed." else "Some protections need attention.",
                                tone = if (current.allGood) MessageTone.SUCCESS else MessageTone.ERROR,
                            )
                            StatusRow(
                                title = "App monitor",
                                subtitle = "Accessibility service that blocks restricted apps",
                                on = current.accessibility,
                                onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            )
                            StatusRow(
                                title = "Notifications",
                                subtitle = "Reminders and the enforcement notice",
                                on = current.notifications,
                                onClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    {
                                        ActivityCompat.requestPermissions(
                                            this@SetupActivity,
                                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                            REQUEST_NOTIFICATIONS,
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                            StatusRow(
                                title = "Battery optimization exempt",
                                subtitle = "Stops Android from killing enforcement",
                                on = current.batteryExempt,
                                onClick = {
                                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri()))
                                },
                            )
                            StatusRow(
                                title = "System clock trusted",
                                subtitle = "Turn on automatic date & time if this is off",
                                on = current.clockTrusted,
                                onClick = null,
                            )
                            MutedText("Tap a row to open the matching system setting.", Modifier)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        EnforcementForegroundService.start(this)
        refreshStatus()
    }

    private fun refreshStatus() {
        val power = getSystemService(PowerManager::class.java)
        val clock = TrustedClock.get(this).now()
        status = SetupStatus(
            accessibility = AppMonitorService.isEnabled(this),
            notifications = NotificationManagerCompat.from(this).areNotificationsEnabled(),
            batteryExempt = power.isIgnoringBatteryOptimizations(packageName),
            clockTrusted = !clock.isTampered,
        )
    }

    private fun packageUri() = Uri.parse("package:$packageName")

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1
    }
}

/** A status line with a switch that reflects the real state; tapping opens the fix. */
@Composable
private fun StatusRow(title: String, subtitle: String, on: Boolean, onClick: (() -> Unit)?) {
    KohliCard(onClick = onClick, borderColor = if (on) KohliColors.Outline else KohliColors.Missing.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KohliColors.Muted)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = on,
                onCheckedChange = onClick?.let { action -> { _: Boolean -> action() } },
                enabled = onClick != null || on,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = KohliColors.OnAccent,
                    checkedTrackColor = KohliColors.Logged,
                    uncheckedThumbColor = KohliColors.Muted,
                    uncheckedTrackColor = KohliColors.SurfaceHigh,
                    uncheckedBorderColor = KohliColors.Accent,
                    disabledCheckedTrackColor = KohliColors.Logged.copy(alpha = 0.6f),
                    disabledUncheckedTrackColor = KohliColors.SurfaceHigh,
                ),
            )
        }
    }
}
