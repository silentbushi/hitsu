package app.hitsu.vault.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hitsu.vault.R
import app.hitsu.vault.ui.theme.ControlShape
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuType

private sealed interface Key {
    data class Digit(val value: Int) : Key
    data object Backspace : Key
    data object Blank : Key
}

private val Layout: List<List<Key>> = listOf(
    listOf(Key.Digit(1), Key.Digit(2), Key.Digit(3)),
    listOf(Key.Digit(4), Key.Digit(5), Key.Digit(6)),
    listOf(Key.Digit(7), Key.Digit(8), Key.Digit(9)),
    listOf(Key.Blank, Key.Digit(0), Key.Backspace),
)

@Composable
fun PinKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyHeight: Dp = 64.dp,
) {
    val contentColor = if (enabled) HitsuColors.TextPrimary else HitsuColors.TextPrimary.copy(alpha = 0.2f)
    Column(modifier.widthIn(max = 300.dp).fillMaxWidth()) {
        Layout.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    KeypadKey(
                        key = key,
                        height = keyHeight,
                        enabled = enabled,
                        onClick = {
                            when (key) {
                                is Key.Digit -> onDigit(key.value)
                                Key.Backspace -> onBackspace()
                                Key.Blank -> Unit
                            }
                        },
                    ) {
                        when (key) {
                            is Key.Digit -> Text(key.value.toString(), style = HitsuType.Keypad, color = contentColor)
                            Key.Backspace -> Icon(
                                imageVector = HitsuIcons.Delete,
                                contentDescription = stringResource(R.string.cd_backspace),
                                tint = contentColor,
                                modifier = Modifier.size(24.dp),
                            )
                            Key.Blank -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.KeypadKey(
    key: Key,
    height: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(height)
            .clip(ControlShape)
            .clickable(enabled = enabled && key != Key.Blank, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
