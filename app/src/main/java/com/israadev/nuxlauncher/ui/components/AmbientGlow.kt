package com.israadev.nuxlauncher.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Warna rata-rata di empat tepi gambar latar yang bergerak (GIF atau video). */
@Immutable
class AmbientColors(val top: Color, val right: Color, val bottom: Color, val left: Color)

/** Tempat penyampaian warna dari latar ke lapisan cahaya; null = ambient mati atau belum ada data. */
@Stable
class AmbientState {
    var colors by mutableStateOf<AmbientColors?>(null)
}

/**
 * Mengambil warna tepi dari bitmap kecil (32x18). Murah: hanya membaca 576 piksel, jadi aman
 * dilakukan sekali tiap detik di thread utama.
 */
object AmbientSampler {
    const val W = 32
    const val H = 18

    fun newBitmap(): Bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)

    fun newPixels(): IntArray = IntArray(W * H)

    /** Bitmap [bmp] berukuran W x H sudah berisi bingkai yang diperkecil. */
    fun fromBitmap(bmp: Bitmap, pixels: IntArray): AmbientColors? {
        if (bmp.width != W || bmp.height != H) return null
        bmp.getPixels(pixels, 0, W, 0, 0, W, H)
        val bandX = W / 4
        val bandY = H / 4
        val top = average(pixels, 0, W, 0, bandY) ?: return null
        val bottom = average(pixels, 0, W, H - bandY, H) ?: return null
        val left = average(pixels, 0, bandX, 0, H) ?: return null
        val right = average(pixels, W - bandX, W, 0, H) ?: return null
        return AmbientColors(lift(top), lift(right), lift(bottom), lift(left))
    }

    /** Menggambar bingkai [drawable] saat ini (mis. GIF) ke bitmap kecil lalu mengambil warnanya. */
    fun fromDrawable(drawable: Drawable, bmp: Bitmap, pixels: IntArray): AmbientColors? {
        val b = drawable.bounds
        if (b.isEmpty) return null
        bmp.eraseColor(0)
        val canvas = android.graphics.Canvas(bmp)
        canvas.scale(W.toFloat() / b.width(), H.toFloat() / b.height())
        canvas.translate(-b.left.toFloat(), -b.top.toFloat())
        drawable.draw(canvas)
        return fromBitmap(bmp, pixels)
    }

    private fun average(p: IntArray, x0: Int, x1: Int, y0: Int, y1: Int): Int? {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val c = p[y * W + x]
                if ((c ushr 24) < 32) continue
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                n++
            }
        }
        if (n == 0) return null
        return android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /** Sedikit lebih jenuh dan terang supaya cahayanya terlihat di atas latar hitam. */
    private fun lift(rgb: Int): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv[1] = (hsv[1] * 1.3f + 0.04f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 1.15f).coerceIn(0.30f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}

/** Pemegang warna terakhir supaya cahaya memudar dengan warna lamanya, bukan menjadi hitam. */
private class LastColors {
    var value: AmbientColors? = null
}

/**
 * Cahaya lembut di sekeliling kartu Beranda (di area hitam sekitarnya) yang berwarna mengikuti
 * tepi GIF atau video. Digambar sebagai cincin tipis berlapis dengan kecerahan menurun ke luar,
 * jadi tidak ada lapisan penuh yang menumpuk dan murah untuk GPU HP kelas bawah.
 *
 * [bounds] = kotak kartu dalam piksel; [intensity] 0..100.
 */
@Composable
fun AmbientGlow(
    state: AmbientState,
    intensity: Int,
    bounds: Rect?,
    cornerRadius: Dp,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val last = remember { LastColors() }
    val current = state.colors
    if (current != null) last.value = current
    val shown = last.value ?: AmbientColors(Color.Black, Color.Black, Color.Black, Color.Black)

    val colorSpec = tween<Color>(durationMillis = 1100, easing = LinearEasing)
    val top = animateColorAsState(shown.top, colorSpec, label = "ambientTop")
    val right = animateColorAsState(shown.right, colorSpec, label = "ambientRight")
    val bottom = animateColorAsState(shown.bottom, colorSpec, label = "ambientBottom")
    val left = animateColorAsState(shown.left, colorSpec, label = "ambientLeft")
    val fade = animateFloatAsState(
        targetValue = if (current != null) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "ambientFade"
    )

    Canvas(modifier = modifier) {
        val box = bounds ?: return@Canvas
        val visible = fade.value
        if (visible <= 0.01f) return@Canvas

        val strength = intensity.coerceIn(0, 100) / 100f
        val base = (0.05f + 0.50f * strength) * visible
        val rings = 16
        val step = 4.dp.toPx()
        val baseRadius = cornerRadius.toPx()

        // Sapuan warna mengelilingi kartu: kanan, bawah, kiri, atas, kembali ke kanan.
        val brush = Brush.sweepGradient(
            listOf(right.value, bottom.value, left.value, top.value, right.value),
            center = box.center
        )

        for (i in 1..rings) {
            val mid = step * (i - 0.5f)
            val t = (i - 0.5f) / rings
            val falloff = (1f - t) * (1f - t)
            drawRoundRect(
                brush = brush,
                topLeft = Offset(box.left - mid, box.top - mid),
                size = Size(box.width + 2f * mid, box.height + 2f * mid),
                cornerRadius = CornerRadius(baseRadius + mid),
                alpha = base * falloff,
                style = Stroke(width = step + 0.75f)
            )
        }
    }
}
