package com.vishnu.kohliprotocol.ui.discipline

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.vishnu.kohliprotocol.data.local.entity.WeeklyReportEntity
import com.vishnu.kohliprotocol.data.reports.ReportEmailInfo
import com.vishnu.kohliprotocol.ui.components.GhostButton
import com.vishnu.kohliprotocol.ui.components.MutedText
import com.vishnu.kohliprotocol.ui.components.PrimaryButton
import com.vishnu.kohliprotocol.ui.components.SecondaryButton
import com.vishnu.kohliprotocol.ui.components.SectionCard
import com.vishnu.kohliprotocol.ui.components.StatusPill
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val shortDay = DateTimeFormatter.ofPattern("d MMM")

/** Weekly results with their PDF reports, plus the in-progress preview. */
@Composable
fun WeeklyReportsSection(
    reports: List<WeeklyReportEntity>,
    protocolStart: LocalDate?,
    emailedIds: Set<String>,
    busy: Boolean,
    onEvaluateNow: () -> Unit,
    onView: (String) -> Unit,
    onShare: (String) -> Unit,
    onRebuild: (WeeklyReportEntity) -> Unit,
    onPreview: () -> Unit,
    onEmailPreview: () -> Unit,
) {
    SectionCard("Weekly results & reports") {
        protocolStart?.let { start ->
            val first = start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val last = start.minusDays(1).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            MutedText(
                "Your weeks run $first → $last (protocol started ${start.format(shortDay)}). Each result " +
                    "arrives $first morning, once $last's analysis is in; its PDF report is then emailed to you."
            )
        }
        if (busy) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                color = KohliColors.Accent,
                trackColor = KohliColors.Outline,
            )
        }
        if (reports.isEmpty()) MutedText("No weeks evaluated yet.")

        reports.take(12).forEach { report ->
            val pdf = remember(report.pdfPath) { report.pdfPath?.takeIf { File(it).isFile } }
            val resultColor = if (report.isSuccess) KohliColors.Logged else KohliColors.Missing
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = KohliColors.Background,
                border = BorderStroke(1.dp, KohliColors.Outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(resultColor))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${report.startDate.format(shortDay)} – ${report.endDate.format(shortDay)}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            StatusPill(if (report.isSuccess) "Success" else "Failed", resultColor)
                        }
                        Text(
                            String.format(
                                Locale.US, "avg %.2f vs %.1f · logs %s", report.averageRating, report.biryaniParameter,
                                if (report.logsComplete) "complete" else "incomplete",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = KohliColors.Muted,
                        )
                        Text(
                            listOfNotNull(
                                if (pdf != null) "PDF ready" else "PDF pending",
                                if (report.id.toString() in emailedIds) "emailed ✓" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = KohliColors.Muted,
                        )
                        Row {
                            if (pdf != null) {
                                GhostButton("View", onClick = { onView(pdf) })
                                GhostButton("Share", onClick = { onShare(pdf) })
                            }
                            GhostButton("Rebuild & email", onClick = { onRebuild(report) }, enabled = !busy, color = KohliColors.Muted)
                        }
                    }
                }
            }
        }

        GhostButton("Evaluate finished weeks now", onClick = onEvaluateNow, enabled = !busy)
        PrimaryButton("Preview this week's report", onClick = onPreview, enabled = !busy)
        GhostButton("Email me the preview (test)", onClick = onEmailPreview, enabled = !busy, color = KohliColors.Muted)
    }
}

/** The REPORT Brevo account — separate from the Guardian account used for codes. */
@Composable
fun ReportEmailSection(info: ReportEmailInfo?, onSave: (apiKey: String, sender: String, senderName: String, recipient: String) -> Unit) {
    var apiKey by rememberSaveable { mutableStateOf("") }
    var sender by rememberSaveable(info?.senderEmail) { mutableStateOf(info?.senderEmail.orEmpty()) }
    var senderName by rememberSaveable(info?.senderName) { mutableStateOf(info?.senderName ?: "Kohli Protocol") }
    var recipient by rememberSaveable(info?.recipientEmail) { mutableStateOf(info?.recipientEmail.orEmpty()) }
    val configured = info?.hasApiKey == true && info?.recipientEmail?.isNotBlank() == true

    SectionCard("Report email") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MutedText(
                "Your own Brevo account, used only to email weekly reports to you — separate from the " +
                    "Guardian account that sends approval codes.",
                modifier = Modifier.weight(1f),
            )
        }
        StatusPill(
            if (configured) "Configured: ${info?.senderEmail} → ${info?.recipientEmail}" else "Not configured",
            if (configured) KohliColors.Logged else KohliColors.Missing,
        )
        OutlinedTextField(
            apiKey, { apiKey = it }, label = { Text("Report Brevo API key") },
            placeholder = { Text(if (info?.hasApiKey == true) "•••••••• (saved)" else "xkeysib-…") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(sender, { sender = it }, label = { Text("Verified sender email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(senderName, { senderName = it }, label = { Text("Sender name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(recipient, { recipient = it }, label = { Text("Send reports to (your email)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SecondaryButton(
            "Save report email",
            onClick = {
                onSave(apiKey, sender, senderName, recipient)
                apiKey = ""
            },
        )
    }
}

/** Opening and sharing report PDFs through the app's FileProvider (temporary read grants only). */
object ReportFiles {

    private fun uri(context: Context, path: String) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))

    fun view(context: Context, path: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri(context, path), "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No PDF viewer installed — use Share instead", Toast.LENGTH_LONG).show()
        }
    }

    fun share(context: Context, path: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri(context, path))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share weekly report"))
    }
}
