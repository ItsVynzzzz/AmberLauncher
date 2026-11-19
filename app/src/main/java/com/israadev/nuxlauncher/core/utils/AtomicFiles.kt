package com.israadev.nuxlauncher.core.utils

import java.io.File
import java.io.FileOutputStream

/**
 * Menulis berkas lewat berkas sementara lalu memindahkannya ke tujuan, jadi berkas lama tidak pernah
 * tertinggal setengah tertulis bila proses dimatikan sistem tepat saat menulis (sering terjadi di HP RAM kecil).
 */
object AtomicFiles {
    /**
     * [sync] = tunggu data benar-benar sampai ke penyimpanan (fsync) sebelum rename. Aman untuk data yang tidak boleh
     * hilang; mahal di penyimpanan HP kelas bawah. Berkas turunan yang bisa dibuat ulang dari berkas lain boleh `false`:
     * rename tetap atomik, jadi paling buruk berkasnya hilang, tidak pernah setengah tertulis.
     */
    fun writeText(target: File, text: String, sync: Boolean = true) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            if (sync) {
                try { out.fd.sync() } catch (_: Exception) {}
            }
        }
        if (!tmp.renameTo(target)) {
            // Sebagian penyimpanan menolak rename ke berkas yang sudah ada: jatuh ke tulis biasa.
            target.writeText(text)
            tmp.delete()
        }
    }
}
