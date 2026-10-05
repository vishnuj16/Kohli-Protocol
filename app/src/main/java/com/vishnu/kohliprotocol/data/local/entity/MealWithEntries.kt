package com.vishnu.kohliprotocol.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class MealWithEntries(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId")
    val entries: List<FoodEntryEntity>,
) {
    val isLogged: Boolean get() = meal.isSkipped || entries.isNotEmpty()
}
