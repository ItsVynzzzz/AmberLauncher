package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.components.amberTap
import com.israadev.nuxlauncher.ui.components.drawGamepadFocusRing
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.AmberUi
import kotlin.math.roundToInt

/*
 * Kontrol untuk menu dalam game (GameMenuSheet) dengan gaya Amber yang sekarang: aksen mengikuti
 * pilihan pengguna (AmberUi), isian tipis tanpa garis tepi, baris dipisah garis halus. Sama seperti
 * versi lamanya, semuanya digambar langsung di kanvas tanpa ripple/blur/animasi, dan status
 * tekan/geser hanya dibaca saat menggambar, supaya menyentuh menu tidak membebani game yang
 * sedang berjalan di belakangnya.
 */

/** Kelompok pengaturan: satu kartu tipis; isinya dipisah dengan [MenuDivider]. */
@Composable
internal fun MenuGroup(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AmberUi.SurfaceRaised)
            .padding(horizontal = 14.dp),
        content = content
    )
}

/** Garis halus antar baris dalam satu [MenuGroup]. */
@Composable
internal fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AmberUi.Border)
    )
}

/** Baris judul + keterangan dengan saklar di kanan. Seluruh baris bisa diketuk (dan disorot controller). */
@Composable
internal fun MenuToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .amberTap { onChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AmberUi.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = AmberUi.TextDim,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        MenuSwitch(checked)
    }
}

/** Gambar saklar saja (tidak bisa diketuk sendiri; barisnya yang menangani ketukan). */
@Composable
private fun MenuSwitch(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 24.dp)
            .drawBehind {
                val r = size.height / 2f
                val sw = 1.dp.toPx()
                if (checked) {
                    drawRoundRect(color = AmberUi.Accent, cornerRadius = CornerRadius(r))
                } else {
                    drawRoundRect(color = AmberUi.SurfaceRaised, cornerRadius = CornerRadius(r))
                }
                drawRoundRect(
                    color = if (checked) AmberUi.AccentEdge else AmberUi.BorderStrong,
                    topLeft = Offset(sw / 2f, sw / 2f),
                    size = Size(size.width - sw, size.height - sw),
                    cornerRadius = CornerRadius(r - sw / 2f),
                    style = Stroke(width = sw)
                )
                drawCircle(
                    color = if (checked) Color.White else AmberUi.TextDim,
                    radius = r - 5.dp.toPx(),
                    center = Offset(if (checked) size.width - r else r, r)
                )
            }
    )
}

/** Satu baris penggeser. Nilai sementara disimpan di sini supaya menggeser tidak menyusun ulang menu. */
@Composable
internal fun MenuSliderRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onCommit: (Int) -> Unit
) {
    var live by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = AmberUi.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${live.roundToInt()}%",
                color = AmberUi.AccentSoft,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
        MenuSlider(
            value = { live },
            range = min.toFloat()..max.toFloat(),
            step = step.toFloat(),
            onChange = { live = it },
            onFinished = { onCommit(live.roundToInt()) }
        )
    }
}

/**
 * Penggeser tipis satu simpul gambar: garis redup, bagian terisi berwarna aksen terang, bulatan
 * putih. [value] dibaca saat menggambar, jadi menggeser hanya menggambar ulang penggeser ini.
 */
@Composable
private fun MenuSlider(
    value: () -> Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinished)
    val read by rememberUpdatedState(value)
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            // D-pad kiri/kanan menggeser satu langkah saat penggeser disorot.
            .onKeyEvent { event ->
                val dir = when (event.key) {
                    Key.DirectionLeft -> -1f
                    Key.DirectionRight -> 1f
                    else -> 0f
                }
                if (dir == 0f) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyDown) {
                        val unit = if (step > 0f) step else (range.endInclusive - range.start) / 20f
                        change((read() + dir * unit).coerceIn(range.start, range.endInclusive))
                        finish()
                    }
                    true
                }
            }
            .focusable(interactionSource = interaction)
            .drawBehind {
                val pad = 10.dp.toPx()
                val y = size.height / 2f
                val span = size.width - 2f * pad
                val frac = ((read() - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                val sw = 3.dp.toPx()
                drawLine(AmberUi.BorderStrong, Offset(pad, y), Offset(pad + span, y), sw, StrokeCap.Round)
                if (frac > 0f) {
                    drawLine(AmberUi.AccentSoft, Offset(pad, y), Offset(pad + span * frac, y), sw, StrokeCap.Round)
                }
                val c = Offset(pad + span * frac, y)
                drawCircle(AmberUi.AccentSoft.copy(alpha = 0.22f), 10.dp.toPx(), c)
                drawCircle(Color.White, 6.dp.toPx(), c)
                if (focused.value) drawGamepadFocusRing()
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

/** Pilihan 2-3 segmen berbentuk pil; segmen terpilih berwarna aksen pengguna. */
@Composable
internal fun MenuSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(CircleShape)
            .background(Color(0x40000000))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .then(
                        if (on) {
                            Modifier
                                .background(AmberUi.Accent)
                                .border(1.dp, AmberUi.AccentEdge, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .amberTap { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (on) Color.White else AmberUi.TextDim,
                    fontSize = 11.5.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
