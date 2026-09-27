package app.hitsu.vault.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hitsu.vault.R
import app.hitsu.vault.ui.components.HitsuIcons
import app.hitsu.vault.ui.components.PhonePreview
import app.hitsu.vault.ui.components.PinDots
import app.hitsu.vault.ui.components.PinKeypad
import app.hitsu.vault.ui.theme.ControlShape
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuTheme
import app.hitsu.vault.ui.theme.HitsuType
import androidx.compose.runtime.LaunchedEffect

@Composable
fun LockRoute(viewModel: LockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.biometricSheetVisible, onBack = viewModel::onUsePin)

    val prompter = rememberBiometricPrompter()
    val (title, subtitle, negative) = biometricPromptStrings()
    LaunchedEffect(state.biometric) {
        if (state.biometric != BiometricState.Requested) return@LaunchedEffect
        val cipher = viewModel.unlockCipher()
        if (prompter == null || cipher == null) {
            viewModel.onBiometricUnavailable()
            return@LaunchedEffect
        }
        prompter.authenticate(title, subtitle, negative, cipher) { outcome ->
            when (outcome) {
                is BiometricOutcome.Authorised -> viewModel.onBiometricAuthorised(outcome.cipher)
                BiometricOutcome.NotRecognised -> viewModel.onBiometricNotRecognised()
                BiometricOutcome.Dismissed -> viewModel.onUsePin()
            }
        }
    }

    // Spec §5.2: if a fingerprint can open it, offer that first instead of making them ask.
    LaunchedEffect(Unit) {
        if (state.biometric == BiometricState.Available) viewModel.onUseBiometric()
    }
    LockScreen(
        state = state,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onUseBiometric = viewModel::onUseBiometric,
        onUsePin = viewModel::onUsePin,
    )
}

@Composable
fun LockScreen(
    state: LockUiState,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onUseBiometric: () -> Unit,
    onUsePin: () -> Unit,
) {
    val entry = state.entry
    Box(
        Modifier
            .fillMaxSize()
            .background(HitsuColors.Bg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.wordmark),
                style = HitsuType.Wordmark,
                modifier = Modifier.padding(top = 96.dp),
            )
            Text(
                text = stringResource(R.string.lock_title),
                style = HitsuType.Body,
                color = HitsuColors.TextMuted,
                modifier = Modifier.padding(top = 14.dp),
            )
            PinDots(
                total = state.pinLength,
                filled = state.entered,
                error = entry is PinEntry.Wrong,
                modifier = Modifier.padding(top = 40.dp),
            )
            Text(
                text = when (entry) {
                    is PinEntry.Wrong -> stringResource(R.string.lock_wrong_pin, entry.attempt, entry.maxAttempts)
                    is PinEntry.Throttled -> stringResource(R.string.lock_too_many_attempts)
                    PinEntry.Idle, PinEntry.Verifying -> ""
                },
                style = HitsuType.Meta,
                color = HitsuColors.Danger,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .height(18.dp),
            )
            Spacer(Modifier.weight(1f))
            PinKeypad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                enabled = entry !is PinEntry.Throttled,
                modifier = Modifier.alpha(if (state.biometricSheetVisible) 0.35f else 1f),
            )
            LockFooter(
                state = state,
                onUseBiometric = onUseBiometric,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        BiometricSheet(state = state.biometric, onUsePin = onUsePin)
    }
}

@Composable
private fun LockFooter(state: LockUiState, onUseBiometric: () -> Unit, modifier: Modifier = Modifier) {
    val entry = state.entry
    Box(modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        when {
            entry is PinEntry.Throttled -> Text(
                text = if (entry.secondsLeft >= 60) {
                    stringResource(R.string.lock_wait_minutes, (entry.secondsLeft + 59) / 60)
                } else {
                    stringResource(R.string.lock_wait_seconds, entry.secondsLeft)
                },
                style = HitsuType.Action,
                color = HitsuColors.TextMuted,
            )
            state.biometric == BiometricState.Available -> Row(
                modifier = Modifier
                    .clip(ControlShape)
                    .clickable(role = Role.Button, onClick = onUseBiometric)
                    .padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = HitsuIcons.Fingerprint,
                    contentDescription = null,
                    tint = HitsuColors.Accent,
                    modifier = Modifier.size(22.dp),
                )
                Text(stringResource(R.string.lock_use_biometric), style = HitsuType.Action, color = HitsuColors.Accent)
            }
        }
    }
}

@Composable
private fun LockPreview(state: LockUiState) = HitsuTheme {
    LockScreen(state = state, onDigit = {}, onBackspace = {}, onUseBiometric = {}, onUsePin = {})
}

@PhonePreview
@Composable
private fun LockEmptyPreview() = LockPreview(LockUiState(biometric = BiometricState.Available))

@PhonePreview
@Composable
private fun LockTypingPreview() = LockPreview(LockUiState(entered = 3, biometric = BiometricState.Available))

@PhonePreview
@Composable
private fun LockWrongPreview() = LockPreview(
    LockUiState(entered = 6, entry = PinEntry.Wrong(3, 8), biometric = BiometricState.Available),
)

@PhonePreview
@Composable
private fun LockThrottledPreview() = LockPreview(LockUiState(entry = PinEntry.Throttled(30)))

@PhonePreview
@Composable
private fun BiometricRequestedPreview() = LockPreview(LockUiState(biometric = BiometricState.Requested))

@PhonePreview
@Composable
private fun BiometricFailedPreview() = LockPreview(LockUiState(biometric = BiometricState.NotRecognized))

@PhonePreview
@Composable
private fun BiometricInvalidatedPreview() = LockPreview(LockUiState(biometric = BiometricState.Invalidated))
