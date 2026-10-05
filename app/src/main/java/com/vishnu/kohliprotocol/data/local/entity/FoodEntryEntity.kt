package com.vishnu.kohliprotocol.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A single thing eaten. Slot food has a [mealId]; "+ Add Food" items are [isArbitrary] with no
 * meal. [date] is the calendar day the food belongs to — food eaten after midnight belongs to
 * the new day.
 */
@Entity(
    tableName = "food_entries",
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mealId"), Index("date")],
)
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long?,
    val date: LocalDate,
    val description: String,
    /** Absolute path inside app-private storage, or null when no photo is attached. */
    val photoPath: String? = null,
    val timestamp: Long,
    val isArbitrary: Boolean,
)
