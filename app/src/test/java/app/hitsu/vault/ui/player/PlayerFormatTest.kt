package app.hitsu.vault.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerFormatTest {

    @Test
    fun timesReadLikeTheMockups() {
        assertEquals("00:00", formatTime(0))
        assertEquals("00:11", formatTime(11_400))
        assertEquals("03:54", formatTime(234_000))
        assertEquals("06:40", formatTime(400_000))
    }

    @Test
    fun negativePositionsClampToZero() {
        assertEquals("00:00", formatTime(-5_000))
    }

    @Test
    fun seekDeltasCarryTheirSign() {
        assertEquals("+00:18", formatDelta(18_000))
        assertEquals("-00:18", formatDelta(-18_000))
    }

    @Test
    fun speedsDropTheTrailingZero() {
        assertEquals(listOf("0.5", "0.75", "1", "1.25", "1.5", "2"), SPEEDS.map(::formatSpeed))
    }
}
