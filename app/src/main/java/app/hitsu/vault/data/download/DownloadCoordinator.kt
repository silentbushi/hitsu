package app.hitsu.vault.data.download

import android.content.Context
import app.hitsu.vault.R
import app.hitsu.vault.data.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.io.File

sealed interface DownloadPhase {
    data object Idle : DownloadPhase
    data object Preparing : DownloadPhase
    data class Ready(val media: PostMedia) : DownloadPhase
    data class Running(val progress: Float) : DownloadPhase
    data object Importing : DownloadPhase
    data object Done : DownloadPhase
    data class Failed(val reason: Reason, val detail: String? = null) : DownloadPhase

    enum class Reason { Unsupported, Network, Locked, Storage }
}

/**
 * Turns a shared link into a vault item: yt-dlp fetches the media, and from there it takes exactly
 * the same path as anything imported by hand, fingerprint and encryption included.
 */
class DownloadCoordinator(
    private val context: Context,
    private val engine: YtDlpEngine,
    private val repository: MediaRepository,
    private val cookies: CookieStore,
    private val notifications: DownloadNotifications,
    private val directory: File,
    private val appScope: CoroutineScope,
) {

    private val _phase = MutableStateFlow<DownloadPhase>(DownloadPhase.Idle)
    val phase: StateFlow<DownloadPhase> = _phase.asStateFlow()

    private var job: Job? = null

    /**
     * One download at a time, each in its own folder. The screen and the share-sheet shortcut share
     * this coordinator, and a second run used to wipe the folder the first was still writing into,
     * which yt-dlp reports as an I/O error on a closed file.
     */
    private val running = Mutex()

    fun resolve(url: String) {
        if (job?.isActive == true) return
        job = appScope.launch {
            _phase.value = DownloadPhase.Preparing
            val media = withCookieJar { jar -> engine.resolve(url, jar) }
            _phase.value = if (media == null) {
                DownloadPhase.Failed(DownloadPhase.Reason.Unsupported, engine.lastError)
            } else {
                DownloadPhase.Ready(media)
            }
        }
    }

    fun start(url: String) {
        if (job?.isActive == true) return
        job = appScope.launch {
            running.withLock {
            val runDirectory = newRunDirectory()
            _phase.value = DownloadPhase.Running(0f)
            val files = withCookieJar { jar ->
                engine.download(url, runDirectory, jar) { progress ->
                    _phase.value = DownloadPhase.Running(progress)
                }
            }
            if (files.isEmpty()) {
                _phase.value = DownloadPhase.Failed(DownloadPhase.Reason.Network, engine.lastError)
                return@withLock
            }
            if (!repository.canImport()) {
                // The vault closed while the download ran; nothing stays behind in the clear.
                runDirectory.deleteRecursively()
                _phase.value = DownloadPhase.Failed(DownloadPhase.Reason.Locked)
                return@withLock
            }
            _phase.value = DownloadPhase.Importing
            repository.importDownloaded(files)?.join()
            runDirectory.deleteRecursively()
            _phase.value = DownloadPhase.Done
            }
        }
    }

    private fun newRunDirectory(): File = File(directory, RUN_PREFIX + UUID.randomUUID())

    /**
     * The cookie jar gets a folder of its own, away from the one yt-dlp writes into. That folder is
     * wiped at the start of every run and everything left in it afterwards is imported into the
     * vault, and the jar should survive the first and never go through the second — a cookies.txt
     * sitting there was both deleted before yt-dlp could read it and reported as a file that could
     * not be imported.
     */
    private suspend fun <T> withCookieJar(block: suspend (File?) -> T): T {
        val jarDirectory = File(directory, JAR_PREFIX + UUID.randomUUID())
        return try {
            cookies.withCookies(jarDirectory, block)
        } finally {
            jarDirectory.deleteRecursively()
        }
    }

    /**
     * The share-sheet shortcut: no screen, so the outcome is told through a notification. A closed
     * vault is not a failure here; the file waits, encrypted on the next unlock, as a share does.
     */
    suspend fun runQuickDownload(url: String, onProgress: (String?, Int?) -> Unit) = running.withLock {
        val runDirectory = newRunDirectory()
        val media = withCookieJar { jar -> engine.resolve(url, jar) }
        if (media == null) {
            notifications.finished(null, context.getString(R.string.notification_failed))
            return@withLock
        }
        onProgress(media.title, 0)

        val files = withCookieJar { jar ->
            engine.download(url, runDirectory, jar) { progress ->
                onProgress(media.title, (progress * 100).toInt())
            }
        }
        if (files.isEmpty()) {
            notifications.finished(media.title, context.getString(R.string.notification_failed))
            return@withLock
        }

        if (repository.canImport()) {
            repository.importDownloaded(files)?.join()
            runDirectory.deleteRecursively()
            notifications.finished(media.title, savedMessage(files.size))
        } else {
            repository.holdForUnlock(files)
            runDirectory.deleteRecursively()
            notifications.finished(media.title, context.getString(R.string.notification_waiting))
        }
    }

    private fun savedMessage(count: Int): String = if (count > 1) {
        context.resources.getQuantityString(R.plurals.notification_saved_many, count, count)
    } else {
        context.getString(R.string.notification_saved)
    }

    fun cancel() {
        engine.cancel()
        job?.cancel()
        _phase.value = DownloadPhase.Idle
    }

    fun reset() {
        _phase.value = DownloadPhase.Idle
    }

    private companion object {
        const val RUN_PREFIX = "run-"
        const val JAR_PREFIX = "jar-"
    }
}
