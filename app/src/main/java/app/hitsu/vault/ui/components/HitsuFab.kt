package app.hitsu.vault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.hitsu.vault.ui.theme.ControlShape
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuMotion

/** Spec §10.4: square 56, radius 8, accent. Not a pill, no label. */
@Composable
fun HitsuFab(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = if (pressed) HitsuColors.AccentPress else HitsuColors.Accent,
        animationSpec = HitsuMotion.standard(),
        label = "fabContainer",
    )
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(ControlShape)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = HitsuIcons.Plus,
            contentDescription = contentDescription,
            tint = HitsuColors.Bg,
            modifier = Modifier.size(24.dp),
        )
    }
}
