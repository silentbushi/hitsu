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

/**
 * The grid of each tab, grouped by day. All three are kept because a swipe has two tabs on screen at
 * once; they are derived from the same list rather than queried apart, so what the filters show can
 * never drift from what Todos shows.
 */
class MediaPages(
    val all: List<MediaDay> = emptyList(),
    val photos: List<MediaDay> = emptyList(),
    val videos: List<MediaDay> = emptyList(),
) {
    operator fun get(tab: HomeTab): List<MediaDay> = when (tab) {
        HomeTab.All -> all
        HomeTab.Photos -> photos
        HomeTab.Videos -> videos
        HomeTab.Albums -> emptyList()
    }
}

data class HomeUiState(
    val tab: HomeTab = HomeTab.All,
    val pages: MediaPages = MediaPages(),
    val albums: List<AlbumWithCount> = emptyList(),
    val showAlbumCounts: Boolean = true,
    val pickingAlbum: Boolean = false,
    val namingAlbum: Boolean = false,
    val days: List<MediaDay> = emptyList(),
    val loaded: Boolean = false,
    val importStatus: ImportStatus = ImportStatus(),
    val receivingShare: Boolean = false,
    val selection: Set<String> = emptySet(),
    val exportStatus: ExportStatus = ExportStatus(),
) {
    /** Which list the viewer should swipe through when something is opened from this tab. */
    val filter: MediaFilter
        get() = when (tab) {
            HomeTab.Photos -> MediaFilter.Photos
            HomeTab.Videos -> MediaFilter.Videos
            HomeTab.All, HomeTab.Albums -> MediaFilter.All
        }

    fun showEmptyState(tab: HomeTab): Boolean =
        loaded && pages[tab].isEmpty() && !importStatus.running && !receivingShare

    /** Long-press opens selection mode; letting go of the last item closes it again. */
    val selecting: Boolean get() = selection.isNotEmpty()

    val allIds: List<String> get() = days.flatMap { day -> day.items.map { it.id } }
}
