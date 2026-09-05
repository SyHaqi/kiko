package com.kiko.tracker.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Material 3 Expressive shape
//
// Expressive replaces M3's original
// range of corner rounding
// families" (small controls stay
// softer, and hero/FAB-scale elements
// than the narrower, more
//
// Radii below are Google's
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
    // "Full" is resolved as
    // radius — see kikoPillShape().
}

/** Every call site in */
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

/** Expressive "full" shape — */
@Composable fun kikoCircleShape(): Shape = CircleShape

@Composable fun kikoPillShape(): Shape = RoundedCornerShape(50)

/** Convenience: rounded shape at */
@Composable
fun kikoShape(radius: Dp): Shape = RoundedCornerShape(kikoCorner(radius))

/** The MaterialTheme shape scheme, */
val KikoShapes = Shapes(
    extraSmall = RoundedCornerShape(KikoShape.extraSmall),
    small = RoundedCornerShape(KikoShape.small),
    medium = RoundedCornerShape(KikoShape.medium),
    large = RoundedCornerShape(KikoShape.large),
    extraLarge = RoundedCornerShape(KikoShape.extraLarge),
)
