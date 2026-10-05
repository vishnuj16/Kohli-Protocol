package com.vishnu.kohliprotocol.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vishnu.kohliprotocol.data.local.entity.WeeklyReportEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WeeklyReportDao {

    /** Re-evaluating a week replaces its previous report. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: WeeklyReportEntity): Long

    @Query("SELECT * FROM weekly_reports WHERE id = :id")
    suspend fun get(id: Long): WeeklyReportEntity?

    @Query("SELECT * FROM weekly_reports WHERE startDate = :startDate")
    suspend fun getByStartDate(startDate: LocalDate): WeeklyReportEntity?

    @Query("SELECT * FROM weekly_reports ORDER BY startDate DESC")
    fun observeAll(): Flow<List<WeeklyReportEntity>>

    @Query("SELECT * FROM weekly_reports ORDER BY startDate DESC LIMIT 1")
    fun observeLatest(): Flow<WeeklyReportEntity?>

    @Query("UPDATE weekly_reports SET aiSummary = :summary WHERE id = :id")
    suspend fun setAiSummary(id: Long, summary: String?): Int

    @Query("UPDATE weekly_reports SET pdfPath = :pdfPath WHERE id = :id")
    suspend fun setPdfPath(id: Long, pdfPath: String?): Int
}
