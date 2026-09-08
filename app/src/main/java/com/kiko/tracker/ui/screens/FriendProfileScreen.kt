@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiko.tracker.data.api.MalFavorites
import com.kiko.tracker.data.api.MalFriend
import com.kiko.tracker.data.api.MalFriendProfile
import com.kiko.tracker.data.api.MalProfileScrapeApi
import com.kiko.tracker.data.api.MalSessionCookie
import com.kiko.tracker.data.api.MalSessionExpired
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.ui.components.MalLoginWebView
import com.kiko.tracker.ui.components.Pill
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoCorner
import kotlinx.coroutines.launch

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
    username: String, avatarHint: String? = null, onBack: () -> Unit,
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

    var friendProfile by remember(username) { mutableStateOf<MalFriendProfile?>(null) }
    var friends by remember(username) { mutableStateOf<List<MalFriend>?>(null) }
    var favorites by remember(username) { mutableStateOf<MalFavorites?>(null) }
    var loading by remember(username) { mutableStateOf(false) }
    var friendsFavoritesLoading by remember(username) { mutableStateOf(false) }
    var error by remember(username) { mutableStateOf<String?>(null) }
    var statsTab by remember(username) { mutableStateOf(MediaType.Anime) }
    val scope = rememberCoroutineScope()

    fun load() {
        if (username.isBlank()) return
        loading = true; error = null
        scope.launch {
            val api = MalProfileScrapeApi(context)
            runCatching { api.fullProfile(username) }
                .onSuccess { friendProfile = it }
                .onFailure { e ->
                    if (e is MalSessionExpired) { connected = false; session.clear() }
                    else error = "Couldn't load this profile — try again."
                }
            loading = false
        }
    }
    fun loadFriendsFavorites(forUsername: String) {
        friendsFavoritesLoading = true
        scope.launch {
            val api = MalProfileScrapeApi(context)
            runCatching {
                val f = api.friends(forUsername)
                val fav = api.favorites(forUsername)
                friends = f; favorites = fav
            }.onFailure { e -> if (e is MalSessionExpired) { connected = false; session.clear() } }
            friendsFavoritesLoading = false
        }
    }

    LaunchedEffect(connected, username) { if (connected) load() }

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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            // Same "Profile" title as Kiko's own Profile page — the avatar
            // card below already shows this user's name next to their
            // avatar, so this stays generic rather than repeating it.
            Text("Profile", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp).weight(1f))
        }

        if (!connected) {
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
            return
        }

        when {
            loading && friendProfile == null -> Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
            error != null && friendProfile == null -> Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = c.muted, fontSize = 13.sp)
                TextButton(onClick = { load() }, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
            }
            friendProfile != null -> {
                val header = friendProfile!!.header
                // MAL prints these plain (Last Online/Gender/Birthday/Joined)
                // next to the avatar on the real profile page — the same
                // "details" pills row Profile's own avatar card uses for
                // gender, just with the extra fields a friend profile has
                // that Kiko never needed to show for the signed-in user
                // (their own last-online/birthday aren't interesting to
                // themselves the way a friend's are).
                val aboutPills = listOfNotNull(
                    header.lastOnline?.let { "Online $it" },
                    header.gender,
                    header.birthday?.let { "Born $it" },
                    header.joined?.let { "Joined $it" },
                )
                if (aboutPills.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                        aboutPills.forEach { Pill(it, c.surfaceLow, c.muted) }
                    }
                }
                Box(Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                    ProfileStatsSection(
                        connected = true, profile = friendProfile!!.stats, items = emptyList(), onConnect = {},
                        statsTab = statsTab, onStatsTabChange = { statsTab = it },
                        onOpenFriendsFavorites = { onOpenFriendsFavorites(username) },
                        onOpenFriend = onOpenFriend,
                        onOpenCharacter = onOpenCharacter, onOpenPerson = onOpenPerson, onOpenCompany = onOpenCompany,
                        onOpenFavoriteTitle = onOpenFavoriteTitle,
                        cachedFriends = friends, cachedFavorites = favorites,
                        onLoadFriendsFavorites = { forUsername -> loadFriendsFavorites(forUsername) },
                        friendsFavoritesLoading = friendsFavoritesLoading,
                    )
                }
            }
        }
    }
}
