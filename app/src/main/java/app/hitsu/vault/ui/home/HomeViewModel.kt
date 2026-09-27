package app.hitsu.vault.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hitsu.vault.data.AlbumCreation
import app.hitsu.vault.data.AlbumPreferences
import app.hitsu.vault.data.AlbumRepository
import app.hitsu.vault.data.MediaRepository
import app.hitsu.vault.domain.AutoLock
import app.hitsu.vault.domain.MediaFilter
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val albums: AlbumRepository,
    private val preferences: AlbumPreferences,
    private val autoLock: AutoLock,
) : ViewModel() {

    private val tab = MutableStateFlow(HomeTab.All)
    private val filter = MutableStateFlow(MediaFilter.All)
    private val selection = MutableStateFlow(emptySet<String>())
    private val picking = MutableStateFlow(false)

    init {
        repository.ensureFingerprints()
        // Reaching home means the vault is open, which is what anything shared was waiting for.
        repository.importShared()
    }

    /** Grouped because combine only goes up to five flows. */
    private val work = combine(
        repository.importStatus,
        repository.sharing,
        repository.exportStatus,
    ) { importStatus, sharing, exportStatus -> Triple(importStatus, sharing, exportStatus) }

    private val albumState = combine(
        tab,
        albums.albums(preferences.order),
        picking,
    ) { current, list, pickingAlbum -> Triple(current, list, pickingAlbum) }

    val state: StateFlow<HomeUiState> = combine(
        filter,
        filter.flatMapLatest { repository.media(it) },
        selection,
        work,
        albumState,
    ) { selected, items, chosen, (importStatus, sharing, exportStatus), (current, list, pickingAlbum) ->
        val days = groupByDay(items)
        HomeUiState(
            tab = current,
            albums = list,
            showAlbumCounts = preferences.showCounts,
            pickingAlbum = pickingAlbum,
            filter = selected,
            days = days,
            loaded = true,
            importStatus = importStatus,
            receivingShare = sharing,
            // What was deleted or filtered out cannot stay selected behind the scenes.
            selection = chosen intersect days.flatMap { day -> day.items.map { it.id } }.toSet(),
            exportStatus = exportStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    fun onTabSelected(selected: HomeTab) {
        tab.value = selected
        // Albums are a list, not a filter: the grid keeps whatever it was showing underneath.
        when (selected) {
            HomeTab.All -> filter.value = MediaFilter.All
            HomeTab.Photos -> filter.value = MediaFilter.Photos
            HomeTab.Videos -> filter.value = MediaFilter.Videos
            HomeTab.Albums -> Unit
        }
        if (selected == HomeTab.Albums) selection.value = emptySet()
    }

    /** The picker backgrounds the app; without this the vault would lock before it returns. */
    fun onPickerLaunching() = autoLock.allowNextBackground()

    fun onPicked(uris: List<Uri>) = repository.import(uris)

    fun onToggleSelected(id: String) {
        selection.value = selection.value.let { if (id in it) it - id else it + id }
    }

    fun onSelectAll(ids: List<String>) {
        selection.value = ids.toSet()
    }

    fun onClearSelection() {
        selection.value = emptySet()
    }

    fun onExportSelected() {
        repository.export(selection.value.toList())
        selection.value = emptySet()
    }

    fun onDeleteSelected() {
        repository.delete(selection.value.toList())
        selection.value = emptySet()
    }

    fun onExportStatusSeen() = repository.clearExportStatus()

    fun onOpenPicker() {
        picking.value = true
    }

    fun onDismissPicker() {
        picking.value = false
    }

    fun onAddToAlbum(albumId: String) {
        val chosen = selection.value.toList()
        picking.value = false
        viewModelScope.launch {
            albums.add(albumId, chosen)
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
