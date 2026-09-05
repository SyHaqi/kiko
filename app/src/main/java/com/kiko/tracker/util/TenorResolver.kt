package com.kiko.tracker.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import com.kiko.tracker.data.api.NetworkClient

// Resolve Tenor GIF URL
object TenorResolver {
    // Built off the shared
    // connection pool and dispatcher
    // since a slow Tenor
    private val client = NetworkClient.shared.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    // Cache resolved GIF URLs.
    // ConcurrentHashMap forbids null *values*
    // NullPointerException unconditionally, it's not
    // A failed/empty resolution (network
    // with no og:image/twitter:image meta
    // not a rare edge
    // it tried to cache
    // nothing" so it's still
    // recomposition while scrolling) without
    private val cache = ConcurrentHashMap<String, String>()
    private const val NO_RESULT = "\u0000NO_TENOR_RESULT"

    private val metaTagRegex = Regex(
        """<meta[^>]+(?:property|name)\s*=\s*['"](?:og:image|twitter:image)['"][^>]+content\s*=\s*['"]([^'"]+)['"]""",
        RegexOption.IGNORE_CASE,
    )
    // Handle reversed attribute order
    private val metaTagRegexAlt = Regex(
        """<meta[^>]+content\s*=\s*['"]([^'"]+)['"][^>]+(?:property|name)\s*=\s*['"](?:og:image|twitter:image)['"]""",
        RegexOption.IGNORE_CASE,
    )

    suspend fun resolveGifUrl(pageUrl: String): String? {
        cache[pageUrl]?.let { return if (it == NO_RESULT) null else it }
        return withContext(Dispatchers.IO) {
            val resolved = runCatching {
                val request = Request.Builder()
                    .url(pageUrl)
                    // Handle stripped meta page
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Mobile Safari/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val html = response.body?.string() ?: return@use null
                    val raw = metaTagRegex.find(html)?.groupValues?.get(1)
                        ?: metaTagRegexAlt.find(html)?.groupValues?.get(1)
                    raw?.let { decodeHtmlAttribute(it) }
                }
            }.getOrNull()
            cache[pageUrl] = resolved ?: NO_RESULT
            resolved
        }
    }

    private fun decodeHtmlAttribute(text: String): String =
        text.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">")
}