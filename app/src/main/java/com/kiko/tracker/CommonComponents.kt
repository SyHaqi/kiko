@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable fun BottomBar(selected: Destination, select: (Destination) -> Unit) { val c = LocalKikoColors.current; NavigationBar(containerColor = c.surface, tonalElevation = 4.dp) { Destination.entries.forEach { d -> NavigationBarItem(selected = d == selected, onClick = { select(d) }, icon = { Icon(d.icon, null) }, label = { Text(d.label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = c.accent, selectedTextColor = c.accent, unselectedIconColor = c.muted, unselectedTextColor = c.muted, indicatorColor = c.primaryContainer)) } } }

@Composable fun AppHeader(title: String, horizontalPadding: Dp = 20.dp, titleColor: Color = LocalKikoColors.current.ink, action: @Composable () -> Unit = {}) { Row(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-1).sp, color = titleColor); action() } }

// Unused params kept intentionally

// Shared shimmer clock for skeleton placeholders. Each SkeletonBlock used to call
// rememberInfiniteTransition() + two animateFloat()s ITSELF — harmless with one or two
// on screen, but a loading grid/list routinely shows 9 skeleton cards × 2-3 blocks each
// (cover + title + subtitle), i.e. 20-40+ fully independent Choreographer-driven
// animations all repainting every ~16ms at once, competing with the actual network/parse
// work that's happening at the exact same moment — which is exactly when a "hiccup" is
// most noticeable. Hoisting ONE transition per screen (via SkeletonPhaseProvider, wrapped
// once around KikoApp) and having every SkeletonBlock just read its current value cuts
// that down to a single animation regardless of how many placeholders are on screen, with
// no visible difference since they were always meant to pulse in lockstep anyway.
@Immutable
data class SkeletonPhase(val alpha: Float, val sweep: Float)

// compositionLocalOf (not staticCompositionLocalOf): alpha/sweep change every frame
// forever via the infinite transition below, and this is provided once around the
// whole KikoApp tree in MainActivity. staticCompositionLocalOf can't do targeted
// invalidation -- on every value change it force-recomposes the ENTIRE content lambda
// under the CompositionLocalProvider, i.e. the whole app, every single frame, on every
// screen, whether or not a skeleton is even visible. compositionLocalOf uses the
// snapshot system so only the actual .current readers (SkeletonBlock) recompose.
val LocalSkeletonPhase = compositionLocalOf<SkeletonPhase?> { null }

@Composable
fun SkeletonPhaseProvider(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val sweep by transition.animateFloat(
        initialValue = -0.4f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "skeletonSweep",
    )
    CompositionLocalProvider(LocalSkeletonPhase provides SkeletonPhase(alpha, sweep), content = content)
}

// Base "breathing" fill, plus a diagonal highlight band that sweeps across every
// skeleton on screen in lockstep. Reads the shared clock from SkeletonPhaseProvider;
// falls back to a static (non-animated) mid-value if used outside one, rather than
// spinning up its own duplicate animation.
@Composable fun SkeletonBlock(modifier: Modifier, shape: Shape = RoundedCornerShape(kikoCorner(12.dp))) {
    val c = LocalKikoColors.current
    val phase = LocalSkeletonPhase.current
    val alpha = phase?.alpha ?: 0.45f
    val sweep = phase?.sweep ?: 0.5f
    Box(
        modifier
            .clip(shape)
            .background(c.surfaceLow.copy(alpha = alpha))
            .drawWithContent {
                drawContent()
                val bandWidth = size.width * 0.5f
                val x = sweep * (size.width + bandWidth)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, c.ink.copy(alpha = .08f), Color.Transparent),
                        start = Offset(x - bandWidth, 0f),
                        end = Offset(x, size.height),
                    ),
                )
            },
    )
}
// Continue card placeholder

@Composable fun MiniCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    Column(Modifier.width(118.dp).kikoClickable { onOpenDetail(item) }) {
        Cover(item, Modifier.fillMaxWidth().height(150.dp), showStatus = true)
        Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(if (item.status == WatchStatus.Plan) "Saved for later" else progressLabel(item), color = c.accent, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    }
}

@Composable fun Cover(item: MediaItem, modifier: Modifier = Modifier, showStatus: Boolean = false, statusAlignment: Alignment = Alignment.TopStart, overrideStatus: WatchStatus? = null, showRating: Boolean = false, selected: Boolean = false) {
    val c = LocalKikoColors.current
    val displayTitle = item.displayTitle()
    Box(modifier.clip(RoundedCornerShape(kikoCorner(16.dp))).background(Color(item.color)), contentAlignment = Alignment.Center) {
        if (item.cover.isNotBlank()) {
            AsyncImage(
                model = item.cover,
                contentDescription = displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        else Text(displayTitle.take(1), fontWeight = FontWeight.Bold, fontSize = 36.sp, color = Color.White.copy(.85f))
        // Optional tracking mark — overrideStatus lets callers supply the real list status for
        // items that weren't sourced from the user's own list (item.inUserList would be false)
        if (showStatus) (overrideStatus ?: trackedBadgeStatus(item))?.let { CoverStatusMark(it, Modifier.align(statusAlignment).padding(6.dp)) }
        // User's own score, bottom-right so it never collides with the status mark
        if (showRating && item.myRating > 0) CoverRatingMark(item.myRating, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        // Long-press selection — tint the whole cover and drop a checkmark, top-right
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
        ) {
            Box(Modifier.fillMaxSize().background(c.primary.copy(alpha = .32f)))
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .6f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = .6f),
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        ) {
            Box(
                Modifier.size(22.dp).clip(kikoCircleShape()).background(c.primary).border(1.5.dp, Color.White.copy(alpha = .9f), kikoCircleShape()),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Check, "Selected", tint = c.onPrimary, modifier = Modifier.size(13.dp)) }
        }
    }
}
// All 5 states shown

fun trackedBadgeStatus(item: MediaItem): WatchStatus? =
    if (item.inUserList) item.status else null
// Icon per tracking status

fun WatchStatus.badgeIcon(): ImageVector = when (this) {
    WatchStatus.Watching, WatchStatus.Reading -> Icons.Default.PlayArrow
    WatchStatus.Completed -> Icons.Default.Check
    WatchStatus.OnHold -> Icons.Default.Pause
    WatchStatus.Dropped -> Icons.Default.Close
    WatchStatus.Plan -> Icons.Default.Bookmark
}
// Small color coded dot

@Composable fun CoverStatusMark(status: WatchStatus, modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(kikoCircleShape()).background(statusColor(status)).border(1.5.dp, Color.White.copy(alpha = .9f), kikoCircleShape()),
        contentAlignment = Alignment.Center,
    ) { Icon(status.badgeIcon(), status.label, tint = Color.White, modifier = Modifier.size(13.dp)) }
}
// User's score, pinned to a cover corner — dark pill so it reads on any artwork

@Composable fun CoverRatingMark(rating: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(kikoCorner(7.dp)))
            .background(Color.Black.copy(alpha = .6f))
            .border(1.dp, Color.White.copy(alpha = .9f), RoundedCornerShape(kikoCorner(7.dp)))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
        Text(rating.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
    }
}

@Composable fun TypeToggle(current: MediaType, trackColor: Color = LocalKikoColors.current.surface, set: (MediaType) -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(trackColor).padding(4.dp)) {
        MediaType.entries.forEach { t ->
            val selected = current == t
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(kikoCorner(12.dp))).background(if (selected) c.primary else Color.Transparent).kikoClickable { set(t) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (t == MediaType.Anime) "Anime" else "Manga", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (selected) c.onPrimary else c.muted)
            }
        }
    }
}

// Header for screens that switch between Anime/Manga (e.g. My list) — replaces the separate
// title + full-width TypeToggle pill with a single tappable title that opens a small dropdown,
// saving vertical space while still making it obvious the section is switchable. Generic over
// any small option set (MediaType for the list screen, CommunityTab for Forums/Clubs, ...) so
// every "tap the big title to switch section" header in the app shares one implementation.
@Composable fun <T> SwitcherHeader(current: T, options: List<T>, labelFor: (T) -> String, onSelect: (T) -> Unit, horizontalPadding: Dp = 20.dp, switchDescription: String = "Switch section", action: @Composable () -> Unit = {}) {
    val c = LocalKikoColors.current
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    // Anchor's measured width, so the menu below can match it exactly instead of
    // shrink-wrapping to its own (different) content and reading as a mismatched size.
    var anchorWidthPx by remember { mutableStateOf(0) }
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "switcherArrowRotation")
    Row(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Box {
            Row(
                Modifier
                    .onGloballyPositioned { anchorWidthPx = it.size.width }
                    .clip(RoundedCornerShape(kikoCorner(4.dp)))
                    .kikoClickable { expanded = true }
                    .padding(start = 0.dp, top = 0.dp, end = 6.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(labelFor(current), fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-1).sp, color = c.ink)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = switchDescription, tint = c.muted, modifier = Modifier.padding(start = 2.dp).size(28.dp).rotate(arrowRotation))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = c.surface,
                shape = RoundedCornerShape(kikoCorner(18.dp)),
                modifier = Modifier.width(with(density) { anchorWidthPx.toDp() }),
            ) {
                options.forEach { opt ->
                    val selected = opt == current
                    DropdownMenuItem(
                        text = { Text(labelFor(opt), fontWeight = FontWeight.Bold, color = if (selected) c.accent else c.ink) },
                        // Selected row gets a filled background plus accent text — that's
                        // enough to read as "active" on its own, no separate checkmark needed.
                        modifier = if (selected) Modifier.background(c.primaryContainer) else Modifier,
                        onClick = { expanded = false; onSelect(opt) },
                    )
                }
            }
        }
        action()
    }
}

@Composable fun TypeSwitcherHeader(current: MediaType, onSelect: (MediaType) -> Unit, horizontalPadding: Dp = 20.dp, action: @Composable () -> Unit = {}) =
    SwitcherHeader(current, MediaType.entries.toList(), { if (it == MediaType.Anime) "Anime" else "Manga" }, onSelect, horizontalPadding, "Switch between Anime and Manga", action)

@Composable fun SearchField(value: String, change: (String) -> Unit, hint: String, onSearch: (() -> Unit)? = null, onClear: (() -> Unit)? = null) {
    val c = LocalKikoColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = change, placeholder = { Text(hint, color = c.muted) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = c.muted) },
        trailingIcon = {
            // Show clear when non-empty
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = {
                        (onClear ?: { change("") })()
                        // Clear field, drop focus
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
                    modifier = Modifier.size(32.dp),
                ) { Icon(Icons.Default.Close, "Clear search", tint = c.muted, modifier = Modifier.size(16.dp)) }
            }
        },
        singleLine = true, shape = RoundedCornerShape(kikoCorner(18.dp)),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.cardBorder, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface, focusedTextColor = c.ink, unfocusedTextColor = c.ink),
        keyboardOptions = KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Default),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(); keyboard?.hide() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

// Google-style "search this" suggestions shown under the search bar as the user
// types. Plain title rows only (no thumbnails/detail lookup) — tapping one just
// fills the search bar with that title and runs the search, same as typing it in.
@Composable fun SearchSuggestionsList(suggestions: List<String>, onSelect: (String) -> Unit) {
    val c = LocalKikoColors.current
    if (suggestions.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(kikoCorner(18.dp))),
    ) {
        suggestions.forEachIndexed { index, title ->
            Row(
                Modifier.fillMaxWidth().kikoClickable { onSelect(title) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = c.muted, modifier = Modifier.size(16.dp))
                Text(title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp).weight(1f))
            }
            if (index < suggestions.lastIndex) HorizontalDivider(thickness = 1.dp, color = c.cardBorder, modifier = Modifier.padding(start = 46.dp))
        }
    }
}

// Floats the suggestion list over the rest of the screen, anchored directly under the
// search row. Also lays two invisible scrims (above and below the anchor) that dismiss
// the suggestions and drop keyboard focus when tapped, without intercepting taps meant
// for the search row itself (which sits in the untouched gap between the two scrims).
// `anchorBounds`/`containerBounds` are captured via Modifier.onGloballyPositioned on the
// search row and the screen's outer Box, respectively — both in the same root coordinate
// space, so they stay correctly aligned even while the list underneath is scrolling.
@Composable fun BoxScope.FloatingSearchSuggestions(
    anchorBounds: Rect?,
    containerBounds: Rect?,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    if (suggestions.isEmpty() || anchorBounds == null || containerBounds == null) return
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismiss: () -> Unit = { focusManager.clearFocus(); keyboard?.hide(); onDismiss() }

    val relTop = anchorBounds.top - containerBounds.top
    val relBottom = anchorBounds.bottom - containerBounds.top
    val relLeft = anchorBounds.left - containerBounds.left
    val widthPx = anchorBounds.width
    val remainingHeightPx = (containerBounds.height - relBottom).coerceAtLeast(0f)

    // Scrim above the anchor (e.g. the header/title sitting above the search bar)
    if (relTop > 0f) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { relTop.toDp() })
                .pointerInput(Unit) { detectTapGestures { dismiss() } }
        )
    }
    // Scrim below the anchor, covering the rest of the screen
    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { remainingHeightPx.toDp() })
            .offset { IntOffset(0, relBottom.roundToInt()) }
            .pointerInput(Unit) { detectTapGestures { dismiss() } }
    )
    // The floating suggestion list itself, drawn on top of the scrim above so its own
    // taps register as selections rather than dismissals
    Box(
        Modifier
            .offset { IntOffset(relLeft.roundToInt(), (relBottom + with(density) { 8.dp.toPx() }).roundToInt()) }
            .width(with(density) { widthPx.toDp() })
            .shadow(10.dp, RoundedCornerShape(kikoCorner(18.dp))),
    ) {
        SearchSuggestionsList(suggestions) { picked -> dismiss(); onSelect(picked) }
    }
}

// Standard FilterChip color scheme used by every filter/category chip row in the app
// (Home, Discover, Forums, Ranking, Seasonal, Stacks, Profile, Detail). Pulled out so the
// scheme lives in one place instead of being copy-pasted at every call site.
@Composable fun kikoFilterChipColors(): SelectableChipColors {
    val c = LocalKikoColors.current
    return FilterChipDefaults.filterChipColors(containerColor = c.surface, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary)
}

// Re-centers a scrollable chip row on the tapped chip so neighboring categories peek into
// view, in one continuous motion (no snap-then-correct jump). Only meant for chip rows that
// can actually overflow the screen — small fixed rows (e.g. a 2-option toggle) don't need it.
// Uses an explicit eased tween rather than animateScrollBy's default spring, which is stiff
// enough to read as an abrupt snap rather than a glide, especially over the short distances
// most chip taps cover.
private val chipCenterEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

fun CoroutineScope.centerChip(listState: LazyListState, index: Int) {
    launch {
        val info = listState.layoutInfo
        val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@launch
        val viewportCenter = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) / 2
        val itemCenter = itemInfo.offset + itemInfo.size / 2
        val distance = (itemCenter - viewportCenter).toFloat()
        // Scale duration with distance so a neighboring chip glides quickly while a far-off
        // one gets a longer, still-smooth travel time.
        val duration = (abs(distance) / 2.2f).roundToInt().coerceIn(220, 420)
        listState.animateScrollBy(distance, animationSpec = tween(durationMillis = duration, easing = chipCenterEasing))
    }
}

fun statusColor(status: WatchStatus): Color = when (status) {
    WatchStatus.Watching, WatchStatus.Reading -> StatusWatchingColor
    WatchStatus.Completed -> StatusCompletedColor
    WatchStatus.OnHold -> StatusOnHoldColor
    WatchStatus.Dropped -> StatusDroppedColor
    WatchStatus.Plan -> StatusPlanColor
}
// Status color by label

fun statusColor(label: String): Color = when {
    label.startsWith("Watch", true) || label.startsWith("Read", true) -> StatusWatchingColor
    label.startsWith("Complet", true) -> StatusCompletedColor
    label.contains("hold", true) -> StatusOnHoldColor
    label.startsWith("Drop", true) -> StatusDroppedColor
    else -> StatusPlanColor // Plan to watch
}
// Fallback avatar tile. Reports its own true on-screen bounds to onClick so callers can
// anchor a popup — like AvatarMenu — directly under it, even though that popup renders
// in its own Dialog window elsewhere in the tree (see Navigation's profileMenuAnchor).
// positionOnScreen() (absolute screen coordinates) is used rather than boundsInWindow()
// (coordinates relative to *this* composable's own window) because AvatarMenu's Dialog is
// a separate Android window with its own origin — window-relative coordinates from the
// main activity window don't line up inside it, which is what caused the redrawn avatar
// to appear offset from the real one instead of directly on top of it.

@Composable fun Avatar(picture: String = "", name: String = "", onClick: ((Rect) -> Unit)? = null) {
    val c = LocalKikoColors.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val posMod = Modifier.onGloballyPositioned { val pos = it.positionOnScreen(); bounds = Rect(pos.x, pos.y, pos.x + it.size.width, pos.y + it.size.height) }
    val tapMod = if (onClick != null) Modifier.kikoClickable { onClick(bounds) } else Modifier
    if (picture.isNotBlank()) {
        AsyncImage(model = picture, contentDescription = "Profile picture", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(43.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.warm).then(posMod).then(tapMod))
    } else {
        Box(Modifier.size(43.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(c.warm).then(posMod).then(tapMod), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase().ifBlank { "M" }, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = c.ink) }
    }
}

// Detail section

@Composable fun Pill(text: String, container: Color, content: Color) {
    Box(Modifier.clip(kikoPillShape()).background(container).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = content, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
// Outline style genre chip

@Composable fun GenreChip(text: String, onClick: (() -> Unit)? = null) {
    val c = LocalKikoColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(kikoCorner(10.dp)))
            .border(1.dp, c.muted.copy(alpha = .35f), RoundedCornerShape(kikoCorner(10.dp)))
            .let { if (onClick != null) it.kikoClickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = c.ink, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    }
}

@Composable fun StatBlock(modifier: Modifier, value: String, label: String) {
    val c = LocalKikoColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(label, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
// Uniform shared card shell