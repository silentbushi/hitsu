package app.hitsu.vault.ui.home

import app.hitsu.vault.domain.MediaItem
import app.hitsu.vault.domain.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MediaDayTest {

    private val zone = ZoneId.of("America/Mexico_City")

    private fun item(id: String, takenAt: Long?, importedAt: Long = 0L) = MediaItem(
        id = id,
        type = MediaType.Photo,
        mime = "image/jpeg",
        width = 100,
        height = 100,
        durationMs = null,
        sizeBytes = 1_000,
        takenAt = takenAt,
        importedAt = importedAt,
        originalName = null,
    )

    private fun atNoon(date: String): Long =
        LocalDate.parse(date).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun groupsConsecutiveItemsOfTheSameDay() {
        val days = groupByDay(
            listOf(
                item("a", atNoon("2026-09-21")),
                item("b", atNoon("2026-09-21")),
                item("c", atNoon("2026-09-18")),
            ),
            zone,
        )

        assertEquals(2, days.size)
        assertEquals(LocalDate.parse("2026-09-21"), days[0].date)
        assertEquals(listOf("a", "b"), days[0].items.map { it.id })
        assertEquals(listOf("c"), days[1].items.map { it.id })
    }

    @Test
    fun fallsBackToImportDateWhenExifHasNone() {
        val days = groupByDay(listOf(item("a", takenAt = null, importedAt = atNoon("2026-09-09"))), zone)

        assertEquals(LocalDate.parse("2026-09-09"), days.single().date)
    }

    @Test
    fun chunksEachDayIntoGridRows() {
        val items = (1..7).map { item("id$it", atNoon("2026-09-21")) }

        val rows = groupByDay(items, zone).single().rows

        assertEquals(listOf(3, 3, 1), rows.map { it.size })
        assertEquals(GRID_COLUMNS, 3)
    }
}
