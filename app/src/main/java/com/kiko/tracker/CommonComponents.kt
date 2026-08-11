@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import kotlin.math.roundToInt

@Composable fun BottomBar(selected: Destination, select: (Destination) -> Unit) { val c = LocalKikoColors.current; NavigationBar(containerColor = c.surface, tonalElevation = 4.dp) { Destination.entries.forEach { d -> NavigationBarItem(selected = d == selected, onClick = { select(d) }, icon = { Icon(d.icon, null) }, label = { Text(d.label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = c.primary, selectedTextColor = c.primary, unselectedIconColor = c.muted, unselectedTextColor = c.muted, indicatorColor = c.primaryContainer)) } } }

@Composable fun AppHeader(title: String, horizontalPadding: Dp = 20.dp, action: @Composable () -> Unit = {}) { Row(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-1).sp, color = LocalKikoColors.current.ink); action() } }

// Unused params kept intentionally

@Composable fun SkeletonBlock(modifier: Modifier, shape: RoundedCornerShape = RoundedCornerShape(12.dp)) {
    val c = LocalKikoColors.current
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(modifier.clip(shape).background(c.surfaceLow.copy(alpha = alpha)))
}
// Continue card placeholder

@Composable fun MiniCard(item: MediaItem, onOpenDetail: (MediaItem) -> Unit) {
    val c = LocalKikoColors.current
    Column(Modifier.width(118.dp).clickable { onOpenDetail(item) }) {
        Cover(item, Modifier.fillMaxWidth().height(150.dp), showStatus = true)
        Text(item.displayTitle(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(if (item.status == WatchStatus.Plan) "Saved for later" else progressLabel(item), color = c.primary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    }
}

@Composable fun Cover(item: MediaItem, modifier: Modifier = Modifier, showStatus: Boolean = false, statusAlignment: Alignment = Alignment.TopStart, overrideStatus: WatchStatus? = null, showRating: Boolean = false, selected: Boolean = false) {
    val c = LocalKikoColors.current
    val displayTitle = item.displayTitle()
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color(item.color)), contentAlignment = Alignment.Center) {
        if (item.cover.isNotBlank()) AsyncImage(model = item.cover, contentDescription = displayTitle, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
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
                Modifier.size(22.dp).clip(CircleShape).background(c.primary).border(1.5.dp, Color.White.copy(alpha = .9f), CircleShape),
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
        modifier.size(22.dp).clip(CircleShape).background(statusColor(status)).border(1.5.dp, Color.White.copy(alpha = .9f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(status.badgeIcon(), status.label, tint = Color.White, modifier = Modifier.size(13.dp)) }
}
// User's score, pinned to a cover corner — dark pill so it reads on any artwork

@Composable fun CoverRatingMark(rating: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = .6f))
            .border(1.dp, Color.White.copy(alpha = .9f), RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
        Text(rating.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
    }
}

@Composable fun TypeToggle(current: MediaType, trackColor: Color = LocalKikoColors.current.surface, set: (MediaType) -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(trackColor).padding(4.dp)) {
        MediaType.entries.forEach { t ->
            val selected = current == t
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (selected) c.primary else Color.Transparent).clickable { set(t) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (t == MediaType.Anime) "Anime" else "Manga", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (selected) c.onPrimary else c.muted)
            }
        }
    }
}

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
        singleLine = true, shape = RoundedCornerShape(18.dp),
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.cardBorder, RoundedCornerShape(18.dp)),
    ) {
        suggestions.forEachIndexed { index, title ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(title) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = c.muted, modifier = Modifier.size(16.dp))
                Text(title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp).weight(1f))
            }
            if (index < suggestions.lastIndex) HorizontalDivider(thickness = 1.dp, color = c.muted.copy(alpha = .12f), modifier = Modifier.padding(start = 46.dp))
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
            .shadow(10.dp, RoundedCornerShape(18.dp)),
    ) {
        SearchSuggestionsList(suggestions) { picked -> dismiss(); onSelect(picked) }
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
// Fallback avatar tile. Reports its own on-screen bounds (in window coordinates) to
// onClick so callers can anchor a popup — like AvatarMenu — directly under it, even
// though that popup is rendered elsewhere in the tree (see Navigation's profileMenuAnchor).

@Composable fun Avatar(picture: String = "", name: String = "", onClick: ((Rect) -> Unit)? = null) {
    val c = LocalKikoColors.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val posMod = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }
    val tapMod = if (onClick != null) Modifier.clickable { onClick(bounds) } else Modifier
    if (picture.isNotBlank()) {
        AsyncImage(model = picture, contentDescription = "Profile picture", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(43.dp).clip(RoundedCornerShape(16.dp)).background(c.warm).then(posMod).then(tapMod))
    } else {
        Box(Modifier.size(43.dp).clip(RoundedCornerShape(16.dp)).background(c.warm).then(posMod).then(tapMod), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase().ifBlank { "M" }, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = c.ink) }
    }
}

// Detail section

@Composable fun Pill(text: String, container: Color, content: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = content, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
// Outline style genre chip

@Composable fun GenreChip(text: String, onClick: (() -> Unit)? = null) {
    val c = LocalKikoColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, c.muted.copy(alpha = .35f), RoundedCornerShape(10.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
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