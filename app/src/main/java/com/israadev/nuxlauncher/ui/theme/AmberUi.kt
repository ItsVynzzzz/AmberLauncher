package com.israadev.nuxlauncher.ui.theme

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp

/**
 * Satu pilihan warna aksen. [base] isian tombol, [edge] tepi, [soft] teks/ikon terang, [deep] bayangan keras.
 * [end] hanya diisi untuk aksen gradient halus: isian bergeser dari [base] ke [end] (kiri ke kanan).
 */
class AmberAccent(
    val id: String,
    val label: String,
    val base: Color,
    val edge: Color,
    val soft: Color,
    val deep: Color,
    val end: Color? = null
) {
    /** Isian untuk tombol dan penanda terpilih: warna polos, atau gradient bila [end] ada. */
    val brush: Brush
        get() {
            val to = end
            return if (to == null) SolidColor(base) else Brush.horizontalGradient(listOf(base, to))
        }
}

/** Menurunkan warna tepi, teks terang, dan bayangan dari warna dasar (dan warna akhir untuk gradient). */
private fun accentOf(id: String, label: String, base: Color, end: Color? = null): AmberAccent {
    val mid = if (end == null) base else lerp(base, end, 0.5f)
    return AmberAccent(
        id = id,
        label = label,
        base = base,
        edge = lerp(mid, Color.White, 0.55f),
        soft = lerp(mid, Color.White, 0.62f),
        deep = lerp(base, Color.Black, 0.55f),
        end = end
    )
}

/** Pilihan aksen yang tampil di Pengaturan > Tampilan. Navy adalah bawaan. */
val AMBER_ACCENTS = listOf(
    AmberAccent("navy", "Navy", Color(0xFF1F3380), Color(0xFF8F99C0), Color(0xFFA9BDFF), Color(0xFF0F1A4D)),
    AmberAccent("green", "Hijau", Color(0xFF2FB86C), Color(0xFF9ADDB8), Color(0xFF8DF0B8), Color(0xFF14603A)),
    AmberAccent("purple", "Ungu", Color(0xFF6B4BD1), Color(0xFFB9A8EC), Color(0xFFC9B8FF), Color(0xFF39257D)),
    AmberAccent("teal", "Teal", Color(0xFF14A3A8), Color(0xFF8FD6D9), Color(0xFF8FE6EA), Color(0xFF0A5659)),
    // Warna yang sering menyatu dengan wallpaper Minecraft: langit malam, hutan, senja, laut, bunga sakura, obor.
    accentOf("blue", "Biru", Color(0xFF2F62D8)),
    accentOf("sky", "Biru langit", Color(0xFF1E88C7)),
    accentOf("pink", "Merah muda", Color(0xFFC93F78)),
    accentOf("red", "Merah", Color(0xFFCC3F4A)),
    accentOf("orange", "Jingga", Color(0xFFD9692A)),
    accentOf("amber", "Amber", Color(0xFFC48A14)),
    accentOf("forest", "Hutan", Color(0xFF2E7D4F)),
    accentOf("gray", "Abu-abu", Color(0xFF52627A)),
    // Gradient halus: dua warna berdekatan yang bergeser pelan dari kiri ke kanan.
    accentOf("dusk", "Senja", Color(0xFF6B4BD1), Color(0xFFC04B9C)),
    accentOf("dawn", "Fajar", Color(0xFFD9692A), Color(0xFFC93F78)),
    accentOf("ocean", "Samudra", Color(0xFF1F5FD0), Color(0xFF14A3A8)),
    accentOf("aurora", "Aurora", Color(0xFF14A3A8), Color(0xFF5B6CE0)),
    accentOf("midnight", "Tengah malam", Color(0xFF1F3380), Color(0xFF6B4BD1)),
    accentOf("ember", "Bara", Color(0xFFCC3F4A), Color(0xFFD9892A))
)

/**
 * Gaya tampilan: DEFAULT = tema AmberLauncher sendiri, LIQUID = kaca buram (blur) ala liquid glass,
 * SIMPLE = panel tipis tembus pandang yang berwarna sesuai satu warna pilihan pengguna (tanpa blur).
 */
enum class AmberStyle(val id: String, val label: String) {
    DEFAULT("default", "Default"),
    LIQUID("liquid", "Liquid Glass"),
    SIMPLE("simple", "Simple");

    companion object {
        fun fromId(id: String?): AmberStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Warna gaya Simple, diturunkan dari satu warna pilihan: [hue] 0 - 359 dan [tone] 0 - 100 (kepekatan warna,
 * 0 = abu-abu netral). Panel gelap tembus pandang yang diwarnai tipis, tepi tipis yang ikut berwarna, dan
 * aksen dari warna yang sama, jadi seluruh launcher terlihat satu warna yang serasi dengan wallpaper pilihan.
 */
private class SimplePalette(hue: Int, tone: Int) {
    private val h = hue.coerceIn(0, 359).toFloat()
    private val t = tone.coerceIn(0, 100) / 100f

    val surface = Color.hsv(h, 0.80f * t, 0.20f, 0.46f)
    val surfaceRaised = Color.hsv(h, 0.35f * t, 1f, 0.10f)
    val border = Color.hsv(h, 0.30f * t, 1f, 0.26f)
    val borderStrong = Color.hsv(h, 0.30f * t, 1f, 0.44f)
    val homeGlass = Color.hsv(h, 0.70f * t, 0.16f, 0.64f)
    val homeGlassEdge = Color.hsv(h, 0.30f * t, 1f, 0.34f)
    val menuCard = Color.hsv(h, 0.55f * t, 0.11f, 0.94f)
    val accent = accentOf("simple", "Simple", Color.hsv(h, 0.80f * t, 0.50f + 0.10f * t))
}

/**
 * Warna tampilan baru: kaca gelap dengan aksen. Dipakai Home, Mod, Akun, Pengaturan.
 * Aksen dan gaya bisa diganti pengguna; karena disimpan sebagai state Compose, semua layar
 * langsung ikut berubah tanpa memulai ulang.
 */
object AmberUi {
    private var accentPreset by mutableStateOf(AMBER_ACCENTS.first())
    private var simpleHue by mutableStateOf(215)
    private var simpleTone by mutableStateOf(55)
    private val simple by derivedStateOf { SimplePalette(simpleHue, simpleTone) }

    /** Aksen yang berlaku: turunan warna pilihan untuk gaya Simple, selain itu pilihan aksen biasa. */
    private val activeAccent: AmberAccent
        get() = if (style == AmberStyle.SIMPLE) simple.accent else accentPreset

    /** Gaya tampilan aktif. */
    var style by mutableStateOf(AmberStyle.DEFAULT)
        private set

    /** Seberapa gelap latar (0f..1f), dari slider "Redupkan latar". Dipakai juga oleh kaca Liquid Glass. */
    var dim by mutableStateOf(0.5f)
        private set

    fun applyDim(value: Float) {
        dim = value.coerceIn(0f, 1f)
    }

    fun setAccent(id: String?) {
        accentPreset = AMBER_ACCENTS.firstOrNull { it.id == id } ?: AMBER_ACCENTS.first()
    }

    fun setStyle(id: String?) {
        style = AmberStyle.fromId(id)
    }

    /** Warna gaya Simple (rona 0 - 359, kepekatan 0 - 100). Tidak berpengaruh pada gaya lain. */
    fun setSimpleTint(hue: Int, tone: Int) {
        simpleHue = hue.coerceIn(0, 359)
        simpleTone = tone.coerceIn(0, 100)
    }

    val Accent: Color get() = activeAccent.base

    /** Isian aksen untuk tombol dan penanda terpilih. Polos untuk aksen biasa, gradient halus untuk aksen gradient. */
    val AccentBrush: Brush get() = activeAccent.brush
    val AccentEdge: Color get() = activeAccent.edge
    val AccentSoft: Color get() = activeAccent.soft
    val AccentDeep: Color get() = activeAccent.deep

    // Permukaan: tetap sama di Default dan Liquid Glass, ikut warna pilihan di gaya Simple.
    val Surface: Color get() = if (style == AmberStyle.SIMPLE) simple.surface else Color(0x85000000)
    val SurfaceRaised: Color get() = if (style == AmberStyle.SIMPLE) simple.surfaceRaised else Color(0x14FFFFFF)
    val Border: Color get() = if (style == AmberStyle.SIMPLE) simple.border else Color(0x24FFFFFF)
    val BorderStrong: Color get() = if (style == AmberStyle.SIMPLE) simple.borderStrong else Color(0x40FFFFFF)
    val Text = Color(0xFFF4F3EE)
    val TextDim = Color(0xFFB9B5BC)
    val ModrinthGreen = Color(0xFF2FDC7A)

    /** Kaca gelap untuk tombol/pil di atas foto latar yang terang (Home). */
    val HomeGlass: Color get() = if (style == AmberStyle.SIMPLE) simple.homeGlass else Color(0xD11B1720)
    val HomeGlassEdge: Color get() = if (style == AmberStyle.SIMPLE) simple.homeGlassEdge else Color(0x3DFFFFFF)

    /** Kartu menu akun cepat di Home. */
    val MenuCard: Color get() = if (style == AmberStyle.SIMPLE) simple.menuCard else Color(0xF2121217)

    /** Merah untuk aksi hapus. */
    val Danger = Color(0xFFE5575A)
}

/**
 * Lapisan gelap di atas latar pilihan pengguna untuk semua layar selain Home, supaya kartu
 * dan teks tetap terbaca di foto yang terang. [dim] 0f..1f dari slider "Redupkan latar".
 */
fun amberScrimBrush(dim: Float): Brush {
    val d = dim.coerceIn(0f, 1f)
    val top = d * 0.9f
    val bottom = (0.3f + d * 0.85f).coerceAtMost(0.92f)
    return Brush.verticalGradient(
        listOf(
            Color(0xFF121115).copy(alpha = top),
            Color(0xFF121115).copy(alpha = (top + bottom) / 2f),
            Color(0xFF121115).copy(alpha = bottom)
        )
    )
}
