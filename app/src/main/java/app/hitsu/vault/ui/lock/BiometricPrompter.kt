package app.hitsu.vault.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.hitsu.vault.R
import javax.crypto.Cipher

/** What came back from the system prompt. */
sealed interface BiometricOutcome {
    /** The fingerprint authorised this cipher; it is good for exactly one use. */
    data class Authorised(val cipher: Cipher) : BiometricOutcome

    /** A finger that is not enrolled. The prompt stays up, so this is only worth showing. */
    data object NotRecognised : BiometricOutcome

    data object Dismissed : BiometricOutcome
}

/**
 * Spec §5.2. The prompt is the system's, not ours: the fingerprint never reaches the app, it only
 * authorises the Keystore key that holds the sealed DEK. Which is why the cipher goes into the
 * prompt and comes back out of it.
 */
class BiometricPrompter(private val activity: FragmentActivity) {

    fun authenticate(
        title: String,
        subtitle: String,
        negative: String,
        cipher: Cipher,
        onOutcome: (BiometricOutcome) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authorised = result.cryptoObject?.cipher
                    onOutcome(
                        if (authorised != null) {
                            BiometricOutcome.Authorised(authorised)
                        } else {
                            BiometricOutcome.Dismissed
                        },
                    )
                }

                override fun onAuthenticationFailed() = onOutcome(BiometricOutcome.NotRecognised)

                override fun onAuthenticationError(code: Int, message: CharSequence) =
                    onOutcome(BiometricOutcome.Dismissed)
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negative)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}

@Composable
fun rememberBiometricPrompter(): BiometricPrompter? {
    val context = LocalContext.current
    return remember(context) { context.findActivity()?.let(::BiometricPrompter) }
}

/** The strings the two prompts share, so the vault sounds the same wherever it asks. */
@Composable
fun biometricPromptStrings(): Triple<String, String, String> = Triple(
    stringResource(R.string.biometric_prompt_title),
    stringResource(R.string.biometric_prompt_subtitle),
    stringResource(R.string.action_use_pin),
)

private fun Context.findActivity(): FragmentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
