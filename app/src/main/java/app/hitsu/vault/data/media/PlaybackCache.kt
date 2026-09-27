package app.hitsu.vault.data.media

import android.os.storage.StorageManager
import app.hitsu.vault.crypto.VaultCipher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

class InsufficientSpaceException(val requiredBytes: Long, val freeBytes: Long) : Exception()

/**
 * Media3 cannot read our ciphertext, so a video is decrypted to a private cache file before it
 * plays and wiped afterwards (spec §5.4). Spec §18.2: refuse up front unless there is room for the
 * copy plus the same again, rather than filling the disk and failing halfway.
 */
class PlaybackCache(
    private val directory: File,
    private val files: VaultFiles,
    private val storage: StorageManager?,
    private val ioDispatcher: CoroutineDispatcher,
) {

    fun requiredFreeBytes(sizeBytes: Long): Long = sizeBytes * 2

    /**
     * Asks the system what it could actually give us, which includes cached data it is willing to
     * evict. Plain usable space would refuse videos the device has perfectly good room for.
     */
    fun freeBytes(): Long {
        val path = directory.parentFile ?: directory
        return try {
            storage?.let { it.getAllocatableBytes(it.getUuidForPath(path)) } ?: path.usableSpace
        } catch (_: IOException) {
            path.usableSpace
        }
    }

    /** [onProgress] is the fraction of plaintext written so far, for the "Descifrando" indicator. */
    suspend fun prepare(
        objectPath: String,
        sizeBytes: Long,
        cipher: VaultCipher,
        onProgress: (Float) -> Unit,
    ): File = withContext(ioDispatcher) {
        directory.mkdirs()
        val required = requiredFreeBytes(sizeBytes)
        val free = freeBytes()
        if (free < required) throw InsufficientSpaceException(required, free)

        val target = File(directory, "playback.tmp")
        target.delete()
        try {
            files.file(objectPath).inputStream().use { input ->
                target.outputStream().use { output ->
                    cipher.decryptTo(input, output) { written ->
                        if (sizeBytes > 0) onProgress((written.toFloat() / sizeBytes).coerceIn(0f, 1f))
                    }
                }
            }
            coroutineContext.ensureActive()
            target
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
    }

    /** Called when playback ends, the vault locks, or the process is about to give up the screen. */
    fun wipe() {
        directory.listFiles()?.forEach { it.delete() }
    }
}
