package app.hitsu.vault.data

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * What the slideshow remembers between sessions (spec §7.11). Nothing here is secret: it says how a
 * pass runs, never what is in the vault, so these are ordinary preferences.
 */
class SlideshowPreferences(private val prefs: SharedPreferences) {

    /** Spec §7.11: five seconds is long enough to look at a photo and short enough not to stall. */
    var secondsPerPhoto: Int
        get() = prefs.getInt(SECONDS, DEFAULT_SECONDS)
        set(value) = prefs.edit { putInt(SECONDS, value) }

    var shuffle: Boolean
        get() = prefs.getBoolean(SHUFFLE, false)
        set(value) = prefs.edit { putBoolean(SHUFFLE, value) }

    var loop: Boolean
        get() = prefs.getBoolean(LOOP, false)
        set(value) = prefs.edit { putBoolean(LOOP, value) }

    companion object {
        const val DEFAULT_SECONDS = 5
        private const val SECONDS = "secondsPerPhoto"
        private const val SHUFFLE = "shuffle"
        private const val LOOP = "loop"
    }
}
