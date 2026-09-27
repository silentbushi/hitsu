package app.hitsu.vault.data

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import app.hitsu.vault.domain.BiometricAvailability

class SystemBiometricAvailability(private val context: Context) : BiometricAvailability {
    override fun canEnroll(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
}
