package com.vishnu.kohliprotocol.data.ai

import android.util.Base64
import com.vishnu.kohliprotocol.data.storage.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Downscales a stored photo to a JPEG small enough to send to an AI provider (orientation
 * corrected). The file in private storage is never modified.
 */
internal object ImageEncoder {

    private const val MAX_EDGE = 1280
    private const val JPEG_QUALITY = 80

    /** Base64 (no line wraps) JPEG, or null if the file is missing or undecodable. */
    suspend fun jpegBase64(path: String): String? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.isFile) return@withContext null
        val bytes = runCatching {
            ImageCompressor.compress({ FileInputStream(file) }, MAX_EDGE, JPEG_QUALITY, flattenAlpha = true)
        }.getOrNull() ?: return@withContext null
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
