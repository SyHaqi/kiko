package com.kiko.tracker

import androidx.compose.runtime.Composable

// Character search/detail live outside the MediaItem model on purpose — MediaItem's shape
// (episodes, format, genres, watch status, ...) is anime/manga tracking data that a
// character simply doesn't have, and forcing it in would mean a pile of fields that are
// always blank for this type. These are the two shapes Discover's Characters tab needs:
// a lightweight search-result row, and the fuller character detail page.

// One row in MAL's character search results (character.php?q=...)
data class CharacterSummary(
    val malId: Int,
    val name: String,
    val image: String = "",
    // Parenthetical nicknames/alt names shown under the name on the search results row,
    // e.g. "(Hououin Kyouma, Okarin, Mad Scientist)"
    val altName: String = "",
    // Titles this character appears in, in the order MAL lists them (anime then manga)
    val relatedWorks: List<String> = emptyList(),
)

// One row in a character's Animeography/Mangaography list. titleEnglish starts blank —
// MAL's character page only ever renders one title per work (whatever this MAL account's
// own title-display preference is, which this app doesn't control) — and is resolved by
// LibraryViewModel.resolveCharacterWorkTitles (awaited before openCharacterDetail's
// onLoaded ever fires, so this is never blank by the time displayTitle() below reads it
// under Title Language: English) so displayTitle() can actually honor this app's own
// Title Language setting, same as everywhere else titles appear (see
// MediaItem.displayTitle() in Models.kt).
data class CharacterWork(val malId: Int, val title: String, val image: String = "", val role: String = "", val titleEnglish: String = "")

// Mirrors MediaItem.displayTitle()/secondaryTitle() in Models.kt — same LocalTitleLanguage
// preference, applied to a character's Animeography/Mangaography rows.
@Composable
fun CharacterWork.displayTitle(): String {
    val pref = LocalTitleLanguage.current
    return if (pref == TitleLanguage.English && titleEnglish.isNotBlank()) titleEnglish else title
}

// One row in a character's Voice Actors list — every dub, not just Japanese (unlike
// CharacterEntry.japaneseVoiceActor on an anime/manga detail page, which only ever needs
// the Japanese cast for its own Voice Actors row).
data class CharacterVoiceActor(val malId: Int, val name: String, val image: String = "", val language: String = "")

// Full character detail page
data class CharacterDetail(
    val malId: Int,
    val name: String,
    // Parenthetical Japanese name from the page's own h2, e.g. "岡部 倫太郎"
    val nameKanji: String = "",
    // Quoted alter-ego/nicknames pulled out of the h1, e.g. "Hououin Kyouma, Okarin, Mad Scientist"
    val nicknames: String = "",
    val image: String = "",
    val favorites: Int = 0,
    // Structured "Label: value" lines from the top of the bio block (Age, Birthdate, Blood
    // Type, Height, Weight, Affiliations, Occupation, ...) — whatever MAL actually lists for
    // this character, in the order they appear on the page.
    val bioFields: List<Pair<String, String>> = emptyList(),
    // Free-text biography/background/timeline that follows the structured fields
    val about: String = "",
    val voiceActors: List<CharacterVoiceActor> = emptyList(),
    val animeography: List<CharacterWork> = emptyList(),
    val mangaography: List<CharacterWork> = emptyList(),
    // True from the moment this page first appears until LibraryViewModel.resolveCharacterWorkTitles
    // finishes patching English titles into the two rows above (see openCharacterDetail).
    // Only ever true when Title Language is English and at least one of those rows is
    // non-empty; false immediately otherwise. CharacterDetailScreen reads this — together
    // with each row entry's own titleEnglish being still blank — to know whether to show a
    // shimmer in place of that entry's title rather than the raw MAL-default one, so a
    // character page never actually displays the wrong-language title, just a placeholder
    // for the brief moment before the right one is in.
    val workTitlesLoading: Boolean = false,
)