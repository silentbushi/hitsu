package app.hitsu.vault.ui.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hitsu.vault.data.AlbumCreation
import app.hitsu.vault.data.AlbumPreferences
import app.hitsu.vault.data.AlbumRepository
import app.hitsu.vault.data.db.AlbumWithCount
import app.hitsu.vault.ui.home.MediaDay
import app.hitsu.vault.ui.home.groupByDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumUiState(
    val albumId: String = "",
    val name: String = "",
    val days: List<MediaDay> = emptyList(),
    val selection: Set<String> = emptySet(),
    val albums: List<AlbumWithCount> = emptyList(),
    val pickingAlbum: Boolean = false,
) {
    val allIds: List<String> get() = days.flatMap { day -> day.items.map { it.id } }
}

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val albums: AlbumRepository,
    preferences: AlbumPreferences,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val albumId: String = checkNotNull(savedState[ARG_ID])
    private val selection = MutableStateFlow(emptySet<String>())
    private val picking = MutableStateFlow(false)

    val state: StateFlow<AlbumUiState> = combine(
        albums.album(albumId),
        albums.media(albumId),
        albums.albums(preferences.order),
        selection,
        picking,
    ) { album, items, all, chosen, pickingAlbum ->
        val days = groupByDay(items)
        AlbumUiState(
            albumId = albumId,
            name = album?.name.orEmpty(),
            days = days,
            // What left the album cannot stay selected out of sight.
            selection = chosen intersect days.flatMap { day -> day.items.map { it.id } }.toSet(),
            albums = all,
            pickingAlbum = pickingAlbum,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AlbumUiState(albumId = albumId))

    fun onToggleSelected(id: String) {
        selection.value = selection.value.let { if (id in it) it - id else it + id }
    }

    fun onSelectAll(ids: List<String>) {
        selection.value = ids.toSet()
    }

    fun onClearSelection() {
        selection.value = emptySet()
    }

    fun onOpenPicker() {
        picking.value = true
    }

    fun onDismissPicker() {
        picking.value = false
    }

    fun onAddToAlbum(targetId: String) {
        val chosen = selection.value.toList()
        picking.value = false
        viewModelScope.launch {
            albums.add(targetId, chosen)
            selection.value = emptySet()
        }
    }

    fun onCreateAlbum(name: String) {
        val chosen = selection.value.toList()
        picking.value = false
        viewModelScope.launch {
            val target = when (val created = albums.create(name)) {
                is AlbumCreation.Created -> created.id
                is AlbumCreation.NameTaken -> created.id
                AlbumCreation.Invalid -> return@launch
            }
            albums.add(target, chosen)
            selection.value = emptySet()
        }
    }

    /** Spec §9: this takes them out of the album and nowhere else; the vault keeps them. */
    fun onRemoveFromAlbum() {
        val chosen = selection.value.toList()
        viewModelScope.launch {
            albums.remove(albumId, chosen)
            selection.value = emptySet()
        }
    }

    companion object {
        const val ARG_ID = "id"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
