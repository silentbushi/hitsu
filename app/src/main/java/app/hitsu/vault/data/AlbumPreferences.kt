package app.hitsu.vault.data

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * What the album settings remember (mockup `05-albumes.png`). None of it is secret — the album names
 * themselves already live in the clear in the index (spec §14) — so these are ordinary preferences.
 *
 * The two destinations are stored as ids rather than names, so renaming an album does not quietly
 * send new files somewhere else. "Not set" and "None" are different things for downloads: before the
 * first download there is no album yet, and the default is to make one called «Descargas»; choosing
 * None afterwards has to survive, so it is written as an empty value rather than left absent.
 */
class AlbumPreferences(private val prefs: SharedPreferences) {

    var importAlbumId: String?
        get() = prefs.getString(IMPORT_ALBUM, null)?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit { putString(IMPORT_ALBUM, value.orEmpty()) }

    var downloadAlbumId: String?
        get() = prefs.getString(DOWNLOAD_ALBUM, null)?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit { putString(DOWNLOAD_ALBUM, value.orEmpty()) }

    /** True until the user says otherwise, which is what makes «Descargas» the default. */
    val downloadAlbumUnset: Boolean get() = !prefs.contains(DOWNLOAD_ALBUM)

    var showCounts: Boolean
        get() = prefs.getBoolean(SHOW_COUNTS, true)
        set(value) = prefs.edit { putBoolean(SHOW_COUNTS, value) }

    var order: AlbumOrder
        get() = runCatching { AlbumOrder.valueOf(prefs.getString(ORDER, null).orEmpty()) }
            .getOrDefault(AlbumOrder.Alphabetical)
        set(value) = prefs.edit { putString(ORDER, value.name) }

    /** Forgets a destination that points at an album that no longer exists. */
    fun forget(albumId: String) {
        if (importAlbumId == albumId) importAlbumId = null
        if (downloadAlbumId == albumId) downloadAlbumId = null
    }

    companion object {
        const val DEFAULT_DOWNLOAD_ALBUM = "Descargas"
        private const val IMPORT_ALBUM = "importAlbum"
        private const val DOWNLOAD_ALBUM = "downloadAlbum"
        private const val SHOW_COUNTS = "showCounts"
        private const val ORDER = "order"
    }
}
