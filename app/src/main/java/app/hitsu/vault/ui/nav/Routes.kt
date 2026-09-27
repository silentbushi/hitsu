package app.hitsu.vault.ui.nav

import app.hitsu.vault.domain.MediaFilter
import app.hitsu.vault.domain.VaultState

object Routes {
    const val SPLASH = "splash"
    const val SETUP = "setup"
    const val LOCK = "lock"
    const val HOME = "home"

    /** Spec §11. The filter rides along so the viewer swipes through the same list as the grid. */
    const val PHOTO = "photo/{id}?filter={filter}"

    const val VIDEO = "video/{id}"
    const val DOWNLOAD = "download/{url}"
    const val SETTINGS = "settings"
    const val SETTINGS_AUTO_LOCK = "settings/autolock"
    const val SETTINGS_YTDLP = "settings/ytdlp"
    const val SETTINGS_BACKUP = "settings/backup"
    const val SETTINGS_ABOUT = "settings/about"

    fun photo(id: String, filter: MediaFilter): String = "photo/$id?filter=${filter.name}"

    fun video(id: String): String = "video/$id"

    fun download(url: String): String =
        "download/" + java.net.URLEncoder.encode(url, Charsets.UTF_8.name())
}

fun VaultState.route(): String = when (this) {
    VaultState.Unknown -> Routes.SPLASH
    VaultState.Absent -> Routes.SETUP
    VaultState.Locked -> Routes.LOCK
    VaultState.Unlocked -> Routes.HOME
}
