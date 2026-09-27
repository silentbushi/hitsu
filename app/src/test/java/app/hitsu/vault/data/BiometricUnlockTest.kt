package app.hitsu.vault.data

import app.hitsu.vault.FakeBiometricKey
import app.hitsu.vault.InMemoryVaultMetaStore
import app.hitsu.vault.testCrypto
import app.hitsu.vault.testGateway
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.VaultState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §5.2: a fingerprint opens the vault by authorising a key that already holds the DEK, not by
 * deriving anything. What matters here is that the copy it holds is the same DEK — otherwise the two
 * ways in would open different vaults — and that turning it off really takes that copy away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BiometricUnlockTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock { 0L }
    private val store = InMemoryVaultMetaStore()
    private val crypto = testCrypto()
    private val biometricKey = FakeBiometricKey()

    private suspend fun TestScope.gateway() =
        testGateway(this, dispatcher, clock, store, crypto, biometricKey)

    @Test
    fun aFingerprintOpensTheSameVaultAsThePin() = runTest(dispatcher) {
        val vault = gateway()
        vault.create(PIN.toCharArray(), biometricRequested = true)
        val byPin = vault.cipher!!.encrypt(SECRET)

        assertTrue(vault.enableBiometric(biometricKey.encryptCipher()))
        assertTrue(vault.biometricEnabled)
        vault.lock()

        assertTrue(vault.unlockWithBiometric(vault.biometricUnlockCipher()!!))
        assertEquals(VaultState.Unlocked, vault.state.value)
        assertArrayEquals(SECRET, vault.cipher!!.decrypt(byPin))
    }

    @Test
    fun withoutItEnabledThereIsNothingToUnlockWith() = runTest(dispatcher) {
        val vault = gateway()
        vault.create(PIN.toCharArray(), biometricRequested = false)
        vault.lock()

        assertFalse(vault.biometricEnabled)
        assertNull(vault.biometricUnlockCipher())
    }

    @Test
    fun turningItOffTakesTheSealedCopyAway() = runTest(dispatcher) {
        val vault = gateway()
        vault.create(PIN.toCharArray(), biometricRequested = true)
        vault.enableBiometric(biometricKey.encryptCipher())
        assertNotNull(store.read()!!.biometricDek)

        vault.forgetBiometric()

        assertFalse(vault.biometricEnabled)
        assertNull(store.read()!!.biometricDek)
        assertFalse(biometricKey.exists())
    }

    @Test
    fun enablingItAgainReplacesTheOldKey() = runTest(dispatcher) {
        val vault = gateway()
        vault.create(PIN.toCharArray(), biometricRequested = true)
        vault.enableBiometric(biometricKey.encryptCipher())
        val first = store.read()!!.biometricDek

        vault.enableBiometric(biometricKey.encryptCipher())

        assertTrue(first != store.read()!!.biometricDek)
    }

    @Test
    fun aClosedVaultHasNoDekToSeal() = runTest(dispatcher) {
        val vault = gateway()
        vault.create(PIN.toCharArray(), biometricRequested = true)
        vault.lock()

        assertFalse(vault.enableBiometric(biometricKey.encryptCipher()))
    }

    private companion object {
        const val PIN = "123456"
        val SECRET = "una foto".toByteArray()
    }
}
