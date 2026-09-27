package app.hitsu.vault.ui.download

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hitsu.vault.R
import app.hitsu.vault.data.download.DownloadCoordinator
import app.hitsu.vault.data.download.DownloadPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.net.URLDecoder
import javax.inject.Inject

data class DownloadUiState(
    val url: String = "",
    val title: String? = null,
    val source: String? = null,
    val phase: DownloadPhase = DownloadPhase.Preparing,
) {
    val detail: String? get() = (phase as? DownloadPhase.Failed)?.detail

    val failed: Boolean get() = phase is DownloadPhase.Failed

    @get:StringRes
    val status: Int
        get() = when (val current = phase) {
            DownloadPhase.Idle, DownloadPhase.Preparing -> R.string.download_reading
            is DownloadPhase.Ready -> R.string.download_ready
            is DownloadPhase.Running -> R.string.download_running
            DownloadPhase.Importing -> R.string.download_importing
            DownloadPhase.Done -> R.string.download_done
            is DownloadPhase.Failed -> when (current.reason) {
                DownloadPhase.Reason.Unsupported -> R.string.download_failed_unsupported
                DownloadPhase.Reason.Network -> R.string.download_failed_network
                DownloadPhase.Reason.Locked -> R.string.download_failed_locked
                DownloadPhase.Reason.Storage -> R.string.download_failed_storage
            }
        }
}

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val coordinator: DownloadCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val url: String =
        URLDecoder.decode(checkNotNull(savedStateHandle.get<String>(ARG_URL)), Charsets.UTF_8.name())

    val state: StateFlow<DownloadUiState> = coordinator.phase
        .map { phase ->
            val ready = phase as? DownloadPhase.Ready
            DownloadUiState(
                url = url,
                title = ready?.media?.title,
                source = ready?.media?.extractor,
                phase = phase,
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            DownloadUiState(url = url),
        )

    init {
        coordinator.resolve(url)
    }

    fun onStart() = coordinator.start(url)

    fun onCancel() = coordinator.cancel()

    fun onFinished() = coordinator.reset()

    companion object {
        const val ARG_URL = "url"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
