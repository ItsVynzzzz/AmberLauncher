package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.control.drawControlIcon
import com.israadev.nuxlauncher.ui.theme.Amber
import kotlin.math.roundToInt

/**
 * Bahan dasar gaya AmberLauncher untuk dialog dan panel di dalam game. Semuanya digambar langsung
 * di kanvas, tanpa ripple, tanpa blur, dan status tekan dibaca hanya saat menggambar, jadi
 * menyentuh sesuatu di menu tidak menyusun ulang layar sementara game masih berjalan.
 */

/** Ketuk dengan umpan balik tekan (lapisan putih tipis). Pasang setelah clip bentuknya. */
@Composable
fun Modifier.amberTap(onClick: () -> Unit): Modifier {
    val callback by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    return this
        .drawWithContent {
            drawContent()
            if (pressed) drawRect(Amber.Press)
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { callback() }
            )
        }
}

/** Ikon garis buatan sendiri (lihat drawControlIcon di ControlSkin). */
@Composable
fun AmberIcon(
    name: String,
    iconSize: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    slash: Boolean = false
) {
    Box(
        modifier = modifier
            .size(iconSize)
            .drawBehind {
                drawControlIcon(name, size.width / 2f, size.height / 2f, size.minDimension, color, slash)
            }
    )
}

fun fpsColor(value: Int): Color = when {
    value >= 50 -> Amber.Good
    value >= 25 -> Amber.Yellow
    value > 0 -> Amber.Danger
    else -> Amber.TextFaint
}

/** Titik warna + angka FPS. Angka dibaca lewat lambda di sini saja. */
@Composable
fun FpsReadout(fps: () -> Int) {
    val value = fps()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(fpsColor(value), CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (value > 0) "$value FPS" else "-- FPS",
            color = Amber.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Saklar tipis: kuning saat aktif. */
@Composable
fun AmberToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val callback by rememberUpdatedState(onChange)
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 24.dp)
            .drawBehind {
                val r = size.height / 2f
                if (checked) {
                    drawRoundRect(color = Amber.Yellow, cornerRadius = CornerRadius(r))
                } else {
                    val sw = 1.dp.toPx()
                    drawRoundRect(color = Amber.Fill, cornerRadius = CornerRadius(r))
                    drawRoundRect(
                        color = Amber.LineStrong,
                        topLeft = Offset(sw / 2f, sw / 2f),
                        size = Size(size.width - sw, size.height - sw),
                        cornerRadius = CornerRadius(r - sw / 2f),
                        style = Stroke(width = sw)
                    )
                }
                val cx = if (checked) size.width - r else r
                drawCircle(
                    color = if (checked) Amber.Ink else Amber.TextDim,
                    radius = r - 5.dp.toPx(),
                    center = Offset(cx, r)
                )
            }
            .pointerInput(checked) { detectTapGestures { callback(!checked) } }
    )
}

/**
 * Penggeser tipis satu simpul gambar. [value] dibaca saat menggambar, jadi menggeser hanya
 * menggambar ulang penggeser itu sendiri.
 */
@Composable
fun AmberSlider(
    value: () -> Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinished)
    val read by rememberUpdatedState(value)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .drawBehind {
                val pad = 10.dp.toPx()
                val y = size.height / 2f
                val span = size.width - 2f * pad
                val frac = ((read() - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                val sw = 3.dp.toPx()
                drawLine(Amber.LineStrong, Offset(pad, y), Offset(pad + span, y), sw, StrokeCap.Round)
                if (frac > 0f) {
                    drawLine(Amber.Yellow, Offset(pad, y), Offset(pad + span * frac, y), sw, StrokeCap.Round)
                }
                val c = Offset(pad + span * frac, y)
                drawCircle(Amber.YellowTint, 10.dp.toPx(), c)
                drawCircle(Amber.Yellow, 6.5.dp.toPx(), c)
            }
            .pointerInput(range, step) {
                val pad = 10.dp.toPx()
                fun toValue(x: Float): Float {
                    val span = size.width - 2f * pad
                    val f = if (span <= 0f) 0f else ((x - pad) / span).coerceIn(0f, 1f)
                    val raw = range.start + f * (range.endInclusive - range.start)
                    return if (step > 0f) {
                        ((raw / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)
                    } else {
                        raw
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    change(toValue(down.position.x))
                    while (true) {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        change(toValue(ch.position.x))
                    }
                    finish()
                }
            }
    )
}

/** Pilihan 2-3 segmen: segmen terpilih kuning dengan teks tinta. */
@Composable
fun AmberSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val outer = RoundedCornerShape(10.dp)
    val inner = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(outer)
            .background(Amber.Fill)
            .border(1.dp, Amber.Line, outer)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(inner)
                    .background(if (on) Amber.Yellow else Color.Transparent)
                    .amberTap { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (on) Amber.Ink else Amber.TextDim,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
