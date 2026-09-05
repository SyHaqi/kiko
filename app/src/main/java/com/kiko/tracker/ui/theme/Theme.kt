@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt
import com.kiko.tracker.data.model.ColorSource
import com.kiko.tracker.data.model.PaletteStyle

// ---------------------------------------------------------------------------
// Palette section — Material
//
// This used to be
// plus a few app-specific
// tonal containers for primary/secondary/tertiary,
// container ladder (surfaceContainerLowest to
// "surface", and dedicated outline/inverse/error
// and emphasis are communicated
// role here is generated
// be harmonious, whether that
// color, or a user-picked
// ---------------------------------------------------------------------------
@Immutable
data class KikoColors(
    val ink: Color, val onPrimary: Color, val primary: Color, val primaryContainer: Color,
    val onPrimaryContainer: Color = ink,
    val background: Color, val surface: Color, val surfaceLow: Color, val muted: Color,
    val lavender: Color, val warm: Color, val danger: Color,
    // Secondary / tertiary tonal
    // compete with primary (e.g.
    val secondary: Color = primary,
    val onSecondary: Color = onPrimary,
    val secondaryContainer: Color = primaryContainer,
    val onSecondaryContainer: Color = ink,
    val tertiary: Color = primary,
    val onTertiary: Color = onPrimary,
    val tertiaryContainer: Color = primaryContainer,
    val onTertiaryContainer: Color = ink,
    // Five-step surface container ladder
    // shadow-based elevation. Higher tier
    val surfaceContainerLowest: Color = background,
    val surfaceContainerLow: Color = surface,
    val surfaceContainer: Color = surfaceLow,
    val surfaceContainerHigh: Color = surfaceLow,
    val surfaceContainerHighest: Color = surfaceLow,
    val onSurfaceVariant: Color = muted,
    val outline: Color = muted,
    val outlineVariant: Color = muted,
    val errorContainer: Color = danger,
    val onError: Color = onPrimary,
    val onErrorContainer: Color = danger,
    val inverseSurface: Color = ink,
    val inverseOnSurface: Color = background,
    val inversePrimary: Color = primary,
    val scrim: Color = Color.Black,
    // Outline for buttons/cards in
    // everywhere else and doesn't
    val cardBorder: Color = Color.Transparent,
)

val LocalKikoColors = staticCompositionLocalOf { LightKiko }

// Readable stand-in for `primary`
// sitting directly on a
val KikoColors.accent: Color get() = primary

val AppFont = FontFamily.SansSerif

// Generate theme from seed

val AppDefaultSeed = Color(0xFF2E51A2)

fun normHue(h: Float) = ((h % 360f) + 360f) % 360f

fun hslColor(hue: Float, saturation: Float, lightness: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(normHue(hue), saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))))

fun seedHue(seed: Color): Float {
    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL(
        (seed.red * 255f).roundToInt().coerceIn(0, 255),
        (seed.green * 255f).roundToInt().coerceIn(0, 255),
        (seed.blue * 255f).roundToInt().coerceIn(0, 255),
        hsl,
    )
    return hsl[0]
}

/** Generates a full Expressive */
fun themedPalette(seed: Color, style: PaletteStyle, dark: Boolean): KikoColors {
    val hue = seedHue(seed)
    // Saturation bands per style
    val (accentSat, containerSat, neutralSat) = when (style) {
        PaletteStyle.TonalSpot -> Triple(0.52f, 0.35f, 0.06f)
        PaletteStyle.Neutral -> Triple(0.18f, 0.10f, 0.02f)
        PaletteStyle.Monochrome -> Triple(0f, 0f, 0f)
    }
    val secondarySat = accentSat * 0.55f
    val secondaryContainerSat = containerSat * 0.7f
    val tertiaryHue = hue + 60f

    return if (!dark) run {
        val mutedColor = hslColor(hue, neutralSat, 0.45f)
        val onSurfaceVariant = hslColor(hue, neutralSat, 0.32f)
        KikoColors(
            ink = hslColor(hue, neutralSat, 0.12f),
            onPrimary = Color.White,
            primary = hslColor(hue, accentSat, 0.46f),
            primaryContainer = hslColor(hue, containerSat, 0.88f),
            onPrimaryContainer = hslColor(hue, accentSat, 0.16f),
            background = hslColor(hue, neutralSat, 0.985f),
            surface = hslColor(hue, neutralSat * 0.6f, 0.995f),
            surfaceLow = hslColor(hue, neutralSat, 0.95f),
            muted = mutedColor,
            lavender = hslColor(hue + 40f, containerSat, 0.93f),
            warm = hslColor(hue - 150f, containerSat, 0.87f),
            danger = Color(0xFFB3261E),
            secondary = hslColor(hue, secondarySat, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = hslColor(hue, secondaryContainerSat, 0.90f),
            onSecondaryContainer = hslColor(hue, secondarySat, 0.16f),
            tertiary = hslColor(tertiaryHue, accentSat * 0.8f, 0.42f),
            onTertiary = Color.White,
            tertiaryContainer = hslColor(tertiaryHue, containerSat, 0.89f),
            onTertiaryContainer = hslColor(tertiaryHue, accentSat * 0.8f, 0.16f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hslColor(hue, neutralSat, 0.97f),
            surfaceContainer = hslColor(hue, neutralSat, 0.945f),
            surfaceContainerHigh = hslColor(hue, neutralSat, 0.915f),
            surfaceContainerHighest = hslColor(hue, neutralSat, 0.89f),
            onSurfaceVariant = onSurfaceVariant,
            outline = hslColor(hue, neutralSat, 0.50f),
            outlineVariant = hslColor(hue, neutralSat, 0.82f),
            errorContainer = Color(0xFFF9DEDC),
            onError = Color.White,
            onErrorContainer = Color(0xFF410E0B),
            inverseSurface = hslColor(hue, neutralSat, 0.20f),
            inverseOnSurface = hslColor(hue, neutralSat, 0.96f),
            inversePrimary = hslColor(hue, accentSat, 0.78f),
            scrim = Color.Black,
            // See below — same
            // instead of a fixed
            cardBorder = mutedColor.copy(alpha = 0.16f),
        )
    } else run {
        val mutedColor = hslColor(hue, neutralSat, 0.68f)
        val onSurfaceVariant = hslColor(hue, neutralSat, 0.78f)
        KikoColors(
            ink = hslColor(hue, neutralSat, 0.94f),
            onPrimary = hslColor(hue, neutralSat, 0.10f),
            primary = hslColor(hue, accentSat, 0.74f),
            primaryContainer = hslColor(hue, containerSat, 0.30f),
            onPrimaryContainer = hslColor(hue, accentSat, 0.90f),
            background = hslColor(hue, neutralSat, 0.08f),
            surface = hslColor(hue, neutralSat, 0.13f),
            surfaceLow = hslColor(hue, neutralSat, 0.17f),
            muted = mutedColor,
            lavender = hslColor(hue + 40f, containerSat, 0.18f),
            warm = hslColor(hue - 150f, containerSat, 0.21f),
            danger = Color(0xFFFFB4AB),
            secondary = hslColor(hue, secondarySat, 0.72f),
            onSecondary = hslColor(hue, neutralSat, 0.12f),
            secondaryContainer = hslColor(hue, secondaryContainerSat, 0.28f),
            onSecondaryContainer = hslColor(hue, secondarySat, 0.90f),
            tertiary = hslColor(tertiaryHue, accentSat * 0.8f, 0.76f),
            onTertiary = hslColor(tertiaryHue, neutralSat, 0.14f),
            tertiaryContainer = hslColor(tertiaryHue, containerSat, 0.27f),
            onTertiaryContainer = hslColor(tertiaryHue, accentSat * 0.8f, 0.90f),
            surfaceContainerLowest = hslColor(hue, neutralSat, 0.05f),
            surfaceContainerLow = hslColor(hue, neutralSat, 0.11f),
            surfaceContainer = hslColor(hue, neutralSat, 0.15f),
            surfaceContainerHigh = hslColor(hue, neutralSat, 0.20f),
            surfaceContainerHighest = hslColor(hue, neutralSat, 0.25f),
            onSurfaceVariant = onSurfaceVariant,
            outline = hslColor(hue, neutralSat, 0.42f),
            outlineVariant = hslColor(hue, neutralSat, 0.22f),
            errorContainer = Color(0xFF8C1D18),
            onError = Color(0xFF690005),
            onErrorContainer = Color(0xFFF9DEDC),
            inverseSurface = hslColor(hue, neutralSat, 0.92f),
            inverseOnSurface = hslColor(hue, neutralSat, 0.16f),
            inversePrimary = hslColor(hue, accentSat, 0.42f),
            scrim = Color.Black,
            cardBorder = mutedColor.copy(alpha = 0.16f),
        )
    }
}

// The app's default (non-dynamic,
// the exact same Expressive
// the same standard rather
val LightKiko: KikoColors by lazy { themedPalette(AppDefaultSeed, PaletteStyle.TonalSpot, dark = false) }
val DarkKiko: KikoColors by lazy { themedPalette(AppDefaultSeed, PaletteStyle.TonalSpot, dark = true) }

// Resolve palette seed color

fun resolveSeedColor(context: Context, source: ColorSource, customHex: String, dark: Boolean): Color = when (source) {
    ColorSource.AppDefault -> AppDefaultSeed
    ColorSource.Dynamic -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
    } else AppDefaultSeed
    ColorSource.Custom -> parseHexColor(customHex) ?: AppDefaultSeed
}

fun parseHexColor(hex: String): Color? {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6 || cleaned.any { it !in "0123456789abcdefABCDEF" }) return null
    return try { Color(0xFF000000 or cleaned.toLong(16)) } catch (e: Exception) { null }
}

// True-black variant for AMOLED
// black so OLED pixels
// Also gives buttons/cards/dividers a
// separators, so they stay
// blending into it. Pure
// the ~0x12 dark background),
// light/dark themes' 0.16f to
fun amoledify(colors: KikoColors): KikoColors = colors.copy(
    background = Color.Black,
    surface = Color(0xFF000000),
    surfaceLow = Color(0xFF0A0A0A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0C0C0C),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF181818),
    cardBorder = colors.muted.copy(alpha = .32f),
)

/** Builds a real androidx.compose.material3.ColorScheme */
fun KikoColors.toMaterialColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary, secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary, tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = background, onBackground = ink,
        surface = surface, onSurface = ink, surfaceVariant = surfaceLow, onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLowest = surfaceContainerLowest, surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer, surfaceContainerHigh = surfaceContainerHigh, surfaceContainerHighest = surfaceContainerHighest,
        outline = outline, outlineVariant = outlineVariant,
        error = danger, onError = onError, errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface, inversePrimary = inversePrimary,
        scrim = scrim, surfaceTint = primary,
    )
}

// Romaji or English titles
