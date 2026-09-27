package app.hitsu.vault.ui.home

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hitsu.vault.R
import app.hitsu.vault.data.ExportStatus
import app.hitsu.vault.data.ImportStatus
import app.hitsu.vault.domain.MediaFilter
import app.hitsu.vault.domain.MediaItem
import app.hitsu.vault.domain.MediaType
import app.hitsu.vault.ui.components.HitsuButton
import app.hitsu.vault.ui.components.HitsuButtonStyle
import app.hitsu.vault.ui.components.HitsuDialog
import app.hitsu.vault.ui.components.HitsuFab
import app.hitsu.vault.ui.components.HitsuIcons
import app.hitsu.vault.ui.components.PhonePreview
import app.hitsu.vault.ui.format.dayLabel
import app.hitsu.vault.ui.media.ThumbnailKey
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuTheme
import app.hitsu.vault.ui.theme.HitsuType
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.LocalDate
import androidx.compose.foundation.layout.RowScope
import app.hitsu.vault.data.db.AlbumWithCount
import app.hitsu.vault.ui.albums.AlbumPickerDialog
import androidx.compose.ui.text.style.TextOverflow
import app.hitsu.vault.ui.albums.NewAlbumDialog

private val TABS = listOf(
    HomeTab.All to R.string.home_tab_all,
    HomeTab.Photos to R.string.home_tab_photos,
    HomeTab.Videos to R.string.home_tab_videos,
    HomeTab.Albums to R.string.home_tab_albums,
)

@Composable
fun HomeRoute(
    onOpenPhoto: (String, MediaFilter) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris: List<Uri> -> viewModel.onPicked(uris) }

    HomeScreen(
        state = state,
        onFilterSelected = viewModel::onTabSelected,
        onOpenAlbum = onOpenAlbum,
        onOpenPicker = viewModel::onOpenPicker,
        onDismissPicker = viewModel::onDismissPicker,
        onAddToAlbum = viewModel::onAddToAlbum,
        onCreateAlbum = viewModel::onCreateAlbum,
        onNewAlbum = viewModel::onNewAlbum,
        onNewAlbumNamed = viewModel::onNewAlbumNamed,
        onDismissNewAlbum = viewModel::onDismissNewAlbum,
        onToggleSelected = viewModel::onToggleSelected,
        onSelectAll = viewModel::onSelectAll,
        onClearSelection = viewModel::onClearSelection,
        onExportSelected = viewModel::onExportSelected,
        onDeleteSelected = viewModel::onDeleteSelected,
        onExportStatusSeen = viewModel::onExportStatusSeen,
        onImport = {
            viewModel.onPickerLaunching()
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
        },
        onOpenSettings = onOpenSettings,
        onOpen = { item ->
            if (item.type == MediaType.Video) onOpenVideo(item.id) else onOpenPhoto(item.id, state.filter)
        },
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onFilterSelected: (HomeTab) -> Unit,
    onImport: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpen: (MediaItem) -> Unit = {},
    onToggleSelected: (String) -> Unit = {},
    onSelectAll: (List<String>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onExportSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onExportStatusSeen: () -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    onOpenPicker: () -> Unit = {},
    onDismissPicker: () -> Unit = {},
    onAddToAlbum: (String) -> Unit = {},
    onCreateAlbum: (String) -> Unit = {},
    onNewAlbum: () -> Unit = {},
    onNewAlbumNamed: (String) -> Unit = {},
    onDismissNewAlbum: () -> Unit = {},
) {
    var confirming by remember { mutableStateOf<Confirmation?>(null) }
    BackHandler(enabled = state.selecting) { onClearSelection() }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(HitsuColors.Bg)
                .safeDrawingPadding(),
        ) {
            if (state.selecting) {
                SelectionBar(
                    count = state.selection.size,
                    onLeave = onClearSelection,
                    onSelectAll = { onSelectAll(state.allIds) },
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.wordmark),
                        style = HitsuType.Title,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = HitsuIcons.Settings,
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = HitsuColors.TextPrimary,
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onOpenSettings)
                            .size(22.dp),
                    )
                }
            }
            FilterTabs(selected = state.tab, onSelected = onFilterSelected)
            StatusStrip(
                importStatus = state.importStatus,
                exportStatus = state.exportStatus,
                receivingShare = state.receivingShare,
                onExportStatusSeen = onExportStatusSeen,
            )

            Box(Modifier.fillMaxSize()) {
                when {
                    state.tab == HomeTab.Albums -> AlbumGrid(
                        albums = state.albums,
                        showCounts = state.showAlbumCounts,
                        onOpenAlbum = onOpenAlbum,
                        onNewAlbum = onNewAlbum,
                    )
                    state.showEmptyState -> EmptyVault(onImport = onImport)
                    else -> MediaGrid(
                        days = state.days,
                        selection = state.selection,
                        selecting = state.selecting,
                        onOpen = onOpen,
                        onToggleSelected = onToggleSelected,
                    )
                }
                if (state.selecting) {
                    SelectionActionBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                        SelectionAction(
                            icon = HitsuIcons.Export,
                            label = stringResource(R.string.action_export),
                            tint = HitsuColors.TextPrimary,
                            onClick = { confirming = Confirmation.Export(state.selection.size) },
                        )
                        SelectionAction(
                            icon = HitsuIcons.Plus,
                            label = stringResource(R.string.action_album),
                            tint = HitsuColors.TextPrimary,
                            onClick = onOpenPicker,
                        )
                        SelectionAction(
                            icon = HitsuIcons.Delete,
                            label = stringResource(R.string.action_delete),
                            tint = HitsuColors.Danger,
                            onClick = { confirming = Confirmation.Delete(state.selection.size) },
                        )
                    }
                } else if (state.tab != HomeTab.Albums) {
                    HitsuFab(
                        onClick = onImport,
                        contentDescription = stringResource(R.string.cd_import),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 28.dp),
                    )
                }
            }
        }

        if (state.namingAlbum) {
            NewAlbumDialog(onCancel = onDismissNewAlbum, onCreate = onNewAlbumNamed)
        }

        if (state.pickingAlbum) {
            AlbumPickerDialog(
                albums = state.albums,
                count = state.selection.size,
                onCancel = onDismissPicker,
                onPick = onAddToAlbum,
                onCreate = onCreateAlbum,
            )
        }

        when (val pending = confirming) {
            is Confirmation.Delete -> HitsuDialog(
                title = stringResource(R.string.dialog_delete_title),
                body = pluralStringResource(R.plurals.dialog_delete_body, pending.count, pending.count),
                actions = {
                    HitsuButton(
                        text = stringResource(R.string.action_delete),
                        onClick = {
                            confirming = null
                            onDeleteSelected()
                        },
                        style = HitsuButtonStyle.Danger,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HitsuButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { confirming = null },
                        style = HitsuButtonStyle.Outlined,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
            is Confirmation.Export -> HitsuDialog(
                title = stringResource(R.string.dialog_export_title),
                body = pluralStringResource(R.plurals.dialog_export_body, pending.count, pending.count),
                actions = {
                    HitsuButton(
                        text = stringResource(R.string.action_export),
                        onClick = {
                            confirming = null
                            onExportSelected()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HitsuButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { confirming = null },
                        style = HitsuButtonStyle.Outlined,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
            null -> Unit
        }
    }
}

/** What the user is being asked about, and about how many items. */
private sealed interface Confirmation {
    val count: Int

    data class Delete(override val count: Int) : Confirmation
    data class Export(override val count: Int) : Confirmation
}

/**
 * Mockup `album/album-main-page.png`: the same dense grid as the photos, three columns with a 1px
 * gap, each album showing its newest item as a cover. Creating one is a text action in the section
 * header rather than another floating button, and the lock sits next to the count.
 */
@Composable
private fun AlbumGrid(
    albums: List<AlbumWithCount>,
    showCounts: Boolean,
    onOpenAlbum: (String) -> Unit,
    onNewAlbum: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = HitsuColors.Stroke,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = HitsuIcons.Lock,
                contentDescription = null,
                tint = HitsuColors.TextMuted,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = pluralStringResource(R.plurals.albums_count, albums.size, albums.size)
                    .uppercase(),
                style = HitsuType.Meta.copy(letterSpacing = HitsuType.Meta.fontSize * 0.08f),
                color = HitsuColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier.clickable(role = Role.Button, onClick = onNewAlbum),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = HitsuIcons.Plus,
                    contentDescription = null,
                    tint = HitsuColors.Accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.albums_new),
                    style = HitsuType.Action,
                    color = HitsuColors.Accent,
                )
            }
        }

        if (albums.isEmpty()) {
            EmptyAlbums(onNewAlbum = onNewAlbum)
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(albums.chunked(GRID_COLUMNS), key = { row -> row.first().id }) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    row.forEach { album ->
                        AlbumCell(
                            album = album,
                            showCount = showCounts,
                            onOpen = { onOpenAlbum(album.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AlbumCell(
    album: AlbumWithCount,
    showCount: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(role = Role.Button, onClick = onOpen)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(HitsuColors.BgElevated),
        ) {
            if (album.coverId != null) {
                AsyncImage(
                    model = ThumbnailKey(album.coverId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = album.name,
            style = HitsuType.Caption,
            color = HitsuColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
        )
        Text(
            text = if (showCount) album.itemCount.toString() else "",
            style = HitsuType.Meta,
            color = HitsuColors.Accent,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun EmptyAlbums(onNewAlbum: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringResource(R.string.albums_none_title), style = HitsuType.Subtitle)
        Text(
            text = stringResource(R.string.albums_none),
            style = HitsuType.Caption,
            color = HitsuColors.TextMuted,
            modifier = Modifier.padding(top = 10.dp),
        )
        HitsuButton(
            text = stringResource(R.string.albums_new),
            onClick = onNewAlbum,
            style = HitsuButtonStyle.Outlined,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
    }
}

@Composable
fun SelectionBar(count: Int, onLeave: () -> Unit, onSelectAll: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = HitsuIcons.Close,
            contentDescription = stringResource(R.string.cd_leave_selection),
            tint = HitsuColors.TextPrimary,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onLeave)
                .size(22.dp),
        )
        Text(
            text = pluralStringResource(R.plurals.home_selected, count, count),
            style = HitsuType.Title,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.action_all),
            style = HitsuType.Action,
            color = HitsuColors.Accent,
            modifier = Modifier.clickable(role = Role.Button, onClick = onSelectAll),
        )
    }
}

/**
 * Album is in the mockup too, but it waits for step 11 of the spec: a button that does nothing is
 * worse than one that is not there yet.
 */
/** The bar the mockup puts under a selection; what goes in it depends on the screen. */
@Composable
fun SelectionActionBar(modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .background(HitsuColors.Bg)
            .drawBehind {
                drawLine(
                    color = HitsuColors.Stroke,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        content = actions,
    )
}

@Composable
fun SelectionAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(text = label, style = HitsuType.Meta, color = tint)
    }
}

@Composable
private fun FilterTabs(selected: HomeTab, onSelected: (HomeTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = HitsuColors.Stroke,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        TABS.forEach { (tab, label) ->
            val active = tab == selected
            Text(
                text = stringResource(label),
                style = HitsuType.Action.copy(
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (active) HitsuColors.TextPrimary else HitsuColors.TextMuted,
                modifier = Modifier
                    .clickable(role = Role.Tab) { onSelected(tab) }
                    .padding(bottom = 12.dp)
                    .drawBehind {
                        if (active) {
                            drawLine(
                                color = HitsuColors.Accent,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    },
            )
        }
    }
}

@Composable
private fun StatusStrip(
    importStatus: ImportStatus,
    exportStatus: ExportStatus,
    receivingShare: Boolean,
    onExportStatusSeen: () -> Unit,
) {
    val exportFinished = !exportStatus.running && exportStatus.total > 0
    LaunchedEffect(exportFinished) {
        // The result of an export is worth reading, but not worth keeping on screen for good.
        if (exportFinished) {
            delay(RESULT_MS)
            onExportStatusSeen()
        }
    }

    val message = when {
        receivingShare -> stringResource(R.string.home_receiving_share)
        importStatus.running -> stringResource(R.string.home_importing, importStatus.done + 1, importStatus.total)
        exportStatus.running -> stringResource(
            R.string.home_exporting,
            exportStatus.done + exportStatus.failed + 1,
            exportStatus.total,
        )
        exportFinished && exportStatus.failed > 0 -> pluralStringResource(
            R.plurals.home_export_failed,
            exportStatus.failed,
            exportStatus.failed,
        )
        exportFinished -> pluralStringResource(
            R.plurals.home_exported,
            exportStatus.done,
            exportStatus.done,
        )
        importStatus.failedName != null -> stringResource(R.string.home_import_failed, importStatus.failedName)
        importStatus.duplicates > 0 -> pluralStringResource(
            R.plurals.import_duplicates,
            importStatus.duplicates,
            importStatus.duplicates,
        )
        else -> return
    }
    val bad = importStatus.failedName != null || (exportFinished && exportStatus.failed > 0)
    Text(
        text = message,
        style = HitsuType.Meta,
        color = if (bad) HitsuColors.Danger else HitsuColors.TextMuted,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
    )
}

private const val RESULT_MS = 4_000L

@Composable
private fun EmptyVault(onImport: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty),
            style = HitsuType.Body,
            color = HitsuColors.TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        HitsuButton(
            text = stringResource(R.string.action_import),
            onClick = onImport,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
fun MediaGrid(
    days: List<MediaDay>,
    selection: Set<String>,
    selecting: Boolean,
    onOpen: (MediaItem) -> Unit,
    onToggleSelected: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        days.forEach { day ->
            item(key = "header-" + day.date) {
                Text(
                    text = dayLabel(day.date),
                    style = HitsuType.Caption.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HitsuColors.Bg)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
                )
            }
            items(day.rows, key = { row -> row.first().id }) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    row.forEach { item ->
                        MediaCell(
                            item = item,
                            selected = item.id in selection,
                            selecting = selecting,
                            onOpen = { onOpen(item) },
                            onToggleSelected = { onToggleSelected(item.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MediaCell(
    item: MediaItem,
    selected: Boolean,
    selecting: Boolean,
    onOpen: () -> Unit,
    onToggleSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .aspectRatio(1f)
            .background(HitsuColors.BgElevated)
            .combinedClickable(
                role = Role.Button,
                // Once the selection is open, a tap adds and removes instead of opening.
                onClick = { if (selecting) onToggleSelected() else onOpen() },
                onLongClick = onToggleSelected,
            )
            .then(if (selected) Modifier.border(2.dp, HitsuColors.Accent) else Modifier),
    ) {
        AsyncImage(
            model = ThumbnailKey(item.id),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val duration = item.durationMs
        if (duration != null) {
            Text(
                text = formatDuration(duration),
                style = HitsuType.Meta.copy(fontSize = 10.5.sp),
                color = HitsuColors.TextPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 4.dp),
            )
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(HitsuColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HitsuIcons.Check,
                    contentDescription = null,
                    tint = HitsuColors.Bg,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return (totalSeconds / 60).toString() + ":" + seconds
}

@PhonePreview
@Composable
private fun HomeEmptyPreview() = HitsuTheme {
    HomeScreen(state = HomeUiState(loaded = true), onFilterSelected = {}, onImport = {})
}
