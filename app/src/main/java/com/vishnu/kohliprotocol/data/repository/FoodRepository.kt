package com.vishnu.kohliprotocol.data.repository

import androidx.room.withTransaction
import com.vishnu.kohliprotocol.data.local.KohliDatabase
import com.vishnu.kohliprotocol.data.local.entity.AuditAction
import com.vishnu.kohliprotocol.data.local.entity.DailyAnalysisEntity
import com.vishnu.kohliprotocol.data.local.entity.DailyCategory
import com.vishnu.kohliprotocol.data.local.entity.FoodEntryEntity
import com.vishnu.kohliprotocol.data.local.entity.MealEntity
import com.vishnu.kohliprotocol.data.local.entity.MealType
import com.vishnu.kohliprotocol.data.local.entity.MealWithEntries
import com.vishnu.kohliprotocol.data.storage.InternalStorageManager
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Food logging, meal slots, completeness and daily AI analyses. */
class FoodRepository(
    private val database: KohliDatabase,
    private val storage: InternalStorageManager,
    private val audit: AuditRepository,
) {
    private val mealDao = database.mealDao()
    private val foodEntryDao = database.foodEntryDao()
    private val analysisDao = database.dailyAnalysisDao()

    // --- Observation -------------------------------------------------------------------------

    /** The meal slots that exist for [date] (a slot row is created on first log or skip). */
    fun observeMeals(date: LocalDate): Flow<List<MealWithEntries>> = mealDao.observeDay(date)

    fun observeArbitraryFood(date: LocalDate): Flow<List<FoodEntryEntity>> =
        foodEntryDao.observeArbitrary(date)

    fun observeAllFood(date: LocalDate): Flow<List<FoodEntryEntity>> = foodEntryDao.observeForDate(date)

    suspend fun getMeals(date: LocalDate): List<MealWithEntries> = mealDao.getRange(date, date)

    fun observeMealsRange(start: LocalDate, end: LocalDate): Flow<List<MealWithEntries>> =
        mealDao.observeRange(start, end)

    suspend fun getArbitraryFood(date: LocalDate): List<FoodEntryEntity> =
        foodEntryDao.getForDate(date).filter { it.isArbitrary }

    /** Creation time of the newest food entry on [date], or null if none. */
    suspend fun latestFoodTimestamp(date: LocalDate): Long? =
        foodEntryDao.getForDate(date).maxOfOrNull { it.timestamp }

    // --- Logging -----------------------------------------------------------------------------

    /** Logs food into a meal slot. Logging food into a skipped slot un-skips it. */
    suspend fun logMealFood(
        date: LocalDate,
        mealType: MealType,
        description: String,
        photoPath: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ): Long {
        val text = requireDescription(description)
        return database.withTransaction {
            val meal = ensureMeal(date, mealType, timestamp)
            if (meal.isSkipped) mealDao.update(meal.copy(isSkipped = false, timestamp = timestamp))
            val id = foodEntryDao.insert(
                FoodEntryEntity(
                    mealId = meal.id,
                    date = date,
                    description = text,
                    photoPath = photoPath,
                    timestamp = timestamp,
                    isArbitrary = false,
                )
            )
            audit.log(AuditAction.FOOD_LOGGED, "${mealType.label} ($date): $text")
            id
        }
    }

    /**
     * "+ Add Food": arbitrary food outside the meal slots. It belongs to the calendar day of
     * [timestamp], so food eaten after midnight counts toward the new day.
     */
    suspend fun addArbitraryFood(
        description: String,
        photoPath: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ): Long {
        val text = requireDescription(description)
        val date = dateOf(timestamp)
        return database.withTransaction {
            val id = foodEntryDao.insert(
                FoodEntryEntity(
                    mealId = null,
                    date = date,
                    description = text,
                    photoPath = photoPath,
                    timestamp = timestamp,
                    isArbitrary = true,
                )
            )
            audit.log(AuditAction.FOOD_LOGGED, "+ Add Food ($date): $text")
            id
        }
    }

    /**
     * Explicitly marks a slot as skipped, which counts as logged. Refused while the slot still
     * has food in it — "skipped" and "ate something" cannot both be true.
     */
    suspend fun markSkipped(
        date: LocalDate,
        mealType: MealType,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        database.withTransaction {
            val meal = ensureMeal(date, mealType, timestamp)
            check(foodEntryDao.countForMeal(meal.id) == 0) {
                "${mealType.label} on $date has food logged; remove it before marking skipped"
            }
            if (!meal.isSkipped) {
                mealDao.update(meal.copy(isSkipped = true, timestamp = timestamp))
                audit.log(AuditAction.MEAL_SKIPPED, "${mealType.label} ($date) marked skipped")
            }
        }
    }

    /** Edits an entry's text and/or photo. A replaced photo file is deleted. */
    suspend fun editFoodEntry(id: Long, description: String, photoPath: String?) {
        val text = requireDescription(description)
        val oldPhoto = database.withTransaction {
            val entry = foodEntryDao.get(id) ?: throw NoSuchElementException("No food entry $id")
            foodEntryDao.update(entry.copy(description = text, photoPath = photoPath))
            audit.log(
                AuditAction.FOOD_EDITED,
                "Entry $id (${entry.date}): \"${entry.description}\" → \"$text\"",
            )
            entry.photoPath
        }
        if (oldPhoto != null && oldPhoto != photoPath) storage.delete(oldPhoto)
    }

    suspend fun deleteFoodEntry(id: Long) {
        val entry = database.withTransaction {
            val entry = foodEntryDao.get(id) ?: throw NoSuchElementException("No food entry $id")
            foodEntryDao.delete(entry)
            audit.log(AuditAction.FOOD_DELETED, "Entry $id (${entry.date}): ${entry.description}")
            entry
        }
        entry.photoPath?.let { storage.delete(it) }
    }

    // --- Completeness ------------------------------------------------------------------------

    /** True when every one of the four meal slots is logged or explicitly skipped. */
    suspend fun isDayComplete(date: LocalDate): Boolean =
        mealDao.countLoggedSlots(date) == MealType.entries.size

    /** Slots on [date] that are neither logged nor explicitly skipped. */
    suspend fun missingSlots(date: LocalDate): List<MealType> {
        val logged = mealDao.getRange(date, date).filter { it.isLogged }.map { it.meal.mealType }.toSet()
        return MealType.entries.filterNot { it in logged }
    }

    /** True when every day from [start] to [end] (inclusive) is complete. */
    suspend fun isRangeComplete(start: LocalDate, end: LocalDate): Boolean {
        var day = start
        while (!day.isAfter(end)) {
            if (!isDayComplete(day)) return false
            day = day.plusDays(1)
        }
        return true
    }

    // --- Daily AI analysis -------------------------------------------------------------------

    fun observeAnalysis(date: LocalDate): Flow<DailyAnalysisEntity?> = analysisDao.observe(date)

    suspend fun getAnalysis(date: LocalDate): DailyAnalysisEntity? = analysisDao.get(date)

    fun observeAnalyses(start: LocalDate, end: LocalDate): Flow<List<DailyAnalysisEntity>> =
        analysisDao.observeRange(start, end)

    suspend fun getAnalyses(start: LocalDate, end: LocalDate): List<DailyAnalysisEntity> =
        analysisDao.getRange(start, end)

    suspend fun recordAnalysisRequested(date: LocalDate, provider: String) {
        audit.log(AuditAction.AI_ANALYSIS_REQUESTED, "Analysis requested for $date", metadata = provider)
    }

    suspend fun recordAnalysisFallback(date: LocalDate, detail: String) {
        audit.log(
            AuditAction.AI_FALLBACK_USED,
            "AI request succeeded using fallback model/key for $date",
            metadata = detail,
        )
    }

    /**
     * Final guard before an AI result reaches the database. Throws [IllegalArgumentException]
     * if the values are out of range or the category contradicts the rating. Saving over an
     * existing analysis counts as a regeneration and resets its approval.
     */
    suspend fun saveAnalysis(
        date: LocalDate,
        minCalories: Int,
        maxCalories: Int,
        rating: Int,
        categoryLabel: String?,
        confidence: String? = null,
        mealEstimatesJson: String? = null,
        habitSummary: String? = null,
        aiRating: Int? = null,
    ): DailyAnalysisEntity {
        require(minCalories in 0..MAX_DAILY_CALORIES) { "minCalories out of range: $minCalories" }
        require(maxCalories in minCalories..MAX_DAILY_CALORIES) {
            "maxCalories must be between minCalories and $MAX_DAILY_CALORIES: $maxCalories"
        }
        val category = requireNotNull(DailyCategory.fromRating(rating)) { "rating must be 1–5: $rating" }
        if (categoryLabel != null) {
            require(DailyCategory.fromLabel(categoryLabel) == category) {
                "Category \"$categoryLabel\" does not match rating $rating (${category.label})"
            }
        }

        val analysis = DailyAnalysisEntity(
            date = date,
            minCalories = minCalories,
            maxCalories = maxCalories,
            rating = rating,
            category = category,
            confidence = confidence,
            mealEstimatesJson = mealEstimatesJson,
            habitSummary = habitSummary,
            aiRating = aiRating,
            analyzedAt = System.currentTimeMillis(),
        )
        database.withTransaction {
            val regenerated = analysisDao.get(date) != null
            analysisDao.upsert(analysis)
            audit.log(
                if (regenerated) AuditAction.AI_ANALYSIS_REGENERATED else AuditAction.AI_ANALYSIS_RECEIVED,
                "$date: $rating/5 ${category.label}, $minCalories–$maxCalories kcal",
            )
        }
        return analysis
    }

    /**
     * Re-bands a stored analysis to [rating] (from its stored calorie range) without calling the
     * AI. Returns true if the rating changed.
     */
    suspend fun updateAnalysisTier(date: LocalDate, rating: Int, reason: String): Boolean = database.withTransaction {
        val analysis = analysisDao.get(date) ?: return@withTransaction false
        if (analysis.rating == rating) return@withTransaction false
        val category = requireNotNull(DailyCategory.fromRating(rating)) { "rating must be 1–5: $rating" }
        analysisDao.upsert(analysis.copy(rating = rating, category = category))
        audit.log(
            AuditAction.DAILY_TIER_RECALCULATED,
            "$date: ${analysis.rating} (${analysis.category.label}) → $rating (${category.label}) — $reason",
        )
        true
    }

    /** Returns false if there is no analysis for [date]. */
    suspend fun acceptAnalysis(date: LocalDate): Boolean = database.withTransaction {
        val updated = analysisDao.setApproval(date, approved = true, reason = null) > 0
        if (updated) audit.log(AuditAction.AI_ANALYSIS_ACCEPTED, "Analysis for $date accepted")
        updated
    }

    /** "Doesn't look right". Returns false if there is no analysis for [date]. */
    suspend fun rejectAnalysis(date: LocalDate, reason: String): Boolean = database.withTransaction {
        val updated = analysisDao.setApproval(date, approved = false, reason = reason.trim()) > 0
        if (updated) audit.log(AuditAction.AI_ANALYSIS_REJECTED, "Analysis for $date rejected: $reason")
        updated
    }

    // --- Helpers -----------------------------------------------------------------------------

    private suspend fun ensureMeal(date: LocalDate, mealType: MealType, timestamp: Long): MealEntity {
        mealDao.get(date, mealType)?.let { return it }
        val id = mealDao.insert(MealEntity(date = date, mealType = mealType, timestamp = timestamp))
        return MealEntity(id = id, date = date, mealType = mealType, timestamp = timestamp)
    }

    private fun requireDescription(description: String): String =
        description.trim().also { require(it.isNotEmpty()) { "Describe what you ate" } }

    private fun dateOf(timestamp: Long): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

    companion object {
        /** Sanity ceiling for a single day's estimate; anything above is a malformed AI result. */
        private const val MAX_DAILY_CALORIES = 20_000
    }
}
