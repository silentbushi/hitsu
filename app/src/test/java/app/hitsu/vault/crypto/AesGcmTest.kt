package app.hitsu.vault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException

class AesGcmTest {

    private val key = AesGcm.newKey()
    private val plaintext = "cofre".toByteArray() + ByteArray(4096) { it.toByte() }

    @Test
    fun roundTripsBytes() {
        assertArrayEquals(plaintext, AesGcm.decrypt(key, AesGcm.encrypt(key, plaintext)))
    }

    @Test
    fun ciphertextHidesPlaintextAndNeverRepeats() {
        val first = AesGcm.encrypt(key, plaintext)
        val second = AesGcm.encrypt(key, plaintext)
        assertNotEquals(first.toList(), second.toList())
        assertFalse(first.asList().windowed(5).contains("cofre".toByteArray().asList()))
    }

    @Test
    fun rejectsTamperedCiphertext() {
        val payload = AesGcm.encrypt(key, plaintext)
        payload[payload.size - 1] = (payload[payload.size - 1] + 1).toByte()
        assertThrows(GeneralSecurityException::class.java) { AesGcm.decrypt(key, payload) }
    }

    @Test
    fun rejectsWrongKey() {
        val payload = AesGcm.encrypt(key, plaintext)
        assertThrows(GeneralSecurityException::class.java) { AesGcm.decrypt(AesGcm.newKey(), payload) }
    }

    @Test
    fun keysAre256Bit() {
        assertEquals(32, AesGcm.newKey().size)
    }
}
