package app.hitsu.vault.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hitsu.vault.R
import app.hitsu.vault.ui.components.HitsuButton
import app.hitsu.vault.ui.components.PhonePreview
import app.hitsu.vault.ui.components.PinDots
import app.hitsu.vault.ui.components.PinKeypad
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuTheme
import app.hitsu.vault.ui.theme.HitsuType

@Composable
fun ChangePinRoute(onBack: () -> Unit, viewModel: ChangePinViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Nothing to celebrate: the PIN changed, so the screen steps out of the way.
    LaunchedEffect(state.done) {
        if (state.done) onBack()
    }

    ChangePinScreen(
        state = state,
        onBack = onBack,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onContinue = viewModel::onContinue,
    )
}

@Composable
fun ChangePinScreen(
    state: ChangePinUiState,
    onBack: () -> Unit,
    onDigit: (Int) -> Unit = {},
    onBackspace: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HitsuColors.Bg)
            .navigationBarsPadding(),
    ) {
        SettingsTopBar(title = stringResource(R.string.settings_change_pin), onBack = onBack)

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(
                    when (state.step) {
                        ChangePinStep.Current -> R.string.change_pin_current
                        ChangePinStep.New -> R.string.change_pin_new
                        ChangePinStep.Confirm -> R.string.change_pin_confirm
                    },
                ),
                style = HitsuType.Subtitle,
                textAlign = TextAlign.Center,
            )
            val warning = when {
                state.wrongCurrent -> stringResource(R.string.change_pin_wrong)
                state.mismatch -> stringResource(R.string.change_pin_mismatch)
                state.step == ChangePinStep.New -> stringResource(R.string.change_pin_hint)
                else -> null
            }
            Text(
                text = warning.orEmpty(),
                style = HitsuType.Meta,
                color = if (state.wrongCurrent || state.mismatch) {
                    HitsuColors.Danger
                } else {
                    HitsuColors.TextMuted
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            PinDots(
                total = state.dotSlots,
                filled = state.entered,
                error = state.wrongCurrent || state.mismatch,
                modifier = Modifier.padding(top = 28.dp),
            )
        }

        PinKeypad(
            onDigit = onDigit,
            onBackspace = onBackspace,
            enabled = !state.working,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 24.dp),
        )
        HitsuButton(
            text = stringResource(R.string.action_continue),
            onClick = onContinue,
            enabled = state.canContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        )
    }
}

@PhonePreview
@Composable
private fun ChangePinPreview() = HitsuTheme {
    ChangePinScreen(
        state = ChangePinUiState(currentLength = 6, entered = 3),
        onBack = {},
    )
}

@PhonePreview
@Composable
private fun ChangePinMismatchPreview() = HitsuTheme {
    ChangePinScreen(
        state = ChangePinUiState(step = ChangePinStep.New, mismatch = true),
        onBack = {},
    )
}
