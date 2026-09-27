package app.hitsu.vault.data.media

import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.InputStream

/**
 * One thing to import, decoupled from where it came from: the picker today, a share intent later,
 * a test fixture in between.
 */
class ImportSource(
    val displayName: String?,
    val mime: String,
    val sizeBytes: Long,
    val open: () -> InputStream,
    /** Video metadata and frames need random access, which a stream cannot give. */
    val openDescriptor: (() -> ParcelFileDescriptor?)? = null,
) {
    val isVideo: Boolean get() = mime.startsWith("video/")
}

/** Lets the importer stay ignorant of ContentResolver, and lets tests hand over plain bytes. */
fun interface ImportSources {
    fun from(uri: Uri): ImportSource
}
