package com.israadev.nuxlauncher.core.settings

import com.israadev.nuxlauncher.core.models.LauncherSettings

/**
 * Mode Hemat: preset sekali ketuk untuk HP yang cepat panas atau baterainya boros.
 *
 * Yang diubah hanya tiga setelan yang sudah ada, jadi game tidak butuh kode baru untuk
 * menjalankannya:
 *  - mode performa stabil sistem (sustained performance) dinyalakan,
 *  - V-Sync Zink dinyalakan (berlaku bila renderer Zink yang dipakai),
 *  - skala resolusi dibatasi paling besar [MAX_RESOLUTION] persen.
 *
 * Nilai sebelumnya disimpan di settings.json supaya mematikan Mode Hemat mengembalikan keadaan
 * semula, bukan angka bawaan.
 */
object PowerSaver {
    const val MAX_RESOLUTION = 80
    private const val MIN_RESOLUTION = 40
    private const val MAX_STORED_RESOLUTION = 125

    /** Skala resolusi yang dipakai selama Mode Hemat menyala. */
    fun cappedResolution(current: Int): Int {
        return minOf(current, MAX_RESOLUTION).coerceAtLeast(MIN_RESOLUTION)
    }

    fun enable(s: LauncherSettings): LauncherSettings {
        if (s.powerSaverMode) return s
        return s.copy(
            powerSaverMode = true,
            powerSaverPrevResolution = s.resolutionRatio.coerceIn(MIN_RESOLUTION, MAX_STORED_RESOLUTION),
            powerSaverPrevSustained = s.sustainedPerformanceMode,
            powerSaverPrevVsync = s.vsyncInZink,
            sustainedPerformanceMode = true,
            vsyncInZink = true,
            resolutionRatio = cappedResolution(s.resolutionRatio)
        )
    }

    fun disable(s: LauncherSettings): LauncherSettings {
        if (!s.powerSaverMode) return s
        // Resolusi dikembalikan hanya bila pengguna tidak mengubahnya sendiri selama Mode Hemat menyala.
        val untouched = s.resolutionRatio == cappedResolution(s.powerSaverPrevResolution)
        return s.copy(
            powerSaverMode = false,
            sustainedPerformanceMode = s.powerSaverPrevSustained,
            vsyncInZink = s.powerSaverPrevVsync,
            resolutionRatio = if (untouched) s.powerSaverPrevResolution else s.resolutionRatio
        )
    }

    fun set(s: LauncherSettings, on: Boolean): LauncherSettings = if (on) enable(s) else disable(s)
}
