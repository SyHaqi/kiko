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
    // "Classic UI" flag — read by shared components (see kikoCorner/kikoCircleShape/
    // kikoPillShape below) so they can flatten their own rounding without every call site
    // needing to branch on it individually. classicAccent2 is a secondary accent only
    // ClassicKiko sets, for places that want a tint distinct from `primary`.
    val classic: Boolean = false, val classicAccent2: Color = Color.Transparent,
)
// MAL brand palette colors

val LightKiko = KikoColors(
    ink = Color(0xFF1B1B1F), onPrimary = Color.White, primary = Color(0xFF2E51A2), primaryContainer = Color(0xFFE1E7F5),
    background = Color(0xFFFFFFFF), surface = Color(0xFFF8F8F8), surfaceLow = Color(0xFFEDEDED), muted = Color(0xFF6D6D6D),
    lavender = Color(0xFFEAF0FF), warm = Color(0xFFFFE9C7), danger = Color(0xFFB3261E)
)

val DarkKiko = KikoColors(
    ink = Color(0xFFEDEDED), onPrimary = Color(0xFF14203D), primary = Color(0xFFABC4ED), primaryContainer = Color(0xFF24365E),
    background = Color(0xFF121212), surface = Color(0xFF181818), surfaceLow = Color(0xFF222222), muted = Color(0xFFA3A3A3),
    lavender = Color(0xFF1F2A44), warm = Color(0xFF463A28), danger = Color(0xFFFFB4AB)
)

// "Classic UI" — mirrors MyAnimeList's actual current site: dark charcoal page (#121212),
// slightly-lighter panel surfaces (#181818), the same MAL nav-bar blue (#2E51A2) used
// elsewhere as AppDefaultSeed, a lighter tint of that blue (#ABC4ED, sampled from MAL's
// in-panel title links) for text/accents on dark surfaces, and a #414141 hairline for the
// row dividers MAL uses instead of card gaps. Values sampled directly from myanimelist.net.
// Applied regardless of light/dark or colorSource/paletteStyle — see the theme-selection
// `remember` in Navigation.kt. Ignores amoledDark too: true black would crush MAL's actual
// panel/background contrast.
val ClassicKiko = KikoColors(
    ink = Color(0xFFEDEDED), onPrimary = Color.White, primary = Color(0xFF2E51A2), primaryContainer = Color(0xFF24365E),
    background = Color(0xFF121212), surface = Color(0xFF181818), surfaceLow = Color(0xFF242424), muted = Color(0xFFA3A3A3),
    lavender = Color(0xFF1F2A44), warm = Color(0xFF463A28), danger = Color(0xFFFFB4AB),
    cardBorder = Color(0xFF414141), classic = true, classicAccent2 = Color(0xFFABC4ED),
)

val LocalKikoColors = staticCompositionLocalOf { LightKiko }

// Readable stand-in for `primary` wherever it's used as a *foreground* — text or an icon
// sitting directly on a surface/background, rather than as a button's own fill. In Classic
// UI, `primary` is MAL's brand navy (#2E51A2), picked to sit on light button fills; as text
// on Classic's dark panels it reads as barely-there. `classicAccent2` is the lighter tint
// made for exactly this case, so route through it whenever the theme is Classic. Everywhere
// else `primary` already has correct contrast on its own, so this is a no-op there.
val KikoColors.accent: Color get() = if (classic) classicAccent2 else primary

// Scales a default corner radius down to a small flat-ish corner in Classic mode (3dp
// floor so it never goes fully sharp), and passes it through unchanged otherwise.
@Composable fun kikoCorner(default: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    if (LocalKikoColors.current.classic) (default.value * 0.3f).coerceAtLeast(3f).dp else default

// Classic mode stand-ins for CircleShape / RoundedCornerShape(50) (full "pill" rounding).
// Circular avatars, status dots, pagination dots, and pill badges/progress bars all read
// as squared-off little tiles in MAL's actual site rather than perfect circles/capsules,
// so both collapse to the same flat corner (kikoCorner's 3dp floor) instead of a
// stadium/circle shape. No-ops (return the normal shape) outside classic mode.
@Composable fun kikoCircleShape(): androidx.compose.ui.graphics.Shape =
    if (LocalKikoColors.current.classic) RoundedCornerShape(kikoCorner(0.dp)) else CircleShape

@Composable fun kikoPillShape(): androidx.compose.ui.graphics.Shape =
    if (LocalKikoColors.current.classic) RoundedCornerShape(kikoCorner(0.dp)) else RoundedCornerShape(50)

// True-black variant for AMOLED screens — flattens background/surface tones to pure
// black so OLED pixels can switch off, while keeping accent/text colors untouched.
// Also gives buttons/cards a hairline border in the same tone as the list separators,
// so they stay visible against the pure-black background instead of blending into it.
fun amoledify(colors: KikoColors): KikoColors = colors.copy(
    background = Color.Black,
    surface = Color(0xFF000000),
    surfaceLow = Color(0xFF0A0A0A),
    cardBorder = colors.muted.copy(alpha = .15f),
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
    return if (!dark) KikoColors(
        ink = hslColor(hue, neutralSat, 0.12f),
        onPrimary = Color.White,
        primary = hslColor(hue, accentSat, 0.46f),
        primaryContainer = hslColor(hue, containerSat, 0.88f),
        background = hslColor(hue, neutralSat, 0.975f),
        surface = hslColor(hue, neutralSat * 0.6f, 0.995f),
        surfaceLow = hslColor(hue, neutralSat, 0.95f),
        muted = hslColor(hue, neutralSat, 0.45f),
        lavender = hslColor(hue + 40f, containerSat, 0.93f),
        warm = hslColor(hue - 150f, containerSat, 0.87f),
        danger = Color(0xFFB3261E),
    ) else KikoColors(
        ink = hslColor(hue, neutralSat, 0.94f),
        onPrimary = hslColor(hue, neutralSat, 0.10f),
        primary = hslColor(hue, accentSat, 0.74f),
        primaryContainer = hslColor(hue, containerSat, 0.30f),
        background = hslColor(hue, neutralSat, 0.08f),
        surface = hslColor(hue, neutralSat, 0.13f),
        surfaceLow = hslColor(hue, neutralSat, 0.17f),
        muted = hslColor(hue, neutralSat, 0.68f),
        lavender = hslColor(hue + 40f, containerSat, 0.18f),
        warm = hslColor(hue - 150f, containerSat, 0.21f),
        danger = Color(0xFFFFB4AB),
    )
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