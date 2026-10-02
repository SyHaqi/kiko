@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import com.kiko.tracker.ui.components.headerEdgeEnd
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.components.headerTitleStart
import com.kiko.tracker.util.openInBrowser
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.kiko.tracker.ui.components.ChoiceDialog
import com.kiko.tracker.BuildConfig
import com.kiko.tracker.data.api.MalAboutMe
import com.kiko.tracker.data.api.MalAboutMeItem
import com.kiko.tracker.data.api.MalFavoriteEntry
import com.kiko.tracker.data.api.MalFavorites
import com.kiko.tracker.data.api.MalFriend
import com.kiko.tracker.data.api.MalProfile
import com.kiko.tracker.data.api.MalSessionCookie
import com.kiko.tracker.data.model.ColorSource
import com.kiko.tracker.data.model.ListViewMode
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.PaletteStyle
import com.kiko.tracker.data.model.ThemeMode
import com.kiko.tracker.data.model.TitleLanguage
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.oneDecimal
import com.kiko.tracker.data.model.twoDecimals
import com.kiko.tracker.ui.components.Pill
import com.kiko.tracker.ui.components.SkeletonBlock
import com.kiko.tracker.ui.components.TypeToggle
import com.kiko.tracker.ui.components.centerChip
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.components.statusColor
import com.kiko.tracker.ui.theme.KikoColors
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.StaggeredItem
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.ui.theme.parseHexColor
import com.kiko.tracker.ui.theme.rememberStaggerMemory
import com.kiko.tracker.util.AppUpdateInfo
import com.kiko.tracker.viewmodel.LibraryViewModel

// Small (icon, label) pair for the location/gender/birthday/joined pill
// row under the avatar+name — the icon exists mainly so birthday and
// joined-date (both just look like "Month D, YYYY") are distinguishable
// at a glance instead of reading identically.
data class DetailPill(val icon: androidx.compose.ui.graphics.vector.ImageVector, val text: String)

// Full page for the
@Composable fun ProfileStatsScreen(
    connected: Boolean, profile: MalProfile?, items: List<MediaItem>, onConnect: () -> Unit, onBack: () -> Unit,
    scrollOffset: Int = 0, onSaveScroll: (Int) -> Unit = {}, statsTab: MediaType = MediaType.Anime, onStatsTabChange: (MediaType) -> Unit = {},
    onScoreClick: (MediaType, Int) -> Unit = { _, _ -> }, onYearClick: (MediaType, Int) -> Unit = { _, _ -> }, onFormatClick: (MediaType, String) -> Unit = { _, _ -> },
    onGenreClick: (MediaType, String) -> Unit = { _, _ -> },
    onSignOut: () -> Unit = {}, refreshing: Boolean = false, onRefresh: () -> Unit = {},
    onOpenFriendsFavorites: () -> Unit = {},
    // Tapping a friend in the Friends row — opens an in-app FriendProfileScreen.
    onOpenFriend: (MalFriend) -> Unit = {},
    // Favorites (anime/manga/characters/people/companies) open in-app, same
    // as everywhere else in Kiko. Row scroll positions and the loading id
    // are hoisted up to the ViewModel (via Navigation) since Profile is torn
    // down while a detail page is on top, same reasoning as scrollOffset above.
    onOpenCharacter: (Int) -> Unit = {}, onOpenPerson: (Int) -> Unit = {}, onOpenCompany: (Int) -> Unit = {},
    onOpenFavoriteTitle: (Int, MediaType) -> Unit = { _, _ -> }, favoriteLoadingId: Int? = null,
    friendsRowScroll: Pair<Int, Int> = 0 to 0, onSaveFriendsRowScroll: (Int, Int) -> Unit = { _, _ -> },
    getFavoritesRowScroll: (String) -> Pair<Int, Int> = { 0 to 0 }, onSaveFavoritesRowScroll: (String, Int, Int) -> Unit = { _, _, _ -> },
    // Friends/favorites cache lives in the ViewModel (see LibraryViewModel)
    // so it survives Profile being torn down and rebuilt while a favorite's
    // detail page is on top — cachedFriends/cachedFavorites are null until
    // onLoadFriendsFavorites has fetched them at least once.
    cachedFriends: List<MalFriend>? = null, cachedFavorites: MalFavorites? = null,
    onLoadFriendsFavorites: (String) -> Unit = {},
    friendsFavoritesLoading: Boolean = false,
    cachedAboutMe: MalAboutMe? = null,
    // Forwarded straight to ProfileStatsSection (own-profile path only —
    // ProfileStatsScreen is always Kiko's own signed-in Profile, so
    // isOwnProfile stays at its default true here).
    onOpenListStatus: (MediaType, String) -> Unit = { _, _ -> },
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    // Leaving the Profile page
    // remembered scroll offset, so
    // (Drilling into the score
    // separately below — that
    val exitProfile = { onBack(); onStatsTabChange(MediaType.Anime); onSaveScroll(0) }
    BackHandler(onBack = exitProfile)
    // Confirm before signing out
    // account it signs out
    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = c.surfaceContainerHigh,
            title = { Text("Sign out?", color = c.ink) },
            text = { Text("Are you sure you want to sign out of your MyAnimeList account?", color = c.muted) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }, colors = ButtonDefaults.textButtonColors(contentColor = c.danger)) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }, colors = ButtonDefaults.textButtonColors(contentColor = c.muted)) { Text("Cancel") } },
        )
    }
    // Restore scroll position on
    val scrollState = rememberScrollState(initial = scrollOffset)
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        // No blanket horizontal padding here anymore — the hero panel inside
        // ProfileStatsSection now runs edge-to-edge (full-bleed, no card
        // margin around it), the way a page header does on a website rather
        // than another tile in a stack of cards. Everything below the hero
        // applies its own horizontal inset instead.
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 13.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = exitProfile, modifier = Modifier.headerEdgeStart(20.dp)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
                // "Profile" moved down to a small eyebrow label above the
                // username in the avatar/name row below (ProfileStatsSection),
                // matching DetailScreen's small-label-above-title pattern —
                // so this row is now just the back button, spacer, and menu.
                Spacer(Modifier.weight(1f))
                // 3-dot overflow menu — replaces the separate "open in
                // browser" button that used to live on the avatar card, and
                // the standalone sign-out icon that used to sit here. Only
                // shown once connected, since both items need a MAL session.
                if (connected) {
                    var moreOpen by remember { mutableStateOf(false) }
                    Box(Modifier.headerEdgeEnd(20.dp)) {
                        IconButton(onClick = { moreOpen = true }) {
                            Icon(Icons.Default.MoreVert, "More options", tint = c.ink, modifier = Modifier.size(24.dp))
                        }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }, shape = RoundedCornerShape(kikoCorner(18.dp)), containerColor = c.surfaceContainer) {
                            if (profile?.name?.isNotBlank() == true) {
                                DropdownMenuItem(
                                    text = { Text("Open in browser") },
                                    leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                                    onClick = {
                                        moreOpen = false
                                        context.openInBrowser("https://myanimelist.net/profile/${profile.name}")
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = c.danger) },
                                onClick = { moreOpen = false; confirmSignOut = true },
                            )
                        }
                    }
                }
            }
            Box(Modifier.padding(bottom = 24.dp)) {
                ProfileStatsSection(
                    connected, profile, items, onConnect, statsTab = statsTab, onStatsTabChange = onStatsTabChange,
                    onScoreClick = { type, score -> onSaveScroll(scrollState.value); onScoreClick(type, score) },
                    onYearClick = { type, year -> onSaveScroll(scrollState.value); onYearClick(type, year) },
                    onFormatClick = onFormatClick,
                    onGenreClick = { type, genre -> onSaveScroll(scrollState.value); onGenreClick(type, genre) },
                    onOpenFriendsFavorites = { onSaveScroll(scrollState.value); onOpenFriendsFavorites() },
                    onOpenFriend = { friend -> onSaveScroll(scrollState.value); onOpenFriend(friend) },
                    onOpenCharacter = { malId -> onSaveScroll(scrollState.value); onOpenCharacter(malId) },
                    onOpenPerson = { malId -> onSaveScroll(scrollState.value); onOpenPerson(malId) },
                    onOpenCompany = { malId -> onSaveScroll(scrollState.value); onOpenCompany(malId) },
                    onOpenFavoriteTitle = { malId, type -> onSaveScroll(scrollState.value); onOpenFavoriteTitle(malId, type) },
                    favoriteLoadingId = favoriteLoadingId,
                    friendsRowScroll = friendsRowScroll, onSaveFriendsRowScroll = onSaveFriendsRowScroll,
                    getFavoritesRowScroll = getFavoritesRowScroll, onSaveFavoritesRowScroll = onSaveFavoritesRowScroll,
                    cachedFriends = cachedFriends, cachedFavorites = cachedFavorites, onLoadFriendsFavorites = onLoadFriendsFavorites,
                    friendsFavoritesLoading = friendsFavoritesLoading,
                    cachedAboutMe = cachedAboutMe,
                    onOpenListStatus = { type, label -> onSaveScroll(scrollState.value); onOpenListStatus(type, label) },
                )
            }
        }
    }
}

// Full page for the app settings, laid out as Play Store-style sections.
@Composable fun SettingsScreen(
    connected: Boolean, themeMode: ThemeMode, colorSource: ColorSource, paletteStyle: PaletteStyle, titleLanguage: TitleLanguage,
    nsfwEnabled: Boolean, onNsfwChange: (Boolean) -> Unit,
    amoledDark: Boolean, onAmoledDarkChange: (Boolean) -> Unit,
    onThemeClick: () -> Unit, onColorClick: () -> Unit, onPaletteClick: () -> Unit, onTitleLanguageClick: () -> Unit,
    updateInfo: AppUpdateInfo?, onAboutClick: () -> Unit, onBack: () -> Unit,
    listViewMode: ListViewMode = ListViewMode.List, onListViewModeChange: (ListViewMode) -> Unit = {},
    listSort: com.kiko.tracker.data.model.ListSort = com.kiko.tracker.data.model.ListSort.Title, onListSortChange: (com.kiko.tracker.data.model.ListSort) -> Unit = {},
    onSignOut: (() -> Unit)? = null,
) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.headerTitleStart())
        }
        Box(Modifier.padding(top = 12.dp, bottom = 24.dp)) {
            SettingsSection(
                connected = connected, themeMode = themeMode, colorSource = colorSource, paletteStyle = paletteStyle, titleLanguage = titleLanguage,
                nsfwEnabled = nsfwEnabled, onNsfwChange = onNsfwChange,
                amoledDark = amoledDark, onAmoledDarkChange = onAmoledDarkChange,
                onThemeClick = onThemeClick, onColorClick = onColorClick, onPaletteClick = onPaletteClick, onTitleLanguageClick = onTitleLanguageClick,
                updateInfo = updateInfo, onAboutClick = onAboutClick,
                listViewMode = listViewMode, onListViewModeChange = onListViewModeChange,
                listSort = listSort, onListSortChange = onListSortChange,
                onSignOut = onSignOut,
            )
        }
    }
}

// Profile header card +
// expandable "avatar + name"
@Composable fun ProfileStatsSection(
    connected: Boolean, profile: MalProfile?, items: List<MediaItem>, onConnect: () -> Unit, statsTab: MediaType = MediaType.Anime, onStatsTabChange: (MediaType) -> Unit = {},
    onScoreClick: (MediaType, Int) -> Unit = { _, _ -> }, onYearClick: (MediaType, Int) -> Unit = { _, _ -> }, onFormatClick: (MediaType, String) -> Unit = { _, _ -> }, onGenreClick: (MediaType, String) -> Unit = { _, _ -> }, onOpenFriendsFavorites: () -> Unit = {},
    onOpenCharacter: (Int) -> Unit = {}, onOpenPerson: (Int) -> Unit = {}, onOpenCompany: (Int) -> Unit = {},
    // Tapping a friend in FriendsRow — opens an in-app FriendProfileScreen
    // for that friend instead of falling back to the browser.
    onOpenFriend: (MalFriend) -> Unit = {},
    onOpenFavoriteTitle: (Int, MediaType) -> Unit = { _, _ -> }, favoriteLoadingId: Int? = null,
    friendsRowScroll: Pair<Int, Int> = 0 to 0, onSaveFriendsRowScroll: (Int, Int) -> Unit = { _, _ -> },
    getFavoritesRowScroll: (String) -> Pair<Int, Int> = { 0 to 0 }, onSaveFavoritesRowScroll: (String, Int, Int) -> Unit = { _, _, _ -> },
    // Friends/favorites cache lives in the ViewModel (see LibraryViewModel)
    // so it survives Profile being torn down and rebuilt while a favorite's
    // detail page is on top — cachedFriends/cachedFavorites are null until
    // onLoadFriendsFavorites has fetched them at least once.
    cachedFriends: List<MalFriend>? = null, cachedFavorites: MalFavorites? = null,
    onLoadFriendsFavorites: (String) -> Unit = {},
    // True while loadProfileFriendsFavorites' network round-trip is in
    // flight — used below to show a skeleton in place of the friends/
    // favorites rows instead of them just silently popping in once loaded.
    friendsFavoritesLoading: Boolean = false,
    // Overrides the default location/gender pill row under the avatar+name
    // with a caller-supplied one, in caller order — used by
    // FriendProfileScreen to show Online/Gender/Birthday/Joined here
    // instead of duplicating them in a separate row above this card.
    // Null (the default) keeps Profile's own location+gender behavior.
    detailsPills: List<DetailPill>? = null,
    // MAL's free-form "About Me" widget (banner/name/intro + auto-generated
    // rows) — scraped alongside friends/favorites (same cookie session, see
    // hasFfSession below), so it's null until that scrape has run once and
    // MalAboutMe.isEmpty when the user hasn't set one up on MAL at all.
    cachedAboutMe: MalAboutMe? = null,
    // True for Kiko's own signed-in Profile page (ProfileStatsScreen), false
    // for another MAL member's page (FriendProfileScreen) — decides what
    // tapping a Watching/Completed/On-Hold/Dropped/Plan-to-Watch legend row
    // does below: navigate in-app to My List on your own profile (since
    // that data is already synced locally), or open that status's MAL list
    // page in the browser for anyone else (MAL doesn't expose another
    // member's list through the API/app the way it does your own).
    isOwnProfile: Boolean = true,
    onOpenListStatus: (MediaType, String) -> Unit = { _, _ -> },
    // Friend path: a single "See List" action per tab (bottom-right of the
    // Stats card, see below) opens that type's full list in Kiko's own
    // FriendListScreen — replaces what used to be five separately-tappable
    // StatusLegendRow taps (one per status), which read as more interactive
    // than a read-only friend's list actually is.
    onOpenFriendList: (MediaType) -> Unit = {},
    // Horizontal padding the CALLER already applies around this whole section (FriendProfileScreen
    // wraps it in 14dp); added to this section's own 20dp so About Me still reaches the screen edge.
    outerInset: androidx.compose.ui.unit.Dp = 0.dp,
) {    val c = LocalKikoColors.current
    val context = LocalContext.current
    // Friends/favorites aren't in MAL's official API — scraped off the
    // profile page, which needs the separate logged-in cookie session
    // (MalSessionCookie/MalLoginWebView) that FriendsFavoritesScreen sets
    // up. The actual fetch + cache lives in the ViewModel (cachedFriends/
    // cachedFavorites/onLoadFriendsFavorites) so it survives this composable
    // being torn down and rebuilt; hasFfSession just decides what to render
    // here (the rows once loaded, or the fallback "Friends & Favorites"
    // card that opens the full screen and its login flow if there's no
    // session yet).
    val hasFfSession = remember { MalSessionCookie(context).has() }
    // Tapping a Watching/Completed/On-Hold/Dropped/Plan-to-Watch legend row
    // below (see isOwnProfile doc above) is only wired up on your own
    // profile now — in-app My List navigation for that status. A friend's
    // rows are display-only; opening their list happens via the single
    // "See List" button per tab instead (onOpenFriendList below).
    val openOwnStatus: (MediaType, WatchStatus) -> Unit = { type, status ->
        val label = when (status) {
            WatchStatus.Watching, WatchStatus.Reading -> if (type == MediaType.Anime) "Watching" else "Reading"
            WatchStatus.Plan -> if (type == MediaType.Anime) "Plan to Watch" else "Plan to Read"
            WatchStatus.Completed -> "Completed"
            WatchStatus.OnHold -> "On Hold"
            WatchStatus.Dropped -> "Dropped"
        }
        onOpenListStatus(type, label)
    }
    LaunchedEffect(connected, profile?.name, hasFfSession) {
        val username = profile?.name.orEmpty()
        if (connected && hasFfSession && username.isNotBlank()) onLoadFriendsFavorites(username)
    }
    Column {
        // Body content sits on the plain page background, same margin as
        // everywhere else — no tonal banner setting the header apart.
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (connected && profile != null) {
                // ---- Header: avatar left, name right -------------------------
                // Same rounded-square avatar shape as the app header's Avatar()
                // (RoundedCornerShape, not a circle), just larger — reads as
                // the same avatar language used everywhere else in Kiko.
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (profile.picture.isNotBlank()) {
                        AsyncImage(
                            model = profile.picture, contentDescription = profile.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(kikoCorner(28.dp))).background(c.warm),
                        )
                    } else {
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(kikoCorner(28.dp))).background(c.warm), contentAlignment = Alignment.Center) {
                            Text(profile.name.take(1).uppercase().ifBlank { "M" }, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = c.ink)
                        }
                    }
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        // Small "PROFILE" eyebrow label above the username —
                        // same treatment DetailScreen gives its type/format
                        // label above the title (color c.primary, bold,
                        // uppercase, letterSpacing) — now that the header
                        // row's own "Profile" text is gone.
                        Text("PROFILE", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
                        Text(
                            profile.name.ifBlank { "MyAnimeList" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            color = c.ink, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                // "Open in browser" / sign out live in the 3-dot menu up in
                // the Profile header (ProfileStatsScreen) instead of here.
                val details = detailsPills ?: listOfNotNull(
                    profile.location.takeIf { it.isNotBlank() }?.let { DetailPill(Icons.Default.LocationOn, it) },
                    profile.gender.takeIf { it.isNotBlank() }?.let { DetailPill(Icons.Default.Person, it) },
                    profile.birthday.take(10).takeIf { it.length == 10 }?.let { DetailPill(Icons.Default.Cake, formatFullDate(it)) },
                    // Was a plain "Joined ..." line next to the name
                    // above — moved down here as a pill (same ISO-date
                    // formatting via formatFullDate) so it matches
                    // FriendProfileScreen's Online/Gender/Born/Joined
                    // pill row instead of looking like a different
                    // pattern on your own profile. Own icon (Event,
                    // vs. birthday's Cake) so the two dates read
                    // differently at a glance.
                    profile.joinedAt.take(10).takeIf { it.length == 10 }?.let { DetailPill(Icons.Default.Event, formatFullDate(it)) },
                )
                if (details.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 14.dp),
                    ) {
                        details.forEach { Pill(it.text, c.surfaceLow, c.muted, icon = it.icon) }
                    }
                }
                // Right under the header — MAL's own mobile webview puts About Me
                // directly below the avatar/name block too, above
                // Friends/Favorites, so this mirrors that order. Shares
                // friendsFavoritesLoading with Friends/Favorites below since
                // it's scraped in the same round-trip (see loadProfileFriendsFavorites).
                if (cachedAboutMe == null && friendsFavoritesLoading) {
                    AboutMeCardSkeleton(bleed = 20.dp + outerInset)
                } else {
                    cachedAboutMe?.let { AboutMeCard(it, onOpenTitle = onOpenFavoriteTitle, bleed = 20.dp + outerInset) }
                }
                if (hasFfSession) {
                    if (cachedFriends == null && friendsFavoritesLoading) {
                        FriendsRowSkeleton()
                    } else {
                        cachedFriends?.takeIf { it.isNotEmpty() }?.let { friends ->
                            FriendsRow(
                                friends, c, onOpenFriend = onOpenFriend,
                                initialScroll = friendsRowScroll, onScrollChange = onSaveFriendsRowScroll,
                            )
                        }
                    }
                } else {
                    // No scrape session yet — tapping opens FriendsFavoritesScreen,
                    // which handles the embedded-login flow itself. A plain row
                    // instead of a boxed card — reads as one more line, not
                    // another tile stacked on the page.
                    Row(
                        Modifier.fillMaxWidth().padding(top = 24.dp).kikoClickable { onOpenFriendsFavorites() }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.People, null, tint = c.primary, modifier = Modifier.size(22.dp))
                        Text("Unlock more features", color = c.ink, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = c.muted, modifier = Modifier.size(20.dp))
                    }
                }
                HorizontalDivider(color = c.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(top = 12.dp))
            }

            // Tabbed anime/manga stats card
            // remember(items): same reasoning as
            // screens below (typeItems at
            // whole library on every
            val animeItems = remember(items) { items.filter { it.type == MediaType.Anime } }
            val mangaItems = remember(items) { items.filter { it.type == MediaType.Manga } }
            val mangaTotal = mangaItems.size
            val mangaChaptersRead = remember(mangaItems) { mangaItems.sumOf { it.progress } }
            val ratedManga = remember(mangaItems) { mangaItems.filter { it.myRating > 0 } }
            val mangaMeanScore = if (ratedManga.isNotEmpty()) ratedManga.map { it.myRating }.average() else 0.0
            val animeDaysWatched = profile?.animeDaysWatched ?: 0.0
            // MAL: 8 min/chapter — fallback estimate, only used until a cookie
            // session lets MalProfileScrapeApi pull the real manga stats
            val mangaDaysReadEst = mangaChaptersRead * 8.0 / 60.0 / 24.0
            // Manga stats aren't in MAL's official API at all — profile?.manga*
            // comes from MalProfileScrapeApi scraping the profile page (requires
            // a logged-in cookie session, see MalSessionCookie/MalLoginWebView).
            // Fall back to the local estimate/count from the synced list until
            // that session exists.
            val hasScrapedMangaStats = (profile?.mangaTotalEntries ?: 0) > 0
            val mangaDaysDisplay = if (hasScrapedMangaStats) profile!!.mangaDaysRead else mangaDaysReadEst
            val mangaMeanScoreDisplay = if (hasScrapedMangaStats) profile!!.mangaMeanScore else mangaMeanScore
            val mangaReadingCount = if (hasScrapedMangaStats) profile!!.mangaReading else mangaItems.count { it.status == WatchStatus.Reading }
            val mangaCompletedCount = if (hasScrapedMangaStats) profile!!.mangaCompleted else mangaItems.count { it.status == WatchStatus.Completed }
            val mangaOnHoldCount = if (hasScrapedMangaStats) profile!!.mangaOnHold else mangaItems.count { it.status == WatchStatus.OnHold }
            val mangaDroppedCount = if (hasScrapedMangaStats) profile!!.mangaDropped else mangaItems.count { it.status == WatchStatus.Dropped }
            val mangaPlanCount = if (hasScrapedMangaStats) profile!!.mangaPlanToRead else mangaItems.count { it.status == WatchStatus.Plan }
            val mangaTotalDisplay = if (hasScrapedMangaStats) profile!!.mangaTotalEntries else mangaTotal
            val mangaRereadDisplay = if (hasScrapedMangaStats) profile!!.mangaReread else mangaItems.sumOf { it.timesRewatched }
            val mangaChaptersDisplay = if (hasScrapedMangaStats) profile!!.mangaChaptersRead else mangaChaptersRead
            if (connected && ((profile?.animeTotalEntries ?: 0) > 0 || mangaItems.isNotEmpty())) {
                if (animeDaysWatched > 0 || mangaDaysDisplay > 0) {
                    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
                        Text("OVERVIEW", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HeroStat(Modifier.weight(1f), Icons.Default.PlayCircle, "Days watched", animeDaysWatched.oneDecimal(), c.lavender, c.primary)
                            HeroStat(Modifier.weight(1f), Icons.Default.MenuBook, if (hasScrapedMangaStats) "Days read" else "Days read (est.)", mangaDaysDisplay.oneDecimal(), c.primaryContainer, c.onPrimaryContainer)
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(top = 36.dp)) {
                    Text("Stats", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.padding(bottom = 16.dp))
                    StatsTypeButtonGroup(statsTab) { onStatsTabChange(it) }
                    Spacer(Modifier.height(20.dp))
                    // Basic cross-fade between the
                    // tab-switch transition used elsewhere
                    AnimatedContent(
                        statsTab,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "profile-stats-tab",
                    ) { tab ->
                        Column {
                            if (tab == MediaType.Anime) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    LabeledStat("Days:", animeDaysWatched.oneDecimal(), c)
                                    LabeledStat("Mean Score:", (profile?.animeMeanScore ?: 0.0).let { if (it > 0) it.twoDecimals() else "—" }, c)
                                }
                                Spacer(Modifier.height(12.dp))
                                SegmentedStatBar(listOf(
                                    (profile?.animeWatching ?: 0) to statusColor("Watching"),
                                    (profile?.animeCompleted ?: 0) to statusColor("Completed"),
                                    (profile?.animeOnHold ?: 0) to statusColor("On hold"),
                                    (profile?.animeDropped ?: 0) to statusColor("Dropped"),
                                    (profile?.animePlanToWatch ?: 0) to statusColor("Plan to watch"),
                                ), c)
                                Spacer(Modifier.height(20.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        StatusLegendRow("Watching", profile?.animeWatching ?: 0, statusColor("Watching"), c, onClick = if (isOwnProfile) { { openOwnStatus(MediaType.Anime, WatchStatus.Watching) } } else null)
                                        StatusLegendRow("Completed", profile?.animeCompleted ?: 0, statusColor("Completed"), c, onClick = if (isOwnProfile) { { openOwnStatus(MediaType.Anime, WatchStatus.Completed) } } else null)
                                        StatusLegendRow("On-Hold", profile?.animeOnHold ?: 0, statusColor("On hold"), c, onClick = if (isOwnProfile) { { openOwnStatus(MediaType.Anime, WatchStatus.OnHold) } } else null)
                                        StatusLegendRow("Dropped", profile?.animeDropped ?: 0, statusColor("Dropped"), c, onClick = if (isOwnProfile) { { openOwnStatus(MediaType.Anime, WatchStatus.Dropped) } } else null)
                                        StatusLegendRow("Plan to Watch", profile?.animePlanToWatch ?: 0, statusColor("Plan to watch"), c, onClick = if (isOwnProfile) { { openOwnStatus(MediaType.Anime, WatchStatus.Plan) } } else null)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        SummaryRow("Total Entries", formatExact(profile?.animeTotalEntries ?: 0), c)
                                        SummaryRow("Rewatched", formatExact(animeItems.sumOf { it.timesRewatched }), c)
                                        SummaryRow("Episodes", formatExact(profile?.animeEpisodesWatched ?: 0), c)
                                        // Bottom-right of the card — lines up under
                                        // "Episodes", to the right of "Plan to Watch".
                                        if (!isOwnProfile) SeeFriendListButton(c, onClick = { onOpenFriendList(MediaType.Anime) })
                                    }
                                }
                                if (animeItems.isNotEmpty()) {
                                    Spacer(Modifier.height(24.dp))
                                    Text("GENRE BREAKDOWN", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    GenreBreakdownChart(animeItems, c, onGenreClick = { onGenreClick(MediaType.Anime, it) })
                                    Spacer(Modifier.height(24.dp))
                                    Text("SCORE DISTRIBUTION", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    ScoreDistributionChart(animeItems, c, onScoreClick = { onScoreClick(MediaType.Anime, it) })
                                    Spacer(Modifier.height(24.dp))
                                    Text("FORMAT BREAKDOWN", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    FormatBreakdownChart(animeItems, c, onFormatClick = { onFormatClick(MediaType.Anime, it) })
                                    Spacer(Modifier.height(24.dp))
                                    Text("YEAR DISTRIBUTION", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    YearDistributionChart(animeItems, c, onYearClick = { onYearClick(MediaType.Anime, it) })
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    LabeledStat("Days:", mangaDaysDisplay.oneDecimal() + if (hasScrapedMangaStats) "" else " (est.)", c)
                                    LabeledStat("Mean Score:", if (mangaMeanScoreDisplay > 0) mangaMeanScoreDisplay.twoDecimals() else "—", c)
                                }
                                Spacer(Modifier.height(12.dp))
                                SegmentedStatBar(listOf(
                                    mangaReadingCount to statusColor("Reading"),
                                    mangaCompletedCount to statusColor("Completed"),
                                    mangaOnHoldCount to statusColor("On hold"),
                                    mangaDroppedCount to statusColor("Dropped"),
                                    mangaPlanCount to statusColor("Plan to read"),
                                ), c)
                                Spacer(Modifier.height(20.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        StatusLegendRow("Reading", mangaReadingCount, statusColor("Reading"), c, onClick = if (isOwnProfile) { { openOwnStatus(MediaType.Manga, WatchStatus.Reading) } } else null)
                                        StatusLegendRow("Completed", mangaCompletedCount, statusColor("Completed"), c, onClick = if (isOwnProfile) { { openOwnStatus(MediaType.Manga, WatchStatus.Completed) } } else null)
                                        StatusLegendRow("On-Hold", mangaOnHoldCount, statusColor("On hold"), c, onClick = if (isOwnProfile) { { openOwnStatus(MediaType.Manga, WatchStatus.OnHold) } } else null)
                                        StatusLegendRow("Dropped", mangaDroppedCount, statusColor("Dropped"), c, onClick = if (isOwnProfile) { { openOwnStatus(MediaType.Manga, WatchStatus.Dropped) } } else null)
                                        StatusLegendRow("Plan to Read", mangaPlanCount, statusColor("Plan to read"), c, onClick = if (isOwnProfile) { { openOwnStatus(MediaType.Manga, WatchStatus.Plan) } } else null)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        SummaryRow("Total Entries", formatExact(mangaTotalDisplay), c)
                                        SummaryRow("Reread", formatExact(mangaRereadDisplay), c)
                                        SummaryRow("Chapters", formatExact(mangaChaptersDisplay), c)
                                        // Bottom-right of the card — lines up under
                                        // "Chapters", to the right of "Plan to Read".
                                        if (!isOwnProfile) SeeFriendListButton(c, onClick = { onOpenFriendList(MediaType.Manga) })
                                    }
                                }
                                if (mangaItems.isNotEmpty()) {
                                    Spacer(Modifier.height(24.dp))
                                    Text("GENRE BREAKDOWN", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    GenreBreakdownChart(mangaItems, c, onGenreClick = { onGenreClick(MediaType.Manga, it) })
                                    Spacer(Modifier.height(24.dp))
                                    Text("SCORE DISTRIBUTION", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    ScoreDistributionChart(mangaItems, c, onScoreClick = { onScoreClick(MediaType.Manga, it) })
                                    Spacer(Modifier.height(24.dp))
                                    Text("FORMAT BREAKDOWN", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    FormatBreakdownChart(mangaItems, c, onFormatClick = { onFormatClick(MediaType.Manga, it) })
                                    Spacer(Modifier.height(24.dp))
                                    Text("YEAR DISTRIBUTION", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    YearDistributionChart(mangaItems, c, onYearClick = { onYearClick(MediaType.Manga, it) })
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = c.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(top = 32.dp))
            }

            // Favorites, split by anime/manga/etc., right below the year
            // distribution charts above.
            if (connected && hasFfSession) {
                if (cachedFavorites == null && friendsFavoritesLoading) {
                    FavoritesRowsSectionSkeleton()
                } else {
                    cachedFavorites?.let {
                        FavoritesRowsSection(
                            it, c,
                            onOpenCharacter = onOpenCharacter, onOpenPerson = onOpenPerson, onOpenCompany = onOpenCompany,
                            onOpenFavoriteTitle = onOpenFavoriteTitle, loadingId = favoriteLoadingId,
                            getRowScroll = getFavoritesRowScroll, onSaveRowScroll = onSaveFavoritesRowScroll,
                        )
                    }
                }
            }

            // Only shown when signed-out — a big expressive banner rather than
            // a small card, since it's the one thing on the page at that point.
            if (!connected) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .clip(RoundedCornerShape(kikoCorner(32.dp)))
                        .background(c.lavender)
                        .padding(24.dp),
                ) {
                    Text("Connect MyAnimeList", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = c.ink)
                    Text("Sign in with your MyAnimeList account to bring in your real list.", color = c.muted, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
                    Button(onClick = onConnect, shape = RoundedCornerShape(kikoCorner(16.dp)), colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)) { Text("Sign in with MyAnimeList") }
                }
            }
        }
    }
}

// Bottom-right action for a friend's Stats card — replaces individually
// tapping each StatusLegendRow (Watching/Completed/…) with one button per
// tab that opens that type's full list (all statuses) in FriendListScreen.
@Composable private fun SeeFriendListButton(c: KikoColors, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
        Row(
            Modifier
                .clip(RoundedCornerShape(kikoCorner(12.dp)))
                .background(c.surfaceContainerHigh)
                .kikoClickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("See List", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Icon(Icons.Default.ChevronRight, null, tint = c.primary, modifier = Modifier.size(16.dp))
        }
    }
}

// Pulls the numeric MAL id out of an anime/manga URL — favorites entries
// don't carry a parsed id, just the scraped title/url/cover (see
// MalFavoriteEntry), so this (and parseMalProfileLink for
// character/person/company) is how taps get routed in-app.
fun malIdFromFavoriteUrl(url: String): Int? = runCatching { Uri.parse(url).pathSegments.getOrNull(1)?.toIntOrNull() }.getOrNull()

// MAL's free-form "About Me" widget, right under the avatar/name card —
// same slot FriendsRow/FavoritesRowsSection use below it. Unlike those,
// this card keeps the user's own about-me theme (the three --about-me-
// color-* values they picked in MAL's editor, parsed off by
// MalProfileScrapeApi.aboutMe()) as its background/text colors instead of
// Kiko's usual surfaceContainer/ink — that's the "don't change the about
// me design" part. What *does* follow Kiko's own design language: the card
// shape/radius (kikoCorner(28.dp), matching every other card on this
// page), the uppercase muted section labels, and collapsing MAL's desktop
// 2-column row layout down to single-column stacked LazyRows — the same
// single-column shape MAL's own mobile webview uses for this widget,
// rather than reproducing the desktop grid. Each entry's title is left
// off the poster — it's reconstructed from a URL slug rather than
// scraped page text (see MalAboutMeItem), so it's often not the title's
// actual name; the poster art alone reads better than a wrong label.
// Lets a child grow past its parent's horizontal padding: measured `amount` wider on each side and
// shifted left by `amount`, while still reporting the parent's own width so siblings don't move.
// Used to run About Me edge-to-edge inside ProfileStatsSection's 20dp-padded column.
private fun Modifier.bleedHorizontal(amount: androidx.compose.ui.unit.Dp): Modifier = if (amount <= 0.dp) this else this.layout { measurable, constraints ->
    val extra = (amount * 2).roundToPx()
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-amount.roundToPx(), 0) }
}

// bleed > 0 runs the card edge-to-edge (no screen-edge margin, corners stay rounded); `bleed` is the total
// horizontal inset above it that needs cancelling out. Its own content stays inset by 20dp to line
// up with the rest of the page.
@Composable fun AboutMeCard(aboutMe: MalAboutMe, onOpenTitle: (Int, MediaType) -> Unit = { _, _ -> }, modifier: Modifier = Modifier, bleed: androidx.compose.ui.unit.Dp = 0.dp) {
    if (aboutMe.isEmpty) return
    val c = LocalKikoColors.current
    // Falls back to Kiko's own card colors if a user's about-me somehow has
    // no theme color set (e.g. content but no <style> block) — never a
    // fully unstyled/invisible card.
    val bg = aboutMe.backgroundColor?.let(::parseHexColor) ?: c.surfaceContainer
    val body = aboutMe.bodyTextColor?.let(::parseHexColor) ?: c.ink
    val header = aboutMe.headerTextColor?.let(::parseHexColor) ?: c.primary
    val muted = body.copy(alpha = 0.62f)
    val hasIntro = !aboutMe.displayName.isNullOrBlank() || !aboutMe.introText.isNullOrBlank()
    val fullBleed = bleed > 0.dp
    // Rounded either way: full-bleed only removes the side margins, the corners stay.
    val cardShape = RoundedCornerShape(kikoCorner(32.dp))
    val bannerShape = RoundedCornerShape(topStart = kikoCorner(32.dp), topEnd = kikoCorner(32.dp))

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .bleedHorizontal(bleed)
            .clip(cardShape)
            .background(bg),
    ) {
        // Banner spans the module edge-to-edge, same treatment MAL's
        // mobile webview gives it (full-bleed, above any padding),
        // rather than inset like the poster rows below.
        aboutMe.mainVisualUrl?.let { url ->
            // Sized to the banner's own aspect ratio once it's loaded
            // (MAL lets users upload any size/shape image here) rather
            // than forcing every banner into the same fixed crop.
            // Starts at the same 21:9 fallback AboutMeCardSkeleton uses
            // so there's no layout jump before the real size is known;
            // Crop still applies during that brief window, but has
            // nothing to crop once bannerAspect matches the image.
            var bannerAspect by remember(url) { mutableFloatStateOf(21f / 9f) }
            AsyncImage(
                model = url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onSuccess = { state ->
                    val d = state.result.drawable
                    if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) bannerAspect = d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(bannerAspect)
                    .clip(bannerShape)
                    .background(bg),
            )
        }
        Column(Modifier.padding(horizontal = if (fullBleed) 20.dp else 16.dp, vertical = 16.dp)) {
            Text("ABOUT ME", color = muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            if (hasIntro) {
                aboutMe.displayName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleLarge, color = header, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                }
                aboutMe.introText?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            aboutMe.sections.forEachIndexed { index, section ->
                Spacer(Modifier.height(if (index == 0 && !hasIntro) 4.dp else 20.dp))
                Text(section.heading.uppercase(), color = muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    items(section.items, key = { it.url }) { entry: MalAboutMeItem ->
                        Column(
                            Modifier.width(96.dp).kikoClickable {
                                val id = malIdFromFavoriteUrl(entry.url)
                                if (id != null && entry.type != null) onOpenTitle(id, entry.type)
                            },
                        ) {
                            AsyncImage(
                                model = entry.imageUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(kikoCorner(12.dp))).background(body.copy(alpha = 0.08f)),
                            )
                        }
                    }
                }
            }
        }
    }
}
// Loading placeholder for AboutMeCard — same module shape/padding, a mock
// banner + a couple of text-line blocks for the intro, and one row of
// 96.dp poster-shaped blocks (matching AboutMeCard's own entry width),
// shown while loadProfileFriendsFavorites is still fetching, same
// friendsFavoritesLoading gate FriendsRowSkeleton/FavoritesRowsSectionSkeleton use.
@Composable fun AboutMeCardSkeleton(bleed: androidx.compose.ui.unit.Dp = 0.dp) {
    val c = LocalKikoColors.current
    val fullBleed = bleed > 0.dp
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .bleedHorizontal(bleed)
            .clip(RoundedCornerShape(kikoCorner(32.dp)))
            .background(c.surfaceContainer),
    ) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(21f / 9f), shape = RoundedCornerShape(topStart = kikoCorner(32.dp), topEnd = kikoCorner(32.dp)))
        Column(Modifier.padding(horizontal = if (fullBleed) 20.dp else 24.dp, vertical = 24.dp)) {
            Text("ABOUT ME", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            SkeletonBlock(Modifier.padding(top = 12.dp).fillMaxWidth(0.5f).height(18.dp))
            SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth().height(13.dp))
            SkeletonBlock(Modifier.padding(top = 6.dp).fillMaxWidth(0.7f).height(13.dp))
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) {
                    SkeletonBlock(Modifier.width(96.dp).aspectRatio(2f / 3f), shape = RoundedCornerShape(kikoCorner(12.dp)))
                }
            }
        }
    }
}

// Scrollable row of friends, right under the avatar/name card on Profile,
// in its own section container to match Favorites below. Each friend shows
// their cover (avatar) with their name underneath; tapping one opens an
// in-app FriendProfileScreen — a 1:1 mirror of this same Profile page, just
// scraped for that friend's username (see MalProfileScrapeApi.fullProfile
// and Navigation's friendProfileOpen). The row's own scroll position is hoisted
// out via initialScroll/onScrollChange — Profile itself gets torn down
// while a favorite's detail page is on top, so without this the row would
// reset to the start every time the user comes back.
@Composable fun FriendsRow(
    friends: List<MalFriend>, c: KikoColors, onOpenFriend: (MalFriend) -> Unit = {},
    initialScroll: Pair<Int, Int> = 0 to 0, onScrollChange: (Int, Int) -> Unit = { _, _ -> },
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScroll.first, initialFirstVisibleItemScrollOffset = initialScroll.second)
    DisposableEffect(Unit) { onDispose { onScrollChange(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) } }
    Column(Modifier.fillMaxWidth().padding(top = 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Friends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            items(friends, key = { it.profileUrl }) { friend ->
                Column(Modifier.width(70.dp).kikoClickable { onOpenFriend(friend) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!friend.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = friend.avatarUrl, contentDescription = friend.username, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.surfaceContainerHigh),
                        )
                    } else {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Text(friend.username.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.ink)
                        }
                    }
                    Text(
                        friend.username, color = c.ink, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
// Loading placeholder for FriendsRow — same 64.dp avatar/70.dp column
// sizing, shown in its place while loadProfileFriendsFavorites is still
// fetching (see friendsFavoritesLoading in ProfileStatsSection) so the
// section doesn't just sit blank until the scrape finishes.
@Composable fun FriendsRowSkeleton() {
    val c = LocalKikoColors.current
    Column(Modifier.fillMaxWidth().padding(top = 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Friends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(5) {
                Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SkeletonBlock(Modifier.size(64.dp), shape = RoundedCornerShape(kikoCorner(18.dp)))
                    SkeletonBlock(Modifier.padding(top = 6.dp).fillMaxWidth(0.7f).height(11.dp))
                }
            }
        }
    }
}
// Favorites, split into rows per category (anime/manga/characters/people/
// companies) — each its own horizontally-scrollable row of covers. Anime/
// manga open their in-app detail page (fetched by id via
// onOpenFavoriteTitle); characters/people/companies open via
// parseMalProfileLink same as everywhere else in Kiko that renders MAL
// links (forum posts, clubs, stacks). A plain section on the page background — no
// boxed card — with each category introduced by its own small label.
@Composable fun FavoritesRowsSection(
    favorites: MalFavorites, c: KikoColors,
    onOpenCharacter: (Int) -> Unit = {}, onOpenPerson: (Int) -> Unit = {}, onOpenCompany: (Int) -> Unit = {},
    onOpenFavoriteTitle: (Int, MediaType) -> Unit = { _, _ -> }, loadingId: Int? = null,
    getRowScroll: (String) -> Pair<Int, Int> = { 0 to 0 }, onSaveRowScroll: (String, Int, Int) -> Unit = { _, _, _ -> },
) {
    val sections = listOfNotNull(
        favorites.anime.takeIf { it.isNotEmpty() }?.let { "Anime" to it },
        favorites.manga.takeIf { it.isNotEmpty() }?.let { "Manga" to it },
        favorites.characters.takeIf { it.isNotEmpty() }?.let { "Characters" to it },
        favorites.people.takeIf { it.isNotEmpty() }?.let { "People" to it },
        favorites.companies.takeIf { it.isNotEmpty() }?.let { "Companies" to it },
    )
    if (sections.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Favorites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.weight(1f))
        }
        sections.forEachIndexed { index, (label, entries) ->
            Spacer(Modifier.height(if (index == 0) 18.dp else 26.dp))
            Text(label.uppercase(), color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 10.dp))
            key(label) {
                FavoritesCategoryRow(
                    label, entries, c,
                    initialScroll = getRowScroll(label), onScrollChange = { i, o -> onSaveRowScroll(label, i, o) },
                    onOpenCharacter = onOpenCharacter, onOpenPerson = onOpenPerson, onOpenCompany = onOpenCompany,
                    onOpenFavoriteTitle = onOpenFavoriteTitle, loadingId = loadingId,
                )
            }
        }
    }
}
// Loading placeholder for FavoritesRowsSection — same header, with two mock
// category rows of 96.dp poster-shaped blocks (the common case is Anime +
// Manga) shown in its place while loadProfileFriendsFavorites is still
// fetching, so the section doesn't just sit blank until it resolves.
@Composable fun FavoritesRowsSectionSkeleton() {
    val c = LocalKikoColors.current
    Column(Modifier.fillMaxWidth().padding(top = 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Favorites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.weight(1f))
        }
        repeat(2) { rowIndex ->
            Spacer(Modifier.height(if (rowIndex == 0) 18.dp else 26.dp))
            SkeletonBlock(Modifier.width(70.dp).height(11.dp), shape = RoundedCornerShape(kikoCorner(4.dp)))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) {
                    Column(Modifier.width(96.dp)) {
                        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(2f / 3f), shape = RoundedCornerShape(kikoCorner(12.dp)))
                        SkeletonBlock(Modifier.padding(top = 4.dp).fillMaxWidth(0.8f).height(12.dp))
                    }
                }
            }
        }
    }
}
// One category's row inside FavoritesRowsSection — own LazyListState so
// each category's scroll position is tracked (and restored) independently.
@Composable private fun FavoritesCategoryRow(
    label: String, entries: List<MalFavoriteEntry>, c: KikoColors,
    initialScroll: Pair<Int, Int>, onScrollChange: (Int, Int) -> Unit,
    onOpenCharacter: (Int) -> Unit, onOpenPerson: (Int) -> Unit, onOpenCompany: (Int) -> Unit,
    onOpenFavoriteTitle: (Int, MediaType) -> Unit, loadingId: Int?,
) {
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScroll.first, initialFirstVisibleItemScrollOffset = initialScroll.second)
    DisposableEffect(Unit) { onDispose { onScrollChange(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) } }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(entries, key = { it.url }) { entry: MalFavoriteEntry ->
            // Anime/manga wait on a fetch before navigating (see
            // onOpenFavoriteTitle/openMalTitleDetail), so show a spinner on
            // the tapped cover while it loads — character/person/company
            // navigate immediately to a page that shows its own skeleton.
            val titleMalId = remember(entry.url, label) { if (label == "Anime" || label == "Manga") malIdFromFavoriteUrl(entry.url) else null }
            val isLoading = titleMalId != null && titleMalId == loadingId
            Column(
                Modifier.width(96.dp).kikoClickable(enabled = !isLoading) {
                    when (label) {
                        "Anime" -> titleMalId?.let { onOpenFavoriteTitle(it, MediaType.Anime) } ?: uriHandler.openUri(entry.url)
                        "Manga" -> titleMalId?.let { onOpenFavoriteTitle(it, MediaType.Manga) } ?: uriHandler.openUri(entry.url)
                        else -> when (val link = parseMalProfileLink(entry.url)) {
                            is MalProfileLink.Character -> onOpenCharacter(link.malId)
                            is MalProfileLink.Person -> onOpenPerson(link.malId)
                            is MalProfileLink.Company -> onOpenCompany(link.malId)
                            null -> uriHandler.openUri(entry.url)
                        }
                    }
                },
            ) {
                Box {
                    // Companies are logos (often landscape/square, never a
                    // poster), same reasoning as CompanySearchResultRow's
                    // square logo treatment — cropping them to a 2:3 poster
                    // shape like anime/manga/characters/people below would
                    // chop the logo up.
                    if (label == "Companies") {
                        AsyncImage(
                            model = entry.imageUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.surfaceContainerHigh),
                        )
                    } else {
                        AsyncImage(
                            model = entry.imageUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.surfaceContainerHigh),
                        )
                    }
                    if (isLoading) {
                        Box(Modifier.matchParentSize().clip(RoundedCornerShape(kikoCorner(12.dp))).background(Color.Black.copy(alpha = .35f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                // minLines = 2 always reserves two title lines, so the format · year line below sits at the
                // same height on every cover in the row instead of riding up under short titles.
                Text(entry.title, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))

                favoriteSubtitle(label, entry.subtitle)?.let { Text(it, color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp)) }
            }
        }
    }
}

// MAL hands anime/manga favorites back as a cramped "TV·2011" (format, middle dot, year with no
// breathing room). Re-split it and re-join as "TV · 2011" like the rest of the app ("format · year");
// a blank side (no format, or no year yet) just drops the dot. Characters carry the work title in
// this slot, so those (and anything that doesn't look like format/year) pass through untouched.
private fun favoriteSubtitle(label: String, raw: String?): String? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (label != "Anime" && label != "Manga") return text
    val parts = text.split('·', '\u2022', '|').map { it.trim() }.filter { it.isNotEmpty() }
    return if (parts.size in 2..3 && parts.last().matches(Regex("""\d{4}|\?|TBA"""))) parts.joinToString(" · ") else text
}

// Settings list, grouped into titled sections with the same big-outer / tight-inner
// corner cards as the profile drawer (the Play Store settings look). Theme/colour/palette/
// title-language rows only report taps (their dialogs live in Navigation); the new layout,
// sort, cache and sign-out rows own their small dialogs here.
private class SettingsItem(
    val title: String,
    val subtitle: String? = null,
    val titleColor: Color? = null,
    val subtitleColor: Color? = null,
    val trailing: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit,
)

private fun settingsGroupShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val outer = 28.dp
    val inner = 4.dp
    if (count == 1) return RoundedCornerShape(outer)
    return when (index) {
        0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

@Composable private fun SettingsGroup(title: String, items: List<SettingsItem>) {
    val c = LocalKikoColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(title, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { i, item ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(settingsGroupShape(i, items.size)).background(c.surfaceContainerHigh)
                        .kikoClickable(scale = 0.98f, onClick = item.onClick).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontSize = 17.sp, color = item.titleColor ?: c.ink)
                        item.subtitle?.let { Text(it, fontSize = 13.sp, color = item.subtitleColor ?: c.muted, modifier = Modifier.padding(top = 2.dp)) }
                    }
                    item.trailing?.let { trailing -> Box(Modifier.padding(start = 12.dp)) { trailing() } }
                }
            }
        }
    }
}

private fun formatCacheBytes(b: Long): String = when {
    b >= (1L shl 20) -> "${(b * 10 / (1L shl 20)) / 10.0} MB"
    b >= (1L shl 10) -> "${b shr 10} KB"
    else -> "$b B"
}

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable fun SettingsSection(
    connected: Boolean, themeMode: ThemeMode, colorSource: ColorSource, paletteStyle: PaletteStyle, titleLanguage: TitleLanguage,
    nsfwEnabled: Boolean, onNsfwChange: (Boolean) -> Unit,
    amoledDark: Boolean = false, onAmoledDarkChange: (Boolean) -> Unit = {},
    onThemeClick: () -> Unit, onColorClick: () -> Unit, onPaletteClick: () -> Unit, onTitleLanguageClick: () -> Unit,
    updateInfo: AppUpdateInfo? = null, onAboutClick: () -> Unit = {},
    listViewMode: ListViewMode = ListViewMode.List, onListViewModeChange: (ListViewMode) -> Unit = {},
    listSort: com.kiko.tracker.data.model.ListSort = com.kiko.tracker.data.model.ListSort.Title, onListSortChange: (com.kiko.tracker.data.model.ListSort) -> Unit = {},
    onSignOut: (() -> Unit)? = null,
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listViewDialog by remember { mutableStateOf(false) }
    var listSortDialog by remember { mutableStateOf(false) }
    var clearCacheDialog by remember { mutableStateOf(false) }
    var signOutDialog by remember { mutableStateOf(false) }
    // Coil's on-disk image cache (covers etc.). Re-measured after a clear.
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var cacheTick by remember { mutableStateOf(0) }
    LaunchedEffect(cacheTick) {
        cacheBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { coil.Coil.imageLoader(context).diskCache?.size }.getOrNull() }
    }
    val switchColors = SwitchDefaults.colors(checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary)

    Column {
        SettingsGroup("Appearance", listOf(
            SettingsItem("Theme", themeMode.label, onClick = onThemeClick),
            SettingsItem("Color", colorSource.label, onClick = onColorClick),
            SettingsItem("Color palette", paletteStyle.label, onClick = onPaletteClick),
            SettingsItem(
                "AMOLED black", "True black backgrounds in dark mode, saves battery on AMOLED screens",
                trailing = { Switch(checked = amoledDark, onCheckedChange = null, colors = switchColors) },
                onClick = { onAmoledDarkChange(!amoledDark) },
            ),
        ))
        SettingsGroup("Library", listOf(
            SettingsItem("Title language", titleLanguage.label, onClick = onTitleLanguageClick),
            SettingsItem("List layout", listViewMode.name, onClick = { listViewDialog = true }),
            SettingsItem("List sort order", listSort.label, onClick = { listSortDialog = true }),
        ))
        SettingsGroup("Content", listOf(
            SettingsItem(
                "Adult content", if (nsfwEnabled) "Hentai-rated titles are shown" else "Hentai-rated titles are hidden",
                trailing = { Switch(checked = nsfwEnabled, onCheckedChange = null, colors = switchColors) },
                onClick = { onNsfwChange(!nsfwEnabled) },
            ),
        ))
        SettingsGroup("Storage", listOf(
            SettingsItem(
                "Clear image cache",
                cacheBytes?.let { "Covers and images saved on this device · ${formatCacheBytes(it)}" } ?: "Covers and images saved on this device",
                onClick = { clearCacheDialog = true },
            ),
        ))
        if (connected && onSignOut != null) {
            SettingsGroup("Account", listOf(
                SettingsItem("Sign out", "Disconnect your MyAnimeList account", titleColor = c.danger, onClick = { signOutDialog = true }),
            ))
        }
        SettingsGroup("About", listOf(
            SettingsItem(
                "About Kiko",
                if (updateInfo != null) "Update available — ${updateInfo.version}" else "v${BuildConfig.VERSION_NAME}",
                subtitleColor = if (updateInfo != null) c.primary else null,
                trailing = { if (updateInfo != null) Box(Modifier.size(8.dp).clip(kikoCircleShape()).background(c.danger)) },
                onClick = onAboutClick,
            ),
        ))
    }

    if (listViewDialog) ChoiceDialog(
        title = "List layout", options = ListViewMode.entries, selected = listViewMode, label = { it.name },
        onSelect = { onListViewModeChange(it); listViewDialog = false }, onDismiss = { listViewDialog = false },
    )
    if (listSortDialog) ChoiceDialog(
        title = "List sort order", options = com.kiko.tracker.data.model.ListSort.entries, selected = listSort, label = { it.label },
        onSelect = { onListSortChange(it); listSortDialog = false }, onDismiss = { listSortDialog = false },
    )
    if (clearCacheDialog) AlertDialog(
        onDismissRequest = { clearCacheDialog = false },
        containerColor = c.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Clear image cache?", color = c.ink) },
        text = { Text("Covers and images will be downloaded again the next time they're needed.", color = c.muted) },
        confirmButton = {
            TextButton(onClick = {
                clearCacheDialog = false
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { coil.Coil.imageLoader(context).diskCache?.clear() }
                        runCatching { coil.Coil.imageLoader(context).memoryCache?.clear() }
                    }
                    cacheTick++
                }
            }, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("Clear") }
        },
        dismissButton = { TextButton(onClick = { clearCacheDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("Cancel") } },
    )
    if (signOutDialog) AlertDialog(
        onDismissRequest = { signOutDialog = false },
        containerColor = c.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Sign out?", color = c.ink) },
        text = { Text("Your list stays on MyAnimeList. You can sign back in anytime.", color = c.muted) },
        confirmButton = { TextButton(onClick = { signOutDialog = false; onSignOut?.invoke() }, colors = ButtonDefaults.textButtonColors(contentColor = c.danger)) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { signOutDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("Cancel") } },
    )
}

// Opened by tapping a
// Starts on the tapped
@Composable fun ScoreFilterScreen(vm: LibraryViewModel, type: MediaType, initialScore: Int, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    var score by remember { mutableStateOf(initialScore) }
    val typeItems = remember(vm.items, type) { vm.items.filter { it.type == type } }
    // Tab/page order: All, then 10 down to 1. Swipe left/right to move between scores.
    val scoreOptions = remember { listOf(0) + (10 downTo 1).toList() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 13.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
            Text("Score Distribution", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.headerTitleStart())
        }
        KikoTabPager(
            items = scoreOptions,
            selected = score,
            onSelect = { score = it },
            tab = { s, selected ->
                if (s == 0) KikoTabText("All", selected)
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
                    Text(s.toString(), fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(start = 3.dp))
                }
            },
        ) { pageScore -> ScoreFilterPage(vm, typeItems, pageScore, onOpenDetail) }
    }
}
// One swipeable page of ScoreFilterScreen — the titles count/toggle/sort row plus the list or grid for one score.
@Composable private fun ScoreFilterPage(vm: LibraryViewModel, typeItems: List<MediaItem>, score: Int, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val filtered = remember(typeItems, score, vm.scoreFilterSort, vm.titleLanguage) {
        typeItems.filter { it.myRating > 0 && (score == 0 || it.myRating == score) }.sortedWithListSort(vm.scoreFilterSort, vm.titleLanguage)
    }
    val staggerSeen = rememberStaggerMemory()
    val isGrid = vm.scoreFilterViewMode == ListViewMode.Grid
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} title${if (filtered.size == 1) "" else "s"}", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SortMenu(vm.scoreFilterSort) { vm.setScoreFilterSort(context, it) }
                ListViewModeToggle(vm.scoreFilterViewMode) { vm.setScoreFilterViewMode(context, it) }
            }
        }
    }
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, item -> StaggeredItem(index, staggerSeen) { ListGridCard(item, onOpenDetail) } }
            if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles at this score yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp)) {
            item { header() }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
                StaggeredItem(index, staggerSeen) {
                    Column {
                        ListRow(it, onOpenDetail, showType = false)
                    }
                }
            }
            if (filtered.isEmpty()) item { Text("No titles at this score yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    }
}
// Opened by tapping a
// Starts on the tapped
// appears in the list,
// ScoreFilterScreen above — same
// year instead of score
// isn't limited to rated
@Composable fun YearFilterScreen(vm: LibraryViewModel, type: MediaType, initialYear: Int, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var year by remember { mutableStateOf(initialYear) }
    val typeItems = remember(vm.items, type) { vm.items.filter { it.type == type } }
    // Same tolerant startDate parsing
    // here line up with
    fun releaseYear(item: MediaItem) = item.startDate.take(4).toIntOrNull()?.takeIf { it in 1900..2100 }
    val years = remember(typeItems) { typeItems.mapNotNull(::releaseYear).distinct().sortedDescending() }
    val filtered = remember(typeItems, year, vm.yearFilterSort, vm.titleLanguage) {
        typeItems.filter { val y = releaseYear(it); y != null && (year == 0 || y == year) }.sortedWithListSort(vm.yearFilterSort, vm.titleLanguage)
    }
    val staggerSeen = rememberStaggerMemory()
    val isGrid = vm.yearFilterViewMode == ListViewMode.Grid
    // Year picker moved off
    // them scrolled out of
    // GenreFilterScreen below. See YearFilterFab/YearFilterSheet.
    var yearSheetOpen by remember { mutableStateOf(false) }
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
            Text("Year Distribution", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.headerTitleStart())
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} title${if (filtered.size == 1) "" else "s"}", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SortMenu(vm.yearFilterSort) { vm.setYearFilterSort(context, it) }
                ListViewModeToggle(vm.yearFilterViewMode) { vm.setYearFilterViewMode(context, it) }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (isGrid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
                itemsIndexed(filtered, key = { _, it -> it.id }) { index, item -> StaggeredItem(index, staggerSeen) { ListGridCard(item, onOpenDetail) } }
                if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles from this year yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 96.dp)) {
                item { header() }
                itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
                    StaggeredItem(index, staggerSeen) {
                        Column {
                            ListRow(it, onOpenDetail, showType = false)
                        }
                    }
                }
                if (filtered.isEmpty()) item { Text("No titles from this year yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
            }
        }
        YearFilterFab(year, onClick = { yearSheetOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp))
    }
    if (yearSheetOpen) YearFilterSheet(years, year, onDismiss = { yearSheetOpen = false }) { year = it; yearSheetOpen = false }
}
// Year picker FAB —
// "spell it out, don't
// Tapping it opens YearFilterSheet
// count here is unbounded

@Composable fun YearFilterFab(current: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = c.primary,
        contentColor = c.onPrimary,
        icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
        text = { Text(if (current == 0) "All Years" else current.toString()) },
        modifier = modifier,
    )
}
// Year picker sheet —
// first, wrapped into a
// every year is reachable
// AdvancedFilterSheet genre/theme sections (see

@Composable fun YearFilterSheet(years: List<Int>, current: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val c = LocalKikoColors.current
    val colors = kikoFilterChipColors()
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Filter by", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Year", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = current == 0, onClick = { onSelect(0) }, label = { Text("All") }, colors = colors)
                years.forEach { y -> FilterChip(selected = current == y, onClick = { onSelect(y) }, label = { Text(y.toString()) }, colors = colors) }
            }
        }
    }
}
// Opened by tapping a
// Starts on the tapped
// present in the list,
// YearFilterScreen above — same
// string instead of score
@Composable fun FormatFilterScreen(vm: LibraryViewModel, type: MediaType, initialFormat: String, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onBack)
    var format by remember { mutableStateOf(initialFormat) }
    val typeItems = remember(vm.items, type) { vm.items.filter { it.type == type } }
    val formats = remember(typeItems) { typeItems.map { it.format }.filter { it.isNotBlank() }.distinct().sorted() }
    // Tab/page order: All (blank), then each format alphabetically.
    val formatOptions = remember(formats) { listOf("") + formats }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 13.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
            Text("Format Breakdown", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.headerTitleStart())
        }
        KikoTabPager(
            items = formatOptions,
            selected = format,
            onSelect = { format = it },
            tab = { f, selected -> KikoTabText(f.ifBlank { "All" }, selected) },
        ) { pageFormat -> FormatFilterPage(vm, typeItems, pageFormat, onOpenDetail) }
    }
}
// One swipeable page of FormatFilterScreen — the titles count/toggle/sort row plus the list or grid for one format.
@Composable private fun FormatFilterPage(vm: LibraryViewModel, typeItems: List<MediaItem>, format: String, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val filtered = remember(typeItems, format, vm.formatFilterSort, vm.titleLanguage) {
        typeItems.filter { it.format.isNotBlank() && (format.isBlank() || it.format == format) }.sortedWithListSort(vm.formatFilterSort, vm.titleLanguage)
    }
    val staggerSeen = rememberStaggerMemory()
    val isGrid = vm.formatFilterViewMode == ListViewMode.Grid
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} title${if (filtered.size == 1) "" else "s"}", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SortMenu(vm.formatFilterSort) { vm.setFormatFilterSort(context, it) }
                ListViewModeToggle(vm.formatFilterViewMode) { vm.setFormatFilterViewMode(context, it) }
            }
        }
    }
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, item -> StaggeredItem(index, staggerSeen) { ListGridCard(item, onOpenDetail) } }
            if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles of this format yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp)) {
            item { header() }
            itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
                StaggeredItem(index, staggerSeen) {
                    Column {
                        ListRow(it, onOpenDetail, showType = false)
                    }
                }
            }
            if (filtered.isEmpty()) item { Text("No titles of this format yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
        }
    }
}
// Opened by tapping a
// Starts on the tapped
// present in the list,
// above — same header/list/grid/sort
@Composable fun GenreFilterScreen(vm: LibraryViewModel, type: MediaType, initialGenre: String, onBack: () -> Unit, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var genre by remember { mutableStateOf(initialGenre) }
    val typeItems = remember(vm.items, type) { vm.items.filter { it.type == type } }
    // Ranked most → least,
    // without the chart's top-6
    val genreCounts = remember(typeItems) { typeItems.flatMap { it.genres }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value } }
    val filtered = remember(typeItems, genre, vm.genreFilterSort, vm.titleLanguage) {
        typeItems.filter { genre.isBlank() || it.genres.any { g -> g == genre } }.sortedWithListSort(vm.genreFilterSort, vm.titleLanguage)
    }
    val staggerSeen = rememberStaggerMemory()
    val isGrid = vm.genreFilterViewMode == ListViewMode.Grid
    // Genre picker moved off
    // past 30-40 distinct tags,
    // see GenreFilterFab/GenreFilterSheet below.
    var genreSheetOpen by remember { mutableStateOf(false) }
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
            Text("Genre Breakdown", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.headerTitleStart())
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} title${if (filtered.size == 1) "" else "s"}", color = c.muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListViewModeToggle(vm.genreFilterViewMode) { vm.setGenreFilterViewMode(context, it) }
                SortMenu(vm.genreFilterSort) { vm.setGenreFilterSort(context, it) }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (isGrid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
                itemsIndexed(filtered, key = { _, it -> it.id }) { index, item -> StaggeredItem(index, staggerSeen) { ListGridCard(item, onOpenDetail) } }
                if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No titles with this genre yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 96.dp)) {
                item { header() }
                itemsIndexed(filtered, key = { _, it -> it.id }) { index, it ->
                    StaggeredItem(index, staggerSeen) {
                        Column {
                            ListRow(it, onOpenDetail, showType = false)
                        }
                    }
                }
                if (filtered.isEmpty()) item { Text("No titles with this genre yet.", color = c.muted, modifier = Modifier.fillMaxWidth().padding(36.dp), textAlign = TextAlign.Center) }
            }
        }
        GenreFilterFab(genre, onClick = { genreSheetOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 20.dp))
    }
    if (genreSheetOpen) GenreFilterSheet(genreCounts, typeItems.size, genre, onDismiss = { genreSheetOpen = false }) { genre = it; genreSheetOpen = false }
}
// Genre picker FAB —
// StatusFilterFab. Truncates to one
// doesn't blow up the

@Composable fun GenreFilterFab(current: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = c.primary,
        contentColor = c.onPrimary,
        icon = { Icon(Icons.Default.Sell, contentDescription = null) },
        text = { Text(current.ifBlank { "All Genres" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
    )
}
// Genre picker sheet —
// with a proportional bar
// distribution the chart summarizes

@Composable fun GenreFilterSheet(genres: List<Pair<String, Int>>, total: Int, current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val c = LocalKikoColors.current
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Filter by", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Genre", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GenreFilterBarRow("All", total, total, c.primary, selected = current.isBlank()) { onSelect("") }
                genres.forEachIndexed { index, (g, count) -> GenreFilterBarRow(g, count, total, chartColor(c, index), selected = current == g) { onSelect(g) } }
            }
        }
    }
}
// One ranked row in
// library total, colored the
// color reads the same

@Composable fun GenreFilterBarRow(label: String, count: Int, total: Int, barColor: Color, selected: Boolean, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    val fraction = if (total > 0) (count.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(14.dp)))
            .let { m -> if (selected) m.background(c.surfaceContainerHigh) else m }
            .kikoClickable(scale = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = c.primary, modifier = Modifier.size(16.dp).padding(end = 8.dp))
                Text(label, color = c.ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
            }
            Text(count.toString(), color = barColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(kikoPillShape()).background(c.surfaceLow)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(kikoPillShape()).background(barColor))
        }
    }
}

// App info page

// Anime / Manga switch for the Stats card — a Material 3 Expressive connected button group
// (single-select), same construction as the tab row on the detail page: ToggleButtons spaced by
// ButtonGroupDefaults.ConnectedSpaceBetween with the stock leading / trailing connected shapes.
// Unchecked = surfaceContainer, checked = primary.
@Composable
private fun StatsTypeButtonGroup(current: MediaType, onSelect: (MediaType) -> Unit) {
    val c = LocalKikoColors.current
    val types = MediaType.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        types.forEachIndexed { index, t ->
            ToggleButton(
                checked = current == t,
                onCheckedChange = { if (current != t) onSelect(t) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    types.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = c.surfaceContainer,
                    contentColor = c.onSurfaceVariant,
                    checkedContainerColor = c.primary,
                    checkedContentColor = c.onPrimary,
                ),
            ) {
                Text(if (t == MediaType.Anime) "Anime" else "Manga", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}