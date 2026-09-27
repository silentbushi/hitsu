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

private val TABS = listOf(
    MediaFilter.All to R.string.home_tab_all,
    MediaFilter.Photos to R.string.home_tab_photos,
    MediaFilter.Videos to R.string.home_tab_videos,
)

@Composable
fun HomeRoute(
    onOpenPhoto: (String, MediaFilter) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris: List<Uri> -> viewModel.onPicked(uris) }

    HomeScreen(
        state = state,
        onFilterSelected = viewModel::onFilterSelected,
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
    onFilterSelected: (MediaFilter) -> Unit,
    onImport: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpen: (MediaItem) -> Unit = {},
    onToggleSelected: (String) -> Unit = {},
    onSelectAll: (List<String>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onExportSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onExportStatusSeen: () -> Unit = {},
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
            FilterTabs(selected = state.filter, onSelected = onFilterSelected)
            StatusStrip(
                importStatus = state.importStatus,
                exportStatus = state.exportStatus,
                receivingShare = state.receivingShare,
                onExportStatusSeen = onExportStatusSeen,
            )

            Box(Modifier.fillMaxSize()) {
                if (state.showEmptyState) {
                    EmptyVault(onImport = onImport)
                } else {
                    MediaGrid(
                        days = state.days,
                        selection = state.selection,
                        selecting = state.selecting,
                        onOpen = onOpen,
                        onToggleSelected = onToggleSelected,
                    )
                }
                if (state.selecting) {
                    SelectionActions(
                        onExport = { confirming = Confirmation.Export(state.selection.size) },
                        onDelete = { confirming = Confirmation.Delete(state.selection.size) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else {
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

@Composable
private fun SelectionBar(count: Int, onLeave: () -> Unit, onSelectAll: () -> Unit) {
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
@Composable
private fun SelectionActions(
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    ) {
        SelectionAction(
            icon = HitsuIcons.Export,
            label = stringResource(R.string.action_export),
            tint = HitsuColors.TextPrimary,
            onClick = onExport,
        )
        SelectionAction(
            icon = HitsuIcons.Delete,
            label = stringResource(R.string.action_delete),
            tint = HitsuColors.Danger,
            onClick = onDelete,
        )
    }
}

@Composable
private fun SelectionAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
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
private fun FilterTabs(selected: MediaFilter, onSelected: (MediaFilter) -> Unit) {
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
        TABS.forEach { (filter, label) ->
            val active = filter == selected
            Text(
                text = stringResource(label),
                style = HitsuType.Action.copy(
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (active) HitsuColors.TextPrimary else HitsuColors.TextMuted,
                modifier = Modifier
                    .clickable(role = Role.Tab) { onSelected(filter) }
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
private fun MediaGrid(
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
