package app.hitsu.vault.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

object HitsuMotion {
    const val DURATION_MS = 220

    fun <T> standard(): TweenSpec<T> = tween(DURATION_MS, easing = FastOutSlowInEasing)
}
