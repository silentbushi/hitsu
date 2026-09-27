package app.hitsu.vault.ui.media

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import app.hitsu.vault.data.MediaRepository
import okio.Buffer
import okio.FileSystem

/** Coil models for vault media; the ids double as memory cache keys. */
data class ThumbnailKey(val mediaId: String)

data class FullImageKey(val mediaId: String)

/**
 * Decrypts a thumbnail straight into memory. Coil's disk cache stays off (spec §5.4): the only
 * thumbnails on disk are the encrypted ones.
 */
class EncryptedThumbnailFetcher(
    private val key: ThumbnailKey,
    private val repository: MediaRepository,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = repository.thumbnail(key.mediaId) ?: return null
        return SourceFetchResult(
            source = ImageSource(Buffer().apply { write(bytes) }, FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory(private val repository: MediaRepository) : Fetcher.Factory<ThumbnailKey> {
        override fun create(data: ThumbnailKey, options: Options, imageLoader: ImageLoader): Fetcher =
            EncryptedThumbnailFetcher(data, repository)
    }
}

/** The same idea for the full photo behind the viewer. */
class EncryptedImageFetcher(
    private val key: FullImageKey,
    private val repository: MediaRepository,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = repository.original(key.mediaId) ?: return null
        return SourceFetchResult(
            source = ImageSource(Buffer().apply { write(bytes) }, FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory(private val repository: MediaRepository) : Fetcher.Factory<FullImageKey> {
        override fun create(data: FullImageKey, options: Options, imageLoader: ImageLoader): Fetcher =
            EncryptedImageFetcher(data, repository)
    }
}
