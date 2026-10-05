package com.vishnu.kohliprotocol.ui.review

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.analysis.TierCalculator
import com.vishnu.kohliprotocol.data.local.entity.DailyAnalysisEntity
import com.vishnu.kohliprotocol.data.local.entity.FoodEntryEntity
import com.vishnu.kohliprotocol.data.local.entity.MealType
import com.vishnu.kohliprotocol.data.repository.FoodRepository
import com.vishnu.kohliprotocol.security.SecureActivity
import com.vishnu.kohliprotocol.ui.components.AccentBanner
import com.vishnu.kohliprotocol.ui.components.Eyebrow
import com.vishnu.kohliprotocol.ui.components.KohliCard
import com.vishnu.kohliprotocol.ui.components.MutedText
import com.vishnu.kohliprotocol.ui.components.PageColumn
import com.vishnu.kohliprotocol.ui.components.PageScaffold
import com.vishnu.kohliprotocol.ui.components.PhotoThumbnail
import com.vishnu.kohliprotocol.ui.components.SectionLabel
import com.vishnu.kohliprotocol.ui.components.StatusPill
import com.vishnu.kohliprotocol.ui.components.bounceClick
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import com.vishnu.kohliprotocol.ui.theme.KohliType
import com.vishnu.kohliprotocol.weekly.WeeklyEvaluationManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Read-only review of any day of the current week: meals, calories, tier and the AI review. */
class DailyReviewActivity : SecureActivity() {

    override val screenName = "Daily review"

    private val viewModel: DailyReviewViewModel by viewModels {
        DailyReviewViewModel.factory(intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSecureContent { DailyReviewScreen(viewModel, onBack = ::finish) }
    }

    companion object {
        private const val EXTRA_DATE = "review_date"

        /** Opens the review on [date] (default: today). */
        fun intent(context: Context, date: LocalDate? = null): Intent =
            Intent(context, DailyReviewActivity::class.java).apply { date?.let { putExtra(EXTRA_DATE, it.toString()) } }
    }
}

data class ReviewSlot(val type: MealType, val skipped: Boolean, val entries: List<FoodEntryEntity>)

data class DayReview(
    val date: LocalDate,
    val slots: List<ReviewSlot>,
    val extras: List<FoodEntryEntity>,
    val analysis: DailyAnalysisEntity?,
    val complete: Boolean,
)

data class WeekStrip(val days: List<LocalDate>, val today: LocalDate, val ratings: Map<LocalDate, Int>)

@OptIn(ExperimentalCoroutinesApi::class)
class DailyReviewViewModel(
    private val food: FoodRepository,
    weekly: WeeklyEvaluationManager,
    initialDate: LocalDate?,
) : ViewModel() {

    private val today = LocalDate.now()
    private val selected = MutableStateFlow(initialDate?.takeIf { !it.isAfter(today) } ?: today)
    val selectedDate: StateFlow<LocalDate> = selected.asStateFlow()

    /** The evaluation week containing the selected day (anchored to the protocol start weekday). */
    val week: StateFlow<WeekStrip?> = combine(weekly.protocolStart, selected) { start, day -> start to day }
        .flatMapLatest { (start, day) ->
            val weekStart = WeeklyEvaluationManager.weekStart(day, start ?: today)
            val days = (0L..6L).map { weekStart.plusDays(it) }
            food.observeAnalyses(weekStart, weekStart.plusDays(6)).map { analyses ->
                WeekStrip(days, today, analyses.associate { it.date to it.rating })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val day: StateFlow<DayReview?> = selected
        .flatMapLatest { date ->
            combine(food.observeMeals(date), food.observeAllFood(date), food.observeAnalysis(date)) { meals, entries, analysis ->
                val byType = meals.associateBy { it.meal.mealType }
                val slots = MealType.entries.map { type ->
                    val meal = byType[type]
                    ReviewSlot(type, skipped = meal?.meal?.isSkipped == true, entries = meal?.entries.orEmpty().sortedBy { it.timestamp })
                }
                DayReview(
                    date = date,
                    slots = slots,
                    extras = entries.filter { it.isArbitrary },
                    analysis = analysis,
                    complete = slots.all { it.skipped || it.entries.isNotEmpty() },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(date: LocalDate) {
        if (!date.isAfter(today)) selected.value = date
    }

    companion object {
        fun factory(initialDate: LocalDate?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as KohliApplication).container
                DailyReviewViewModel(container.foodRepository, container.weeklyEvaluationManager, initialDate)
            }
        }
    }
}

private val dayTitle = DateTimeFormatter.ofPattern("EEEE, d MMM")
private val chipDay = DateTimeFormatter.ofPattern("EEE")
private val time = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun DailyReviewScreen(viewModel: DailyReviewViewModel, onBack: () -> Unit) {
    val week by viewModel.week.collectAsStateWithLifecycle()
    val selected by viewModel.selectedDate.collectAsStateWithLifecycle()
    val day by viewModel.day.collectAsStateWithLifecycle()

    PageScaffold(title = "Daily review", onBack = onBack) { padding ->
        PageColumn(padding) {
            week?.let { DayStrip(it, selected, onSelect = viewModel::select) }
            Text(selected.format(dayTitle), style = MaterialTheme.typography.headlineLarge)
            day?.let { review ->
                VerdictCard(review)
                SectionLabel("Meals")
                review.slots.forEach { SlotCard(it) }
                SectionLabel("Extra food")
                if (review.extras.isEmpty()) {
                    MutedText("Nothing outside the meal slots.", Modifier.padding(horizontal = 4.dp))
                } else {
                    KohliCard { review.extras.forEach { EntryRow(it) } }
                }
            }
        }
    }
}

/** Seven day chips for the week; rated days show a dot in their tier colour. */
@Composable
private fun DayStrip(week: WeekStrip, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        week.days.forEach { date ->
            val isSelected = date == selected
            val future = date.isAfter(week.today)
            val rating = week.ratings[date]
            val shape = RoundedCornerShape(14.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (future) 0.35f else 1f)
                    .then(if (future) Modifier else Modifier.bounceClick())
                    .clip(shape)
                    .background(if (isSelected) KohliColors.Accent else KohliColors.Surface)
                    .border(1.dp, if (isSelected) KohliColors.Accent else KohliColors.Outline, shape)
                    .clickable(enabled = !future) { onSelect(date) }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    date.format(chipDay).uppercase(Locale.getDefault()),
                    style = KohliType.Pill,
                    color = if (isSelected) KohliColors.OnAccent else KohliColors.Muted,
                )
                Text(
                    date.dayOfMonth.toString(),
                    style = KohliType.Metric.copy(fontSize = 22.sp, lineHeight = 24.sp),
                    color = if (isSelected) KohliColors.OnAccent else KohliColors.Text,
                )
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                rating == null -> Color.Transparent
                                isSelected -> KohliColors.OnAccent
                                else -> KohliColors.tier(rating)
                            }
                        ),
                )
            }
        }
    }
}

@Composable
private fun VerdictCard(review: DayReview) {
    val analysis = review.analysis
    if (analysis == null) {
        KohliCard {
            Eyebrow("AI review")
            Text("Not analyzed yet", style = MaterialTheme.typography.titleMedium)
            MutedText(
                if (review.slots.any { it.skipped || it.entries.isNotEmpty() } || review.extras.isNotEmpty()) {
                    "Analysis runs automatically at 23:00, or tap Analyze on the dashboard."
                } else {
                    "Nothing was logged on this day."
                }
            )
        }
        return
    }

    val tier = KohliColors.tier(analysis.rating)
    val midpoint = TierCalculator.midpoint(analysis.minCalories, analysis.maxCalories)
    AccentBanner(accent = tier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(tier.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(analysis.rating.toString(), style = KohliType.Metric, color = tier)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(analysis.category.label, style = MaterialTheme.typography.titleLarge, color = tier)
                Text(
                    "${kcal(analysis.minCalories)} – ${kcal(analysis.maxCalories)} kcal",
                    style = MaterialTheme.typography.bodyLarge,
                )
                MutedText("Midpoint ${kcal(midpoint)} kcal")
                val calorieTier = TierCalculator.ratingForMidpoint(midpoint)
                MutedText(
                    analysis.aiRating?.let { "Calorie tier $calorieTier · AI rating $it → ${analysis.rating}" }
                        ?: "Calorie tier $calorieTier"
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                when {
                    analysis.rejectionReason != null -> "Disputed"
                    analysis.isApproved -> "Accepted"
                    else -> "Awaiting approval"
                },
                if (analysis.isApproved) KohliColors.Logged else KohliColors.Accent,
            )
            StatusPill(
                if (review.complete) "Logs complete" else "Incomplete — max ${TierCalculator.INCOMPLETE_CAP}",
                if (review.complete) KohliColors.Logged else KohliColors.Warning,
            )
        }
        Eyebrow("AI review")
        Text(
            analysis.habitSummary ?: "No written review for this day.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (analysis.habitSummary != null) KohliColors.Text else KohliColors.Muted,
        )
        mealEstimates(analysis.mealEstimatesJson).takeIf { it.isNotEmpty() }?.let { estimates ->
            Eyebrow("Per-meal estimate")
            estimates.forEach { (meal, range) ->
                Row {
                    Text(meal, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(range, style = MaterialTheme.typography.bodySmall, color = KohliColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun SlotCard(slot: ReviewSlot) {
    val (pill, color) = when {
        slot.skipped -> "Skipped" to KohliColors.Skipped
        slot.entries.isNotEmpty() -> "Logged" to KohliColors.Logged
        else -> "Not logged" to KohliColors.Missing
    }
    KohliCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(slot.type.label, Modifier.weight(1f), color = KohliColors.Text)
            StatusPill(pill, color)
        }
        if (slot.entries.isEmpty()) {
            MutedText(if (slot.skipped) "Deliberately skipped." else "Nothing was logged for this slot.")
        } else {
            slot.entries.forEach { EntryRow(it) }
        }
    }
}

@Composable
private fun EntryRow(entry: FoodEntryEntity) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).format(time),
            style = MaterialTheme.typography.labelLarge,
            color = KohliColors.Accent,
            modifier = Modifier.width(52.dp),
        )
        Text(entry.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        entry.photoPath?.let { PhotoThumbnail(it, size = 56.dp, modifier = Modifier.padding(start = 10.dp)) }
    }
    Spacer(Modifier.height(2.dp))
}

private fun kcal(value: Int) = String.format(Locale.US, "%,d", value)

/** Validated per-meal estimates stored by the analysis, as display rows. */
private fun mealEstimates(json: String?): List<Pair<String, String>> = runCatching {
    val array = JSONArray(json ?: return emptyList())
    (0 until array.length()).map { i ->
        val item = array.getJSONObject(i)
        item.getString("meal") to "${kcal(item.getInt("minimum_calories"))} – ${kcal(item.getInt("maximum_calories"))} kcal"
    }
}.getOrDefault(emptyList())
