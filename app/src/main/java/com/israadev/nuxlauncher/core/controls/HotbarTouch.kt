package com.israadev.nuxlauncher.core.controls

import java.io.File

/**
 * Hotbar sentuh gaya Bedrock: mengetuk langsung hotbar milik game memilih slotnya.
 *
 * Tidak ada yang digambar ulang. Posisi slot dihitung dari ukuran jendela game dan GUI scale
 * Minecraft (rumus yang sama dengan Minecraft), jadi tidak ada beban tambahan saat bermain; hitungan
 * hanya dilakukan sekali tiap jari menyentuh layar. Murni hitungan (tanpa Android) supaya mudah diuji.
 */
object HotbarGeometry {
    const val SLOTS = 9

    /** Lebar dan tinggi gambar hotbar Minecraft dalam piksel GUI. */
    private const val BAR_W = 182
    private const val BAR_H = 22

    /** Keleluasaan area ketuk di atas hotbar (piksel GUI) supaya jempol tidak harus tepat. */
    private const val TOP_SLACK = 4

    /** Ukuran GUI minimum (piksel GUI) yang dijaga Minecraft saat memilih skala Otomatis. */
    private const val MIN_GUI_W = 320
    private const val MIN_GUI_H = 240

    /**
     * GUI scale yang dipakai Minecraft untuk jendela [winW] x [winH]. [option] = nilai guiScale di
     * options.txt (0 = Otomatis, yaitu skala terbesar yang masih muat 320x240 piksel GUI).
     */
    fun guiScale(winW: Int, winH: Int, option: Int): Int {
        val limit = if (option <= 0) Int.MAX_VALUE else option
        var i = 1
        while (i != limit && i < winW && i < winH &&
            winW / (i + 1) >= MIN_GUI_W && winH / (i + 1) >= MIN_GUI_H
        ) i++
        return i
    }

    /**
     * Nomor slot 0..8 di titik ([x], [y]) (piksel layar), atau -1 bila di luar hotbar.
     * [screenW] x [screenH] ukuran area sentuh, [winW] x [winH] ukuran jendela game, keduanya bisa
     * berbeda karena pengaturan resolusi.
     */
    fun slotAt(
        x: Float, y: Float,
        screenW: Float, screenH: Float,
        winW: Int, winH: Int,
        guiScaleOption: Int
    ): Int {
        if (screenW <= 0f || screenH <= 0f || winW <= 0 || winH <= 0) return -1
        val gx = x * (winW / screenW)
        val gy = y * (winH / screenH)
        val s = guiScale(winW, winH, guiScaleOption)

        val scaledW = (winW + s - 1) / s
        val scaledH = (winH + s - 1) / s
        val left = (scaledW / 2 - BAR_W / 2) * s
        val width = BAR_W * s
        val top = (scaledH - BAR_H - TOP_SLACK) * s

        if (gx < left || gx >= left + width) return -1
        if (gy < top) return -1
        val slot = ((gx - left) * SLOTS / width).toInt()
        return slot.coerceIn(0, SLOTS - 1)
    }
}

/** Membaca nilai guiScale dari options.txt; hanya membaca ulang bila berkasnya berubah. */
class GuiScaleReader(private val file: File) {
    private var lastCheckMs = Long.MIN_VALUE
    private var lastModified = -1L
    private var value = 0

    /** GUI scale terbaru (0 = Otomatis). Aman dipanggil sering; memeriksa berkas paling cepat tiap 2 detik. */
    fun get(nowMs: Long): Int {
        if (lastCheckMs != Long.MIN_VALUE && nowMs - lastCheckMs < RECHECK_MS) return value
        lastCheckMs = nowMs
        try {
            val m = file.lastModified()
            if (m != lastModified) {
                lastModified = m
                value = if (m == 0L) 0 else file.useLines { parseGuiScale(it) }
            }
        } catch (_: Throwable) {
        }
        return value
    }

    companion object {
        private const val RECHECK_MS = 2000L

        /** Nilai "guiScale:N" pada baris-baris options.txt; 0 bila tidak ada atau tidak valid. */
        fun parseGuiScale(lines: Sequence<String>): Int {
            for (line in lines) {
                val t = line.trim()
                if (t.startsWith("guiScale:")) {
                    return t.substring("guiScale:".length).trim().toIntOrNull()?.coerceIn(0, 16) ?: 0
                }
            }
            return 0
        }
    }
}
