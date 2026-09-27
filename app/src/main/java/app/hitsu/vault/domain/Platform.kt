package app.hitsu.vault.domain

fun interface Clock {
    fun now(): Long
}

fun interface BiometricAvailability {
    fun canEnroll(): Boolean
}
