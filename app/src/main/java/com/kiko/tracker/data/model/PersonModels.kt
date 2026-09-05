package com.kiko.tracker.data.model

import androidx.compose.runtime.Composable

// Person (voice actor/staff) search/detail
// CharacterModels.kt's types do —
// (episodes, format, watch status,
// needs: a lightweight search-result

// One row in MAL's
data class PersonSummary(
    val malId: Int,
    val name: String,
    val image: String = "",
    // Parenthetical real/alternate names shown
    // e.g. "(Ono Kana)" —
    // uses for its own
    val altName: String = "",
)

// One row in a
// anime that role was
// in-app on the detail
// opens CharacterDetailScreen.
data class PersonVoiceRole(
    val workId: Int,
    val workTitle: String,
    // Starts blank — MAL's
    // same reasoning as CharacterWork.titleEnglish
    // LibraryViewModel.resolvePersonWorkTitles (awaited before openPersonDetail's
    // ever fires, so this
    // Title Language: English) so
    // Language setting.
    val workTitleEnglish: String = "",
    val workImage: String = "",
    // e.g. "TV, Summer 2026"
    val workInfo: String = "",
    val characterId: Int,
    val characterName: String,
    val characterImage: String = "",
    // "Main" or "Supporting"
    val roleLabel: String = "",
)

// Mirrors MediaItem.displayTitle()/CharacterWork.displayTitle() — same
// preference, applied to a
@Composable
fun PersonVoiceRole.displayTitle(): String {
    val pref = LocalTitleLanguage.current
    return if (pref == TitleLanguage.English && workTitleEnglish.isNotBlank()) workTitleEnglish else workTitle
}

// Full person detail page
data class PersonDetail(
    val malId: Int,
    val name: String,
    val image: String = "",
    val favorites: Int = 0,
    // Structured "Label: value" lines
    // Hometown, Blood type, Height,
    // for this person, in
    // every field, unlike a
    val bioFields: List<Pair<String, String>> = emptyList(),
    // Free-text bio paragraph pulled
    // membership) — kept separate
    // CharacterDetail.about is, so it
    // stuck onto whichever labeled
    val about: String = "",
    val voiceActingRoles: List<PersonVoiceRole> = emptyList(),
    // Anime Staff Positions (Theme
    // both are already real
    // reuse the same DetailRowCard
    // rows use, rather than
    val staffCredits: List<MediaItem> = emptyList(),
    val publishedManga: List<MediaItem> = emptyList(),
)