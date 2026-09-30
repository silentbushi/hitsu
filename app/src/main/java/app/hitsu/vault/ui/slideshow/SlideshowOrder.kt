package app.hitsu.vault.ui.slideshow

import app.hitsu.vault.domain.MediaItem

/**
 * Spec §7.11: the order a pass runs in. Kept apart from the ViewModel because it is the part with
 * rules worth testing on the JVM, and it has nothing to do with Android.
 */
internal fun orderedForPass(
    photos: List<MediaItem>,
    startId: String?,
    shuffle: Boolean,
): List<MediaItem> {
    if (!shuffle) return photos
    // Shuffled, but the photo the viewer was showing still goes first: that is where it started.
    val start = photos.firstOrNull { it.id == startId } ?: return photos.shuffled()
    return listOf(start) + (photos - start).shuffled()
}

/** In order, a pass starts where the viewer was; shuffled, [orderedForPass] already put it first. */
internal fun startIndexFor(photos: List<MediaItem>, startId: String?, shuffle: Boolean): Int =
    if (shuffle || startId == null) 0 else photos.indexOfFirst { it.id == startId }.coerceAtLeast(0)
