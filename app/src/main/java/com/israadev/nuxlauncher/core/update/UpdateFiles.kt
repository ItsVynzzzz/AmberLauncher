package com.israadev.nuxlauncher.core.update

import java.io.File

/**
 * Berkas unduhan APK pembaruan di cache: mana yang masih berguna dan mana yang harus dibuang, supaya
 * data aplikasi tidak menggembung oleh APK lama. Tanpa ketergantungan Android, jadi bisa diuji di JVM.
 */
object UpdateFiles {
    private const val STALE_PART_MS = 14L * 24 * 3600 * 1000

    // Nama lama (AmberLauncher-0.1.0-beta.7.apk, ...-32bit.apk) dan nama rilis sekarang
    // (AmberLauncher-0.1.0-beta.7-64bit-arm64-v8a-RECOMMENDED.apk, ...-32bit-armeabi-v7a-OLD-PHONES-ONLY.apk).
    private val NAME = Regex("""^AmberLauncher-(.+?)(?:-(?:32|64)bit[A-Za-z0-9_-]*)?\.apk(\.part)?$""")

    /**
     * Membuang APK versi yang sudah terpasang atau lebih lama, berkas bernama aneh, dan unduhan setengah
     * jadi yang lebih dari 14 hari. APK versi lebih baru (yang menunggu dipasang) tidak disentuh.
     */
    fun cleanup(dir: File, installed: AppVersion, now: Long = System.currentTimeMillis()) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (!f.isFile) continue
            val m = NAME.matchEntire(f.name)
            val version = m?.let { AppVersion.parse(it.groupValues[1]) }
            val stalePart = f.name.endsWith(".part") && now - f.lastModified() > STALE_PART_MS
            if (version == null || version <= installed || stalePart) {
                try {
                    f.delete()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Membuang semua isi [dir] selain [keep]: APK versi lain yang sudah tidak dipakai (mis. rilis yang
     * dilewati dan sudah ada yang lebih baru) dan sisa unduhan setengah jadi. Dipanggil setelah satu
     * unduhan utuh lolos pemeriksaan, jadi folder ini tidak pernah menampung lebih dari satu APK.
     */
    fun cleanupExcept(dir: File, keep: File) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.name == keep.name) continue
            try {
                f.deleteRecursively()
            } catch (_: Exception) {
            }
        }
    }
}
