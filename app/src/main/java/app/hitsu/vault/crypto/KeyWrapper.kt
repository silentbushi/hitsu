package app.hitsu.vault.crypto

/** Seals bytes with a key that never leaves the device (Android Keystore in production). */
interface KeyWrapper {
    fun wrap(plaintext: ByteArray): ByteArray

    fun unwrap(payload: ByteArray): ByteArray
}
