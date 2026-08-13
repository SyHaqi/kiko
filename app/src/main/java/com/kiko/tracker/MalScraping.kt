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
fun fullResMalImage(url: String): String = url.replaceFirst(Regex("/r/\\d+x\\d+(?:-\\d+)?/"), "/")

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
