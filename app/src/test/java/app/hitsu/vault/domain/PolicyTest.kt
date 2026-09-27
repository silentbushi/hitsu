package app.hitsu.vault.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {

    @Test
    fun pinLengthBounds() {
        assertFalse(PinPolicy.isValid(5))
        assertTrue(PinPolicy.isValid(6))
        assertTrue(PinPolicy.isValid(12))
        assertFalse(PinPolicy.isValid(13))
    }

    @Test
    fun noDelayBeforeMaxAttempts() {
        (0 until UnlockThrottle.MAX_ATTEMPTS).forEach { assertEquals(0L, UnlockThrottle.delayAfter(it)) }
    }

    @Test
    fun delayDoublesAfterMaxAttemptsAndIsCapped() {
        assertEquals(30_000L, UnlockThrottle.delayAfter(8))
        assertEquals(60_000L, UnlockThrottle.delayAfter(9))
        assertEquals(120_000L, UnlockThrottle.delayAfter(10))
        assertEquals(3_600_000L, UnlockThrottle.delayAfter(40))
    }
}
