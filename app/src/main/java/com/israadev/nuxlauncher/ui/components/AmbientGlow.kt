package com.israadev.nuxlauncher.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.israadev.nuxlauncher.ui.theme.NuxColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Kisi warna kecil (W x H, opak) dari bingkai GIF atau video yang sedang tampil. */
@Immutable
class AmbientColors(val grid: IntArray)

/** Tempat penyampaian warna dari latar ke lapisan cahaya; null = ambient mati atau belum ada data. */
@Stable
class AmbientState {
    var colors by mutableStateOf<AmbientColors?>(null)

    /** Lebar / tinggi kotak tempat latar tampil; dipakai agar GIF dibaca terpotong seperti tampilannya. */
    @Volatile
    var aspect: Float = 16f / 9f
}

/**
 * Mengambil kisi warna dari bingkai latar. Bingkai digambar dulu ke bitmap kecil (192x108) lalu
 * dirata-ratakan jadi 48x27 supaya tidak bergerigi. Semuanya murah: sekitar 20 ribu piksel, jadi
 * aman dilakukan di thread utama beberapa kali per detik.
 */
object AmbientSampler {
    const val W = 48
    const val H = 27
    private const val SRC_W = W * 4
    private const val SRC_H = H * 4

    /** Jeda antar pengambilan warna (ms). */
    const val INTERVAL_MS = 700L

    fun newBitmap(): Bitmap = Bitmap.createBitmap(SRC_W, SRC_H, Bitmap.Config.ARGB_8888)

    fun newPixels(): IntArray = IntArray(SRC_W * SRC_H)

    /** [bmp] berukuran 192x108 sudah berisi bingkai yang diperkecil; null bila bingkai belum tergambar. */
    fun fromBitmap(bmp: Bitmap, pixels: IntArray): AmbientColors? {
        if (bmp.width != SRC_W || bmp.height != SRC_H) return null
        bmp.getPixels(pixels, 0, SRC_W, 0, 0, SRC_W, SRC_H)
        val grid = IntArray(W * H)
        var opaque = 0
        for (gy in 0 until H) {
            for (gx in 0 until W) {
                var r = 0
                var g = 0
                var b = 0
                var n = 0
                for (y in gy * 4 until gy * 4 + 4) {
                    val row = y * SRC_W
                    for (x in gx * 4 until gx * 4 + 4) {
                        val c = pixels[row + x]
                        if ((c ushr 24) < 32) continue
                        r += (c shr 16) and 0xFF
                        g += (c shr 8) and 0xFF
                        b += c and 0xFF
                        n++
                    }
                }
                if (n == 0) {
                    grid[gy * W + gx] = 0xFF000000.toInt()
                } else {
                    opaque++
                    grid[gy * W + gx] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
                }
            }
        }
        if (opaque < W * H / 4) return null
        return AmbientColors(grid)
    }

    /**
     * Menggambar bingkai [drawable] saat ini (mis. GIF) ke bitmap kecil lalu mengambil warnanya.
     * [boxAspect] = lebar/tinggi kotak tampil; gambar dipotong tengah seperti CENTER_CROP.
     */
    fun fromDrawable(drawable: Drawable, bmp: Bitmap, pixels: IntArray, boxAspect: Float): AmbientColors? {
        val b = drawable.bounds
        val dw = b.width().toFloat()
        val dh = b.height().toFloat()
        if (dw <= 0f || dh <= 0f) return null
        val aspect = if (boxAspect > 0.1f) boxAspect else 16f / 9f
        val visW: Float
        val visH: Float
        if (dw / dh > aspect) {
            visH = dh
            visW = dh * aspect
        } else {
            visW = dw
            visH = dw / aspect
        }
        val srcLeft = b.left + (dw - visW) / 2f
        val srcTop = b.top + (dh - visH) / 2f
        bmp.eraseColor(android.graphics.Color.BLACK)
        val canvas = android.graphics.Canvas(bmp)
        canvas.scale(SRC_W / visW, SRC_H / visH)
        canvas.translate(-srcLeft, -srcTop)
        drawable.draw(canvas)
        return fromBitmap(bmp, pixels)
    }
}

/**
 * Pembuat "medan cahaya": bitmap kecil (satu piksel = sekitar 4dp) seukuran layar yang sudah
 * berisi hasil akhir latar. Warna di luar kartu mengikuti tepi video di titik yang sama (diperluas
 * keluar seperti Ambilight), memudar halus makin jauh dari kartu. Tidak ada garis atau cincin.
 * Dikerjakan di thread latar sekali tiap sampel; yang tergambar tiap bingkai hanya satu gambar kecil.
 */
private class AmbientFieldBuilder {
    private data class Geo(
        val w: Int, val h: Int,
        val l: Float, val t: Float, val r: Float, val b: Float,
        val radius: Float, val density: Float, val strength: Float
    )

    private var geo: Geo? = null
    private var fw = 0
    private var fh = 0
    private var colIdx = IntArray(0)
    private var colW = FloatArray(0)
    private var rowIdx = IntArray(0)
    private var rowW = FloatArray(0)
    private var alpha = FloatArray(0)

    private val ink = NuxColors.Ink.toArgb()
    private val inkR = (ink shr 16) and 0xFF
    private val inkG = (ink shr 8) and 0xFF
    private val inkB = ink and 0xFF

    @Synchronized
    fun build(
        grid: IntArray,
        width: Int,
        height: Int,
        box: Rect,
        radiusPx: Float,
        density: Float,
        strength: Float
    ): Bitmap? {
        if (width <= 0 || height <= 0 || box.width <= 1f || box.height <= 1f) return null
        val key = Geo(width, height, box.left, box.top, box.right, box.bottom, radiusPx, density, strength)
        if (key != geo) {
            prepare(key)
            geo = key
        }

        val gw = AmbientSampler.W
        val gh = AmbientSampler.H
        val n = gw * gh
        val pr = FloatArray(n)
        val pg = FloatArray(n)
        val pb = FloatArray(n)
        for (i in 0 until n) {
            val c = grid[i]
            pr[i] = ((c shr 16) and 0xFF) / 255f
            pg[i] = ((c shr 8) and 0xFF) / 255f
            pb[i] = (c and 0xFF) / 255f
        }

        // Haluskan dulu: detail gambar tidak penting, yang dicari suasana warnanya.
        val scratch = FloatArray(n)
        blur(pr, gw, gh, scratch)
        blur(pg, gw, gh, scratch)
        blur(pb, gw, gh, scratch)

        // Warna lebih jenuh dan gelapnya sedikit diangkat, tapi terangnya dibatasi supaya teks
        // putih di sidebar tetap terbaca.
        val cap = 0.45f + 0.35f * strength
        for (i in 0 until n) {
            var r = pr[i]
            var g = pg[i]
            var b = pb[i]
            val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            r = (lum + (r - lum) * SATURATION).coerceIn(0f, 1f).pow(GAMMA)
            g = (lum + (g - lum) * SATURATION).coerceIn(0f, 1f).pow(GAMMA)
            b = (lum + (b - lum) * SATURATION).coerceIn(0f, 1f).pow(GAMMA)
            val lum2 = 0.2126f * r + 0.7152f * g + 0.0722f * b
            if (lum2 > cap) {
                val k = cap / lum2
                r *= k
                g *= k
                b *= k
            }
            pr[i] = r
            pg[i] = g
            pb[i] = b
        }

        // Lebarkan ke medan: mendatar dulu, lalu menurun (bikubik, tepi diperpanjang keluar).
        val tr = FloatArray(gh * fw)
        val tg = FloatArray(gh * fw)
        val tb = FloatArray(gh * fw)
        for (y in 0 until gh) {
            val row = y * gw
            for (fx in 0 until fw) {
                val o = fx * 4
                var r = 0f
                var g = 0f
                var b = 0f
                for (k in 0 until 4) {
                    val src = row + colIdx[o + k]
                    val wgt = colW[o + k]
                    r += pr[src] * wgt
                    g += pg[src] * wgt
                    b += pb[src] * wgt
                }
                tr[y * fw + fx] = r
                tg[y * fw + fx] = g
                tb[y * fw + fx] = b
            }
        }

        val out = IntArray(fw * fh)
        for (fy in 0 until fh) {
            val o = fy * 4
            for (fx in 0 until fw) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (k in 0 until 4) {
                    val src = rowIdx[o + k] * fw + fx
                    val wgt = rowW[o + k]
                    r += tr[src] * wgt
                    g += tg[src] * wgt
                    b += tb[src] * wgt
                }
                val a = alpha[fy * fw + fx]
                val ro = (inkR * (1f - a) + r.coerceIn(0f, 1f) * 255f * a).roundToInt().coerceIn(0, 255)
                val go = (inkG * (1f - a) + g.coerceIn(0f, 1f) * 255f * a).roundToInt().coerceIn(0, 255)
                val bo = (inkB * (1f - a) + b.coerceIn(0f, 1f) * 255f * a).roundToInt().coerceIn(0, 255)
                out[fy * fw + fx] = (0xFF shl 24) or (ro shl 16) or (go shl 8) or bo
            }
        }

        val bmp = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, fw, 0, 0, fw, fh)
        return bmp
    }

    /** Hitung hal yang hanya bergantung pada ukuran layar, posisi kartu, dan kekuatan. */
    private fun prepare(g: Geo) {
        val cell = max(4, (4f * g.density).roundToInt())
        fw = ceil(g.w / cell.toFloat()).toInt()
        fh = ceil(g.h / cell.toFloat()).toInt()

        colIdx = IntArray(fw * 4)
        colW = FloatArray(fw * 4)
        val bw = g.r - g.l
        for (fx in 0 until fw) {
            val x = (fx + 0.5f) * cell
            val u = ((x - g.l) / bw).coerceIn(0f, 1f)
            taps(u * AmbientSampler.W - 0.5f, AmbientSampler.W, colIdx, colW, fx * 4)
        }
        rowIdx = IntArray(fh * 4)
        rowW = FloatArray(fh * 4)
        val bh = g.b - g.t
        for (fy in 0 until fh) {
            val y = (fy + 0.5f) * cell
            val v = ((y - g.t) / bh).coerceIn(0f, 1f)
            taps(v * AmbientSampler.H - 0.5f, AmbientSampler.H, rowIdx, rowW, fy * 4)
        }

        // Seberapa kuat cahaya di tiap titik: terkuat di tepi kartu, memudar ke luar.
        val s = g.strength.coerceIn(0f, 1f)
        val gain = 0.35f + 0.75f * s
        val reach = (48f + 70f * s) * g.density
        val cx = (g.l + g.r) / 2f
        val cy = (g.t + g.b) / 2f
        val hw = (g.r - g.l) / 2f - g.radius
        val hh = (g.b - g.t) / 2f - g.radius
        alpha = FloatArray(fw * fh)
        for (fy in 0 until fh) {
            val y = (fy + 0.5f) * cell
            for (fx in 0 until fw) {
                val x = (fx + 0.5f) * cell
                val dx = abs(x - cx) - hw
                val dy = abs(y - cy) - hh
                val outside = sqrt(max(dx, 0f) * max(dx, 0f) + max(dy, 0f) * max(dy, 0f))
                val inside = min(max(dx, dy), 0f)
                val dist = max(outside + inside - g.radius, 0f)
                val falloff = exp(-(dist / reach).pow(1.25f))
                alpha[fy * fw + fx] = min(MAX_ALPHA, gain * falloff)
            }
        }
    }

    /** Bobot bikubik Catmull-Rom untuk titik [pos] di antara [n] sel. */
    private fun taps(pos: Float, n: Int, idx: IntArray, w: FloatArray, off: Int) {
        val i0 = floor(pos).toInt()
        val t = pos - i0
        val t2 = t * t
        val t3 = t2 * t
        w[off] = -0.5f * t3 + t2 - 0.5f * t
        w[off + 1] = 1.5f * t3 - 2.5f * t2 + 1f
        w[off + 2] = -1.5f * t3 + 2f * t2 + 0.5f * t
        w[off + 3] = 0.5f * t3 - 0.5f * t2
        for (k in 0 until 4) idx[off + k] = (i0 - 1 + k).coerceIn(0, n - 1)
    }

    /** Dua kali blur kotak berjari-jari 2 sel di kedua arah. */
    private fun blur(a: FloatArray, w: Int, h: Int, tmp: FloatArray) {
        val radius = 2
        val div = (2 * radius + 1).toFloat()
        repeat(2) {
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    var sum = 0f
                    for (k in -radius..radius) sum += a[row + (x + k).coerceIn(0, w - 1)]
                    tmp[row + x] = sum / div
                }
            }
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var sum = 0f
                    for (k in -radius..radius) sum += tmp[(y + k).coerceIn(0, h - 1) * w + x]
                    a[y * w + x] = sum / div
                }
            }
        }
    }

    private companion object {
        const val SATURATION = 1.6f
        const val GAMMA = 0.72f
        const val MAX_ALPHA = 0.95f
    }
}

/** Lama peralihan dari satu sampel ke sampel berikutnya (ms). */
private const val FADE_MS = 600

/**
 * Cahaya ambient di belakang sidebar dan di sekeliling kartu Beranda. Warnanya mengikuti isi GIF
 * atau video di titik yang sama: tepi kiri video menerangi sisi kiri, tepi atas menerangi sisi atas,
 * dan seterusnya, memudar lembut makin jauh dari kartu. Digambar sebagai satu bitmap kecil yang
 * dibentangkan ke seluruh layar (bukan lapisan tebal atau cincin), jadi murah untuk GPU HP kelas bawah.
 *
 * [bounds] = kotak kartu dalam koordinat root; [intensity] 0..100.
 */
@Composable
fun AmbientGlow(
    state: AmbientState,
    intensity: Int,
    bounds: Rect?,
    cornerRadius: Dp,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val density = LocalDensity.current.density
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    val builder = remember { AmbientFieldBuilder() }

    var origin by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var current by remember { mutableStateOf<ImageBitmap?>(null) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val mix = remember { Animatable(1f) }
    val master = remember { Animatable(0f) }

    val frame = state.colors
    val strength = (intensity.coerceIn(0, 100)) / 100f

    LaunchedEffect(frame, canvasSize, origin, bounds, strength, density) {
        if (frame == null || bounds == null || canvasSize.width <= 0 || canvasSize.height <= 0) {
            master.animateTo(0f, tween(durationMillis = 600))
            if (frame == null) {
                current = null
                previous = null
            }
            return@LaunchedEffect
        }
        val local = Rect(
            bounds.left - origin.x,
            bounds.top - origin.y,
            bounds.right - origin.x,
            bounds.bottom - origin.y
        )
        val bmp = withContext(Dispatchers.Default) {
            try {
                builder.build(frame.grid, canvasSize.width, canvasSize.height, local, radiusPx, density, strength)
            } catch (_: Throwable) {
                null
            }
        } ?: return@LaunchedEffect

        previous = if (master.value > 0.01f) current else null
        current = bmp.asImageBitmap()
        mix.snapTo(0f)
        coroutineScope {
            launch { master.animateTo(1f, tween(durationMillis = 700)) }
            launch { mix.animateTo(1f, tween(durationMillis = FADE_MS, easing = LinearEasing)) }
        }
        previous = null
    }

    Canvas(
        modifier = modifier.onGloballyPositioned { coords ->
            val p = coords.positionInRoot()
            if (p != origin) origin = p
            if (coords.size != canvasSize) canvasSize = coords.size
        }
    ) {
        val img = current ?: return@Canvas
        val m = master.value
        if (m <= 0.01f) return@Canvas
        val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
        val old = previous
        val t = mix.value
        if (old != null && t < 0.999f) {
            // Peralihan mulus: gambar lama penuh, gambar baru dimasukkan pelan-pelan di atasnya.
            drawImage(old, dstSize = dst, alpha = m, filterQuality = FilterQuality.Low)
            drawImage(img, dstSize = dst, alpha = t * m, filterQuality = FilterQuality.Low)
        } else {
            drawImage(img, dstSize = dst, alpha = m, filterQuality = FilterQuality.Low)
        }
    }
}
