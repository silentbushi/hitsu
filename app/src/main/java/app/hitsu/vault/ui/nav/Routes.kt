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
    const val ALBUM = "album/{id}"
    const val DOWNLOAD = "download/{url}"
    const val SETTINGS = "settings"
    const val SETTINGS_AUTO_LOCK = "settings/autolock"
    const val SETTINGS_SLIDESHOW = "settings/slideshow"
    const val SETTINGS_YTDLP = "settings/ytdlp"
    const val SETTINGS_ALBUMS = "settings/albums"
    const val SETTINGS_CHANGE_PIN = "settings/pin"
    const val SETTINGS_BACKUP = "settings/backup"
    const val SETTINGS_ABOUT = "settings/about"

    /**
     * Spec §7.11. The source rides in the route so the pass shows the same set as the screen it was
     * started from, and an album id and a filter are never both meaningful at once.
     */
    const val SLIDESHOW = "slideshow?album={album}&filter={filter}&start={start}"

    fun photo(id: String, filter: MediaFilter): String = "photo/$id?filter=${filter.name}"

    fun slideshow(
        albumId: String? = null,
        filter: MediaFilter = MediaFilter.All,
        startId: String? = null,
    ): String = "slideshow?album=${albumId.orEmpty()}&filter=${filter.name}&start=${startId.orEmpty()}"

    fun video(id: String): String = "video/$id"

    fun album(id: String): String = "album/$id"

    fun download(url: String): String =
        "download/" + java.net.URLEncoder.encode(url, Charsets.UTF_8.name())
}

fun VaultState.route(): String = when (this) {
    VaultState.Unknown -> Routes.SPLASH
    VaultState.Absent -> Routes.SETUP
    VaultState.Locked -> Routes.LOCK
    VaultState.Unlocked -> Routes.HOME
}
