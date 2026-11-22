@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeTonalSpot
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

/**
 * Generates a real Material 3 palette: seed -> HCT -> tonal palettes -> the standard M3 role/tone
 * mapping (material-color-utilities, the same engine behind Android's dynamic color). The old
 * version approximated this with hand-tuned HSL values, which is why the colors looked slightly
 * "off" next to stock Material 3 apps.
 */
fun themedPalette(seed: Color, style: PaletteStyle, dark: Boolean): KikoColors {
    val hct = Hct.fromInt(seed.toArgb())
    val scheme: DynamicScheme = when (style) {
        PaletteStyle.TonalSpot -> SchemeTonalSpot(hct, dark, 0.0)
        PaletteStyle.Neutral -> SchemeNeutral(hct, dark, 0.0)
        PaletteStyle.Monochrome -> SchemeMonochrome(hct, dark, 0.0)
    }
    val d = MaterialDynamicColors()
    fun role(c: DynamicColor) = Color(c.getArgb(scheme))
    return kikoColorsFromRoles(
        primary = role(d.primary()), onPrimary = role(d.onPrimary()),
        primaryContainer = role(d.primaryContainer()), onPrimaryContainer = role(d.onPrimaryContainer()),
        secondary = role(d.secondary()), onSecondary = role(d.onSecondary()),
        secondaryContainer = role(d.secondaryContainer()), onSecondaryContainer = role(d.onSecondaryContainer()),
        tertiary = role(d.tertiary()), onTertiary = role(d.onTertiary()),
        tertiaryContainer = role(d.tertiaryContainer()), onTertiaryContainer = role(d.onTertiaryContainer()),
        error = role(d.error()), onError = role(d.onError()),
        errorContainer = role(d.errorContainer()), onErrorContainer = role(d.onErrorContainer()),
        background = role(d.background()), onSurface = role(d.onSurface()),
        surface = role(d.surface()), onSurfaceVariant = role(d.onSurfaceVariant()),
        surfaceContainerLowest = role(d.surfaceContainerLowest()), surfaceContainerLow = role(d.surfaceContainerLow()),
        surfaceContainer = role(d.surfaceContainer()), surfaceContainerHigh = role(d.surfaceContainerHigh()),
        surfaceContainerHighest = role(d.surfaceContainerHighest()),
        outline = role(d.outline()), outlineVariant = role(d.outlineVariant()),
        inverseSurface = role(d.inverseSurface()), inverseOnSurface = role(d.inverseOnSurface()),
        inversePrimary = role(d.inversePrimary()),
    )
}

/** Maps standard M3 roles onto Kiko's legacy field names so existing screens keep compiling. */
private fun kikoColorsFromRoles(
    primary: Color, onPrimary: Color, primaryContainer: Color, onPrimaryContainer: Color,
    secondary: Color, onSecondary: Color, secondaryContainer: Color, onSecondaryContainer: Color,
    tertiary: Color, onTertiary: Color, tertiaryContainer: Color, onTertiaryContainer: Color,
    error: Color, onError: Color, errorContainer: Color, onErrorContainer: Color,
    background: Color, onSurface: Color, surface: Color, onSurfaceVariant: Color,
    surfaceContainerLowest: Color, surfaceContainerLow: Color, surfaceContainer: Color,
    surfaceContainerHigh: Color, surfaceContainerHighest: Color,
    outline: Color, outlineVariant: Color,
    inverseSurface: Color, inverseOnSurface: Color, inversePrimary: Color,
) = KikoColors(
    ink = onSurface, onPrimary = onPrimary, primary = primary, primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    background = background, surface = surface,
    surfaceLow = surfaceContainerHigh,          // pills / progress tracks: M3 "surfaceContainerHigh"
    muted = onSurfaceVariant,                   // secondary text: M3 "onSurfaceVariant"
    lavender = tertiaryContainer,               // accent surfaces come from real M3 containers now
    warm = secondaryContainer,                  // avatar placeholders etc.
    danger = error,
    secondary = secondary, onSecondary = onSecondary,
    secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary, onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
    surfaceContainerLowest = surfaceContainerLowest, surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer, surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    onSurfaceVariant = onSurfaceVariant, outline = outline, outlineVariant = outlineVariant,
    errorContainer = errorContainer, onError = onError, onErrorContainer = onErrorContainer,
    inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface, inversePrimary = inversePrimary,
    scrim = Color.Black,
    cardBorder = outlineVariant.copy(alpha = 0.5f),
)

/** Wallpaper (Dynamic) + Tonal Spot: use the system's own M3 scheme as-is, no re-derivation. */
fun systemDynamicPalette(context: Context, dark: Boolean): KikoColors {
    val s = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return kikoColorsFromRoles(
        s.primary, s.onPrimary, s.primaryContainer, s.onPrimaryContainer,
        s.secondary, s.onSecondary, s.secondaryContainer, s.onSecondaryContainer,
        s.tertiary, s.onTertiary, s.tertiaryContainer, s.onTertiaryContainer,
        s.error, s.onError, s.errorContainer, s.onErrorContainer,
        s.background, s.onSurface, s.surface, s.onSurfaceVariant,
        s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer,
        s.surfaceContainerHigh, s.surfaceContainerHighest,
        s.outline, s.outlineVariant, s.inverseSurface, s.inverseOnSurface, s.inversePrimary,
    )
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

// True-black variant for AMOLED. The page stays pure black so OLED pixels switch off, but every layer above it
// is the normal dark-mode surface ladder pulled a little darker (not re-derived from the accent), so tonal rows
// stay clearly visible on black and still carry the palette's own tint.
//
// NOTE: the surface steps are mixed in plain sRGB on purpose. Compose's `lerp(Color, Color, f)` blends in Oklab,
// where a fraction of the way towards a light accent is a tiny fraction of its *lightness*; the old
// `lerp(Black, accent, 0.12f)` landed on RGB(2,3,5), which is black to the eye, so tonal rows vanished.
private const val AMOLED_SURFACE_SCALE = 0.85f   // 1.0 = same as normal dark mode; lower = darker rows

private fun oledSurface(c: Color): Color =
    Color(red = c.red * AMOLED_SURFACE_SCALE, green = c.green * AMOLED_SURFACE_SCALE, blue = c.blue * AMOLED_SURFACE_SCALE, alpha = 1f)
private fun oledContainer(c: Color): Color = androidx.compose.ui.graphics.lerp(Color.Black, c, 0.62f)

fun amoledify(colors: KikoColors): KikoColors {
    val a = colors.primary
    val secondaryContainer = oledContainer(colors.secondaryContainer)
    val high = oledSurface(colors.surfaceContainerHigh)
    return colors.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceLow = high,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = oledSurface(colors.surfaceContainerLow),
        surfaceContainer = oledSurface(colors.surfaceContainer),
        surfaceContainerHigh = high,
        surfaceContainerHighest = oledSurface(colors.surfaceContainerHighest),
        primaryContainer = oledContainer(colors.primaryContainer),
        secondaryContainer = secondaryContainer,
        tertiaryContainer = oledContainer(colors.tertiaryContainer),
        lavender = oledContainer(colors.tertiaryContainer),
        warm = secondaryContainer,
        outlineVariant = androidx.compose.ui.graphics.lerp(colors.outlineVariant, a, 0.22f),
        cardBorder = a.copy(alpha = .30f),
    )
}

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