package com.vishnu.kohliprotocol.data.local

import androidx.room.TypeConverter
import com.vishnu.kohliprotocol.data.local.entity.AuditAction
import com.vishnu.kohliprotocol.data.local.entity.DailyCategory
import com.vishnu.kohliprotocol.data.local.entity.MealType
import java.time.LocalDate

/**
 * Dates are stored as ISO `YYYY-MM-DD` text, so string comparison (and BETWEEN) matches
 * chronological order. Timestamps stay as epoch-millis Longs and need no conversion.
 */
class Converters {

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromMealType(type: MealType?): String? = type?.name

    @TypeConverter
    fun toMealType(value: String?): MealType? = value?.let(MealType::valueOf)

    /** Stored as the human-readable label, e.g. "Fair play". */
    @TypeConverter
    fun fromDailyCategory(category: DailyCategory?): String? = category?.label

    @TypeConverter
    fun toDailyCategory(value: String?): DailyCategory? =
        value?.let { DailyCategory.fromLabel(it) ?: error("Unknown daily category: $it") }

    @TypeConverter
    fun fromAuditAction(action: AuditAction?): String? = action?.name

    @TypeConverter
    fun toAuditAction(value: String?): AuditAction? = value?.let(AuditAction::valueOf)
}
