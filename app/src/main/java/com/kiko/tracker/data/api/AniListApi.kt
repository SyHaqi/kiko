package com.kiko.tracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.next

private const val ANILIST = "https://graphql.anilist.co"

// Confirmed next-episode number +
// AniList's own staff/mods rather
// MediaItem.nextEpisodeNumber() in Models.kt —
// first air date and
// hiatuses, since AniList updates
// slip. See LibraryViewModel.loadAiringEpisode for
data class AiringInfo(val episode: Int, val airingAt: Long)

private const val NEXT_AIRING_QUERY = """
query (${'$'}id: Int) {
  Media(idMal: ${'$'}id, type: ANIME) {
    nextAiringEpisode { episode airingAt }
  }
}
"""

private const val BATCH_AIRING_QUERY = """
query (${'$'}ids: [Int]) {
  Page(page: 1, perPage: 50) {
    media(idMal_in: ${'$'}ids, type: ANIME) {
      idMal
      nextAiringEpisode { episode airingAt }
    }
  }
}
"""

class AniListApi {
    private val client = NetworkClient.shared

    // Null both when the
    // simply has no nextAiringEpisode
    // "Currently Airing" status check
    // to an entry) —
    suspend fun nextAiringEpisode(malId: Int): AiringInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("query", NEXT_AIRING_QUERY)
                put("variables", JSONObject().put("id", malId))
            }
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(ANILIST).post(body).build()
            val text = client.newCall(request).execute().use { resp ->
                val t = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw IOException("AniList request failed (${resp.code}): ${t.take(300)}")
                t
            }
            val next = JSONObject(text).optJSONObject("data")?.optJSONObject("Media")?.optJSONObject("nextAiringEpisode")
            next?.let { AiringInfo(episode = it.optInt("episode"), airingAt = it.optLong("airingAt")) }
        }.getOrNull()
    }

    // One request per 50 MAL ids. Unlike nextAiringEpisode above, this THROWS on any failure
    // so the caller can leave the cache untouched (and per-row lookups still work as fallback).
    // Ids AniList doesn't return, or that have no next episode, map to null.
    suspend fun nextAiringEpisodes(malIds: List<Int>): Map<Int, AiringInfo?> = withContext(Dispatchers.IO) {
        val result = HashMap<Int, AiringInfo?>()
        for (chunk in malIds.distinct().chunked(50)) {
            val ids = org.json.JSONArray().apply { chunk.forEach { put(it) } }
            val payload = JSONObject().apply {
                put("query", BATCH_AIRING_QUERY)
                put("variables", JSONObject().put("ids", ids))
            }
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(ANILIST).post(body).build()
            val text = client.newCall(request).execute().use { resp ->
                val t = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw IOException("AniList request failed (${resp.code}): ${t.take(300)}")
                t
            }
            val media = JSONObject(text).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
                ?: throw IOException("AniList returned no data: ${text.take(300)}")
            chunk.forEach { result[it] = null }
            for (i in 0 until media.length()) {
                val m = media.getJSONObject(i)
                val malId = m.optInt("idMal", -1).takeIf { it > 0 } ?: continue
                result[malId] = m.optJSONObject("nextAiringEpisode")
                    ?.let { AiringInfo(episode = it.optInt("episode"), airingAt = it.optLong("airingAt")) }
            }
        }
        result
    }
}