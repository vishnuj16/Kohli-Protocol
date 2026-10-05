package com.vishnu.kohliprotocol.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.vishnu.kohliprotocol.data.local.entity.MotivationPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MotivationDao {

    @Insert
    suspend fun insert(photo: MotivationPhotoEntity): Long

    @Delete
    suspend fun delete(photo: MotivationPhotoEntity)

    @Query("SELECT * FROM motivation_photos WHERE id = :id")
    suspend fun get(id: Long): MotivationPhotoEntity?

    @Query("SELECT * FROM motivation_photos ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<MotivationPhotoEntity>>
}
