@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kiko.tracker.ui.theme

// Shared animation/perf-polish toolkit: tap
// staggered list-item entrance. Pulled
// the same primitives instead

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
import com.kiko.tracker.ui.components.SkeletonBlock

// ---------------------------------------------------------------------------
// Material 3 Expressive motion
//
// Expressive motion is spring-based
// animation is described by
// millisecond count. There are
// allowed to overshoot and
// critically damped, never bounces)
// Values below are Google's
// https://m3.material.io/styles/motion/overview/specs
// ---------------------------------------------------------------------------

object KikoMotion {
    // Spatial — for anything
    // is intentional here; it's
    // rather than mechanical.
    fun <T> spatialFast() = spring<T>(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> spatialDefault() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> spatialSlow() = spring<T>(dampingRatio = 0.8f, stiffness = 200f)

    // Effects — for color/opacity/elevation
    // (dampingRatio = 1) so
    // only spatial motion is
    fun <T> effectsFast() = spring<T>(dampingRatio = 1f, stiffness = 3800f)
    fun <T> effectsDefault() = spring<T>(dampingRatio = 1f, stiffness = 1600f)
    fun <T> effectsSlow() = spring<T>(dampingRatio = 1f, stiffness = 800f)
}

// ---------------------------------------------------------------------------
// Tap feedback — a
// and buttons read as
// ---------------------------------------------------------------------------

/** Low-level: scales `this` down */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, scale: Float = 0.96f): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) scale else 1f,
        animationSpec = KikoMotion.spatialFast(),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
}

/** Drop-in replacement for `.clickable */
@Composable
fun Modifier.kikoClickable(scale: Float = 0.96f, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .pressScale(interactionSource, scale)
        .clickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

/** Drop-in replacement for `.combinedClickable(...)` */
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
// Staggered entrance — wraps
// place the first time
// fresh item), instead of
// ---------------------------------------------------------------------------

// Per-list "have we already
// these per screen (rememberStaggerMemory())
// the item's index. Without
// every re-entry into view
//
// Why this is needed
// scrolls out of the
// into view, so `remember(index)`
// index already played its
// replaying the fade+slide for
// recomposition/animation work and, more
// scrolling look like content
// A plain (non-snapshot) MutableSet
// at that item's own
// to observe it changing.
@Composable
fun rememberStaggerMemory(): MutableSet<Int> = remember { mutableSetOf() }

// Previously wrapped content in
// staggered delay, played every
// composition + alpha/translation animation
// List/Discover/Seasonal/Hub, which are all
// with the scroll gesture
// Home (which has no
// a plain passthrough so
// unchanged, just without the
@Composable
fun StaggeredItem(index: Int, seen: MutableSet<Int>? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) { content() }
}

// ---------------------------------------------------------------------------
// Skeleton placeholders — shaped
// loading list reads as
// ---------------------------------------------------------------------------

/** Stand-in for a [ListRow]: */
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

/** Stand-in for a [ListGridCard]: */
@Composable
fun ListGridCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SkeletonBlock(Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(kikoCorner(18.dp)))
        SkeletonBlock(Modifier.padding(top = 8.dp).fillMaxWidth(0.85f).height(13.dp))
        SkeletonBlock(Modifier.padding(top = 6.dp).fillMaxWidth(0.4f).height(10.dp))
    }
}

/** A handful of [ListRowSkeleton]s, */
@Composable
fun ListRowSkeletonGroup(count: Int = 6) {
    Column {
        repeat(count) { i -> StaggeredItem(i) { ListRowSkeleton() } }
    }
}

/** Stand-in for an avatar-led */
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

/** A handful of [TopicRowSkeleton]s, */
@Composable
fun TopicRowSkeletonGroup(count: Int = 6) {
    Column {
        repeat(count) { i -> StaggeredItem(i) { TopicRowSkeleton() } }
    }
}

/** Stand-in for the Home */
@Composable
fun ContinueCardSkeleton(modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(22.dp)))
            .background(c.surfaceContainer),
    ) {
        ListRowSkeleton(Modifier.padding(horizontal = 14.dp))
    }
}

/** Stand-in for a single */
@Composable
fun AiringNextCardSkeleton(modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(kikoCorner(22.dp)))
            .background(c.surfaceContainer),
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

/** A horizontally-scrolling row of */
@Composable
fun AiringNextRowSkeleton() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(3) { i -> StaggeredItem(i) { AiringNextCardSkeleton(modifier = Modifier.fillParentMaxWidth(0.94f)) } }
    }
}

/** Stand-in for [AiringNextBannerCard]: one */
@Composable
fun AiringNextBannerSkeleton() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 34.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(200.dp), shape = RoundedCornerShape(kikoCorner(26.dp)))
    }
}

/** Stand-in for a single */
@Composable
fun DetailFeaturedArticleCardSkeleton(modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(20.dp)))
            .background(c.surfaceContainer)
            .padding(12.dp),
    ) {
        SkeletonBlock(Modifier.width(76.dp).aspectRatio(2f / 3f), shape = RoundedCornerShape(kikoCorner(14.dp)))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(14.dp))
            SkeletonBlock(Modifier.padding(top = 6.dp).fillMaxWidth(0.6f).height(14.dp))
            SkeletonBlock(Modifier.padding(top = 12.dp).fillMaxWidth(0.7f).height(11.dp))
            SkeletonBlock(Modifier.padding(top = 6.dp).fillMaxWidth(0.4f).height(11.dp))
            SkeletonBlock(Modifier.padding(top = 12.dp).fillMaxWidth(0.35f).height(10.dp))
        }
    }
}

/** Stand-in for [SnapshotsGrid]'s Pinterest-style */
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