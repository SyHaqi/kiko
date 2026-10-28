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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

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

/** No longer shrinks anything: pressed items are shown as pressed by their highlight (ripple /
 *  indication) alone. Kept as a pass-through so every existing call site — [kikoClickable],
 *  [kikoCombinedClickable] and the cards that call it directly — compiles unchanged and the `scale`
 *  arguments are simply ignored. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, scale: Float = 0.96f): Modifier = this

/** Drop-in replacement for `.clickable */
@Composable
fun Modifier.kikoClickable(scale: Float = 0.96f, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .pressScale(interactionSource, scale)
        .clickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

/** Drop-in replacement for `.combinedClickable(...)` — implemented with manual
 *  gesture detection (rather than Foundation's `combinedClickable`) so a long
 *  press never triggers the platform's automatic long-press haptic buzz. */
@Composable
fun Modifier.kikoCombinedClickable(
    scale: Float = 0.97f,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val tapTimeout = remember { android.view.ViewConfiguration.getTapTimeout().toLong() } // ~100ms, delays the pressed state
    // scale == 1f means "no press-shrink": skip pressScale entirely so the row doesn't pay for an
    // animation state + graphicsLayer it would never visibly use (matters on long lists).
    return (if (scale != 1f) this.pressScale(interactionSource, scale) else this)
        .indication(interactionSource, LocalIndication.current)
        .then(
            if (enabled) {
                Modifier.pointerInput(onClick, onLongClick, tapTimeout) {
                    detectTapGestures(
                        onPress = { offset ->
                            // Same behaviour as Foundation's clickable: don't show the pressed
                            // highlight the instant a finger lands. Wait out the tap timeout first;
                            // if a scroll/drag takes over before then, the press is cancelled and
                            // nothing is ever shown (no flashing rows while scrolling a list).
                            val press = PressInteraction.Press(offset)
                            var shown = false
                            coroutineScope {
                                val showJob = launch {
                                    delay(tapTimeout)
                                    interactionSource.emit(press)
                                    shown = true
                                }
                                val released = tryAwaitRelease()
                                if (shown) {
                                    interactionSource.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                                } else {
                                    showJob.cancel()
                                    if (released) {
                                        // Quick tap: still flash the ripple so it feels responsive.
                                        interactionSource.emit(press)
                                        interactionSource.emit(PressInteraction.Release(press))
                                    }
                                }
                            }
                        },
                        onTap = { onClick() },
                        onLongPress = onLongClick?.let { click -> { _: androidx.compose.ui.geometry.Offset -> click() } },
                    )
                }
            } else {
                Modifier
            },
        )
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