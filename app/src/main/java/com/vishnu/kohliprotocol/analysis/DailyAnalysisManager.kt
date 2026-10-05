package com.vishnu.kohliprotocol.analysis

import android.util.Log
import com.vishnu.kohliprotocol.data.ai.AiException
import com.vishnu.kohliprotocol.data.ai.AiProviderFactory
import com.vishnu.kohliprotocol.data.ai.DailyAnalysisResult
import com.vishnu.kohliprotocol.data.ai.DayLog
import com.vishnu.kohliprotocol.data.local.entity.DailyAnalysisEntity
import com.vishnu.kohliprotocol.data.preferences.PreferencesManager
import com.vishnu.kohliprotocol.data.repository.FoodRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Sends a day's stored log to the selected AI provider and saves the validated result.
 * On any failure nothing is written: the day simply stays "Analysis Pending" and its food
 * log is untouched. AI failure is not eating failure.
 */
class DailyAnalysisManager(
    private val food: FoodRepository,
    private val providers: AiProviderFactory,
    private val preferences: PreferencesManager,
) {
    sealed interface Outcome {
        data class Success(val analysis: DailyAnalysisEntity) : Outcome
        data class Failed(val error: AiException) : Outcome
        object NothingLogged : Outcome
    }

    /** One analysis at a time, so a manual tap and the background job can't race. */
    private val mutex = Mutex()

    suspend fun analyze(date: LocalDate, feedback: String? = null): Outcome = mutex.withLock {
        val meals = food.getMeals(date)
        val extraFood = food.getArbitraryFood(date)
        if (meals.none { it.isLogged } && extraFood.isEmpty()) return@withLock Outcome.NothingLogged

        val previous = food.getAnalysis(date)
        // Re-analysis after "Doesn't look right" carries the complaint and the disputed result.
        val complaint = feedback ?: previous?.rejectionReason

        try {
            val provider = providers.create()
            food.recordAnalysisRequested(date, provider.type.label)
            val result = provider.analyzeDay(
                DayLog(
                    date = date,
                    meals = meals,
                    extraFood = extraFood,
                    userFeedback = complaint,
                    previousResult = if (complaint != null) previous?.toResult() else null,
                )
            )
            // The tier is the app's decision, not the AI's: calorie midpoint, capped when the
            // day's log is incomplete (a slot neither logged nor skipped).
            val logsComplete = food.isDayComplete(date)
            val rating = TierCalculator.rating(result.minCalories, result.maxCalories, result.rating, logsComplete)
            val saved = food.saveAnalysis(
                date = date,
                minCalories = result.minCalories,
                maxCalories = result.maxCalories,
                rating = rating,
                categoryLabel = TierCalculator.category(rating).label,
                confidence = result.confidence,
                mealEstimatesJson = result.mealEstimatesJson,
                habitSummary = result.habitSummary,
                aiRating = result.rating,
            )
            result.fallbackNote?.let { food.recordAnalysisFallback(date, it) }
            preferences.setLastAnalysisError(null)
            Outcome.Success(saved)
        } catch (e: AiException) {
            fail(date, e)
        } catch (e: IllegalArgumentException) {
            // The repository's own guard rejected the values.
            fail(date, AiException.InvalidResponse(e.message ?: "rejected by validation"))
        }
    }

    /** "Doesn't look right": records the rejection, then sends the original log again. */
    suspend fun rejectAndReanalyze(date: LocalDate, comment: String?): Outcome {
        val reason = comment?.trim()?.takeIf { it.isNotEmpty() } ?: "Doesn't look right"
        food.rejectAnalysis(date, reason)
        return analyze(date, reason)
    }

    /**
     * Days in the [lookbackDays] window ending at [through] that need (re-)analysis: food
     * logged but no analysis, a rejected analysis, or an unapproved analysis that predates
     * food logged later.
     */
    suspend fun pendingDates(through: LocalDate, lookbackDays: Long = 14): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var day = through.minusDays(lookbackDays - 1)
        while (!day.isAfter(through)) {
            val hasLogs = food.getMeals(day).any { it.isLogged } || food.getArbitraryFood(day).isNotEmpty()
            if (hasLogs) {
                val analysis = food.getAnalysis(day)
                val latestFood = food.latestFoodTimestamp(day)
                val pending = analysis == null ||
                    analysis.rejectionReason != null ||
                    (!analysis.isApproved && latestFood != null && latestFood > analysis.analyzedAt)
                if (pending) result += day
            }
            day = day.plusDays(1)
        }
        return result
    }

    /**
     * Re-applies [TierCalculator] to every unapproved, undisputed analysis in the window ending at
     * [through] — locally, from the stored calorie range, with no AI call. Picks up threshold
     * changes and days completed after they were analysed. Approved days are final.
     */
    suspend fun refreshTiers(through: LocalDate = LocalDate.now(), lookbackDays: Long = 14) = mutex.withLock {
        food.getAnalyses(through.minusDays(lookbackDays - 1), through)
            .filter { !it.isApproved && it.rejectionReason == null }
            .forEach { analysis ->
                val complete = food.isDayComplete(analysis.date)
                val rating = TierCalculator.rating(analysis.minCalories, analysis.maxCalories, analysis.aiRating, complete)
                food.updateAnalysisTier(
                    analysis.date,
                    rating,
                    "midpoint ${TierCalculator.midpoint(analysis.minCalories, analysis.maxCalories)} kcal, " +
                        (analysis.aiRating?.let { "AI rating $it, " } ?: "") +
                        if (complete) "logs complete" else "logs incomplete (capped at ${TierCalculator.INCOMPLETE_CAP})",
                )
            }
    }

    private suspend fun fail(date: LocalDate, error: AiException): Outcome.Failed {
        Log.w(TAG, "Analysis of $date failed: ${error.message}")
        preferences.setLastAnalysisError("$date$ERROR_SEPARATOR${error.message}")
        return Outcome.Failed(error)
    }

    private fun DailyAnalysisEntity.toResult() = DailyAnalysisResult(
        minCalories = minCalories,
        maxCalories = maxCalories,
        rating = rating,
        category = category,
        confidence = confidence,
        mealEstimatesJson = mealEstimatesJson,
        habitSummary = habitSummary,
    )

    companion object {
        private const val TAG = "KohliProtocol"
        private const val ERROR_SEPARATOR = "|"

        /** Splits the stored last error into the day it applies to and its message. */
        fun parseLastError(stored: String?): Pair<LocalDate, String>? {
            val (date, message) = stored?.split(ERROR_SEPARATOR, limit = 2)?.takeIf { it.size == 2 } ?: return null
            return runCatching { LocalDate.parse(date) to message }.getOrNull()
        }
    }
}
