package com.vishnu.kohliprotocol.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A photo in the private Motivation gallery. The file lives in app-private storage. */
@Entity(tableName = "motivation_photos")
data class MotivationPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoPath: String,
    val dateAdded: Long,
    val title: String? = null,
)
