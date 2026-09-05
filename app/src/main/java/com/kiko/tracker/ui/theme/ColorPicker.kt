package com.kiko.tracker.ui.theme

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
// A self-contained HSV color
// hue, radius = saturation),
// Kiko (LocalKikoColors, no Material
// rather than a bolted-on
//
// Brightness ("value") is fixed
// Material's dynamic color pipeline
// color's hue/chroma and normalizes
// brightness axis here wouldn't
// it'd just be a
// result.
// ---------------------------------------------------------------------------

/** @param color current selected */
@Composable
fun HsvColorPicker(color: Color, onColorChange: (Color) -> Unit, onColorChangeFinished: (Color) -> Unit = {}, modifier: Modifier = Modifier) {
    // Hue/sat are kept as
    // every recomposition — a
    // would otherwise make the
    // through the neutral center.
    val initial = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var sat by remember { mutableFloatStateOf(initial[1]) }

    // Resync only when `color`
    // (e.g. the user typed
    // the drag gesture below
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
    // Same seven-stop rainbow used
    // the wheel so hue
    val rainbow = remember {
        (0..6).map { step -> Color(android.graphics.Color.HSVToColor(floatArrayOf((step * 60).coerceAtMost(360).toFloat(), 1f, 1f))) }
    }
    var wheelSize by remember { mutableStateOf(0) }

    // angle: degrees, 0 at
    // Brush.sweepGradient's convention, so the
    // radius: 0 (center, desaturated)
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
            // Desaturate toward the center:
            // from the middle to
            // saturation/value square stacked its
            .background(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
            .border(1.dp, c.muted.copy(alpha = .18f), kikoCircleShape())
            .onSizeChanged { wheelSize = min(it.width, it.height) }
            // A tap is a
            // phase to spare from
            .pointerInput(Unit) { detectTapGestures { pos -> resolve(pos)?.let { (h, s) -> onChangeFinished(h, s) } } }
            // While actually dragging, every
            // onChange (local thumb position
            // finger lifts, which is
            // downstream (persisting the color,
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