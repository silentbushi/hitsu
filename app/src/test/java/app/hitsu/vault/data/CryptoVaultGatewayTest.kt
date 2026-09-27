package app.hitsu.vault.data

import app.hitsu.vault.FakeKeyWrapper
import app.hitsu.vault.InMemoryVaultMetaStore
import app.hitsu.vault.TEST_KDF_ITERATIONS
import app.hitsu.vault.crypto.VaultCrypto
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.UnlockResult
import app.hitsu.vault.domain.VaultState
import app.hitsu.vault.testCrypto
import app.hitsu.vault.testGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CryptoVaultGatewayTest {

    private val dispatcher = StandardTestDispatcher()
    private var now = 0L
    private val clock = Clock { now }
    private val store = InMemoryVaultMetaStore()

    /** Shared by default: a new [testCrypto] would mean a new device key, i.e. a wiped Keystore. */
    private val crypto = testCrypto()

    private suspend fun TestScope.gateway(crypto: VaultCrypto = this@CryptoVaultGatewayTest.crypto) =
        testGateway(this, dispatcher, clock, store, crypto)

    @Test
    fun startsAbsentAndBecomesUnlockedOnCreate() = runTest(dispatcher) {
        val vault = gateway()
        assertEquals(VaultState.Absent, vault.state.value)

        vault.create("123456".toCharArray(), biometricRequested = true)

        assertEquals(VaultState.Unlocked, vault.state.value)
        assertEquals(6, vault.pinLength)
        assertNotNull(vault.cipher)
        assertNotNull(store.meta)
    }

    @Test
    fun existingVaultStartsLockedAndOpensWithTheSamePin() = runTest(dispatcher) {
        val payload = "foto".toByteArray()
        val first = gateway()
        first.create("123456".toCharArray(), biometricRequested = false)
        val encrypted = first.cipher!!.encrypt(payload)

        // A fresh process: same store, same device key, new gateway.
        val second = gateway()
        assertEquals(VaultState.Locked, second.state.value)
        assertNull(second.cipher)

        assertEquals(UnlockResult.Success, second.unlock("123456".toCharArray()))
        assertEquals(VaultState.Unlocked, second.state.value)
        assertArrayEquals(payload, second.cipher!!.decrypt(encrypted))
    }

    @Test
    fun lockClosesTheSession() = runTest(dispatcher) {
        val vault = gateway()
        vault.create("123456".toCharArray(), biometricRequested = false)

        vault.lock()

        assertEquals(VaultState.Locked, vault.state.value)
        assertNull(vault.cipher)
    }

    @Test
    fun failedAttemptsSurviveRestartAndThrottle() = runTest(dispatcher) {
        val first = gateway()
        first.create("123456".toCharArray(), biometricRequested = false)
        first.lock()
        assertEquals(UnlockResult.Rejected(1, 0L), first.unlock("000000".toCharArray()))

        val second = gateway()
        assertEquals(UnlockResult.Rejected(2, 0L), second.unlock("000000".toCharArray()))
        repeat(5) { second.unlock("000000".toCharArray()) }
        assertEquals(UnlockResult.Rejected(8, 30_000L), second.unlock("000000".toCharArray()))

        assertEquals(UnlockResult.Rejected(8, 30_000L), second.unlock("123456".toCharArray()))
        assertEquals(VaultState.Locked, second.state.value)

        now = 30_000L
        assertEquals(UnlockResult.Success, second.unlock("123456".toCharArray()))
        assertEquals(0, store.meta!!.failedUnlocks)
    }

    @Test
    fun lostDeviceKeyDoesNotBurnAnAttempt() = runTest(dispatcher) {
        val vault = gateway()
        vault.create("123456".toCharArray(), biometricRequested = false)
        vault.lock()
        vault.unlock("000000".toCharArray())

        val afterWipe = gateway(crypto = VaultCrypto(FakeKeyWrapper(), TEST_KDF_ITERATIONS))

        assertEquals(UnlockResult.Rejected(1, 0L), afterWipe.unlock("123456".toCharArray()))
        assertEquals(1, store.meta!!.failedUnlocks)
    }
}
