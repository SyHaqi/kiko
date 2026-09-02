package com.kiko.tracker

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

// One row in a character's Animeography/Mangaography list
data class CharacterWork(val malId: Int, val title: String, val image: String = "", val role: String = "")

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
)