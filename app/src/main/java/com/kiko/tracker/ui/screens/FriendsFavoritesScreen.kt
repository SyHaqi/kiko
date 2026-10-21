@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.components.headerTitleStart
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
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoCorner
import kotlinx.coroutines.launch

// Full Friends page reached from Profile's Friends row ("See more") and the drawer. (Still named
// FriendsFavoritesScreen so its call sites keep working — the Favorites tab is gone, and the
// favorites callbacks below are now unused.) Friends aren't in MAL's official API, so they're
// scraped (MalProfileScrapeApi) with a logged-in session cookie; this screen shows the embedded
// MalLoginWebView first if there isn't one yet.
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

    var friends by remember { mutableStateOf<List<MalFriend>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        if (username.isBlank()) return
        loading = true; error = null
        scope.launch {
            val api = MalProfileScrapeApi(context)
            runCatching {
                friends = api.friends(username)
            }.onFailure { e ->
                if (e is MalSessionExpired) {
                    connected = false; session.clear()
                } else {
                    error = "Couldn't load friends — try again."
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
                        KikoLoadingIndicator(color = c.primary)
                        Text("Confirming your MAL login…", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
            Text("Friends", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.headerTitleStart())
        }

        if (!connected) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.People, null, tint = c.muted, modifier = Modifier.size(48.dp))
                Text("Unlock more features", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "MAL doesn't expose some features such as friends, favorites, etc. Kiko needs to open a one-time login page to read them from your profile.",
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
            loading && friends == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(modifier = Modifier.padding(top = 40.dp), color = c.primary) }
            error != null -> Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = c.muted, fontSize = 13.sp)
                TextButton(onClick = { load() }, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
            }
            else -> FriendsList(friends.orEmpty(), c, onOpenFriend)
        }
    }
}

@Composable
private fun FriendsList(friends: List<MalFriend>, c: KikoColors, onOpenFriend: (MalFriend) -> Unit = {}) {
    if (friends.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { Text("No friends listed on this profile.", color = c.muted, fontSize = 13.sp) }
        return
    }
    // Tonal rows, one grouped list — same look as the profile drawer / Settings groups.
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        friends.forEachIndexed { index, friend ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(friendGroupShape(index, friends.size)).background(c.surfaceContainer)
                    .kikoClickable(scale = 0.98f) { onOpenFriend(friend) }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Circle avatar, same as the Friends row on Profile.
                if (!friend.avatarUrl.isNullOrBlank()) {
                    FriendAvatar(
                        url = friend.avatarUrl, contentDescription = friend.username,
                        modifier = Modifier.size(52.dp).clip(kikoCircleShape()).background(c.surfaceContainerHigh),
                    )
                } else {
                    Box(Modifier.size(52.dp).clip(kikoCircleShape()).background(c.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        Text(friend.username.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.ink)
                    }
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(friend.username, color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    friend.lastOnline?.let {
                        Text(it, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                    friend.friendsSince?.let {
                        Text("Friends since $it", color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
                    }
                }
            }
        }
    }
}

// Big outer corners, tight inner ones — same grouped shape as the profile drawer and Settings.
private fun friendGroupShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val outer = 28.dp
    val inner = 4.dp
    if (count == 1) return RoundedCornerShape(outer)
    return when (index) {
        0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}