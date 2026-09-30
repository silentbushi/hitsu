package app.hitsu.vault.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import app.hitsu.vault.R
import app.hitsu.vault.data.SlideshowPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** How long each photo stays on screen (spec §7.11). */
enum class SlideshowInterval(val seconds: Int, @StringRes val label: Int) {
    ThreeSeconds(3, R.string.slideshow_3s),
    FiveSeconds(5, R.string.slideshow_5s),
    TenSeconds(10, R.string.slideshow_10s),
    ThirtySeconds(30, R.string.slideshow_30s),
    OneMinute(60, R.string.slideshow_1m),
    ;

    companion object {
        fun of(seconds: Int): SlideshowInterval =
            entries.firstOrNull { it.seconds == seconds } ?: FiveSeconds
    }
}

data class SlideshowSettingsUiState(
    val interval: SlideshowInterval = SlideshowInterval.FiveSeconds,
    val shuffle: Boolean = false,
    val loop: Boolean = false,
)

@HiltViewModel
class SlideshowSettingsViewModel @Inject constructor(
    private val preferences: SlideshowPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SlideshowSettingsUiState(
            interval = SlideshowInterval.of(preferences.secondsPerPhoto),
            shuffle = preferences.shuffle,
            loop = preferences.loop,
        ),
    )
    val state: StateFlow<SlideshowSettingsUiState> = _state.asStateFlow()

    fun onIntervalPicked(interval: SlideshowInterval) {
        preferences.secondsPerPhoto = interval.seconds
        _state.update { it.copy(interval = interval) }
    }

    fun onShuffleToggled() {
        val shuffle = !_state.value.shuffle
        preferences.shuffle = shuffle
        _state.update { it.copy(shuffle = shuffle) }
    }

    fun onLoopToggled() {
        val loop = !_state.value.loop
        preferences.loop = loop
        _state.update { it.copy(loop = loop) }
    }
}
