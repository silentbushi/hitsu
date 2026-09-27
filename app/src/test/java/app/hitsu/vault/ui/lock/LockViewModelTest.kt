package app.hitsu.vault.ui.lock

import app.hitsu.vault.MainDispatcherRule
import app.hitsu.vault.data.CryptoVaultGateway
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.VaultState
import app.hitsu.vault.testGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcher = MainDispatcherRule(dispatcher)

    private val clock = Clock { dispatcher.scheduler.currentTime }
    private lateinit var vault: CryptoVaultGateway

    private suspend fun TestScope.lockedViewModel(): LockViewModel {
        vault = testGateway(this, dispatcher, clock)
        vault.create("123456".toCharArray(), biometricRequested = false)
        vault.lock()
        return LockViewModel(vault, clock)
    }

    private fun TestScope.type(viewModel: LockViewModel, pin: String) {
        pin.forEach { viewModel.onDigit(it.digitToInt()) }
        runCurrent()
    }

    @Test
    fun correctPinUnlocksVault() = runTest(dispatcher) {
        val viewModel = lockedViewModel()
        type(viewModel, "123456")
        assertEquals(VaultState.Unlocked, vault.state.value)
    }

    @Test
    fun wrongPinKeepsDotsFilledAndReportsAttempt() = runTest(dispatcher) {
        val viewModel = lockedViewModel()
        type(viewModel, "000000")
        assertEquals(PinEntry.Wrong(1, 8), viewModel.state.value.entry)
        assertEquals(6, viewModel.state.value.entered)

        viewModel.onDigit(1)
        assertEquals(PinEntry.Idle, viewModel.state.value.entry)
        assertEquals(1, viewModel.state.value.entered)
    }

    @Test
    fun eighthFailureThrottlesAndCountdownReleasesKeypad() = runTest(dispatcher) {
        val viewModel = lockedViewModel()
        repeat(8) { type(viewModel, "000000") }
        assertEquals(PinEntry.Throttled(30), viewModel.state.value.entry)

        viewModel.onDigit(1)
        assertEquals(0, viewModel.state.value.entered)

        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(PinEntry.Idle, viewModel.state.value.entry)
    }

    @Test
    fun activeThrottleSurvivesRelock() = runTest(dispatcher) {
        val first = lockedViewModel()
        repeat(8) { type(first, "000000") }

        val second = LockViewModel(vault, clock)
        runCurrent()
        assertEquals(PinEntry.Throttled(30), second.state.value.entry)
    }
}
