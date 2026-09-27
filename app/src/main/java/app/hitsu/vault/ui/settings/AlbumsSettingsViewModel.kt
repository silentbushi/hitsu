package app.hitsu.vault.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hitsu.vault.data.AlbumOrder
import app.hitsu.vault.data.AlbumPreferences
import app.hitsu.vault.data.AlbumRepository
import app.hitsu.vault.data.db.AlbumWithCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which destination the album picker is being opened for, if any. */
enum class DestinationKind { Import, Download }

data class AlbumsSettingsUiState(
    val albums: List<AlbumWithCount> = emptyList(),
    val order: AlbumOrder = AlbumOrder.Alphabetical,
    val showCounts: Boolean = true,
    val importAlbum: String? = null,
    val downloadAlbum: String? = null,
    val downloadAlbumIsDefault: Boolean = true,
    val choosing: DestinationKind? = null,
    val renaming: AlbumWithCount? = null,
    val deleting: AlbumWithCount? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumsSettingsViewModel @Inject constructor(
    private val albums: AlbumRepository,
    private val preferences: AlbumPreferences,
) : ViewModel() {

    private val settings = MutableStateFlow(readSettings())
    private val choosing = MutableStateFlow<DestinationKind?>(null)
    private val renaming = MutableStateFlow<AlbumWithCount?>(null)
    private val deleting = MutableStateFlow<AlbumWithCount?>(null)

    val state: StateFlow<AlbumsSettingsUiState> = combine(
        settings,
        settings.flatMapLatest { albums.albums(it.order) },
        choosing,
        renaming,
        deleting,
    ) { current, list, choice, rename, delete ->
        current.copy(
            albums = list,
            choosing = choice,
            renaming = rename,
            deleting = delete,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), readSettings())

    fun onOrderChanged(order: AlbumOrder) {
        preferences.order = order
        settings.value = readSettings()
    }

    fun onShowCountsToggled() {
        preferences.showCounts = !preferences.showCounts
        settings.value = readSettings()
    }

    fun onChooseDestination(kind: DestinationKind) {
        choosing.value = kind
    }

    fun onDismissChoice() {
        choosing.value = null
    }

    /** A null album is «Ninguno»; for downloads that is a real choice, not "not decided yet". */
    fun onDestinationPicked(albumId: String?) {
        when (choosing.value) {
            DestinationKind.Import -> preferences.importAlbumId = albumId
            DestinationKind.Download -> preferences.downloadAlbumId = albumId
            null -> return
        }
        choosing.value = null
        settings.value = readSettings()
    }

    fun onRenameRequested(album: AlbumWithCount) {
        renaming.value = album
    }

    fun onDismissRename() {
        renaming.value = null
    }

    fun onRenamed(name: String) {
        val album = renaming.value ?: return
        renaming.value = null
        viewModelScope.launch { albums.rename(album.id, name) }
    }

    fun onDeleteRequested(album: AlbumWithCount) {
        deleting.value = album
    }

    fun onDismissDelete() {
        deleting.value = null
    }

    fun onDeleteConfirmed() {
        val album = deleting.value ?: return
        deleting.value = null
        viewModelScope.launch {
            albums.delete(album.id)
            // A destination pointing at an album that no longer exists would silently do nothing.
            preferences.forget(album.id)
            settings.value = readSettings()
        }
    }

    private fun readSettings() = AlbumsSettingsUiState(
        order = preferences.order,
        showCounts = preferences.showCounts,
        importAlbum = preferences.importAlbumId,
        downloadAlbum = preferences.downloadAlbumId,
        downloadAlbumIsDefault = preferences.downloadAlbumUnset,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
