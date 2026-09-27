package app.hitsu.vault.domain

object PinPolicy {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 12

    fun isValid(length: Int): Boolean = length in MIN_LENGTH..MAX_LENGTH
}
