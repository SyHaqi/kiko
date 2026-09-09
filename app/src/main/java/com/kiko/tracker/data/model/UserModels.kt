package com.kiko.tracker.data.model

// One row in MAL's user search results (users.php) — just enough to
// render a row/tile and open that user's profile (see
// FriendProfileScreen, which this reuses: no separate "UserProfile"
// screen needed since it's already username-in, scraped-profile-out).
// Unlike PersonSummary/CompanySummary/CharacterSummary there's no MAL
// numeric id surfaced on the search page — usernames are the key MAL
// itself uses for profile URLs, so that's what's carried here too.
data class UserSummary(
    val username: String,
    val avatarUrl: String = "",
    // Raw "joined" timestamp text as MAL prints it, e.g. "Mar 9, 2010
    // 3:27 AM" — shown as-is rather than parsed, same spirit as
    // PersonSummary.altName being kept as a plain display string.
    val joined: String = "",
)

// Advanced filters for the Users tab — mirrors MAL's own users.php
// "Advanced Search" panel (Location/Age/Gender) rather than the
// Anime/Manga DiscoverFilters shape, since none of the genre/format/year
// facets there apply to a user search.
data class UserSearchFilters(
    val location: String = "",
    val ageLow: Int? = null,
    val ageHigh: Int? = null,
    // "", "Male", "Female", "Non-Binary" — blank means MAL's own
    // "Don't care" option.
    val gender: String = "",
) {
    fun isActive() = location.isNotBlank() || ageLow != null || ageHigh != null || gender.isNotBlank()
}