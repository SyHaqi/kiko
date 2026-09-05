package com.kiko.tracker.data.api

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

// Shared helpers for the
// (ClubsApi, MalCompanyApi, MalPeopleApi, StacksApi)
// Tenrai/Jikan. These used to
// had already started drifting
// message formats for the
// header tweak only needs

// The desktop Chrome UA
// StacksApi intentionally sends its
// so that one stays
const val MAL_DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

// GET a MAL page
// same request/response/error-handling shape by
// always throws the same
fun OkHttpClient.fetchMalDocument(url: String, userAgent: String = MAL_DESKTOP_USER_AGENT): Document {
    val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
    newCall(request).execute().use { resp ->
        val body = resp.body?.string() ?: ""
        if (!resp.isSuccessful) throw IOException("MAL request failed (${resp.code}): $url")
        return Jsoup.parse(body, url)
    }
}

// Flattened element text with
// MAL renders separators like
fun normalizeWhitespace(el: Element): String = el.text().replace('\u00A0', ' ')

// MAL serves thumbnails (club/member
// resizing proxy path like
// "/r/WxH/" segment returns the
//
// Company/producer logos never go
// no-op for them —
// and its full profile
// baked-in size ("cdn.myanimelist.net/s/common/company_logos/{uuid}_100x100_i" vs
// "..._600x600_i" for the exact
// why company thumbnails specifically
// there was never a
// filename-encoded size to MAL's
// already request) fixes it
// page's own logo.
private val companyLogoSize = Regex("_\\d+x\\d+_i(?=\\?|$)")
fun fullResMalImage(url: String): String {
    val proxyStripped = url.replaceFirst(Regex("/r/\\d+x\\d+(?:-\\d+)?/"), "/")
    return if (proxyStripped.contains("/company_logos/")) proxyStripped.replaceFirst(companyLogoSize, "_600x600_i") else proxyStripped
}

// MAL/Jikan both print person
fun reorderMalPersonName(raw: String): String {
    val parts = raw.split(", ")
    return if (parts.size == 2) "${parts[1]} ${parts[0]}" else raw
}

// "one-shot"/"oneshot" -> "One Shot";
fun normalizeMangaFormatLabel(rawType: String): String = when (rawType.lowercase()) {
    "one-shot", "oneshot" -> "One Shot"
    else -> rawType
}