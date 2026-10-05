package com.vishnu.kohliprotocol.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import com.vishnu.kohliprotocol.R
import com.vishnu.kohliprotocol.data.local.entity.MealType
import com.vishnu.kohliprotocol.ui.components.GoldGradient
import java.time.LocalTime
import com.vishnu.kohliprotocol.ui.components.CardShape
import com.vishnu.kohliprotocol.ui.components.MutedText
import com.vishnu.kohliprotocol.ui.components.PhotoThumbnail
import com.vishnu.kohliprotocol.ui.components.SectionLabel
import com.vishnu.kohliprotocol.ui.components.StatusPill
import com.vishnu.kohliprotocol.ui.components.bounceClick
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import com.vishnu.kohliprotocol.ui.theme.KohliType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** The Dashboard tab: today's meals, yesterday's approval, this week and today's AI analysis. */
@Composable
fun DashboardTab(
    viewModel: DashboardViewModel,
    contentPadding: PaddingValues,
    onOpenAiSettings: () -> Unit,
    onOpenDiscipline: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val week by viewModel.weekState.collectAsStateWithLifecycle()
    val analysis by viewModel.analysisState.collectAsStateWithLifecycle()
    val analyzing by viewModel.analyzing.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()

    val current = state
    if (current == null) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KohliColors.Accent)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeroCard(current) }
            item {
                analysis?.let { a ->
                    YesterdayCard(
                        state = a,
                        analyzing = a.yesterday in analyzing,
                        onAccept = { viewModel.acceptAnalysis(a.yesterday) },
                        onReject = { comment -> viewModel.rejectAnalysis(a.yesterday, comment) },
                        onAnalyzeNow = { viewModel.analyzeNow(a.yesterday) },
                        onOpenAiSettings = onOpenAiSettings,
                    )
                }
            }
            item { week?.let { WeekCard(it, onClick = onOpenDiscipline) } }
            item { SectionLabel("Meals") }
            items(current.slots, key = { it.mealType.name }) { slot ->
                SlotCard(slot, onClick = { viewModel.openSlot(slot) })
            }
            item { AddFoodButton(onClick = viewModel::openAddFood) }
            item {
                analysis?.let { a ->
                    TodayAnalysisCard(
                        state = a,
                        hasLogs = current.loggedCount > 0 || current.feed.isNotEmpty(),
                        analyzing = a.today in analyzing,
                        onAnalyze = { viewModel.analyzeNow(a.today) },
                        onOpenAiSettings = onOpenAiSettings,
                    )
                }
            }
            item { SectionLabel("Today's feed") }
            if (current.feed.isEmpty()) {
                item { EmptyFeed() }
            } else {
                items(current.feed, key = { it.entry.id }) { item ->
                    FeedRow(item, onClick = { viewModel.openEntry(item) })
                }
            }
        }
    }

    editor?.let { FoodEditorSheet(state = it, viewModel = viewModel) }
}

/** Gradient hero: greeting, date, meals ring and the bat-and-ball illustration. */
@Composable
private fun HeroCard(state: DashboardState) {
    val total = MealType.entries.size
    val complete = state.loggedCount == total
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late night"
    }
    val shape = RoundedCornerShape(24.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF3D2A0B), Color(0xFF261B30), KohliColors.Surface),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            )
            .border(1.dp, Brush.linearGradient(listOf(KohliColors.Accent.copy(alpha = 0.55f), KohliColors.Outline)), shape),
    ) {
        Image(
            painterResource(R.drawable.ill_bat_ball),
            contentDescription = null,
            modifier = Modifier
                .size(132.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 14.dp),
        )
        Column(Modifier.fillMaxWidth(0.7f).padding(20.dp)) {
            Text("$greeting, Vishnu".uppercase(), style = KohliType.Eyebrow, color = KohliColors.Accent)
            Spacer(Modifier.height(2.dp))
            Text(
                state.date.format(dayFormatter),
                style = MaterialTheme.typography.headlineLarge,
                color = KohliColors.Text,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MealsRing(done = state.loggedCount, total = total)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "${state.loggedCount} of $total meals",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (complete) KohliColors.Logged else KohliColors.Text,
                    )
                    Text(
                        if (complete) "Day complete — nicely done." else "Log each meal or mark it skipped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KohliColors.Muted,
                    )
                }
            }
        }
    }
}

/** One arc segment per meal slot, filled green as slots are logged. */
@Composable
private fun MealsRing(done: Int, total: Int) {
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val gap = 14f
            val sweep = 360f / total
            repeat(total) { index ->
                drawArc(
                    color = if (index < done) KohliColors.Logged else Color.White.copy(alpha = 0.12f),
                    startAngle = -90f + index * sweep + gap / 2,
                    sweepAngle = sweep - gap,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text("$done/$total", style = KohliType.Metric.copy(fontSize = 18.sp, lineHeight = 18.sp), color = KohliColors.Text)
    }
}

/** A time-of-day illustration for each meal slot, on a tinted gradient tile. */
@Composable
private fun MealArt(type: MealType) {
    val (art, tint) = when (type) {
        MealType.BREAKFAST -> R.drawable.ill_sunrise to listOf(Color(0xFF3F2C0E), Color(0xFF241C12))
        MealType.LUNCH -> R.drawable.ill_sun to listOf(Color(0xFF40260E), Color(0xFF26190F))
        MealType.EVENING -> R.drawable.ill_sunset to listOf(Color(0xFF3F1A2E), Color(0xFF251628))
        MealType.DINNER -> R.drawable.ill_moon to listOf(Color(0xFF212648), Color(0xFF171A2E))
    }
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(tint)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(art), contentDescription = null, modifier = Modifier.size(34.dp))
    }
}

/** The main call to action: a gold-gradient button with a warm glow. */
@Composable
private fun AddFoodButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .height(56.dp)
            .bounceClick()
            .shadow(elevation = 12.dp, shape = shape, ambientColor = KohliColors.Accent, spotColor = KohliColors.Accent)
            .clip(shape)
            .background(GoldGradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = KohliColors.OnAccent)
            Spacer(Modifier.width(8.dp))
            Text("ADD FOOD", style = KohliType.Brand.copy(fontSize = 22.sp, letterSpacing = 2.sp), color = KohliColors.OnAccent)
        }
    }
}

@Composable
private fun EmptyFeed() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(painterResource(R.drawable.ill_empty_plate), contentDescription = null, modifier = Modifier.size(96.dp))
        Text("Nothing logged yet today", style = MaterialTheme.typography.titleSmall)
        MutedText("Your plate is clean — for now. Tap a meal above to log it.")
    }
}

@Composable
private fun SlotCard(slot: SlotState, onClick: () -> Unit) {
    val (pillText, pillColor, statusText) = when (slot.status) {
        SlotStatus.EMPTY -> Triple("Missing", KohliColors.Muted, "Not logged — tap to log")
        SlotStatus.LOGGED -> Triple("Logged", KohliColors.Logged, slot.entries.joinToString(" · ") { it.description })
        SlotStatus.SKIPPED -> Triple("Skipped", KohliColors.Skipped, "Skipped")
    }
    val thumbnail = slot.entries.firstNotNullOfOrNull { it.photoPath }

    Surface(
        shape = CardShape,
        color = KohliColors.Surface,
        border = BorderStroke(1.dp, KohliColors.Outline),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (slot.status == SlotStatus.SKIPPED) 0.6f else 1f)
            .bounceClick()
            .clip(CardShape)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.height(84.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(10.dp))
            SlotIndicator(slot.status)
            Spacer(Modifier.width(12.dp))
            MealArt(slot.mealType)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        slot.mealType.label.uppercase(),
                        style = KohliType.Eyebrow,
                        color = if (slot.status == SlotStatus.SKIPPED) KohliColors.Muted else KohliColors.Text,
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill(pillText, pillColor)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (slot.status == SlotStatus.LOGGED) KohliColors.Text else KohliColors.Muted,
                    fontStyle = if (slot.status == SlotStatus.LOGGED) FontStyle.Normal else FontStyle.Italic,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                thumbnail != null -> PhotoThumbnail(thumbnail, size = 60.dp, modifier = Modifier.padding(end = 12.dp))
                slot.status == SlotStatus.EMPTY -> Box(
                    Modifier
                        .padding(end = 16.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(50))
                        .background(KohliColors.SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = KohliColors.Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** 6dp vertical pill: dashed when missing, solid emerald when logged, grey when skipped. */
@Composable
private fun SlotIndicator(status: SlotStatus) {
    val modifier = Modifier.width(6.dp).fillMaxHeight().padding(vertical = 14.dp)
    when (status) {
        SlotStatus.LOGGED -> Box(modifier.clip(RoundedCornerShape(50)).background(KohliColors.Logged))
        SlotStatus.SKIPPED -> Box(modifier.clip(RoundedCornerShape(50)).background(KohliColors.Skipped))
        SlotStatus.EMPTY -> Canvas(modifier) {
            drawRoundRect(
                color = KohliColors.OutlineStrong,
                cornerRadius = CornerRadius(size.width / 2, size.width / 2),
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                ),
            )
        }
    }
}

@Composable
private fun FeedRow(item: FeedItem, onClick: () -> Unit) {
    val time = Instant.ofEpochMilli(item.entry.timestamp).atZone(ZoneId.systemDefault()).format(timeFormatter)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = KohliColors.Surface,
        border = BorderStroke(1.dp, KohliColors.Outline),
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                time,
                style = MaterialTheme.typography.labelLarge,
                color = KohliColors.Accent,
                modifier = Modifier.width(54.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(item.label.uppercase(), style = KohliType.Pill, color = KohliColors.Muted)
                Spacer(Modifier.height(2.dp))
                Text(
                    item.entry.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.entry.photoPath?.let { PhotoThumbnail(it, size = 48.dp, modifier = Modifier.padding(start = 10.dp)) }
        }
    }
}
