package com.kiko.tracker

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
    val shared: OkHttpClient = OkHttpClient()
}
