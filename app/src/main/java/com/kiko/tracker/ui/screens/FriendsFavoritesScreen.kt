@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiko.tracker.data.api.MalFavoriteEntry
import com.kiko.tracker.data.api.MalFavorites
import com.kiko.tracker.data.api.MalFriend
import com.kiko.tracker.data.api.MalProfileScrapeApi
import com.kiko.tracker.data.api.MalSessionCookie
import com.kiko.tracker.data.api.MalSessionExpired
import com.kiko.tracker.ui.components.MalLoginWebView
import com.kiko.tracker.ui.theme.KikoColors
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import kotlinx.coroutines.launch

private enum class FriendsFavoritesTab { Friends, Favorites }

// Full page reached from Profile's "Friends & Favorites" card. Both are
// scraped off the profile page (MalProfileScrapeApi) since neither is in
// MAL's official API — that needs a logged-in session cookie, so this screen
// shows the embedded MalLoginWebView first if there isn't one yet.
@Composable fun FriendsFavoritesScreen(
    username: String, onBack: () -> Unit,
    onOpenCharacter: (Int) -> Unit = {}, onOpenPerson: (Int) -> Unit = {}, onOpenCompany: (Int) -> Unit = {},
    onOpenFavoriteTitle: (Int, com.kiko.tracker.data.model.MediaType) -> Unit = { _, _ -> },
    // Tapping a friend on the Friends tab — opens an in-app
    // FriendProfileScreen for them instead of falling back to the browser.
    onOpenFriend: (MalFriend) -> Unit = {},
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val session = remember { MalSessionCookie(context) }
    var connected by remember { mutableStateOf(session.has()) }
    var showLogin by remember { mutableStateOf(false) }
    var verifyingLogin by remember { mutableStateOf(false) }

    var tab by remember { mutableStateOf(FriendsFavoritesTab.Friends) }
    var friends by remember { mutableStateOf<List<MalFriend>?>(null) }
    var favorites by remember { mutableStateOf<MalFavorites?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        if (username.isBlank()) return
        loading = true; error = null
        scope.launch {
            val api = MalProfileScrapeApi(context)
            runCatching {
                val f = api.friends(username)
                val fav = api.favorites(username)
                friends = f; favorites = fav
            }.onFailure { e ->
                if (e is MalSessionExpired) {
                    connected = false; session.clear()
                } else {
                    error = "Couldn't load friends & favorites — try again."
                }
            }
            loading = false
        }
    }

    LaunchedEffect(connected) { if (connected) load() }

    if (showLogin) {
        Box(Modifier.fillMaxSize()) {
            MalLoginWebView(
                session = session,
                onLoginSuccess = { showLogin = false; connected = true },
                onVerifyingChange = { verifyingLogin = it },
                modifier = Modifier.fillMaxSize(),
            )
            // The check happens on a page the user never asked to see —
            // an extra full page load between "looks logged in" and MAL
            // actually confirming it. Cover it so it reads as progress
            // instead of the webview stalling.
            if (verifyingLogin) {
                Box(
                    Modifier.fillMaxSize().background(c.surface.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = c.primary)
                        Text("Confirming your MAL login…", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(kikoCorner(13.dp))).background(c.surfaceContainerHigh)) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink) }
            Text("Friends & Favorites", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.padding(start = 12.dp))
        }

        if (!connected) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.People, null, tint = c.muted, modifier = Modifier.size(48.dp))
                Text("Connect your MAL account", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "MAL doesn't expose friends or favorites through sign-in alone — Kiko needs to open a one-time login page to read them from your profile.",
                    color = c.muted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
                )
                Button(onClick = { showLogin = true }, modifier = Modifier.padding(top = 20.dp), colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)) {
                    Text("Connect")
                }
            }
            return
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.surfaceLow).padding(4.dp)) {
            FriendsFavoritesTab.entries.forEach { t ->
                val selected = tab == t
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(kikoCorner(12.dp))).background(if (selected) c.secondaryContainer else Color.Transparent).kikoClickable { tab = t }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (selected) c.onSecondaryContainer else c.muted)
                }
            }
        }

        when {
            loading && friends == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary, modifier = Modifier.padding(top = 40.dp)) }
            error != null -> Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = c.muted, fontSize = 13.sp)
                TextButton(onClick = { load() }, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
            }
            tab == FriendsFavoritesTab.Friends -> FriendsList(friends.orEmpty(), c, onOpenFriend)
            else -> FavoritesSections(favorites, c, onOpenCharacter, onOpenPerson, onOpenCompany, onOpenFavoriteTitle)
        }
    }
}

@Composable
private fun FriendsList(friends: List<MalFriend>, c: KikoColors, onOpenFriend: (MalFriend) -> Unit = {}) {
    if (friends.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { Text("No friends listed on this profile.", color = c.muted, fontSize = 13.sp) }
        return
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
        friends.forEach { friend ->
            Row(
                Modifier.fillMaxWidth().kikoClickable { onOpenFriend(friend) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = friend.avatarUrl, contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHigh),
                )
                Text(friend.username, color = c.ink, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(start = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FavoritesSections(
    favorites: MalFavorites?, c: KikoColors,
    onOpenCharacter: (Int) -> Unit, onOpenPerson: (Int) -> Unit, onOpenCompany: (Int) -> Unit,
    onOpenFavoriteTitle: (Int, com.kiko.tracker.data.model.MediaType) -> Unit,
) {
    val sections = listOfNotNull(
        favorites?.anime?.takeIf { it.isNotEmpty() }?.let { "Anime" to it },
        favorites?.manga?.takeIf { it.isNotEmpty() }?.let { "Manga" to it },
        favorites?.characters?.takeIf { it.isNotEmpty() }?.let { "Characters" to it },
        favorites?.people?.takeIf { it.isNotEmpty() }?.let { "People" to it },
        favorites?.companies?.takeIf { it.isNotEmpty() }?.let { "Companies" to it },
    )
    if (sections.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { Text("No favorites listed on this profile.", color = c.muted, fontSize = 13.sp) }
        return
    }
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp)) {
        sections.forEach { (label, entries) ->
            Text(label.uppercase(), color = c.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                entries.forEach { entry: MalFavoriteEntry ->
                    // Anime/manga/characters/people/companies all open their
                    // in-app detail page (same routing as Profile's inline
                    // Favorites rows) — falls back to the browser only if a
                    // url doesn't parse as a recognized MAL link.
                    Column(
                        Modifier.width(96.dp).kikoClickable {
                            when (label) {
                                "Anime" -> malIdFromFavoriteUrl(entry.url)?.let { onOpenFavoriteTitle(it, com.kiko.tracker.data.model.MediaType.Anime) } ?: uriHandler.openUri(entry.url)
                                "Manga" -> malIdFromFavoriteUrl(entry.url)?.let { onOpenFavoriteTitle(it, com.kiko.tracker.data.model.MediaType.Manga) } ?: uriHandler.openUri(entry.url)
                                else -> when (val link = parseMalProfileLink(entry.url)) {
                                    is MalProfileLink.Character -> onOpenCharacter(link.malId)
                                    is MalProfileLink.Person -> onOpenPerson(link.malId)
                                    is MalProfileLink.Company -> onOpenCompany(link.malId)
                                    null -> uriHandler.openUri(entry.url)
                                }
                            }
                        },
                    ) {
                        AsyncImage(
                            model = entry.imageUrl, contentDescription = null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.surfaceContainerHigh),
                        )
                        Text(entry.title, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        entry.subtitle?.let { Text(it, color = c.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}