package app.hitsu.vault.domain

import app.hitsu.vault.crypto.VaultCipher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.crypto.Cipher

@OptIn(ExperimentalCoroutinesApi::class)
class AutoLockTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeVault(override var lockTimeoutMillis: Long) : VaultGateway {
        private val _state = MutableStateFlow(VaultState.Unlocked)
        override val state: StateFlow<VaultState> = _state
        override val pinLength = 6
        override val biometricRequested = false
        override val retryAtMillis = 0L
        override val cipher: VaultCipher? = null
        override suspend fun create(pin: CharArray, biometricRequested: Boolean) = Unit
        override suspend fun unlock(pin: CharArray): UnlockResult = UnlockResult.Success
        override suspend fun setLockTimeout(millis: Long) {
            lockTimeoutMillis = millis
        }

        override val biometricEnabled = false
        override fun biometricEnrollCipher(): Cipher? = null
        override fun biometricUnlockCipher(): Cipher? = null
        override suspend fun enableBiometric(cipher: Cipher) = false
        override suspend fun unlockWithBiometric(cipher: Cipher) = false
        override suspend fun forgetBiometric() = Unit

        override fun lock() {
            _state.value = VaultState.Locked
        }

        val locked: Boolean get() = _state.value == VaultState.Locked
    }

    private fun TestScope.autoLock(timeoutMillis: Long): Pair<AutoLock, FakeVault> {
        val vault = FakeVault(timeoutMillis)
        return AutoLock(vault, this) to vault
    }

    @Test
    fun withoutADelayTheVaultClosesAtOnce() = runTest(dispatcher) {
        val (autoLock, vault) = autoLock(timeoutMillis = 0L)

        autoLock.onBackgrounded(changingConfiguration = false)

        assertEquals(true, vault.locked)
    }

    @Test
    fun withADelayTheVaultStaysOpenUntilItRunsOut() = runTest(dispatcher) {
        val (autoLock, vault) = autoLock(timeoutMillis = 15 * 60_000L)

        autoLock.onBackgrounded(changingConfiguration = false)
        advanceTimeBy(14 * 60_000L)
        runCurrent()
        assertEquals(false, vault.locked)

        advanceTimeBy(60_001L)
        runCurrent()
        assertEquals(true, vault.locked)
    }

    /** The point of the delay: stepping out and back does not make you type the PIN again. */
    @Test
    fun comingBackInTimeCallsOffTheLock() = runTest(dispatcher) {
        val (autoLock, vault) = autoLock(timeoutMillis = 60_000L)

        autoLock.onBackgrounded(changingConfiguration = false)
        advanceTimeBy(30_000L)
        autoLock.onForegrounded()
        advanceTimeBy(10 * 60_000L)
        runCurrent()

        assertEquals(false, vault.locked)
    }

    @Test
    fun rotatingIsNotLeaving() = runTest(dispatcher) {
        val (autoLock, vault) = autoLock(timeoutMillis = 0L)

        autoLock.onBackgrounded(changingConfiguration = true)

        assertEquals(false, vault.locked)
    }

    /** The system photo picker backgrounds us; that hop was asked for by the user. */
    @Test
    fun theExemptedHopDoesNotLockAndOnlyCountsOnce() = runTest(dispatcher) {
        val (autoLock, vault) = autoLock(timeoutMillis = 0L)

        autoLock.allowNextBackground()
        autoLock.onBackgrounded(changingConfiguration = false)
        assertEquals(false, vault.locked)

        autoLock.onBackgrounded(changingConfiguration = false)
        assertEquals(true, vault.locked)
    }

    @Test
    fun leavingAgainRestartsTheCountdown() = runTest(dispatcher) {
        val (autoLock, vault) = autoLock(timeoutMillis = 60_000L)

        autoLock.onBackgrounded(changingConfiguration = false)
        advanceTimeBy(50_000L)
        autoLock.onForegrounded()
        autoLock.onBackgrounded(changingConfiguration = false)
        advanceTimeBy(50_000L)
        runCurrent()
        assertEquals(false, vault.locked)

        advanceTimeBy(10_001L)
        runCurrent()
        assertEquals(true, vault.locked)
    }
}
