package app.hitsu.vault.ui.login

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import app.hitsu.vault.R
import app.hitsu.vault.data.download.CookieStore
import app.hitsu.vault.data.download.netscapeLines
import app.hitsu.vault.domain.AutoLock
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.ui.components.HitsuIcons
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuTheme
import app.hitsu.vault.ui.theme.HitsuType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A full-screen browser for logging into a site that will not show a post without a session. The
 * login has to happen inside Hitsu rather than in the phone's browser, because cookies live in the
 * private storage of whichever app made them; what the site hands back is sealed with the vault key
 * by [CookieStore].
 *
 * Which address to open is the user's call, asked for before this opens: the sites change their
 * login URLs often enough that a built-in list goes stale, and some only work from their mobile
 * address.
 */
@AndroidEntryPoint
class LoginActivity : ComponentActivity() {

    @Inject
    lateinit var cookies: CookieStore

    @Inject
    lateinit var clock: Clock

    @Inject
    lateinit var autoLock: AutoLock

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isBlank()) {
            finish()
            return
        }
        setContent {
            HitsuTheme {
                LoginWindow(startUrl = url, onDone = ::save, onClose = ::finish)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        autoLock.onForegrounded()
    }

    override fun onStop() {
        super.onStop()
        // Handing the screen back to the vault is not leaving the app; walking away from here is.
        if (!isFinishing) autoLock.onBackgrounded(isChangingConfigurations)
    }

    private fun save(host: String, rawCookies: String) {
        if (host.isBlank() || rawCookies.isBlank()) {
            finish()
            return
        }
        lifecycleScope.launch {
            cookies.save(host, netscapeLines(host, rawCookies, clock.now() / 1000 + YEAR_SECONDS))
            finish()
        }
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val YEAR_SECONDS = 365L * 24 * 60 * 60

        fun intent(context: Context, url: String): Intent =
            Intent(context, LoginActivity::class.java).putExtra(EXTRA_URL, url)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWindow(
    startUrl: String,
    onDone: (host: String, rawCookies: String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    val webView = remember {
        CookieManager.getInstance().setAcceptCookie(true)
        WebView(context).apply {
            /*
             * An exact size, not "up to this much". A view handed to AndroidView is measured as
             * wrap-content by default, and a WebView without a definite height resolves every
             * viewport unit to zero: measured here, vh, svh, lvh and dvh all came back 0 while the
             * window was 733 pixels tall. Instagram builds its login box out of vh, so it collapsed
             * into a black page, while pages that size themselves in percentages looked fine.
             */
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            /*
             * The stock WebView announces itself with "; wv" in its user agent, which marks it as an
             * embedded browser, and Instagram is known to answer those differently. Dropping those
             * two words leaves a plain Chrome for Android string. Precaution rather than a proven
             * cure: the blank Instagram login turned out to be the viewport bug above.
             */
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            webViewClient = object : WebViewClient() {
                /*
                 * Halfway through its login, TikTok tries to hand the session to its own app with a
                 * link of its own making (snssdk1233://...), which a WebView cannot open and reports
                 * as ERR_UNKNOWN_URL_SCHEME. Opening it would be worse than failing: the cookies
                 * would end up in that app, where Hitsu cannot read them. So anything that is not
                 * plain web navigation is left alone, and the login carries on here.
                 */
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val target = request.url
                    if (target.scheme == "http" || target.scheme == "https") return false
                    val fallback = runCatching {
                        Intent.parseUri(target.toString(), Intent.URI_INTENT_SCHEME)
                            .getStringExtra("browser_fallback_url")
                    }.getOrNull()
                    if (fallback != null) view.loadUrl(fallback)
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, pageTitle: String) {
                    title = pageTitle
                }
            }
            loadUrl(startUrl)
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HitsuColors.Bg)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = HitsuIcons.Close,
                contentDescription = stringResource(R.string.action_cancel),
                tint = HitsuColors.TextPrimary,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onClose)
                    .size(22.dp),
            )
            Text(
                text = title.ifBlank { startUrl.toUri().host.orEmpty() },
                style = HitsuType.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.action_done),
                style = HitsuType.Body,
                color = HitsuColors.Accent,
                modifier = Modifier.clickable(role = Role.Button) {
                    val url = webView.url.orEmpty()
                    val host = url.toUri().host.orEmpty().removePrefix("www.")
                    onDone(host, CookieManager.getInstance().getCookie(url).orEmpty())
                },
            )
        }

        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}
