package app.hitsu.vault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.hitsu.vault.ui.theme.ControlShape
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuMotion
import app.hitsu.vault.ui.theme.HitsuType

enum class HitsuButtonStyle { Primary, Outlined, Danger }

@Composable
fun HitsuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: HitsuButtonStyle = HitsuButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> HitsuColors.BgElevated
            style == HitsuButtonStyle.Primary -> if (pressed) HitsuColors.AccentPress else HitsuColors.Accent
            style == HitsuButtonStyle.Danger -> if (pressed) HitsuColors.Danger.copy(alpha = 0.10f) else Color.Transparent
            pressed -> HitsuColors.BgElevated
            else -> Color.Transparent
        },
        animationSpec = HitsuMotion.standard(),
        label = "buttonContainer",
    )
    val content = when {
        !enabled -> HitsuColors.TextMuted
        style == HitsuButtonStyle.Primary -> HitsuColors.Bg
        style == HitsuButtonStyle.Danger -> HitsuColors.Danger
        else -> HitsuColors.TextPrimary
    }
    val borderColor = when {
        !enabled || style == HitsuButtonStyle.Outlined -> HitsuColors.Stroke
        style == HitsuButtonStyle.Danger -> HitsuColors.Danger
        else -> null
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(ControlShape)
            .background(container)
            .then(borderColor?.let { Modifier.border(1.dp, it, ControlShape) } ?: Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HitsuType.Label, color = content)
    }
}
