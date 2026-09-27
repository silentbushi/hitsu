package app.hitsu.vault.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hitsu.vault.R
import app.hitsu.vault.ui.components.PhonePreview
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuTheme
import app.hitsu.vault.ui.theme.HitsuType

@Composable
fun SplashScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(HitsuColors.Bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.wordmark), style = HitsuType.Wordmark)
    }
}

@PhonePreview
@Composable
private fun SplashPreview() = HitsuTheme { SplashScreen() }
