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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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

@Composable
fun StaggeredItem(index: Int, content: @Composable () -> Unit) {
    val visibleState = remember(index) { MutableTransitionState(false).apply { targetState = true } }
    val delay = (index * 30).coerceAtMost(240)
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(260, delayMillis = delay)) +
                slideInVertically(tween(260, delayMillis = delay), initialOffsetY = { it / 8 }),
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------
// Skeleton placeholders — shaped like the real content they stand in for, so a
// loading list reads as "this is about to be a list" instead of blank space.
// ---------------------------------------------------------------------------

/** Stand-in for a [ListRow]: cover-sized block + a few text-line bars. */
@Composable
fun ListRowSkeleton(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(Modifier.size(width = 84.dp, height = 118.dp), shape = RoundedCornerShape(16.dp))
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
        SkeletonBlock(Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(18.dp))
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
        SkeletonBlock(Modifier.size(36.dp), shape = androidx.compose.foundation.shape.CircleShape)
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

/** Stand-in for the Home "Continue" card while the first sync hasn't landed yet. */
@Composable
fun ContinueCardSkeleton(modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.size(width = 84.dp, height = 118.dp), shape = RoundedCornerShape(16.dp))
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(16.dp))
            SkeletonBlock(Modifier.padding(top = 10.dp).fillMaxWidth(0.4f).height(12.dp))
            SkeletonBlock(Modifier.padding(top = 14.dp).fillMaxWidth(0.55f).height(8.dp))
        }
    }
}

/** Stand-in for a single [AiringNextCard]: cover-sized block + title/time bars. */
@Composable
fun AiringNextCardSkeleton(modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Row(
        modifier
            .width(264.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.size(width = 78.dp, height = 110.dp), shape = RoundedCornerShape(16.dp))
        Column(Modifier.padding(start = 13.dp).weight(1f)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(14.dp))
            SkeletonBlock(Modifier.padding(top = 6.dp).fillMaxWidth(0.5f).height(14.dp))
            SkeletonBlock(Modifier.padding(top = 12.dp).fillMaxWidth(0.6f).height(11.dp))
        }
    }
}

/** A row of [AiringNextCardSkeleton]s, staggered in — Home's "Airing next" first-load state. */
@Composable
fun AiringNextRowSkeleton() {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        repeat(3) { i -> StaggeredItem(i) { AiringNextCardSkeleton() } }
    }
}

/** Stand-in for [SnapshotsGrid]'s Pinterest-style two-column layout — same
 *  alternating tall/short rhythm as the real cards so the page doesn't reflow
 *  once the images land. */
@Composable
fun SnapshotsGridSkeleton() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(210.dp), shape = RoundedCornerShape(18.dp))
            SkeletonBlock(Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(18.dp))
            SkeletonBlock(Modifier.fillMaxWidth().height(210.dp), shape = RoundedCornerShape(18.dp))
        }
    }
}