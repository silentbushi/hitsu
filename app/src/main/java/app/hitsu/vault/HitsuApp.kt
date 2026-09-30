package app.hitsu.vault

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.hitsu.vault.data.MediaRepository
import app.hitsu.vault.data.SharedImportWatcher
import app.hitsu.vault.data.VaultSessionCleaner
import app.hitsu.vault.ui.media.EncryptedImageFetcher
import app.hitsu.vault.ui.media.EncryptedThumbnailFetcher
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HitsuApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var mediaRepository: MediaRepository

    @Inject
    lateinit var sessionCleaner: VaultSessionCleaner

    @Inject
    lateinit var sharedImports: SharedImportWatcher

    override fun onCreate() {
        super.onCreate()
        sessionCleaner.start()
        sharedImports.start()
        publishQuickDownload()
    }

    /**
     * Puts "Descarga rápida" under the Hitsu icon in the share sheet. It has to be a long-lived
     * dynamic shortcut matching the share-target in shortcuts.xml; the XML alone is not enough.
     */
    private fun publishQuickDownload() {
        val shortcut = ShortcutInfoCompat.Builder(this, QUICK_DOWNLOAD_ID)
            .setShortLabel(getString(R.string.quick_download))
            .setLongLived(true)
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_launcher_foreground))
            .setCategories(setOf(QUICK_DOWNLOAD_CATEGORY))
            .setIntent(
                Intent(this, QuickDownloadActivity::class.java).setAction(Intent.ACTION_SEND),
            )
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(this, shortcut) }
    }

    private companion object {
        const val QUICK_DOWNLOAD_ID = "quick-download"
        const val QUICK_DOWNLOAD_CATEGORY = "app.hitsu.vault.category.QUICK_DOWNLOAD"
    }

    /** Spec §5.4: decrypted thumbnails may live in memory, never in Coil's disk cache. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(EncryptedThumbnailFetcher.Factory(mediaRepository))
                add(EncryptedImageFetcher.Factory(mediaRepository))
            }
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
}
