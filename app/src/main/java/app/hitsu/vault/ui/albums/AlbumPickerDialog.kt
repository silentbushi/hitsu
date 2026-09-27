package app.hitsu.vault.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.hitsu.vault.R
import app.hitsu.vault.data.db.AlbumWithCount
import app.hitsu.vault.ui.components.HitsuButton
import app.hitsu.vault.ui.components.HitsuButtonStyle
import app.hitsu.vault.ui.components.HitsuDialog
import app.hitsu.vault.ui.theme.ControlShape
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuType

/**
 * Where the selected items should go. Existing albums first, because filing into one that already
 * exists is the common case; making one is the same field, typed instead of tapped.
 */
@Composable
fun AlbumPickerDialog(
    albums: List<AlbumWithCount>,
    count: Int,
    onCancel: () -> Unit,
    onPick: (albumId: String) -> Unit,
    onCreate: (name: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    HitsuDialog(
        title = stringResource(R.string.albums_add_title),
        body = stringResource(R.string.albums_add_body, count),
        modifier = Modifier.imePadding(),
        content = {
            if (albums.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    albums.forEach { album ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onPick(album.id) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(album.name, style = HitsuType.Body)
                            Text(
                                text = album.itemCount.toString(),
                                style = HitsuType.Meta,
                                color = HitsuColors.TextMuted,
                            )
                        }
                    }
                }
            }
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
                text = stringResource(R.string.albums_create),
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
