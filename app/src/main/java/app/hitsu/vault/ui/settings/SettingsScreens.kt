package app.hitsu.vault.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hitsu.vault.BuildConfig
import app.hitsu.vault.data.download.UpdateOutcome
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.hitsu.vault.R
import app.hitsu.vault.ui.login.LoginActivity
import app.hitsu.vault.ui.components.HitsuButton
import app.hitsu.vault.ui.components.HitsuButtonStyle
import app.hitsu.vault.ui.components.HitsuDialog
import app.hitsu.vault.ui.components.PhonePreview
import app.hitsu.vault.ui.format.dayLabel
import app.hitsu.vault.ui.format.dayTimeLabel
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuTheme
import app.hitsu.vault.ui.theme.HitsuType
import java.time.Instant
import java.time.ZoneId
import app.hitsu.vault.ui.components.HitsuSwitch
import app.hitsu.vault.ui.lock.BiometricOutcome
import app.hitsu.vault.ui.lock.biometricPromptStrings
import app.hitsu.vault.ui.lock.rememberBiometricPrompter

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenAutoLock: () -> Unit,
    onOpenYtDlp: () -> Unit,
    onOpenChangePin: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prompter = rememberBiometricPrompter()
    val (title, _, negative) = biometricPromptStrings()
    val subtitle = stringResource(R.string.biometric_enroll_subtitle)

    SettingsScreen(
        state = state,
        onBack = onBack,
        onOpenAutoLock = onOpenAutoLock,
        onOpenYtDlp = onOpenYtDlp,
        onOpenChangePin = onOpenChangePin,
        onOpenBackup = onOpenBackup,
        onOpenAbout = onOpenAbout,
        /*
         * Turning it on asks for the fingerprint there and then: the key that will hold the DEK is
         * only usable once authenticated, so the same finger that will open the vault is the one
         * that seals it. Turning it off needs no permission — it only throws a key away.
         */
        onToggleBiometric = {
            if (state.biometricEnabled) {
                viewModel.onBiometricDisabled()
            } else {
                val cipher = viewModel.enrollCipher()
                if (prompter != null && cipher != null) {
                    prompter.authenticate(title, subtitle, negative, cipher) { outcome ->
                        if (outcome is BiometricOutcome.Authorised) {
                            viewModel.onBiometricAuthorised(outcome.cipher)
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenAutoLock: () -> Unit,
    onOpenYtDlp: () -> Unit,
    onOpenChangePin: () -> Unit = {},
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    onToggleBiometric: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(HitsuColors.Bg)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        SettingsTopBar(title = stringResource(R.string.settings_title), onBack = onBack)

        SettingsSection(stringResource(R.string.settings_security))
        SettingsRow(
            title = stringResource(R.string.settings_auto_lock),
            value = stringResource(state.autoLock.label),
            onClick = onOpenAutoLock,
        )

        SettingsSection(stringResource(R.string.settings_storage))
        SettingsRow(
            title = stringResource(R.string.settings_change_pin),
            onClick = onOpenChangePin,
        )
        SettingsRow(
            title = stringResource(R.string.settings_biometric),
            onClick = if (state.biometricAvailable) onToggleBiometric else null,
            trailing = { HitsuSwitch(checked = state.biometricEnabled) },
        )
        SettingsNote(
            stringResource(
                if (state.biometricAvailable) {
                    R.string.settings_biometric_note
                } else {
                    R.string.settings_biometric_unavailable
                },
            ),
        )

        SettingsRow(
            title = stringResource(R.string.settings_space_used),
            value = Formatter.formatShortFileSize(context, state.vaultBytes),
        )

        SettingsSection(stringResource(R.string.settings_tools))
        SettingsRow(
            title = stringResource(R.string.settings_ytdlp),
            value = state.ytDlpVersion,
            onClick = onOpenYtDlp,
        )
        SettingsRow(title = stringResource(R.string.settings_backup), onClick = onOpenBackup)
        SettingsRow(title = stringResource(R.string.settings_about), onClick = onOpenAbout)
        SettingsDivider()
    }
}

@Composable
fun AutoLockRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AutoLockScreen(
        state = state,
        onBack = onBack,
        onPick = viewModel::onAutoLockPicked,
        onConfirm = viewModel::onAutoLockConfirmed,
        onDismiss = viewModel::onAutoLockDismissed,
    )
}

@Composable
fun AutoLockScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onPick: (AutoLockChoice) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HitsuColors.Bg)
            .navigationBarsPadding(),
    ) {
        SettingsTopBar(title = stringResource(R.string.settings_auto_lock), onBack = onBack)
        AutoLockChoice.entries.forEach { choice ->
            SettingsChoiceRow(
                label = stringResource(choice.label),
                selected = choice == state.autoLock,
                onSelect = { onPick(choice) },
            )
        }
        SettingsDivider()
        SettingsNote(stringResource(R.string.settings_auto_lock_note))
    }

    val pending = state.pendingConfirmation
    if (pending != null) {
        HitsuDialog(
            title = stringResource(R.string.settings_auto_lock_confirm_title, stringResource(pending.label)),
            body = stringResource(R.string.settings_auto_lock_confirm_body, stringResource(pending.label)),
        ) {
            HitsuButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                style = HitsuButtonStyle.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
            HitsuButton(
                text = stringResource(R.string.action_change),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun YtDlpRoute(onBack: () -> Unit, viewModel: YtDlpViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The login window is an activity of its own, so what it saved shows up on the way back.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshCookies()
        onPauseOrDispose {}
    }
    YtDlpScreen(
        state = state,
        onBack = onBack,
        onUpdate = viewModel::onUpdate,
        onCreateCookies = { url ->
            viewModel.onLoginLaunching()
            context.startActivity(LoginActivity.intent(context, url))
        },
        onDeleteCookies = viewModel::onDeleteCookies,
    )
}

@Composable
fun YtDlpScreen(
    state: YtDlpUiState,
    onBack: () -> Unit,
    onUpdate: () -> Unit,
    onCreateCookies: (String) -> Unit = {},
    onDeleteCookies: (String) -> Unit = {},
) {
    var askingForUrl by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HitsuColors.Bg)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        SettingsTopBar(title = stringResource(R.string.settings_ytdlp), onBack = onBack)
        SettingsSection(stringResource(R.string.ytdlp_installed_version))
        Text(
            text = state.version ?: stringResource(R.string.ytdlp_unknown_version),
            style = HitsuType.Title,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (state.lastCheckMillis > 0L) {
            Text(
                text = stringResource(R.string.ytdlp_last_check, dayTimeLabel(state.lastCheckMillis)),
                style = HitsuType.Meta,
                color = HitsuColors.TextMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
        Text(
            text = stringResource(state.statusLabel()),
            style = HitsuType.Meta,
            color = if (state.result is UpdateOutcome.Failed) HitsuColors.Danger else HitsuColors.TextMuted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        )
        HitsuButton(
            text = stringResource(
                if (state.updating) R.string.ytdlp_updating_action else R.string.ytdlp_check,
            ),
            onClick = onUpdate,
            style = HitsuButtonStyle.Outlined,
            enabled = !state.updating && !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        SettingsDivider()
        SettingsNote(stringResource(R.string.ytdlp_note))

        SettingsSection(stringResource(R.string.cookies_section))
        HitsuButton(
            text = stringResource(R.string.cookies_create),
            onClick = { askingForUrl = true },
            style = HitsuButtonStyle.Outlined,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        if (state.cookies.isEmpty()) {
            SettingsNote(stringResource(R.string.cookies_empty))
        } else {
            state.cookies.forEach { saved ->
                SettingsRow(
                    title = saved.domain,
                    value = stringResource(R.string.cookies_saved_on, dayLabelOf(saved.createdAtMillis)),
                    onClick = { onDeleteCookies(saved.domain) },
                )
            }
            SettingsDivider()
            SettingsNote(stringResource(R.string.cookies_delete_hint))
        }
    }
        if (askingForUrl) {
            CookieUrlDialog(
                onCancel = { askingForUrl = false },
                onConfirm = { url ->
                    askingForUrl = false
                    onCreateCookies(url)
                },
            )
        }
    }
}

@Composable
private fun dayLabelOf(millis: Long): String = dayLabel(
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate(),
)

private fun YtDlpUiState.statusLabel(): Int = when {
    loading -> R.string.ytdlp_preparing
    updating -> R.string.ytdlp_updating
    result is UpdateOutcome.AlreadyCurrent -> R.string.ytdlp_up_to_date
    result is UpdateOutcome.Updated -> R.string.ytdlp_updated
    result is UpdateOutcome.Failed -> R.string.ytdlp_update_failed
    else -> R.string.ytdlp_hint
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HitsuColors.Bg)
            .navigationBarsPadding(),
    ) {
        SettingsTopBar(title = stringResource(R.string.settings_about), onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.wordmark), style = HitsuType.WordmarkLarge)
            Text(stringResource(R.string.about_kanji), style = HitsuType.Title, color = HitsuColors.TextMuted)
            Text(
                text = stringResource(
                    R.string.about_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
                style = HitsuType.Meta,
                color = HitsuColors.TextMuted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.about_description),
                style = HitsuType.Caption,
                textAlign = TextAlign.Center,
            )
        }
        SettingsDivider()
        SettingsNote(stringResource(R.string.about_uninstall))
    }
}

@PhonePreview
@Composable
private fun SettingsPreview() = HitsuTheme {
    SettingsScreen(
        state = SettingsUiState(autoLock = AutoLockChoice.Immediate, vaultBytes = 3_650_000_000L),
        onBack = {},
        onOpenAutoLock = {},
        onOpenYtDlp = {},
        onOpenBackup = {},
        onOpenAbout = {},
    )
}

@PhonePreview
@Composable
private fun AutoLockPreview() = HitsuTheme {
    AutoLockScreen(
        state = SettingsUiState(autoLock = AutoLockChoice.FifteenMinutes),
        onBack = {},
        onPick = {},
        onConfirm = {},
        onDismiss = {},
    )
}
