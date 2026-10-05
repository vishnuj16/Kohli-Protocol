package com.vishnu.kohliprotocol.weekly

import android.util.Log
import com.vishnu.kohliprotocol.data.local.entity.DailyAnalysisEntity
import com.vishnu.kohliprotocol.data.local.entity.MealType
import com.vishnu.kohliprotocol.data.local.entity.MealWithEntries
import com.vishnu.kohliprotocol.data.local.entity.WeeklyReportEntity
import com.vishnu.kohliprotocol.data.preferences.PreferencesManager
import com.vishnu.kohliprotocol.data.repository.EnforcementRepository
import com.vishnu.kohliprotocol.data.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Deterministic weekly result (spec §11) — the app decides, not the AI:
 * a week succeeds only if every day's four meal slots are logged or explicitly skipped AND the
 * average daily rating ≥ the Biryani Parameter.
 *
 * - Weeks are anchored to the protocol start date (the app's first launch): first launched on
 *   a Tuesday means weeks run Tuesday → Monday, and each result is ready the following
 *   Tuesday morning (once Monday's analysis is in). The first week begins on the start date.
 * - A week with complete logs but days still awaiting AI analysis is deferred, not failed —
 *   AI failure is not eating failure.
 * - A week with incomplete logs fails immediately.
 * - After a new result, game access follows the most recent week's result.
 */
class WeeklyEvaluationManager(
    private val food: FoodRepository,
    private val enforcement: EnforcementRepository,
    private val preferences: PreferencesManager,
    /** Called when new weekly results were recorded (queues report generation). */
    private val onResultsRecorded: () -> Unit = {},
) {
    sealed interface WeekOutcome {
        data class Recorded(val report: WeeklyReportEntity) : WeekOutcome
        data class Deferred(val weekStart: LocalDate, val reason: String) : WeekOutcome
    }

    private val mutex = Mutex()

    /** Null only before the first launch has recorded it. */
    val protocolStart: Flow<LocalDate?> = preferences.protocolStartDate

    /** Evaluates every finished, not-yet-evaluated week (at most the last [MAX_WEEKS_BACK]). */
    suspend fun evaluatePendingWeeks(today: LocalDate = LocalDate.now()): List<WeekOutcome> = mutex.withLock {
        val protocolStart = preferences.ensureProtocolStartDate(today)
        val lastFinished = weekStart(today, protocolStart).minusWeeks(1)
        // Both bounds fall on the anchor weekday, so stepping by whole weeks stays aligned.
        var week = maxOf(protocolStart, lastFinished.minusWeeks(MAX_WEEKS_BACK - 1))

        val outcomes = mutableListOf<WeekOutcome>()
        while (!week.isAfter(lastFinished)) {
            if (enforcement.getWeeklyReport(week) == null) outcomes += evaluateWeek(week)
            week = week.plusWeeks(1)
        }

        if (outcomes.any { it is WeekOutcome.Recorded }) {
            onResultsRecorded()
            val latest = enforcement.latestWeeklyReport()
            if (latest != null) {
                enforcement.setGamesUnlocked(
                    unlocked = latest.isSuccess,
                    reason = "Week ${latest.startDate}–${latest.endDate}: " +
                        if (latest.isSuccess) "SUCCESS" else "WEEK FAILED",
                )
            }
        }
        outcomes
    }

    private suspend fun evaluateWeek(start: LocalDate): WeekOutcome {
        val end = start.plusDays(6)
        val logsComplete = food.isRangeComplete(start, end)
        val ratings = usableRatings(food.getAnalyses(start, end))
        val average = if (ratings.isEmpty()) 0f else ratings.values.sum().toFloat() / ratings.size

        if (logsComplete && ratings.size < DAYS_PER_WEEK) {
            val missing = DAYS_PER_WEEK - ratings.size
            Log.i(TAG, "Week $start deferred: $missing day(s) awaiting AI analysis")
            return WeekOutcome.Deferred(start, "$missing day(s) still awaiting AI analysis")
        }
        val report = enforcement.recordWeeklyResult(start, end, average, logsComplete)
        Log.i(TAG, "Week $start evaluated: ${if (report.isSuccess) "SUCCESS" else "FAILED"}")
        return WeekOutcome.Recorded(report)
    }

    /** Live progress for the week containing [today], for the dashboard. */
    fun progress(
        today: LocalDate,
        protocolStart: LocalDate,
        meals: List<MealWithEntries>,
        analyses: List<DailyAnalysisEntity>,
    ): WeekProgress {
        val start = weekStart(today, protocolStart)
        val loggedSlots = meals.filter { it.isLogged }.groupBy { it.meal.date }
        val elapsed = (0L until DAYS_PER_WEEK).map { start.plusDays(it) }.filter { !it.isAfter(today) }
        val completeDays = elapsed.count { day ->
            loggedSlots[day].orEmpty().map { it.meal.mealType }.toSet().size == MealType.entries.size
        }
        val ratings = usableRatings(analyses)
        return WeekProgress(
            start = start,
            end = start.plusDays(6),
            daysElapsed = elapsed.size,
            completeDays = completeDays,
            ratedDays = ratings.size,
            average = if (ratings.isEmpty()) null else ratings.values.sum().toFloat() / ratings.size,
        )
    }

    /** Ratings that count: every analysis except one the user rejected (re-analysis pending). */
    private fun usableRatings(analyses: List<DailyAnalysisEntity>): Map<LocalDate, Int> =
        analyses.filter { it.rejectionReason == null }.associate { it.date to it.rating }

    companion object {
        private const val TAG = "KohliProtocol"
        const val DAYS_PER_WEEK = 7
        private const val MAX_WEEKS_BACK = 8L

        /** The start of the evaluation week containing [date]: the latest anchor weekday on or before it. */
        fun weekStart(date: LocalDate, protocolStart: LocalDate): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(protocolStart.dayOfWeek))
    }
}

data class WeekProgress(
    val start: LocalDate,
    val end: LocalDate,
    val daysElapsed: Int,
    val completeDays: Int,
    val ratedDays: Int,
    val average: Float?,
)
