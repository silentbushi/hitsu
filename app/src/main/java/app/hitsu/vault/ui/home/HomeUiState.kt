package app.hitsu.vault.ui.home

import app.hitsu.vault.data.ExportStatus
import app.hitsu.vault.data.ImportStatus
import app.hitsu.vault.data.db.AlbumWithCount
import app.hitsu.vault.domain.MediaFilter

/**
 * Spec §9: the three filters of the grid plus the albums, which are not a filter but a list. Keeping
 * them in one row is what the mockup shows, so the tab is what changes, not the screen.
 */
enum class HomeTab { All, Photos, Videos, Albums }

data class HomeUiState(
    val tab: HomeTab = HomeTab.All,
    val albums: List<AlbumWithCount> = emptyList(),
    val showAlbumCounts: Boolean = true,
    val pickingAlbum: Boolean = false,
    val namingAlbum: Boolean = false,
    val filter: MediaFilter = MediaFilter.All,
    val days: List<MediaDay> = emptyList(),
    val loaded: Boolean = false,
    val importStatus: ImportStatus = ImportStatus(),
    val receivingShare: Boolean = false,
    val selection: Set<String> = emptySet(),
    val exportStatus: ExportStatus = ExportStatus(),
) {
    val showEmptyState: Boolean
        get() = tab != HomeTab.Albums && loaded && days.isEmpty() &&
            !importStatus.running && !receivingShare

    /** Long-press opens selection mode; letting go of the last item closes it again. */
    val selecting: Boolean get() = selection.isNotEmpty()

    val allIds: List<String> get() = days.flatMap { day -> day.items.map { it.id } }
}
