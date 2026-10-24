package com.israadev.nuxlauncher.core.forge

import java.io.File
import java.util.jar.Manifest
import java.util.zip.ZipFile

/** Satu cara menjalankan installer: kelas utama dan argumennya (diberikan setelah `-cp <installer.jar>`). */
internal class InstallerCommand(val mainClass: String, val args: List<String>)

/**
 * Perintah untuk menjalankan installer resmi tanpa antarmuka grafis.
 *
 * Forge: perintahnya sudah diketahui pasti (`SimpleInstaller --installClient <folder> --offline`).
 *
 * NeoForge: installer-nya turunan installer Forge, tetapi nama kelas utama dan penulisan opsinya
 * (`--installClient` atau `--install-client`) dapat berbeda antarversi dan tidak bisa dipastikan
 * dari luar. Karena itu isi jar installer dipindai (tanpa dijalankan) untuk mencari opsi yang benar-benar
 * ada, dan beberapa kandidat disiapkan. Kandidat berikutnya hanya dicoba bila installer menolak opsinya,
 * jadi pada kondisi normal installer berjalan sekali saja.
 */
internal object ForgeInstallerCli {

    private const val FORGE_MAIN = "net.minecraftforge.installer.SimpleInstaller"
    private const val NEOFORGE_MAIN_FALLBACK = "net.neoforged.installer.SimpleInstaller"

    private const val OPT_CLIENT_CAMEL = "--installClient"
    private const val OPT_CLIENT_KEBAB = "--install-client"
    private const val OPT_OFFLINE = "--offline"

    /** Kelas yang lebih besar dari ini tidak dipindai (kelas installer berukuran kecil). */
    private const val MAX_CLASS_BYTES = 4L * 1024 * 1024

    /** Hasil pemindaian jar installer. */
    class Hints(
        val mainClass: String?,
        val hasCamelClient: Boolean,
        val hasKebabClient: Boolean,
        val hasOffline: Boolean
    )

    /** Daftar perintah yang dicoba berurutan untuk memasang [flavor] ke [targetDir]. Tidak pernah kosong. */
    fun commands(flavor: ForgeFlavor, hints: Hints?, targetDir: String): List<InstallerCommand> {
        if (flavor == ForgeFlavor.FORGE) {
            return listOf(InstallerCommand(FORGE_MAIN, listOf(OPT_CLIENT_CAMEL, targetDir, OPT_OFFLINE)))
        }
        val main = hints?.mainClass ?: NEOFORGE_MAIN_FALLBACK

        // Opsi yang terdeteksi lebih dulu; dua bentuk lainnya hanya cadangan
        val clientOptions = LinkedHashSet<String>()
        if (hints?.hasCamelClient == true) clientOptions.add(OPT_CLIENT_CAMEL)
        if (hints?.hasKebabClient == true) clientOptions.add(OPT_CLIENT_KEBAB)
        clientOptions.add(OPT_CLIENT_CAMEL)
        clientOptions.add(OPT_CLIENT_KEBAB)

        val result = ArrayList<InstallerCommand>()
        // --offline hanya bila terdeteksi (atau jar tak terbaca); varian tanpa --offline menjadi cadangan terakhir
        if (hints == null || hints.hasOffline) {
            clientOptions.forEach { result.add(InstallerCommand(main, listOf(it, targetDir, OPT_OFFLINE))) }
        }
        clientOptions.forEach { result.add(InstallerCommand(main, listOf(it, targetDir))) }
        return result
    }

    /** True bila installer menolak salah satu opsi yang diberikan (layak dicoba dengan kandidat lain). */
    fun isUnknownOption(log: String): Boolean {
        val l = log.lowercase()
        return l.contains("not a recognized option") || l.contains("unrecognized option") ||
            l.contains("unknown option") || l.contains("unmatched argument")
    }

    /** True bila log menandakan Java terlalu tua untuk class file installer (layak dicoba dengan Java lain). */
    fun isWrongJava(log: String): Boolean =
        log.contains("UnsupportedClassVersionError") || log.contains("class file version")

    /** Memindai [installerJar]; null bila jar tidak terbaca. */
    fun readHints(installerJar: File): Hints? = try {
        ZipFile(installerJar).use { zip ->
            val mainClass = zip.getEntry("META-INF/MANIFEST.MF")?.let { entry ->
                zip.getInputStream(entry).use { Manifest(it).mainAttributes.getValue("Main-Class") }
            }?.trim()?.takeIf { it.isNotEmpty() }

            var camel = false
            var kebab = false
            var offline = false
            val entries = zip.entries()
            while (entries.hasMoreElements() && !(camel && kebab && offline)) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !entry.name.endsWith(".class")) continue
                if (entry.size > MAX_CLASS_BYTES) continue
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                if (!camel && contains(bytes, "installClient")) camel = true
                if (!kebab && contains(bytes, "install-client")) kebab = true
                if (!offline && contains(bytes, "offline")) offline = true
            }
            Hints(mainClass, camel, kebab, offline)
        }
    } catch (_: Exception) {
        null
    }

    /** Pencarian byte sederhana: teks ASCII di constant pool kelas tersimpan apa adanya. */
    internal fun contains(haystack: ByteArray, needle: String): Boolean {
        val n = needle.toByteArray(Charsets.US_ASCII)
        if (n.isEmpty() || haystack.size < n.size) return false
        val first = n[0]
        val last = haystack.size - n.size
        var i = 0
        while (i <= last) {
            if (haystack[i] == first) {
                var j = 1
                while (j < n.size && haystack[i + j] == n[j]) j++
                if (j == n.size) return true
            }
            i++
        }
        return false
    }
}
