package app.hitsu.vault.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.hitsu.vault.R
import app.hitsu.vault.ui.theme.HitsuColors

/** Spec §7.11: the way into a pass, the same icon wherever it is offered. */
@Composable
fun PresentAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Icon(
        imageVector = HitsuIcons.Play,
        contentDescription = stringResource(R.string.cd_slideshow),
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
