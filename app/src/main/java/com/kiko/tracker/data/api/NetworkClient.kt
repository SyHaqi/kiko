package com.kiko.tracker.data.api

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient

// Single shared OkHttpClient for
// MalCompanyApi, MalPeopleApi, StacksApi, ClubsApi,
// those classes built its
// fresh per call site
// paying for a brand
// existing one. A single
//
// Callers that need different
// timeouts, or MainActivity's forum-image
// this via `.newBuilder()` rather
// shares the underlying connection
// settings differ.
object NetworkClient {
    // Set once from MainActivity.onCreate,
    // "configure once at startup"
    // is lazy so this
    // (it shouldn't), it just
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // 10MB on disk is
    // client's — see MainActivity's
    // ranking/seasonal/forum page serve from
    // whenever that response's own
    // `Authorization: Bearer ...` calls)
    // RFC 7234 — OkHttp
    // list data. POSTs (list
    val shared: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
        appContext?.let { ctx ->
            builder.cache(Cache(java.io.File(ctx.cacheDir, "http_cache"), 10L * 1024 * 1024))
        }
        builder.build()
    }
}