package app.hitsu.vault.ui.slideshow

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hitsu.vault.data.AlbumRepository
import app.hitsu.vault.data.MediaRepository
import app.hitsu.vault.data.SlideshowPreferences
import app.hitsu.vault.domain.MediaFilter
import app.hitsu.vault.domain.MediaItem
import app.hitsu.vault.domain.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SlideshowUiState(
    val items: List<MediaItem> = emptyList(),
    val index: Int = 0,
    val playing: Boolean = true,
    val chromeVisible: Boolean = true,
    /** The pass ran out with no loop, or there was nothing to show: the screen closes itself. */
    val finished: Boolean = false,
) {
    val current: MediaItem? get() = items.getOrNull(index)
    val next: MediaItem? get() = items.getOrNull(index + 1) ?: items.firstOrNull()
    val loaded: Boolean get() = items.isNotEmpty()
}

/**
 * Spec §7.11. The set is taken as a snapshot rather than followed live: an import landing mid-pass
 * must not reshuffle the order or move the photo on screen out from under the timer.
 */
@HiltViewModel
class SlideshowViewModel @Inject constructor(
    repository: MediaRepository,
    albums: AlbumRepository,
    preferences: SlideshowPreferences,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val albumId: String? = savedState.get<String>(ARG_ALBUM)?.takeIf { it.isNotEmpty() }
    private val startId: String? = savedState.get<String>(ARG_START)?.takeIf { it.isNotEmpty() }
    private val filter = runCatching {
        MediaFilter.valueOf(savedState[ARG_FILTER] ?: MediaFilter.All.name)
    }.getOrDefault(MediaFilter.All)

    private val shuffle = preferences.shuffle
    private val loop = preferences.loop
    private val stepMillis = preferences.secondsPerPhoto * 1_000L

    private val _state = MutableStateFlow(SlideshowUiState())
    val state: StateFlow<SlideshowUiState> = _state.asStateFlow()

    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            val source = if (albumId != null) albums.media(albumId) else repository.media(filter)
            // Photos only: the pass does not start the video player halfway through (spec §7.11).
            val photos = source.first().filter { it.type == MediaType.Photo }
            if (photos.isEmpty()) {
                _state.value = SlideshowUiState(finished = true)
                return@launch
            }
            _state.value = SlideshowUiState(
                items = orderedForPass(photos, startId, shuffle),
                index = startIndexFor(photos, startId, shuffle),
            )
            resumeTicker()
        }
    }

    fun onToggleChrome() {
        _state.value = _state.value.copy(chromeVisible = !_state.value.chromeVisible)
    }

    fun onChromeHidden() {
        _state.value = _state.value.copy(chromeVisible = false)
    }

    /**
     * Spec §7.11: a swipe means «this one now», so the wait starts over and the photo it lands on
     * gets its whole turn instead of the remainder of the one before it.
     */
    fun onNext() {
        if (!_state.value.loaded) return
        advance()
        if (_state.value.playing) resumeTicker()
    }

    fun onPrevious() {
        val current = _state.value
        if (!current.loaded) return
        _state.value = current.copy(
            index = previousIndex(current.index, current.items.lastIndex, loop),
        )
        if (current.playing) resumeTicker()
    }

    fun onTogglePlay() {
        val playing = !_state.value.playing
        _state.value = _state.value.copy(playing = playing, chromeVisible = true)
        if (playing) resumeTicker() else ticker?.cancel()
    }

    /** Resuming restarts the wait, so the photo just unpaused gets its whole turn. */
    private fun resumeTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                delay(stepMillis)
                advance()
            }
        }
    }

    private fun advance() {
        val current = _state.value
        if (current.index < current.items.lastIndex) {
            _state.value = current.copy(index = current.index + 1)
            return
        }
        if (!loop) {
            ticker?.cancel()
            _state.value = current.copy(playing = false, finished = true)
            return
        }
        // Each lap is shuffled again, or the second pass would be the first one repeated (spec §7.11).
        _state.value = current.copy(
            items = if (shuffle) current.items.shuffled() else current.items,
            index = 0,
        )
    }

    companion object {
        const val ARG_ALBUM = "album"
        const val ARG_FILTER = "filter"
        const val ARG_START = "start"
    }
}
