package app.hitsu.vault.data.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * A shared link is just text, so unlike media it can simply wait for the PIN.
 *
 * Apps rarely share a bare URL: they wrap it in a sentence, sometimes in quotes or brackets. Those
 * trailing characters are not part of the address, and yt-dlp rejects the whole thing as an
 * unsupported URL if they come along.
 */
class PendingLinks {

    private val _link = MutableStateFlow<String?>(null)
    val link: StateFlow<String?> = _link.asStateFlow()

    fun offer(text: String) {
        _link.value = extract(text) ?: return
    }

    fun take(): String? = _link.getAndUpdate { null }

    /** For the quick download, which acts on the link instead of parking it. */
    fun extractOnly(text: String): String? = extract(text)

    private fun extract(text: String): String? =
        URL_PATTERN.find(text)?.value?.trimEnd(*TRAILING)?.takeIf { it.length > MIN_LENGTH }

    private companion object {
        val URL_PATTERN = Regex("""https?://\S+""")
        val TRAILING = charArrayOf('.', ',', ')', ']', '}', '"', '\'', '>', '»', '!', '?', ';', ':')
        const val MIN_LENGTH = 12
    }
}
