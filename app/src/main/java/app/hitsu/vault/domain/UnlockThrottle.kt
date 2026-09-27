package app.hitsu.vault.domain

/** Spec §5.1: after [MAX_ATTEMPTS] failures the wait doubles per failure. Never wipes. */
object UnlockThrottle {
    const val MAX_ATTEMPTS = 8
    private const val BASE_DELAY_MS = 30_000L
    private const val MAX_DELAY_MS = 60 * 60_000L

    fun delayAfter(failedAttempts: Int): Long {
        if (failedAttempts < MAX_ATTEMPTS) return 0L
        val doublings = (failedAttempts - MAX_ATTEMPTS).coerceAtMost(16)
        return (BASE_DELAY_MS shl doublings).coerceAtMost(MAX_DELAY_MS)
    }
}
