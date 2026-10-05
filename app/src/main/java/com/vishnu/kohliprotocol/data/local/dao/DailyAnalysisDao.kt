package com.vishnu.kohliprotocol.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vishnu.kohliprotocol.data.local.entity.DailyAnalysisEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DailyAnalysisDao {

    @Upsert
    suspend fun upsert(analysis: DailyAnalysisEntity)

    @Query("SELECT * FROM daily_analyses WHERE date = :date")
    suspend fun get(date: LocalDate): DailyAnalysisEntity?

    @Query("SELECT * FROM daily_analyses WHERE date = :date")
    fun observe(date: LocalDate): Flow<DailyAnalysisEntity?>

    @Query("SELECT * FROM daily_analyses WHERE date BETWEEN :start AND :end ORDER BY date")
    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<DailyAnalysisEntity>>

    @Query("SELECT * FROM daily_analyses WHERE date BETWEEN :start AND :end ORDER BY date")
    suspend fun getRange(start: LocalDate, end: LocalDate): List<DailyAnalysisEntity>

    /** Returns the number of rows changed (0 if there is no analysis for [date]). */
    @Query("UPDATE daily_analyses SET isApproved = :approved, rejectionReason = :reason WHERE date = :date")
    suspend fun setApproval(date: LocalDate, approved: Boolean, reason: String?): Int
}
