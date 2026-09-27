package app.hitsu.vault.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Apps wrap the link in a sentence; what reaches yt-dlp has to be the address and nothing else. */
class PendingLinksTest {

    private val links = PendingLinks()

    private fun offered(text: String): String? {
        links.offer(text)
        return links.link.value
    }

    @Test
    fun takesTheLinkOutOfASentence() {
        assertEquals(
            "https://vm.tiktok.com/ZMabc123/",
            offered("Mira esto https://vm.tiktok.com/ZMabc123/ está buenísimo"),
        )
    }

    @Test
    fun dropsTrailingPunctuationThatIsNotPartOfTheAddress() {
        assertEquals(
            "https://x.com/alguien/status/123",
            offered("qué opinas de (https://x.com/alguien/status/123)."),
        )
        assertEquals(
            "https://www.instagram.com/reel/AbC/",
            offered("\"https://www.instagram.com/reel/AbC/\""),
        )
    }

    @Test
    fun keepsTheQueryStringWhichCarriesTheIdentifier() {
        assertEquals(
            "https://x.com/i/status/1234567890?s=46&t=xyz",
            offered("https://x.com/i/status/1234567890?s=46&t=xyz"),
        )
    }

    @Test
    fun ignoresTextWithoutALink() {
        assertNull(offered("mira esta foto que te mandé"))
    }

    @Test
    fun takeClearsIt() {
        offered("https://vm.tiktok.com/ZMabc123/")

        assertEquals("https://vm.tiktok.com/ZMabc123/", links.take())
        assertNull(links.link.value)
    }
}
