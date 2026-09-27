package app.hitsu.vault.crypto

import app.hitsu.vault.FakeKeyWrapper
import app.hitsu.vault.testCrypto
import app.hitsu.vault.domain.PinKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCryptoTest {

    private val crypto = testCrypto()

    private fun create(pin: String = "123456") =
        crypto.createVault(pin.toCharArray(), PinKind.Numeric, biometricRequested = true)

    @Test
    fun correctPinReturnsTheSameDek() {
        val created = create()
        val unlock = crypto.unlock("123456".toCharArray(), created.meta)
        assertTrue(unlock is VaultUnlock.Success)
        assertArrayEquals(created.dek, (unlock as VaultUnlock.Success).dek)
        assertEquals(32, created.dek.size)
    }

    @Test
    fun wrongPinIsRejected() {
        val created = create()
        assertEquals(VaultUnlock.WrongPin, crypto.unlock("654321".toCharArray(), created.meta))
    }

    @Test
    fun metaCarriesNoPlaintextKeyMaterial() {
        val created = create()
        val wrapped = created.meta.wrappedDek.decodeBase64()
        assertFalse(wrapped.asList().windowed(created.dek.size).contains(created.dek.asList()))
        assertEquals(Pbkdf2.ALGORITHM, created.meta.kdfAlgorithm)
        assertEquals(6, created.meta.pinLength)
        assertTrue(created.meta.biometricRequested)
    }

    @Test
    fun anotherDeviceKeyCannotOpenTheVault() {
        val created = create()
        val otherDevice = VaultCrypto(FakeKeyWrapper(), iterations = created.meta.kdfIterations)
        assertEquals(VaultUnlock.Unrecoverable, otherDevice.unlock("123456".toCharArray(), created.meta))
    }

    @Test
    fun dekEncryptsAndDecryptsVaultBytes() {
        val created = create()
        val cipher = VaultCipher(created.dek)
        val photo = ByteArray(64 * 1024) { (it * 7).toByte() }

        val encrypted = cipher.encrypt(photo)

        assertFalse(encrypted.asList().windowed(32).contains(photo.take(32)))
        assertArrayEquals(photo, cipher.decrypt(encrypted))
    }
}
