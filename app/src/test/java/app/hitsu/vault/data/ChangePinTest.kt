package app.hitsu.vault.data

import app.hitsu.vault.FakeBiometricKey
import app.hitsu.vault.InMemoryVaultMetaStore
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.UnlockResult
import app.hitsu.vault.testCrypto
import app.hitsu.vault.testGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §5.1: a new PIN rewraps the key, it does not re-encrypt the vault. The test that matters is
 * therefore not that the new PIN works, but that what was sealed before the change still opens after
 * it — including through a fingerprint, whose sealed copy the change never touches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangePinTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock { 0L }
    private val store = InMemoryVaultMetaStore()
    private val crypto = testCrypto()
    private val biometricKey = FakeBiometricKey()

    private suspend fun TestScope.gateway() =
        testGateway(this, dispatcher, clock, store, crypto, biometricKey)

    @Test
    fun whatWasEncryptedBeforeOpensAfter() = runTest(dispatcher) {
        val vault = gateway()
        vault.create(OLD.toCharArray(), biometricRequested = false)
        val sealed = vault.cipher!!.encrypt(SECRET)

        assertTrue(vault.changePin(OLD.toCharArray(), NEW.toCharArray()))
        vault.lock()

        assertEquals(UnlockResult.Success, vault.unlock(NEW.toCharArray()))
        assertArrayEquals(SECRET, vault.cipher!!.decrypt(sealed))
    }

    @Test
    fun theOldPinStopsWorking() = runTest(dispatcher) {
        val vault = gateway()
        vault.create(OLD.toCharArray(), biometricRequested = false)
        vault.changePin(OLD.toCharArray(), NEW.toCharArray())
        vault.lock()

        assertTrue(vault.unlock(OLD.toCharArray()) is UnlockResult.Rejected)
    }

    @Test
    fun aWrongCurrentPinChangesNothing() = runTest(dispatcher) {
        val vault = gateway()
        vault.create(OLD.toCharArray(), biometricRequested = false)
        val before = store.read()

        assertFalse(vault.changePin("999999".toCharArray(), NEW.toCharArray()))

        assertEquals(before, store.read())
        vault.lock()
        assertEquals(UnlockResult.Success, vault.unlock(OLD.toCharArray()))
    }

    @Test
    fun theFingerprintStillOpensIt() = runTest(dispatcher) {
        val vault = gateway()
        vault.create(OLD.toCharArray(), biometricRequested = true)
        vault.enableBiometric(biometricKey.encryptCipher())
        val sealed = vault.cipher!!.encrypt(SECRET)

        vault.changePin(OLD.toCharArray(), NEW.toCharArray())
        vault.lock()

        assertTrue(vault.unlockWithBiometric(vault.biometricUnlockCipher()!!))
        assertArrayEquals(SECRET, vault.cipher!!.decrypt(sealed))
    }

    @Test
    fun aNewPinOfAnotherLengthIsRemembered() = runTest(dispatcher) {
        val vault = gateway()
        vault.create(OLD.toCharArray(), biometricRequested = false)

        assertTrue(vault.changePin(OLD.toCharArray(), LONGER.toCharArray()))

        assertEquals(LONGER.length, vault.pinLength)
    }

    private companion object {
        const val OLD = "123456"
        const val NEW = "654321"
        const val LONGER = "12345678"
        val SECRET = "una foto".toByteArray()
    }
}
