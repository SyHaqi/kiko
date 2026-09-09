@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kiko.tracker.ui.screens

import androidx.activity.compose.BackHandler
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import coil.compose.AsyncImage
import com.kiko.tracker.data.api.MalFriend
import com.kiko.tracker.data.api.MalSessionCookie
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.ui.components.MalLoginWebView
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.viewmodel.LibraryViewModel

// A friend/other MAL user's profile — deliberately 1:1 with Kiko's own
// Profile page (same avatar card, same anime/manga STATS card, same
// FriendsRow, same Favorites rows — all via the shared ProfileStatsSection
// composable), just scraped for someone else's username instead of the
// signed-in user's. None of this is in MAL's official API for anyone but
// "@me" (see MalApi.profile()), so every field here — avatar, gender,
// birthday, joined date, last online, and both anime/manga stats blocks —
// comes from MalProfileScrapeApi.fullProfile scraping their public profile
// page instead, the same way applyMangaStats already does for the
// signed-in user's manga stats. That needs the same logged-in cookie
// session as FriendsFavoritesScreen (MalSessionCookie/MalLoginWebView),
// so this screen shows the same embedded-login flow first if there isn't
// one yet.
//
// Score/genre/format/year distribution charts are the one part of Profile
// this can't mirror: those read from the full local MediaItem list (with
// genres, my rating, etc.), which only exists for the signed-in user's own
// synced library — a friend's list isn't fetchable that way. Passing an
// empty item list into ProfileStatsSection already degrades gracefully:
// the Days/Mean Score/status-breakdown/totals card still shows in full
// (from the scraped stats), just without those four deeper charts under it.
@Composable fun FriendProfileScreen(
    vm: LibraryViewModel, username: String, avatarHint: String? = null, onBack: () -> Unit,
    onOpenFriend: (MalFriend) -> Unit = {}, onOpenFriendsFavorites: (String) -> Unit = {},
    onOpenCharacter: (Int) -> Unit = {}, onOpenPerson: (Int) -> Unit = {}, onOpenCompany: (Int) -> Unit = {},
    onOpenFavoriteTitle: (Int, MediaType) -> Unit = { _, _ -> },
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val session = remember { MalSessionCookie(context) }
    var connected by remember { mutableStateOf(session.has()) }
    var showLogin by remember { mutableStateOf(false) }
    var verifyingLogin by remember { mutableStateOf(false) }

    var statsTab by remember(username) { mutableStateOf(MediaType.Anime) }

    // Cached in the ViewModel keyed by username (see
    // LibraryViewModel.FriendProfileState) rather than remembered locally,
    // so it survives this composable being torn down and rebuilt — e.g.
    // stepping into a friend-of-a-friend and back, or into a favorite's
    // detail page and back — for as long as friendProfileStack (Navigation)
    // stays non-empty. It's dropped once the whole chain is backed out of,
    // so the next visit starts from a fresh scrape rather than stale data.
    val state = vm.getFriendProfileState(username)

    LaunchedEffect(connected, username) { if (connected) vm.loadFriendProfile(context, username) }

    if (showLogin) {
        Box(Modifier.fillMaxSize()) {
            MalLoginWebView(
                session = session,
                onLoginSuccess = { showLogin = false; connected = true },
                onVerifyingChange = { verifyingLogin = it },
                modifier = Modifier.fillMaxSize(),
            )
            if (verifyingLogin) {
                Box(Modifier.fillMaxSize().background(c.surface.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = c.primary)
                        Text("Confirming your MAL login…", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
        }
        return
    }

    if (!connected) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                Text("Profile", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp).weight(1f))
            }
            Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.People, null, tint = c.muted, modifier = Modifier.size(48.dp))
                Text("Connect your MAL account", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "MAL doesn't expose other members' profiles through sign-in alone — Kiko needs to open a one-time login page to read them.",
                    color = c.muted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
                )
                Button(onClick = { showLogin = true }, modifier = Modifier.padding(top = 20.dp), colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)) {
                    Text("Connect")
                }
            }
        }
        return
    }

    // Only shows the pull-to-refresh spinner once there's already content
    // on screen — same "loading && already has data" gate FeaturedArticlesScreen
    // uses, so the very first scrape (which has its own full-page spinner
    // below) doesn't show two loading indicators at once.
    val refreshing = (state.loading && state.profile != null) || (state.friendsFavoritesLoading && state.favorites != null)
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refreshFriendProfile(context, username) }, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
                // Same "Profile" title as Kiko's own Profile page — the avatar
                // card below already shows this user's name next to their
                // avatar, so this stays generic rather than repeating it.
                Text("Profile", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp).weight(1f))
                // 3-dot overflow menu — mirrors Kiko's own Profile header,
                // just with "Open in browser" only (no sign out, since this
                // isn't the signed-in user's account).
                var moreOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { moreOpen = true }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) {
                        Icon(Icons.Default.MoreVert, "More options", tint = c.ink, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }, shape = RoundedCornerShape(kikoCorner(18.dp)), containerColor = c.surfaceContainer) {
                        DropdownMenuItem(
                            text = { Text("Open in browser") },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                            onClick = {
                                moreOpen = false
                                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://myanimelist.net/profile/$username"))
                            },
                        )
                    }
                }
            }

            when {
                state.loading && state.profile == null -> Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Shows the avatar the caller already had (from the
                    // FriendsRow/FriendsList thumbnail that was tapped) while
                    // the full scrape is still in flight, so the page doesn't
                    // open on a completely blank state.
                    if (!avatarHint.isNullOrBlank()) {
                        AsyncImage(model = avatarHint, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(64.dp).clip(kikoCircleShape()).background(c.warm))
                        Spacer(Modifier.height(16.dp))
                    }
                    CircularProgressIndicator(color = c.primary)
                }
                state.error != null && state.profile == null -> Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, color = c.muted, fontSize = 13.sp)
                    TextButton(onClick = { vm.loadFriendProfile(context, username, force = true) }, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
                }
                state.profile != null -> {
                    val header = state.profile.header
                    // MAL prints these plain (Last Online/Gender/Birthday/Joined)
                    // next to the avatar on the real profile page. Passed into
                    // ProfileStatsSection's own avatar card via detailsPills so
                    // they sit under the avatar+name like Profile's gender pill
                    // does, instead of a separate row above the card — and
                    // gender only appears once here (ProfileStatsSection would
                    // otherwise also render it from friendProfile.stats.gender,
                    // which MalProfileScrapeApi copies from the same header).
                    val aboutPills = listOfNotNull(
                        header.lastOnline?.let { DetailPill(Icons.Default.Schedule, it) },
                        header.gender?.let { DetailPill(Icons.Default.Person, it) },
                        header.birthday?.let { DetailPill(Icons.Default.Cake, it) },
                        header.joined?.let { DetailPill(Icons.Default.Event, it) },
                    )
                    Box(Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                        ProfileStatsSection(
                            connected = true, profile = state.profile.stats, items = emptyList(), onConnect = {},
                            statsTab = statsTab, onStatsTabChange = { statsTab = it },
                            onOpenFriendsFavorites = { onOpenFriendsFavorites(username) },
                            onOpenFriend = onOpenFriend,
                            onOpenCharacter = onOpenCharacter, onOpenPerson = onOpenPerson, onOpenCompany = onOpenCompany,
                            onOpenFavoriteTitle = onOpenFavoriteTitle,
                            cachedFriends = state.friends, cachedFavorites = state.favorites,
                            onLoadFriendsFavorites = { forUsername -> vm.loadFriendProfileFriendsFavorites(context, forUsername) },
                            friendsFavoritesLoading = state.friendsFavoritesLoading,
                            detailsPills = aboutPills,
                            cachedAboutMe = state.aboutMe,
                        )
                    }
                }
            }
        }
    }
}