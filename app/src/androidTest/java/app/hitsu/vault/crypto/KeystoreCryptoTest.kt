package app.hitsu.vault.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hitsu.vault.domain.PinKind
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.security.KeyStore

/** The only test that exercises the real Android Keystore; needs a device or emulator. */
@RunWith(AndroidJUnit4::class)
class KeystoreCryptoTest {

    private val alias = "hitsu.test.${System.nanoTime()}"
    private val wrapper = KeystoreKeyWrapper(alias)

    @After
    fun deleteKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun wrapsAndUnwrapsWithANonExportableKey() {
        val secret = AesGcm.newKey()

        val wrapped = wrapper.wrap(secret)

        assertArrayEquals(secret, wrapper.unwrap(wrapped))
        val entry = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null)
        assertEquals(null, entry.encoded)
    }

    @Test
    fun anotherAliasCannotUnwrap() {
        val wrapped = wrapper.wrap(AesGcm.newKey())
        val other = KeystoreKeyWrapper("$alias.other")
        try {
            assertThrows(GeneralSecurityException::class.java) { other.unwrap(wrapped) }
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("$alias.other")
        }
    }

    @Test
    fun vaultOpensWithTheRealKeystore() {
        val crypto = VaultCrypto(wrapper, iterations = Pbkdf2.DEFAULT_ITERATIONS)
        val created = crypto.createVault("123456".toCharArray(), PinKind.Numeric, biometricRequested = false)

        val unlock = crypto.unlock("123456".toCharArray(), created.meta)

        assertTrue(unlock is VaultUnlock.Success)
        assertArrayEquals(created.dek, (unlock as VaultUnlock.Success).dek)
        assertEquals(VaultUnlock.WrongPin, crypto.unlock("999999".toCharArray(), created.meta))
    }
}
