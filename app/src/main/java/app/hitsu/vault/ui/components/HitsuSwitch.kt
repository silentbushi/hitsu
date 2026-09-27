package app.hitsu.vault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuMotion

private val TrackShape = RoundedCornerShape(13.dp)

/** Visual only: the parent row owns the toggleable semantics and touch target. */
@Composable
fun HitsuSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = HitsuMotion.standard(),
        label = "switchKnob",
    )
    val knobColor by animateColorAsState(
        targetValue = if (checked) HitsuColors.Accent else HitsuColors.TextMuted,
        animationSpec = HitsuMotion.standard(),
        label = "switchKnobColor",
    )
    Box(
        modifier
            .size(width = 46.dp, height = 26.dp)
            .background(HitsuColors.SurfaceInput, TrackShape)
            .border(1.dp, HitsuColors.Stroke, TrackShape),
    ) {
        Box(
            Modifier
                .offset { IntOffset(knobOffset.roundToPx(), 2.dp.roundToPx()) }
                .size(20.dp)
                .background(knobColor, CircleShape),
        )
    }
}
