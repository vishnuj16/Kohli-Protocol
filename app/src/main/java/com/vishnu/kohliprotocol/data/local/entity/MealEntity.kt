package com.vishnu.kohliprotocol.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One meal slot on one day. A slot counts as logged when it has food entries or is explicitly
 * skipped; a skipped meal is logged, not missing.
 */
@Entity(
    tableName = "meals",
    indices = [Index(value = ["date", "mealType"], unique = true)],
)
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val mealType: MealType,
    /** When the slot was last logged or marked skipped (epoch millis). */
    val timestamp: Long,
    val isSkipped: Boolean = false,
)
