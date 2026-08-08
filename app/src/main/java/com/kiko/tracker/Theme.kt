@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import kotlin.math.roundToInt

// Palette section
@Immutable

data class KikoColors(
    val ink: Color, val onPrimary: Color, val primary: Color, val primaryContainer: Color,
    val background: Color, val surface: Color, val surfaceLow: Color, val muted: Color,
    val lavender: Color, val warm: Color, val danger: Color
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

val LocalKikoColors = staticCompositionLocalOf { LightKiko }

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
