package com.kiko.tracker.ui.screens

import com.kiko.tracker.ui.components.KikoDropdownMenu
import com.kiko.tracker.ui.components.KikoHeader
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.ui.components.FavoriteHeartButton
import com.kiko.tracker.ui.components.headerEdgeEnd
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.util.openInBrowser

// Header shared by the Person, Character and Company pages so they match the anime/manga
// DetailScreen: eyebrow label + title, then the cover with one tonal "fact" row per line beside it.

/** One row beside the cover: icon, icon tint, text. */
data class DetailFact(val icon: ImageVector, val tint: Color, val text: String)

/** "1 voice role" / "5 voice roles" — [plural] defaults to singular + "s". */
fun countLabel(n: Int, singular: String, plural: String = singular + "s"): String = "$n ${if (n == 1) singular else plural}"

/**
 * Shared pinned top bar for every sub-page: the same Material 3 small top app bar the anime/manga detail page
 * uses. It sits on the page background, and [progress] (0..1) fades the title in once the page's own big title
 * has scrolled out of view. Pass `{ 1f }` for pages that have no big title of their own (the title is then
 * always shown). The screen host already applies the status-bar inset above this page, so none is added here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KikoTopBar(
    title: String,
    onBack: () -> Unit,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    backDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalKikoColors.current
    val p = progress()
    TopAppBar(
        modifier = modifier,
        windowInsets = WindowInsets(top = 0.dp),
        title = {
            Text(
                title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
            )
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, backDescription) } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = c.background,
            scrolledContainerColor = c.background,
            navigationIconContentColor = c.ink,
            titleContentColor = c.ink.copy(alpha = p),
            actionIconContentColor = c.ink,
        ),
    )
}

/**
 * 0 while the page's big title is still on screen -> 1 once it has scrolled away, for a [KikoTopBar] over a
 * LazyColumn whose first item holds the title. Read it inside the bar's `progress` lambda so scrolling only
 * recomposes the bar, not the whole screen.
 */
@Composable
fun rememberTitleProgress(listState: LazyListState, startOffset: Dp = 20.dp, fadeRange: Dp = 32.dp): State<Float> {
    val density = LocalDensity.current
    val startPx = with(density) { startOffset.toPx() }
    val fadePx = with(density) { fadeRange.toPx() }
    return remember(listState, startPx, fadePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else ((listState.firstVisibleItemScrollOffset - startPx) / fadePx).coerceIn(0f, 1f)
        }
    }
}

/** Same as above for a page that scrolls with a plain `verticalScroll(scrollState)` column. */
@Composable
fun rememberTitleProgress(scrollState: ScrollState, startOffset: Dp = 20.dp, fadeRange: Dp = 32.dp): State<Float> {
    val density = LocalDensity.current
    val startPx = with(density) { startOffset.toPx() }
    val fadePx = with(density) { fadeRange.toPx() }
    return remember(scrollState, startPx, fadePx) {
        derivedStateOf { ((scrollState.value - startPx) / fadePx).coerceIn(0f, 1f) }
    }
}

/** ⋮ button with Share + Open in browser, plus any [extraItems] (they get a `dismiss` callback). */
@Composable
fun ShareMoreMenu(title: String, shareUrl: String, extraItems: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = {}) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    var moreOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { moreOpen = true }) { Icon(Icons.Rounded.MoreVert, "More options") }
        KikoDropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }, shape = RoundedCornerShape(kikoCorner(18.dp)), containerColor = c.surfaceContainer) {
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                onClick = {
                    moreOpen = false
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, title))
                },
            )
            DropdownMenuItem(
                text = { Text("Open in browser") },
                leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) },
                onClick = { moreOpen = false; context.openInBrowser(shareUrl) },
            )
            extraItems { moreOpen = false }
        }
    }
}

/**
 * The back / favorite / ⋮ bar of [DetailHeader] as its own composable, so a page can pin it above its scrolling
 * content (pass `showTopRow = false` to [DetailHeader] then). [progress] fades the title in as in [KikoTopBar].
 */
@Composable
fun DetailTopRow(
    title: String,
    shareUrl: String,
    favorited: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    progress: () -> Float = { 0f },
) {
    KikoTopBar(title = title, onBack = onBack, progress = progress) {
        FavoriteHeartButton(favorited = favorited, onClick = onToggleFavorite, size = 48.dp, iconSize = 24.dp, background = Color.Transparent)
        ShareMoreMenu(title = title, shareUrl = shareUrl)
    }
}

/**
 * Plain page header (no banner/backdrop): back / favorite / ⋮ buttons in the top row, then the [eyebrow] label
 * and [title] on the normal page background. Tap the title to see it in full when it's cut off (3-line cap),
 * long-press to copy it.
 */
@Composable
fun DetailHeader(
    eyebrow: String,
    title: String,
    shareUrl: String,
    favorited: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    showTopRow: Boolean = true,
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    var showFullTitle by remember(title) { mutableStateOf(false) }
    var titleOverflows by remember(title) { mutableStateOf(false) }
    if (showTopRow) DetailTopRow(title = title, shareUrl = shareUrl, favorited = favorited, onBack = onBack, onToggleFavorite = onToggleFavorite)
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Text(eyebrow, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
        Text(
            title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            onTextLayout = { titleOverflows = it.hasVisualOverflow },
            modifier = Modifier.padding(top = 4.dp).pointerInput(titleOverflows, title) {
                detectTapGestures(
                    onTap = { if (titleOverflows) showFullTitle = true },
                    onLongPress = { copyTitleToClipboard(context, title) },
                )
            },
        )
    }
    if (showFullTitle) {
        AlertDialog(
            onDismissRequest = { showFullTitle = false },
            containerColor = c.surfaceContainerHigh,
            title = { Text("Full title", color = c.ink) },
            text = { Text(title, color = c.ink, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showFullTitle = false }, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("Close") } },
            dismissButton = { TextButton(onClick = { copyTitleToClipboard(context, title) }, colors = ButtonDefaults.textButtonColors(contentColor = c.muted)) { Text("Copy") } },
        )
    }
}

/**
 * Cover on the left, one tonal row per [facts] entry on the right. The rows column is exactly as tall as the
 * cover and the rows split that height evenly. [coverModifier] adds background/click behavior to the cover box;
 * [cover] draws its content.
 */
@Composable
fun DetailCoverRow(
    coverWidth: Dp,
    coverHeight: Dp,
    coverCorner: Dp,
    facts: List<DetailFact>,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(kikoCorner(coverCorner))
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(width = coverWidth, height = coverHeight).shadow(6.dp, shape).clip(shape).then(coverModifier), content = cover)
        Column(Modifier.weight(1f).height(coverHeight), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            facts.forEachIndexed { i, f -> DetailInfoRow(i, facts.size, f.icon, f.tint, f.text) }
        }
    }
}

/** "Details" label/value pairs as the same grouped tonal rows the anime/manga Info tab uses. */
@Composable
fun DetailFieldRows(fields: List<Pair<String, String>>) {
    val c = LocalKikoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        fields.forEachIndexed { i, (label, value) ->
            DetailGroupRow(i, fields.size) {
                Text(label, color = c.muted, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(start = 16.dp), horizontalArrangement = Arrangement.End) {
                    Text(value, color = c.ink, fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}