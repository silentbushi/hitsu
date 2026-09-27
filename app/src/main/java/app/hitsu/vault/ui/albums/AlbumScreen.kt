package app.hitsu.vault.ui.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hitsu.vault.R
import app.hitsu.vault.domain.MediaItem
import app.hitsu.vault.ui.components.HitsuIcons
import app.hitsu.vault.ui.home.MediaGrid
import app.hitsu.vault.ui.home.SelectionAction
import app.hitsu.vault.ui.home.SelectionActionBar
import app.hitsu.vault.ui.home.SelectionBar
import app.hitsu.vault.ui.settings.SettingsTopBar
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuType

@Composable
fun AlbumRoute(
    onOpen: (MediaItem) -> Unit,
    onBack: () -> Unit,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.selection.isNotEmpty(), onBack = viewModel::onClearSelection)

    AlbumScreen(
        state = state,
        onBack = onBack,
        onOpen = onOpen,
        onToggleSelected = viewModel::onToggleSelected,
        onSelectAll = viewModel::onSelectAll,
        onClearSelection = viewModel::onClearSelection,
        onAddToAlbum = viewModel::onAddToAlbum,
        onCreateAlbum = viewModel::onCreateAlbum,
        onRemoveFromAlbum = viewModel::onRemoveFromAlbum,
        onDismissPicker = viewModel::onDismissPicker,
        onOpenPicker = viewModel::onOpenPicker,
    )
}

@Composable
fun AlbumScreen(
    state: AlbumUiState,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit = {},
    onToggleSelected: (String) -> Unit = {},
    onSelectAll: (List<String>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onAddToAlbum: (String) -> Unit = {},
    onCreateAlbum: (String) -> Unit = {},
    onRemoveFromAlbum: () -> Unit = {},
    onDismissPicker: () -> Unit = {},
    onOpenPicker: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(HitsuColors.Bg)
                .safeDrawingPadding(),
        ) {
            if (state.selection.isNotEmpty()) {
                SelectionBar(
                    count = state.selection.size,
                    onLeave = onClearSelection,
                    onSelectAll = { onSelectAll(state.allIds) },
                )
            } else {
                SettingsTopBar(title = state.name, onBack = onBack)
            }

            Box(Modifier.fillMaxSize()) {
                if (state.days.isEmpty()) {
                    Text(
                        text = stringResource(R.string.albums_empty),
                        style = HitsuType.Body,
                        color = HitsuColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp),
                    )
                } else {
                    MediaGrid(
                        days = state.days,
                        selection = state.selection,
                        selecting = state.selection.isNotEmpty(),
                        onOpen = onOpen,
                        onToggleSelected = onToggleSelected,
                    )
                }
                if (state.selection.isNotEmpty()) {
                    SelectionActionBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                        SelectionAction(
                            icon = HitsuIcons.Plus,
                            label = stringResource(R.string.action_album),
                            tint = HitsuColors.TextPrimary,
                            onClick = onOpenPicker,
                        )
                        SelectionAction(
                            icon = HitsuIcons.Close,
                            label = stringResource(R.string.action_remove_from_album),
                            tint = HitsuColors.TextPrimary,
                            onClick = onRemoveFromAlbum,
                        )
                    }
                }
            }
        }

        if (state.pickingAlbum) {
            AlbumPickerDialog(
                albums = state.albums.filter { it.id != state.albumId },
                count = state.selection.size,
                onCancel = onDismissPicker,
                onPick = onAddToAlbum,
                onCreate = onCreateAlbum,
            )
        }
    }
}
