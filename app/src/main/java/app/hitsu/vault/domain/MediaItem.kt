package app.hitsu.vault.domain

enum class MediaType { Photo, Video }

data class MediaItem(
    val id: String,
    val type: MediaType,
    val mime: String,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val sizeBytes: Long,
    val takenAt: Long?,
    val importedAt: Long,
    val originalName: String?,
) {
    /** Spec §9: newest first by capture time, falling back to when it entered the vault. */
    val sortedAt: Long get() = takenAt ?: importedAt
}

enum class MediaFilter { All, Photos, Videos }
