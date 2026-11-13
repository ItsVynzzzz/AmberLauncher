package com.israadev.nuxlauncher.core.mods

import com.israadev.nuxlauncher.core.utils.SafePaths
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipFile

/** Galat yang pesannya aman ditampilkan ke pengguna: arsip bukan world Java, rusak, terlalu besar, atau tidak aman. */
class WorldImportException(message: String) : IOException(message)

/**
 * Memasang world (folder di dalam `saves`) dari arsip .zip. Dipakai oleh impor file lokal dan unduhan CurseForge.
 *
 * Murni berkas dan zip (tanpa Android), jadi bisa diuji di JVM. Aturannya:
 * - Hanya world Minecraft Java: ada `level.dat` berformat gzip. World Bedrock (.mcworld) ditolak dengan pesan jelas.
 * - `level.dat` boleh di akar arsip atau di dalam folder (sampai 3 tingkat); arsip berisi beberapa world juga bisa.
 * - Diekstrak dulu ke folder sementara satu folder dengan `saves`, baru dipindah dengan satu `rename`. Bila gagal di
 *   tengah jalan (penyimpanan penuh, dibatalkan, arsip rusak), `saves` tidak tersentuh.
 * - World yang sudah ada tidak pernah ditimpa: nama yang bentrok mendapat akhiran " (2)", " (3)", dan seterusnya.
 * - Tahan zip-slip (path "../", absolut) dan arsip "bom" (batas total byte dan jumlah berkas), serta diperiksa dulu
 *   apakah ruang penyimpanan cukup. Isi disalin per blok 64 KB, tidak pernah dimuat utuh ke RAM.
 */
object WorldInstaller {
    const val ITEM_TYPE = "worlds"

    /** Batas total isi yang boleh diekstrak dari satu arsip world. */
    const val MAX_EXTRACT_BYTES = 4L * 1024L * 1024L * 1024L

    /** Batas ukuran arsip world yang disalin atau diunduh. */
    const val MAX_ARCHIVE_BYTES = 2L * 1024L * 1024L * 1024L

    private const val MAX_ENTRIES = 300_000
    private const val MAX_ROOT_DEPTH = 3
    private const val MAX_WORLDS = 20
    private const val FREE_SPACE_MARGIN = 64L * 1024L * 1024L
    private const val STAGING_PREFIX = ".amber-world-"
    private const val STALE_AFTER_MS = 60L * 60L * 1000L
    private const val DEFAULT_NAME = "World"
    private val JUNK_NAMES = setOf(".ds_store", "thumbs.db", "desktop.ini", "session.lock")

    const val MSG_NOT_A_WORLD = "Arsip ini bukan world Minecraft Java (level.dat tidak ditemukan)."
    const val MSG_BEDROCK = "World Bedrock (.mcworld) tidak didukung. Launcher ini hanya memasang world Minecraft Java."
    const val MSG_BAD_LEVEL = "File level.dat di arsip ini rusak, jadi world tidak bisa dipasang."
    const val MSG_TOO_BIG = "Isi world terlalu besar (maksimal 4 GB), pemasangan dibatalkan."
    const val MSG_ARCHIVE_TOO_BIG = "Arsip world terlalu besar (maksimal 2 GB), pemasangan dibatalkan."
    const val MSG_TOO_MANY_FILES = "Arsip berisi terlalu banyak berkas, pemasangan dibatalkan."
    const val MSG_TOO_MANY_WORLDS = "Arsip berisi lebih dari 20 world, pemasangan dibatalkan."
    const val MSG_UNSAFE = "Arsip ditolak karena berisi path yang tidak aman."
    const val MSG_SAME_NAME = "Terlalu banyak world dengan nama yang sama."
    const val MSG_DISK_FULL = "Penyimpanan penuh. Kosongkan ruang lalu coba lagi."

    /** Satu world di dalam arsip. [prefix] = folder world di arsip ("" bila level.dat ada di akar, selain itu berakhir "/"). */
    data class Found(val prefix: String, val name: String)

    /** Pesan "penyimpanan tidak cukup" dengan angka MB yang perlu dikosongkan (dibulatkan ke atas). */
    fun noSpaceMessage(neededBytes: Long): String {
        val mb = (neededBytes + 1024L * 1024L - 1L) / (1024L * 1024L)
        return "Penyimpanan tidak cukup untuk memasang world ini. Kosongkan sekitar $mb MB lalu coba lagi."
    }

    /** Nama folder world yang aman dari teks bebas: tanpa folder, karakter kontrol, dan karakter terlarang; maksimal 64 huruf. */
    fun cleanName(raw: String?): String {
        val s = (raw ?: "").replace('\\', '/').substringAfterLast('/')
            .filter { it >= ' ' && it != '\u007f' && it !in "<>:\"|?*" }
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .trim()
        return if (s.isEmpty()) DEFAULT_NAME else s.take(64).trim()
    }

    /** Nama dasar dari nama berkas arsip: "Skyblock v1.2.zip" menjadi "Skyblock v1.2". */
    fun stemOf(fileName: String?): String = cleanName(File(cleanName(fileName)).nameWithoutExtension)

    /** Folder baru di [parent] yang belum ada: "Nama", lalu "Nama (2)", "Nama (3)", dan seterusnya. */
    fun uniqueDir(parent: File, base: String): File {
        var candidate = File(parent, base)
        var n = 2
        while (candidate.exists()) {
            if (n > 999) throw WorldImportException(MSG_SAME_NAME)
            candidate = File(parent, "$base ($n)")
            n++
        }
        return candidate
    }

    /** True bila [dir] berupa folder world Java yang dikenali game (punya level.dat). */
    fun isWorldDir(dir: File): Boolean = dir.isDirectory && File(dir, "level.dat").isFile

    private fun normalize(name: String): String = name.replace('\\', '/')

    private fun isJunk(rel: String): Boolean {
        if (rel.startsWith("__MACOSX/")) return true
        return rel.trimEnd('/').substringAfterLast('/').lowercase() in JUNK_NAMES
    }

    private fun hasGzipMagic(zip: ZipFile, entryName: String): Boolean {
        val entry = zip.getEntry(entryName) ?: return false
        return zip.getInputStream(entry).use { input ->
            input.read() == 0x1f && input.read() == 0x8b
        }
    }

    /**
     * Mencari semua world Java di dalam [zip]. Melempar [WorldImportException] (pesan siap tampil) bila tidak ada
     * satu pun world yang bisa dipasang.
     */
    fun findWorlds(zip: ZipFile, fallbackName: String): List<Found> {
        val levelEntries = LinkedHashMap<String, String>() // prefix -> nama asli entri level.dat
        val bedrock = HashSet<String>()
        var count = 0
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (++count > MAX_ENTRIES) throw WorldImportException(MSG_TOO_MANY_FILES)
            if (entry.isDirectory) continue
            val name = normalize(entry.name)
            if (name.startsWith("__MACOSX/")) continue
            val slash = name.lastIndexOf('/')
            val base = name.substring(slash + 1)
            val prefix = name.substring(0, slash + 1)
            when {
                base == "level.dat" && prefix.count { it == '/' } <= MAX_ROOT_DEPTH -> levelEntries[prefix] = entry.name
                base == "levelname.txt" -> bedrock.add(prefix)
                name == "db/CURRENT" || name.endsWith("/db/CURRENT") -> bedrock.add(name.removeSuffix("db/CURRENT"))
            }
        }

        val all = levelEntries.keys
        // level.dat di akar berarti seluruh arsip adalah satu world; selain itu buang yang bersarang di world lain
        val roots = if ("" in all) {
            listOf("")
        } else {
            all.filter { p -> all.none { o -> o != p && p.startsWith(o) } }
        }
        val javaRoots = roots.filter { it !in bedrock }
        if (javaRoots.isEmpty()) {
            throw WorldImportException(if (roots.isEmpty() && bedrock.isEmpty()) MSG_NOT_A_WORLD else MSG_BEDROCK)
        }
        val valid = javaRoots.filter { hasGzipMagic(zip, levelEntries.getValue(it)) }
        if (valid.isEmpty()) throw WorldImportException(MSG_BAD_LEVEL)
        if (valid.size > MAX_WORLDS) throw WorldImportException(MSG_TOO_MANY_WORLDS)

        return valid.map { p ->
            val raw = p.trimEnd('/').substringAfterLast('/').ifBlank { fallbackName }
            Found(p, cleanName(raw))
        }
    }

    /** World pemilik entri bernama [name]; prefix terpanjang menang. Null bila entri bukan bagian dari world mana pun. */
    private fun ownerOf(worlds: List<Found>, name: String): Found? =
        worlds.filter { it.prefix.isEmpty() || name.startsWith(it.prefix) }.maxByOrNull { it.prefix.length }

    /**
     * Memasang semua world di [zipFile] ke [savesDir] dan mengembalikan nama folder yang dibuat.
     * [stagingParent] harus satu volume dengan [savesDir] (biasanya folder game) supaya pemindahan akhir cukup satu rename.
     * [fallbackName] dipakai bila world ada di akar arsip dan tidak punya nama folder sendiri.
     */
    fun install(
        zipFile: File,
        savesDir: File,
        stagingParent: File,
        fallbackName: String,
        maxBytes: Long = MAX_EXTRACT_BYTES,
        checkCancelled: () -> Unit = {},
        onProgress: (status: String, current: Int, total: Int) -> Unit = { _, _, _ -> }
    ): List<String> {
        cleanupStale(stagingParent)
        ZipFile(zipFile).use { zip ->
            val worlds = findWorlds(zip, fallbackName)

            // Putaran 1: hitung berkas dan ukuran yang akan diekstrak, lalu pastikan muat sebelum menulis apa pun
            var declared = 0L
            var fileCount = 0
            val scan = zip.entries()
            while (scan.hasMoreElements()) {
                checkCancelled()
                val e = scan.nextElement()
                if (e.isDirectory) continue
                if (ownerOf(worlds, normalize(e.name)) == null) continue
                fileCount++
                if (e.size > 0) declared += e.size
                if (declared > maxBytes) throw WorldImportException(MSG_TOO_BIG)
            }
            val free = stagingParent.usableSpace
            if (free > 0 && free < declared + FREE_SPACE_MARGIN) {
                throw WorldImportException(noSpaceMessage(declared + FREE_SPACE_MARGIN - free))
            }

            // Putaran 2: ekstrak ke folder sementara
            val stagingRoot = File(stagingParent, STAGING_PREFIX + UUID.randomUUID().toString().take(8))
            try {
                if (!stagingRoot.mkdirs()) throw IOException("Gagal membuat folder sementara")
                val stages = worlds.indices.map { File(stagingRoot, "w$it").also { dir -> dir.mkdirs() } }
                var done = 0
                var used = 0L
                val buffer = ByteArray(64 * 1024)
                val extract = zip.entries()
                while (extract.hasMoreElements()) {
                    checkCancelled()
                    val e = extract.nextElement()
                    val name = normalize(e.name)
                    val owner = ownerOf(worlds, name) ?: continue
                    val rel = name.substring(owner.prefix.length)
                    if (rel.isEmpty() || isJunk(rel)) continue
                    val stage = stages[worlds.indexOf(owner)]
                    val dest = SafePaths.resolveInside(stage, rel) ?: throw WorldImportException(MSG_UNSAFE)
                    if (e.isDirectory) {
                        dest.mkdirs()
                        continue
                    }
                    dest.parentFile?.mkdirs()
                    zip.getInputStream(e).use { input ->
                        FileOutputStream(dest).use { output ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                used += read
                                if (used > maxBytes) throw WorldImportException(MSG_TOO_BIG)
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    done++
                    if (done % 40 == 0 || done == fileCount) onProgress("Mengekstrak world...", done, fileCount)
                }
                if (stages.any { !File(it, "level.dat").isFile }) throw WorldImportException(MSG_NOT_A_WORLD)

                // Putaran 3: pindahkan ke saves (cepat, satu rename per world)
                savesDir.mkdirs()
                val installed = ArrayList<String>()
                for ((i, w) in worlds.withIndex()) {
                    val target = uniqueDir(savesDir, w.name)
                    if (!stages[i].renameTo(target)) {
                        stages[i].copyRecursively(target, overwrite = false)
                        stages[i].deleteRecursively()
                    }
                    installed.add(target.name)
                }
                return installed
            } finally {
                stagingRoot.deleteRecursively()
            }
        }
    }

    /** Menyalin [input] ke [dest] per blok. Melempar [WorldImportException] bila lebih besar dari [maxBytes]; berkas yang gagal dihapus. */
    fun copyLimited(
        input: InputStream,
        dest: File,
        maxBytes: Long = MAX_ARCHIVE_BYTES,
        checkCancelled: () -> Unit = {}
    ) {
        try {
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    checkCancelled()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw WorldImportException(MSG_ARCHIVE_TOO_BIG)
                    output.write(buffer, 0, read)
                }
            }
        } catch (e: Throwable) {
            dest.delete()
            throw e
        }
    }

    /** Folder sementara baru (satu volume dengan [parent]) untuk menampung arsip yang diunduh atau disalin. */
    fun newTempDir(parent: File): File {
        cleanupStale(parent)
        val dir = File(parent, STAGING_PREFIX + "dl-" + UUID.randomUUID().toString().take(8))
        if (!dir.mkdirs()) throw IOException("Gagal membuat folder sementara")
        return dir
    }

    /** Hapus sisa folder sementara yang tertinggal lebih dari satu jam (mis. aplikasi ditutup paksa saat mengekstrak). */
    fun cleanupStale(parent: File) {
        val now = System.currentTimeMillis()
        parent.listFiles { f -> f.isDirectory && f.name.startsWith(STAGING_PREFIX) }?.forEach {
            if (now - it.lastModified() > STALE_AFTER_MS) it.deleteRecursively()
        }
    }
}
