package app.hitsu.vault.data.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.InputStream

/** Turns picker/share URIs into [ImportSource]s without leaking ContentResolver into the importer. */
class ContentImportSources(private val resolver: ContentResolver) : ImportSources {

    override fun from(uri: Uri): ImportSource {
        val mime = resolver.getType(uri) ?: DEFAULT_MIME
        var displayName: String? = null
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) displayName = cursor.getString(nameColumn)
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
            }
        }
        return ImportSource(
            displayName = displayName,
            mime = mime,
            sizeBytes = size,
            open = { resolver.openInputStream(uri) ?: throw FileNotFoundException() },
            openDescriptor = { resolver.openFileDescriptor(uri, "r") },
        )
    }

    private companion object {
        const val DEFAULT_MIME = "application/octet-stream"
    }
}
