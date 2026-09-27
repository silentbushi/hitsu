package app.hitsu.vault.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a copy is called and what format it is in, which is the part of exporting that can be checked
 * without a gallery to write into.
 */
class ExportNamingTest {

    @Test
    fun aHeicLeavesAsJpeg() {
        assertEquals("image/jpeg", exportMime("image/heic"))
        assertEquals("image/jpeg", exportMime("image/heif"))
        assertEquals("IMG_0042.jpg", exportName("IMG_0042.heic", ID, exportMime("image/heic")))
    }

    @Test
    fun everythingElseKeepsItsFormat() {
        assertEquals("image/jpeg", exportMime("image/jpeg"))
        assertEquals("video/mp4", exportMime("video/mp4"))
        assertEquals("VID_0007.mp4", exportName("VID_0007.mp4", ID, "video/mp4"))
    }

    @Test
    fun whatArrivedWithoutANameIsCalledAfterItsItem() {
        assertEquals("hitsu-0123abcd.mp4", exportName(null, ID, "video/mp4"))
        assertEquals("hitsu-0123abcd.jpg", exportName("   ", ID, "image/jpeg"))
    }

    @Test
    fun aNameWithDotsInsideKeepsThemAll() {
        assertEquals("foto.de.ayer.jpg", exportName("foto.de.ayer.jpeg", ID, "image/jpeg"))
    }

    private companion object {
        const val ID = "0123abcd-dead-beef"
    }
}
