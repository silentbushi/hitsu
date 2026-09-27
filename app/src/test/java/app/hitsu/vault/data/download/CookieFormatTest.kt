package app.hitsu.vault.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * yt-dlp reads cookies in the old Netscape layout, where the fields are separated by tabs and the
 * order is fixed. Get it wrong and yt-dlp says nothing: it just behaves as if there were no session.
 */
class CookieFormatTest {

    private val expiry = 1_800_000_000L

    @Test
    fun writesOneTabSeparatedLinePerCookie() {
        val lines = netscapeLines("x.com", "auth_token=abc; ct0=def", expiry)

        assertEquals(2, lines.size)
        assertEquals(
            listOf(".x.com", "TRUE", "/", "TRUE", "$expiry", "auth_token", "abc"),
            lines.first().split("\t"),
        )
    }

    @Test
    fun keepsValuesThatContainEqualsSigns() {
        val lines = netscapeLines("instagram.com", "sessionid=abc==xyz", expiry)

        assertEquals("abc==xyz", lines.single().split("\t").last())
    }

    @Test
    fun leadsTheDomainWithADotSoSubdomainsCount() {
        val fromBare = netscapeLines("tiktok.com", "a=1", expiry).single().split("\t").first()
        val fromDotted = netscapeLines(".tiktok.com", "a=1", expiry).single().split("\t").first()

        assertEquals(".tiktok.com", fromBare)
        assertEquals(".tiktok.com", fromDotted)
    }

    @Test
    fun ignoresFragmentsThatAreNotCookies() {
        val lines = netscapeLines("x.com", "  ; =novalue; good=1 ;", expiry)

        assertEquals(1, lines.size)
        assertTrue(lines.single().endsWith("good\t1"))
    }

    @Test
    fun emptyInputYieldsNothingToSave() {
        assertTrue(netscapeLines("x.com", "", expiry).isEmpty())
    }
}
