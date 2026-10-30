package com.israadev.nuxlauncher.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Satu pilihan warna aksen. [base] isian tombol, [edge] tepi, [soft] teks/ikon terang, [deep] bayangan keras. */
class AmberAccent(
    val id: String,
    val label: String,
    val base: Color,
    val edge: Color,
    val soft: Color,
    val deep: Color
)

/** Pilihan aksen yang tampil di Pengaturan > Tampilan. Navy adalah bawaan. */
val AMBER_ACCENTS = listOf(
    AmberAccent("navy", "Navy", Color(0xFF1F3380), Color(0xFF8F99C0), Color(0xFFA9BDFF), Color(0xFF0F1A4D)),
    AmberAccent("green", "Hijau", Color(0xFF2FB86C), Color(0xFF9ADDB8), Color(0xFF8DF0B8), Color(0xFF14603A)),
    AmberAccent("purple", "Ungu", Color(0xFF6B4BD1), Color(0xFFB9A8EC), Color(0xFFC9B8FF), Color(0xFF39257D)),
    AmberAccent("teal", "Teal", Color(0xFF14A3A8), Color(0xFF8FD6D9), Color(0xFF8FE6EA), Color(0xFF0A5659))
)

/** Gaya tampilan: DEFAULT = tema AmberLauncher sendiri, LIQUID = kaca buram (blur) ala liquid glass. */
enum class AmberStyle(val id: String, val label: String) {
    DEFAULT("default", "Default"),
    LIQUID("liquid", "Liquid Glass");

    companion object {
        fun fromId(id: String?): AmberStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Warna tampilan baru: kaca gelap dengan aksen. Dipakai Home, Mod, Akun, Pengaturan.
 * Aksen dan gaya bisa diganti pengguna; karena disimpan sebagai state Compose, semua layar
 * langsung ikut berubah tanpa memulai ulang.
 */
object AmberUi {
    private var accentPreset by mutableStateOf(AMBER_ACCENTS.first())

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

    val Accent: Color get() = accentPreset.base
    val AccentEdge: Color get() = accentPreset.edge
    val AccentSoft: Color get() = accentPreset.soft
    val AccentDeep: Color get() = accentPreset.deep

    val Surface = Color(0x85000000)
    val SurfaceRaised = Color(0x14FFFFFF)
    val Border = Color(0x24FFFFFF)
    val BorderStrong = Color(0x40FFFFFF)
    val Text = Color(0xFFF4F3EE)
    val TextDim = Color(0xFFB9B5BC)
    val ModrinthGreen = Color(0xFF2FDC7A)

    /** Kaca gelap untuk tombol/pil di atas foto latar yang terang (Home). */
    val HomeGlass = Color(0xD11B1720)
    val HomeGlassEdge = Color(0x3DFFFFFF)

    /** Kartu menu akun cepat di Home. */
    val MenuCard = Color(0xF2121217)

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
