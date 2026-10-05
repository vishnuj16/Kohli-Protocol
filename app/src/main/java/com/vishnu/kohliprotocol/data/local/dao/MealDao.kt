package com.vishnu.kohliprotocol.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.vishnu.kohliprotocol.data.local.entity.MealEntity
import com.vishnu.kohliprotocol.data.local.entity.MealType
import com.vishnu.kohliprotocol.data.local.entity.MealWithEntries
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface MealDao {

    /** Returns the new row id, or -1 if the slot already exists for that day. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(meal: MealEntity): Long

    @Update
    suspend fun update(meal: MealEntity)

    @Query("SELECT * FROM meals WHERE date = :date AND mealType = :mealType LIMIT 1")
    suspend fun get(date: LocalDate, mealType: MealType): MealEntity?

    @Transaction
    @Query("SELECT * FROM meals WHERE date = :date")
    fun observeDay(date: LocalDate): Flow<List<MealWithEntries>>

    @Transaction
    @Query("SELECT * FROM meals WHERE date BETWEEN :start AND :end ORDER BY date")
    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<MealWithEntries>>

    @Transaction
    @Query("SELECT * FROM meals WHERE date BETWEEN :start AND :end ORDER BY date")
    suspend fun getRange(start: LocalDate, end: LocalDate): List<MealWithEntries>

    /** Slots on [date] that count as logged: explicitly skipped, or with at least one entry. */
    @Query(
        """
        SELECT COUNT(*) FROM meals m
        WHERE m.date = :date
          AND (m.isSkipped = 1 OR EXISTS (SELECT 1 FROM food_entries f WHERE f.mealId = m.id))
        """
    )
    suspend fun countLoggedSlots(date: LocalDate): Int
}
