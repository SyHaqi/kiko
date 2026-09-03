package com.kiko.tracker

import androidx.compose.runtime.Composable

// Person (voice actor/staff) search/detail live outside MediaItem for the same reason
// CharacterModels.kt's types do — a person has none of MediaItem's tracking fields
// (episodes, format, watch status, ...). These are the two shapes Discover's People tab
// needs: a lightweight search-result row, and the fuller person detail page.

// One row in MAL's people search results (people.php?cat=person&q=...)
data class PersonSummary(
    val malId: Int,
    val name: String,
    val image: String = "",
    // Parenthetical real/alternate names shown under the name on the search results row,
    // e.g. "(Ono Kana)" — kept as-is (parens included), same convention CharacterSummary
    // uses for its own altName.
    val altName: String = "",
)

// One row in a person's Voice Acting Roles table: the character they voiced, and the
// anime that role was in. Two ids/images (work + character) since both link somewhere
// in-app on the detail page — the work opens this app's own DetailScreen, the character
// opens CharacterDetailScreen.
data class PersonVoiceRole(
    val workId: Int,
    val workTitle: String,
    // Starts blank — MAL's Voice Acting Roles table only ever renders one title per work,
    // same reasoning as CharacterWork.titleEnglish in CharacterModels.kt — resolved by
    // LibraryViewModel.resolvePersonWorkTitles (awaited before openPersonDetail's onLoaded
    // ever fires, so this is never blank by the time displayTitle() below reads it under
    // Title Language: English) so displayTitle() can actually honor this app's own Title
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

// Mirrors MediaItem.displayTitle()/CharacterWork.displayTitle() — same LocalTitleLanguage
// preference, applied to a person's Voice Acting Roles row.
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
    // Structured "Label: value" lines (Given name, Family name, Alternate names, Birthday,
    // Hometown, Blood type, Height, Skills & Abilities, ...) — whatever MAL actually lists
    // for this person, in the order they appear on the page. Not every person page has
    // every field, unlike a character's more fixed bio shape.
    val bioFields: List<Pair<String, String>> = emptyList(),
    // Free-text bio paragraph pulled out of the "More:" block (e.g. seiyuu-unit
    // membership) — kept separate from bioFields for the same reason
    // CharacterDetail.about is, so it renders as its own prose section instead of getting
    // stuck onto whichever labeled field happened to sit next to it on the page.
    val about: String = "",
    val voiceActingRoles: List<PersonVoiceRole> = emptyList(),
    // Anime Staff Positions (Theme Song Performance, Director, ...) and Published Manga —
    // both are already real MediaItem lists via MalPeopleApi.fetchCreditedWorks, so they
    // reuse the same DetailRowCard the anime/manga detail page's own Related/Recommended
    // rows use, rather than a bespoke shape just for this page.
    val staffCredits: List<MediaItem> = emptyList(),
    val publishedManga: List<MediaItem> = emptyList(),
)