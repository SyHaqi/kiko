@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kiko.tracker

// Shared animation/perf-polish toolkit: tap feedback, skeleton placeholders, and
// staggered list-item entrance. Pulled into one file so every screen reaches for
// the same primitives instead of hand-rolling its own press/loading animation.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Tap feedback — a small press-in scale on top of the normal ripple, so cards
// and buttons read as physically responsive rather than just color-flashing.
// ---------------------------------------------------------------------------

/** Low-level: scales `this` down while `interactionSource` reports a press. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, scale: Float = 0.96f): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) scale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 700f),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
}

/** Drop-in replacement for `.clickable { onClick() }` that adds press-scale feedback. */
@Composable
fun Modifier.kikoClickable(scale: Float = 0.96f, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .pressScale(interactionSource, scale)
        .clickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

/** Drop-in replacement for `.combinedClickable(...)` that adds press-scale feedback. */
@Composable
fun Modifier.kikoCombinedClickable(
    scale: Float = 0.97f,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .pressScale(interactionSource, scale)
        .combinedClickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled, onClick = onClick, onLongClick = onLongClick)
}

// ---------------------------------------------------------------------------
// Staggered entrance — wraps a lazy list/grid item so it fades + rises into
// place the first time it's composed (initial load, or scrolling to reveal a
// fresh item), instead of just popping into existence.
// ---------------------------------------------------------------------------

// Per-list "have we already played this item's entrance once" memory. Hoist one of
// these per screen (rememberStaggerMemory()) and pass it into StaggeredItem alongside
// the item's index. Without it (seen == null, the default), behavior is unchanged —
// every re-entry into view replays the animation, same as before.
//
// Why this is needed at all: a LazyColumn/Grid disposes an item's composition when it
// scrolls out of the retained window and creates a brand-new one when it scrolls back
// into view, so `remember(index)` *inside* StaggeredItem alone has no memory of "this
// index already played its entrance" — scrolling up and down through a long list kept
// replaying the fade+slide for every row on every pass, which is both unnecessary
// recomposition/animation work and, more noticeably, makes fast back-and-forth
// scrolling look like content is constantly "arriving" instead of just being there.
// A plain (non-snapshot) MutableSet is enough since it's only ever read once per item
// at that item's own first composition and written imperatively — nothing else needs
// to observe it changing.
@Composable
fun rememberStaggerMemory(): MutableSet<Int> = remember { mutableSetOf() }

// Previously wrapped content in an AnimatedVisibility fade+slide-in with a per-index
// staggered delay, played every time a row first scrolled into view. That's extra
// composition + alpha/translation animation work happening during scroll fling — on
// List/Discover/Seasonal/Hub, which are all real (many-row) lazy lists, this competed
// with the scroll gesture for frame time and made those tabs feel less smooth than
// Home (which has no itemized entrance animation at all). Kept the same signature as
// a plain passthrough so every call site (List, Discover, Seasonal, Hub) keeps working
// unchanged, just without the animation.
@Composable
fun StaggeredItem(index: Int, seen: MutableSet<Int>? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) { content() }
}

// ---------------------------------------------------------------------------
// Skeleton placeholders — shaped like the real content they stand in for, so a
// loading list reads as "this is about to be a list" instead of blank space.
// ---------------------------------------------------------------------------

/** Stand-in for a [ListRow]: cover-sized block + a few text-line bars. */
@Composable
fun ListRowSkeleton(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(Modifier.size(width = 84.dp, height = 118.dp), shape = RoundedCornerShape(kikoCorner(16.dp)))
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.75f).height(16.dp))
            SkeletonBlock(Modifier.padding(top = 10.dp).fillMaxWidth(0.45f).height(12.dp))
            SkeletonBlock(Modifier.padding(top = 14.dp).fillMaxWidth(0.6f).height(8.dp))
            SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.3f).height(12.dp))
        }
    }
}

/** Stand-in for a [ListGridCard]: cover tile + two text bars underneath. */
@Composable
fun ListGridCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SkeletonBlock(Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(kikoCorner(18.dp)))
        SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.85f).height(13.dp))
        SkeletonBlock(Modifier.padding(top = 6.dp).fillMaxWidth(0.4f).height(10.dp))
    }
}

/** A handful of [ListRowSkeleton]s, staggered in — used as a list's first-load state. */
@Composable
fun ListRowSkeletonGroup(count: Int = 6) {
    Column {
        repeat(count) { i -> StaggeredItem(i) { ListRowSkeleton() } }
    }
}

/** Stand-in for an avatar-led row (forum topics, boards): circle avatar + text bars. */
@Composable
fun TopicRowSkeleton(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        SkeletonBlock(Modifier.size(36.dp), shape = kikoCircleShape())
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(14.dp))
            SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.5f).height(11.dp))
        }
    }
}

/** A handful of [TopicRowSkeleton]s, staggered in. */
@Composable
fun TopicRowSkeletonGroup(count: Int = 6) {
    Column {
        repeat(count) { i -> StaggeredItem(i) { TopicRowSkeleton() } }
    }
}

/** Stand-in for the Home "Continue" row while the first sync hasn't landed yet — boxed
 *  the same way as the real [ContinueCard] (surface fill + cardBorder outline) so the
 *  page doesn't reflow once real data lands. */
@Composable
fun ContinueCardSkeleton(modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(22.dp)))
            .background(c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(kikoCorner(22.dp))),
    ) {
        ListRowSkeleton(Modifier.padding(horizontal = 14.dp))
    }
}

/** Stand-in for a single [AiringNextCard] — boxed the exact same way as the real card
 *  (surface fill + cardBorder outline + 22dp rounded corners, cover-then-text layout at
 *  matching sizes) so the loading state and the real content share the same card shape.
 *  Previously this only rendered the inner content with no card of its own, and
 *  [AiringNextRowSkeleton] wrapped all three in one shared container instead — the real
 *  row is a horizontally-scrolling carousel of individually-bordered cards, so that read
 *  as a visibly different shape once real data swapped in. */
@Composable
fun AiringNextCardSkeleton(modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(kikoCorner(22.dp)))
            .background(c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(kikoCorner(22.dp))),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(Modifier.size(width = 84.dp, height = 118.dp), shape = RoundedCornerShape(kikoCorner(16.dp)))
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(14.dp))
                SkeletonBlock(Modifier.padding(top = 6.dp).fillMaxWidth(0.5f).height(14.dp))
                SkeletonBlock(Modifier.padding(top = 14.dp).fillMaxWidth(0.6f).height(11.dp))
            }
        }
    }
}

/** A horizontally-scrolling row of [AiringNextCardSkeleton]s — same carousel shape as the
 *  real [AiringNextRow] (each card individually boxed, sized to fillParentMaxWidth so the
 *  next card peeks in from the edge), not the old single-shared-container skeleton. */
@Composable
fun AiringNextRowSkeleton() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(3) { i -> StaggeredItem(i) { AiringNextCardSkeleton(modifier = Modifier.fillParentMaxWidth(0.94f)) } }
    }
}

/** Stand-in for [AiringNextBannerCard]: one full-width backdrop block, sized to match
 *  the banner's own height so the page doesn't reflow once the real slide lands. */
@Composable
fun AiringNextBannerSkeleton() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 34.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(200.dp), shape = RoundedCornerShape(kikoCorner(26.dp)))
    }
}

/** Stand-in for [SnapshotsGrid]'s Pinterest-style two-column layout — same
 *  alternating tall/short rhythm as the real cards so the page doesn't reflow
 *  once the images land. */
@Composable
fun SnapshotsGridSkeleton() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(210.dp), shape = RoundedCornerShape(kikoCorner(18.dp)))
            SkeletonBlock(Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(kikoCorner(18.dp)))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(kikoCorner(18.dp)))
            SkeletonBlock(Modifier.fillMaxWidth().height(210.dp), shape = RoundedCornerShape(kikoCorner(18.dp)))
        }
    }
}