package app.hitsu.vault.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.text.format.Formatter
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.hitsu.vault.R
import app.hitsu.vault.ui.components.HitsuButton
import app.hitsu.vault.ui.components.HitsuButtonStyle
import app.hitsu.vault.ui.components.HitsuDialog
import app.hitsu.vault.ui.components.HitsuIcons
import app.hitsu.vault.ui.components.HitsuSwitch
import app.hitsu.vault.ui.theme.ControlShape
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuMotion
import app.hitsu.vault.ui.theme.HitsuType
import kotlin.math.abs
import android.content.Intent
import android.os.storage.StorageManager
import android.provider.Settings

private const val VOLUME_DRAG_RANGE_PX = 600f
private const val BRIGHTNESS_DRAG_RANGE_PX = 600f

@Composable
fun VideoPlayerRoute(onClose: () -> Unit, viewModel: VideoPlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    /*
     * Reaching onStop means the app is gone from the screen, including the case where the floating
     * window was dismissed: playing on with nothing visible would leave vault audio running.
     */
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onPause() }

    /*
     * Coming back from the system storage manager is the one case where the answer may have changed
     * without the user doing anything here, so the video tries again by itself rather than leaving a
     * dialog that now says something untrue.
     */
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.phase is PlaybackPhase.NoSpace) viewModel.onRetryAfterSpace()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onPause()
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    VideoPlayerScreen(state = state, viewModel = viewModel, onClose = onClose)
}

/** Registers the player with the activity so the floating window's buttons reach it. */
@Composable
private fun PipBinding(state: VideoPlayerUiState, viewModel: VideoPlayerViewModel, host: PipHost?) {
    DisposableEffect(host) {
        host?.handler = object : PipHandler {
            override fun onPlayPause() = viewModel.onPlayPause()
            override fun onSkip(forward: Boolean) = viewModel.onSkip(forward)
            override fun onUserLeaving() = viewModel.onPause()
            override fun onPictureInPictureChanged(inPictureInPicture: Boolean) =
                viewModel.onPictureInPictureChanged(inPictureInPicture)
        }
        onDispose { host?.handler = null }
    }
    LaunchedEffect(state.playing) { host?.updateActions(state.playing) }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    state: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val pipHost = LocalPipHost.current
    PipBinding(state = state, viewModel = viewModel, host = pipHost)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = viewModel.player
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val item = state.item ?: return@onGloballyPositioned
                    val bounds = coordinates.boundsInWindow()
                    pipHost?.describeVideo(
                        width = item.width,
                        height = item.height,
                        bounds = android.graphics.Rect(
                            bounds.left.toInt(),
                            bounds.top.toInt(),
                            bounds.right.toInt(),
                            bounds.bottom.toInt(),
                        ),
                    )
                },
        )

        if (state.inPictureInPicture) return@Box

        PlayerGestures(
            state = state,
            viewModel = viewModel,
            audio = audio,
            onBrightness = { fraction -> activity?.applyBrightness(fraction) },
        )

        when (val phase = state.phase) {
            is PlaybackPhase.Decrypting -> DecryptingOverlay(phase.progress, onClose)
            is PlaybackPhase.NoSpace -> NoSpaceDialog(
                phase = phase,
                onClose = onClose,
                onFreeSpace = {
                    viewModel.onFreeingSpace()
                    context.startActivity(storageManagerIntent(context))
                },
            )
            PlaybackPhase.Failed -> FailedOverlay(onClose)
            PlaybackPhase.Ready -> Unit
        }

        state.feedback?.let { FeedbackOverlay(it) }

        AnimatedVisibility(
            visible = state.chromeVisible && state.phase == PlaybackPhase.Ready,
            enter = fadeIn(HitsuMotion.standard()),
            exit = fadeOut(HitsuMotion.standard()),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PlayerTopBar(name = state.item?.originalName, onClose = onClose)
        }

        AnimatedVisibility(
            visible = state.chromeVisible && state.phase == PlaybackPhase.Ready,
            enter = fadeIn(HitsuMotion.standard()),
            exit = fadeOut(HitsuMotion.standard()),
            modifier = Modifier.align(Alignment.Center),
        ) {
            CenterPlayButton(playing = state.playing, onClick = viewModel::onPlayPause)
        }

        AnimatedVisibility(
            visible = state.chromeVisible && state.phase == PlaybackPhase.Ready,
            enter = fadeIn(HitsuMotion.standard()),
            exit = fadeOut(HitsuMotion.standard()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerControls(state = state, viewModel = viewModel, pipHost = pipHost)
        }

        if (state.menuVisible) {
            PlaybackMenu(
                state = state,
                onSpeed = viewModel::onSpeed,
                onToggleLoop = viewModel::onToggleLoop,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

/**
 * Spec §8: tap toggles the chrome, a horizontal drag seeks, a vertical drag changes the system
 * volume on the right half and the window brightness on the left, and a double tap jumps ten
 * seconds towards the side that was tapped.
 */
@Composable
private fun PlayerGestures(
    state: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    audio: AudioManager,
    onBrightness: (Float) -> Unit,
) {
    var dragAxis = remember { DragAxis() }
    // Kept across gestures so brightness resumes where it was left, not from the middle.
    var brightness by remember { mutableFloatStateOf(0.5f) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(state.phase) {
                if (state.phase != PlaybackPhase.Ready) return@pointerInput
                detectTapGestures(
                    onTap = { viewModel.onToggleChrome() },
                    onDoubleTap = { position ->
                        viewModel.onSkip(forward = position.x > size.width / 2f)
                    },
                )
            }
            .pointerInput(state.phase) {
                if (state.phase != PlaybackPhase.Ready) return@pointerInput
                detectDragGestures(
                    onDragStart = { start ->
                        dragAxis = DragAxis(
                            startX = start.x,
                            width = size.width.toFloat(),
                            // Read the live level once: the system volume moves in whole steps, so
                            // the gesture has to accumulate a fraction and round only at the end.
                            volume = audio.volumeFraction(),
                        )
                    },
                    onDragEnd = {
                        if (dragAxis.horizontal == true) viewModel.onSeekDragFinished()
                        dragAxis = DragAxis()
                    },
                    onDragCancel = { dragAxis = DragAxis() },
                ) { change, drag ->
                    change.consume()
                    if (dragAxis.horizontal == null) {
                        dragAxis.horizontal = abs(drag.x) > abs(drag.y)
                    }
                    if (dragAxis.horizontal == true) {
                        viewModel.onSeekDrag(drag.x / size.width.toFloat())
                    } else {
                        val onRight = dragAxis.startX > size.width / 2f
                        if (onRight) {
                            dragAxis.volume = (dragAxis.volume - drag.y / VOLUME_DRAG_RANGE_PX)
                                .coerceIn(0f, 1f)
                            audio.applyVolume(dragAxis.volume)
                            viewModel.onVolumeChanged((dragAxis.volume * 100).toInt())
                        } else {
                            brightness = (brightness - drag.y / BRIGHTNESS_DRAG_RANGE_PX).coerceIn(0f, 1f)
                            onBrightness(brightness)
                            viewModel.onBrightnessChanged((brightness * 100).toInt())
                        }
                    }
                }
            },
    )
}

private class DragAxis(
    val startX: Float = 0f,
    val width: Float = 1f,
    var horizontal: Boolean? = null,
    var volume: Float = 0f,
)

private fun AudioManager.volumeFraction(): Float {
    val max = getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    return if (max <= 0) 0f else getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

private fun AudioManager.applyVolume(fraction: Float) {
    val max = getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val target = Math.round(fraction * max).coerceIn(0, max)
    if (target != getStreamVolume(AudioManager.STREAM_MUSIC)) {
        setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }
}

private fun Activity.applyBrightness(fraction: Float) {
    window.attributes = window.attributes.apply { screenBrightness = fraction.coerceIn(0.01f, 1f) }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Big target in the middle while the chrome is up; the bar keeps its own small one. */
@Composable
private fun CenterPlayButton(playing: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, HitsuColors.Stroke, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) HitsuIcons.Pause else HitsuIcons.Play,
            contentDescription = stringResource(if (playing) R.string.cd_pause else R.string.cd_play),
            tint = HitsuColors.TextPrimary,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun PlayerTopBar(name: String?, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlayerIcon(HitsuIcons.Close, stringResource(R.string.cd_close), onClose)
        if (name != null) {
            Text(name, style = HitsuType.Caption, color = HitsuColors.TextPrimary)
        }
    }
}

@Composable
private fun PlayerControls(
    state: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    pipHost: PipHost? = null,
) {
    val position = state.scrubbingMs ?: state.positionMs
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScrubBar(
            positionMs = position,
            durationMs = state.durationMs,
            scrubbing = state.scrubbingMs != null,
            onScrub = viewModel::onScrub,
            onScrubFinished = viewModel::onScrubFinished,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerIcon(
                icon = if (state.playing) HitsuIcons.Pause else HitsuIcons.Play,
                contentDescription = stringResource(
                    if (state.playing) R.string.cd_pause else R.string.cd_play,
                ),
                onClick = viewModel::onPlayPause,
            )
            Spacer(Modifier.width(18.dp))
            Row {
                Text(formatTime(position), style = HitsuType.Meta, color = HitsuColors.TextPrimary)
                Text(
                    text = " / " + formatTime(state.durationMs),
                    style = HitsuType.Meta,
                    color = HitsuColors.TextMuted,
                )
            }
            Spacer(Modifier.weight(1f))
            PlayerIcon(
                icon = if (state.muted) HitsuIcons.VolumeOff else HitsuIcons.VolumeOn,
                contentDescription = stringResource(R.string.cd_mute),
                onClick = viewModel::onToggleMute,
            )
            if (pipHost?.supported == true) {
                Spacer(Modifier.width(18.dp))
                PlayerIcon(
                    icon = HitsuIcons.PictureInPicture,
                    contentDescription = stringResource(R.string.cd_pip),
                    onClick = { pipHost.enter(state.playing) },
                )
            }
            Spacer(Modifier.width(18.dp))
            PlayerIcon(
                icon = HitsuIcons.More,
                contentDescription = stringResource(R.string.cd_menu),
                onClick = viewModel::onToggleMenu,
            )
        }
    }
}

@Composable
private fun ScrubBar(
    positionMs: Long,
    durationMs: Long,
    scrubbing: Boolean,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragEnd = { onScrubFinished() },
                    onDragCancel = { onScrubFinished() },
                ) { change, _ ->
                    change.consume()
                    onScrub((change.position.x / size.width * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    onScrub((offset.x / size.width * durationMs).toLong())
                    onScrubFinished()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(HitsuColors.Stroke),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(2.dp)
                .background(HitsuColors.Accent),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(24.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .size(if (scrubbing) 18.dp else 12.dp)
                    .clip(CircleShape)
                    .background(HitsuColors.Accent),
            )
        }
        if (scrubbing) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = formatTime(positionMs),
                    style = HitsuType.Meta,
                    color = HitsuColors.TextPrimary,
                    modifier = Modifier
                        .padding(bottom = 44.dp)
                        .background(HitsuColors.BgElevated, ControlShape)
                        .border(1.dp, HitsuColors.Stroke, ControlShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PlaybackMenu(
    state: VideoPlayerUiState,
    onSpeed: (Float) -> Unit,
    onToggleLoop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = 96.dp)
            .background(HitsuColors.BgElevated, ControlShape)
            .border(1.dp, HitsuColors.Stroke, ControlShape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.player_speed), style = HitsuType.Meta, color = HitsuColors.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SPEEDS.forEach { speed ->
                val selected = speed == state.speed
                Text(
                    text = formatSpeed(speed),
                    style = HitsuType.Meta,
                    color = if (selected) HitsuColors.Bg else HitsuColors.TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) HitsuColors.Accent else Color.Transparent)
                        .clickable(role = Role.Button) { onSpeed(speed) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HitsuColors.Stroke),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.player_loop),
                style = HitsuType.Body,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(24.dp))
            Box(Modifier.clickable(role = Role.Switch) { onToggleLoop() }) {
                HitsuSwitch(checked = state.looping)
            }
        }
    }
}

@Composable
private fun FeedbackOverlay(feedback: GestureFeedback) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (feedback) {
            is GestureFeedback.Seek -> Row(
                modifier = Modifier.pill(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = formatTime(feedback.targetMs),
                    style = HitsuType.Title.copy(fontSize = 26.sp),
                    color = HitsuColors.TextPrimary,
                )
                Text(
                    text = formatDelta(feedback.deltaMs),
                    style = HitsuType.Meta,
                    color = HitsuColors.Accent,
                )
            }
            is GestureFeedback.Skip -> Column(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(HitsuColors.TextPrimary.copy(alpha = 0.10f))
                    .align(if (feedback.forward) Alignment.CenterEnd else Alignment.CenterStart),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = if (feedback.forward) HitsuIcons.Forward else HitsuIcons.Rewind,
                    contentDescription = null,
                    tint = HitsuColors.TextPrimary,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = stringResource(
                        if (feedback.forward) R.string.player_skip_forward else R.string.player_skip_back,
                    ),
                    style = HitsuType.Meta,
                    color = HitsuColors.Accent,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            is GestureFeedback.Volume -> LevelPill(
                icon = HitsuIcons.VolumeOn,
                percent = feedback.percent,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
            is GestureFeedback.Brightness -> LevelPill(
                icon = HitsuIcons.Brightness,
                percent = feedback.percent,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

private fun Modifier.pill(): Modifier = this
    .background(Color.Black.copy(alpha = 0.72f), ControlShape)
    .border(1.dp, HitsuColors.Stroke, ControlShape)
    .padding(horizontal = 20.dp, vertical = 14.dp)

@Composable
private fun LevelPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    percent: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(horizontal = 24.dp)
            .background(Color.Black.copy(alpha = 0.72f), ControlShape)
            .border(1.dp, HitsuColors.Stroke, ControlShape)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = HitsuColors.TextPrimary, modifier = Modifier.size(20.dp))
        Box(
            Modifier
                .width(3.dp)
                .height(140.dp)
                .background(HitsuColors.Stroke),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(percent / 100f)
                    .background(HitsuColors.TextPrimary),
            )
        }
        Text("$percent", style = HitsuType.Meta, color = HitsuColors.TextPrimary)
    }
}

@Composable
private fun DecryptingOverlay(progress: Float, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PlayerIcon(
            icon = HitsuIcons.Close,
            contentDescription = stringResource(R.string.cd_close),
            onClick = onClose,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp),
        )
        Text(
            text = stringResource(R.string.player_decrypting, (progress * 100).toInt()),
            style = HitsuType.Caption,
            color = HitsuColors.TextMuted,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun FailedOverlay(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.player_failed),
            style = HitsuType.Body,
            color = HitsuColors.Danger,
            modifier = Modifier.clickable(role = Role.Button, onClick = onClose),
        )
    }
}

@Composable
private fun NoSpaceDialog(
    phase: PlaybackPhase.NoSpace,
    onClose: () -> Unit,
    onFreeSpace: () -> Unit,
) {
    val context = LocalContext.current
    HitsuDialog(
        title = stringResource(R.string.player_no_space_title),
        body = stringResource(
            R.string.player_no_space_body,
            Formatter.formatShortFileSize(context, phase.requiredBytes),
            Formatter.formatShortFileSize(context, phase.freeBytes),
        ),
    ) {
        HitsuButton(
            text = stringResource(R.string.action_free_space),
            onClick = onFreeSpace,
            modifier = Modifier.fillMaxWidth(),
        )
        HitsuButton(
            text = stringResource(R.string.action_close),
            onClick = onClose,
            style = HitsuButtonStyle.Outlined,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The system screen for making room, which knows about caches and apps Hitsu cannot see. Older or
 * trimmed-down systems may not have it, so the plain storage settings are the fallback.
 */
private fun storageManagerIntent(context: Context): Intent {
    val manage = Intent(StorageManager.ACTION_MANAGE_STORAGE)
    return manage.takeIf { it.resolveActivityInfo(context.packageManager, 0) != null }
        ?: Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
}

@Composable
private fun PlayerIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = HitsuColors.TextPrimary,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .size(22.dp),
    )
}

internal fun formatTime(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%02d:%02d".format(minutes, seconds)
}

internal fun formatDelta(millis: Long): String {
    val sign = if (millis >= 0) "+" else "-"
    return sign + formatTime(abs(millis))
}

internal fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
