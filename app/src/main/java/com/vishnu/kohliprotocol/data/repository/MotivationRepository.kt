package com.vishnu.kohliprotocol.data.repository

import android.net.Uri
import com.vishnu.kohliprotocol.data.local.dao.MotivationDao
import com.vishnu.kohliprotocol.data.local.entity.MotivationPhotoEntity
import com.vishnu.kohliprotocol.data.storage.InternalStorageManager
import kotlinx.coroutines.flow.Flow

/** The private Motivation gallery: database rows plus their files in private storage. */
class MotivationRepository(
    private val dao: MotivationDao,
    private val storage: InternalStorageManager,
) {
    fun observePhotos(): Flow<List<MotivationPhotoEntity>> = dao.observeAll()

    /** Copies [source] into private storage and adds it to the gallery. */
    suspend fun addPhoto(source: Uri, title: String? = null): MotivationPhotoEntity {
        val file = storage.importFrom(source, InternalStorageManager.Directory.MOTIVATION)
        val photo = MotivationPhotoEntity(
            photoPath = file.absolutePath,
            dateAdded = System.currentTimeMillis(),
            title = title?.trim()?.ifEmpty { null },
        )
        return try {
            photo.copy(id = dao.insert(photo))
        } catch (e: Exception) {
            storage.delete(file.absolutePath)
            throw e
        }
    }

    suspend fun deletePhoto(photo: MotivationPhotoEntity) {
        dao.delete(photo)
        storage.delete(photo.photoPath)
    }
}
