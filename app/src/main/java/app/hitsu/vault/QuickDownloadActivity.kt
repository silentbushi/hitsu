package app.hitsu.vault

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import app.hitsu.vault.data.download.DownloadService
import app.hitsu.vault.data.download.PendingLinks
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The share sheet's "Descarga rápida": it has no interface of its own. It hands the link to the
 * service and gets out of the way, so the user stays where they were.
 */
@AndroidEntryPoint
class QuickDownloadActivity : ComponentActivity() {

    @Inject
    lateinit var pendingLinks: PendingLinks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val url = text?.let(pendingLinks::extractOnly)
        if (url == null) {
            Toast.makeText(this, R.string.download_failed_unsupported, Toast.LENGTH_SHORT).show()
        } else {
            DownloadService.start(this, url)
            Toast.makeText(this, R.string.quick_download_started, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
