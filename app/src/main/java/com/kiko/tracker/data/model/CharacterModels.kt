package com.kiko.tracker.data.model

import androidx.compose.runtime.Composable

// Character search/detail live outside
// (episodes, format, genres, watch
// character simply doesn't have,
// always blank for this
// a lightweight search-result row,

// One row in MAL's
data class CharacterSummary(
    val malId: Int,
    val name: String,
    val image: String = "",
    // Parenthetical nicknames/alt names shown
    // e.g. "(Hououin Kyouma, Okarin,
    val altName: String = "",
    // Titles this character appears
    val relatedWorks: List<String> = emptyList(),
)

// One row in a
// MAL's character page only
// own title-display preference is,
// LibraryViewModel.resolveCharacterWorkTitles (awaited before openCharacterDetail's
// onLoaded ever fires, so
// under Title Language: English)
// Title Language setting, same
// MediaItem.displayTitle() in Models.kt).
data class CharacterWork(val malId: Int, val title: String, val image: String = "", val role: String = "", val titleEnglish: String = "")

// Mirrors MediaItem.displayTitle()/secondaryTitle() in Models.kt
// preference, applied to a
@Composable
fun CharacterWork.displayTitle(): String {
    val pref = LocalTitleLanguage.current
    return if (pref == TitleLanguage.English && titleEnglish.isNotBlank()) titleEnglish else title
}

// One row in a
// CharacterEntry.japaneseVoiceActor on an anime/manga
// the Japanese cast for
data class CharacterVoiceActor(val malId: Int, val name: String, val image: String = "", val language: String = "")

// Full character detail page
data class CharacterDetail(
    val malId: Int,
    val name: String,
    // Parenthetical Japanese name from
    val nameKanji: String = "",
    // Quoted alter-ego/nicknames pulled out
    val nicknames: String = "",
    val image: String = "",
    val favorites: Int = 0,
    // Structured "Label: value" lines
    // Type, Height, Weight, Affiliations,
    // this character, in the
    val bioFields: List<Pair<String, String>> = emptyList(),
    // Free-text biography/background/timeline that follows
    val about: String = "",
    val voiceActors: List<CharacterVoiceActor> = emptyList(),
    val animeography: List<CharacterWork> = emptyList(),
    val mangaography: List<CharacterWork> = emptyList(),
)