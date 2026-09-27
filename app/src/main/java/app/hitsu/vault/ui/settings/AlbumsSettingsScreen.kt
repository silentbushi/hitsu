package app.hitsu.vault.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hitsu.vault.R
import app.hitsu.vault.data.AlbumOrder
import app.hitsu.vault.data.AlbumPreferences
import app.hitsu.vault.data.db.AlbumWithCount
import app.hitsu.vault.ui.components.HitsuButton
import app.hitsu.vault.ui.components.HitsuButtonStyle
import app.hitsu.vault.ui.components.HitsuDialog
import app.hitsu.vault.ui.components.HitsuIcons
import app.hitsu.vault.ui.components.HitsuSwitch
import app.hitsu.vault.ui.theme.ControlShape
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuType

@Composable
fun AlbumsSettingsRoute(onBack: () -> Unit, viewModel: AlbumsSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlbumsSettingsScreen(
        state = state,
        onBack = onBack,
        onChooseDestination = viewModel::onChooseDestination,
        onDestinationPicked = viewModel::onDestinationPicked,
        onDismissChoice = viewModel::onDismissChoice,
        onShowCountsToggled = viewModel::onShowCountsToggled,
        onOrderChanged = viewModel::onOrderChanged,
        onRenameRequested = viewModel::onRenameRequested,
        onRenamed = viewModel::onRenamed,
        onDismissRename = viewModel::onDismissRename,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDismissDelete = viewModel::onDismissDelete,
    )
}

@Composable
fun AlbumsSettingsScreen(
    state: AlbumsSettingsUiState,
    onBack: () -> Unit,
    onChooseDestination: (DestinationKind) -> Unit = {},
    onDestinationPicked: (String?) -> Unit = {},
    onDismissChoice: () -> Unit = {},
    onShowCountsToggled: () -> Unit = {},
    onOrderChanged: (AlbumOrder) -> Unit = {},
    onRenameRequested: (AlbumWithCount) -> Unit = {},
    onRenamed: (String) -> Unit = {},
    onDismissRename: () -> Unit = {},
    onDeleteRequested: (AlbumWithCount) -> Unit = {},
    onDeleteConfirmed: () -> Unit = {},
    onDismissDelete: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(HitsuColors.Bg)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsTopBar(title = stringResource(R.string.settings_albums), onBack = onBack)

            SettingsRow(
                title = stringResource(R.string.albums_import_destination),
                value = state.albums.nameOf(state.importAlbum)
                    ?: stringResource(R.string.albums_destination_none),
                onClick = { onChooseDestination(DestinationKind.Import) },
            )
            SettingsRow(
                title = stringResource(R.string.albums_download_destination),
                value = when {
                    state.downloadAlbumIsDefault -> AlbumPreferences.DEFAULT_DOWNLOAD_ALBUM
                    else -> state.albums.nameOf(state.downloadAlbum)
                        ?: stringResource(R.string.albums_destination_none)
                },
                onClick = { onChooseDestination(DestinationKind.Download) },
            )
            SettingsRow(
                title = stringResource(R.string.albums_show_counts),
                onClick = onShowCountsToggled,
                trailing = { HitsuSwitch(checked = state.showCounts) },
            )
            SettingsRow(
                title = stringResource(R.string.albums_order),
                value = stringResource(
                    when (state.order) {
                        AlbumOrder.Alphabetical -> R.string.albums_order_alphabetical
                        AlbumOrder.Newest -> R.string.albums_order_newest
                    },
                ),
                onClick = {
                    onOrderChanged(
                        if (state.order == AlbumOrder.Alphabetical) {
                            AlbumOrder.Newest
                        } else {
                            AlbumOrder.Alphabetical
                        },
                    )
                },
            )

            SettingsSection(stringResource(R.string.albums_yours))
            if (state.albums.isEmpty()) {
                SettingsNote(stringResource(R.string.albums_none_yet))
            } else {
                state.albums.forEach { album ->
                    SettingsRow(
                        title = album.name,
                        value = if (state.showCounts) album.itemCount.toString() else null,
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                Icon(
                                    imageVector = HitsuIcons.Edit,
                                    contentDescription = stringResource(R.string.albums_rename),
                                    tint = HitsuColors.TextMuted,
                                    modifier = Modifier
                                        .clickable(role = Role.Button) { onRenameRequested(album) }
                                        .size(18.dp),
                                )
                                Icon(
                                    imageVector = HitsuIcons.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = HitsuColors.Danger,
                                    modifier = Modifier
                                        .clickable(role = Role.Button) { onDeleteRequested(album) }
                                        .size(18.dp),
                                )
                            }
                        },
                    )
                }
            }
            SettingsDivider()
            SettingsNote(stringResource(R.string.albums_delete_note))
        }

        if (state.choosing != null) {
            DestinationDialog(
                albums = state.albums,
                onCancel = onDismissChoice,
                onPick = onDestinationPicked,
            )
        }
        state.renaming?.let { album ->
            RenameDialog(album = album, onCancel = onDismissRename, onRename = onRenamed)
        }
        state.deleting?.let { album ->
            HitsuDialog(
                title = stringResource(R.string.albums_delete_title, album.name),
                body = pluralStringResource(
                    R.plurals.albums_delete_body,
                    album.itemCount,
                    album.itemCount,
                ),
                actions = {
                    HitsuButton(
                        text = stringResource(R.string.action_delete),
                        onClick = onDeleteConfirmed,
                        style = HitsuButtonStyle.Danger,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HitsuButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismissDelete,
                        style = HitsuButtonStyle.Outlined,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
    }
}

private fun List<AlbumWithCount>.nameOf(id: String?): String? =
    id?.let { wanted -> firstOrNull { it.id == wanted }?.name }

@Composable
private fun DestinationDialog(
    albums: List<AlbumWithCount>,
    onCancel: () -> Unit,
    onPick: (String?) -> Unit,
) {
    HitsuDialog(
        title = stringResource(R.string.albums_destination_title),
        body = stringResource(R.string.albums_destination_body),
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.albums_destination_none),
                    style = HitsuType.Body,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onPick(null) }
                        .padding(vertical = 12.dp),
                )
                albums.forEach { album ->
                    Text(
                        text = album.name,
                        style = HitsuType.Body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onPick(album.id) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        actions = {
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
private fun RenameDialog(
    album: AlbumWithCount,
    onCancel: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(album.name) }

    HitsuDialog(
        title = stringResource(R.string.albums_rename),
        body = stringResource(R.string.albums_rename_body),
        modifier = Modifier.imePadding(),
        content = {
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
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = HitsuType.Body.copy(color = HitsuColors.TextPrimary),
                    cursorBrush = SolidColor(HitsuColors.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        actions = {
            HitsuButton(
                text = stringResource(R.string.action_change),
                onClick = { onRename(name) },
                enabled = name.isNotBlank() && name != album.name,
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
