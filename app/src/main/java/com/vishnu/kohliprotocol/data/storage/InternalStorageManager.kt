package com.vishnu.kohliprotocol.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

/**
 * Stores photos in app-private storage (`filesDir`), which no other app can read. The only way
 * out is a content:// URI from the non-exported FileProvider, which another app can open only
 * with an explicit, temporary grant (e.g. the camera writing a capture).
 *
 * New photos are stored downscaled (longest edge [STORED_MAX_EDGE] px, JPEG) — a few hundred KB
 * instead of several MB. Anything that can't be processed is kept as-is, and photos saved
 * before this optimisation are never touched.
 */
class InternalStorageManager(context: Context) {

    enum class Directory(val dirName: String) {
        MEAL_PHOTOS("meal_photos"),
        MOTIVATION("motivation"),
    }

    private val appContext = context.applicationContext
    private val authority = "${appContext.packageName}.fileprovider"

    fun directory(dir: Directory): File = File(appContext.filesDir, dir.dirName).apply { mkdirs() }

    /**
     * Stores a downscaled JPEG of a gallery/document [source] in private storage and returns the
     * new file. Falls back to a byte-for-byte copy if the image can't be decoded or has
     * transparency.
     */
    suspend fun importFrom(source: Uri, dir: Directory): File = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val optimized = runCatching {
            ImageCompressor.compress({ resolver.openInputStream(source) }, STORED_MAX_EDGE, STORED_QUALITY, flattenAlpha = false)
        }.onFailure { Log.w(TAG, "Photo optimisation failed; storing original", it) }.getOrNull()
        if (optimized != null) {
            val target = newFile(dir, DEFAULT_EXTENSION)
            try {
                target.writeBytes(optimized)
                return@withContext target
            } catch (e: IOException) {
                target.delete()
                throw e
            }
        }

        val extension = resolver.getType(source)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: DEFAULT_EXTENSION
        val target = newFile(dir, extension)
        try {
            val input = resolver.openInputStream(source) ?: throw IOException("Cannot open $source")
            input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
            target
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    /**
     * An empty private file for the camera to write into. Pass [contentUriFor] of it to
     * `ActivityResultContracts.TakePicture`; call [delete] on it if the capture is cancelled.
     */
    fun newCaptureTarget(dir: Directory): File = newFile(dir, DEFAULT_EXTENSION)

    /**
     * Shrinks a photo already in private storage (e.g. a fresh camera capture) in place. Only
     * replaces the file if the result is smaller; on any failure the original is left untouched.
     */
    suspend fun optimizeInPlace(path: String) {
        withContext(Dispatchers.IO) {
            val file = runCatching { requireOwned(File(path)) }.getOrNull() ?: return@withContext
            val temp = File(file.parentFile, "${file.name}.tmp")
            try {
                val bytes = ImageCompressor.compress({ FileInputStream(file) }, STORED_MAX_EDGE, STORED_QUALITY, flattenAlpha = false)
                if (bytes != null && bytes.size < file.length()) {
                    temp.writeBytes(bytes)
                    if (!temp.renameTo(file)) temp.delete()
                }
            } catch (e: Throwable) {
                // Includes OutOfMemoryError on very large images: the original stays usable.
                Log.w(TAG, "Photo optimisation failed; keeping original", e)
                temp.delete()
            }
        }
    }

    /** A content:// URI for a file this manager owns. Throws for any other path. */
    fun contentUriFor(file: File): Uri =
        FileProvider.getUriForFile(appContext, authority, requireOwned(file))

    fun fileFor(path: String): File = requireOwned(File(path))

    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        requireOwned(File(path)).delete()
    }

    private fun newFile(dir: Directory, extension: String): File =
        File(directory(dir), "${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")

    /** Rejects paths outside our photo directories, including `../` traversal. */
    private fun requireOwned(file: File): File {
        val canonical = file.canonicalFile
        val owned = Directory.entries.any { canonical.parentFile == directory(it).canonicalFile }
        require(owned) { "Not an app-private photo: $file" }
        return canonical
    }

    companion object {
        private const val TAG = "KohliProtocol"
        private const val DEFAULT_EXTENSION = "jpg"

        /** Plenty for full-screen viewing and AI analysis. */
        const val STORED_MAX_EDGE = 1600
        private const val STORED_QUALITY = 85
    }
}
