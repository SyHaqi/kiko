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
}
