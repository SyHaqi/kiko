package com.kiko.tracker

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

// Shared helpers for the classes that scrape MAL's own HTML pages directly
// (ClubsApi, MalCompanyApi, MalPeopleApi, StacksApi) rather than going through
// Tenrai/Jikan. These used to be copy-pasted into each class individually and
// had already started drifting apart (e.g. two different "MAL request failed"
// message formats for the same failure) — centralized here so a fix or a
// header tweak only needs to happen once.

// The desktop Chrome UA that ClubsApi, MalCompanyApi, and MalPeopleApi all send.
// StacksApi intentionally sends its own distinct "Kiko/1.0" UA (see StacksApi),
// so that one stays as an explicit parameter rather than being folded in here.
const val MAL_DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

// GET a MAL page and parse it as HTML. Every scraper class was building this
// same request/response/error-handling shape by hand; a failed request now
// always throws the same IOException shape regardless of which class asked.
fun OkHttpClient.fetchMalDocument(url: String, userAgent: String = MAL_DESKTOP_USER_AGENT): Document {
    val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
    newCall(request).execute().use { resp ->
        val body = resp.body?.string() ?: ""
        if (!resp.isSuccessful) throw IOException("MAL request failed (${resp.code}): $url")
        return Jsoup.parse(body, url)
    }
}

// Flattened element text with non-breaking spaces folded to regular spaces —
// MAL renders separators like "N Entries" with &nbsp;, which plain \s won't match.
fun normalizeWhitespace(el: Element): String = el.text().replace('\u00A0', ' ')

// MAL serves thumbnails (club/member avatars, credited-work covers, etc.) through a
// resizing proxy path like "/r/50x70/images/manga/3/122224.jpg" — stripping the
// "/r/WxH/" segment returns the same image at its original, higher-resolution size.
//
// Company/producer logos never go through that proxy at all, though, so this strip is a
// no-op for them — confirmed against MAL's own pages: Kyoto Animation's search-row logo
// and its full profile logo are literally the same asset, just requested at a different
// baked-in size ("cdn.myanimelist.net/s/common/company_logos/{uuid}_100x100_i" vs
// "..._600x600_i" for the exact same uuid), not two files behind a resize proxy. That's
// why company thumbnails specifically stayed blurry no matter what this function did:
// there was never a "/r/WxH/" segment here to strip in the first place. Upgrading that
// filename-encoded size to MAL's own largest variant (600x600 — the size its detail pages
// already request) fixes it for both the Discover company search rows and the detail
// page's own logo.
private val companyLogoSize = Regex("_\\d+x\\d+_i(?=\\?|$)")
fun fullResMalImage(url: String): String {
    val proxyStripped = url.replaceFirst(Regex("/r/\\d+x\\d+(?:-\\d+)?/"), "/")
    return if (proxyStripped.contains("/company_logos/")) proxyStripped.replaceFirst(companyLogoSize, "_600x600_i") else proxyStripped
}

// MAL/Jikan both print person names as "Last, First"; the app displays "First Last".
fun reorderMalPersonName(raw: String): String {
    val parts = raw.split(", ")
    return if (parts.size == 2) "${parts[1]} ${parts[0]}" else raw
}

// "one-shot"/"oneshot" -> "One Shot"; every other manga format passes through as-is.
fun normalizeMangaFormatLabel(rawType: String): String = when (rawType.lowercase()) {
    "one-shot", "oneshot" -> "One Shot"
    else -> rawType
}