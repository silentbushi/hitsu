package app.hitsu.vault.crypto

import app.hitsu.vault.TEST_KDF_ITERATIONS
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class Pbkdf2Test {

    private val salt = Pbkdf2.newSalt()

    @Test
    fun derivesA256BitKeyDeterministically() {
        val first = Pbkdf2.derive("123456".toCharArray(), salt, TEST_KDF_ITERATIONS)
        val second = Pbkdf2.derive("123456".toCharArray(), salt, TEST_KDF_ITERATIONS)
        assertEquals(32, first.size)
        assertArrayEquals(first, second)
    }

    @Test
    fun differentPinSaltOrIterationsChangeTheKey() {
        val key = Pbkdf2.derive("123456".toCharArray(), salt, TEST_KDF_ITERATIONS)
        assertNotEquals(
            key.toList(),
            Pbkdf2.derive("123457".toCharArray(), salt, TEST_KDF_ITERATIONS).toList(),
        )
        assertNotEquals(
            key.toList(),
            Pbkdf2.derive("123456".toCharArray(), Pbkdf2.newSalt(), TEST_KDF_ITERATIONS).toList(),
        )
        assertNotEquals(
            key.toList(),
            Pbkdf2.derive("123456".toCharArray(), salt, TEST_KDF_ITERATIONS + 1).toList(),
        )
    }

    @Test
    fun saltsAreRandomAndSized() {
        assertEquals(Pbkdf2.SALT_BYTES, salt.size)
        assertNotEquals(salt.toList(), Pbkdf2.newSalt().toList())
    }
}
