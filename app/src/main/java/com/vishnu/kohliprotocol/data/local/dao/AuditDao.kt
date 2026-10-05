package com.vishnu.kohliprotocol.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.vishnu.kohliprotocol.data.local.entity.AuditAction
import com.vishnu.kohliprotocol.data.local.entity.AuditEventEntity
import kotlinx.coroutines.flow.Flow

/** Insert and read only: the audit log is append-only by design. */
@Dao
interface AuditDao {

    @Insert
    suspend fun insert(event: AuditEventEntity): Long

    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_events WHERE actionType = :action ORDER BY timestamp DESC, id DESC")
    fun observeByAction(action: AuditAction): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_events WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp, id")
    suspend fun getBetween(from: Long, to: Long): List<AuditEventEntity>
}
