package app.hitsu.vault.ui.home

import app.hitsu.vault.domain.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val GRID_COLUMNS = 3

data class MediaDay(val date: LocalDate, val items: List<MediaItem>) {
    /** Pre-chunked so the grid can live inside a LazyColumn that supports sticky headers. */
    val rows: List<List<MediaItem>> = items.chunked(GRID_COLUMNS)
}

/** Spec §9: one sticky header per day, keeping the order the query already produced. */
fun groupByDay(items: List<MediaItem>, zone: ZoneId = ZoneId.systemDefault()): List<MediaDay> =
    items
        .groupBy { Instant.ofEpochMilli(it.sortedAt).atZone(zone).toLocalDate() }
        .map { (date, dayItems) -> MediaDay(date, dayItems) }
