package com.kiko.tracker

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient

// Single shared OkHttpClient for every plain API/scraper class (MalApi, TenraiApi,
// MalCompanyApi, MalPeopleApi, StacksApi, ClubsApi, AppUpdateChecker). Previously each of
// those classes built its own bare `OkHttpClient()`, and several of them are constructed
// fresh per call site rather than held as singletons — so every one of those calls was
// paying for a brand new connection pool and dispatcher thread pool instead of reusing an
// existing one. A single shared client fixes that everywhere at once.
//
// Callers that need different timeouts or interceptors (e.g. TenorResolver's shorter
// timeouts, or MainActivity's forum-image Referer/UA interceptor for Coil) should build off
// this via `.newBuilder()` rather than constructing a fresh `OkHttpClient()` — that still
// shares the underlying connection pool and dispatcher even though the resulting client's
// settings differ.
object NetworkClient {
    // Set once from MainActivity.onCreate, before any API class gets constructed — same
    // "configure once at startup" pattern as Coil's global ImageLoader below it. `shared`
    // is lazy so this can run first; if something somehow reads `shared` before `init()`
    // (it shouldn't), it just falls back to an uncached client instead of crashing.
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // 10MB on disk is plenty for JSON/HTML API responses (images are Coil's job, not this
    // client's — see MainActivity's ImageLoader). Lets a repeat visit to an already-fetched
    // ranking/seasonal/forum page serve from disk instead of round-tripping to MAL again,
    // whenever that response's own Cache-Control allows it. Authenticated requests (MalApi's
    // `Authorization: Bearer ...` calls) are only cached if the server explicitly opts in per
    // RFC 7234 — OkHttp won't cache those by default — so this can't serve stale signed-in
    // list data. POSTs (list updates, auth) are never cached either way.
    val shared: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
        appContext?.let { ctx ->
            builder.cache(Cache(java.io.File(ctx.cacheDir, "http_cache"), 10L * 1024 * 1024))
        }
        builder.build()
    }
}