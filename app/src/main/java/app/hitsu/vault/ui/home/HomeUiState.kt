package app.hitsu.vault.ui.home

import app.hitsu.vault.data.ExportStatus
import app.hitsu.vault.data.ImportStatus
import app.hitsu.vault.domain.MediaFilter

data class HomeUiState(
    val filter: MediaFilter = MediaFilter.All,
    val days: List<MediaDay> = emptyList(),
    val loaded: Boolean = false,
    val importStatus: ImportStatus = ImportStatus(),
    val receivingShare: Boolean = false,
    val selection: Set<String> = emptySet(),
    val exportStatus: ExportStatus = ExportStatus(),
) {
    val showEmptyState: Boolean
        get() = loaded && days.isEmpty() && !importStatus.running && !receivingShare

    /** Long-press opens selection mode; letting go of the last item closes it again. */
    val selecting: Boolean get() = selection.isNotEmpty()

    val allIds: List<String> get() = days.flatMap { day -> day.items.map { it.id } }
}
