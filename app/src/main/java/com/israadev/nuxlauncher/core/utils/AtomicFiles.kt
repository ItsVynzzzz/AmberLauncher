package com.israadev.nuxlauncher.core.utils

import java.io.File
import java.io.FileOutputStream

/**
 * Menulis berkas lewat berkas sementara lalu memindahkannya ke tujuan, jadi berkas lama tidak pernah
 * tertinggal setengah tertulis bila proses dimatikan sistem tepat saat menulis (sering terjadi di HP RAM kecil).
 */
object AtomicFiles {
    fun writeText(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            try { out.fd.sync() } catch (_: Exception) {}
        }
        if (!tmp.renameTo(target)) {
            // Sebagian penyimpanan menolak rename ke berkas yang sudah ada: jatuh ke tulis biasa.
            target.writeText(text)
            tmp.delete()
        }
    }
}
