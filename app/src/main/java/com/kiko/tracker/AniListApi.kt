package com.kiko.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

private const val ANILIST = "https://graphql.anilist.co"

// Confirmed next-episode number + air time for a currently-airing show, as curated by
// AniList's own staff/mods rather than inferred from broadcast cadence. Unlike
// MediaItem.nextEpisodeNumber() in Models.kt — which just counts weeks elapsed since the
// first air date and assumes zero gaps — this correctly reflects one-off delays and
// hiatuses, since AniList updates the field by hand whenever a studio announces a schedule
// slip. See LibraryViewModel.loadAiringEpisode for how this overrides the date-math guess.
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

    // Null both when the request fails (network error, AniList down) and when AniList
    // simply has no nextAiringEpisode for this id (show finished airing between our own
    // "Currently Airing" status check and this call, or AniList hasn't matched this MAL id
    // to an entry) — callers fall back to the date-math estimate in either case.
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
