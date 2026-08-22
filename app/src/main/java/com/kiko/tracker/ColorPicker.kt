package com.kiko.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// A self-contained HSV color picker: a single hue/saturation wheel (angle =
// hue, radius = saturation), built from the same primitives as the rest of
// Kiko (LocalKikoColors, no Material defaults) so it reads as part of the app
// rather than a bolted-on system widget.
//
// Brightness ("value") is fixed at full (1f) and isn't exposed as a control:
// Material's dynamic color pipeline derives its own tonal palette from a
// color's hue/chroma and normalizes lightness itself, so a separate
// brightness axis here wouldn't actually carry through to the app's theme --
// it'd just be a control that visually does something without affecting the
// result.
// ---------------------------------------------------------------------------

/**
 * @param color current selected color (drives the wheel's initial hue/sat)
 * @param onColorChange called continuously while dragging with the resulting color -- cheap
 *   local UI updates only (e.g. moving the wheel's own thumb/swatch). Fires once per pointer
 *   move, which on a fast finger drag can be dozens of times a second.
 * @param onColorChangeFinished called once when a drag lifts (or once per tap) with the final
 *   resulting color -- the right place for anything comparatively expensive, like persisting
 *   to disk or feeding a value that other composables key a `remember`/theme rebuild on,
 *   since those shouldn't re-run on every intermediate pointer-move frame of the same drag.
 */
@Composable
fun HsvColorPicker(color: Color, onColorChange: (Color) -> Unit, onColorChangeFinished: (Color) -> Unit = {}, modifier: Modifier = Modifier) {
    // Hue/sat are kept as local state rather than re-derived from `color` on
    // every recomposition — a grey/white input has an undefined hue, which
    // would otherwise make the wheel's thumb jump around while dragging
    // through the neutral center.
    val initial = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var sat by remember { mutableFloatStateOf(initial[1]) }

    // Resync only when `color` changed for a reason other than our own emit
    // (e.g. the user typed a hex value directly) — guarded so we don't fight
    // the drag gesture below with our own rounding.
    LaunchedEffect(color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        if (abs(hsv[0] - hue) > 0.75f || abs(hsv[1] - sat) > 0.006f) {
            hue = hsv[0]; sat = hsv[1]
        }
    }

    fun emit(h: Float = hue, s: Float = sat) {
        onColorChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, 1f))))
    }
    fun emitFinished(h: Float = hue, s: Float = sat) {
        onColorChangeFinished(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, 1f))))
    }

    ColorWheel(
        hue = hue, sat = sat,
        onChange = { h, s -> hue = h; sat = s; emit(h = h, s = s) },
        onChangeFinished = { h, s -> hue = h; sat = s; emitFinished(h = h, s = s) },
        modifier = modifier,
    )
}

@Composable
private fun ColorWheel(hue: Float, sat: Float, onChange: (Float, Float) -> Unit, onChangeFinished: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    // Same seven-stop rainbow used by the old hue rail, spread evenly around
    // the wheel so hue is read off the angle exactly as before.
    val rainbow = remember {
        (0..6).map { step -> Color(android.graphics.Color.HSVToColor(floatArrayOf((step * 60).coerceAtMost(360).toFloat(), 1f, 1f))) }
    }
    var wheelSize by remember { mutableStateOf(0) }

    // angle: degrees, 0 at 3 o'clock increasing clockwise -- matches
    // Brush.sweepGradient's convention, so the thumb always sits on its own hue.
    // radius: 0 (center, desaturated) .. 1 (edge, fully saturated).
    fun resolve(pos: Offset): Pair<Float, Float>? {
        if (wheelSize == 0) return null
        val r = wheelSize / 2f
        val dx = pos.x - r
        val dy = pos.y - r
        val dist = sqrt(dx * dx + dy * dy)
        val angle = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f
        val radius = (dist / r).coerceIn(0f, 1f)
        return angle to radius
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(kikoCircleShape())
            .background(Brush.sweepGradient(rainbow))
            // Desaturate toward the center: white fading out to transparent
            // from the middle to the rim, layered the same way the old
            // saturation/value square stacked its gradients.
            .background(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
            .border(1.dp, c.muted.copy(alpha = .18f), kikoCircleShape())
            .onSizeChanged { wheelSize = min(it.width, it.height) }
            // A tap is a single instantaneous placement -- there's no separate "in-progress"
            // phase to spare from the expensive path, so it goes straight to onChangeFinished.
            .pointerInput(Unit) { detectTapGestures { pos -> resolve(pos)?.let { (h, s) -> onChangeFinished(h, s) } } }
            // While actually dragging, every intermediate move goes through the cheap
            // onChange (local thumb position only); onChangeFinished fires once, when the
            // finger lifts, which is the only point that should trigger anything expensive
            // downstream (persisting the color, rebuilding the app theme, ...).
            .pointerInput(Unit) {
                var lastResolved: Pair<Float, Float>? = null
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        resolve(change.position)?.let { lastResolved = it; onChange(it.first, it.second) }
                    },
                    onDragEnd = { lastResolved?.let { (h, s) -> onChangeFinished(h, s) } },
                )
            }
    ) {
        Box(
            Modifier
                .offset {
                    val r = wheelSize / 2f
                    val thumbR = 12.dp.toPx()
                    val angleRad = Math.toRadians(hue.toDouble())
                    val dist = sat.coerceIn(0f, 1f) * r
                    val x = r + dist * cos(angleRad).toFloat()
                    val y = r + dist * sin(angleRad).toFloat()
                    IntOffset((x - thumbR).roundToInt(), (y - thumbR).roundToInt())
                }
                .size(24.dp)
                .clip(kikoCircleShape())
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = .25f), kikoCircleShape())
                .padding(3.dp)
                .clip(kikoCircleShape())
                .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f)))),
        )
    }
}