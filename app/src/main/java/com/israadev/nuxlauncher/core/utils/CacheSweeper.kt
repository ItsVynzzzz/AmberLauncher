package com.israadev.nuxlauncher.core.utils

import com.israadev.nuxlauncher.core.storage.StorageMigration
import java.io.File

/**
 * Membuang sisa berkas sementara di cache aplikasi yang tidak akan dipakai lagi, supaya data aplikasi
 * tidak menggembung: salinan modpack yang dipilih tapi tidak jadi dipasang, berkas tahap unduhan mod
 * mesin Rust yang terputus, salinan log untuk dibagikan, dan installer Forge lama. Hanya menyentuh berkas
 * yang sudah lama tidak diubah, jadi aman dijalankan di thread latar saat aplikasi dibuka.
 * Tanpa ketergantungan Android, jadi bisa diuji di JVM.
 */
object CacheSweeper {
    private const val HOUR_MS = 60L * 60 * 1000

    /** Folder cache yang isinya hanya dipakai sebentar; lewat [SHORT_LIVED_HOURS] jam berarti sisa. */
    private val SHORT_LIVED = listOf("modpack-import", "horizon-dl", "shared_logs")
    private const val SHORT_LIVED_HOURS = 24L

    /** Installer Forge disimpan agar pemasangan yang gagal bisa diulang tanpa mengunduh lagi. */
    private const val FORGE_DIR = "forge"
    private const val FORGE_KEEP_DAYS = 14L

    fun sweep(cacheDir: File, now: Long = System.currentTimeMillis()) {
        for (name in SHORT_LIVED) sweepDir(File(cacheDir, name), now - SHORT_LIVED_HOURS * HOUR_MS)
        sweepDir(File(cacheDir, FORGE_DIR), now - FORGE_KEEP_DAYS * 24 * HOUR_MS)
    }

    /** Menghapus isi [dir] yang terakhir diubah sebelum [cutoff]; [dir] sendiri dihapus bila jadi kosong. */
    private fun sweepDir(dir: File, cutoff: Long) {
        if (!dir.isDirectory) return
        try {
            for (child in dir.listFiles() ?: return) {
                if (child.lastModified() < cutoff) StorageMigration.deleteTree(child)
            }
            if (dir.list().isNullOrEmpty()) dir.delete()
        } catch (_: Exception) {
        }
    }
}
