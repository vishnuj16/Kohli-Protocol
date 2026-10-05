package com.vishnu.kohliprotocol.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * The AI's verdict on one day. Calories are always a range, never a falsely precise number.
 * Pending until the user accepts it; [rejectionReason] is set when they say it doesn't look right.
 */
@Entity(tableName = "daily_analyses")
data class DailyAnalysisEntity(
    @PrimaryKey val date: LocalDate,
    val minCalories: Int,
    val maxCalories: Int,
    val rating: Int,
    val category: DailyCategory,
    val isApproved: Boolean = false,
    val rejectionReason: String? = null,
    /** Optional confidence information returned by the AI. */
    val confidence: String? = null,
    /** Validated per-meal min/max calorie estimates, as JSON. */
    val mealEstimatesJson: String? = null,
    val analyzedAt: Long,
)
