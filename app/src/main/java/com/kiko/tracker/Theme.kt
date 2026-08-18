@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

// Palette section
@Immutable

data class KikoColors(
    val ink: Color, val onPrimary: Color, val primary: Color, val primaryContainer: Color,
    val background: Color, val surface: Color, val surfaceLow: Color, val muted: Color,
    val lavender: Color, val warm: Color, val danger: Color,
    // Outline for buttons/cards in AMOLED mode — transparent otherwise, so it's a no-op
    // everywhere else and doesn't need to be threaded through every existing constructor
    val cardBorder: Color = Color.Transparent,
)
// MAL brand palette colors

val LightKiko = KikoColors(
    ink = Color(0xFF1B1B1F), onPrimary = Color.White, primary = Color(0xFF2E51A2), primaryContainer = Color(0xFFE1E7F5),
    background = Color(0xFFFFFFFF), surface = Color(0xFFF8F8F8), surfaceLow = Color(0xFFEDEDED), muted = Color(0xFF6D6D6D),
    lavender = Color(0xFFEAF0FF), warm = Color(0xFFFFE9C7), danger = Color(0xFFB3261E),
    // Was fully transparent, so cards/search bars/buttons had nothing but a ~2-3%
    // surface-vs-background tint to read against — nearly invisible, especially in
    // light mode. A real hairline (same trick amoledify already uses) fixes that.
    cardBorder = Color(0xFF6D6D6D).copy(alpha = 0.16f),
)

val DarkKiko = KikoColors(
    ink = Color(0xFFEDEDED), onPrimary = Color(0xFF14203D), primary = Color(0xFFABC4ED), primaryContainer = Color(0xFF24365E),
    background = Color(0xFF121212), surface = Color(0xFF181818), surfaceLow = Color(0xFF222222), muted = Color(0xFFA3A3A3),
    lavender = Color(0xFF1F2A44), warm = Color(0xFF463A28), danger = Color(0xFFFFB4AB),
    cardBorder = Color(0xFFA3A3A3).copy(alpha = 0.16f),
)

val LocalKikoColors = staticCompositionLocalOf { LightKiko }

// Readable stand-in for `primary` wherever it's used as a *foreground* — text or an icon
// sitting directly on a surface/background, rather than as a button's own fill.
val KikoColors.accent: Color get() = primary

// Default corner radius passthrough (kept as a helper so call sites don't need to change).
@Composable fun kikoCorner(default: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = default

// Stand-ins for CircleShape / RoundedCornerShape(50) (full "pill" rounding), kept as
// helpers so call sites don't need to change.
@Composable fun kikoCircleShape(): androidx.compose.ui.graphics.Shape = CircleShape

@Composable fun kikoPillShape(): androidx.compose.ui.graphics.Shape = RoundedCornerShape(50)

// True-black variant for AMOLED screens — flattens background/surface tones to pure
// black so OLED pixels can switch off, while keeping accent/text colors untouched.
// Also gives buttons/cards/dividers a hairline border in the same tone as the list
// separators, so they stay visible against the pure-black background instead of
// blending into it. Pure black has no ambient tone to blend a faint gray into (unlike
// the ~0x12 dark background), so this needs a noticeably higher alpha than the
// light/dark themes' 0.16f to actually read as a line rather than disappear.
fun amoledify(colors: KikoColors): KikoColors = colors.copy(
    background = Color.Black,
    surface = Color(0xFF000000),
    surfaceLow = Color(0xFF0A0A0A),
    cardBorder = colors.muted.copy(alpha = .32f),
)

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

fun themedPalette(seed: Color, style: PaletteStyle, dark: Boolean): KikoColors {
    val hue = seedHue(seed)
    // Saturation bands per style
    val (accentSat, containerSat, neutralSat) = when (style) {
        PaletteStyle.TonalSpot -> Triple(0.52f, 0.35f, 0.06f)
        PaletteStyle.Neutral -> Triple(0.18f, 0.10f, 0.02f)
        PaletteStyle.Monochrome -> Triple(0f, 0f, 0f)
    }
    return if (!dark) run {
        val mutedColor = hslColor(hue, neutralSat, 0.45f)
        KikoColors(
            ink = hslColor(hue, neutralSat, 0.12f),
            onPrimary = Color.White,
            primary = hslColor(hue, accentSat, 0.46f),
            primaryContainer = hslColor(hue, containerSat, 0.88f),
            background = hslColor(hue, neutralSat, 0.975f),
            surface = hslColor(hue, neutralSat * 0.6f, 0.995f),
            surfaceLow = hslColor(hue, neutralSat, 0.95f),
            muted = mutedColor,
            lavender = hslColor(hue + 40f, containerSat, 0.93f),
            warm = hslColor(hue - 150f, containerSat, 0.87f),
            danger = Color(0xFFB3261E),
            // See LightKiko/DarkKiko — same hairline-border fix, tinted to this seed's hue
            // instead of a fixed gray so generated/dynamic themes get it too.
            cardBorder = mutedColor.copy(alpha = 0.16f),
        )
    } else run {
        val mutedColor = hslColor(hue, neutralSat, 0.68f)
        KikoColors(
            ink = hslColor(hue, neutralSat, 0.94f),
            onPrimary = hslColor(hue, neutralSat, 0.10f),
            primary = hslColor(hue, accentSat, 0.74f),
            primaryContainer = hslColor(hue, containerSat, 0.30f),
            background = hslColor(hue, neutralSat, 0.08f),
            surface = hslColor(hue, neutralSat, 0.13f),
            surfaceLow = hslColor(hue, neutralSat, 0.17f),
            muted = mutedColor,
            lavender = hslColor(hue + 40f, containerSat, 0.18f),
            warm = hslColor(hue - 150f, containerSat, 0.21f),
            danger = Color(0xFFFFB4AB),
            cardBorder = mutedColor.copy(alpha = 0.16f),
        )
    }
}
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

// Romaji or English titles