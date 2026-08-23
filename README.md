# Kiko for Android

> A modern, unofficial MyAnimeList client for tracking anime and manga.

Kiko is an Android app built around MyAnimeList (MAL), with a focus on a clean Material 3 interface, fast discovery, detailed title pages, personal list management, forums, Interest Stacks, clubs, rankings, seasonal charts, and profile statistics.

Join the community on Discord: [Himawari HS](https://discord.gg/KZYQHpDWKH)

---

## ✨ Features

### 🏠 Home
- See anime that are **airing next**, with the next episode and local broadcast time.
- Quickly return to your most recently updated **currently watching/reading** title.
- Browse recent MAL forum/news snapshots.
- See a featured **Interest Stack** directly from Home.
- Pull to refresh Home content.

### 📚 My List
- Sync your anime and manga lists with your **MyAnimeList account**.
- Browse lists by status:
  - Watching / Reading
  - Completed
  - On Hold
  - Dropped
  - Plan to Watch / Plan to Read
- Search within your list.
- Sort by:
  - Title
  - Score
  - Last Updated
  - Start Date
- Switch between **list and grid** layouts.
- Edit list entries, including:
  - Status
  - Progress
  - Score
  - Start date
  - End date
  - Rewatch/reread information
  - Tags
  - Notes
- Update or remove entries directly from Kiko.
- Quickly increment episode/chapter progress with **+1**.

### 🔎 Discover
- Search and browse anime and manga.
- Use the **Tenrai API** for discovery/search and richer filtering.
- Advanced filters include:
  - Anime / Manga type
  - Status
  - Source
  - Year
  - Season
  - Rating
  - Format
  - Airing status
  - Genres
  - Explicit genres
  - Themes
  - Demographics
  - Creator
- Sort discovery results by:
  - Members
  - Score
  - Newest
  - Title
- Open a title's genre or creator information to discover related entries.
- Browse a personalized **You might like** recommendation section.

### 🏆 Rankings
- Browse MAL rankings for anime and manga.
- Ranking modes include:
  - Score
  - Popularity
  - Favorites
  - Upcoming anime
- Open any ranked title directly.

### 🌸 Seasonal Chart
- Browse anime by season and year.
- Navigate between Winter, Spring, Summer, and Fall.
- Sort seasonal titles by:
  - Members
  - Score
- Include anime that are still airing from previous seasons.

### 🗓️ Release Schedule (accessed by the see more in "airing next")
- View upcoming episode releases by day.
- Times are displayed using the device's local time format.

### 📖 Title Details
Title pages can include:
- Synopsis
- Characters & Voice Actors
- Staff
- Theme songs
- Reviews
- Recommendations
- Related entries
- Statistics
- Background information
- Status distribution
- Genres, themes, demographics, and other metadata
- Links to related people/companies where available
- Tap genres/creators to continue discovering related titles

### 💬 Forums
- Browse MyAnimeList forum boards and topics.
- Read forum topics and posts inside Kiko.
- View post counts, authors, timestamps, polls, and topic images.
- Render images included in forum posts.
- Support animated **GIFs**.
- Tap images to open a larger viewer.
- **Pinch-to-zoom and pan** forum images.
- Render supported MAL BBCode-style forum content.
- MAL does not provide Kiko with the ability to create forum posts; forum participation remains on the official MyAnimeList website.

### 📰 Forum / News Snapshots
- Browse recent forum/news topics from Home.
- Topic thumbnails are displayed when available.
- Open a snapshot to read the full topic.

### 🧩 Interest Stacks
- Browse MyAnimeList Interest Stacks.
- Browse:
  - All
  - Challenges
  - Anime
  - Manga
  - MyAnimeList
- Search and load more stacks.
- Open a stack to view its entries.
- View stack covers, author information, tags, entry counts, restacks, and update information.
- Track your progress through titles contained in a stack.

> Interest Stacks are obtained from MyAnimeList web pages because this feature is not provided through the public MAL API.

### 👥 MyAnimeList Clubs
- Browse and search MyAnimeList clubs.
- Open club details.
- View club information, staff, members, and Couch posts.
- Load additional club content.
- Open relevant MAL pages in the browser when interaction is required.

### 👤 Profile & Statistics
- Connect your MyAnimeList account using OAuth.
- View profile information and account statistics.
- View anime/manga activity statistics.
- Compare **time watched vs. time read**.
- Explore:
  - Genre breakdown
  - Score distribution
  - Format breakdown
  - Year distribution
- Tap score distribution segments to see titles at a particular score.

### 🎨 Appearance & Settings
- System, light, or dark theme.
- App-default, dynamic, or custom colors.
- Tonal Spot, Neutral, or Monochrome color palettes.
- Choose **Romaji or English** title display.
- Optional adult-content display.
- Built-in About page with app version and update checking.

### 🔄 Updates
- Built-in update checker.
- Notify the user when a newer Kiko release is available.
- Download and hand the APK to the Android package installer from within Kiko.

### 🔗 MyAnimeList Links
- Kiko can handle MyAnimeList anime and manga links and open matching entries directly in the app when Android allows Kiko to be selected as the handler.

---

## 🖼️ Screenshots

![Screenshots](screenshots/screenshots.png)
![Screenshots2](screenshots/screenshots2.png)

---

## 📦 Installation

Download the latest APK from the **GitHub Releases** page.

Kiko currently targets **Android 8.0 (API 26) and above**.

Because Kiko is distributed outside Google Play, Android may ask you to allow installation from the source you used to obtain the APK.

---

## 🛠️ Building from Source

### Requirements

- Android Studio with a current Android SDK
- JDK 17
- Android SDK Platform 35
- Android SDK Build-Tools
- A MyAnimeList API client ID

### 1. Clone the repository

```bash
git clone https://github.com/SyHaqi/kiko.git
cd kiko
```

### 2. Configure the MyAnimeList client ID

Copy:

```text
local.properties.example
```

to:

```text
local.properties
```

Then set:

```properties
MAL_CLIENT_ID=YOUR_MYANIMELIST_CLIENT_ID
```

Kiko uses MyAnimeList OAuth with the redirect URI:

```text
com.kiko.tracker://oauth/callback
```

**Do not put a MyAnimeList client secret in the Android project.** The app only needs the client ID.

### 3. Build

On Windows:

```bat
gradlew.bat assembleDebug
```

On macOS/Linux:

```bash
./gradlew assembleDebug
```

The APK will be generated under:

```text
app/build/outputs/apk/debug/
```

### Release builds

Kiko derives its version name from the latest Git tag. For example:

```text
v2.7.1
```

becomes:

```text
2.7.1
```

---

## 🧱 Built With

- **Kotlin**
- **Jetpack Compose**
- **Material 3**
- **AndroidX**
- **MyAnimeList API**
- **Tenrai API**
- **OkHttp**
- **Coil**
- **Jsoup**
- **Kotlin Coroutines**

---

## 🌐 Data Sources & Services

Kiko retrieves information from several services:

- **MyAnimeList** — account authentication, lists, title data, rankings, seasonal data, forums, profiles, clubs, Interest Stacks, and related community content.
- **Tenrai API** — title discovery/search and additional metadata/filtering support.
- **GitHub Releases** — used by Kiko's built-in update checker to determine whether a newer app version is available.

Kiko does not operate or own these services.

---

## ❤️ Credits

Kiko would not be possible without the services and open-source projects it uses.

Special thanks to:

- MyAnimeList
- Tenrai API
- Jetpack Compose / AndroidX
- Coil
- OkHttp
- Jsoup
- Kotlin Coroutines
- The maintainers of all other open-source dependencies used by Kiko

Third-party libraries remain subject to their respective licenses. See their individual project repositories and license files for details.

---

## ⚠️ Disclaimer

Kiko is an **unofficial third-party MyAnimeList client**.

Kiko is **not affiliated with, sponsored by, or endorsed by MyAnimeList**.

MyAnimeList names, trademarks, artwork, anime/manga information, user-generated content, forum content, and other third-party content belong to their respective owners.

Kiko only provides an alternative client interface for accessing available information and services. Availability and functionality may change if MyAnimeList or other services change their APIs or website structure.

---

## 📄 License

The Kiko source code is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

A complete copy of the license is included in the [`LICENSE`](LICENSE) file in this repository.

In short, GPL-3.0 allows you to use, study, modify, and redistribute the covered source code under the terms of the license. Modified or derivative versions that you distribute must preserve the applicable GPL-3.0 licensing requirements.

**Important:** Third-party dependencies, artwork, screenshots, service content, and other materials that are not part of Kiko's original source code may have separate licenses or usage restrictions.

See [`LICENSE`](LICENSE) for the complete legal terms.

---

## 📜 License Notice

Copyright © 2026 Kiko contributors.

Kiko is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 3.

Kiko is distributed in the hope that it will be useful, but **WITHOUT ANY WARRANTY**; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

---

## 💬 Community

For discussion, feedback, bug reports, and development updates, join the [Kiko Discord community](https://discord.gg/KZYQHpDWKH).

