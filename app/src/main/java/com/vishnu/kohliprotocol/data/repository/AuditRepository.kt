package com.vishnu.kohliprotocol.data.repository

import com.vishnu.kohliprotocol.data.local.dao.AuditDao
import com.vishnu.kohliprotocol.data.local.entity.AuditAction
import com.vishnu.kohliprotocol.data.local.entity.AuditEventEntity
import kotlinx.coroutines.flow.Flow

/** Local, append-only audit trail (spec §24). There is deliberately no way to edit or delete. */
class AuditRepository(private val dao: AuditDao) {

    suspend fun log(
        action: AuditAction,
        description: String,
        metadata: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ): Long = dao.insert(
        AuditEventEntity(
            timestamp = timestamp,
            actionType = action,
            description = description,
            metadata = metadata,
        )
    )

    fun observeRecent(limit: Int = 100): Flow<List<AuditEventEntity>> = dao.observeRecent(limit)

    fun observeByAction(action: AuditAction): Flow<List<AuditEventEntity>> = dao.observeByAction(action)

    suspend fun eventsBetween(fromMillis: Long, toMillis: Long): List<AuditEventEntity> =
        dao.getBetween(fromMillis, toMillis)
}
