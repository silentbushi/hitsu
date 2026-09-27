package app.hitsu.vault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.hitsu.vault.R
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuMotion

@Composable
fun PinDots(
    total: Int,
    filled: Int,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    val description = stringResource(R.string.cd_pin_progress, filled, total)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(total) { index ->
            val on = index < filled
            val fill by animateColorAsState(
                targetValue = when {
                    !on -> Color.Transparent
                    error -> HitsuColors.Danger
                    else -> HitsuColors.Accent
                },
                animationSpec = HitsuMotion.standard(),
                label = "pinDot",
            )
            val ring = when {
                on -> fill
                error -> HitsuColors.Danger
                else -> HitsuColors.Stroke
            }
            Box(
                Modifier
                    .size(12.dp)
                    .background(fill, CircleShape)
                    .border(1.dp, ring, CircleShape),
            )
        }
    }
}
