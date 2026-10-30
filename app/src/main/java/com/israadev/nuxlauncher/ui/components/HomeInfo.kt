package com.israadev.nuxlauncher.ui.components

import com.israadev.nuxlauncher.ui.i18n.Tr
import java.util.Locale

/**
 * Hitungan kecil untuk layar Home (tanpa Android/Compose): teks RAM, tingkat RAM,
 * dan nama renderer singkat.
 */
object HomeInfo {
    const val RAM_GOOD_MB = 1400
    const val RAM_OK_MB = 800

    /** Bulatkan ke bawah per 100 MB supaya angka di layar tidak berkedip tiap kali RAM bergeser sedikit. */
    fun ramBucket(freeMb: Int): Int = (freeMb.coerceAtLeast(0) / 100) * 100

    /** 2 = lega, 1 = sedang, 0 = sempit. */
    fun ramLevel(freeMb: Int): Int = when {
        freeMb >= RAM_GOOD_MB -> 2
        freeMb >= RAM_OK_MB -> 1
        else -> 0
    }

    /** Contoh: "RAM bebas 1,4 GB" (koma desimal; Inggris memakai titik; 1 GB = 1024 MB). */
    fun ramText(freeMb: Int): String {
        val gb = freeMb.coerceAtLeast(0) / 1024f
        val locale = if (Tr.effective == Tr.EN) Locale.US else Locale.forLanguageTag("id-ID")
        val value = String.format(locale, "%.1f", gb)
        return "RAM bebas $value GB"
    }

    /** Nama renderer singkat untuk Home: "Otomatis" untuk Auto, selain itu kata pertama nama tampilnya. */
    fun rendererLabel(id: String, displayName: String): String {
        if (id.equals("auto", ignoreCase = true)) return "Otomatis"
        val first = displayName.trim().substringBefore(' ')
        return first.ifBlank { displayName.trim() }.ifBlank { "Otomatis" }
    }
}
