package app.hitsu.vault.data.download

import android.content.Context
import androidx.core.content.edit
import app.hitsu.vault.domain.Clock
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class PostMedia(
    val title: String?,
    val durationSeconds: Int,
    val extractor: String?,
    val extension: String?,
)

sealed interface UpdateOutcome {
    data class Updated(val version: String?) : UpdateOutcome
    data object AlreadyCurrent : UpdateOutcome
    data class Failed(val reason: String?) : UpdateOutcome
}

/**
 * Wraps yt-dlp, which is a real Python program bundled with the app rather than a web request.
 *
 * Extracting the media URL out of a post is what sites change constantly, so yt-dlp is the part
 * that ages: [update] replaces it with a newer release without shipping a new build of Hitsu.
 */
class YtDlpEngine(
    private val context: Context,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val mutex = Mutex()
    private var started = false

    /** Why the engine would not start, kept so the UI and tests can say something useful. */
    var lastError: String? = null
        private set

    /**
     * Every run gets its own handle. Deriving it from the URL meant two downloads of the same post
     * collided, and yt-dlp refused the second with "Process ID already exists".
     */
    private val activeProcesses = ConcurrentHashMap.newKeySet<String>()

    /** Unpacks the runtime on first use, which takes a moment; everything else waits on this. */
    suspend fun ensureReady(): Boolean = mutex.withLock {
        if (started) return@withLock true
        started = withContext(ioDispatcher) {
            runCatching { YoutubeDL.init(context) }
                .onFailure { lastError = it.message ?: it::class.java.simpleName }
                .isSuccess
        }
        started
    }

    /**
     * Asks the binary itself. The library keeps its own note of the version, but that note is what
     * its updater compares against GitHub, and once the two disagree it refuses to update while
     * still running the old copy. The program that actually runs is the only honest answer.
     */
    suspend fun version(): String? = withContext(ioDispatcher) {
        if (!ensureReady()) return@withContext null
        runtimeVersion()
    }

    /**
     * Keeps yt-dlp current before it is used. The copy inside the library is as old as the release
     * that shipped it, and an old one does not merely miss new sites: it fails on posts it cannot
     * parse any more, with errors as unhelpful as "I/O operation on closed file". Sites change
     * weekly, so age is the thing to watch, not a fixed floor.
     */
    suspend fun ensureUsable(): Boolean = withContext(ioDispatcher) {
        if (!ensureReady()) return@withContext false
        val current = runtimeVersion() ?: return@withContext false
        if (!isStale(current)) return@withContext true
        // Do not hammer GitHub when an update is unavailable; carry on with what we have.
        if (clock.now() - lastCheckMillis < CHECK_INTERVAL_MS) return@withContext true
        update()
        runtimeVersion() != null
    }

    /** When the engine last asked GitHub for a newer yt-dlp, for the settings screen. */
    var lastCheckMillis: Long
        get() = preferences.getLong(LAST_CHECK_KEY, 0L)
        private set(value) = preferences.edit { putLong(LAST_CHECK_KEY, value) }

    private fun isStale(version: String): Boolean {
        val released = runCatching {
            val parts = version.trim().split(".").take(3).map(String::toInt)
            LocalDate.of(parts[0], parts[1], parts[2])
        }.getOrNull() ?: return true
        val today = Instant.ofEpochMilli(clock.now()).atZone(ZoneId.systemDefault()).toLocalDate()
        return ChronoUnit.DAYS.between(released, today) > STALE_AFTER_DAYS
    }

    private val preferences
        get() = context.getSharedPreferences(ENGINE_PREFS, Context.MODE_PRIVATE)

    private fun runtimeVersion(): String? = runCatching {
        YoutubeDL.execute(YoutubeDLRequest(listOf("--version")))
            .out
            .trim()
            .takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Describes the post. Asks for a single entry on purpose: a post with several videos makes
     * yt-dlp print one JSON document per video, which does not parse as one. The preview shows the
     * first item, and download still brings the whole post.
     */
    suspend fun resolve(url: String, cookies: File? = null): PostMedia? = withContext(ioDispatcher) {
        if (!ensureUsable()) return@withContext null
        runCatching {
            val info = YoutubeDL.getInfo(request(url, cookies).addOption("--no-playlist"))
            PostMedia(
                title = info.title,
                durationSeconds = info.duration,
                extractor = info.extractorKey ?: info.extractor,
                extension = info.ext,
            )
        }.onFailure { lastError = it.summarise() }.getOrNull()
    }

    /**
     * Downloads everything the post holds and returns the files that landed: several videos in
     * one post mean several files, which is why nothing asks for a single entry here. Only single-file formats
     * are asked for: merging separate video and audio tracks would need ffmpeg, which Hitsu does
     * not carry.
     */
    suspend fun download(
        url: String,
        directory: File,
        cookies: File? = null,
        onProgress: (Float) -> Unit,
    ): List<File> = withContext(ioDispatcher) {
        if (!ensureUsable()) return@withContext emptyList()
        directory.deleteRecursively()
        directory.mkdirs()

        val request = request(url, cookies).apply {
            addOption("-f", SINGLE_FILE_FORMATS)
            addOption("-o", File(directory, OUTPUT_TEMPLATE).absolutePath)
        }
        val processId = UUID.randomUUID().toString()
        activeProcesses += processId
        runCatching {
            YoutubeDL.execute(request, processId) { progress, _, _ ->
                // Anything thrown here would reach the reader of yt-dlp's output and close it.
                runCatching { onProgress((progress / 100f).coerceIn(0f, 1f)) }
                Unit
            }
        }.onFailure { lastError = it.summarise() }
            .also { activeProcesses -= processId }
            .getOrNull() ?: return@withContext emptyList()

        coroutineContext.ensureActive()
        directory.listFiles().orEmpty().filter { it.isFile && it.length() > 0 }.sortedBy { it.name }
    }

    /** Stops whatever is downloading right now; there is at most one run at a time. */
    fun cancel() {
        activeProcesses.forEach { id ->
            runCatching { YoutubeDL.destroyProcessById(id) }
        }
        activeProcesses.clear()
    }

    suspend fun update(): UpdateOutcome = withContext(ioDispatcher) {
        if (!ensureReady()) return@withContext UpdateOutcome.Failed(lastError)
        val before = runtimeVersion()
        lastCheckMillis = clock.now()
        forgetRememberedVersion()
        runCatching { YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE) }.fold(
            onSuccess = {
                val after = runtimeVersion()
                when {
                    after == null -> UpdateOutcome.Failed(null)
                    after == before -> UpdateOutcome.AlreadyCurrent
                    else -> UpdateOutcome.Updated(after)
                }
            },
            onFailure = {
                lastError = it.summarise()
                UpdateOutcome.Failed(lastError)
            },
        )
    }

    /**
     * Drops the library's note of which version it has. Without this its updater compares that note
     * against the newest release, sees a match and declines to replace the old program on disk.
     */
    private fun forgetRememberedVersion() {
        runCatching {
            context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE)
                .edit { remove(LIBRARY_VERSION_KEY) }
        }
    }

    /**
     * yt-dlp explains itself on stderr, and that explanation is the difference between "this site
     * changed" and "this post needs your session". The last line carries it.
     */
    private fun Throwable.summarise(): String {
        val text = message.orEmpty()
        val error = text.lineSequence()
            .map(String::trim)
            .lastOrNull { it.startsWith("ERROR:") || it.contains("error", ignoreCase = true) }
        return (error ?: text.lines().lastOrNull { it.isNotBlank() } ?: this::class.java.simpleName)
            .removePrefix("ERROR:")
            .trim()
            .take(MAX_ERROR_CHARS)
    }

    private fun request(url: String, cookies: File? = null) = YoutubeDLRequest(url).apply {
        addOption("--no-warnings")
        // Sites that hide a post behind a login only answer when the session comes along.
        cookies?.let { addOption("--cookies", it.absolutePath) }
    }

    private companion object {
        const val SINGLE_FILE_FORMATS = "best[ext=mp4]/best[ext=webm]/best"
        const val OUTPUT_TEMPLATE = "%(id).60s.%(ext)s"
        const val MAX_ERROR_CHARS = 300
        const val LIBRARY_PREFS = "youtubedl-android"
        const val LIBRARY_VERSION_KEY = "youtubeDLVersion"

        const val ENGINE_PREFS = "hitsu.ytdlp"
        const val LAST_CHECK_KEY = "lastCheck"

        /** yt-dlp itself starts warning at ninety days; a month keeps well clear of trouble. */
        const val STALE_AFTER_DAYS = 30L
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
