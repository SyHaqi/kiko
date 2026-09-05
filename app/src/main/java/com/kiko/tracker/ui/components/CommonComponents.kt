@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
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
import com.kiko.tracker.data.model.Destination
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.displayTitle
import com.kiko.tracker.ui.screens.StatusCompletedColor
import com.kiko.tracker.ui.screens.StatusDroppedColor
import com.kiko.tracker.ui.screens.StatusOnHoldColor
import com.kiko.tracker.ui.screens.StatusPlanColor
import com.kiko.tracker.ui.screens.StatusWatchingColor
import com.kiko.tracker.ui.screens.progressLabel
import com.kiko.tracker.ui.theme.KikoMotion
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.accent
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape

// Expressive nav bar: surfaceContainer
// elevated layer per the
// indicator (the M3 default
// which this app reserves
// selected icon/label rather than
@Composable fun BottomBar(selected: Destination, onDoubleTapDiscover: () -> Unit = {}, select: (Destination) -> Unit) {
    val c = LocalKikoColors.current
    // NavigationBarItem has no built-in
    // destination + its timestamp
    // the window counts as
    // always runs either way.
    var lastTapDestination by remember { mutableStateOf<Destination?>(null) }
    var lastTapTime by remember { mutableStateOf(0L) }
    NavigationBar(containerColor = c.surfaceContainer, tonalElevation = 0.dp) {
        Destination.entries.forEach { d ->
            NavigationBarItem(
                selected = d == selected,
                onClick = {
                    val now = System.currentTimeMillis()
                    if (d == Destination.Discover && lastTapDestination == d && now - lastTapTime < 300) onDoubleTapDiscover()
                    lastTapDestination = d
                    lastTapTime = now
                    select(d)
                },
                icon = { Icon(d.icon, null) },
                label = { Text(d.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.onSecondaryContainer, selectedTextColor = c.onSecondaryContainer,
                    unselectedIconColor = c.muted, unselectedTextColor = c.muted,
                    indicatorColor = c.secondaryContainer,
                ),
            )
        }
    }
}

@Composable fun AppHeader(title: String, horizontalPadding: Dp = 20.dp, titleColor: Color = LocalKikoColors.current.ink, action: @Composable () -> Unit = {}) { Row(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(title, style = MaterialTheme.typography.headlineLarge, letterSpacing = (-1).sp, color = titleColor); action() } }

// Unused params kept intentionally

// Shared shimmer clock for
// rememberInfiniteTransition() + two animateFloat()s
// on screen, but a
// (cover + title +
// animations all repainting every
// work that's happening at
// most noticeable. Hoisting ONE
// once around KikoApp) and
// that down to a
// no visible difference since
@Immutable
data class SkeletonPhase(val alpha: Float, val sweep: Float)

// compositionLocalOf (not staticCompositionLocalOf): alpha/sweep
// forever via the infinite
// whole KikoApp tree in
// invalidation -- on every
// under the CompositionLocalProvider, i.e.
// screen, whether or not
// snapshot system so only
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

// Base "breathing" fill, plus
// skeleton on screen in
// falls back to a
// spinning up its own
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
        // Optional tracking mark —
        // items that weren't sourced
        if (showStatus) (overrideStatus ?: trackedBadgeStatus(item))?.let { CoverStatusMark(it, Modifier.align(statusAlignment).padding(6.dp)) }
        // User's own score, bottom-right
        if (showRating && item.myRating > 0) CoverRatingMark(item.myRating, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        // Long-press selection — tint
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
// User's score, pinned to

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

@Composable fun TypeToggle(current: MediaType, trackColor: Color = LocalKikoColors.current.surfaceContainerHigh, set: (MediaType) -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(trackColor).padding(4.dp)) {
        MediaType.entries.forEach { t ->
            val selected = current == t
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(kikoCorner(12.dp))).background(if (selected) c.secondaryContainer else Color.Transparent).kikoClickable { set(t) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (t == MediaType.Anime) "Anime" else "Manga", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (selected) c.onSecondaryContainer else c.muted)
            }
        }
    }
}

// Header for screens that
// title + full-width TypeToggle
// saving vertical space while
// any small option set
// every "tap the big
@Composable fun <T> SwitcherHeader(current: T, options: List<T>, labelFor: (T) -> String, onSelect: (T) -> Unit, horizontalPadding: Dp = 20.dp, switchDescription: String = "Switch section", action: @Composable () -> Unit = {}) {
    val c = LocalKikoColors.current
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    // Anchor's measured width, so
    // shrink-wrapping to its own
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
                Text(labelFor(current), style = MaterialTheme.typography.headlineLarge, letterSpacing = (-1).sp, color = c.ink)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = switchDescription, tint = c.muted, modifier = Modifier.padding(start = 2.dp).size(28.dp).rotate(arrowRotation))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = c.surfaceContainerHigh,
                shape = RoundedCornerShape(kikoCorner(18.dp)),
                modifier = Modifier.width(with(density) { anchorWidthPx.toDp() }),
            ) {
                options.forEach { opt ->
                    val selected = opt == current
                    DropdownMenuItem(
                        text = { Text(labelFor(opt), fontWeight = FontWeight.Bold, color = if (selected) c.accent else c.ink) },
                        // Selected row gets a
                        // enough to read as
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

// Header for any screen
// Forums/Clubs on Community, ...)
// rounded-square style) sitting just
// field out from the
// point, not sliding in
// out underneath. The field
// so opening it never
// same reason SwitcherHeader is
@Composable fun <T> ExpandableSearchHeader(
    current: T,
    options: List<T>,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    hint: String = "Search",
    horizontalPadding: Dp = 20.dp,
    switchDescription: String = "Switch section",
    avatar: @Composable () -> Unit,
) {
    val c = LocalKikoColors.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = expanded) { onExpandedChange(false) }

    // Autofocus + pop the
    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            focusManager.clearFocus()
        }
    }

    // Root-coordinate bounds of the
    // work out what fraction
    // icon's actual position, not
    var containerBounds by remember { mutableStateOf<Rect?>(null) }
    var iconBounds by remember { mutableStateOf<Rect?>(null) }
    val pivotFraction = remember(containerBounds, iconBounds) {
        val cb = containerBounds; val ib = iconBounds
        if (cb == null || ib == null || cb.width <= 0f) 1f
        else (((ib.left + ib.right) / 2f - cb.left) / cb.width).coerceIn(0f, 1f)
    }
    // Uses the critically-damped "effects"
    // this is a scale/reveal,
    val progress by animateFloatAsState(if (expanded) 1f else 0f, animationSpec = KikoMotion.effectsDefault(), label = "searchExpandProgress")

    Box(Modifier.fillMaxWidth().onGloballyPositioned { containerBounds = it.boundsInRoot() }) {
        // Title switcher + pill
        // Only composed while not
        if (progress < 1f) {
            Box(Modifier.graphicsLayer { alpha = 1f - progress }) {
                SwitcherHeader(current, options, labelFor, onSelect, horizontalPadding, switchDescription) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .size(43.dp)
                                .onGloballyPositioned { iconBounds = it.boundsInRoot() }
                                .clip(RoundedCornerShape(kikoCorner(16.dp)))
                                .background(c.surfaceContainerHigh)
                                .kikoClickable { onExpandedChange(true) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Search, hint, tint = c.ink) }
                        avatar()
                    }
                }
            }
        }

        // Search field — scales
        // Mounted as soon as
        // its focusRequester is attached
        // to call requestFocus() on
        // and crash with "FocusRequester
        if (expanded || progress > 0f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = progress
                        scaleX = progress.coerceAtLeast(0.0001f)
                        transformOrigin = TransformOrigin(pivotFraction, 0.5f)
                    },
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeaderSearchField(
                        value = query,
                        onValueChange = onQueryChange,
                        hint = hint,
                        onSearch = onSearch,
                        onBack = { onExpandedChange(false) },
                        onClear = onClear,
                        focusRequester = focusRequester,
                    )
                }
            }
        }
    }
}

// Slim pill search field
// height as the icon/avatar
// (~56dp), so expanding the
@Composable fun HeaderSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
) {
    val c = LocalKikoColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        Modifier.fillMaxWidth().height(43.dp).clip(kikoPillShape()).background(c.surfaceContainerHigh).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ArrowBack, "Close search", tint = c.muted, modifier = Modifier.size(18.dp)) }
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, color = c.muted, fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = c.ink, fontSize = 14.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(); keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Clear search", tint = c.muted, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable fun SearchField(value: String, change: (String) -> Unit, hint: String, onSearch: (() -> Unit)? = null, onClear: (() -> Unit)? = null, focusRequester: androidx.compose.ui.focus.FocusRequester? = null) {
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
        singleLine = true, shape = kikoPillShape(),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, unfocusedContainerColor = c.surfaceContainerHigh, focusedContainerColor = c.surfaceContainerHigh, focusedTextColor = c.ink, unfocusedTextColor = c.ink),
        keyboardOptions = KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Default),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(); focusManager.clearFocus(); keyboard?.hide() }),
        modifier = if (focusRequester != null) Modifier.fillMaxWidth().focusRequester(focusRequester) else Modifier.fillMaxWidth(),
    )
}

// Google-style "search this" suggestions
// types. Plain title rows
// fills the search bar
@Composable fun SearchSuggestionsList(suggestions: List<String>, onSelect: (String) -> Unit) {
    val c = LocalKikoColors.current
    if (suggestions.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.surfaceContainerHigh),
    ) {
        suggestions.forEachIndexed { index, title ->
            Row(
                Modifier.fillMaxWidth().kikoClickable { onSelect(title) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = c.muted, modifier = Modifier.size(16.dp))
                Text(title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp).weight(1f))
            }
            if (index < suggestions.lastIndex) HorizontalDivider(thickness = 1.dp, color = c.outlineVariant, modifier = Modifier.padding(start = 46.dp))
        }
    }
}

// Floats the suggestion list
// search row. Also lays
// the suggestions and drop
// for the search row
// `anchorBounds`/`containerBounds` are captured via
// search row and the
// space, so they stay
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

    // Scrim above the anchor
    if (relTop > 0f) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { relTop.toDp() })
                .pointerInput(Unit) { detectTapGestures { dismiss() } }
        )
    }
    // Scrim below the anchor,
    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { remainingHeightPx.toDp() })
            .offset { IntOffset(0, relBottom.roundToInt()) }
            .pointerInput(Unit) { detectTapGestures { dismiss() } }
    )
    // The floating suggestion list
    // taps register as selections
    Box(
        Modifier
            .offset { IntOffset(relLeft.roundToInt(), (relBottom + with(density) { 8.dp.toPx() }).roundToInt()) }
            .width(with(density) { widthPx.toDp() })
            .shadow(10.dp, RoundedCornerShape(kikoCorner(18.dp))),
    ) {
        SearchSuggestionsList(suggestions) { picked -> dismiss(); onSelect(picked) }
    }
}

// Standard FilterChip color scheme
// (Home, Discover, Forums, Ranking,
// scheme lives in one
@Composable fun kikoFilterChipColors(): SelectableChipColors {
    val c = LocalKikoColors.current
    return FilterChipDefaults.filterChipColors(containerColor = c.surfaceContainerLow, labelColor = c.ink, selectedContainerColor = c.secondaryContainer, selectedLabelColor = c.onSecondaryContainer)
}

// Re-centers a scrollable chip
// view, in one continuous
// can actually overflow the
// Uses an explicit eased
// enough to read as
// most chip taps cover.
private val chipCenterEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

fun CoroutineScope.centerChip(listState: LazyListState, index: Int) {
    launch {
        val info = listState.layoutInfo
        val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@launch
        val viewportCenter = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) / 2
        val itemCenter = itemInfo.offset + itemInfo.size / 2
        val distance = (itemCenter - viewportCenter).toFloat()
        // Scale duration with distance
        // one gets a longer,
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
// Fallback avatar tile. Reports
// anchor a popup —
// in its own Dialog
// positionOnScreen() (absolute screen coordinates)
// (coordinates relative to *this*
// a separate Android window
// main activity window don't
// to appear offset from

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
            .border(1.dp, c.outlineVariant, RoundedCornerShape(kikoCorner(10.dp)))
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