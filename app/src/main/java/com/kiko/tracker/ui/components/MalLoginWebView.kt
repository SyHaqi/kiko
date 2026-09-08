package com.kiko.tracker.ui.components

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kiko.tracker.data.api.MalSessionCookie

private const val LOGIN_URL = "https://myanimelist.net/login.php"
// Reachable by any authenticated account (not just moderators — panel.php
// is the Moderation Panel and redirects regular users away from it even
// when logged in, which broke this check for non-mod accounts). Bounces
// to login.php when there's no session, so it still confirms the login
// actually took.
private const val LOGIN_CHECK_URL = "https://myanimelist.net/editprofile.php"
private const val MAL_HOST = "myanimelist.net"

/**
 * First real embedded WebView in the app — everywhere else (OAuth sign-in,
 * forum links) uses CustomTabsIntent. That's fine for those, but scraping the
 * profile page for manga stats, friends, and favorites needs the raw session
 * cookie, which Custom Tabs' separate Chrome process won't expose.
 *
 * Drop this into a full-screen route (e.g. a new TopScreen.MalLogin case in
 * Navigation.kt) and pop back to the previous screen once onLoginSuccess
 * fires, then retry whatever scrape call triggered MalSessionExpired.
 */
@Composable
fun MalLoginWebView(
    session: MalSessionCookie,
    onLoginSuccess: () -> Unit,
    onVerifyingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true // MAL's login form needs this
                webViewClient = MalLoginWebViewClient(session, onLoginSuccess, onVerifyingChange)
                loadUrl(LOGIN_URL)
            }
        }
    )
}

/**
 * Simplified state machine for clarity — if a failed re-verify needs a
 * second attempt (e.g. the check page bounces back to the login form),
 * reset `verifying` on the next onPageFinished for LOGIN_URL.
 */
private class MalLoginWebViewClient(
    private val session: MalSessionCookie,
    private val onLoginSuccess: () -> Unit,
    private val onVerifyingChange: (Boolean) -> Unit
) : WebViewClient() {

    // Setter reports every transition so the host screen can show a spinner
    // for the (real, unavoidable — it's an extra page load) gap between
    // "looks logged in" and onLoginSuccess actually firing.
    private var verifying = false
        set(value) {
            field = value
            onVerifyingChange(value)
        }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        if (url == null) return

        // MAL's login page offers Google/Facebook/Apple/X sign-in, which
        // navigates the WebView to those providers' own domains as a
        // legitimate mid-flow step. That's not "logged in", it's not MAL
        // at all — treating any non-login.php page as a success signal
        // was hijacking the OAuth flow the instant it left myanimelist.net,
        // before the user had a chance to authenticate.
        val host = Uri.parse(url).host.orEmpty()
        val onMal = host == MAL_HOST || host.endsWith(".$MAL_HOST")

        when {
            url.startsWith(LOGIN_CHECK_URL) -> {
                session.captureFromWebView()
                if (session.has()) {
                    onLoginSuccess()
                } else {
                    // Cookie wasn't actually there yet (or check bounced
                    // back some other way without hitting login.php) — let
                    // a subsequent navigation retry the verification.
                    verifying = false
                }
            }
            !onMal -> {
                // Mid-flow on a third-party auth provider — wait for it to
                // hand control back to myanimelist.net.
            }
            url.contains("login.php") -> {
                // Bounced back to the login form — either the user hasn't
                // logged in yet, or the previous verify attempt raced the
                // session cookie being set. Reset so the next non-login
                // page load re-triggers verification.
                verifying = false
            }
            verifying -> {
                // We fired the check navigation but landed somewhere other
                // than LOGIN_CHECK_URL or login.php (an unexpected redirect
                // target) — don't stay stuck waiting forever, let the next
                // page load retry.
                verifying = false
            }
            else -> {
                // Landed somewhere other than the login form — probably
                // authenticated. Confirm with a page only a logged-in
                // session can reach.
                verifying = true
                view.loadUrl(LOGIN_CHECK_URL)
            }
        }
    }
}