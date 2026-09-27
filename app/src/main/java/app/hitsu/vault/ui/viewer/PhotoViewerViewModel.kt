package app.hitsu.vault.ui.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hitsu.vault.data.MediaRepository
import app.hitsu.vault.domain.MediaFilter
import app.hitsu.vault.domain.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PhotoViewerUiState(
    val items: List<MediaItem> = emptyList(),
    val startIndex: Int = 0,
    val chromeVisible: Boolean = true,
    val infoVisible: Boolean = false,
) {
    val loaded: Boolean get() = items.isNotEmpty()
}

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    repository: MediaRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mediaId: String = checkNotNull(savedStateHandle[ARG_ID])
    private val filter = runCatching {
        MediaFilter.valueOf(savedStateHandle[ARG_FILTER] ?: MediaFilter.All.name)
    }.getOrDefault(MediaFilter.All)

    private val chromeVisible = MutableStateFlow(true)
    private val infoVisible = MutableStateFlow(false)

    /** Where the pager opens. Kept from the first load so a later import cannot shift the page. */
    private var startIndex: Int? = null

    val state: StateFlow<PhotoViewerUiState> = combine(
        repository.media(filter),
        chromeVisible,
        infoVisible,
    ) { items, chrome, info ->
        if (startIndex == null && items.isNotEmpty()) {
            startIndex = items.indexOfFirst { it.id == mediaId }.coerceAtLeast(0)
        }
        PhotoViewerUiState(
            items = items,
            startIndex = startIndex ?: 0,
            chromeVisible = chrome,
            infoVisible = info,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PhotoViewerUiState())

    fun onToggleChrome() {
        val visible = !chromeVisible.value
        chromeVisible.value = visible
        if (!visible) infoVisible.value = false
    }

    fun onToggleInfo() {
        infoVisible.value = !infoVisible.value
    }

    companion object {
        const val ARG_ID = "id"
        const val ARG_FILTER = "filter"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
