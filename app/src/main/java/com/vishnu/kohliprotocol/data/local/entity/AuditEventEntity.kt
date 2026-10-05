package com.vishnu.kohliprotocol.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Append-only record of an important action. Never updated or deleted. */
@Entity(
    tableName = "audit_events",
    indices = [Index("timestamp"), Index("actionType")],
)
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val actionType: AuditAction,
    val description: String,
    val metadata: String? = null,
)
