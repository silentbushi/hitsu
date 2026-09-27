package app.hitsu.vault.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hitsu.vault.R
import app.hitsu.vault.ui.components.HitsuButton
import app.hitsu.vault.ui.components.HitsuButtonStyle
import app.hitsu.vault.ui.components.HitsuIcons
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuType

private data class SheetContent(
    val tint: Color,
    val title: Int,
    val body: Int,
    val bodyColor: Color,
    val action: Int,
    val actionStyle: HitsuButtonStyle,
)

private fun BiometricState.sheetContent(): SheetContent? = when (this) {
    BiometricState.Requested -> SheetContent(
        tint = HitsuColors.Accent,
        title = R.string.biometric_requested_title,
        body = R.string.biometric_requested_body,
        bodyColor = HitsuColors.TextMuted,
        action = R.string.action_use_pin,
        actionStyle = HitsuButtonStyle.Outlined,
    )
    BiometricState.NotRecognized -> SheetContent(
        tint = HitsuColors.Danger,
        title = R.string.biometric_failed_title,
        body = R.string.biometric_failed_body,
        bodyColor = HitsuColors.Danger,
        action = R.string.action_use_pin,
        actionStyle = HitsuButtonStyle.Outlined,
    )
    BiometricState.Invalidated -> SheetContent(
        tint = HitsuColors.TextMuted,
        title = R.string.biometric_invalidated_title,
        body = R.string.biometric_invalidated_body,
        bodyColor = HitsuColors.TextMuted,
        action = R.string.action_enter_pin,
        actionStyle = HitsuButtonStyle.Primary,
    )
    BiometricState.Unavailable, BiometricState.Available -> null
}

@Composable
fun BiometricSheet(state: BiometricState, onUsePin: () -> Unit) {
    val content = state.sheetContent() ?: return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(HitsuColors.BgElevated)
                .drawBehind { drawLine(HitsuColors.Stroke, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = HitsuIcons.Fingerprint,
                contentDescription = null,
                tint = content.tint,
                modifier = Modifier.size(46.dp),
            )
            Text(
                text = stringResource(content.title),
                style = HitsuType.Subtitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = stringResource(content.body),
                style = HitsuType.Caption.copy(fontSize = 13.5.sp, lineHeight = 21.sp),
                color = content.bodyColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .widthIn(max = 280.dp),
            )
            HitsuButton(
                text = stringResource(content.action),
                onClick = onUsePin,
                style = content.actionStyle,
                modifier = Modifier
                    .padding(top = 26.dp)
                    .widthIn(max = 300.dp)
                    .fillMaxWidth(),
            )
        }
    }
}
