package app.hitsu.vault.data.media

import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Holds what other apps shared into Hitsu until the vault is open enough to encrypt it.
 *
 * Spec §7.4: the bytes are copied the moment the intent arrives, before the PIN is asked for. The
 * app that shared them can die at any point, and its permission grant dies with it, so waiting for
 * the unlock would mean losing the file. The copy is plaintext, which is why it lives in private
 * cache, is overwritten before being unlinked, and never survives the import.
 */
class SharedIntake(
    private val sources: ImportSources,
    private val stagingDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val appScope: CoroutineScope,
) {

    class Staged(
        val file: File,
        val displayName: String?,
        val mime: String,
    )

    private val _staged = MutableStateFlow<List<Staged>>(emptyList())
    val staged: StateFlow<List<Staged>> = _staged.asStateFlow()

    private val _staging = MutableStateFlow(false)
    val staging: StateFlow<Boolean> = _staging.asStateFlow()

    fun stage(uris: List<Uri>): Job? {
        val accepted = uris.distinct()
        if (accepted.isEmpty()) return null
        return appScope.launch {
            _staging.value = true
            try {
                val copied = withContext(ioDispatcher) { accepted.mapNotNull(::copy) }
                if (copied.isNotEmpty()) _staged.update { it + copied }
            } finally {
                _staging.value = false
            }
        }
    }

    /** A file we downloaded ourselves: it waits here exactly like anything else shared in. */
    fun hold(file: File, displayName: String?, mime: String) {
        val target = File(stagingDir.also { it.mkdirs() }, UUID.randomUUID().toString())
        if (file.renameTo(target) || runCatching { file.copyTo(target, overwrite = true) }.isSuccess) {
            file.delete()
            _staged.update { it + Staged(target, displayName, mime) }
        }
    }

    /** Hands over what was staged; the caller owns the files and must [discard] them when done. */
    fun take(): List<Staged> = _staged.getAndUpdate { emptyList() }

    fun discard(items: List<Staged>) {
        items.forEach { shred(it.file) }
    }

    fun wipe() {
        discard(take())
        stagingDir.listFiles()?.forEach(::shred)
    }

    private fun copy(uri: Uri): Staged? = try {
        val source = sources.from(uri)
        stagingDir.mkdirs()
        val target = File(stagingDir, UUID.randomUUID().toString())
        source.open().use { input -> target.outputStream().use(input::copyTo) }
        Staged(target, source.displayName, source.mime)
    } catch (_: Exception) {
        null
    }

    private fun shred(file: File) {
        try {
            if (file.exists()) {
                file.outputStream().use { out ->
                    val zeros = ByteArray(ZERO_CHUNK)
                    var written = 0L
                    val size = file.length()
                    while (written < size) {
                        val step = minOf(ZERO_CHUNK.toLong(), size - written).toInt()
                        out.write(zeros, 0, step)
                        written += step
                    }
                }
            }
        } catch (_: Exception) {
            // Best effort; the file is deleted either way.
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val ZERO_CHUNK = 64 * 1024
    }
}

fun SharedIntake.Staged.asImportSource(): ImportSource = ImportSource(
    displayName = displayName,
    mime = mime,
    sizeBytes = file.length(),
    open = { file.inputStream() },
    openDescriptor = { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) },
)
