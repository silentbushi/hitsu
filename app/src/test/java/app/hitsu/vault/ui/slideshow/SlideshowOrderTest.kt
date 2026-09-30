package app.hitsu.vault.ui.slideshow

import app.hitsu.vault.domain.MediaItem
import app.hitsu.vault.domain.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class SlideshowOrderTest {

    private fun item(id: String) = MediaItem(
        id = id,
        type = MediaType.Photo,
        mime = "image/jpeg",
        width = 100,
        height = 100,
        durationMs = null,
        sizeBytes = 1_000,
        takenAt = null,
        importedAt = 0L,
        originalName = null,
    )

    private val photos = listOf("a", "b", "c", "d", "e").map(::item)

    @Test
    fun inOrderThePassKeepsTheGalleryOrder() {
        assertEquals(photos, orderedForPass(photos, startId = "c", shuffle = false))
    }

    @Test
    fun inOrderThePassStartsWhereTheViewerWas() {
        assertEquals(2, startIndexFor(photos, startId = "c", shuffle = false))
    }

    /** A photo that is no longer there (deleted, or another filter) must not send the pass nowhere. */
    @Test
    fun anUnknownStartFallsBackToTheBeginning() {
        assertEquals(0, startIndexFor(photos, startId = "zz", shuffle = false))
        assertEquals(0, startIndexFor(photos, startId = null, shuffle = false))
    }

    @Test
    fun shuffledThePassStartsOnTheViewersPhotoAndKeepsTheRest() {
        val ordered = orderedForPass(photos, startId = "d", shuffle = true)

        assertEquals("d", ordered.first().id)
        assertEquals(photos.size, ordered.size)
        assertEquals(photos.map { it.id }.toSet(), ordered.map { it.id }.toSet())
        // Shuffled, the first photo is index 0 by construction, not by searching for it.
        assertEquals(0, startIndexFor(photos, startId = "d", shuffle = true))
    }

    @Test
    fun swipingBackWrapsOnlyWhenThePassLoops() {
        assertEquals(2, previousIndex(index = 3, lastIndex = 4, loop = false))
        assertEquals(4, previousIndex(index = 0, lastIndex = 4, loop = true))
        assertEquals(0, previousIndex(index = 0, lastIndex = 4, loop = false))
    }

    @Test
    fun shuffledWithoutAStartKeepsEveryPhotoExactlyOnce() {
        val ordered = orderedForPass(photos, startId = null, shuffle = true)

        assertEquals(photos.size, ordered.size)
        assertEquals(photos.map { it.id }.toSet(), ordered.map { it.id }.toSet())
    }
}
