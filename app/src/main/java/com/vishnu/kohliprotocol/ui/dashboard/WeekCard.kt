package com.vishnu.kohliprotocol.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vishnu.kohliprotocol.ui.components.Eyebrow
import com.vishnu.kohliprotocol.ui.components.KohliCard
import com.vishnu.kohliprotocol.ui.components.MutedText
import com.vishnu.kohliprotocol.ui.components.StatusPill
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import com.vishnu.kohliprotocol.ui.theme.KohliType
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val weekDay = DateTimeFormatter.ofPattern("d MMM")

/** This week's running average and log completeness vs. the Biryani Parameter, plus game status. */
@Composable
fun WeekCard(state: WeekUiState, onClick: () -> Unit) {
    val progress = state.progress
    val average = progress.average
    val onTrack = average != null && average >= state.biryaniParameter
    val logsOnTrack = progress.completeDays == progress.daysElapsed
    val unlocked = state.games.isUnlocked()
    // On target reads green; below target takes the warm tier colour of the rounded average.
    val averageColor = when {
        average == null -> KohliColors.Muted
        onTrack -> KohliColors.Logged
        else -> KohliColors.tier(average.roundToInt().coerceIn(1, 3))
    }

    KohliCard(
        onClick = onClick,
        background = Brush.linearGradient(listOf(Color(0xFF7C5CFF).copy(alpha = 0.12f), Color.Transparent)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("This week · ${progress.start.format(weekDay)} – ${progress.end.format(weekDay)}", Modifier.weight(1f))
            StatusPill(if (unlocked) "🎮 Unlocked" else "🔒 Games locked", if (unlocked) KohliColors.Logged else KohliColors.Warning)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                average?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                style = KohliType.Metric,
                color = averageColor,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "avg of ${progress.ratedDays} rated day(s)\ntarget ${state.biryaniParameter}",
                style = MaterialTheme.typography.bodySmall,
                color = KohliColors.Muted,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        // One segment per day of the week: filled when that day's logs are complete.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(7) { index ->
                val color = when {
                    index >= progress.daysElapsed -> KohliColors.Outline.copy(alpha = 0.5f)
                    index < progress.completeDays -> KohliColors.Logged
                    else -> KohliColors.OutlineStrong
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color),
                )
            }
        }
        Text(
            "Logs complete: ${progress.completeDays} / ${progress.daysElapsed} day(s) so far",
            style = MaterialTheme.typography.bodySmall,
            color = if (logsOnTrack) KohliColors.Text else KohliColors.Missing,
        )
        if (!unlocked) MutedText(state.games.lockReason())
    }
}
