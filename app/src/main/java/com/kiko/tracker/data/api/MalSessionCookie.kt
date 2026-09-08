package com.kiko.tracker.data.api

import android.content.Context
import android.webkit.CookieManager

private const val MAL_DOMAIN = "https://myanimelist.net"

/**
 * Stores the raw MAL session cookie ("name1=value1; name2=value2") needed to
 * scrape pages that require a logged-in session — manga stats, friends,
 * favorites — none of which MAL's official API exposes.
 *
 * Shares MalApi's "mal_session" SharedPreferences file under a different key
 * ("mal_cookie") since this is the same MAL auth domain, just a second,
 * unrelated credential: the OAuth access/refresh tokens are for the official
 * API, this cookie is for the plain website.
 */
class MalSessionCookie(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("mal_session", Context.MODE_PRIVATE)

    fun get(): String? = prefs.getString("mal_cookie", null)

    fun has(): Boolean = !get().isNullOrBlank()

    fun clear() {
        prefs.edit().remove("mal_cookie").apply()
        CookieManager.getInstance().removeAllCookies(null)
    }

    /** Pulls whatever cookies the system WebView currently holds for MAL and persists them. */
    fun captureFromWebView() {
        val cookie = CookieManager.getInstance().getCookie(MAL_DOMAIN)
        if (!cookie.isNullOrBlank()) {
            prefs.edit().putString("mal_cookie", cookie).apply()
        }
    }
}