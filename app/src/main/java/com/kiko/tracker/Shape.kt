package com.kiko.tracker

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Material 3 Expressive shape scale.
//
// Expressive replaces M3's original 7-step scale with a wider, more dramatic
// range of corner rounding — components are meant to sit on distinct "shape
// families" (small controls stay tight and precise, containers get noticeably
// softer, and hero/FAB-scale elements go all the way to a full pill) rather
// than the narrower, more uniform rounding the app used before.
//
// Radii below are Google's published Expressive tokens.
// https://m3.material.io/styles/shape/shape-scale-tokens
// ---------------------------------------------------------------------------

object KikoShape {
    val none = 0.dp
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val largeIncreased = 20.dp
    val extraLarge = 28.dp
    val extraLargeIncreased = 32.dp
    val extraExtraLarge = 48.dp
    // "Full" is resolved as a pill (50% of the shorter side) rather than a fixed
    // radius — see kikoPillShape().
}

/**
 * Every call site in the app was written against an arbitrary, hand-picked corner
 * radius (anywhere from 4dp to 32dp, no consistent system). Rather than touch all
 * ~250 call sites individually, this snaps whatever radius was requested onto the
 * nearest step of the real Expressive shape scale — so the whole app inherits the
 * same handful of deliberate shape families from one place.
 */
@Composable
fun kikoCorner(default: Dp): Dp = when {
    default <= 2.dp -> KikoShape.none
    default <= 6.dp -> KikoShape.extraSmall
    default <= 10.dp -> KikoShape.small
    default <= 14.dp -> KikoShape.medium
    default <= 18.dp -> KikoShape.large
    default <= 24.dp -> KikoShape.largeIncreased
    default <= 30.dp -> KikoShape.extraLarge
    default <= 40.dp -> KikoShape.extraLargeIncreased
    else -> KikoShape.extraExtraLarge
}

/** Expressive "full" shape — a true pill, used for FABs, chips, and hero buttons. */
@Composable fun kikoCircleShape(): Shape = CircleShape

@Composable fun kikoPillShape(): Shape = RoundedCornerShape(50)

/** Convenience: rounded shape at a given scale step, pre-snapped. */
@Composable
fun kikoShape(radius: Dp): Shape = RoundedCornerShape(kikoCorner(radius))

/**
 * The MaterialTheme shape scheme, wired to the same Expressive tokens above.
 * (The compose-material3 BOM this app pins — 2024.12.01 — predates the newer
 * `Shapes()` overload with `largeIncreased`/`extraLargeIncreased` slots, so those
 * two steps are only reached directly via kikoCorner()/kikoShape() at call sites
 * that want them, e.g. dialogs and FAB-scale surfaces.)
 */
val KikoShapes = Shapes(
    extraSmall = RoundedCornerShape(KikoShape.extraSmall),
    small = RoundedCornerShape(KikoShape.small),
    medium = RoundedCornerShape(KikoShape.medium),
    large = RoundedCornerShape(KikoShape.large),
    extraLarge = RoundedCornerShape(KikoShape.extraLarge),
)
