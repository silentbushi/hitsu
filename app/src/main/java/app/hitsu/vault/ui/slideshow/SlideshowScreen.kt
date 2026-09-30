package app.hitsu.vault.ui.slideshow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hitsu.vault.R
import app.hitsu.vault.ui.components.HitsuIcons
import app.hitsu.vault.ui.media.FullImageKey
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuMotion
import app.hitsu.vault.ui.theme.HitsuType
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay

/** How long the control stays up before it gets out of the way of the photos. */
private const val CHROME_TIMEOUT_MS = 3_000L

@Composable
fun SlideshowRoute(onClose: () -> Unit, viewModel: SlideshowViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // No loop and the pass ran out, or nothing to show: leaving is the whole ending (spec §7.11).
    LaunchedEffect(state.finished) { if (state.finished) onClose() }

    SlideshowScreen(
        state = state,
        onClose = onClose,
        onToggleChrome = viewModel::onToggleChrome,
        onChromeHidden = viewModel::onChromeHidden,
        onTogglePlay = viewModel::onTogglePlay,
    )
}

@Composable
fun SlideshowScreen(
    state: SlideshowUiState,
    onClose: () -> Unit,
    onToggleChrome: () -> Unit = {},
    onChromeHidden: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
) {
    KeepScreenOn()
    PreloadNext(state)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleChrome,
            ),
    ) {
        /*
         * Spec §7.11: the photo on its way out dissolves into the one coming in. Keyed by id so a
         * lap of a shuffled pass that lands on the same photo does not fade it into itself.
         */
        Crossfade(
            targetState = state.current?.id,
            animationSpec = HitsuMotion.crossfade(),
            modifier = Modifier.fillMaxSize(),
            label = "slideshow",
        ) { id ->
            if (id != null) {
                AsyncImage(
                    model = FullImageKey(id),
                    contentDescription = stringResource(R.string.cd_photo),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (state.chromeVisible) {
            // Untouched for a while: the control leaves on its own rather than sitting on the photo.
            LaunchedEffect(state.index, state.playing) {
                if (state.playing) {
                    delay(CHROME_TIMEOUT_MS)
                    onChromeHidden()
                }
            }
        }

        AnimatedVisibility(
            visible = state.chromeVisible,
            enter = fadeIn(HitsuMotion.standard()),
            exit = fadeOut(HitsuMotion.standard()),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ChromeIcon(HitsuIcons.ChevronLeft, stringResource(R.string.cd_close), onClose)
                Text(
                    text = stringResource(R.string.viewer_index, state.index + 1, state.items.size),
                    style = HitsuType.Meta,
                    color = HitsuColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                ChromeIcon(
                    icon = if (state.playing) HitsuIcons.Pause else HitsuIcons.Play,
                    contentDescription = stringResource(
                        if (state.playing) R.string.cd_pause else R.string.cd_play,
                    ),
                    onClick = onTogglePlay,
                )
            }
        }
    }
}

/**
 * The next photo is decrypted and decoded ahead of time, so the crossfade has something to fade into
 * instead of a black gap. Measured on the phone: a full-size photo takes longer to decrypt than the
 * 600 ms of the transition.
 */
@Composable
private fun PreloadNext(state: SlideshowUiState) {
    val context = LocalContext.current
    val nextId = state.next?.id
    LaunchedEffect(nextId) {
        if (nextId == null) return@LaunchedEffect
        SingletonImageLoader.get(context).enqueue(
            ImageRequest.Builder(context).data(FullImageKey(nextId)).build(),
        )
    }
}

/** Spec §7.11: nobody touches the phone during a pass, and the screen must not go dark. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun ChromeIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
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
