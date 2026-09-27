package app.hitsu.vault.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ControlShape = RoundedCornerShape(8.dp)

internal val HitsuShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = ControlShape,
    medium = ControlShape,
    large = ControlShape,
    extraLarge = ControlShape,
)
