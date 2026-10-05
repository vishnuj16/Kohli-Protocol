package com.vishnu.kohliprotocol.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vishnu.kohliprotocol.data.local.entity.DailyAnalysisEntity
import com.vishnu.kohliprotocol.ui.components.AccentBanner
import com.vishnu.kohliprotocol.ui.components.Eyebrow
import com.vishnu.kohliprotocol.ui.components.GhostButton
import com.vishnu.kohliprotocol.ui.components.KohliCard
import com.vishnu.kohliprotocol.ui.components.MutedText
import com.vishnu.kohliprotocol.ui.components.PrimaryButton
import com.vishnu.kohliprotocol.ui.components.SecondaryButton
import com.vishnu.kohliprotocol.ui.components.StatusPill
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import com.vishnu.kohliprotocol.ui.theme.KohliType
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * "Yesterday's Rating" (spec §6): approve or dispute the AI's verdict. Shows the pending
 * state instead when yesterday has no usable analysis yet.
 */
@Composable
fun YesterdayCard(
    state: AnalysisUiState,
    analyzing: Boolean,
    onAccept: () -> Unit,
    onReject: (String) -> Unit,
    onAnalyzeNow: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenDetails: (() -> Unit)? = null,
) {
    val analysis = state.yesterdayAnalysis
    if (analysis == null && !state.yesterdayHasLogs) return
    var showReject by rememberSaveable { mutableStateOf(false) }
    val title = "Yesterday · ${state.yesterday.format(shortDay)}"

    when {
        analysis != null && analysis.isApproved -> {
            KohliCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow(title, modifier = Modifier.weight(1f))
                    StatusPill("${analysis.rating} · ${analysis.category.label}", KohliColors.tier(analysis.rating))
                    Spacer(Modifier.width(6.dp))
                    StatusPill("Accepted", KohliColors.Muted)
                }
                onOpenDetails?.let { GhostButton("Day details", onClick = it, color = KohliColors.Muted) }
            }
        }
        analysis != null && analysis.rejectionReason == null && !analyzing -> AccentBanner(accent = KohliColors.tier(analysis.rating)) {
            Eyebrow(title)
            Verdict(analysis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PrimaryButton("Accept", onClick = onAccept, fillWidth = false, modifier = Modifier.weight(1f))
                SecondaryButton("Doesn't look right", onClick = { showReject = true }, modifier = Modifier.weight(1f).height(52.dp))
            }
            onOpenDetails?.let { GhostButton("Day details", onClick = it, color = KohliColors.Muted) }
        }
        else -> PendingCard(
            title = title,
            rejectedReason = analysis?.rejectionReason,
            error = state.yesterdayError,
            analyzing = analyzing,
            onAnalyzeNow = onAnalyzeNow,
            onOpenAiSettings = onOpenAiSettings,
        )
    }

    if (showReject) {
        RejectDialog(
            onDismiss = { showReject = false },
            onConfirm = { comment ->
                showReject = false
                onReject(comment)
            },
        )
    }
}

/** Today's analysis status and the manual "Analyze Day" trigger. */
@Composable
fun TodayAnalysisCard(
    state: AnalysisUiState,
    hasLogs: Boolean,
    analyzing: Boolean,
    onAnalyze: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val analysis = state.todayAnalysis
    AccentBanner(accent = analysis?.let { KohliColors.tier(it.rating) } ?: KohliColors.OutlineStrong) {
        Eyebrow("Today's AI analysis")
        when {
            analyzing -> Analyzing()
            analysis != null -> {
                Verdict(analysis)
                MutedText("Final approval tomorrow morning.")
            }
            else -> {
                Text(
                    if (hasLogs) "Not analyzed yet. Runs automatically at 23:00." else "Log some food first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KohliColors.Muted,
                )
                state.todayError?.let { ErrorText(it) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(
                if (analysis == null) "Analyze Day" else "Re-analyze",
                onClick = onAnalyze,
                enabled = hasLogs && !analyzing,
            )
            Spacer(Modifier.width(8.dp))
            GhostButton("AI settings", onClick = onOpenAiSettings, color = KohliColors.Muted)
        }
    }
}

@Composable
private fun PendingCard(
    title: String,
    rejectedReason: String?,
    error: String?,
    analyzing: Boolean,
    onAnalyzeNow: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    AccentBanner(accent = KohliColors.Accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(title, modifier = Modifier.weight(1f))
            StatusPill("Analysis pending", KohliColors.Accent)
        }
        if (analyzing) {
            Analyzing()
        } else {
            rejectedReason?.let {
                Text(
                    "You disputed the last result: “$it”. It will be re-analyzed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            error?.let { ErrorText(it) }
            MutedText("Your food log is safely stored. AI failure is not eating failure.")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton("Analyze Now", onClick = onAnalyzeNow, enabled = !analyzing, fillWidth = false)
            Spacer(Modifier.width(8.dp))
            GhostButton("AI settings", onClick = onOpenAiSettings, color = KohliColors.Muted)
        }
    }
}

@Composable
private fun Verdict(analysis: DailyAnalysisEntity) {
    val tier = KohliColors.tier(analysis.rating)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Rating: ${analysis.rating} — ${analysis.category.label}",
            style = KohliType.Rating,
            color = tier,
        )
        Text(
            "Estimated calories: ${formatKcal(analysis.minCalories)} – ${formatKcal(analysis.maxCalories)}",
            style = MaterialTheme.typography.bodyLarge,
        )
        RatingMeter(analysis.rating)
        analysis.habitSummary?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = KohliColors.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        analysis.confidence?.let { MutedText("AI confidence: $it") }
    }
}

/** Five segments filled up to the rating, in the tier colour. */
@Composable
private fun RatingMeter(rating: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
        repeat(5) { index ->
            Box(
                Modifier
                    .width(28.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (index < rating) KohliColors.tier(rating) else KohliColors.Outline),
            )
        }
    }
}

@Composable
private fun Analyzing() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MutedText("Analyzing…")
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
            color = KohliColors.Accent,
            trackColor = KohliColors.Outline,
        )
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, style = MaterialTheme.typography.bodySmall, color = KohliColors.Missing)
}

@Composable
private fun RejectDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var comment by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KohliColors.SurfaceHigh,
        title = { Text("Doesn't look right?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MutedText("Yesterday's original log and photos will be sent to the AI again.")
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("What's off? (optional)") },
                    placeholder = { Text("e.g. the dal was a small bowl") },
                    minLines = 2,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(comment) }) { Text("Re-analyze", color = KohliColors.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = KohliColors.Muted) } },
    )
}

private fun formatKcal(value: Int) = String.format(Locale.US, "%,d", value)
