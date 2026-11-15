package com.israadev.nuxlauncher.core.runtime

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Menyiapkan libjnidispatch.so yang versinya cocok dengan JNA milik game.
 *
 * JNA di dalam Minecraft menolak pustaka native yang versi mayornya beda dengan jar-nya. Pustaka bawaan
 * aplikasi (jniLibs) berversi native 7.x, yang hanya cocok untuk JNA 5.14 ke atas. Minecraft 1.20.4 ke
 * bawah memakai JNA 5.12.1 / 5.13.0 (native 6.x), sehingga `com.sun.jna.Native` gagal diinisialisasi dan
 * mod yang memakai JNA (misalnya Yes Steve Model, untuk mendeteksi jenis libc) ikut gagal.
 *
 * Aset `runtimes/jna/jna-arm64.zip` sudah memuat `jna/<versi>/libjnidispatch.so` untuk tiap versi JNA.
 * Di sini yang dicari hanya versi JNA dari nama jar di classpath, lalu berkas yang cocok diekstrak ke
 * folder sendiri supaya bisa dimasukkan ke `jna.boot.library.path` sebelum folder pustaka aplikasi.
 *
 * Tanpa Android, jadi bisa diuji di JVM biasa. Tidak pernah melempar galat: bila apa pun gagal hasilnya
 * null dan peluncuran memakai jalur lama.
 */
object JnaNatives {

    private const val LIB_NAME = "libjnidispatch.so"

    /** Penanda versi aset di folder hasil ekstrak; bila aset diganti, berkas diekstrak ulang. */
    private const val MARKER = ".asset"

    /** JNA 5.14.0 ke atas memakai native 7.x, sama dengan pustaka bawaan aplikasi, jadi tidak perlu diganti. */
    private const val FIRST_MINOR_WITH_NATIVE_7 = 14

    private val JAR_NAME = Regex("""(?:^|[/\\:=;\s])jna-(\d+(?:\.\d+){1,3})\.jar(?![\w.\-])""")

    /** Versi JNA dari nama berkas jna-X.Y.Z.jar di [text] (classpath atau argumen); jna-platform tidak dihitung. */
    fun versionFrom(text: String): String? = JAR_NAME.find(text)?.groupValues?.get(1)

    /** True bila JNA [version] butuh native yang berbeda dari pustaka bawaan aplikasi (lebih lama dari 5.14.0). */
    fun needsMatchingNative(version: String): Boolean {
        val parts = version.split('.').map { it.toIntOrNull() ?: return false }
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        return major < 5 || (major == 5 && minor < FIRST_MINOR_WITH_NATIVE_7)
    }

    /** Mengekstrak `jna/<version>/libjnidispatch.so` dari [zip] ke [outFile]. False bila tidak ada di arsip. */
    fun extract(zip: InputStream, version: String, outFile: File): Boolean {
        val wanted = "jna/$version/$LIB_NAME"
        ZipInputStream(zip).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: return false
                if (entry.name != wanted) continue
                val tmp = File(outFile.parentFile, outFile.name + ".tmp")
                tmp.outputStream().use { zin.copyTo(it) }
                if (tmp.length() == 0L) {
                    tmp.delete()
                    return false
                }
                if (outFile.exists()) outFile.delete()
                return tmp.renameTo(outFile)
            }
        }
        return false
    }

    /**
     * Mengembalikan folder (di bawah [root]) yang berisi libjnidispatch.so yang cocok untuk JNA game, atau
     * null bila tidak perlu atau tidak bisa disiapkan. Hanya proses 64-bit ([is64Bit]): aset hanya arm64.
     * Sekali diekstrak, panggilan berikutnya hanya memeriksa dua berkas kecil.
     */
    fun prepare(
        root: File,
        classpath: String,
        extraArgs: List<String>,
        is64Bit: Boolean,
        assetVersion: () -> String,
        openZip: () -> InputStream
    ): File? {
        try {
            if (!is64Bit) return null
            val version = versionFrom(classpath) ?: versionFrom(extraArgs.joinToString(" ")) ?: return null
            if (!needsMatchingNative(version)) return null

            val dir = File(root, version)
            val lib = File(dir, LIB_NAME)
            val marker = File(dir, MARKER)
            val stamp = assetVersion()
            if (lib.isFile && lib.length() > 0L && marker.isFile && marker.readText().trim() == stamp) return dir

            dir.mkdirs()
            marker.delete()
            val ok = openZip().use { extract(it, version, lib) }
            if (!ok) return null
            marker.writeText(stamp)
            return dir
        } catch (e: Exception) {
            return null
        }
    }
}
