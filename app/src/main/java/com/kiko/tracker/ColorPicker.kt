package com.kiko.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// A self-contained HSV color picker: saturation/value square + hue rail,
// built from the same primitives as the rest of Kiko (RoundedCornerShape
// cards, LocalKikoColors, no Material defaults) so it reads as part of the
// app rather than a bolted-on system widget.
// ---------------------------------------------------------------------------

/**
 * @param color current selected color (drives the picker's initial hue/sat/value)
 * @param onColorChange called continuously while dragging with the resulting color
 */
@Composable
fun HsvColorPicker(color: Color, onColorChange: (Color) -> Unit, modifier: Modifier = Modifier) {
    // Hue/sat/value are kept as local state rather than re-derived from `color`
    // on every recomposition — grey/white/black inputs have an undefined hue,
    // which would otherwise make the hue rail's thumb jump around while dragging
    // through the neutral column.
    val initial = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var sat by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2]) }

    // Resync only when `color` changed for a reason other than our own emit
    // (e.g. the user typed a hex value directly) — guarded so we don't fight
    // the drag gestures below with our own rounding.
    LaunchedEffect(color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        if (abs(hsv[0] - hue) > 0.75f || abs(hsv[1] - sat) > 0.006f || abs(hsv[2] - value) > 0.006f) {
            hue = hsv[0]; sat = hsv[1]; value = hsv[2]
        }
    }

    fun emit(h: Float = hue, s: Float = sat, v: Float = value) {
        onColorChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))))
    }

    Column(modifier) {
        SaturationValueSquare(hue = hue, sat = sat, value = value) { s, v ->
            sat = s; value = v; emit(s = s, v = v)
        }
        Spacer(Modifier.height(14.dp))
        HueRail(hue = hue) { h -> hue = h; emit(h = h) }
    }
}

@Composable
private fun SaturationValueSquare(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val c = LocalKikoColors.current
    val hueColor = remember(hue) { Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    fun update(pos: Offset) {
        if (boxSize.width == 0 || boxSize.height == 0) return
        val s = (pos.x / boxSize.width).coerceIn(0f, 1f)
        val v = 1f - (pos.y / boxSize.height).coerceIn(0f, 1f)
        onChange(s, v)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(kikoCorner(18.dp)))
            .background(hueColor)
            .background(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black)))
            .border(1.dp, c.muted.copy(alpha = .18f), RoundedCornerShape(kikoCorner(18.dp)))
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) { detectTapGestures { update(it) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); update(change.position) } }
    ) {
        Box(
            Modifier
                .offset {
                    val r = 12.dp.toPx()
                    IntOffset((sat.coerceIn(0f, 1f) * boxSize.width - r).roundToInt(), ((1f - value.coerceIn(0f, 1f)) * boxSize.height - r).roundToInt())
                }
                .size(24.dp)
                .clip(kikoCircleShape())
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = .25f), kikoCircleShape())
                .padding(3.dp)
                .clip(kikoCircleShape())
                .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))),
        )
    }
}

@Composable
private fun HueRail(hue: Float, onChange: (Float) -> Unit) {
    val c = LocalKikoColors.current
    var trackWidth by remember { mutableStateOf(0) }
    val rainbow = remember {
        (0..6).map { step -> Color(android.graphics.Color.HSVToColor(floatArrayOf((step * 60).coerceAtMost(360).toFloat(), 1f, 1f))) }
    }

    fun update(pos: Offset) {
        if (trackWidth == 0) return
        onChange((pos.x / trackWidth).coerceIn(0f, 1f) * 360f)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(kikoPillShape())
            .background(Brush.horizontalGradient(rainbow))
            .border(1.dp, c.muted.copy(alpha = .18f), kikoPillShape())
            .onSizeChanged { trackWidth = it.width }
            .pointerInput(Unit) { detectTapGestures { update(it) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); update(change.position) } },
    ) {
        Box(
            Modifier
                .offset {
                    val r = 14.dp.toPx()
                    IntOffset(((hue / 360f) * trackWidth - r).roundToInt(), 0)
                }
                .size(28.dp)
                .clip(kikoCircleShape())
                .background(Color.White)
                .border(2.5.dp, Color.Black.copy(alpha = .2f), kikoCircleShape())
                .padding(3.dp)
                .clip(kikoCircleShape())
                .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))),
        )
    }
}

