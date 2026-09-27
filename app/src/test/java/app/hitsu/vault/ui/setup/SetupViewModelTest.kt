package app.hitsu.vault.ui.setup

import app.hitsu.vault.MainDispatcherRule
import app.hitsu.vault.data.CryptoVaultGateway
import app.hitsu.vault.domain.BiometricAvailability
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.VaultState
import app.hitsu.vault.testGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcher = MainDispatcherRule(dispatcher)

    private lateinit var vault: CryptoVaultGateway

    private suspend fun TestScope.viewModel(biometrics: Boolean = true): SetupViewModel {
        vault = testGateway(this, dispatcher, Clock { dispatcher.scheduler.currentTime })
        return SetupViewModel(vault, BiometricAvailability { biometrics })
    }

    private fun SetupViewModel.type(pin: String) = pin.forEach { onDigit(it.digitToInt()) }

    @Test
    fun continueRequiresMinimumLength() = runTest(dispatcher) {
        val vm = viewModel()
        vm.type("12345")
        assertFalse(vm.state.value.canContinue)
        vm.type("6")
        assertTrue(vm.state.value.canContinue)
    }

    @Test
    fun pinStopsAtMaximumLength() = runTest(dispatcher) {
        val vm = viewModel()
        vm.type("1234567890123")
        assertEquals(12, vm.state.value.entered)
        assertEquals(12, vm.state.value.dotSlots)
    }

    @Test
    fun matchingConfirmationCreatesVault() = runTest(dispatcher) {
        val vm = viewModel()
        vm.type("1234567")
        vm.onContinue()
        assertEquals(SetupStep.Confirm, vm.state.value.step)
        assertEquals(7, vm.state.value.dotSlots)

        vm.type("1234567")
        vm.onContinue()
        runCurrent()

        assertEquals(VaultState.Unlocked, vault.state.value)
        assertEquals(7, vault.pinLength)
        assertTrue(vault.biometricRequested)
    }

    @Test
    fun mismatchRestartsCreation() = runTest(dispatcher) {
        val vm = viewModel()
        vm.type("123456")
        vm.onContinue()
        vm.type("654321")
        vm.onContinue()
        runCurrent()

        val state = vm.state.value
        assertEquals(SetupStep.Create, state.step)
        assertEquals(0, state.entered)
        assertTrue(state.mismatch)
        assertEquals(VaultState.Absent, vault.state.value)
    }

    @Test
    fun biometricToggleIgnoredWithoutHardware() = runTest(dispatcher) {
        val vm = viewModel(biometrics = false)
        vm.onBiometricToggled(true)
        assertFalse(vm.state.value.biometricRequested)
    }
}
