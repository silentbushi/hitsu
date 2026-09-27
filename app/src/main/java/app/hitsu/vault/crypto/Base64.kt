package app.hitsu.vault.crypto

import java.util.Base64

internal fun ByteArray.encodeBase64(): String = Base64.getEncoder().encodeToString(this)

internal fun String.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)
