package com.vishnu.kohliprotocol.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.vishnu.kohliprotocol.data.local.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface FoodEntryDao {

    @Insert
    suspend fun insert(entry: FoodEntryEntity): Long

    @Update
    suspend fun update(entry: FoodEntryEntity)

    @Delete
    suspend fun delete(entry: FoodEntryEntity)

    @Query("SELECT * FROM food_entries WHERE id = :id")
    suspend fun get(id: Long): FoodEntryEntity?

    @Query("SELECT COUNT(*) FROM food_entries WHERE mealId = :mealId")
    suspend fun countForMeal(mealId: Long): Int

    /** Everything eaten on [date], slot food and "+ Add Food" alike. */
    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY timestamp")
    fun observeForDate(date: LocalDate): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY timestamp")
    suspend fun getForDate(date: LocalDate): List<FoodEntryEntity>

    @Query("SELECT * FROM food_entries WHERE date = :date AND isArbitrary = 1 ORDER BY timestamp")
    fun observeArbitrary(date: LocalDate): Flow<List<FoodEntryEntity>>
}
