package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.graphics.Canvas as GfxCanvas

// =============================================================================
// EFEK SALJU (suasana Natal)
//
// Dua lapisan yang dipasang DashboardScreen:
//  - SnowBackLayer : di belakang semua UI (di atas latar/wallpaper). Banyak butir halus.
//  - SnowFrontLayer: di atas UI, hanya beberapa butir supaya suasananya terasa.
//
// Di Home salju lebat dan terang. Di layar lain ([indoor] = true) jumlahnya jauh lebih sedikit dan
// lebih redup, seolah-olah salju hanya terlihat dari balik jendela di dalam ruangan.
//
// Tampilan: bukan lingkaran keras. Ada empat jenis butir yang digambar dari gambar kecil lembut
// (dibuat sekali, dipakai ulang):
//  - titik halus (butir jauh), gumpalan lembut (butir dekat yang "tidak fokus"),
//  - cincin bokeh besar yang sangat samar, dan kristal salju enam lengan yang berputar pelan.
// Butir jauh kecil, pelan, dan terang; butir dekat besar, cepat, dan samar, jadi ada rasa
// kedalaman. Geraknya berayun dengan dua gelombang, dihanyutkan angin yang berhembus naik-turun,
// berkelip pelan, dan memudar di tepi atas/bawah layar (tidak muncul/hilang mendadak). Di Home
// ada selaput embun putih tipis di dasar layar seperti salju yang menumpuk.
//
// Hemat tenaga: posisi dihitung dari satu nilai waktu (tidak ada objek per butir yang dibuat
// ulang), digambar hanya di fase draw (tanpa recompose) pada lapisan GPU sendiri (UI di
// bawahnya tidak ikut digambar ulang), dan diperbarui sampai ~60 kali per detik. Lapisan tidak
// menangkap sentuhan, jadi UI di bawahnya tetap bisa disentuh.
// =============================================================================

private const val FRAME_NS = 15_000_000L // sampai ~60 fps
private const val TWO_PI = 6.2831855f

private const val K_DOT = 0
private const val K_SOFT = 1
private const val K_BOKEH = 2
private const val K_STAR = 3

/** Ukuran (dp), kecepatan (dp/detik), dan kejernihan satu jenis butir dari jauh (0) sampai dekat (1). */
private class KindSpec(
    val minDia: Float,
    val maxDia: Float,
    val minSpeed: Float,
    val maxSpeed: Float,
    val minAlpha: Float,
    val maxAlpha: Float,
    val twinkle: Float,
    val sway: Float
)

private val Specs = arrayOf(
    KindSpec(2.2f, 5.2f, 16f, 46f, 0.40f, 0.95f, 0.25f, 7f),     // titik halus
    KindSpec(7f, 15f, 26f, 58f, 0.16f, 0.40f, 0.15f, 12f),       // gumpalan lembut
    KindSpec(18f, 34f, 18f, 34f, 0.08f, 0.20f, 0.35f, 18f),      // cincin bokeh
    KindSpec(7f, 13f, 18f, 40f, 0.55f, 0.95f, 0.50f, 14f)        // kristal
)

/** Warna dan skala satu suasana (Home terang, layar lain redup dan dingin). */
private class SnowStyle(
    val tint: ColorFilter,
    val alphaScale: Float,
    val speedScale: Float,
    val sizeScale: Float
)

private val HomeStyle = SnowStyle(
    tint = ColorFilter.tint(Color(0xFFF4F8FF)),
    alphaScale = 1f, speedScale = 1f, sizeScale = 1f
)

private val IndoorStyle = SnowStyle(
    tint = ColorFilter.tint(Color(0xFFC9D6E8)),
    alphaScale = 0.5f, speedScale = 0.8f, sizeScale = 0.9f
)

private val HomeFrontStyle = SnowStyle(
    tint = ColorFilter.tint(Color(0xFFFFFFFF)),
    alphaScale = 0.95f, speedScale = 1.15f, sizeScale = 1.1f
)

private val IndoorFrontStyle = SnowStyle(
    tint = ColorFilter.tint(Color(0xFFDCE5F2)),
    alphaScale = 0.6f, speedScale = 1f, sizeScale = 1f
)

// ----- Gambar butir (dibuat sekali untuk seluruh proses) -----

private fun buildSprite(side: Int, block: DrawScope.() -> Unit): ImageBitmap {
    val bitmap = ImageBitmap(side, side)
    val canvas = GfxCanvas(bitmap)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        canvas,
        Size(side.toFloat(), side.toFloat())
    ) {
        this.block()
    }
    return bitmap
}

private fun dotSprite(): ImageBitmap = buildSprite(48) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.width / 2f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White,
            0.55f to Color.White,
            0.80f to Color.White.copy(alpha = 0.5f),
            1f to Color.Transparent,
            center = c,
            radius = r
        ),
        radius = r,
        center = c
    )
}

private fun softSprite(): ImageBitmap = buildSprite(96) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.width / 2f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.95f),
            0.30f to Color.White.copy(alpha = 0.60f),
            0.60f to Color.White.copy(alpha = 0.22f),
            1f to Color.Transparent,
            center = c,
            radius = r
        ),
        radius = r,
        center = c
    )
}

private fun bokehSprite(): ImageBitmap = buildSprite(96) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.width / 2f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.10f),
            0.70f to Color.White.copy(alpha = 0.16f),
            0.88f to Color.White.copy(alpha = 0.42f),
            0.96f to Color.White.copy(alpha = 0.20f),
            1f to Color.Transparent,
            center = c,
            radius = r
        ),
        radius = r,
        center = c
    )
}

/** Kristal salju enam lengan dengan cabang kecil dan titik pusat bercahaya. */
private fun starSprite(): ImageBitmap = buildSprite(96) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.width * 0.46f
    val stroke = size.width * 0.055f
    val sixty = (PI / 3.0).toFloat()
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.55f),
            1f to Color.Transparent,
            center = c,
            radius = size.width * 0.32f
        ),
        radius = size.width * 0.32f,
        center = c
    )
    for (arm in 0 until 6) {
        val ang = arm * sixty
        val dx = cos(ang)
        val dy = sin(ang)
        drawLine(
            color = Color.White,
            start = c,
            end = Offset(c.x + dx * r, c.y + dy * r),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        val bx = c.x + dx * r * 0.58f
        val by = c.y + dy * r * 0.58f
        for (side in 0 until 2) {
            val bang = if (side == 0) ang - sixty else ang + sixty
            drawLine(
                color = Color.White,
                start = Offset(bx, by),
                end = Offset(bx + cos(bang) * r * 0.30f, by + sin(bang) * r * 0.30f),
                strokeWidth = stroke * 0.8f,
                cap = StrokeCap.Round
            )
        }
    }
    drawCircle(color = Color.White, radius = stroke * 0.9f, center = c)
}

private object SnowSprites {
    val images: Array<ImageBitmap> by lazy {
        arrayOf(dotSprite(), softSprite(), bokehSprite(), starSprite())
    }
}

// ----- Isi lapisan -----

/** Data tetap tiap butir: jenis, posisi awal (0..1), kedalaman (0 jauh, 1 dekat), fase, dan putaran. */
private class SnowField(val count: Int, seed: Int, weights: FloatArray, nearBias: Boolean) {
    val kind = IntArray(count)
    val x0 = FloatArray(count)
    val y0 = FloatArray(count)
    val depth = FloatArray(count)
    val phase = FloatArray(count)
    val freq = FloatArray(count)
    val spin = FloatArray(count)
    val speedJit = FloatArray(count)

    init {
        val rnd = Random(seed)
        var total = 0f
        for (w in weights) total += w
        for (i in 0 until count) {
            val pick = rnd.nextFloat() * total
            var acc = 0f
            var kd = 0
            for (j in weights.indices) {
                acc += weights[j]
                if (pick <= acc) {
                    kd = j
                    break
                }
            }
            kind[i] = kd
            x0[i] = rnd.nextFloat()
            y0[i] = rnd.nextFloat()
            val u = rnd.nextFloat()
            depth[i] = when {
                nearBias -> 0.4f + 0.6f * u
                kd == K_DOT -> u * u          // titik: kebanyakan jauh
                else -> 0.25f + 0.75f * u
            }
            phase[i] = rnd.nextFloat() * TWO_PI
            freq[i] = 0.45f + rnd.nextFloat() * 1.0f
            spin[i] = (rnd.nextFloat() - 0.5f) * 56f
            speedJit[i] = 0.85f + rnd.nextFloat() * 0.3f
        }
    }
}

private fun wrap(v: Float, span: Float): Float {
    val m = v % span
    return if (m < 0f) m + span else m
}

@Composable
private fun SnowCanvas(
    count: Int,
    seed: Int,
    weights: FloatArray,
    nearBias: Boolean,
    style: SnowStyle,
    frost: Boolean,
    modifier: Modifier = Modifier
) {
    val field = remember(count, seed, nearBias) { SnowField(count, seed, weights, nearBias) }
    val time = remember { mutableFloatStateOf(0f) }

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
                    time.floatValue = (now - startNs) / 1_000_000_000f
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (frost) {
            // Selaput putih tipis di dasar layar, seperti salju yang menumpuk.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.30f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFEAF2FF).copy(alpha = 0.05f),
                                Color(0xFFEAF2FF).copy(alpha = 0.13f)
                            )
                        )
                    )
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { }
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            val t = time.floatValue
            val d = density
            val images = SnowSprites.images
            // Hembusan angin: hanyutan tetap ditambah gelombang lambat sehingga arah angin terasa berganti.
            val windBase = t * 5f + 34f * sin(t * 0.17f)
            val fadeTop = h * 0.08f
            val fadeBottom = h * 0.12f

            for (i in 0 until field.count) {
                val k = field.kind[i]
                val spec = Specs[k]
                val dp = field.depth[i]
                val side = ((spec.minDia + (spec.maxDia - spec.minDia) * dp) * style.sizeScale * d).roundToInt()
                if (side < 2) continue
                val speed = (spec.minSpeed + (spec.maxSpeed - spec.minSpeed) * dp) *
                    field.speedJit[i] * style.speedScale * d
                val margin = side * 1.5f
                val spanY = h + margin * 2f
                val y = wrap(field.y0[i] * spanY + t * speed, spanY) - margin

                val f = field.freq[i]
                val ph = field.phase[i]
                val sway = (sin(t * f + ph) * 0.7f + sin(t * f * 2.3f + ph * 1.7f) * 0.3f) *
                    spec.sway * (0.6f + 0.8f * dp) * d
                val wind = windBase * (0.35f + 0.9f * dp) * d
                val spanX = w + margin * 2f
                val x = wrap(field.x0[i] * spanX + sway + wind, spanX) - margin

                // Memudar di tepi atas dan bawah, berkelip pelan.
                val fade = (y / fadeTop).coerceIn(0f, 1f) * ((h - y) / fadeBottom).coerceIn(0f, 1f)
                val twinkle = 1f - spec.twinkle * (0.5f + 0.5f * sin(t * f * 1.9f + ph * 2.3f))
                val alpha = (spec.minAlpha + (spec.maxAlpha - spec.minAlpha) * dp) *
                    style.alphaScale * twinkle * fade
                if (alpha < 0.01f) continue

                val half = side / 2f
                translate(left = x - half, top = y - half) {
                    if (k == K_STAR) {
                        rotate(degrees = ph * 57.29578f + t * field.spin[i], pivot = Offset(half, half)) {
                            drawImage(
                                image = images[k],
                                dstSize = IntSize(side, side),
                                alpha = alpha,
                                colorFilter = style.tint,
                                filterQuality = FilterQuality.Medium
                            )
                        }
                    } else {
                        drawImage(
                            image = images[k],
                            dstSize = IntSize(side, side),
                            alpha = alpha,
                            colorFilter = style.tint,
                            filterQuality = FilterQuality.Medium
                        )
                    }
                }
            }
        }
    }
}

private val BackHomeWeights = floatArrayOf(0.58f, 0.24f, 0.06f, 0.12f)
private val BackIndoorWeights = floatArrayOf(0.70f, 0.30f, 0f, 0f)
private val FrontHomeWeights = floatArrayOf(0.15f, 0.45f, 0.08f, 0.32f)
private val FrontIndoorWeights = floatArrayOf(0.35f, 0.65f, 0f, 0f)

/**
 * Salju di belakang semua UI. [amount] 10..100 (persen). [indoor] = layar selain Home: jumlah
 * dikurangi banyak, warnanya lebih redup dan dingin, dan tidak ada kristal/bokeh.
 */
@Composable
fun SnowBackLayer(
    amount: Int,
    indoor: Boolean,
    modifier: Modifier = Modifier
) {
    val a = amount.coerceIn(10, 100)
    SnowCanvas(
        count = (a * (if (indoor) 0.38f else 1.15f)).roundToInt().coerceAtLeast(4),
        seed = if (indoor) 12 else 11,
        weights = if (indoor) BackIndoorWeights else BackHomeWeights,
        nearBias = false,
        style = if (indoor) IndoorStyle else HomeStyle,
        frost = !indoor,
        modifier = modifier
    )
}

/** Beberapa butir salju di atas UI. Sedikit supaya tidak mengganggu bacaan. */
@Composable
fun SnowFrontLayer(
    amount: Int,
    indoor: Boolean,
    modifier: Modifier = Modifier
) {
    val a = amount.coerceIn(10, 100)
    val count = if (indoor) 3 + (a * 0.04f).roundToInt() else 5 + (a * 0.10f).roundToInt()
    SnowCanvas(
        count = count,
        seed = if (indoor) 34 else 33,
        weights = if (indoor) FrontIndoorWeights else FrontHomeWeights,
        nearBias = true,
        style = if (indoor) IndoorFrontStyle else HomeFrontStyle,
        frost = false,
        modifier = modifier
    )
}
