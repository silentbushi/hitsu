package app.hitsu.vault.ui.viewer

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hitsu.vault.R
import app.hitsu.vault.domain.MediaItem
import app.hitsu.vault.ui.components.HitsuIcons
import app.hitsu.vault.ui.format.dayTimeLabel
import app.hitsu.vault.ui.media.FullImageKey
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuMotion
import app.hitsu.vault.ui.theme.HitsuType
import coil3.compose.AsyncImage
import kotlin.math.abs

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val DISMISS_DISTANCE_PX = 320f

@Composable
fun PhotoViewerRoute(onClose: () -> Unit, viewModel: PhotoViewerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PhotoViewerScreen(
        state = state,
        onClose = onClose,
        onToggleChrome = viewModel::onToggleChrome,
        onToggleInfo = viewModel::onToggleInfo,
    )
}

@Composable
fun PhotoViewerScreen(
    state: PhotoViewerUiState,
    onClose: () -> Unit,
    onToggleChrome: () -> Unit,
    onToggleInfo: () -> Unit,
) {
    // Pure black, like the player: nothing around the photo competes with it.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (!state.loaded) return@Box

        val pagerState = rememberPagerState(initialPage = state.startIndex) { state.items.size }
        var zoomed by remember { mutableStateOf(false) }
        LaunchedEffect(pagerState.currentPage) { zoomed = false }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomablePhoto(
                item = state.items[page],
                onTap = onToggleChrome,
                onDismiss = onClose,
                onZoomChanged = { isZoomed -> if (page == pagerState.currentPage) zoomed = isZoomed },
            )
        }

        val current = state.items.getOrNull(pagerState.currentPage)
        AnimatedVisibility(
            visible = state.chromeVisible,
            enter = fadeIn(HitsuMotion.standard()),
            exit = fadeOut(HitsuMotion.standard()),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ViewerTopBar(
                index = pagerState.currentPage + 1,
                total = state.items.size,
                onClose = onClose,
                onToggleInfo = onToggleInfo,
            )
        }

        AnimatedVisibility(
            visible = state.chromeVisible && state.infoVisible && current != null,
            enter = fadeIn(HitsuMotion.standard()),
            exit = fadeOut(HitsuMotion.standard()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            current?.let { PhotoInfo(it) }
        }
    }
}

@Composable
private fun ZoomablePhoto(
    item: MediaItem,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    var dismissOffset by remember(item.id) { mutableFloatStateOf(0f) }
    var size by remember(item.id) { mutableStateOf(IntSize.Zero) }

    fun clamp() {
        val maxX = (size.width * (scale - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (size.height * (scale - 1f) / 2f).coerceAtLeast(0f)
        offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    fun setScale(target: Float) {
        scale = target.coerceIn(1f, MAX_SCALE)
        if (scale == 1f) offset = Offset.Zero else clamp()
        onZoomChanged(scale > 1f)
    }

    val zoomed = scale > 1f
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(item.id, zoomed) {
                if (zoomed) {
                    // Zoomed in, one finger pans; the pager is disabled meanwhile.
                    detectTransformGestures { _, pan, zoom, _ ->
                        offset += pan
                        setScale(scale * zoom)
                    }
                } else {
                    // At rest only a real pinch reacts, so horizontal swipes still reach the pager.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) {
                                    offset += event.calculatePan()
                                    setScale(scale * zoom)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
            .pointerInput(item.id, zoomed) {
                if (zoomed) return@pointerInput
                // Swipe down to close, spec §7.5.
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (abs(dismissOffset) > DISMISS_DISTANCE_PX) onDismiss() else dismissOffset = 0f
                    },
                    onDragCancel = { dismissOffset = 0f },
                ) { change, delta ->
                    dismissOffset += delta
                    change.consume()
                }
            }
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { setScale(if (scale > 1f) 1f else DOUBLE_TAP_SCALE) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = FullImageKey(item.id),
            contentDescription = stringResource(R.string.cd_photo),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y + dismissOffset
                    alpha = 1f - (abs(dismissOffset) / (DISMISS_DISTANCE_PX * 2f)).coerceIn(0f, 0.6f)
                }
                .onSizeChanged { size = it },
        )
    }
}

@Composable
private fun ViewerTopBar(index: Int, total: Int, onClose: () -> Unit, onToggleInfo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChromeIcon(HitsuIcons.ChevronLeft, stringResource(R.string.cd_close), onClose)
        Text(
            text = stringResource(R.string.viewer_index, index, total),
            style = HitsuType.Meta,
            color = HitsuColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        ChromeIcon(HitsuIcons.Info, stringResource(R.string.cd_info), onToggleInfo)
    }
}

@Composable
private fun ChromeIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = HitsuColors.TextPrimary,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .size(22.dp),
    )
}

@Composable
private fun PhotoInfo(item: MediaItem) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item.originalName?.let { InfoRow(stringResource(R.string.viewer_info_name), it) }
        InfoRow(stringResource(R.string.viewer_info_imported), dayTimeLabel(item.importedAt))
        InfoRow(
            label = stringResource(R.string.viewer_info_dimensions),
            value = stringResource(R.string.viewer_info_dimensions_value, item.width, item.height),
        )
        InfoRow(
            label = stringResource(R.string.viewer_info_size),
            value = Formatter.formatShortFileSize(context, item.sizeBytes),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = HitsuType.Meta, color = HitsuColors.TextMuted)
        Text(value, style = HitsuType.Meta, color = HitsuColors.TextPrimary)
    }
}
