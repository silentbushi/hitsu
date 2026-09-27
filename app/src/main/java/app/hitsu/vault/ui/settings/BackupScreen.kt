package app.hitsu.vault.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hitsu.vault.R
import app.hitsu.vault.data.BackupError
import app.hitsu.vault.data.BackupStatus
import app.hitsu.vault.ui.components.HitsuButton
import app.hitsu.vault.ui.components.HitsuButtonStyle
import app.hitsu.vault.ui.components.HitsuDialog
import app.hitsu.vault.ui.theme.ControlShape
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuType

/** Which of the two things the passphrase is being asked for. */
private enum class Asking { Create, Restore }

@Composable
fun BackupRoute(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf<Asking?>(null) }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME),
    ) { uri -> viewModel.onTargetPicked(uri) }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.onSourcePicked(uri) }

    BackupScreen(
        state = state,
        onBack = onBack,
        onCreate = { asking = Asking.Create },
        onRestore = { asking = Asking.Restore },
        onStatusSeen = viewModel::onStatusSeen,
    )

    asking?.let { intent ->
        PassphraseDialog(
            repeated = intent == Asking.Create,
            onCancel = {
                asking = null
                viewModel.onPickerCancelled()
            },
            onConfirm = { passphrase ->
                asking = null
                viewModel.onPassphraseEntered(passphrase)
                viewModel.onPickerLaunching()
                if (intent == Asking.Create) {
                    createFile.launch(viewModel.suggestedName())
                } else {
                    openFile.launch(arrayOf("*/*"))
                }
            },
        )
    }
}

@Composable
fun BackupScreen(
    state: BackupStatus,
    onBack: () -> Unit,
    onCreate: () -> Unit = {},
    onRestore: () -> Unit = {},
    onStatusSeen: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HitsuColors.Bg)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        SettingsTopBar(title = stringResource(R.string.settings_backup), onBack = onBack)
        SettingsNote(stringResource(R.string.backup_note))

        SettingsSection(stringResource(R.string.backup_section_create))
        HitsuButton(
            text = stringResource(R.string.backup_create),
            onClick = onCreate,
            enabled = !state.running,
            style = HitsuButtonStyle.Outlined,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        SettingsNote(stringResource(R.string.backup_create_note))

        SettingsSection(stringResource(R.string.backup_section_restore))
        HitsuButton(
            text = stringResource(R.string.backup_restore),
            onClick = onRestore,
            enabled = !state.running,
            style = HitsuButtonStyle.Outlined,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        SettingsNote(stringResource(R.string.backup_restore_note))

        BackupStatusLine(state = state, onStatusSeen = onStatusSeen)
    }
}

@Composable
private fun BackupStatusLine(state: BackupStatus, onStatusSeen: () -> Unit) {
    if (!state.running && !state.finished) return

    val message = when {
        state.running && state.restoring ->
            stringResource(R.string.backup_restoring, state.done, state.total)
        state.running -> stringResource(R.string.backup_working, state.done, state.total)
        state.error != null -> stringResource(
            when (state.error) {
                BackupError.Passphrase -> R.string.backup_error_passphrase
                BackupError.Damaged -> R.string.backup_error_damaged
                BackupError.Locked -> R.string.backup_error_locked
                BackupError.Io -> R.string.backup_error_io
            },
        )
        state.restoring -> pluralStringResource(R.plurals.backup_restored, state.done, state.done)
        else -> pluralStringResource(R.plurals.backup_created, state.done, state.done)
    }

    Text(
        text = message,
        style = HitsuType.Meta,
        color = if (state.error != null) HitsuColors.Danger else HitsuColors.TextMuted,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
    )
    if (state.finished && state.duplicates > 0 && state.error == null) {
        Text(
            text = pluralStringResource(
                R.plurals.import_duplicates,
                state.duplicates,
                state.duplicates,
            ),
            style = HitsuType.Meta,
            color = HitsuColors.TextMuted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        )
    }
    if (state.finished) {
        HitsuButton(
            text = stringResource(R.string.action_close),
            onClick = onStatusSeen,
            style = HitsuButtonStyle.Outlined,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

/**
 * Asks for the passphrase, twice when one is being set: a typo in a passphrase that is never shown
 * would only surface the day the backup is needed, which is the worst possible day to find out.
 */
@Composable
private fun PassphraseDialog(
    repeated: Boolean,
    onCancel: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }

    val tooShort = first.isNotEmpty() && first.length < MIN_LENGTH
    val mismatch = repeated && second.isNotEmpty() && first != second
    val ready = first.length >= MIN_LENGTH && (!repeated || first == second)

    HitsuDialog(
        title = stringResource(R.string.backup_pass_title),
        body = stringResource(
            if (repeated) R.string.backup_pass_body else R.string.backup_pass_restore_body,
        ),
        modifier = Modifier.imePadding(),
        content = {
            PassphraseField(
                value = first,
                onValueChange = { first = it },
                hint = stringResource(R.string.backup_pass_hint),
                imeAction = if (repeated) ImeAction.Next else ImeAction.Done,
            )
            if (repeated) {
                PassphraseField(
                    value = second,
                    onValueChange = { second = it },
                    hint = stringResource(R.string.backup_pass_repeat),
                    imeAction = ImeAction.Done,
                )
            }
            val warning = when {
                tooShort -> pluralStringResource(R.plurals.backup_pass_short, MIN_LENGTH, MIN_LENGTH)
                mismatch -> stringResource(R.string.backup_pass_mismatch)
                else -> null
            }
            if (warning != null) {
                Text(text = warning, style = HitsuType.Meta, color = HitsuColors.Danger)
            }
        },
        actions = {
            HitsuButton(
                text = stringResource(R.string.action_continue),
                onClick = { onConfirm(first.toCharArray()) },
                enabled = ready,
                modifier = Modifier.fillMaxWidth(),
            )
            HitsuButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                style = HitsuButtonStyle.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    imeAction: ImeAction,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(HitsuColors.SurfaceInput, ControlShape)
            .border(1.dp, HitsuColors.Stroke, ControlShape)
            .padding(horizontal = 14.dp)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = imeAction,
            ),
            textStyle = HitsuType.Body.copy(color = HitsuColors.TextPrimary),
            cursorBrush = SolidColor(HitsuColors.Accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                if (value.isEmpty()) {
                    Text(text = hint, style = HitsuType.Body, color = HitsuColors.TextMuted)
                }
                field()
            },
        )
    }
}

private const val MIN_LENGTH = 8
private const val MIME = "application/octet-stream"
