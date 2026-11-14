package com.israadev.nuxlauncher.core.modpack

import com.israadev.nuxlauncher.core.utils.SafePaths
import java.io.File

/**
 * Tempat menunggu satu berkas modpack milik sebuah instance yang baru dibuat dari modpack.
 *
 * Instance dari modpack dipasang dua tahap: game dan loader dulu, baru isi modpack. Selama berkas di sini
 * belum dihapus, instance dianggap belum selesai dipasang, jadi tombol UNDUH di Home melanjutkan dari
 * tempat terakhir (unduhan terputus, dibatalkan, atau aplikasi ditutup) dan PLAY tidak membuka game yang
 * modnya belum lengkap. Berkas dihapus hanya setelah seluruh isi modpack terpasang.
 *
 * Folder penampung ada di folder instance, di luar folder game, jadi tidak terlihat oleh Minecraft.
 * Tidak memakai Android sama sekali, jadi bisa diuji di JVM biasa.
 */
object ModpackStaging {

    /** Nama folder penampung di dalam folder instance. */
    const val DIR_NAME = "pending-modpack"

    private const val PART_SUFFIX = ".part"

    private fun dir(instanceDir: File): File = File(instanceDir, DIR_NAME)

    /**
     * Nama berkas modpack yang aman dan berakhiran .mrpack atau .zip (daftar Mod hanya mengenali dua
     * akhiran itu). Nama dari aplikasi lain dibersihkan dari bagian folder; akhiran yang hilang atau
     * salah diganti menurut isi modpack-nya.
     */
    fun archiveName(displayName: String?, source: ModpackReader.Source): String {
        val base = SafePaths.fileName(displayName)
        val lower = base.lowercase()
        return if (lower.endsWith(".mrpack") || lower.endsWith(".zip")) {
            base
        } else {
            base + if (source == ModpackReader.Source.MODRINTH) ".mrpack" else ".zip"
        }
    }

    /**
     * Berkas modpack yang menunggu dipasang di [instanceDir], atau null bila tidak ada. Berkas sementara
     * (.part) dari penyalinan yang terputus tidak dihitung.
     */
    fun pending(instanceDir: File): File? {
        val files = dir(instanceDir).listFiles { f -> f.isFile && !f.name.endsWith(PART_SUFFIX) }
        return files?.minByOrNull { it.name }
    }

    fun hasPending(instanceDir: File): Boolean = pending(instanceDir) != null

    /**
     * Menaruh salinan [source] di penampung milik [instanceDir] dengan nama [fileName], menggantikan isi
     * penampung sebelumnya. [source] dihapus setelah berhasil disalin. Disalin ke berkas .part dulu lalu
     * diganti namanya, jadi berkas yang terlihat di penampung selalu utuh walau aplikasi mati di tengah jalan.
     */
    fun stage(instanceDir: File, source: File, fileName: String): File {
        val target = dir(instanceDir)
        clear(instanceDir)
        if (!target.mkdirs() && !target.isDirectory) {
            throw java.io.IOException("Gagal menyiapkan folder modpack")
        }
        val safeName = SafePaths.fileName(fileName)
        val dest = File(target, safeName)
        val part = File(target, safeName + PART_SUFFIX)
        try {
            source.copyTo(part, overwrite = true)
            if (!part.renameTo(dest)) throw java.io.IOException("Gagal menyimpan berkas modpack")
        } catch (e: Exception) {
            part.delete()
            dest.delete()
            throw e
        }
        source.delete()
        return dest
    }

    /** Menghapus penampung beserta isinya. Aman dipanggil bila penampung tidak ada. */
    fun clear(instanceDir: File) {
        val target = dir(instanceDir)
        if (target.exists()) target.deleteRecursively()
    }
}
