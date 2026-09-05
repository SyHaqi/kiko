@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.navigation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiko.tracker.data.api.MalApi
import com.kiko.tracker.data.api.MalClub
import com.kiko.tracker.data.api.StackBrowseKind
import com.kiko.tracker.data.api.StackDetail
import com.kiko.tracker.data.model.CharacterDetail
import com.kiko.tracker.data.model.ColorSource
import com.kiko.tracker.data.model.CommunityTab
import com.kiko.tracker.data.model.CompanyDetail
import com.kiko.tracker.data.model.Destination
import com.kiko.tracker.data.model.DiscoverFilters
import com.kiko.tracker.data.model.LocalTitleLanguage
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.PaletteStyle
import com.kiko.tracker.data.model.PersonDetail
import com.kiko.tracker.data.model.ReviewEntry
import com.kiko.tracker.data.model.ThemeMode
import com.kiko.tracker.data.model.prev
import com.kiko.tracker.ui.components.AvatarMenu
import com.kiko.tracker.ui.components.BottomBar
import com.kiko.tracker.ui.components.ColorSourceSheet
import com.kiko.tracker.ui.components.ErrorDialog
import com.kiko.tracker.ui.components.PaletteStyleSheet
import com.kiko.tracker.ui.components.ThemeSheet
import com.kiko.tracker.ui.components.TitleLanguageSheet
import com.kiko.tracker.ui.components.UpdateDialog
import com.kiko.tracker.ui.screens.AboutScreen
import com.kiko.tracker.ui.screens.CharacterDetailScreen
import com.kiko.tracker.ui.screens.ClubDetailScreen
import com.kiko.tracker.ui.screens.CommunityScreen
import com.kiko.tracker.ui.screens.CompanyDetailScreen
import com.kiko.tracker.ui.screens.DetailScreen
import com.kiko.tracker.ui.screens.DetailScreenActions
import com.kiko.tracker.ui.screens.DiscoverScreen
import com.kiko.tracker.ui.screens.EditSheet
import com.kiko.tracker.ui.screens.FormatFilterScreen
import com.kiko.tracker.ui.screens.ForumTopicScreen
import com.kiko.tracker.ui.screens.GenreFilterScreen
import com.kiko.tracker.ui.screens.HomeScreen
import com.kiko.tracker.ui.screens.ListScreen
import com.kiko.tracker.ui.screens.MediaStacksScreen
import com.kiko.tracker.ui.screens.PersonDetailScreen
import com.kiko.tracker.ui.screens.ProfileStatsScreen
import com.kiko.tracker.ui.screens.RankingScreen
import com.kiko.tracker.ui.screens.RecommendationsScreen
import com.kiko.tracker.ui.screens.ReviewScreen
import com.kiko.tracker.ui.screens.ScheduleScreen
import com.kiko.tracker.ui.screens.ScoreFilterScreen
import com.kiko.tracker.ui.screens.ScoreStatsSheet
import com.kiko.tracker.ui.screens.SeasonalScreen
import com.kiko.tracker.ui.screens.SettingsScreen
import com.kiko.tracker.ui.screens.StackDetailScreen
import com.kiko.tracker.ui.screens.StacksHomeScreen
import com.kiko.tracker.ui.screens.StacksScreen
import com.kiko.tracker.ui.screens.YearFilterScreen
import com.kiko.tracker.ui.screens.parseMalDeepLink
import com.kiko.tracker.ui.theme.DarkKiko
import com.kiko.tracker.ui.theme.KikoShapes
import com.kiko.tracker.ui.theme.KikoTypography
import com.kiko.tracker.ui.theme.LightKiko
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.amoledify
import com.kiko.tracker.ui.theme.resolveSeedColor
import com.kiko.tracker.ui.theme.themedPalette
import com.kiko.tracker.ui.theme.toMaterialColorScheme
import com.kiko.tracker.util.AppUpdateChecker
import com.kiko.tracker.viewmodel.LibraryViewModel

@Composable

fun SyncSystemBars(darkTheme: Boolean, background: Color) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as? Activity
        SideEffect {
            val window = activity?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            window.statusBarColor = background.toArgb()
            window.navigationBarColor = background.toArgb()
        }
    }
}

// Shared navigation transition motion

// Shared navigation transition motion
// fade+slide so the screen
// (matching the depth cue

val PushEnter = fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 4 }

val PushExit = fadeOut(tween(150)) + scaleOut(tween(220), targetScale = .96f)

val PopEnter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = .96f)

val PopExit = fadeOut(tween(260)) + slideOutHorizontally(tween(260)) { it / 4 }

val FadeEnter = fadeIn(tween(220))

val FadeExit = fadeOut(tween(150))

// Top-level navigation state
sealed class TopScreen {
    data class Detail(val item: MediaItem) : TopScreen()
    object Ranking : TopScreen()
    // Full grid of "You
    object Recommendations : TopScreen()
    // Seed initial schedule day
    data class Schedule(val initialDay: java.time.DayOfWeek) : TopScreen()
    // Reading single forum topic
    data class Topic(val topicId: Int, val title: String) : TopScreen()
    // App info page
    object About : TopScreen()
    // Full review readout
    data class Review(val review: ReviewEntry, val itemTitle: String) : TopScreen()
    // Reviews page in webview
    // Interest stacks homepage —
    object StacksHome : TopScreen()
    // Interest stacks full browse/search,
    data class StacksBrowse(val initialKind: StackBrowseKind) : TopScreen()
    // One stack's entries
    data class StackDetail(val stackId: Int, val title: String) : TopScreen()
    // Full "Interest Stacks" page
    // Detail — see DetailScreenActions.onOpenStacksList
    data class MediaStacks(val item: MediaItem) : TopScreen()
    // Single club page
    data class ClubDetail(val club: MalClub) : TopScreen()
    // Full pages opened from
    object ProfileStats : TopScreen()
    object SettingsPage : TopScreen()
    // Titles at one score,
    data class ScoreFilter(val type: MediaType, val score: Int) : TopScreen()
    // Titles released in one
    data class YearFilter(val type: MediaType, val year: Int) : TopScreen()
    // Titles of one format
    // profile's format breakdown chart
    data class FormatFilter(val type: MediaType, val format: String) : TopScreen()
    // Titles with one genre,
    data class GenreFilter(val type: MediaType, val genre: String) : TopScreen()
    // Character detail page, opened
    // (not CharacterDetail) to avoid
    // (see CharacterModels.kt) that this
    // (nullable) so this route
    // resolves — CharacterDetailScreen renders
    // character is still null
    // characterDetailOpenId's doc comment below
    data class CharacterPage(val malId: Int, val character: CharacterDetail?) : TopScreen()
    // Person detail page, opened
    // Detail/CharacterPage). Named PersonPage for
    // avoids colliding with the
    // malId-carried-separately shape as CharacterPage
    data class PersonPage(val malId: Int, val person: PersonDetail?) : TopScreen()
    // Company (studio/producer/licensor) detail page,
    // tab. Unlike CharacterPage/PersonPage above,
    // another detail page (no
    // castCompanyOnTop equivalent to thread
    // this gets pushed from.
    // (CompanyDetailScreen shows CompanyDetailScreenSkeleton while
    data class CompanyPage(val malId: Int, val company: CompanyDetail?) : TopScreen()
    data class Tab(val destination: Destination) : TopScreen()
}
// Same screen vs navigation

fun TopScreen.navKey(): Any = when (this) {
    is TopScreen.Detail -> "detail:${item.id}"
    TopScreen.Ranking -> "ranking"
    TopScreen.Recommendations -> "recommendations"
    is TopScreen.Schedule -> "schedule"
    is TopScreen.Topic -> "topic:$topicId"
    TopScreen.About -> "about"
    is TopScreen.Review -> "review:${review.malId}"
    TopScreen.StacksHome -> "stacksHome"
    is TopScreen.StacksBrowse -> "stacksBrowse"
    is TopScreen.StackDetail -> "stackDetail:$stackId"
    is TopScreen.MediaStacks -> "mediaStacks:${item.id}:${item.type}"
    is TopScreen.ClubDetail -> "clubDetail:${club.id}"
    TopScreen.ProfileStats -> "profileStats"
    TopScreen.SettingsPage -> "settingsPage"
    is TopScreen.ScoreFilter -> "scoreFilter:$type:$score"
    is TopScreen.YearFilter -> "yearFilter:$type:$year"
    is TopScreen.FormatFilter -> "formatFilter:$type:$format"
    is TopScreen.GenreFilter -> "genreFilter:$type:$genre"
    is TopScreen.CharacterPage -> "characterPage:$malId"
    is TopScreen.PersonPage -> "personPage:$malId"
    is TopScreen.CompanyPage -> "companyPage:$malId"
    is TopScreen.Tab -> "tab:$destination"
}

fun TopScreen.isFullPage() = this is TopScreen.Detail || this is TopScreen.Ranking || this is TopScreen.Recommendations || this is TopScreen.Schedule || this is TopScreen.Topic || this is TopScreen.About || this is TopScreen.Review || this is TopScreen.StacksHome || this is TopScreen.StacksBrowse || this is TopScreen.StackDetail || this is TopScreen.MediaStacks || this is TopScreen.ClubDetail || this is TopScreen.ProfileStats || this is TopScreen.SettingsPage || this is TopScreen.ScoreFilter || this is TopScreen.YearFilter || this is TopScreen.FormatFilter || this is TopScreen.GenreFilter || this is TopScreen.CharacterPage || this is TopScreen.PersonPage || this is TopScreen.CompanyPage


@Composable fun KikoApp(vm: LibraryViewModel = viewModel(), onSignIn: () -> Unit = {}, onSignOut: () -> Unit = {}, malLink: Uri? = null, onMalLinkHandled: () -> Unit = {}) {
    val context = LocalContext.current
    var editor by remember { mutableStateOf<MediaItem?>(null) }; var themeOpen by remember { mutableStateOf(false) }; var titleLangOpen by remember { mutableStateOf(false) }
    var colorSourceOpen by remember { mutableStateOf(false) }; var paletteStyleOpen by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    // Related title navigation stack
    var detailStack by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    // Direction hint for the
    // hops (tapping a related/recommended
    // "isFullPage() on both sides"
    // cross-fade with no sense
    // it's already up to
    var detailGoingBack by remember { mutableStateOf(false) }
    // Interest stacks nav state:
    // (rather than alongside the
    // Discover-detour restoration needs to
    var stacksHomeOpen by remember { mutableStateOf(false) }
    var stacksBrowseKind by remember { mutableStateOf<StackBrowseKind?>(null) }
    var stackDetailOpen by remember { mutableStateOf<Pair<Int, String>?>(null) }
    // Character detail page, opened
    // cast row, or a
    //
    // characterDetailOpenId is set the
    // it has even started
    // skeleton, see CharacterDetailScreenSkeleton) shows
    // user parked on the
    // (the resolved data) fills
    // used to gate on
    // null" instead, since the
    // just what CharacterDetailScreen renders
    var characterDetailOpenId by remember { mutableStateOf<Int?>(null) }
    var characterDetailOpen by remember { mutableStateOf<CharacterDetail?>(null) }
    // True while the open
    // Characters row (see openCharacter
    // need opposite topScreen priority
    // non-null at once: a
    // Detail page that happens
    // *from* the very Detail
    // false by openDetail() (any
    // own Animeography/Mangaography row) and
    // never outlives the specific
    var castCharacterOnTop by remember { mutableStateOf(false) }
    // Person detail page, opened
    // a Detail page's or
    // reasoning as characterDetailOpenId/castCharacterOnTop just
    // since a person can
    // Same immediate-id-then-fill shape as
    var personDetailOpenId by remember { mutableStateOf<Int?>(null) }
    var personDetailOpen by remember { mutableStateOf<PersonDetail?>(null) }
    var castPersonOnTop by remember { mutableStateOf(false) }
    // Company detail page, opened
    // link tapped inside a
    // parseMalProfileLink) — castCompanyOnTop below
    // reasoning as castCharacterOnTop/castPersonOnTop above,
    // immediate-id-then-fill shape as characterDetailOpenId
    var companyDetailOpenId by remember { mutableStateOf<Int?>(null) }
    var companyDetailOpen by remember { mutableStateOf<CompanyDetail?>(null) }
    // True while the open
    // post/club post/stack description rather
    // castCharacterOnTop/castPersonOnTop, just there's no
    // for Company, only this
    // page's own onBack, same
    var castCompanyOnTop by remember { mutableStateOf(false) }
    // True while the currently-open
    // inside a still-open Topic
    // parseMalProfileLink (there's no in-app
    // — they fall through
    // registered to open myanimelist.net/anime
    // AndroidManifest.xml), the tap round-trips
    // out the malLink handler
    // detailItem instead of a
    // are otherwise checked ahead
    // topic/stack opened *from* a
    // otherwise hide the freshly
    // closed. Reset by openDetail()
    var detailOnTopOfTopicOrStack by remember { mutableStateOf(false) }
    // Item to return to
    var discoverReturnItem by remember { mutableStateOf<MediaItem?>(null) }
    // Where the detour started
    // specific stack detail screen
    // back where the user
    // Set once per detour
    // the whole detour is
    // bottom nav bar.
    var discoverReturnDestination by remember { mutableStateOf<Destination?>(null) }
    var discoverReturnStack by remember { mutableStateOf<Pair<Int, String>?>(null) }
    fun openDetail(item: MediaItem) {
        // Always the start of
        // stack entry, etc. —
        // chain, that's openRelatedDetail below).
        // unreachable, so drop it
        vm.clearDetailCache()
        detailGoingBack = false
        detailStack = emptyList()
        // A fresh top-level open
        // including a work opened
        // row, which also goes
        castCharacterOnTop = false
        // Same reasoning, one level
        // opened from a person
        // overlay from wherever it
        castPersonOnTop = false
        // Same reasoning — a
        // overlay opened from a
        castCompanyOnTop = false
        // Same reasoning, for the
        // detailOnTopOfTopicOrStack's doc comment) —
        // this back at false;
        // after calling openDetail(), for
        detailOnTopOfTopicOrStack = false
        // A title opened while
        // another result on the
        // that same detour —
        // returns to the real
        // fresh open (no detour
        if (discoverReturnDestination == null) discoverReturnItem = null
        selectedItem = item
    }
    fun openRelatedDetail(from: MediaItem, to: MediaItem) { detailGoingBack = false; detailStack = detailStack + from; selectedItem = to }
    // Opens the Character page
    // above for why. Every
    // now goes through this
    // to be gotten right
    // site used to set
    // whatever was underneath (same
    // leaving the skeleton on
    // raises vm.error for the
    fun openCharacter(malId: Int, castOnTop: Boolean = false) {
        characterDetailOpenId = malId
        characterDetailOpen = null
        castCharacterOnTop = castOnTop
        vm.openCharacterDetail(
            context, malId,
            onLoaded = { character -> if (characterDetailOpenId == malId) characterDetailOpen = character },
            onError = { if (characterDetailOpenId == malId) { characterDetailOpenId = null; castCharacterOnTop = false } },
        )
    }
    // Same shape as openCharacter
    fun openPerson(malId: Int, castOnTop: Boolean = false) {
        personDetailOpenId = malId
        personDetailOpen = null
        castPersonOnTop = castOnTop
        vm.openPersonDetail(
            context, malId,
            onLoaded = { person -> if (personDetailOpenId == malId) personDetailOpen = person },
            onError = { if (personDetailOpenId == malId) { personDetailOpenId = null; castPersonOnTop = false } },
        )
    }
    // Same shape as openCharacter/openPerson
    // the one case a
    // link tapped inside a
    // parseMalProfileLink and castCompanyOnTop's doc
    fun openCompany(malId: Int, castOnTop: Boolean = false) {
        companyDetailOpenId = malId
        companyDetailOpen = null
        castCompanyOnTop = castOnTop
        vm.openCompanyDetail(
            context, malId,
            onLoaded = { company -> if (companyDetailOpenId == malId) companyDetailOpen = company },
            onError = { if (companyDetailOpenId == malId) { companyDetailOpenId = null; castCompanyOnTop = false } },
        )
    }
    fun backDetail() {
        val leaving = selectedItem
        val prev = detailStack.lastOrNull()
        if (prev != null) {
            detailGoingBack = true; selectedItem = prev; detailStack = detailStack.dropLast(1)
            // Stepped back one level
            // from here (tapping a
            // fresh title anyway), so
            // linger until the whole
            leaving?.let { vm.forgetDetailPage(it.id, it.type) }
            return
        }
        // Backing out from the
        // is now unreachable, so
        vm.clearDetailCache()
        selectedItem = null
        // Detail page is gone
        // let it linger true
        // detailOnTopOfTopicOrStack's doc comment).
        detailOnTopOfTopicOrStack = false
        // Only restore once the
        // been consumed (discoverReturnItem null)
        // intermediate title opened mid-detour,
        // underneath, not jump straight
        val destination = discoverReturnDestination
        if (discoverReturnItem == null && destination != null) {
            vm.destination = destination
            stackDetailOpen = discoverReturnStack
            discoverReturnDestination = null; discoverReturnStack = null
        }
    }
    // Forum topic screen state
    var forumTopicOpen by remember { mutableStateOf<Pair<Int, String>?>(null) }
    // Handle tapped MAL link
    LaunchedEffect(malLink) {
        val uri = malLink ?: return@LaunchedEffect
        parseMalDeepLink(uri)?.let { (id, type) ->
            // Captured before openDetail() resets
            // default for every other
            // link tapped from inside
            // right after.
            val openedFromTopicOrStack = forumTopicOpen != null || stackDetailOpen != null
            vm.loading = true
            runCatching { MalApi(context).detail(id, type) }
                .onSuccess { openDetail(it); detailOnTopOfTopicOrStack = openedFromTopicOrStack }
                .onFailure { vm.error = it.message ?: "Could not load that MAL link" }
            vm.loading = false
        }
        onMalLinkHandled()
    }
    // Home full-screen destinations
    var rankingOpen by remember { mutableStateOf(false) }
    var recommendationsOpen by remember { mutableStateOf(false) }
    // Schedule day to open
    var scheduleOpen by remember { mutableStateOf(false) }
    var scheduleInitialDay by remember { mutableStateOf(java.time.LocalDate.now().dayOfWeek) }
    fun openSchedule(day: java.time.DayOfWeek) { scheduleInitialDay = day; scheduleOpen = true }
    // About page open state
    var aboutOpen by remember { mutableStateOf(false) }
    // Full review readout state
    var reviewOpen by remember { mutableStateOf<Pair<ReviewEntry, String>?>(null) }
    // Reviews webview state
    fun openStacks() { stackDetailOpen = null; stacksBrowseKind = null; stacksHomeOpen = true }
    fun openStacksBrowse(kind: StackBrowseKind) { stackDetailOpen = null; stacksBrowseKind = kind }
    // Club detail state
    var clubDetailOpen by remember { mutableStateOf<MalClub?>(null) }
    // Full pages opened from
    var profileStatsOpen by remember { mutableStateOf(false) }
    var settingsPageOpen by remember { mutableStateOf(false) }
    // Score distribution drill-down, opened
    var scoreFilterOpen by remember { mutableStateOf<Pair<MediaType, Int>?>(null) }
    // Year distribution drill-down, opened
    var yearFilterOpen by remember { mutableStateOf<Pair<MediaType, Int>?>(null) }
    // Format breakdown drill-down, opened
    var formatFilterOpen by remember { mutableStateOf<Pair<MediaType, String>?>(null) }
    // Genre breakdown drill-down, opened
    var genreFilterOpen by remember { mutableStateOf<Pair<MediaType, String>?>(null) }
    // Full "Interest Stacks" page,
    // renders on top of
    // instant back is pressed,
    // need for a separate
    var mediaStacksOpen by remember { mutableStateOf<MediaItem?>(null) }
    // A title's community score
    // more" on Status distribution
    // somewhere you navigate/browse (same
    var scoreStatsOpen by remember { mutableStateOf<MediaItem?>(null) }
    // Jump from a detail
    // Clears every other overlay
    // page can be reached
    // of those leaves its
    // topScreen's priority chain checks
    // once selectedItem is cleared
    // instead of the Discover
    // of a stack would
    fun jumpToDiscover(from: MediaItem, type: String, filters: DiscoverFilters) {
        // Only remember the true
        // detour — a jump
        // another creator link while
        // overwrite it with the
        if (discoverReturnDestination == null) {
            discoverReturnDestination = vm.destination
            discoverReturnStack = stackDetailOpen
        }
        discoverReturnItem = from
        // Deliberately NOT calling vm.clearDetailCache()
        // this is just a
        // genre chip), and `from`
        // detour is backed out
        // re-fetch everything (related/recommended rows,
        // scratch instead of popping
        // for a page that's
        // dropped for real once
        // resets the search itself
        selectedItem = null; detailStack = emptyList()
        characterDetailOpenId = null; characterDetailOpen = null; castCharacterOnTop = false
        personDetailOpenId = null; personDetailOpen = null; castPersonOnTop = false
        companyDetailOpenId = null; companyDetailOpen = null
        stackDetailOpen = null; stacksBrowseKind = null; stacksHomeOpen = false
        clubDetailOpen = null
        mediaStacksOpen = null
        rankingOpen = false; recommendationsOpen = false; scheduleOpen = false
        forumTopicOpen = null; aboutOpen = false; reviewOpen = null
        profileStatsOpen = false; settingsPageOpen = false; scoreFilterOpen = null; yearFilterOpen = null; formatFilterOpen = null
        genreFilterOpen = null
        vm.destination = Destination.Discover
        vm.runDiscoverSearch(context, "", type, filters)
    }
    // Live-merge search result —
    // manga ids are separate
    // Steins;Gate Movie: Fuka Ryouiki
    val editorItem = editor?.let { ed -> vm.visibleItems.find { it.id == ed.id && it.type == ed.type } ?: vm.items.find { it.id == ed.id && it.type == ed.type } ?: ed }
    // Prefer live item copy
    val detailItem = selectedItem?.let { sel -> vm.items.find { it.id == sel.id && it.type == sel.type } ?: sel }
    // Back press returns home
    BackHandler(enabled = detailItem == null && characterDetailOpenId == null && personDetailOpenId == null && companyDetailOpenId == null && !rankingOpen && !recommendationsOpen && !scheduleOpen && forumTopicOpen == null && !aboutOpen && reviewOpen == null && !stacksHomeOpen && stacksBrowseKind == null && stackDetailOpen == null && mediaStacksOpen == null && clubDetailOpen == null && !profileStatsOpen && !settingsPageOpen && scoreFilterOpen == null && yearFilterOpen == null && formatFilterOpen == null && genreFilterOpen == null && (vm.destination != Destination.Home || discoverReturnItem != null)) {
        val returnItem = discoverReturnItem
        if (returnItem != null && vm.destination == Destination.Discover) {
            discoverReturnItem = null
            selectedItem = returnItem
            // Same reset as onExitResults
            // normally beats this one
            // but keep this in
            // always drop the search
            vm.exitDiscoverSearch()
        } else {
            vm.destination = Destination.Home
        }
    }
    val darkTheme = when (vm.themeMode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
    // Default palette uses constants
    val c = remember(darkTheme, vm.colorSource, vm.paletteStyle, vm.customColorHex, vm.amoledDark) {
        val base = if (vm.colorSource == ColorSource.AppDefault && vm.paletteStyle == PaletteStyle.TonalSpot) {
            if (darkTheme) DarkKiko else LightKiko
        } else {
            themedPalette(resolveSeedColor(context, vm.colorSource, vm.customColorHex, darkTheme), vm.paletteStyle, darkTheme)
        }
        if (darkTheme && vm.amoledDark) amoledify(base) else base
    }
    SyncSystemBars(darkTheme, c.background)
    CompositionLocalProvider(LocalKikoColors provides c, LocalTitleLanguage provides vm.titleLanguage) {
        MaterialTheme(
            // Full Expressive ColorScheme generated
            // frame is already using
            // outline, inverse, error) comes
            // 7-role approximation, so MaterialTheme.colorScheme
            // LocalKikoColors.current always agree.
            colorScheme = c.toMaterialColorScheme(darkTheme),
            typography = KikoTypography,
            shapes = KikoShapes,
        ) {
            Scaffold(
                containerColor = c.background,
                bottomBar = { if (detailItem == null && characterDetailOpenId == null && personDetailOpenId == null && companyDetailOpenId == null && !rankingOpen && !recommendationsOpen && !scheduleOpen && forumTopicOpen == null && !aboutOpen && reviewOpen == null && !stacksHomeOpen && stacksBrowseKind == null && stackDetailOpen == null && mediaStacksOpen == null && clubDetailOpen == null && !profileStatsOpen && !settingsPageOpen && scoreFilterOpen == null && yearFilterOpen == null && formatFilterOpen == null && genreFilterOpen == null) BottomBar(vm.destination, onDoubleTapDiscover = { vm.openDiscoverSearch(context) }) { discoverReturnItem = null; discoverReturnDestination = null; discoverReturnStack = null; vm.destination = it } }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    val topScreen = when {
                        reviewOpen != null -> TopScreen.Review(reviewOpen!!.first, reviewOpen!!.second)
                        // A character opened from
                        // onOpenCharacter below) must show
                        // detailItem is still set
                        // explicit signal for that,
                        // Detail is opened from
                        // own Animeography/Mangaography row —
                        // never lingers past the
                        // A person opened from
                        // voice-actor row — same
                        // further out, so it
                        castPersonOnTop && personDetailOpenId != null -> TopScreen.PersonPage(personDetailOpenId!!, personDetailOpen)
                        castCharacterOnTop && characterDetailOpenId != null -> TopScreen.CharacterPage(characterDetailOpenId!!, characterDetailOpen)
                        // Company/anime/manga links tapped inside
                        // StackDetail screen — see
                        // detailOnTopOfTopicOrStack's doc comments above
                        // must be checked here,
                        // just below, rather than
                        // own plain checks further
                        castCompanyOnTop && companyDetailOpenId != null -> TopScreen.CompanyPage(companyDetailOpenId!!, companyDetailOpen)
                        detailOnTopOfTopicOrStack && detailItem != null -> TopScreen.Detail(detailItem)
                        // Checked ahead of detailItem/mediaStacksOpen
                        // from either (or from
                        // shows on top rather
                        // still underneath it —
                        // companyDetailOpenId further down.
                        stackDetailOpen != null -> TopScreen.StackDetail(stackDetailOpen!!.first, stackDetailOpen!!.second)
                        // Opened from Detail's own
                        // way castCharacterOnTop/castPersonOnTop do above,
                        // a separate "on top"
                        mediaStacksOpen != null -> TopScreen.MediaStacks(mediaStacksOpen!!)
                        // A topic opened from
                        // Discussion rows, or from
                        // onOpenNews/onOpenTopic below), must show
                        // though detailItem/companyDetailOpenId is still
                        // reasoning as castCharacterOnTop/castPersonOnTop and
                        // above. Checked before both
                        // companyDetailOpenId, where it only
                        // tap from Detail isn't
                        // matching first; forumTopicOpen ==
                        // branch is a no-op
                        forumTopicOpen != null -> TopScreen.Topic(forumTopicOpen!!.first, forumTopicOpen!!.second)
                        detailItem != null -> TopScreen.Detail(detailItem)
                        characterDetailOpenId != null -> TopScreen.CharacterPage(characterDetailOpenId!!, characterDetailOpen)
                        personDetailOpenId != null -> TopScreen.PersonPage(personDetailOpenId!!, personDetailOpen)
                        companyDetailOpenId != null -> TopScreen.CompanyPage(companyDetailOpenId!!, companyDetailOpen)
                        rankingOpen -> TopScreen.Ranking
                        recommendationsOpen -> TopScreen.Recommendations
                        scheduleOpen -> TopScreen.Schedule(scheduleInitialDay)
                        stacksBrowseKind != null -> TopScreen.StacksBrowse(stacksBrowseKind!!)
                        stacksHomeOpen -> TopScreen.StacksHome
                        clubDetailOpen != null -> TopScreen.ClubDetail(clubDetailOpen!!)
                        aboutOpen -> TopScreen.About
                        scoreFilterOpen != null -> TopScreen.ScoreFilter(scoreFilterOpen!!.first, scoreFilterOpen!!.second)
                        yearFilterOpen != null -> TopScreen.YearFilter(yearFilterOpen!!.first, yearFilterOpen!!.second)
                        formatFilterOpen != null -> TopScreen.FormatFilter(formatFilterOpen!!.first, formatFilterOpen!!.second)
                        genreFilterOpen != null -> TopScreen.GenreFilter(genreFilterOpen!!.first, genreFilterOpen!!.second)
                        profileStatsOpen -> TopScreen.ProfileStats
                        settingsPageOpen -> TopScreen.SettingsPage
                        else -> TopScreen.Tab(vm.destination)
                    }
                    AnimatedContent(
                        targetState = topScreen,
                        contentKey = { it.navKey() },
                        transitionSpec = {
                            when {
                                targetState.isFullPage() && !initialState.isFullPage() -> PushEnter togetherWith PushExit
                                !targetState.isFullPage() && initialState.isFullPage() -> PopEnter togetherWith PopExit
                                // Related/recommended hops: both sides
                                // the two branches above
                                // to a flat cross-fade
                                // related title and then
                                // abrupt "pop". Give it
                                // back) motion the rest
                                targetState is TopScreen.Detail && initialState is TopScreen.Detail ->
                                    if (detailGoingBack) PopEnter togetherWith PopExit else PushEnter togetherWith PushExit
                                else -> FadeEnter togetherWith FadeExit
                            }
                        },
                        label = "topScreen",
                    ) { screen ->
                        when (screen) {
                            is TopScreen.Detail -> DetailScreen(
                                screen.item,
                                actions = DetailScreenActions(
                                    onBack = ::backDetail,
                                    onEdit = { editor = it },
                                    onOpenRelated = { rel -> vm.openRelated(context, rel) { fetched -> openRelatedDetail(screen.item, fetched) } },
                                    onBackfillRelated = { id, type, onFound, onDone -> vm.backfillRelated(context, id, type, onFound, onDone) },
                                    onBackfillThemes = { id, type, onFound, onDone -> vm.backfillThemes(context, id, type, onFound, onDone) },
                                    onBackfillCovers = { id, type, onFound, onDone -> vm.backfillCovers(context, id, type, onFound, onDone) },
                                    onLoadRecommended = { forItem, onFound, onDone -> vm.loadUserRecommendations(context, forItem, onFound, onDone) },
                                    onOpenRecommended = { rec -> vm.openRecommended(context, rec) { fetched -> openRelatedDetail(screen.item, fetched) } },
                                    onLoadStatusDistribution = { forItem, onFound, onDone -> vm.loadStatusDistribution(context, forItem, onFound, onDone) },
                                    onOpenScoreStats = { scoreStatsOpen = it },
                                    onLoadStacks = { forItem, onFound -> vm.loadMediaStacks(forItem, onFound) },
                                    onOpenStacksList = { mediaStacksOpen = it },
                                    onOpenStack = { id, title -> stackDetailOpen = id to title },
                                    onLoadNews = { forItem, onFound, onDone -> vm.loadDetailNews(context, forItem, onFound, onDone) },
                                    onLoadForumDiscussion = { forItem, onFound, onDone -> vm.loadDetailForumDiscussion(context, forItem, onFound, onDone) },
                                    onLoadFeaturedArticles = { forItem, onFound, onDone -> vm.loadDetailFeaturedArticles(context, forItem, onFound, onDone) },
                                    onLoadLinks = { forItem, onFound, onDone -> vm.loadDetailLinks(context, forItem, onFound, onDone) },
                                    onOpenTopic = { id, title -> forumTopicOpen = id to title },
                                    onOpenFeaturedArticle = { url -> CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) },
                                    onLoadCharacters = { forItem, onFound, onDone, onError -> vm.loadCharacters(forItem, onFound, onDone, onError) },
                                    onLoadReviews = { forItem, onFound, onDone -> vm.loadReviews(forItem, onFound, onDone) },
                                    onOpenReview = { rev -> reviewOpen = rev to screen.item.title },
                                    onOpenReviewList = { url, _ -> CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) },
                                    onLeaveScroll = { index, offset -> vm.saveDetailScroll(screen.item.id, screen.item.type, index, offset) },
                                    onLeaveRelatedScroll = { index, offset -> vm.saveRelatedRowScroll(screen.item.id, screen.item.type, index, offset) },
                                    onLeaveRecommendedScroll = { index, offset -> vm.saveRecommendedRowScroll(screen.item.id, screen.item.type, index, offset) },
                                    onLeaveCharactersScroll = { index, offset -> vm.saveCharactersRowScroll(screen.item.id, screen.item.type, index, offset) },
                                    onGenreClick = { genre ->
                                        jumpToDiscover(screen.item, if (screen.item.type == MediaType.Manga) "Manga" else "Anime", DiscoverFilters(genres = setOf(genre)))
                                    },
                                    onCreatorClick = { creator ->
                                        jumpToDiscover(screen.item, if (screen.item.type == MediaType.Manga) "Manga" else "Anime", DiscoverFilters(creator = creator))
                                    },
                                    onLoadAiringEpisode = { forItem -> vm.loadAiringEpisode(forItem) },
                                    onOpenCharacter = { malId -> openCharacter(malId, castOnTop = true) },
                                    onOpenPerson = { malId -> openPerson(malId, castOnTop = true) },
                                ),
                                relatedLoadingId = vm.relatedLoadingId,
                                recommendedLoadingId = vm.recommendedLoadingId,
                                castLoadingId = vm.characterDetailLoadingId,
                                initialScroll = vm.getDetailScroll(screen.item.id, screen.item.type),
                                initialRelatedScroll = vm.getRelatedRowScroll(screen.item.id, screen.item.type),
                                initialRecommendedScroll = vm.getRecommendedRowScroll(screen.item.id, screen.item.type),
                                initialCharactersScroll = vm.getCharactersRowScroll(screen.item.id, screen.item.type),
                                cachedSnapshot = vm.peekDetailCache(screen.item.id, screen.item.type),
                                myListStatus = vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { (it to li.type) to li.status } }.toMap(),
                                airingInfo = vm.getCachedAiring(screen.item.id),
                            )
                            is TopScreen.CharacterPage -> CharacterDetailScreen(
                                screen.malId,
                                screen.character,
                                onBack = { characterDetailOpenId = null; characterDetailOpen = null; castCharacterOnTop = false; vm.forgetCharacterPage(screen.malId) },
                                onOpenWork = { malId, type -> vm.openCharacterWork(context, malId, type, ::openDetail) },
                                onOpenPerson = { malId -> openPerson(malId, castOnTop = true) },
                                workLoadingId = vm.characterWorkLoadingId,
                                myListStatus = vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { (it to li.type) to li.status } }.toMap(),
                                initialScroll = vm.getCharacterScroll(screen.malId),
                                initialAnimeScroll = vm.getCharacterAnimeScroll(screen.malId),
                                initialMangaScroll = vm.getCharacterMangaScroll(screen.malId),
                                onLeaveScroll = { index, offset -> vm.saveCharacterScroll(screen.malId, index, offset) },
                                onLeaveAnimeScroll = { index, offset -> vm.saveCharacterAnimeScroll(screen.malId, index, offset) },
                                onLeaveMangaScroll = { index, offset -> vm.saveCharacterMangaScroll(screen.malId, index, offset) },
                            )
                            is TopScreen.PersonPage -> PersonDetailScreen(
                                screen.malId,
                                screen.person,
                                onBack = { personDetailOpenId = null; personDetailOpen = null; castPersonOnTop = false; vm.forgetPersonPage(screen.malId) },
                                onOpenWork = { malId, type -> vm.openCharacterWork(context, malId, type, ::openDetail) },
                                workLoadingId = vm.characterWorkLoadingId,
                                myListStatus = vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { (it to li.type) to li.status } }.toMap(),
                                initialScroll = vm.getPersonScroll(screen.malId),
                                initialRolesScroll = vm.getPersonRolesScroll(screen.malId),
                                initialStaffScroll = vm.getPersonStaffScroll(screen.malId),
                                initialMangaScroll = vm.getPersonMangaScroll(screen.malId),
                                onLeaveScroll = { index, offset -> vm.savePersonScroll(screen.malId, index, offset) },
                                onLeaveRolesScroll = { index, offset -> vm.savePersonRolesScroll(screen.malId, index, offset) },
                                onLeaveStaffScroll = { index, offset -> vm.savePersonStaffScroll(screen.malId, index, offset) },
                                onLeaveMangaScroll = { index, offset -> vm.savePersonMangaScroll(screen.malId, index, offset) },
                            )
                            is TopScreen.CompanyPage -> CompanyDetailScreen(
                                screen.malId,
                                screen.company,
                                onBack = { companyDetailOpenId = null; companyDetailOpen = null; castCompanyOnTop = false; vm.forgetCompanyPage(screen.malId) },
                                // A company's anime grid
                                // fetch-then-openDetail helper Character/PersonPage's own
                                // work rows already share
                                onOpenWork = { malId -> vm.openCharacterWork(context, malId, MediaType.Anime, ::openDetail) },
                                // The one Recent News
                                // HomeScreen's own News snapshots
                                // news link already do.
                                onOpenNews = { id, title -> forumTopicOpen = id to title },
                                workLoadingId = vm.characterWorkLoadingId,
                                myListStatus = vm.items.mapNotNull { li -> li.id.toIntOrNull()?.takeIf { li.type == MediaType.Anime }?.let { it to li.status } }.toMap(),
                                initialScroll = vm.getCompanyScroll(screen.malId),
                                onLeaveScroll = { index, offset -> vm.saveCompanyScroll(screen.malId, index, offset) },
                            )
                            TopScreen.Ranking -> RankingScreen(vm, onBack = { rankingOpen = false }, onOpenDetail = ::openDetail)
                            TopScreen.Recommendations -> RecommendationsScreen(vm, onBack = { recommendationsOpen = false }, onOpenDetail = ::openDetail, onEdit = { editor = it }, selectedItem = editor)
                            is TopScreen.Schedule -> ScheduleScreen(vm, initialDay = screen.initialDay, onBack = { scheduleOpen = false }, onOpenDetail = ::openDetail)
                            is TopScreen.Topic -> ForumTopicScreen(vm, topicId = screen.topicId, title = screen.title, onBack = { forumTopicOpen = null }, onOpenCharacter = { malId -> openCharacter(malId, castOnTop = true) }, onOpenPerson = { malId -> openPerson(malId, castOnTop = true) }, onOpenCompany = { malId -> openCompany(malId, castOnTop = true) })
                            TopScreen.About -> AboutScreen(
                                onBack = { aboutOpen = false },
                                updateInfo = vm.updateInfo, updateChecking = vm.updateChecking, updateUpToDate = vm.updateUpToDateMessage,
                                onCheckForUpdate = { if (vm.updateInfo != null) vm.updateDialogOpen = true else vm.checkForUpdate(context, manual = true) },
                            )
                            is TopScreen.Review -> ReviewScreen(screen.review, screen.itemTitle, onBack = { reviewOpen = null })
                            // Leaving Stacks Home always
                            // section (it's the root
                            // detail entries here rather
                            // the process.
                            TopScreen.StacksHome -> StacksHomeScreen(vm, onBack = { stacksHomeOpen = false; vm.clearStackDetailCache() }, onOpenBrowse = { kind -> openStacksBrowse(kind) }, onOpenStack = { id, title -> stackDetailOpen = id to title })
                            is TopScreen.StacksBrowse -> StacksScreen(vm, initialKind = screen.initialKind, onBack = { stacksBrowseKind = null }, onOpenStack = { id, title -> stackDetailOpen = id to title })
                            // Backing out of a
                            // section when neither Stacks
                            // (i.e. this stack was
                            // otherwise the user is
                            // the flow, and the
                            is TopScreen.StackDetail -> StackDetailScreen(vm, screen.stackId, screen.title, loadingId = vm.stackEntryLoadingId, myListStatus = vm.items.mapNotNull { li -> li.id.toIntOrNull()?.let { (it to li.type) to li.status } }.toMap(), initialScroll = vm.getStackDetailScroll(screen.stackId), onLeaveScroll = { index, offset -> vm.saveStackDetailScroll(screen.stackId, index, offset) }, onBack = { stackDetailOpen = null; if (!stacksHomeOpen && stacksBrowseKind == null && mediaStacksOpen == null) vm.clearStackDetailCache() }, onOpenEntry = { entry -> vm.openStackEntry(context, entry) { fetched -> openDetail(fetched) } }, onEditEntry = { entry -> vm.openStackEntry(context, entry) { fetched -> editor = fetched } }, selectedItem = editor, onOpenCharacter = { malId -> openCharacter(malId, castOnTop = true) }, onOpenPerson = { malId -> openPerson(malId, castOnTop = true) }, onOpenCompany = { malId -> openCompany(malId, castOnTop = true) })
                            is TopScreen.MediaStacks -> MediaStacksScreen(vm = vm, item = screen.item, onBack = { mediaStacksOpen = null }, onOpenStack = { id, title -> stackDetailOpen = id to title })
                            is TopScreen.ClubDetail -> ClubDetailScreen(screen.club, onBack = { clubDetailOpen = null }, onOpenCharacter = { malId -> openCharacter(malId) }, onOpenPerson = { malId -> openPerson(malId) }, onOpenCompany = { malId -> openCompany(malId) })
                            TopScreen.ProfileStats -> ProfileStatsScreen(vm.signedIn, vm.malProfile, vm.items, onConnect = onSignIn, onBack = { profileStatsOpen = false }, scrollOffset = vm.profileScrollOffset, onSaveScroll = vm::saveProfileScroll, statsTab = vm.profileStatsTab, onStatsTabChange = vm::selectProfileStatsTab, onScoreClick = { type, score -> scoreFilterOpen = type to score }, onYearClick = { type, year -> yearFilterOpen = type to year }, onFormatClick = { type, format -> formatFilterOpen = type to format }, onGenreClick = { type, genre -> genreFilterOpen = type to genre }, onSignOut = { profileStatsOpen = false; onSignOut() }, refreshing = vm.loading || vm.profileLoading, onRefresh = { vm.load(context) })
                            is TopScreen.ScoreFilter -> ScoreFilterScreen(vm = vm, type = screen.type, initialScore = screen.score, onBack = { scoreFilterOpen = null }, onOpenDetail = ::openDetail)
                            is TopScreen.YearFilter -> YearFilterScreen(vm = vm, type = screen.type, initialYear = screen.year, onBack = { yearFilterOpen = null }, onOpenDetail = ::openDetail)
                            is TopScreen.FormatFilter -> FormatFilterScreen(vm = vm, type = screen.type, initialFormat = screen.format, onBack = { formatFilterOpen = null }, onOpenDetail = ::openDetail)
                            is TopScreen.GenreFilter -> GenreFilterScreen(vm = vm, type = screen.type, initialGenre = screen.genre, onBack = { genreFilterOpen = null }, onOpenDetail = ::openDetail)
                            TopScreen.SettingsPage -> SettingsScreen(
                                connected = vm.signedIn, themeMode = vm.themeMode, colorSource = vm.colorSource, paletteStyle = vm.paletteStyle, titleLanguage = vm.titleLanguage,
                                nsfwEnabled = vm.nsfwEnabled, onNsfwChange = { vm.setNsfw(context, it) },
                                amoledDark = vm.amoledDark, onAmoledDarkChange = { vm.setAmoledDark(context, it) },
                                onThemeClick = { themeOpen = true }, onColorClick = { colorSourceOpen = true }, onPaletteClick = { paletteStyleOpen = true }, onTitleLanguageClick = { titleLangOpen = true },
                                updateInfo = vm.updateInfo, onAboutClick = { aboutOpen = true },
                                onBack = { settingsPageOpen = false },
                            )
                            is TopScreen.Tab -> when (screen.destination) {
                                Destination.Home -> HomeScreen(vm, onOpenDetail = ::openDetail, onList = { vm.destination = Destination.List }, onLocateInList = { item -> vm.locateInList(context, item); vm.destination = Destination.List }, onDiscover = { vm.destination = Destination.Discover }, onRanking = { rankingOpen = true }, onSeasonal = { vm.destination = Destination.Seasonal }, onSchedule = ::openSchedule, onOpenTopic = { id, title -> forumTopicOpen = id to title }, onSeeNews = { vm.destination = Destination.Community; vm.selectCommunityTab(context, CommunityTab.Forums); vm.openNewsBoard(context) }, onOpenStack = { id, title -> stackDetailOpen = id to title }, onOpenStacks = ::openStacks, onOpenAnnouncements = { vm.destination = Destination.Community; vm.selectCommunityTab(context, CommunityTab.Forums); vm.openAnnouncementsBoard(context) }, onSignIn = onSignIn, onEdit = { editor = it }, selectedItem = editor)
                                Destination.List -> ListScreen(vm, onOpenDetail = ::openDetail, onIncrement = { vm.saveLive(context, it) }, onEdit = { editor = it }, selectedItem = editor)
                                Destination.Discover -> DiscoverScreen(
                                    vm,
                                    onOpenDetail = ::openDetail,
                                    onRanking = { rankingOpen = true },
                                    onSeasonal = { vm.destination = Destination.Seasonal },
                                    onStacks = ::openStacks,
                                    onRecommendations = { recommendationsOpen = true },
                                    onExitResults = {
                                        val returnItem = discoverReturnItem
                                        if (returnItem != null) { discoverReturnItem = null; selectedItem = returnItem }
                                        // Always reset the search
                                        // way out — previously
                                        // path (returnItem == null),
                                        // genre-chip detour left the
                                        // ViewModel; navigating to the
                                        // that stale search instead
                                        vm.exitDiscoverSearch()
                                    },
                                    onEdit = { editor = it },
                                    selectedItem = editor,
                                    onOpenCharacter = { malId -> openCharacter(malId) },
                                    onOpenPerson = { malId -> openPerson(malId) },
                                    onOpenCompany = { malId -> openCompany(malId) },
                                )
                                Destination.Seasonal -> SeasonalScreen(vm, onOpenDetail = ::openDetail, onEdit = { editor = it }, selectedItem = editor)
                                Destination.Community -> CommunityScreen(vm, onOpenTopic = { id, title -> forumTopicOpen = id to title }, onOpenClub = { clubDetailOpen = it })
                            }
                        }
                    }
                    vm.error?.let { msg -> ErrorDialog(msg, onDismiss = { vm.error = null }) }
                }
            }
            // Keep sheets inside theme
            editorItem?.let { EditSheet(it, onDismiss = { editor = null }, onSave = { vm.saveLive(context, it); editor = null }, onDelete = { vm.deleteLive(context, it); editor = null; if (selectedItem?.id == it.id && selectedItem?.type == it.type) { vm.clearDetailCache(); selectedItem = null; detailStack = emptyList() } }) }
            scoreStatsOpen?.let { item -> ScoreStatsSheet(item = item, onDismiss = { scoreStatsOpen = null }, onLoad = { onFound, onDone -> vm.loadScoreStats(item, onFound, onDone) }) }
            if (themeOpen) ThemeSheet(vm.themeMode, onDismiss = { themeOpen = false }, onSelect = { vm.setTheme(context, it); themeOpen = false })
            if (colorSourceOpen) ColorSourceSheet(vm.colorSource, vm.customColorHex, onDismiss = { colorSourceOpen = false }, onSelect = { vm.setColorSource(context, it) }, onCustomHexChange = { vm.setCustomColor(context, it) })
            if (paletteStyleOpen) PaletteStyleSheet(vm.paletteStyle, onDismiss = { paletteStyleOpen = false }, onSelect = { vm.setPaletteStyle(context, it); paletteStyleOpen = false })
            if (titleLangOpen) TitleLanguageSheet(vm.titleLanguage, onDismiss = { titleLangOpen = false }, onSelect = { vm.setTitleLanguage(context, it); titleLangOpen = false })
            if (vm.updateDialogOpen) vm.updateInfo?.let { info ->
                UpdateDialog(
                    info = info,
                    downloadProgress = vm.updateDownloadProgress,
                    needsInstallPermission = vm.updateNeedsInstallPermission,
                    error = vm.updateError,
                    onDownload = { vm.downloadAndInstallUpdate(context) },
                    onOpenInstallSettings = { vm.updateNeedsInstallPermission = false; context.startActivity(AppUpdateChecker(context).installPermissionSettingsIntent()) },
                    onSkip = { vm.skipUpdate(context) },
                    onDismiss = { vm.updateDialogOpen = false; vm.updateNeedsInstallPermission = false; vm.updateError = null },
                )
            }
            if (vm.profileDrawerOpen) {
                AvatarMenu(
                    connected = vm.signedIn, profile = vm.malProfile, anchor = vm.profileMenuAnchor,
                    onOpenProfile = { profileStatsOpen = true },
                    onOpenSettings = { settingsPageOpen = true },
                    onDismiss = { vm.profileDrawerOpen = false; vm.profileMenuAnchor = null },
                )
            }
        }
    }
}

// Shared pieces section