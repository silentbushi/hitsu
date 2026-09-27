package app.hitsu.vault.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hitsu.vault.R
import app.hitsu.vault.domain.PinPolicy
import app.hitsu.vault.ui.components.HitsuButton
import app.hitsu.vault.ui.components.HitsuSwitch
import app.hitsu.vault.ui.components.PhonePreview
import app.hitsu.vault.ui.components.PinDots
import app.hitsu.vault.ui.components.PinKeypad
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuTheme
import app.hitsu.vault.ui.theme.HitsuType

@Composable
fun SetupRoute(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.step == SetupStep.Confirm, onBack = viewModel::onBack)
    SetupScreen(
        state = state,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onBiometricToggled = viewModel::onBiometricToggled,
        onContinue = viewModel::onContinue,
    )
}

@Composable
fun SetupScreen(
    state: SetupUiState,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onBiometricToggled: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    val creating = state.step == SetupStep.Create
    Column(
        Modifier
            .fillMaxSize()
            .background(HitsuColors.Bg)
            .safeDrawingPadding()
            .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 24.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.wordmark), style = HitsuType.WordmarkLarge)
            Text(
                text = stringResource(R.string.setup_tagline),
                style = HitsuType.Body,
                color = HitsuColors.TextMuted,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .widthIn(max = 240.dp),
            )
            Text(
                text = stringResource(if (creating) R.string.setup_create_title else R.string.setup_confirm_title),
                style = HitsuType.Subtitle,
                modifier = Modifier.padding(top = 44.dp),
            )
            Text(
                text = if (creating) {
                    stringResource(R.string.setup_create_hint, PinPolicy.MIN_LENGTH, PinPolicy.MAX_LENGTH)
                } else {
                    stringResource(R.string.setup_confirm_hint)
                },
                style = HitsuType.Caption,
                color = HitsuColors.TextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
            PinDots(
                total = state.dotSlots,
                filled = state.entered,
                modifier = Modifier.padding(top = 24.dp),
            )
            if (state.mismatch) {
                Text(
                    text = stringResource(R.string.setup_mismatch),
                    style = HitsuType.Meta,
                    color = HitsuColors.Danger,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            if (creating) {
                Box(
                    Modifier
                        .padding(top = 36.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(HitsuColors.Stroke),
                )
                if (state.biometricAvailable) {
                    BiometricRow(
                        checked = state.biometricRequested,
                        onToggle = onBiometricToggled,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.setup_disclaimer),
                    style = HitsuType.Fine,
                    color = HitsuColors.TextMuted,
                    modifier = Modifier.padding(top = if (state.biometricAvailable) 28.dp else 20.dp),
                )
                Text(
                    text = stringResource(R.string.setup_disclaimer_originals),
                    style = HitsuType.Fine,
                    color = HitsuColors.Danger,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        PinKeypad(
            onDigit = onDigit,
            onBackspace = onBackspace,
            enabled = !state.creating,
            keyHeight = 56.dp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp),
        )
        Spacer(Modifier.height(16.dp))
        HitsuButton(
            text = stringResource(R.string.action_continue),
            onClick = onContinue,
            enabled = state.canContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BiometricRow(checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setup_biometric_title), style = HitsuType.Body)
            Text(
                text = stringResource(R.string.setup_biometric_hint),
                style = HitsuType.Meta,
                color = HitsuColors.TextMuted,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        HitsuSwitch(checked = checked)
    }
}

@PhonePreview
@Composable
private fun SetupCreatePreview() = HitsuTheme {
    SetupScreen(
        state = SetupUiState(entered = 4, biometricAvailable = true, biometricRequested = true),
        onDigit = {}, onBackspace = {}, onBiometricToggled = {}, onContinue = {},
    )
}

@PhonePreview
@Composable
private fun SetupConfirmPreview() = HitsuTheme {
    SetupScreen(
        state = SetupUiState(step = SetupStep.Confirm, entered = 2, confirmLength = 6),
        onDigit = {}, onBackspace = {}, onBiometricToggled = {}, onContinue = {},
    )
}
