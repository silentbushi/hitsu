package app.hitsu.vault.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

object HitsuMotion {
    const val DURATION_MS = 220

    /** Spec §7.11: long enough that the slideshow dissolves instead of cutting. */
    const val CROSSFADE_MS = 600

    fun <T> standard(): TweenSpec<T> = tween(DURATION_MS, easing = FastOutSlowInEasing)

    fun <T> crossfade(): TweenSpec<T> = tween(CROSSFADE_MS, easing = FastOutSlowInEasing)
}
