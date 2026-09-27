package app.hitsu.vault.ui.download

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hitsu.vault.R
import app.hitsu.vault.data.download.DownloadPhase
import app.hitsu.vault.ui.components.HitsuButton
import app.hitsu.vault.ui.components.HitsuButtonStyle
import app.hitsu.vault.ui.components.PhonePreview
import app.hitsu.vault.ui.settings.SettingsTopBar
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuTheme
import app.hitsu.vault.ui.theme.HitsuType

@Composable
fun DownloadRoute(onClose: () -> Unit, viewModel: DownloadViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { viewModel.onCancel(); onClose() }
    DownloadScreen(
        state = state,
        onClose = { viewModel.onCancel(); onClose() },
        onStart = viewModel::onStart,
        onDone = { viewModel.onFinished(); onClose() },
    )
}

@Composable
fun DownloadScreen(
    state: DownloadUiState,
    onClose: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HitsuColors.Bg)
            .navigationBarsPadding(),
    ) {
        SettingsTopBar(title = stringResource(R.string.download_title), onBack = onClose)

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = state.title ?: state.url,
                style = HitsuType.Subtitle,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.source ?: state.url,
                style = HitsuType.Meta,
                color = HitsuColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(state.status),
                style = HitsuType.Caption,
                color = if (state.failed) HitsuColors.Danger else HitsuColors.TextMuted,
                modifier = Modifier.padding(top = 14.dp),
            )
            if (state.phase is DownloadPhase.Running) {
                ProgressLine(state.phase.progress)
            }
            state.detail?.let { detail ->
                Text(
                    text = detail,
                    style = HitsuType.Meta,
                    color = HitsuColors.TextMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                state.phase is DownloadPhase.Ready -> HitsuButton(
                    text = stringResource(R.string.download_start),
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.phase is DownloadPhase.Done -> HitsuButton(
                    text = stringResource(R.string.download_open_vault),
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.failed -> HitsuButton(
                    text = stringResource(R.string.action_close),
                    onClick = onClose,
                    style = HitsuButtonStyle.Outlined,
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> HitsuButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onClose,
                    style = HitsuButtonStyle.Outlined,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProgressLine(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(HitsuColors.Stroke),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .background(HitsuColors.Accent),
        )
    }
}

@PhonePreview
@Composable
private fun DownloadPreview() = HitsuTheme {
    DownloadScreen(
        state = DownloadUiState(
            url = "https://x.com/alguien/status/123",
            title = "Un vídeo compartido",
            source = "Twitter",
            phase = DownloadPhase.Running(0.46f),
        ),
        onClose = {},
        onStart = {},
        onDone = {},
    )
}
