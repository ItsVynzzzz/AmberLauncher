package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.israadev.nuxlauncher.core.settings.SettingsManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.graphics.Canvas as GfxCanvas

// =============================================================================
// EFEK BERANDA (selain salju)
//
// Pilihan efek yang bisa diganti di Pengaturan: salju (SnowOverlay.kt, tidak diubah), sakura, daun musim gugur,
// hujan, kunang-kunang, kilau bintang, bara api, dan gelembung. Dipasang di dua lapisan seperti salju: di belakang
// semua UI (banyak, halus) dan di atas UI (beberapa saja, tidak menangkap sentuhan).
//
// Tiap efek punya detail sendiri yang sesuai wataknya, bukan sekadar partikel yang berbeda bentuk:
//  - Sakura: kelopak dua bentuk dan bunga utuh berpusat emas yang berputar dan terbalik di udara, dengan kabut merah muda.
//  - Daun musim gugur: daun biasa, maple, dan ginkgo dengan warna musim gugur yang beragam, berayun lebar saat jatuh.
//  - Hujan: goresan tipis (jauh) sampai tebal (dekat) yang miringnya ikut hembusan angin, riak di tanah, dan kabut dasar.
//  - Kunang-kunang: cahaya berinti putih yang berkedip singkat lalu lama redup, dan kabut cahaya yang samar.
//  - Kilau bintang: kilau empat dan delapan sudut yang membesar dan mengecil mengikuti kedipnya, serta bintang jatuh
//    yang lewat pelan sesekali (tanpa kilatan mendadak).
//  - Bara api: warna berubah dari kuning-putih ke merah saat naik dan mendingin, mengecil, percikan cepat, dan
//    cahaya hangat berdenyut di dasar layar.
//  - Gelembung: membesar sedikit saat naik, bergoyang, ada rangkaian gelembung dari beberapa titik, dan berkas
//    cahaya lembut dari atas.
// Mesinnya sama untuk semuanya dan sama hematnya dengan salju: posisi dihitung dari satu nilai waktu (tidak ada objek
// per partikel yang dibuat ulang), digambar hanya di fase draw pada lapisan GPU sendiri, gambar partikel dibuat sekali
// dan dipakai ulang, jumlahnya dikurangi di HP berRAM kecil dan di layar selain Beranda, dan berhenti sendiri saat layar
// tidak tampil. Satu efek = satu [Effect]: jenis partikel, arah gerak (turun, naik, atau melayang di tempat), angin,
// warna, dan jumlah. Menambah efek baru cukup menambah satu entri di [Effects].
// =============================================================================

const val HOME_EFFECT_SNOW = "snow"

/** Pilihan efek untuk Pengaturan: (id yang disimpan, nama yang ditampilkan). */
val HomeEffectChoices: List<Pair<String, String>> = listOf(
    "snow" to "Salju",
    "sakura" to "Sakura",
    "leaves" to "Daun musim gugur",
    "rain" to "Hujan",
    "fireflies" to "Kunang-kunang",
    "sparkle" to "Kilau bintang",
    "embers" to "Bara api",
    "bubbles" to "Gelembung"
)

/** Id efek yang dikenal; id lain (misalnya dari versi lain) jatuh ke salju. */
fun normalizeHomeEffect(id: String?): String {
    if (id != null) {
        for (choice in HomeEffectChoices) {
            if (choice.first == id) return id
        }
    }
    return HOME_EFFECT_SNOW
}

private const val FRAME_NS = 15_000_000L // sampai ~60 fps
private const val TWO_PI = 6.2831855f
private const val RAD_TO_DEG = 57.29578f

private const val S_DOT = 0
private const val S_GLOW = 1
private const val S_RING = 2
private const val S_PETAL = 3
private const val S_LEAF = 4
private const val S_SPARKLE = 5
private const val S_PETAL2 = 6
private const val S_BLOSSOM = 7
private const val S_MAPLE = 8
private const val S_GINKGO = 9
private const val S_BURST = 10

// Adegan: detail seluruh layar yang digambar sekali per frame di lapisan belakang (di luar partikel)
private const val SCENE_NONE = 0
private const val SCENE_RAIN = 1
private const val SCENE_EMBERS = 2
private const val SCENE_METEOR = 3
private const val SCENE_RAYS = 4

private val WHITE_FILTER = ColorFilter.tint(Color.White)
private val MIST_FILTER = ColorFilter.tint(Color(0xFFC4D8F2))
private val RIPPLE_COLOR = Color(0xFFDCE9FA)

/**
 * Satu jenis partikel, dari jauh (0) sampai dekat (1): ukuran (dp), kecepatan (dp/detik), kejernihan, kekuatan
 * kedip, ayunan (dp), putaran maksimum (derajat/detik), dan seberapa gepeng saat "terbalik" (0..1, untuk kelopak/daun).
 * Pada hujan, ukuran adalah panjang goresan dan [stroke] tebal goresan (dp).
 */
private class PSpec(
    val sprite: Int,
    val minDia: Float,
    val maxDia: Float,
    val minSpeed: Float,
    val maxSpeed: Float,
    val minAlpha: Float,
    val maxAlpha: Float,
    val twinkle: Float,
    val sway: Float,
    val spin: Float = 0f,
    val flip: Float = 0f,
    val stroke: Float = 0f,
    /** Kedipan tajam: >1 membuat bagian terang lebih singkat dan redupnya lebih lama (kunang-kunang). */
    val sharp: Float = 1f,
    /** Perubahan ukuran dari awal sampai ujung lintasan (+ membesar, - mengecil). */
    val grow: Float = 0f,
    /** Ukuran ikut berdenyut mengikuti kedip (0..1), untuk kilau. */
    val sizePulse: Float = 0f,
    /** Gambar inti kecil di tengah (-1 = tanpa), dengan ukuran relatif dan warnanya sendiri. */
    val core: Int = -1,
    val coreScale: Float = 0f,
    val coreColor: Color = Color.White
) {
    val coreFilter: ColorFilter? = if (core >= 0) ColorFilter.tint(coreColor) else null
}

private class Effect(
    val id: String,
    val seed: Int,
    /** +1 turun, -1 naik, 0 melayang di tempat. */
    val dirY: Float,
    /** Hanyutan angin tetap (dp/detik); pada hujan: kemiringan goresan (selisih x per y). */
    val wind: Float,
    /** Amplitudo gelombang angin yang berganti arah (dp). */
    val gust: Float,
    val rain: Boolean,
    val kinds: Array<PSpec>,
    val backWeights: FloatArray,
    val frontWeights: FloatArray,
    colors: Array<Color>,
    /** Jumlah partikel belakang per 1% "jumlah efek". */
    val backCount: Float,
    val frontBase: Int,
    val frontPerPct: Float,
    /** Warna dipilih menurut ketinggian (urutan panas ke dingin), bukan acak. */
    val heat: Boolean = false,
    val scene: Int = SCENE_NONE,
    /** Sebagian partikel muncul berbaris dari [columns] titik (rangkaian gelembung). */
    val columns: Int = 0,
    val streamShare: Float = 0f
) {
    val colors: Array<Color> = colors
    val filters: Array<ColorFilter> = Array(colors.size) { ColorFilter.tint(colors[it]) }
}

private object Effects {
    val sakura = Effect(
        id = "sakura", seed = 201, dirY = 1f, wind = 14f, gust = 30f, rain = false,
        kinds = arrayOf(
            PSpec(S_PETAL, 8f, 15f, 22f, 44f, 0.55f, 0.95f, 0.10f, 26f, spin = 70f, flip = 0.75f),
            PSpec(S_PETAL2, 12f, 20f, 26f, 50f, 0.50f, 0.90f, 0.08f, 30f, spin = 60f, flip = 0.70f),
            PSpec(S_PETAL, 14f, 24f, 30f, 58f, 0.40f, 0.85f, 0.08f, 34f, spin = 55f, flip = 0.80f),
            PSpec(
                S_BLOSSOM, 14f, 22f, 24f, 46f, 0.55f, 0.95f, 0.05f, 24f, spin = 28f, flip = 0.45f,
                core = S_DOT, coreScale = 0.20f, coreColor = Color(0xFFFFD36B)
            ),
            PSpec(S_GLOW, 18f, 34f, 14f, 28f, 0.05f, 0.14f, 0.30f, 16f)
        ),
        backWeights = floatArrayOf(0.36f, 0.22f, 0.26f, 0.10f, 0.06f),
        frontWeights = floatArrayOf(0.05f, 0.15f, 0.62f, 0.18f, 0f),
        colors = arrayOf(
            Color(0xFFFFC1D3), Color(0xFFFFB3C8), Color(0xFFFFD3E0), Color(0xFFFFE4EC), Color(0xFFFF9FBB)
        ),
        backCount = 0.55f, frontBase = 3, frontPerPct = 0.05f
    )
    val leaves = Effect(
        id = "leaves", seed = 202, dirY = 1f, wind = 18f, gust = 36f, rain = false,
        kinds = arrayOf(
            PSpec(S_LEAF, 10f, 17f, 30f, 55f, 0.60f, 0.95f, 0.06f, 34f, spin = 80f, flip = 0.55f),
            PSpec(S_MAPLE, 14f, 24f, 32f, 60f, 0.55f, 0.95f, 0.05f, 38f, spin = 70f, flip = 0.50f),
            PSpec(S_GINKGO, 12f, 21f, 30f, 56f, 0.55f, 0.92f, 0.05f, 36f, spin = 75f, flip = 0.55f),
            PSpec(S_LEAF, 16f, 28f, 36f, 66f, 0.45f, 0.90f, 0.05f, 44f, spin = 60f, flip = 0.60f)
        ),
        backWeights = floatArrayOf(0.28f, 0.28f, 0.20f, 0.24f),
        frontWeights = floatArrayOf(0.10f, 0.45f, 0.10f, 0.35f),
        colors = arrayOf(
            Color(0xFFE8731C), Color(0xFFC9452B), Color(0xFFF2B233), Color(0xFFB85C1E),
            Color(0xFF8FA33A), Color(0xFFA8321E), Color(0xFFE0A526)
        ),
        backCount = 0.40f, frontBase = 2, frontPerPct = 0.05f
    )
    val rain = Effect(
        id = "rain", seed = 203, dirY = 1f, wind = 0.14f, gust = 0f, rain = true,
        kinds = arrayOf(PSpec(S_DOT, 10f, 26f, 480f, 860f, 0.12f, 0.42f, 0f, 0f, stroke = 1.2f)),
        backWeights = floatArrayOf(1f),
        frontWeights = floatArrayOf(1f),
        colors = arrayOf(Color(0xFFCFE0F5)),
        backCount = 1.10f, frontBase = 4, frontPerPct = 0.08f,
        scene = SCENE_RAIN
    )
    val fireflies = Effect(
        id = "fireflies", seed = 204, dirY = 0f, wind = 0f, gust = 0f, rain = false,
        kinds = arrayOf(
            PSpec(
                S_GLOW, 8f, 16f, 0f, 0f, 0.20f, 0.95f, 0.90f, 38f, sharp = 3f,
                core = S_DOT, coreScale = 0.26f, coreColor = Color(0xFFFFFBD0)
            ),
            PSpec(S_GLOW, 5f, 10f, 0f, 0f, 0.20f, 0.90f, 0.85f, 34f, sharp = 2.5f),
            PSpec(S_GLOW, 24f, 42f, 0f, 0f, 0.05f, 0.11f, 0.50f, 30f)
        ),
        backWeights = floatArrayOf(0.45f, 0.40f, 0.15f),
        frontWeights = floatArrayOf(0.70f, 0.10f, 0.20f),
        colors = arrayOf(Color(0xFFE9F57B), Color(0xFFC8F26B), Color(0xFFFFF3A0)),
        backCount = 0.32f, frontBase = 2, frontPerPct = 0.03f
    )
    val sparkle = Effect(
        id = "sparkle", seed = 205, dirY = 0f, wind = 0f, gust = 0f, rain = false,
        kinds = arrayOf(
            PSpec(S_SPARKLE, 6f, 13f, 0f, 0f, 0.35f, 1.00f, 0.90f, 10f, spin = 20f, sharp = 2f, sizePulse = 0.5f),
            PSpec(S_BURST, 11f, 20f, 0f, 0f, 0.45f, 1.00f, 0.92f, 8f, spin = 12f, sharp = 2.5f, sizePulse = 0.45f),
            PSpec(S_DOT, 2f, 4f, 0f, 0f, 0.30f, 0.80f, 0.70f, 6f)
        ),
        backWeights = floatArrayOf(0.30f, 0.10f, 0.60f),
        frontWeights = floatArrayOf(0.55f, 0.35f, 0.10f),
        colors = arrayOf(Color(0xFFFFFFFF), Color(0xFFFFF0B8), Color(0xFFCFE6FF)),
        backCount = 0.70f, frontBase = 3, frontPerPct = 0.05f,
        scene = SCENE_METEOR
    )
    val embers = Effect(
        id = "embers", seed = 206, dirY = -1f, wind = 6f, gust = 14f, rain = false,
        kinds = arrayOf(
            PSpec(S_DOT, 2.5f, 5.5f, 18f, 46f, 0.55f, 1.00f, 0.60f, 12f, sharp = 1.6f, grow = -0.5f),
            PSpec(S_GLOW, 8f, 18f, 14f, 34f, 0.12f, 0.35f, 0.50f, 16f, grow = -0.4f),
            PSpec(S_DOT, 1.5f, 3f, 60f, 120f, 0.60f, 1.00f, 0.50f, 8f, grow = -0.3f)
        ),
        backWeights = floatArrayOf(0.55f, 0.30f, 0.15f),
        frontWeights = floatArrayOf(0.30f, 0.55f, 0.15f),
        // Urutan panas ke dingin: kuning-putih di dasar, memerah makin ke atas
        colors = arrayOf(
            Color(0xFFFFE9A8), Color(0xFFFFC857), Color(0xFFFF9A2E), Color(0xFFFF6A1F), Color(0xFFD8431A)
        ),
        backCount = 0.80f, frontBase = 3, frontPerPct = 0.06f,
        heat = true, scene = SCENE_EMBERS
    )
    val bubbles = Effect(
        id = "bubbles", seed = 207, dirY = -1f, wind = 0f, gust = 10f, rain = false,
        kinds = arrayOf(
            PSpec(S_RING, 8f, 17f, 14f, 30f, 0.35f, 0.75f, 0.15f, 12f, flip = 0.08f, grow = 0.30f),
            PSpec(S_RING, 18f, 34f, 12f, 24f, 0.25f, 0.55f, 0.12f, 16f, flip = 0.10f, grow = 0.25f),
            PSpec(S_DOT, 2f, 4f, 16f, 34f, 0.30f, 0.60f, 0.20f, 8f)
        ),
        backWeights = floatArrayOf(0.50f, 0.22f, 0.28f),
        frontWeights = floatArrayOf(0.25f, 0.65f, 0.10f),
        colors = arrayOf(Color(0xFFD8F1FF), Color(0xFFBFE8FF), Color(0xFFFFFFFF)),
        backCount = 0.30f, frontBase = 2, frontPerPct = 0.04f,
        scene = SCENE_RAYS, columns = 3, streamShare = 0.5f
    )

    fun byId(id: String): Effect = when (id) {
        "sakura" -> sakura
        "leaves" -> leaves
        "rain" -> rain
        "fireflies" -> fireflies
        "sparkle" -> sparkle
        "embers" -> embers
        else -> bubbles
    }
}

// ----- Gambar partikel (dibuat sekali untuk seluruh proses; putih, diberi warna saat digambar) -----

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

/** Cahaya lembut dengan inti terang (kunang-kunang, bara, kabut sakura). */
private fun glowSprite(): ImageBitmap = buildSprite(96) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.width / 2f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White,
            0.22f to Color.White.copy(alpha = 0.70f),
            0.55f to Color.White.copy(alpha = 0.18f),
            1f to Color.Transparent,
            center = c,
            radius = r
        ),
        radius = r,
        center = c
    )
}

/** Gelembung: bagian tengah bening, tepi lebih terang, dan satu titik kilap. */
private fun ringSprite(): ImageBitmap = buildSprite(96) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.width / 2f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.06f),
            0.70f to Color.White.copy(alpha = 0.14f),
            0.90f to Color.White.copy(alpha = 0.55f),
            0.97f to Color.White.copy(alpha = 0.25f),
            1f to Color.Transparent,
            center = c,
            radius = r
        ),
        radius = r,
        center = c
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = size.width * 0.07f,
        center = Offset(size.width * 0.33f, size.height * 0.30f)
    )
}

/** Kelopak sakura: dua tonjolan dengan takik kecil di ujung, menyempit ke pangkal. */
private fun petalSprite(): ImageBitmap = buildSprite(96) {
    val s = size.width
    val path = Path().apply {
        moveTo(0.50f * s, 0.93f * s)
        cubicTo(0.10f * s, 0.72f * s, 0.00f * s, 0.30f * s, 0.30f * s, 0.10f * s)
        cubicTo(0.37f * s, 0.05f * s, 0.45f * s, 0.10f * s, 0.50f * s, 0.20f * s)
        cubicTo(0.55f * s, 0.10f * s, 0.63f * s, 0.05f * s, 0.70f * s, 0.10f * s)
        cubicTo(1.00f * s, 0.30f * s, 0.90f * s, 0.72f * s, 0.50f * s, 0.93f * s)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            0f to Color.White,
            1f to Color.White.copy(alpha = 0.62f),
            startY = 0f,
            endY = s
        )
    )
    // Urat tengah tipis: sedikit mengurangi kepekatan di sepanjang garis
    drawLine(
        color = Color.Black.copy(alpha = 0.28f),
        start = Offset(0.50f * s, 0.30f * s),
        end = Offset(0.50f * s, 0.84f * s),
        strokeWidth = s * 0.022f,
        cap = StrokeCap.Round,
        blendMode = BlendMode.DstOut
    )
}

/** Daun runcing dengan tulang daun dan urat cabang. */
private fun leafSprite(): ImageBitmap = buildSprite(96) {
    val s = size.width
    val path = Path().apply {
        moveTo(0.50f * s, 0.04f * s)
        cubicTo(0.98f * s, 0.28f * s, 0.92f * s, 0.76f * s, 0.50f * s, 0.97f * s)
        cubicTo(0.08f * s, 0.76f * s, 0.02f * s, 0.28f * s, 0.50f * s, 0.04f * s)
        close()
    }
    drawPath(path = path, color = Color.White)
    drawLine(
        color = Color.Black.copy(alpha = 0.45f),
        start = Offset(0.50f * s, 0.16f * s),
        end = Offset(0.50f * s, 0.97f * s),
        strokeWidth = s * 0.035f,
        cap = StrokeCap.Round,
        blendMode = BlendMode.DstOut
    )
    for (i in 0 until 3) {
        val y = (0.38f + i * 0.17f) * s
        for (side in 0 until 2) {
            val dx = if (side == 0) -0.20f else 0.20f
            drawLine(
                color = Color.Black.copy(alpha = 0.30f),
                start = Offset(0.50f * s, y),
                end = Offset((0.50f + dx) * s, y - 0.12f * s),
                strokeWidth = s * 0.022f,
                cap = StrokeCap.Round,
                blendMode = BlendMode.DstOut
            )
        }
    }
}

/** Kilau empat sudut dengan cahaya lembut di tengah. */
private fun sparkleSprite(): ImageBitmap = buildSprite(96) {
    val s = size.width
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.55f),
            1f to Color.Transparent,
            center = Offset(0.5f * s, 0.5f * s),
            radius = 0.30f * s
        ),
        radius = 0.30f * s,
        center = Offset(0.5f * s, 0.5f * s)
    )
    val path = Path().apply {
        moveTo(0.500f * s, 0.02f * s)
        lineTo(0.585f * s, 0.415f * s)
        lineTo(0.980f * s, 0.500f * s)
        lineTo(0.585f * s, 0.585f * s)
        lineTo(0.500f * s, 0.980f * s)
        lineTo(0.415f * s, 0.585f * s)
        lineTo(0.020f * s, 0.500f * s)
        lineTo(0.415f * s, 0.415f * s)
        close()
    }
    drawPath(path = path, color = Color.White)
}

/** Kelopak lain: lebih bulat dan takiknya dangkal, supaya hamparan kelopak tidak terlihat seragam. */
private fun petal2Sprite(): ImageBitmap = buildSprite(96) {
    val s = size.width
    val path = Path().apply {
        moveTo(0.50f * s, 0.94f * s)
        cubicTo(0.06f * s, 0.78f * s, 0.04f * s, 0.30f * s, 0.34f * s, 0.14f * s)
        cubicTo(0.42f * s, 0.10f * s, 0.47f * s, 0.14f * s, 0.50f * s, 0.20f * s)
        cubicTo(0.53f * s, 0.14f * s, 0.58f * s, 0.10f * s, 0.66f * s, 0.14f * s)
        cubicTo(0.96f * s, 0.30f * s, 0.94f * s, 0.78f * s, 0.50f * s, 0.94f * s)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            0f to Color.White,
            1f to Color.White.copy(alpha = 0.55f),
            startY = 0f,
            endY = s
        )
    )
}

/** Bunga sakura utuh: lima kelopak mengelilingi pusat (pusat emasnya digambar terpisah sebagai inti). */
private fun blossomSprite(): ImageBitmap = buildSprite(96) {
    val s = size.width
    val center = Offset(0.5f * s, 0.5f * s)
    val path = Path().apply {
        moveTo(0.50f * s, 0.50f * s)
        cubicTo(0.33f * s, 0.40f * s, 0.29f * s, 0.18f * s, 0.40f * s, 0.07f * s)
        cubicTo(0.44f * s, 0.04f * s, 0.48f * s, 0.07f * s, 0.50f * s, 0.12f * s)
        cubicTo(0.52f * s, 0.07f * s, 0.56f * s, 0.04f * s, 0.60f * s, 0.07f * s)
        cubicTo(0.71f * s, 0.18f * s, 0.67f * s, 0.40f * s, 0.50f * s, 0.50f * s)
        close()
    }
    for (k in 0 until 5) {
        rotate(degrees = k * 72f, pivot = center) {
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    0f to Color.White,
                    1f to Color.White.copy(alpha = 0.70f),
                    startY = 0.05f * s,
                    endY = 0.50f * s
                )
            )
        }
    }
}

/** Daun maple lima lekuk dengan urat dari pangkal ke tiap ujung. */
private fun mapleSprite(): ImageBitmap = buildSprite(96) {
    val s = size.width
    val right = floatArrayOf(
        0.50f, 0.03f, 0.57f, 0.17f, 0.67f, 0.11f, 0.65f, 0.29f, 0.80f, 0.24f, 0.75f, 0.38f,
        0.94f, 0.40f, 0.84f, 0.50f, 0.97f, 0.62f, 0.77f, 0.64f, 0.70f, 0.78f, 0.55f, 0.72f
    )
    val path = Path().apply {
        moveTo(right[0] * s, right[1] * s)
        var i = 2
        while (i < right.size) {
            lineTo(right[i] * s, right[i + 1] * s)
            i += 2
        }
        lineTo(0.53f * s, 0.97f * s)
        lineTo(0.47f * s, 0.97f * s)
        i = right.size - 2
        while (i >= 2) {
            lineTo((1f - right[i]) * s, right[i + 1] * s)
            i -= 2
        }
        close()
    }
    drawPath(path = path, color = Color.White)
    val veins = floatArrayOf(0.50f, 0.10f, 0.78f, 0.32f, 0.90f, 0.52f, 0.72f, 0.68f)
    var v = 0
    while (v < veins.size) {
        for (side in 0 until 2) {
            val x = if (side == 0) veins[v] else 1f - veins[v]
            drawLine(
                color = Color.Black.copy(alpha = 0.40f),
                start = Offset(0.50f * s, 0.78f * s),
                end = Offset(x * s, veins[v + 1] * s),
                strokeWidth = s * 0.022f,
                cap = StrokeCap.Round,
                blendMode = BlendMode.DstOut
            )
        }
        v += 2
    }
}

/** Daun ginkgo berbentuk kipas dengan takik di tengah dan urat yang menyebar dari pangkal. */
private fun ginkgoSprite(): ImageBitmap = buildSprite(96) {
    val s = size.width
    val path = Path().apply {
        moveTo(0.50f * s, 0.96f * s)
        lineTo(0.06f * s, 0.34f * s)
        cubicTo(0.02f * s, 0.14f * s, 0.30f * s, 0.04f * s, 0.44f * s, 0.10f * s)
        lineTo(0.50f * s, 0.30f * s)
        lineTo(0.56f * s, 0.10f * s)
        cubicTo(0.70f * s, 0.04f * s, 0.98f * s, 0.14f * s, 0.94f * s, 0.34f * s)
        close()
    }
    drawPath(path = path, color = Color.White)
    val ends = floatArrayOf(0.14f, 0.28f, 0.24f, 0.14f, 0.38f, 0.10f, 0.62f, 0.10f, 0.76f, 0.14f, 0.86f, 0.28f)
    var i = 0
    while (i < ends.size) {
        drawLine(
            color = Color.Black.copy(alpha = 0.32f),
            start = Offset(0.50f * s, 0.92f * s),
            end = Offset(ends[i] * s, ends[i + 1] * s),
            strokeWidth = s * 0.018f,
            cap = StrokeCap.Round,
            blendMode = BlendMode.DstOut
        )
        i += 2
    }
}

/** Kilau delapan sudut: empat ujung panjang dan empat pendek di antaranya, dengan cahaya lembut di tengah. */
private fun burstSprite(): ImageBitmap = buildSprite(96) {
    val s = size.width
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.60f),
            1f to Color.Transparent,
            center = Offset(0.5f * s, 0.5f * s),
            radius = 0.26f * s
        ),
        radius = 0.26f * s,
        center = Offset(0.5f * s, 0.5f * s)
    )
    val path = Path()
    for (k in 0 until 8) {
        val tipAngle = k * (PI.toFloat() / 4f) - PI.toFloat() / 2f
        val innerAngle = tipAngle + PI.toFloat() / 8f
        val tipR = if (k % 2 == 0) 0.48f else 0.26f
        val tx = (0.5f + cos(tipAngle) * tipR) * s
        val ty = (0.5f + sin(tipAngle) * tipR) * s
        val ix = (0.5f + cos(innerAngle) * 0.08f) * s
        val iy = (0.5f + sin(innerAngle) * 0.08f) * s
        if (k == 0) path.moveTo(tx, ty) else path.lineTo(tx, ty)
        path.lineTo(ix, iy)
    }
    path.close()
    drawPath(path = path, color = Color.White)
}

private object EffectSprites {
    val images: Array<ImageBitmap> by lazy {
        arrayOf(
            dotSprite(), glowSprite(), ringSprite(), petalSprite(), leafSprite(), sparkleSprite(),
            petal2Sprite(), blossomSprite(), mapleSprite(), ginkgoSprite(), burstSprite()
        )
    }
}

// ----- Isi lapisan -----

/** Data tetap tiap partikel: jenis, posisi awal (0..1), kedalaman (0 jauh, 1 dekat), fase, putaran, dan warna. */
private class PField(
    val count: Int,
    seed: Int,
    weights: FloatArray,
    nearBias: Boolean,
    paletteSize: Int,
    columns: Int,
    streamShare: Float
) {
    val kind = IntArray(count)
    val x0 = FloatArray(count)
    val y0 = FloatArray(count)
    val depth = FloatArray(count)
    val phase = FloatArray(count)
    val freq = FloatArray(count)
    val spin = FloatArray(count)
    val speedJit = FloatArray(count)
    val tint = IntArray(count)

    init {
        val rnd = Random(seed)
        var total = 0f
        var positive = 0
        for (w in weights) {
            total += w
            if (w > 0f) positive++
        }
        // Bila partikelnya cukup banyak, tiap jenis yang berbobot muncul minimal sekali (supaya bentuk yang jarang,
        // seperti bunga sakura utuh, tidak kebetulan tidak pernah muncul), sisanya dipilih acak menurut bobot.
        val guaranteed = ArrayList<Int>()
        if (count >= positive * 2) {
            for (j in weights.indices) if (weights[j] > 0f) guaranteed.add(j)
        }
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
            if (i < guaranteed.size) kd = guaranteed[i]
            kind[i] = kd
            x0[i] = rnd.nextFloat()
            if (columns > 0 && rnd.nextFloat() < streamShare) {
                // Berbaris dari beberapa titik (rangkaian gelembung dari celah di dasar)
                x0[i] = (rnd.nextInt(columns) + 0.5f) / columns + (rnd.nextFloat() - 0.5f) * 0.03f
            }
            y0[i] = rnd.nextFloat()
            val u = rnd.nextFloat()
            depth[i] = if (nearBias) 0.4f + 0.6f * u else 0.15f + 0.85f * u
            phase[i] = rnd.nextFloat() * TWO_PI
            freq[i] = 0.45f + rnd.nextFloat() * 1.0f
            spin[i] = (rnd.nextFloat() - 0.5f) * 2f
            speedJit[i] = 0.85f + rnd.nextFloat() * 0.3f
            tint[i] = rnd.nextInt(paletteSize)
        }
    }
}

private fun wrap(v: Float, span: Float): Float {
    val m = v % span
    return if (m < 0f) m + span else m
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Angka acak tetap 0..1 dari dua bilangan bulat (hasil sama setiap dipanggil): untuk jadwal riak dan bintang jatuh. */
private fun hash01(a: Int, b: Int): Float {
    var x = a * 374761393 + b * 668265263
    x = (x xor (x ushr 13)) * 1274126177.toInt()
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777216f
}

private fun DrawScope.drawSprite(img: ImageBitmap, side: Int, alpha: Float, tint: ColorFilter) {
    drawImage(
        image = img,
        dstSize = IntSize(side, side),
        alpha = alpha,
        colorFilter = tint,
        filterQuality = FilterQuality.Medium
    )
}

@Composable
private fun EffectCanvas(
    fx: Effect,
    count: Int,
    seed: Int,
    weights: FloatArray,
    nearBias: Boolean,
    alphaScale: Float,
    speedScale: Float,
    sizeScale: Float,
    withScene: Boolean,
    modifier: Modifier = Modifier
) {
    val field = remember(fx, count, seed, nearBias) {
        PField(count, seed, weights, nearBias, fx.filters.size, fx.columns, fx.streamShare)
    }
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
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { }
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            if (withScene && fx.scene != SCENE_NONE) {
                drawScene(fx, time.floatValue, w, h, density, alphaScale)
            }
            if (fx.rain) {
                drawRain(fx, field, time.floatValue, w, h, density, alphaScale, speedScale, sizeScale)
            } else {
                drawParticles(fx, field, time.floatValue, w, h, density, alphaScale, speedScale, sizeScale)
            }
        }
    }
}

private fun DrawScope.drawParticles(
    fx: Effect,
    field: PField,
    t: Float,
    w: Float,
    h: Float,
    d: Float,
    alphaScale: Float,
    speedScale: Float,
    sizeScale: Float
) {
    val images = EffectSprites.images
    val drifting = fx.dirY == 0f
    // Hembusan angin: hanyutan tetap ditambah gelombang lambat sehingga arah angin terasa berganti.
    val windDp = t * fx.wind + fx.gust * sin(t * 0.17f)
    val fadeTop = h * 0.08f
    val fadeBottom = h * 0.12f

    for (i in 0 until field.count) {
        val spec = fx.kinds[field.kind[i]]
        val dp = field.depth[i]
        val baseSide = lerp(spec.minDia, spec.maxDia, dp) * sizeScale * d
        if (baseSide < 2f) continue
        val f = field.freq[i]
        val ph = field.phase[i]

        val x: Float
        val y: Float
        var fade = 1f
        // Seberapa jauh lintasannya sudah ditempuh (0 di awal, 1 di ujung): untuk perubahan ukuran dan warna panas
        var prog = 0.5f
        if (drifting) {
            // Melayang di tempat: berkeliling pelan di sekitar titik asalnya.
            val ampX = spec.sway * d * (1f + dp)
            val ampY = spec.sway * 0.8f * d * (1f + dp)
            x = (0.04f + 0.92f * field.x0[i]) * w +
                ampX * (sin(t * f * 0.5f + ph) * 0.65f + sin(t * f * 1.1f + ph * 1.9f) * 0.35f)
            y = (0.06f + 0.88f * field.y0[i]) * h +
                ampY * (cos(t * f * 0.43f + ph * 1.3f) * 0.65f + sin(t * f * 0.9f + ph * 0.7f) * 0.35f)
        } else {
            val speed = lerp(spec.minSpeed, spec.maxSpeed, dp) * field.speedJit[i] * speedScale * d
            val margin = baseSide * 1.5f
            val spanY = h + margin * 2f
            y = wrap(field.y0[i] * spanY + fx.dirY * t * speed, spanY) - margin
            val sway = (sin(t * f + ph) * 0.7f + sin(t * f * 2.3f + ph * 1.7f) * 0.3f) *
                spec.sway * (0.6f + 0.8f * dp) * d
            val wind = windDp * (0.35f + 0.9f * dp) * d
            val spanX = w + margin * 2f
            x = wrap(field.x0[i] * spanX + sway + wind, spanX) - margin
            // Memudar di tepi atas dan bawah supaya tidak muncul atau hilang mendadak.
            fade = (y / fadeTop).coerceIn(0f, 1f) * ((h - y) / fadeBottom).coerceIn(0f, 1f)
            val down = (y / h).coerceIn(0f, 1f)
            prog = if (fx.dirY > 0f) down else 1f - down
        }

        // Kedip. Bila spec.sharp > 1 bagian terangnya diperpendek dan redupnya diperlama (kunang-kunang, kilau).
        val w01 = 0.5f + 0.5f * sin(t * f * 1.9f + ph * 2.3f)
        val lit = if (spec.sharp > 1f) w01.pow(spec.sharp) else w01
        val twinkle = 1f - spec.twinkle * (1f - lit)
        val alpha = lerp(spec.minAlpha, spec.maxAlpha, dp) * alphaScale * twinkle * fade
        if (alpha < 0.01f) continue

        val side = (baseSide * (1f + spec.grow * prog) * (1f - spec.sizePulse * (1f - lit))).roundToInt()
        if (side < 2) continue

        // Partikel di zona pembungkus di luar layar tidak perlu digambar
        val reach = side / 2f
        if (x + reach < 0f || x - reach > w || y + reach < 0f || y - reach > h) continue

        val img = images[spec.sprite]
        // Warna: menurut ketinggian untuk efek panas (bara mendingin saat naik), selain itu tetap per partikel.
        val tintIdx = if (fx.heat) {
            (prog * fx.filters.size).toInt().coerceIn(0, fx.filters.size - 1)
        } else {
            field.tint[i]
        }
        val tint = fx.filters[tintIdx]
        val half = side / 2f
        val pivot = Offset(half, half)
        translate(left = x - half, top = y - half) {
            if (spec.spin != 0f || spec.flip > 0f) {
                // Berputar, dan terbalik pelan di udara (dilebarkan dan dipipihkan bergantian) atau bergoyang.
                val angle = if (spec.spin != 0f) ph * RAD_TO_DEG + t * spec.spin * field.spin[i] else 0f
                val sx = if (spec.flip > 0f) 1f - spec.flip * (0.5f + 0.5f * sin(t * f * 1.7f + ph * 1.3f)) else 1f
                rotate(degrees = angle, pivot = pivot) {
                    scale(scaleX = sx, scaleY = 1f, pivot = pivot) {
                        drawSprite(img, side, alpha, tint)
                    }
                }
            } else {
                drawSprite(img, side, alpha, tint)
            }
            // Inti kecil di tengah (pusat bunga emas, inti kunang-kunang), tidak ikut berputar
            val coreFilter = spec.coreFilter
            if (spec.core >= 0 && coreFilter != null) {
                val cs = (side * spec.coreScale).roundToInt()
                if (cs >= 2) {
                    translate(left = half - cs / 2f, top = half - cs / 2f) {
                        drawSprite(images[spec.core], cs, minOf(1f, alpha * 1.3f), coreFilter)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawRain(
    fx: Effect,
    field: PField,
    t: Float,
    w: Float,
    h: Float,
    d: Float,
    alphaScale: Float,
    speedScale: Float,
    sizeScale: Float
) {
    val spec = fx.kinds[0]
    // Kemiringan goresan berayun pelan mengikuti hembusan angin
    val slope = fx.wind + 0.04f * sin(t * 0.23f)
    val fadeTop = h * 0.06f
    val fadeBottom = h * 0.10f
    val color = fx.colors[0]

    for (i in 0 until field.count) {
        val dp = field.depth[i]
        val len = lerp(spec.minDia, spec.maxDia, dp) * sizeScale * d
        if (len < 2f) continue
        val speed = lerp(spec.minSpeed, spec.maxSpeed, dp) * field.speedJit[i] * speedScale * d
        val spanY = h + len * 2f
        val y = wrap(field.y0[i] * spanY + t * speed, spanY) - len
        val spanX = w + len * slope * 2f
        val x = wrap(field.x0[i] * spanX, spanX) - len * slope
        val fade = (y / fadeTop).coerceIn(0f, 1f) * ((h - y) / fadeBottom).coerceIn(0f, 1f)
        val alpha = lerp(spec.minAlpha, spec.maxAlpha, dp) * alphaScale * fade
        if (alpha < 0.01f) continue
        drawLine(
            color = color.copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(x - len * slope, y - len),
            strokeWidth = lerp(spec.stroke * 0.7f, spec.stroke * 1.4f, dp) * d,
            cap = StrokeCap.Round
        )
    }
}

// ----- Adegan: detail seluruh layar tiap efek (hanya di lapisan belakang) -----

private fun DrawScope.drawScene(fx: Effect, t: Float, w: Float, h: Float, d: Float, alphaScale: Float) {
    when (fx.scene) {
        SCENE_RAIN -> drawRainScene(t, w, h, d, alphaScale)
        SCENE_EMBERS -> drawEmberScene(t, h, w, alphaScale)
        SCENE_METEOR -> drawMeteorScene(t, w, h, d, alphaScale)
        SCENE_RAYS -> drawRaysScene(t, w, h, alphaScale)
    }
}

/** Hujan: kabut tipis di dekat tanah (gradasi tetap ditambah tiga gumpal yang hanyut pelan) dan riak di tanah. */
private fun DrawScope.drawRainScene(t: Float, w: Float, h: Float, d: Float, alphaScale: Float) {
    val top = h * 0.68f
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Color(0xFFBFD4F0).copy(alpha = 0.10f * alphaScale),
            startY = top,
            endY = h
        ),
        topLeft = Offset(0f, top),
        size = Size(w, h - top)
    )
    val blob = (w * 0.55f).roundToInt()
    if (blob >= 2) {
        val span = w + blob
        for (k in 0 until 3) {
            val x = wrap(k * span / 3f + t * (9f + 5f * k) * d, span) - blob
            val y = h * (0.90f - 0.05f * k) - blob / 2f
            // Gumpal yang sedang seluruhnya di luar layar tidak digambar
            if (x + blob > 0f && x < w) {
                translate(left = x, top = y) {
                    drawSprite(EffectSprites.images[S_GLOW], blob, 0.07f * alphaScale, MIST_FILTER)
                }
            }
        }
    }
    // Riak: cincin pipih yang melebar lalu memudar; tempat dan waktunya berganti tiap putaran.
    for (k in 0 until 14) {
        val period = 0.9f + 0.6f * hash01(k, 7)
        val shifted = t + hash01(k, 11) * period
        val cycle = (shifted / period).toInt()
        val phase = (shifted - cycle * period) / 0.65f
        if (phase < 0f || phase > 1f) continue
        val cx = hash01(k, cycle * 2 + 1) * w
        val cy = h * (0.80f + 0.17f * hash01(k, cycle * 2 + 2))
        val r = (2f + 16f * phase) * d
        val left = 1f - phase
        drawOval(
            color = RIPPLE_COLOR.copy(alpha = 0.30f * left * left * alphaScale),
            topLeft = Offset(cx - r, cy - r * 0.35f),
            size = Size(r * 2f, r * 0.7f),
            style = Stroke(width = 1.1f * d)
        )
    }
}

/** Bara: cahaya hangat berdenyut tak beraturan di dasar layar, seperti dekat api. */
private fun DrawScope.drawEmberScene(t: Float, h: Float, w: Float, alphaScale: Float) {
    val pulse = 0.5f + 0.25f * sin(t * 0.9f) + 0.25f * sin(t * 2.3f + 1f)
    val top = h * 0.62f
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Color(0xFFFF6A1A).copy(alpha = (0.10f + 0.10f * pulse) * alphaScale),
            startY = top,
            endY = h
        ),
        topLeft = Offset(0f, top),
        size = Size(w, h - top)
    )
}

/**
 * Kilau bintang: sesekali satu bintang jatuh lewat pelan di bagian atas layar (sekitar 1 detik, tiap ~19 detik),
 * muncul dan hilang dengan halus, tanpa kilatan mendadak.
 */
private fun DrawScope.drawMeteorScene(t: Float, w: Float, h: Float, d: Float, alphaScale: Float) {
    val period = 19f
    val cycle = (t / period).toInt()
    val local = (t - cycle * period) - 6f
    val duration = 1.15f
    if (local < 0f || local > duration) return
    val p = local / duration
    val envelope = sin(p * 3.1415927f)
    val dx = if (hash01(cycle, 3) > 0.5f) 1f else -1f
    val startX = if (dx > 0f) (0.05f + 0.35f * hash01(cycle, 4)) * w else (0.60f + 0.35f * hash01(cycle, 4)) * w
    val startY = (0.06f + 0.26f * hash01(cycle, 5)) * h
    val headX = startX + dx * 0.5f * w * p
    val headY = startY + 0.22f * w * p
    val trail = 0.20f * w
    val segments = 12
    var prevX = headX
    var prevY = headY
    for (s in 1..segments) {
        val f = s / segments.toFloat()
        val px = headX - dx * trail * f
        val py = headY - trail * 0.44f * f
        drawLine(
            color = Color.White.copy(alpha = (1f - f) * 0.85f * envelope * alphaScale),
            start = Offset(prevX, prevY),
            end = Offset(px, py),
            strokeWidth = (2.2f * (1f - f) + 0.6f) * d,
            cap = StrokeCap.Round
        )
        prevX = px
        prevY = py
    }
    val head = (10f * d).roundToInt()
    if (head >= 2) {
        translate(left = headX - head / 2f, top = headY - head / 2f) {
            drawSprite(EffectSprites.images[S_GLOW], head, 0.9f * envelope * alphaScale, WHITE_FILTER)
        }
    }
}

/** Gelembung: tiga berkas cahaya lembut dari atas yang bergeser dan berdenyut pelan, seperti di bawah permukaan air. */
private fun DrawScope.drawRaysScene(t: Float, w: Float, h: Float, alphaScale: Float) {
    for (k in 0 until 3) {
        val baseX = w * (0.20f + 0.30f * k) + sin(t * 0.12f + k * 2.1f) * 0.04f * w
        val halfTop = w * (0.05f + 0.015f * k)
        val slant = h * 0.30f
        val pulse = 0.7f + 0.3f * sin(t * 0.35f + k * 1.7f)
        val path = Path().apply {
            moveTo(baseX - halfTop, 0f)
            lineTo(baseX + halfTop, 0f)
            lineTo(baseX + halfTop * 2.6f - slant, h)
            lineTo(baseX - halfTop * 2.6f - slant, h)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                0f to Color(0xFFD8F4FF).copy(alpha = 0.10f * pulse * alphaScale),
                1f to Color.Transparent,
                startY = 0f,
                endY = h
            )
        )
    }
}

@Composable
private fun rememberLowRamDevice(): Boolean {
    val context = LocalContext.current
    return remember { SettingsManager.getTotalDeviceMemoryMb(context) < 4500 }
}

/**
 * Efek di belakang semua UI. [effect] salah satu id di [HomeEffectChoices]; salju memakai [SnowBackLayer] apa adanya.
 * [amount] 10..100 (persen). [indoor] = layar selain Beranda: jumlah dikurangi banyak dan lebih redup.
 */
@Composable
fun HomeEffectBackLayer(
    effect: String,
    amount: Int,
    indoor: Boolean,
    modifier: Modifier = Modifier
) {
    val kind = normalizeHomeEffect(effect)
    val lowRam = rememberLowRamDevice()
    if (kind == HOME_EFFECT_SNOW) {
        SnowBackLayer(amount = amount, indoor = indoor, modifier = modifier)
    } else {
        val fx = Effects.byId(kind)
        val a = amount.coerceIn(10, 100)
        val count = a * fx.backCount * (if (indoor) 0.38f else 1f) * (if (lowRam) 0.7f else 1f)
        EffectCanvas(
            fx = fx,
            count = count.roundToInt().coerceAtLeast(4),
            seed = fx.seed + (if (indoor) 0 else 10),
            weights = fx.backWeights,
            nearBias = false,
            alphaScale = if (indoor) 0.5f else 1f,
            speedScale = if (indoor) 0.8f else 1f,
            sizeScale = if (indoor) 0.9f else 1f,
            withScene = true,
            modifier = modifier
        )
    }
}

/** Beberapa partikel di atas UI. Sedikit supaya tidak mengganggu bacaan. */
@Composable
fun HomeEffectFrontLayer(
    effect: String,
    amount: Int,
    indoor: Boolean,
    modifier: Modifier = Modifier
) {
    val kind = normalizeHomeEffect(effect)
    val lowRam = rememberLowRamDevice()
    if (kind == HOME_EFFECT_SNOW) {
        SnowFrontLayer(amount = amount, indoor = indoor, modifier = modifier)
    } else {
        val fx = Effects.byId(kind)
        val a = amount.coerceIn(10, 100)
        val base = if (indoor) 3 + a * 0.04f else fx.frontBase + a * fx.frontPerPct
        val count = base * (if (lowRam) 0.7f else 1f)
        EffectCanvas(
            fx = fx,
            count = count.roundToInt().coerceAtLeast(2),
            seed = fx.seed + (if (indoor) 30 else 40),
            weights = fx.frontWeights,
            nearBias = true,
            alphaScale = if (indoor) 0.6f else 0.95f,
            speedScale = if (indoor) 1f else 1.15f,
            sizeScale = if (indoor) 1f else 1.1f,
            withScene = false,
            modifier = modifier
        )
    }
}
