# Kiko for Android

Native Android app built with Kotlin, Jetpack Compose, and Material 3. Modern system sans-serif type, rounded expressive containers, and a compact tracker flow.

## What works now

- Real MyAnimeList data: sign in with your MAL account, and your actual anime/manga list (titles, cover art, status, progress) loads from the API
- Editing status/progress in the app writes back to your MAL account live
- Adding custom titles, deleting titles, and resetting to the starter demo library (for local-only browsing)
- Full light/dark mode — a theme picker in Profile (System / Light / Dark), matching system bars, and every screen re-themed
- Session refresh: access tokens are refreshed automatically in the background so you stay signed in
- Sign out, which clears the local session

## Open and run

1. Install current Android Studio.
2. Select **Open** and choose this `kiko-android` folder.
3. `local.properties` already contains a `MAL_CLIENT_ID`. If you rotate your API app, replace the value there (never commit a client *secret* — this app is a public client and doesn't use one).
4. Let Android Studio complete Gradle sync (needs network access to Google's and Maven Central's repos).
5. Choose an Android emulator or connected Android phone, then press **Run**.

## How MAL sign-in works

Kiko uses MAL's OAuth2 PKCE flow (MAL only supports the `plain` challenge method, which `MalApi.kt` implements). Tapping "Sign in with MyAnimeList" in Profile opens the MAL authorization page in the browser; MAL redirects back to `com.kiko.tracker://oauth/callback`, which `MainActivity` picks up to exchange the code for tokens. No client secret is ever stored on-device, matching MAL's guidance for native/mobile apps.

## Scope note

Full parity with every MAL web/community feature depends on what MAL's public API exposes; anything not available through `api.myanimelist.net/v2` (e.g. social features, forums) is out of scope for a client built against that API.