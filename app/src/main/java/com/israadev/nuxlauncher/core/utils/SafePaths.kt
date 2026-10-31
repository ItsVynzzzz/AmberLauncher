package com.israadev.nuxlauncher.core.utils

import java.io.File
import java.net.URI

/**
 * Pelindung path dan alamat unduhan. Nama berkas, path relatif, dan alamat yang datang dari luar
 * (aplikasi lain, isi modpack, respons server) tidak boleh dipakai mentah-mentah, supaya tidak bisa
 * menulis atau menghapus berkas di luar folder instance lewat "../" atau mengunduh dari sumber asing.
 */
object SafePaths {

    /** Nama berkas aman: tanpa folder, tanpa karakter kontrol atau terlarang, dan tidak kosong. */
    fun fileName(raw: String?): String {
        val base = (raw ?: "").replace('\\', '/').substringAfterLast('/')
        val cleaned = base
            .filter { it >= ' ' && it != '\u007f' && it !in "<>:\"|?*" }
            .trim()
            .trim('.')
            .trim()
        return if (cleaned.isEmpty()) "addon_file" else cleaned.takeLast(150)
    }

    /**
     * True bila [rel] aman dipakai sebagai path relatif: tidak kosong, bukan path absolut,
     * tanpa karakter NUL dan tanpa bagian "..".
     */
    fun isSafeRelative(rel: String?): Boolean {
        if (rel.isNullOrBlank() || rel.indexOf('\u0000') >= 0) return false
        val normalized = rel.replace('\\', '/')
        if (normalized.startsWith("/")) return false
        return normalized.split('/').none { it == ".." }
    }

    /**
     * Mengubah [rel] (path relatif dari luar) menjadi File di dalam [base]. Mengembalikan null bila
     * path itu mencoba keluar dari [base] (path absolut, "..", symlink) atau menunjuk ke [base] sendiri.
     */
    fun resolveInside(base: File, rel: String?): File? {
        if (!isSafeRelative(rel)) return null
        return try {
            val baseCanon = base.canonicalFile
            val target = File(baseCanon, rel!!.replace('\\', '/')).canonicalFile
            if (target.path.startsWith(baseCanon.path + File.separator)) target else null
        } catch (_: Exception) {
            null
        }
    }

    /** Host yang dibolehkan sebagai sumber berkas di dalam modpack (Modrinth dan CDN resmi CurseForge). */
    private val MODPACK_HOSTS = setOf(
        "cdn.modrinth.com",
        "github.com",
        "raw.githubusercontent.com",
        "gitlab.com",
        "edge.forgecdn.net",
        "mediafilez.forgecdn.net",
        "media.forgecdn.net"
    )

    /** True bila [url] memakai https dan menuju salah satu host modpack yang dibolehkan. */
    fun isAllowedModpackDownloadUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val uri = URI(url.trim())
            val host = uri.host?.lowercase() ?: return false
            uri.scheme.equals("https", ignoreCase = true) && host in MODPACK_HOSTS
        } catch (_: Exception) {
            false
        }
    }
}
