<div align="center">

<img src="docs/icon.png" alt="Kiko app icon" width="128" height="128">

# Kiko

**Yet Another Unofficial MyAnimeList Client for Android**

Track anime & manga, discover new titles, and stay close to the MyAnimeList community, all in one clean Material 3 app.

<br>

<!-- Row 1: main buttons -->
[![Download](https://img.shields.io/github/v/release/SyHaqi/kiko?style=for-the-badge&label=Download&logo=android&logoColor=white&labelColor=2E51A2&color=1d3470)](https://github.com/SyHaqi/kiko/releases/latest)
[![Website](https://img.shields.io/badge/Website-syhaqi.github.io%2Fkiko-ffe18a?style=for-the-badge&logo=googlechrome&logoColor=2c2000&labelColor=f2c94c)](https://syhaqi.github.io/kiko/)
[![Discord](https://img.shields.io/badge/Discord-Join_the_server-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/qytyH7efp6)

<!-- Row 2: live stats, pastel palette from the website -->
[![Total downloads](https://img.shields.io/github/downloads/SyHaqi/kiko/total?style=for-the-badge&label=Downloads&logo=github&logoColor=052a1d&labelColor=8fd9bb&color=bff0dc)](https://github.com/SyHaqi/kiko/releases)
[![Stars](https://img.shields.io/github/stars/SyHaqi/kiko?style=for-the-badge&logo=github&logoColor=24114f&labelColor=bfaef5&color=d6c9ff)](https://github.com/SyHaqi/kiko/stargazers)
[![Report a bug](https://img.shields.io/github/issues/SyHaqi/kiko?style=for-the-badge&label=Issues&logo=github&logoColor=3b0f05&labelColor=ff9d7a&color=ffbda3)](https://github.com/SyHaqi/kiko/issues)

<!-- Row 3: small info pills -->
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack_Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Material 3](https://img.shields.io/badge/Material_3-Compose-2E51A2?style=flat-square&logo=materialdesign&logoColor=white)
[![Latest release downloads](https://img.shields.io/github/downloads/SyHaqi/kiko/latest/total?style=flat-square&label=this%20release&color=2E51A2)](https://github.com/SyHaqi/kiko/releases/latest)
[![Last commit](https://img.shields.io/github/last-commit/SyHaqi/kiko?style=flat-square&color=555)](https://github.com/SyHaqi/kiko/commits)
[![License](https://img.shields.io/github/license/SyHaqi/kiko?style=flat-square&color=555)](LICENSE)

</div>

---

## 🖼️ Screenshots

<div align="center">
<img src="docs/screenshots/home.jpg" alt="Home" width="19%">
<img src="docs/screenshots/my-list.jpg" alt="My List" width="19%">
<img src="docs/screenshots/discover.jpg" alt="Discover" width="19%">
<img src="docs/screenshots/title.jpg" alt="Title details" width="19%">
<img src="docs/screenshots/relation-pipeline.jpg" alt="Profile" width="19%">
</div>

<details>
<summary><b>More screenshots</b></summary>
<br>
<div align="center">
<img src="docs/screenshots/seasonal.jpg" alt="Seasonal" width="19%">
<img src="docs/screenshots/stacks.jpg" alt="Interest Stacks" width="19%">
<img src="docs/screenshots/forums.jpg" alt="Forums" width="19%">
<img src="docs/screenshots/forum-post.jpg" alt="Forum post" width="19%">
<img src="docs/screenshots/profile.jpg" alt="Clubs" width="19%">
</div>
</details>

---

## ✨ Features

- **Home**: airing schedule (times synced via AniList), MAL's latest announcement, forum snapshots and featured articles.
- **My List**: sync your MAL list, filter/sort/search, edit entries, and bump progress with a quick **+1**.
- **Discover**: search anime, manga, characters, people and companies with advanced filters and personalized recommendations.
- **Rankings & Seasonal Charts**: browse by score, popularity, favorites, or by season and year.
- **Title Details**: synopsis, trailers, characters, staff, reviews, recommendations, stats and related entries on one page.
- **Hub**: MAL forums and clubs in a single tab, plus Interest Stacks browsing.
- **Profile & Stats**: OAuth login, history, friends, and activity breakdowns by genre, score, format and year.
- **Appearance**: light/dark/system themes, dynamic or custom colors, AMOLED dark, Romaji/English titles and a list layout (grid or list) that applies app-wide.
- **Updates**: built-in update checker that pulls new versions straight from GitHub Releases.
- **Deep Links**: open MAL anime/manga links directly in Kiko.

---

## 📦 Installation

1. Go to the **[latest release](https://github.com/SyHaqi/kiko/releases/latest)** and download the APK.
2. Open it on your device. Android may ask you to allow installs from your browser or file manager.
3. Launch Kiko and sign in with your MyAnimeList account.

Kiko needs **Android 8.0 (API 26)** or newer. It isn't on Google Play, so after the first install the app tells you when a new release is out.

---

## 🛠️ Building from Source

**Requirements:** Android Studio, JDK 17, Android SDK Platform 37, and a MyAnimeList API client ID.

```bash
git clone https://github.com/SyHaqi/kiko.git
cd kiko
```

Copy `local.properties.example` → `local.properties` and set:

```properties
MAL_CLIENT_ID=YOUR_MYANIMELIST_CLIENT_ID
```

Kiko uses OAuth with the redirect URI `com.kiko.tracker://oauth/callback`. **Do not include a MAL client secret**, only the client ID is needed.

Build:

```bash
./gradlew assembleDebug     # macOS/Linux
gradlew.bat assembleDebug   # Windows
```

APK output: `app/build/outputs/apk/debug/`. Use `assembleRelease` for a minified, shrunk build with the baseline profile applied. Version numbers come from the latest Git tag (e.g. `v2.7.1` → `2.7.1`).

---

## 🧱 Built With

Kotlin · Jetpack Compose · Material 3 · AndroidX · Baseline Profiles · OkHttp · Coil · Jsoup · Kotlin Coroutines · MyAnimeList API · Tenrai API · AniList API

---

## 🌐 Data Sources

Kiko pulls data from **MyAnimeList** (accounts, lists, titles, forums, clubs, stacks), the **Tenrai API** (discovery/search), **AniList** (airing schedule accuracy), and **GitHub Releases** (update checks). Kiko doesn't operate or own these services.

---

## ⚠️ Disclaimer

Kiko is an **unofficial third-party MyAnimeList client**, not affiliated with, sponsored by, or endorsed by MyAnimeList. All MAL trademarks, artwork, and user content belong to their respective owners. Availability may change if MAL or other services update their APIs.

---

## 📄 License

Licensed under **GPL-3.0**, see [`LICENSE`](LICENSE) for full terms. You're free to use, study, modify, and redistribute Kiko's source under the same license. Third-party dependencies and assets may carry their own licenses.

Copyright © 2026 Kiko contributors.

---

## 💬 Community

Questions, feedback, or bug reports? [Open an issue](https://github.com/SyHaqi/kiko/issues) or join the [Discord](https://discord.gg/qytyH7efp6).

<div align="center">

**[🌐 Website](https://syhaqi.github.io/kiko/)** · **[⬇ Download](https://github.com/SyHaqi/kiko/releases/latest)** · **[🐞 Report a bug](https://github.com/SyHaqi/kiko/issues)**

</div>