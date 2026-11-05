@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kiko.tracker.ui.screens

import com.kiko.tracker.ui.components.KikoDropdownMenu
import com.kiko.tracker.ui.components.KikoHeader
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.components.headerEdgeEnd
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.components.headerTitleStart
import com.kiko.tracker.util.openInBrowser
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import com.kiko.tracker.ui.components.KikoPullToRefreshBox
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
import com.kiko.tracker.data.api.FriendRelation
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
    onOpenList: (MediaType) -> Unit = {},
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

    LaunchedEffect(connected, username) { if (connected) { vm.loadFriendProfile(context, username); vm.loadFriendRequestInfo(context, username) } }

    // Add Friend: state lives in the ViewModel (per username) so it survives this screen being
    // rebuilt when a title/character page is opened on top and closed again.
    val requestState = vm.getFriendRequestState(username)
    var showRequestDialog by remember(username) { mutableStateOf(false) }
    var showRemoveDialog by remember(username) { mutableStateOf(false) }
    var wasRemoving by remember(username) { mutableStateOf(false) }
    LaunchedEffect(requestState.removing, requestState.removed) {
        if (wasRemoving && requestState.removed) android.widget.Toast.makeText(context, "Removed $username from your friends", android.widget.Toast.LENGTH_SHORT).show()
        wasRemoving = requestState.removing
    }
    // Toast only on the sending -> sent transition, not every time this screen is rebuilt.
    var wasSending by remember(username) { mutableStateOf(false) }
    LaunchedEffect(requestState.sending, requestState.sent) {
        if (wasSending && requestState.sent) android.widget.Toast.makeText(context, "Friend request sent to $username", android.widget.Toast.LENGTH_SHORT).show()
        wasSending = requestState.sending
    }
    LaunchedEffect(requestState.error) {
        requestState.error?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show(); vm.clearFriendRequestError(username) }
    }
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove friend") },
            text = { Text("Remove $username from your MyAnimeList friends?", color = c.muted, fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { showRemoveDialog = false; vm.removeFriend(context, username) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") } },
        )
    }
    if (showRequestDialog) {
        var message by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRequestDialog = false },
            title = { Text("Send friend request") },
            text = {
                Column {
                    Text("Send a friend request to $username on MyAnimeList?", color = c.muted, fontSize = 14.sp)
                    OutlinedTextField(
                        value = message, onValueChange = { message = it.take(255) },
                        label = { Text("Personal message (optional)") },
                        supportingText = { Text("${message.length}/255") },
                        minLines = 2, maxLines = 4,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showRequestDialog = false; vm.sendFriendRequest(context, username, message.trim()) }) { Text("Send") } },
            dismissButton = { TextButton(onClick = { showRequestDialog = false }) { Text("Cancel") } },
        )
    }

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
                        KikoLoadingIndicator(color = c.primary)
                        Text("Confirming your MAL login…", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
        }
        return
    }

    if (!connected) {
        Column(Modifier.fillMaxSize()) {
            KikoTopBar(title = "Profile", onBack = onBack, progress = { 1f })
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.People, null, tint = c.muted, modifier = Modifier.size(48.dp))
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
    val scrollState = rememberScrollState(initial = state.scrollOffset)
    // Saved regardless of how the user leaves (back button, or tapping
    // into a title/character/person/company/friend/list-status/Friends &
    // Favorites link below) since this whole screen is torn down and
    // rebuilt on return — see FriendProfileState.scrollOffset's doc
    // comment in LibraryViewModel.
    DisposableEffect(username) { onDispose { vm.saveFriendProfileScroll(username, scrollState.value) } }
    KikoPullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refreshFriendProfile(context, username) }, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val titleProgress = rememberTitleProgress(scrollState, startOffset = 40.dp, fadeRange = 40.dp)
            KikoTopBar(title = username, onBack = onBack, progress = { titleProgress.value }) {
                ShareMoreMenu(title = username, shareUrl = "https://myanimelist.net/profile/$username")
            }
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {

                    when {
                        state.loading && state.profile == null -> Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // Shows the avatar the caller already had (from the
                            // FriendsRow/FriendsList thumbnail that was tapped) while
                            // the full scrape is still in flight, so the page doesn't
                            // open on a completely blank state.
                            if (!avatarHint.isNullOrBlank()) {
                                FriendAvatar(url = avatarHint, modifier = Modifier.size(64.dp).clip(kikoCircleShape()).background(c.warm))
                                Spacer(Modifier.height(16.dp))
                            }
                            KikoLoadingIndicator(color = c.primary)
                        }
                        state.error != null && state.profile == null -> Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
                                header.lastOnline?.let { DetailPill(Icons.Rounded.Schedule, it) },
                                header.gender?.let { DetailPill(Icons.Rounded.Person, it) },
                                header.birthday?.let { DetailPill(Icons.Rounded.Cake, it) },
                                header.joined?.let { DetailPill(Icons.Rounded.Event, it) },
                            )
                            val addFriendSlot: (@Composable () -> Unit)? =
                                when {
                                    requestState.info?.relation == FriendRelation.CanRemove ->
                                        ({ FriendActionButton(FriendAction.Remove, busy = requestState.removing, onClick = { showRemoveDialog = true }) })
                                    requestState.info?.relation == FriendRelation.CanRequest || requestState.sent ->
                                        ({ FriendActionButton(FriendAction.Add, busy = requestState.sending, done = requestState.sent, onClick = { showRequestDialog = true }) })
                                    else -> null
                                }
                            Box(Modifier.padding(bottom = 24.dp)) {
                                ProfileStatsSection(
                                    connected = true, profile = state.profile.stats, items = emptyList(), onConnect = {},
                                    statsTab = statsTab, onStatsTabChange = { statsTab = it },
                                    isOwnProfile = false,
                                    onOpenFriendsFavorites = { onOpenFriendsFavorites(username) },
                                    onOpenFriend = onOpenFriend,
                                    onOpenCharacter = onOpenCharacter, onOpenPerson = onOpenPerson, onOpenCompany = onOpenCompany,
                                    onOpenFavoriteTitle = onOpenFavoriteTitle,
                                    onOpenFriendList = onOpenList,
                                    cachedFriends = state.friends, cachedFavorites = state.favorites,
                                    onLoadFriendsFavorites = { forUsername -> vm.loadFriendProfileFriendsFavorites(context, forUsername) },
                                    friendsFavoritesLoading = state.friendsFavoritesLoading,
                                    detailsPills = aboutPills,
                                    cachedAboutMe = state.aboutMe,
                                    nameTrailing = addFriendSlot,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Icon-only friend button beside the username in the profile header (no label). Shows "add" when
// MAL printed its Add-friend link for this user, "remove" when it printed Remove-friend (existing
// friends); never composed on your own page. After a request is sent the add icon flips to a
// disabled check for the rest of the visit.
private enum class FriendAction { Add, Remove }

@Composable private fun FriendActionButton(action: FriendAction, busy: Boolean, onClick: () -> Unit, done: Boolean = false) {
    val c = LocalKikoColors.current
    val isAdd = action == FriendAction.Add
    FilledTonalIconButton(
        onClick = onClick,
        enabled = !busy && !done,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isAdd) c.primary else c.secondaryContainer,
            contentColor = if (isAdd) c.onPrimary else c.onSecondaryContainer,
            disabledContainerColor = c.secondaryContainer,
            disabledContentColor = c.onSecondaryContainer,
        ),
    ) {
        when {
            busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.onSecondaryContainer)
            done -> Icon(Icons.Rounded.Check, contentDescription = "Friend request sent", modifier = Modifier.size(20.dp))
            isAdd -> Icon(Icons.Rounded.PersonAdd, contentDescription = "Add friend", modifier = Modifier.size(20.dp))
            else -> Icon(Icons.Rounded.PersonRemove, contentDescription = "Remove friend", modifier = Modifier.size(20.dp))
        }
    }
}