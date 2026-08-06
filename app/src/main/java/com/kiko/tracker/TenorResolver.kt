package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// Resolve Tenor GIF URL
object TenorResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    // Cache resolved GIF URLs
    private val cache = ConcurrentHashMap<String, String?>()

    private val metaTagRegex = Regex(
        """<meta[^>]+(?:property|name)\s*=\s*"(?:og:image|twitter:image)"[^>]+content\s*=\s*"([^"]+)"""",
        RegexOption.IGNORE_CASE,
    )
    // Handle reversed attribute order
    private val metaTagRegexAlt = Regex(
        """<meta[^>]+content\s*=\s*"([^"]+)"[^>]+(?:property|name)\s*=\s*"(?:og:image|twitter:image)"""",
        RegexOption.IGNORE_CASE,
    )

    suspend fun resolveGifUrl(pageUrl: String): String? {
        cache[pageUrl]?.let { return it }
        if (cache.containsKey(pageUrl)) return null // a prior attempt already found nothing usable
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
            cache[pageUrl] = resolved
            resolved
        }
    }

    private fun decodeHtmlAttribute(text: String): String =
        text.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">")
}