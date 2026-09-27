package app.hitsu.vault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hitsu.vault.ui.theme.ControlShape
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuType

/**
 * Drawn inside the screen rather than in a Dialog window, which would be a separate window and
 * would not inherit FLAG_SECURE.
 */
@Composable
fun HitsuDialog(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(HitsuColors.BgElevated, ControlShape)
                .border(1.dp, HitsuColors.Stroke, ControlShape)
                .padding(24.dp),
        ) {
            Text(text = title, style = HitsuType.Subtitle.copy(lineHeight = 22.sp))
            Text(
                text = body,
                style = HitsuType.Caption.copy(fontSize = 13.5.sp, lineHeight = 21.sp),
                color = HitsuColors.TextMuted,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = HitsuType.Meta.copy(fontSize = 12.5.sp),
                    color = HitsuColors.TextMuted,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            if (content != null) {
                Column(
                    modifier = Modifier.padding(top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
            }
            Column(
                modifier = Modifier.padding(top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = actions,
            )
        }
    }
}
