package app.hitsu.vault.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.hitsu.vault.R
import app.hitsu.vault.ui.components.HitsuButton
import app.hitsu.vault.ui.components.HitsuButtonStyle
import app.hitsu.vault.ui.components.HitsuDialog
import app.hitsu.vault.ui.theme.ControlShape
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuType

/** An album made from the tab itself, before there is anything to put in it. */
@Composable
fun NewAlbumDialog(onCancel: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    HitsuDialog(
        title = stringResource(R.string.albums_new_title),
        body = stringResource(R.string.albums_new_body),
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onCreate(name) }),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { field ->
                        if (name.isEmpty()) {
                            Text(
                                text = stringResource(R.string.albums_new_hint),
                                style = HitsuType.Body,
                                color = HitsuColors.TextMuted,
                            )
                        }
                        field()
                    },
                )
            }
        },
        actions = {
            HitsuButton(
                text = stringResource(R.string.albums_new),
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
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
