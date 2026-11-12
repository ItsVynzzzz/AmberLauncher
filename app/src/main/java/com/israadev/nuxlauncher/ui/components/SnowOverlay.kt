package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// =============================================================================
// EFEK SALJU (suasana Natal)
//
// Dua lapisan yang dipasang DashboardScreen:
//  - SnowBackLayer : di belakang semua UI (di atas latar/wallpaper). Banyak butir halus.
//  - SnowFrontLayer: di atas UI, hanya beberapa butir kecil supaya suasananya terasa.
//
// Di Home salju lebat dan terang. Di layar lain ([indoor] = true) jumlahnya jauh lebih sedikit dan
// lebih redup, seolah-olah salju hanya terlihat dari balik jendela di dalam ruangan.
//
// Hemat tenaga: posisi butir dihitung dari satu nilai waktu (tidak ada objek per butir yang dibuat
// ulang), digambar hanya di fase draw (tanpa recompose), dan diperbarui ~30 kali per detik.
// Lapisan tidak menangkap sentuhan, jadi UI di bawahnya tetap bisa disentuh.
// =============================================================================

private const val FRAME_NS = 33_000_000L // ~30 fps

/** Gaya satu lapisan. Ukuran dan kecepatan dalam dp (dp per detik), butir dekat lebih besar dan cepat. */
private class SnowStyle(
    val minRadius: Float,
    val maxRadius: Float,
    val minSpeed: Float,
    val maxSpeed: Float,
    val minAlpha: Float,
    val maxAlpha: Float,
    val color: Color,
    val halo: Boolean
)

private val BackHomeStyle = SnowStyle(
    minRadius = 0.9f, maxRadius = 3.0f,
    minSpeed = 20f, maxSpeed = 65f,
    minAlpha = 0.35f, maxAlpha = 0.85f,
    color = Color.White, halo = true
)

private val BackIndoorStyle = SnowStyle(
    minRadius = 0.8f, maxRadius = 2.4f,
    minSpeed = 16f, maxSpeed = 48f,
    minAlpha = 0.16f, maxAlpha = 0.42f,
    color = Color(0xFFD5DEEA), halo = false
)

private val FrontHomeStyle = SnowStyle(
    minRadius = 1.6f, maxRadius = 3.4f,
    minSpeed = 55f, maxSpeed = 95f,
    minAlpha = 0.55f, maxAlpha = 0.85f,
    color = Color.White, halo = true
)

private val FrontIndoorStyle = SnowStyle(
    minRadius = 1.4f, maxRadius = 2.8f,
    minSpeed = 45f, maxSpeed = 75f,
    minAlpha = 0.34f, maxAlpha = 0.6f,
    color = Color(0xFFE3EAF3), halo = false
)

/** Posisi awal tiap butir (0..1), kedalaman (0 = jauh, 1 = dekat), dan fase/laju ayunan. */
private class SnowField(val count: Int, seed: Int, nearOnly: Boolean) {
    val x0 = FloatArray(count)
    val y0 = FloatArray(count)
    val depth = FloatArray(count)
    val phase = FloatArray(count)
    val freq = FloatArray(count)

    init {
        val rnd = Random(seed)
        for (i in 0 until count) {
            x0[i] = rnd.nextFloat()
            y0[i] = rnd.nextFloat()
            val u = rnd.nextFloat()
            // Lapisan belakang: kebanyakan butir jauh (kecil), sedikit yang dekat. Lapisan depan: butir dekat.
            depth[i] = if (nearOnly) 0.5f + 0.5f * u else u * u
            phase[i] = rnd.nextFloat() * 6.2831855f
            freq[i] = 0.5f + rnd.nextFloat() * 1.1f
        }
    }
}

@Composable
private fun SnowCanvas(
    count: Int,
    seed: Int,
    nearOnly: Boolean,
    style: SnowStyle,
    modifier: Modifier = Modifier
) {
    val field = remember(count, seed, nearOnly) { SnowField(count, seed, nearOnly) }
    var timeSec by remember { mutableFloatStateOf(0f) }

    // Satu penghitung waktu; berhenti sendiri saat layar tidak tampil.
    LaunchedEffect(Unit) {
        var startNs = -1L
        var lastNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (startNs < 0L) {
                    startNs = now
                    lastNs = now - FRAME_NS
                }
                if (now - lastNs >= FRAME_NS) {
                    lastNs = now
                    timeSec = (now - startNs) / 1_000_000_000f
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val t = timeSec
        val d = density
        for (i in 0 until field.count) {
            val depth = field.depth[i]
            val r = (style.minRadius + (style.maxRadius - style.minRadius) * depth) * d
            val speed = (style.minSpeed + (style.maxSpeed - style.minSpeed) * depth) * d
            val span = h + r * 4f
            val y = ((field.y0[i] * span + t * speed) % span) - r * 2f
            // Ayunan kiri-kanan dan hanyutan angin pelan; butir dekat bergerak lebih jauh.
            val sway = sin(t * field.freq[i] + field.phase[i]) * (6f + 10f * depth) * d
            val wind = t * (4f + 10f * depth) * d
            var x = (field.x0[i] * w + sway + wind) % w
            if (x < 0f) x += w
            val alpha = style.minAlpha + (style.maxAlpha - style.minAlpha) * depth
            val center = Offset(x, y)
            if (style.halo && depth > 0.5f) {
                drawCircle(
                    color = style.color.copy(alpha = alpha * 0.22f),
                    radius = r * 2.2f,
                    center = center
                )
            }
            drawCircle(
                color = style.color.copy(alpha = alpha),
                radius = r,
                center = center
            )
        }
    }
}

/**
 * Salju di belakang semua UI. [amount] 10..100 (persen). [indoor] = layar selain Home: jumlah
 * dikurangi banyak dan warnanya lebih redup.
 */
@Composable
fun SnowBackLayer(
    amount: Int,
    indoor: Boolean,
    modifier: Modifier = Modifier
) {
    val a = amount.coerceIn(10, 100)
    SnowCanvas(
        count = (a * (if (indoor) 0.38f else 1.1f)).roundToInt().coerceAtLeast(4),
        seed = if (indoor) 12 else 11,
        nearOnly = false,
        style = if (indoor) BackIndoorStyle else BackHomeStyle,
        modifier = modifier
    )
}

/** Beberapa butir salju di atas UI. Sedikit dan kecil supaya tidak mengganggu bacaan. */
@Composable
fun SnowFrontLayer(
    amount: Int,
    indoor: Boolean,
    modifier: Modifier = Modifier
) {
    val a = amount.coerceIn(10, 100)
    val count = if (indoor) 3 + (a * 0.04f).roundToInt() else 6 + (a * 0.10f).roundToInt()
    SnowCanvas(
        count = count,
        seed = if (indoor) 34 else 33,
        nearOnly = true,
        style = if (indoor) FrontIndoorStyle else FrontHomeStyle,
        modifier = modifier
    )
}
