package app.hitsu.vault.data.media

import java.io.File

/** Everything encrypted lives under filesDir/vault; nothing here is visible to other apps. */
class VaultFiles(private val root: File) {

    fun objectPath(id: String): String = "$OBJECTS/$id$EXTENSION"

    fun thumbPath(id: String): String = "$THUMBS/$id$EXTENSION"

    fun file(relativePath: String): File = File(root, relativePath)

    fun prepareDirectories() {
        File(root, OBJECTS).mkdirs()
        File(root, THUMBS).mkdirs()
    }

    private companion object {
        const val OBJECTS = "objects"
        const val THUMBS = "thumbs"
        const val EXTENSION = ".hitsu"
    }
}
