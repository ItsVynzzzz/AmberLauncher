package com.israadev.nuxlauncher.ui.control

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Tampilan semua tombol layar: kaca sangat tipis dengan garis halus, berubah kuning lembut saat
 * ditekan. Tombol sistem (menu, keyboard, sembunyikan GUI) tanpa garis sama sekali: hanya ikon di
 * atas bulatan gelap tipis. Dipakai bersama oleh game dan editor tata letak supaya yang terlihat
 * di editor sama persis dengan di dalam game.
 *
 * Semua digambar langsung di kanvas (tanpa lapisan transparansi per tombol, tanpa gambar atau
 * font tambahan) dan status tekan dibaca hanya saat menggambar, jadi menekan tombol tidak
 * menyusun ulang apa pun.
 */
object ControlSkin {
    val Ink = Color(0xFF111111)
    val Yellow = Color(0xFFFFD60A)
    val YellowSoft = Color(0xFFFFE566)
}

/** Ringkasan satu tombol untuk digambar. */
@Immutable
class SkinSpec(
    val label: String,
    val icon: String,
    val primary: Boolean,
    val system: Boolean,
    val opacity: Float,
    val cornerDp: Float,
    val slash: Boolean = false
)

fun skinSpecFor(b: CustomControlButton, iconOverride: String? = null, slash: Boolean = false): SkinSpec {
    val icon = iconOverride ?: when {
        b.isSystem && (b.systemAction == "CLOSE" || b.systemAction == "MENU") -> "gear"
        b.icon.isNotBlank() -> b.icon
        b.isSystem -> when (b.systemAction) {
            "KEYBOARD" -> "keyboard"
            "HIDE_GUI" -> "eye"
            // "CLOSE" adalah nama lama tombol keluar; sekarang tombol ini hanya membuka menu.
            "CLOSE", "MENU" -> "gear"
            else -> ""
        }
        else -> ""
    }
    return SkinSpec(
        label = b.name,
        icon = icon,
        primary = !b.isSystem && b.heightDp >= 56,
        system = b.isSystem,
        opacity = b.opacity.coerceIn(0.1f, 1f),
        cornerDp = b.cornerRadiusDp.toFloat(),
        slash = slash
    )
}

// Huruf bobot sedang dengan jarak antarhuruf sedikit renggang: lebih tenang daripada huruf tebal.
private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    textAlign = Paint.Align.CENTER
    letterSpacing = 0.06f
}

/** Tombol biasa: isian kaca, garis putih; ditekan atau aktif = kuning penuh. */
fun DrawScope.drawControlButton(spec: SkinSpec, pressed: Boolean, selected: Boolean = false) {
    drawButtonBase(spec, pressed, selected)

    val w = size.width
    val h = size.height
    val k = spec.opacity
    val glyph = when {
        spec.system -> if (pressed) ControlSkin.Yellow else Color.White.copy(alpha = 0.58f + 0.32f * k)
        pressed -> ControlSkin.Ink
        else -> Color.White.copy(alpha = 0.45f + 0.40f * k)
    }
    if (spec.icon.isNotEmpty()) {
        val s = min(w, h) * (if (spec.primary) 0.52f else if (spec.system) 0.56f else 0.58f)
        drawControlIcon(spec.icon, w / 2f, h / 2f, s, glyph, spec.slash)
    } else {
        drawControlLabel(spec.label, w / 2f, h / 2f, w * 0.86f, h, glyph)
    }
}

/** Widget gulir hotbar: dua panah dan nama kecil di tengah. */
fun DrawScope.drawControlScroll(spec: SkinSpec, pressed: Boolean, selected: Boolean = false) {
    drawButtonBase(spec, pressed, selected)

    val w = size.width
    val h = size.height
    val k = spec.opacity
    val glyph = if (pressed) ControlSkin.Ink else Color.White.copy(alpha = 0.45f + 0.40f * k)
    val s = min(w * 0.56f, h * 0.24f)
    drawControlIcon("chev_up", w / 2f, h * 0.22f, s, glyph)
    drawControlIcon("chev_down", w / 2f, h * 0.78f, s, glyph)
    if (spec.label.isNotEmpty()) {
        drawControlLabel(spec.label.take(6), w / 2f, h / 2f, w * 0.80f, h * 0.5f, glyph.copy(alpha = glyph.alpha * 0.75f))
    }
}

private fun DrawScope.drawButtonBase(spec: SkinSpec, pressed: Boolean, selected: Boolean) {
    val w = size.width
    val h = size.height
    val k = spec.opacity
    val radius = min(spec.cornerDp.dp.toPx(), min(w, h) / 2f)

    if (spec.system) {
        // Tombol sistem: bulatan gelap tipis tanpa garis; saat ditekan atau aktif jadi tint kuning.
        val ghost = when {
            pressed -> ControlSkin.Yellow.copy(alpha = 0.26f)
            selected -> ControlSkin.Yellow.copy(alpha = 0.16f)
            else -> Color.Black.copy(alpha = 0.20f * (0.4f + 0.6f * k))
        }
        drawRoundRect(color = ghost, cornerRadius = CornerRadius(radius))
        if (selected) {
            val ow = 1.6f.dp.toPx()
            drawRoundRect(
                color = ControlSkin.Yellow,
                topLeft = Offset(ow / 2f, ow / 2f),
                size = Size(w - ow, h - ow),
                cornerRadius = CornerRadius(max(radius - ow / 2f, 0f)),
                style = Stroke(width = ow)
            )
        }
        return
    }

    val fill = when {
        pressed -> ControlSkin.Yellow.copy(alpha = 0.34f + 0.30f * k)
        selected -> ControlSkin.Yellow.copy(alpha = 0.18f)
        else -> Color.Black.copy(alpha = (if (spec.primary) 0.30f else 0.22f) * (0.4f + 0.6f * k))
    }
    val edge = when {
        pressed -> ControlSkin.YellowSoft.copy(alpha = 0.85f)
        selected -> ControlSkin.Yellow
        else -> Color.White.copy(alpha = (if (spec.primary) 0.22f else 0.16f) + 0.14f * k)
    }
    val sw = (if (selected) 2.2f else if (spec.primary) 1.3f else 1.2f).dp.toPx()

    drawRoundRect(color = fill, cornerRadius = CornerRadius(radius))
    drawRoundRect(
        color = edge,
        topLeft = Offset(sw / 2f, sw / 2f),
        size = Size(w - sw, h - sw),
        cornerRadius = CornerRadius(max(radius - sw / 2f, 0f)),
        style = Stroke(width = sw)
    )
}

private fun DrawScope.drawControlLabel(
    text: String,
    cx: Float,
    cy: Float,
    maxW: Float,
    maxH: Float,
    color: Color
) {
    val shown = text.take(9)
    val len = max(shown.length, 1)
    val size = min(maxH * 0.36f, maxW / (len * 0.74f)).coerceIn(8.dp.toPx(), 13.dp.toPx())
    labelPaint.textSize = size
    labelPaint.color = color.toArgb()
    val fm = labelPaint.fontMetrics
    val baseline = cy - (fm.ascent + fm.descent) / 2f
    drawIntoCanvas { it.nativeCanvas.drawText(shown, cx, baseline, labelPaint) }
}

/**
 * Ikon garis sederhana dalam kotak [s] berpusat di ([cx], [cy]). Nama: jump, sneak, attack, use,
 * inventory, chat, pause, keyboard, eye, close, gear, sliders, terminal, power, chev_up, chev_down,
 * chev_right.
 */
fun DrawScope.drawControlIcon(name: String, cx: Float, cy: Float, s: Float, color: Color, slash: Boolean = false) {
    val x0 = cx - s / 2f
    val y0 = cy - s / 2f
    val sw = s * 0.078f
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun p(u: Float, v: Float) = Offset(x0 + u * s, y0 + v * s)
    fun line(a: Offset, b: Offset, width: Float = sw) =
        drawLine(color, a, b, strokeWidth = width, cap = StrokeCap.Round)
    // Titik diberikan sebagai pasangan u, v (0..1) dalam FloatArray; Offset tidak boleh jadi vararg.
    fun poly(closed: Boolean, uv: FloatArray) {
        val path = Path()
        path.moveTo(x0 + uv[0] * s, y0 + uv[1] * s)
        for (i in 1 until uv.size / 2) path.lineTo(x0 + uv[2 * i] * s, y0 + uv[2 * i + 1] * s)
        if (closed) path.close()
        drawPath(path, color, style = stroke)
    }

    when (name) {
        "jump" -> {
            line(p(0.5f, 0.82f), p(0.5f, 0.26f))
            poly(false, floatArrayOf(0.26f, 0.50f, 0.5f, 0.26f, 0.74f, 0.50f))
        }
        "sneak" -> {
            line(p(0.5f, 0.18f), p(0.5f, 0.74f))
            poly(false, floatArrayOf(0.26f, 0.50f, 0.5f, 0.74f, 0.74f, 0.50f))
        }
        "attack" -> {
            line(p(0.30f, 0.70f), p(0.80f, 0.20f), sw * 1.35f)
            line(p(0.26f, 0.54f), p(0.46f, 0.74f))
            line(p(0.30f, 0.70f), p(0.20f, 0.80f))
        }
        "use" -> {
            poly(
                true,
                floatArrayOf(0.50f, 0.20f, 0.76f, 0.35f, 0.76f, 0.65f, 0.50f, 0.80f, 0.24f, 0.65f, 0.24f, 0.35f))
            poly(false, floatArrayOf(0.24f, 0.35f, 0.50f, 0.50f, 0.76f, 0.35f))
            line(p(0.50f, 0.50f), p(0.50f, 0.80f))
        }
        "inventory" -> {
            val q = Size(0.24f * s, 0.24f * s)
            val cr = CornerRadius(0.05f * s)
            drawRoundRect(color, p(0.22f, 0.22f), q, cr, stroke)
            drawRoundRect(color, p(0.54f, 0.22f), q, cr, stroke)
            drawRoundRect(color, p(0.22f, 0.54f), q, cr, stroke)
            drawRoundRect(color, p(0.54f, 0.54f), q, cr, stroke)
        }
        "chat" -> {
            drawRoundRect(color, p(0.18f, 0.22f), Size(0.64f * s, 0.42f * s), CornerRadius(0.09f * s), stroke)
            poly(false, floatArrayOf(0.34f, 0.64f, 0.32f, 0.80f, 0.52f, 0.64f))
        }
        "pause" -> {
            val bar = Size(0.14f * s, 0.52f * s)
            val cr = CornerRadius(0.04f * s)
            drawRoundRect(color, p(0.30f, 0.24f), bar, cr)
            drawRoundRect(color, p(0.56f, 0.24f), bar, cr)
        }
        "keyboard" -> {
            drawRoundRect(color, p(0.14f, 0.30f), Size(0.72f * s, 0.40f * s), CornerRadius(0.07f * s), stroke)
            for (u in floatArrayOf(0.28f, 0.41f, 0.54f, 0.67f)) {
                drawCircle(color, 0.032f * s, p(u, 0.43f))
            }
            line(p(0.32f, 0.58f), p(0.68f, 0.58f))
        }
        "eye" -> {
            drawOval(color, p(0.12f, 0.32f), Size(0.76f * s, 0.36f * s), style = stroke)
            drawCircle(color, 0.10f * s, p(0.5f, 0.5f))
            if (slash) line(p(0.22f, 0.80f), p(0.78f, 0.20f))
        }
        "close" -> {
            line(p(0.28f, 0.28f), p(0.72f, 0.72f))
            line(p(0.72f, 0.28f), p(0.28f, 0.72f))
        }
        "gear" -> {
            // Cincin, lubang tengah, dan delapan gigi pendek di sekelilingnya.
            val c = p(0.5f, 0.5f)
            drawCircle(color, 0.23f * s, c, style = stroke)
            drawCircle(color, 0.08f * s, c, style = stroke)
            for (i in 0 until 8) {
                val a = i * (Math.PI / 4.0)
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                line(
                    Offset(c.x + ca * 0.32f * s, c.y + sa * 0.32f * s),
                    Offset(c.x + ca * 0.42f * s, c.y + sa * 0.42f * s),
                    sw * 1.7f
                )
            }
        }
        "sliders" -> {
            line(p(0.18f, 0.28f), p(0.82f, 0.28f))
            line(p(0.18f, 0.50f), p(0.82f, 0.50f))
            line(p(0.18f, 0.72f), p(0.82f, 0.72f))
            drawCircle(color, 0.08f * s, p(0.36f, 0.28f))
            drawCircle(color, 0.08f * s, p(0.64f, 0.50f))
            drawCircle(color, 0.08f * s, p(0.44f, 0.72f))
        }
        "terminal" -> {
            poly(false, floatArrayOf(0.26f, 0.34f, 0.44f, 0.50f, 0.26f, 0.66f))
            line(p(0.52f, 0.68f), p(0.76f, 0.68f))
        }
        "power" -> {
            drawArc(color, -60f, 300f, false, p(0.22f, 0.26f), Size(0.56f * s, 0.56f * s), style = stroke)
            line(p(0.5f, 0.14f), p(0.5f, 0.46f))
        }
        "chev_right" -> poly(false, floatArrayOf(0.38f, 0.22f, 0.66f, 0.50f, 0.38f, 0.78f))
        "chev_up" -> poly(false, floatArrayOf(0.22f, 0.62f, 0.5f, 0.34f, 0.78f, 0.62f))
        "chev_down" -> poly(false, floatArrayOf(0.22f, 0.38f, 0.5f, 0.66f, 0.78f, 0.38f))
    }
}

/**
 * Joystick: alas kaca bulat, empat tanda arah, dan kenop. Saat disentuh kenop jadi kuning; saat
 * kunci lari aktif (didorong sampai tepi) cincin luar kuning dan kenop berisi dua panah naik.
 */
fun DrawScope.drawControlJoystick(
    opacity: Float,
    knob: Offset,
    touching: Boolean,
    sprint: Boolean,
    selected: Boolean = false
) {
    val k = opacity.coerceIn(0.1f, 1f)
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = min(size.width, size.height) / 2f
    val sw = (if (sprint || selected) 2.2f else 1.2f).dp.toPx()

    drawCircle(color = Color.Black.copy(alpha = 0.20f * (0.4f + 0.6f * k)), radius = r, center = c)
    drawCircle(
        color = when {
            sprint || selected -> ControlSkin.Yellow
            touching -> Color.White.copy(alpha = 0.34f)
            else -> Color.White.copy(alpha = 0.16f + 0.12f * k)
        },
        radius = r - sw / 2f,
        center = c,
        style = Stroke(width = sw)
    )

    // Empat tanda arah kecil di dekat tepi
    val tick = Color.White.copy(alpha = 0.16f + 0.22f * k)
    val tr = r * 0.80f
    val ts = r * 0.07f
    val tw = 1.3f.dp.toPx()
    fun chevron(dx: Float, dy: Float) {
        val m = Offset(c.x + dx * tr, c.y + dy * tr)
        val px = -dy
        val py = dx
        val a = Offset(m.x - dx * ts + px * ts * 1.3f, m.y - dy * ts + py * ts * 1.3f)
        val b = Offset(m.x - dx * ts - px * ts * 1.3f, m.y - dy * ts - py * ts * 1.3f)
        drawLine(tick, a, m, strokeWidth = tw, cap = StrokeCap.Round)
        drawLine(tick, b, m, strokeWidth = tw, cap = StrokeCap.Round)
    }
    chevron(0f, -1f)
    chevron(1f, 0f)
    chevron(0f, 1f)
    chevron(-1f, 0f)

    val kr = r * 0.36f
    val kc = c + knob
    drawCircle(
        color = if (touching) ControlSkin.Yellow.copy(alpha = 0.40f + 0.30f * k) else Color.White.copy(alpha = 0.12f + 0.08f * k),
        radius = kr,
        center = kc
    )
    drawCircle(
        color = if (touching) ControlSkin.YellowSoft.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.34f + 0.30f * k),
        radius = kr - 0.75f.dp.toPx(),
        center = kc,
        style = Stroke(width = 1.5f.dp.toPx())
    )

    // Kunci lari aktif: dua panah naik di dalam kenop (digambar setelah kenop supaya tidak tertutup)
    if (sprint) {
        val s = kr * 1.15f
        drawControlIcon("chev_up", kc.x, kc.y - s * 0.16f, s, ControlSkin.Ink)
        drawControlIcon("chev_up", kc.x, kc.y + s * 0.16f, s, ControlSkin.Ink)
    }
}
