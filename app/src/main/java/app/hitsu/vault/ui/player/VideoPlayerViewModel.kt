package app.hitsu.vault.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.hitsu.vault.data.MediaRepository
import app.hitsu.vault.data.media.InsufficientSpaceException
import app.hitsu.vault.domain.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

const val SEEK_STEP_MS = 10_000L

sealed interface PlaybackPhase {
    data class Decrypting(val progress: Float) : PlaybackPhase
    data object Ready : PlaybackPhase
    data class NoSpace(val requiredBytes: Long, val freeBytes: Long) : PlaybackPhase
    data object Failed : PlaybackPhase
}

/** Transient overlays the gestures raise, spec §8. */
sealed interface GestureFeedback {
    data class Seek(val targetMs: Long, val deltaMs: Long) : GestureFeedback
    data class Volume(val percent: Int) : GestureFeedback
    data class Brightness(val percent: Int) : GestureFeedback
    data class Skip(val forward: Boolean) : GestureFeedback
}

data class VideoPlayerUiState(
    val item: MediaItem? = null,
    val phase: PlaybackPhase = PlaybackPhase.Decrypting(0f),
    val chromeVisible: Boolean = true,
    val menuVisible: Boolean = false,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val muted: Boolean = false,
    val speed: Float = 1f,
    val looping: Boolean = false,
    val scrubbingMs: Long? = null,
    val feedback: GestureFeedback? = null,
    val inPictureInPicture: Boolean = false,
)

@OptIn(UnstableApi::class)
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: MediaRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mediaId: String = checkNotNull(savedStateHandle[ARG_ID])

    private val _state = MutableStateFlow(VideoPlayerUiState())
    val state: StateFlow<VideoPlayerUiState> = _state.asStateFlow()

    /** Media3 handles audio focus for us, which is what pauses playback on an incoming call. */
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(playing = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.update { it.copy(durationMs = duration.coerceAtLeast(0L)) }
                }
            }
        })
    }

    private var ticker: Job? = null
    private var feedbackJob: Job? = null

    init {
        viewModelScope.launch {
            val item = repository.item(mediaId)
            _state.update { it.copy(item = item, durationMs = item?.durationMs ?: 0L) }
            prepare(item)
        }
    }

    private suspend fun prepare(item: MediaItem?) {
        if (item == null) {
            _state.update { it.copy(phase = PlaybackPhase.Failed) }
            return
        }
        try {
            val file = repository.preparePlayback(mediaId) { progress ->
                _state.update { it.copy(phase = PlaybackPhase.Decrypting(progress)) }
            }
            if (file == null) {
                _state.update { it.copy(phase = PlaybackPhase.Failed) }
                return
            }
            player.setMediaItem(Media3Item.fromUri(file.toURI().toString()))
            player.prepare()
            player.playWhenReady = true
            _state.update { it.copy(phase = PlaybackPhase.Ready) }
            startTicker()
        } catch (e: InsufficientSpaceException) {
            _state.update { it.copy(phase = PlaybackPhase.NoSpace(e.requiredBytes, e.freeBytes)) }
        } catch (_: Exception) {
            _state.update { it.copy(phase = PlaybackPhase.Failed) }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                if (_state.value.scrubbingMs == null) {
                    _state.update {
                        it.copy(
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.coerceAtLeast(it.durationMs),
                        )
                    }
                }
                delay(TICK_MS)
            }
        }
    }

    fun onToggleChrome() {
        _state.update { it.copy(chromeVisible = !it.chromeVisible, menuVisible = false) }
    }

    fun onPlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun onToggleMute() {
        val muted = !_state.value.muted
        player.volume = if (muted) 0f else 1f
        _state.update { it.copy(muted = muted) }
    }

    fun onToggleMenu() {
        _state.update { it.copy(menuVisible = !it.menuVisible) }
    }

    fun onSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun onToggleLoop() {
        val looping = !_state.value.looping
        player.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _state.update { it.copy(looping = looping) }
    }

    fun onScrub(positionMs: Long) {
        _state.update { it.copy(scrubbingMs = positionMs.coerceIn(0L, it.durationMs)) }
    }

    fun onScrubFinished() {
        val target = _state.value.scrubbingMs ?: return
        player.seekTo(target)
        _state.update { it.copy(scrubbingMs = null, positionMs = target) }
    }

    /** Horizontal drag: seek proportional to the duration, previewing before committing. */
    fun onSeekDrag(fractionOfWidth: Float) {
        val state = _state.value
        val base = state.scrubbingMs ?: state.positionMs
        val delta = (fractionOfWidth * state.durationMs).toLong()
        val target = (base + delta).coerceIn(0L, state.durationMs)
        _state.update {
            it.copy(
                scrubbingMs = target,
                feedback = GestureFeedback.Seek(target, target - state.positionMs),
            )
        }
    }

    fun onSeekDragFinished() {
        val target = _state.value.scrubbingMs ?: return
        player.seekTo(target)
        _state.update { it.copy(scrubbingMs = null, positionMs = target) }
        clearFeedbackSoon()
    }

    fun onSkip(forward: Boolean) {
        val target = (player.currentPosition + if (forward) SEEK_STEP_MS else -SEEK_STEP_MS)
            .coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
        _state.update { it.copy(positionMs = target, feedback = GestureFeedback.Skip(forward)) }
        clearFeedbackSoon()
    }

    fun onVolumeChanged(percent: Int) {
        _state.update { it.copy(feedback = GestureFeedback.Volume(percent)) }
        clearFeedbackSoon()
    }

    fun onBrightnessChanged(percent: Int) {
        _state.update { it.copy(feedback = GestureFeedback.Brightness(percent)) }
        clearFeedbackSoon()
    }

    fun onRetryAfterSpace() {
        viewModelScope.launch { prepare(_state.value.item) }
    }

    private fun clearFeedbackSoon() {
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(FEEDBACK_MS)
            _state.update { it.copy(feedback = null) }
        }
    }

    fun onPause() {
        player.pause()
    }

    fun onPictureInPictureChanged(inPictureInPicture: Boolean) {
        // The floating window is too small for chrome; it gets the system's own actions instead.
        _state.update {
            it.copy(
                inPictureInPicture = inPictureInPicture,
                chromeVisible = if (inPictureInPicture) false else it.chromeVisible,
                menuVisible = false,
            )
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        player.release()
        // Spec §5.4: the decrypted copy never outlives the screen that needed it.
        repository.wipePlayback()
    }

    companion object {
        const val ARG_ID = "id"
        private const val TICK_MS = 200L
        private const val FEEDBACK_MS = 700L
    }
}
