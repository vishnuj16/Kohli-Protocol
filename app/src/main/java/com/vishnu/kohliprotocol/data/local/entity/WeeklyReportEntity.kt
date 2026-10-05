package com.vishnu.kohliprotocol.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A week's result. A week succeeds only if all required logs are complete AND the average
 * rating is at least the Biryani Parameter in force when the week was evaluated.
 */
@Entity(
    tableName = "weekly_reports",
    indices = [Index(value = ["startDate"], unique = true)],
)
data class WeeklyReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val averageRating: Float,
    val biryaniParameter: Float,
    val logsComplete: Boolean,
    val isSuccess: Boolean,
    val aiSummary: String? = null,
    val pdfPath: String? = null,
    val generatedAt: Long,
)
