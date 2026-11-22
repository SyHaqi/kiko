package com.kiko.tracker.ui.screens

import com.kiko.tracker.data.api.BlogRef
import com.kiko.tracker.data.api.EpisodeEntry
import com.kiko.tracker.data.api.MagazineTitle
import com.kiko.tracker.data.api.MalMessage
import com.kiko.tracker.data.api.SiteReview

/** One list's loaded rows, paging position and scroll position, kept so the screen comes back exactly as it was left. */
internal class PagedCache<T> {
    var items: List<T> = emptyList()
    var page = 1
    var hasMore = false
    var loaded = false
    /** Scroll position: first visible item and its offset (for scrollable columns [index] holds the pixel value). */
    var index = 0
    var offset = 0
    /** A free number a screen can stash with the list (inbox total, or the reload key the list was fetched under). */
    var extra = 0
}

/**
 * The newer MAL screens (Reviews, Blogs, Messages, Episodes, Magazines) keep their lists, the selected sub-tab and the scroll position
 * here, the same way Forums and Clubs keep theirs in the view model: opening a review or a title and coming back shows the list instantly,
 * where you left it, with no reload. Pull to refresh fetches again. Lives for the app process; cleared when the signed-in account changes.
 */
internal object BrowseCache {
    var reviewsManga = false
    val reviews = mapOf(false to PagedCache<SiteReview>(), true to PagedCache<SiteReview>())

    var blogsMine = false
    val blogs = mapOf(false to PagedCache<BlogRef>(), true to PagedCache<BlogRef>())

    var messagesSent = false
    val messages = mapOf(false to PagedCache<MalMessage>(), true to PagedCache<MalMessage>())

    val episodes = HashMap<Int, PagedCache<EpisodeEntry>>()
    val magazineTitles = HashMap<Int, PagedCache<MagazineTitle>>()
    var magazineQuery = ""
    var magazineIndex = 0
    var magazineOffset = 0

    fun episodesFor(malId: Int) = episodes.getOrPut(malId) { PagedCache() }
    fun magazineTitlesFor(id: Int) = magazineTitles.getOrPut(id) { PagedCache() }

    fun clear() {
        reviewsManga = false; blogsMine = false; messagesSent = false; magazineQuery = ""; magazineIndex = 0; magazineOffset = 0
        listOf(reviews, blogs, messages).forEach { m -> m.values.forEach { it.reset() } }
        episodes.clear(); magazineTitles.clear()
    }

    private fun <T> PagedCache<T>.reset() { items = emptyList(); page = 1; hasMore = false; loaded = false; index = 0; offset = 0; extra = 0 }
}
