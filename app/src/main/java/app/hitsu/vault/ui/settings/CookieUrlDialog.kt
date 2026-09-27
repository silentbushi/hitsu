package app.hitsu.vault.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.hitsu.vault.R
import app.hitsu.vault.ui.components.HitsuButton
import app.hitsu.vault.ui.components.HitsuButtonStyle
import app.hitsu.vault.ui.components.HitsuDialog
import app.hitsu.vault.ui.theme.ControlShape
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuType

/**
 * Asks where to log in before opening the browser window. The address is the user's to choose: the
 * sites move their login pages often, and several of them only answer from their mobile address.
 */
@Composable
fun CookieUrlDialog(onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val confirm = { if (url.isNotBlank()) onConfirm(normalizedUrl(url)) }

    HitsuDialog(
        title = stringResource(R.string.cookies_url_title),
        body = stringResource(R.string.cookies_url_body),
        content = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(HitsuColors.SurfaceInput, ControlShape)
                    .border(1.dp, HitsuColors.Stroke, ControlShape)
                    .padding(horizontal = 14.dp)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    singleLine = true,
                    textStyle = HitsuType.Body.copy(color = HitsuColors.TextPrimary),
                    cursorBrush = SolidColor(HitsuColors.Accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { confirm() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { field ->
                        if (url.isEmpty()) {
                            Text(
                                text = stringResource(R.string.cookies_url_placeholder),
                                style = HitsuType.Body,
                                color = HitsuColors.TextMuted,
                            )
                        }
                        field()
                    },
                )
                Text(
                    text = stringResource(R.string.action_paste),
                    style = HitsuType.Meta,
                    color = HitsuColors.Accent,
                    modifier = Modifier.clickable(role = Role.Button) {
                        clipboard.getText()?.text?.let { url = it.trim() }
                    },
                )
            }
        },
        actions = {
            HitsuButton(
                text = stringResource(R.string.action_confirm),
                onClick = confirm,
                enabled = url.isNotBlank(),
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

/** What people paste or type is a bare host as often as a full address. */
private fun normalizedUrl(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
}
