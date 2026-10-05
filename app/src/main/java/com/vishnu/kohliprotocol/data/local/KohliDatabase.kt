package com.vishnu.kohliprotocol.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vishnu.kohliprotocol.data.local.dao.AuditDao
import com.vishnu.kohliprotocol.data.local.dao.DailyAnalysisDao
import com.vishnu.kohliprotocol.data.local.dao.FoodEntryDao
import com.vishnu.kohliprotocol.data.local.dao.MealDao
import com.vishnu.kohliprotocol.data.local.dao.MotivationDao
import com.vishnu.kohliprotocol.data.local.dao.WeeklyReportDao
import com.vishnu.kohliprotocol.data.local.entity.AuditEventEntity
import com.vishnu.kohliprotocol.data.local.entity.DailyAnalysisEntity
import com.vishnu.kohliprotocol.data.local.entity.FoodEntryEntity
import com.vishnu.kohliprotocol.data.local.entity.MealEntity
import com.vishnu.kohliprotocol.data.local.entity.MotivationPhotoEntity
import com.vishnu.kohliprotocol.data.local.entity.WeeklyReportEntity

@Database(
    entities = [
        MealEntity::class,
        FoodEntryEntity::class,
        DailyAnalysisEntity::class,
        WeeklyReportEntity::class,
        AuditEventEntity::class,
        MotivationPhotoEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KohliDatabase : RoomDatabase() {

    abstract fun mealDao(): MealDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun dailyAnalysisDao(): DailyAnalysisDao
    abstract fun weeklyReportDao(): WeeklyReportDao
    abstract fun auditDao(): AuditDao
    abstract fun motivationDao(): MotivationDao

    companion object {
        private const val NAME = "kohli_protocol.db"

        /**
         * No destructive-migration fallback: the food log and audit trail must never be wiped
         * silently. Every schema change needs an explicit migration.
         */
        fun build(context: Context): KohliDatabase =
            Room.databaseBuilder(context.applicationContext, KohliDatabase::class.java, NAME)
                .build()
    }
}
