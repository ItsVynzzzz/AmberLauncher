package com.israadev.nuxlauncher.core.horizon

import androidx.compose.ui.graphics.toArgb
import com.israadev.nuxlauncher.ui.theme.AMBER_ACCENTS
import com.israadev.nuxlauncher.ui.theme.AmberAccent
import org.json.JSONObject

/**
 * Warna UI mesin Rust (menu garis tiga, editor kontrol, layar mesin) mengikuti Amber. Mesin membaca satu
 * kunci `theme` di android-launcher.json dan memakainya di semua layarnya, jadi cukup ditulis di sini.
 *
 * Dasarnya sama dengan AmberUi: latar gelap 121115 dan kaca gelap 1B1720. Mesin memakai satu warna aksen
 * untuk isian tombol sekaligus teks dan ikon di atas latar gelap, sedangkan Amber memisahkan aksen (isian)
 * dan aksen terang (teks). Karena itu yang dikirim adalah campuran keduanya, supaya biru Amber tetap
 * terlihat sama tetapi teks di atasnya terbaca.
 */
internal object RustTheme {
    private const val BACKGROUND = "#121115"
    private const val SURFACE = "#1B1720"
    private const val SOFT_SHARE = 0.45f

    /** Nilai kunci `theme`. Semua enum memakai ejaan mesin (huruf kecil); mesin juga membersihkan hex yang salah. */
    fun json(accentId: String?): JSONObject {
        val accent = AMBER_ACCENTS.firstOrNull { it.id == accentId } ?: AMBER_ACCENTS.first()
        return JSONObject()
            .put("preset", "custom")
            .put("appearance", "dark")
            .put("background_mode", "solid")
            .put("custom_bg_hex", BACKGROUND)
            .put("custom_surface_hex", SURFACE)
            .put("custom_accent_hex", accentHex(accent))
    }

    internal fun accentHex(accent: AmberAccent): String {
        val base = accent.base.toArgb()
        val soft = accent.soft.toArgb()
        fun channel(shift: Int): Int {
            val a = (base shr shift) and 0xFF
            val b = (soft shr shift) and 0xFF
            return (a + (b - a) * SOFT_SHARE).toInt().coerceIn(0, 255)
        }
        return "#%02X%02X%02X".format(channel(16), channel(8), channel(0))
    }
}
