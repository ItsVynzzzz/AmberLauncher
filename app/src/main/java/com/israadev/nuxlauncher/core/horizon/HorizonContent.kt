package com.israadev.nuxlauncher.core.horizon

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.israadev.nuxlauncher.core.models.InstalledModItem
import com.israadev.nuxlauncher.core.models.ModrinthVersion
import com.israadev.nuxlauncher.core.models.ModrinthVersionFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Shader dan resource pack untuk mesin Rust (Horizon), supaya tempat unduh Amber dan mesin memakai folder dan
 * berkas pengaturan yang SAMA. Tidak ada yang disimpan di tempat lain: apa yang dipasang atau dipilih di Amber
 * langsung terlihat di mesin, dan sebaliknya.
 *
 * Semua fungsi di sini tidak butuh Android (hanya berkas dan JSON), jadi mudah diuji. [home] selalu folder data
 * mesin (HorizonEngine.engineHome); folder game yang sebenarnya dicari lewat [gameHome].
 *
 * Aturan mengikuti mesin (horizon-android: launcher/src/packs.rs dan crates/horizon-client/src/content_files.rs):
 * - Shader  : folder `shaderpacks`, satu pilihan aktif di `horizon-graphics.txt` (baris `pickedShaderPack:<berkas>`).
 * - Resource: folder `resourcepacks`, daftar aktif di `options.txt` (baris `resourcePacks:["vanilla","file/<berkas>"]`,
 *             yang terakhir paling atas).
 * - Baris lain di kedua berkas pengaturan itu ditulis kembali apa adanya.
 * - Berkas pengaturan dibaca mesin saat game dibuka, jadi perubahan berlaku pada permainan berikutnya.
 */
object HorizonContent {

    const val TYPE_SHADERS = "shaderpacks"
    const val TYPE_RESOURCES = "resourcepacks"

    /** Mod (.hmod dan modul bawaan mesin): diurus oleh [HorizonMods]. */
    const val TYPE_MODS = "mods"

    /** Batas ukuran berkas dari mesin (content_files::MAX_INPUT). */
    const val MAX_FILE_BYTES = 256L * 1024L * 1024L
    private const val MAX_EXPANDED_BYTES = 512L * 1024L * 1024L
    private const val MAX_ENTRIES = 32768
    private const val MAX_ENTRY_PATH_BYTES = 1024
    private const val MAX_NAME_BYTES = 180
    private const val MAX_META_BYTES = 1L shl 20

    private const val OPTIONS_FILE = "options.txt"
    private const val GRAPHICS_FILE = "horizon-graphics.txt"
    private const val LAUNCHER_CONFIG = "android-launcher.json"
    private const val KEY_SHADER = "pickedShaderPack"
    private const val KEY_RESOURCES = "resourcePacks"
    private const val KEY_INCOMPATIBLE = "incompatibleResourcePacks"

    private val SHADER_EXTENSIONS = listOf("vsh", "fsh", "glsl", "csh", "gsh", "tcs", "tes")

    /** Shader asli Horizon: zip berekstensi .hshader dengan horizon-shaders.toml di dasar arsip (crate horizon-shader-api). */
    const val NATIVE_SHADER_EXTENSION = ".hshader"
    private const val NATIVE_MANIFEST = "horizon-shaders.toml"
    private const val NATIVE_MAX_FILE_BYTES = 16L * 1024L * 1024L
    private const val NATIVE_MAX_ENTRIES = 512
    private const val NATIVE_MAX_WGSL_BYTES = 256L * 1024L
    private const val NATIVE_MAX_OTHER_BYTES = 1L shl 20
    private const val NATIVE_MAX_TOTAL_BYTES = 8L shl 20

    // Pesan galat ditampilkan apa adanya ke pengguna; semuanya ada di kamus bahasa (mods.tsv).
    const val MSG_TYPE = "Jenis berkas ini belum didukung mesin Rust."
    const val MSG_NAME = "Nama berkas tidak cocok untuk mesin Rust (terlalu panjang atau berisi karakter terlarang)."
    const val MSG_FORMAT = "Mesin Rust hanya menerima berkas .zip di sini."
    const val MSG_TOO_BIG = "Berkas lebih dari 256 MB, terlalu besar untuk mesin Rust."
    const val MSG_BROKEN = "Arsip rusak atau tidak bisa dibaca."
    const val MSG_TOO_MANY = "Arsip berisi terlalu banyak berkas."
    const val MSG_UNSAFE = "Arsip berisi path berbahaya atau ganda, jadi tidak dipasang."
    const val MSG_EXPANDED = "Isi arsip terlalu besar setelah dibuka (lebih dari 512 MB)."
    const val MSG_META = "Metadata resource pack (pack.mcmeta) tidak valid."
    const val MSG_NOT_RESOURCE = "Berkas ini bukan resource pack: pack.mcmeta tidak ditemukan di dasar arsip."
    const val MSG_NOT_SHADER = "Berkas ini bukan shader pack: folder shaders tidak ditemukan di dasar arsip."
    const val MSG_NATIVE = "Ini shader asli Horizon. Pasang dari sumber Horizon di layar Jelajah (format .hshader)."
    const val MSG_NATIVE_MANIFEST = "Ini bukan shader asli Horizon: horizon-shaders.toml tidak ditemukan di dasar arsip."
    const val MSG_NATIVE_BIG = "Shader asli Horizon terlalu besar (berkas lebih dari 16 MB, isi lebih dari 8 MB, atau ada berkas yang terlalu besar)."
    const val MSG_NO_ZIP = "Tidak ada berkas .zip yang cocok di versi ini."
    const val MSG_NO_HASH = "Sumber tidak memberi sidik jari berkas, jadi unduhan tidak bisa diperiksa."
    const val MSG_SAVE = "Gagal menyimpan berkas ke folder mesin Rust."
    const val MSG_OPTIONS =
        "Pengaturan game belum dibuat. Buka game sekali, masuk ke Pengaturan dan ubah satu opsi, lalu coba lagi."
    const val MSG_SELECT = "Gagal menyimpan pilihan ke pengaturan mesin."

    private val gson = GsonBuilder().disableHtmlEscaping().create()

    /** True bila [itemType] (jenis di layar Mod) bisa dipakai mesin Rust. */
    fun supports(itemType: String): Boolean =
        itemType == TYPE_SHADERS || itemType == TYPE_RESOURCES || itemType == TYPE_MODS

    private fun folderOf(itemType: String): String = when (itemType) {
        TYPE_SHADERS -> "shaderpacks"
        TYPE_MODS -> HorizonMods.FOLDER
        else -> "resourcepacks"
    }

    /**
     * Folder game yang dipakai mesin: [home] untuk profil 0 (bawaan), atau profiles/profile-N bila pengguna
     * memilih profil lain di dalam mesin (android-launcher.json, kunci `profile`).
     */
    fun gameHome(home: File): File {
        var profile = 0L
        try {
            val config = File(home, LAUNCHER_CONFIG)
            if (config.isFile && config.length() < 1_000_000L) {
                val root = JsonParser.parseString(config.readText())
                if (root.isJsonObject) {
                    val value = root.asJsonObject.get("profile")
                    if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                        profile = value.asLong
                    }
                }
            }
        } catch (_: Exception) {
            profile = 0L
        }
        return if (profile <= 0L) home else File(File(home, "profiles"), "profile-$profile")
    }

    /** Folder tujuan satu jenis berkas di folder game mesin. */
    fun dirFor(home: File, itemType: String): File = File(gameHome(home), folderOf(itemType))

    /** Aturan nama berkas dari mesin (content_files::plain_name): satu nama biasa, bukan path. */
    fun plainName(name: String): Boolean =
        name.isNotEmpty() &&
            name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES &&
            !name.startsWith(".") &&
            name.none { it == '/' || it == '\\' || it == ':' || it.isISOControl() }

    // ------------------------------------------------------------------------------------------
    // Daftar terpasang
    // ------------------------------------------------------------------------------------------

    /** Berkas shader / resource pack di folder mesin, lengkap dengan status "dipilih" dari berkas pengaturan. */
    fun installed(home: File, itemType: String): List<InstalledModItem> {
        if (itemType == TYPE_MODS) return HorizonMods.installed(home)
        if (!supports(itemType)) return emptyList()
        val files = dirFor(home, itemType).listFiles() ?: return emptyList()
        val on = enabledNames(home, itemType)
        val result = ArrayList<InstalledModItem>()
        for (file in files) {
            if (!file.isFile) continue
            val name = file.name
            if (name.startsWith(".") || name.endsWith(".part")) continue
            val lower = name.lowercase()
            val listed = lower.endsWith(".zip") || (itemType == TYPE_SHADERS && lower.endsWith(".hshader"))
            if (!listed) continue
            result.add(
                InstalledModItem(
                    id = name,
                    filename = name,
                    originalFilename = name,
                    name = file.nameWithoutExtension,
                    description = name,
                    version = "Lokal",
                    category = itemType.replaceFirstChar { it.uppercase() },
                    isEnabled = on.contains(name),
                    sizeBytes = file.length(),
                    itemType = itemType,
                    filePath = file.absolutePath
                )
            )
        }
        return result.sortedBy { it.name.lowercase() }
    }

    /** Nama berkas yang sedang dipilih (shader: paling banyak satu; resource pack: urutan dari bawah). */
    fun enabledNames(home: File, itemType: String): Set<String> {
        val game = gameHome(home)
        return when (itemType) {
            TYPE_SHADERS -> {
                val picked = lineValue(readText(File(game, GRAPHICS_FILE)), KEY_SHADER)?.trim().orEmpty()
                if (picked.isEmpty()) emptySet() else setOf(picked)
            }
            TYPE_RESOURCES -> {
                val ids = parseStringList(lineValue(readText(File(game, OPTIONS_FILE)), KEY_RESOURCES))
                ids.filter { it.startsWith("file/") }.map { it.removePrefix("file/") }.toSet()
            }
            else -> emptySet()
        }
    }

    /** True bila [name] sudah ada di folder tujuan. */
    fun exists(home: File, itemType: String, name: String): Boolean =
        plainName(name) && File(dirFor(home, itemType), name).exists()

    // ------------------------------------------------------------------------------------------
    // Pilih / lepas, hapus
    // ------------------------------------------------------------------------------------------

    /**
     * Memilih ([on] = true) atau melepas satu berkas. Shader: yang baru menggantikan yang lama. Resource pack:
     * yang dihidupkan masuk paling atas. Resource pack butuh options.txt yang sudah dibuat game: berkas itu
     * tidak dibuat dari sini karena tanpa baris versi, mesin menganggapnya berkas versi sangat lama dan
     * mengisi ulang beberapa pengaturan grafik dengan nilai standar yang lebih berat.
     */
    fun setEnabled(home: File, itemType: String, fileName: String, on: Boolean) {
        if (itemType == TYPE_MODS) {
            HorizonMods.setEnabled(home, fileName, on)
            return
        }
        if (!supports(itemType)) throw IOException(MSG_TYPE)
        if (!plainName(fileName)) throw IOException(MSG_NAME)
        val game = gameHome(home)
        try {
            if (itemType == TYPE_SHADERS) {
                setLine(File(game, GRAPHICS_FILE), KEY_SHADER, if (on) fileName else "")
                return
            }
            val options = File(game, OPTIONS_FILE)
            if (!options.isFile) throw IOException(MSG_OPTIONS)
            val text = options.readText()
            val id = "file/$fileName"
            val list = parseStringList(lineValue(text, KEY_RESOURCES))
            list.removeAll { it == id }
            if (list.isEmpty()) list.add("vanilla")
            if (on) {
                list.add(id)
            } else {
                // Jawaban "tetap pakai" dari game untuk pack ini ikut dilepas (sama seperti mesin).
                val incompatible = parseStringList(lineValue(text, KEY_INCOMPATIBLE))
                if (incompatible.contains(id)) {
                    incompatible.removeAll { it == id }
                    setLine(options, KEY_INCOMPATIBLE, toJsonArray(incompatible))
                }
            }
            setLine(options, KEY_RESOURCES, toJsonArray(list))
        } catch (e: IOException) {
            if (e.message == MSG_OPTIONS) throw e
            throw IOException(MSG_SELECT)
        }
    }

    /** Menghapus berkas. Bila sedang dipilih, pilihannya dilepas juga supaya mesin tidak mencari berkas yang hilang. */
    fun delete(home: File, itemType: String, fileName: String): Boolean {
        if (itemType == TYPE_MODS) return HorizonMods.delete(home, fileName)
        if (!supports(itemType) || !plainName(fileName)) return false
        val file = File(dirFor(home, itemType), fileName)
        if (!file.isFile) return false
        val wasOn = enabledNames(home, itemType).contains(fileName)
        if (!file.delete()) return false
        if (wasOn) {
            try {
                setEnabled(home, itemType, fileName, false)
            } catch (_: Exception) {
                // Berkasnya sudah terhapus; pilihan yang tersisa tidak merusak apa pun.
            }
        }
        return true
    }

    // ------------------------------------------------------------------------------------------
    // Pasang berkas (unduhan atau impor): periksa seperti mesin, lalu pindahkan ke folder tujuan
    // ------------------------------------------------------------------------------------------

    /**
     * Memeriksa [staged] (berkas sementara) dengan aturan mesin, lalu memindahkannya ke folder mesin dengan nama
     * [name] (diberi " (2)" dan seterusnya bila sudah dipakai). Berkas sementara tidak dihapus di sini.
     * Mengembalikan nama akhir di folder mesin.
     */
    fun install(staged: File, home: File, itemType: String, name: String): Result<String> {
        if (itemType == TYPE_MODS) return HorizonMods.install(staged, home, name)
        return try {
            if (!supports(itemType)) throw IOException(MSG_TYPE)
            validate(staged, itemType, name)
            val dir = dirFor(home, itemType)
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException(MSG_SAVE)
            val dest = uniqueFile(dir, name)
            if (!plainName(dest.name)) throw IOException(MSG_NAME)
            moveInto(staged, dest)
            Result.success(dest.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Menyalin [input] ke [target] dan berhenti dengan galat bila lebih dari [MAX_FILE_BYTES]
     * (berkas yang terlalu besar tidak boleh memenuhi penyimpanan). Berkas parsial dihapus bila gagal.
     */
    fun copyBounded(input: InputStream, target: File) {
        var failed = true
        try {
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_FILE_BYTES) throw IOException(MSG_TOO_BIG)
                    output.write(buffer, 0, read)
                }
            }
            failed = false
        } finally {
            if (failed) target.delete()
        }
    }

    /**
     * Pemeriksaan seperti content_files::validate milik mesin: nama polos, .zip, paling besar 256 MB, paling
     * banyak 32768 entri, path entri aman, total isi paling besar 512 MB, dan isinya cocok jenisnya
     * (resource pack: pack.mcmeta di dasar arsip; shader: berkas di folder shaders/ di dasar arsip).
     * Isi tiap berkas tidak dibaca ulang seluruhnya (hemat waktu di HP lemah); mesin tetap memeriksa saat memuat.
     */
    internal fun validate(file: File, itemType: String, name: String) {
        if (!plainName(name)) throw IOException(MSG_NAME)
        // Shader asli Horizon (.hshader) punya aturan paket sendiri; hanya jenis Shader yang menerimanya
        if (itemType == TYPE_SHADERS && name.lowercase().endsWith(NATIVE_SHADER_EXTENSION)) {
            validateNativeShader(file)
            return
        }
        if (!name.lowercase().endsWith(".zip")) throw IOException(MSG_FORMAT)
        if (file.length() > MAX_FILE_BYTES) throw IOException(MSG_TOO_BIG)

        var hasPack = false
        var hasShader = false
        var hasNative = false
        try {
            ZipFile(file).use { zip ->
                if (zip.size() > MAX_ENTRIES) throw IOException(MSG_TOO_MANY)
                val seen = HashSet<String>()
                var total = 0L
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val raw = entry.name
                    if (isUnsafeEntry(raw) || !seen.add(raw)) throw IOException(MSG_UNSAFE)
                    val size = entry.size
                    if (size < 0L) throw IOException(MSG_BROKEN)
                    total += size
                    if (total > MAX_EXPANDED_BYTES) throw IOException(MSG_EXPANDED)

                    if (raw == "pack.mcmeta") {
                        hasPack = true
                        if (size > MAX_META_BYTES) throw IOException(MSG_META)
                        val text = zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8)
                        val meta = try {
                            JsonParser.parseString(text)
                        } catch (_: Exception) {
                            throw IOException(MSG_META)
                        }
                        val pack = if (meta.isJsonObject) meta.asJsonObject.get("pack") else null
                        if (pack == null || !pack.isJsonObject) throw IOException(MSG_META)
                    }
                    if (raw == "horizon-shaders.toml") hasNative = true
                    if (raw.startsWith("shaders/") && !entry.isDirectory &&
                        SHADER_EXTENSIONS.any { raw.endsWith(".$it") }
                    ) {
                        hasShader = true
                    }
                }
            }
        } catch (e: IOException) {
            // IOException dari pemeriksaan di atas berisi pesan kita; zip rusak dari pustaka diganti pesan umum.
            throw if (isOurMessage(e.message)) e else IOException(MSG_BROKEN)
        } catch (_: Exception) {
            throw IOException(MSG_BROKEN)
        }

        if (itemType == TYPE_RESOURCES) {
            if (!hasPack) throw IOException(MSG_NOT_RESOURCE)
        } else {
            if (hasNative) throw IOException(MSG_NATIVE)
            if (!hasShader) throw IOException(MSG_NOT_SHADER)
        }
    }

    /**
     * Pemeriksaan bentuk paket .hshader seperti horizon_shader_api::Pack::read: zip, paling banyak 512 entri,
     * path entri aman, horizon-shaders.toml di dasar arsip, berkas .wgsl paling besar 256 KB, berkas lain 1 MB,
     * total isi 8 MB. Isi WGSL dan manifestnya sendiri tidak dibaca di sini: katalog dan mesin memeriksanya
     * (mesin menolak paket yang rusak sebelum memuatnya).
     */
    private fun validateNativeShader(file: File) {
        if (file.length() > NATIVE_MAX_FILE_BYTES) throw IOException(MSG_NATIVE_BIG)
        var hasManifest = false
        try {
            ZipFile(file).use { zip ->
                if (zip.size() > NATIVE_MAX_ENTRIES) throw IOException(MSG_TOO_MANY)
                val seen = HashSet<String>()
                var total = 0L
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val raw = entry.name
                    if (isUnsafeEntry(raw) || !seen.add(raw)) throw IOException(MSG_UNSAFE)
                    if (entry.isDirectory) continue
                    val size = entry.size
                    if (size < 0L) throw IOException(MSG_BROKEN)
                    val limit = if (raw.endsWith(".wgsl")) NATIVE_MAX_WGSL_BYTES else NATIVE_MAX_OTHER_BYTES
                    if (size > limit) throw IOException(MSG_NATIVE_BIG)
                    total += size
                    if (total > NATIVE_MAX_TOTAL_BYTES) throw IOException(MSG_NATIVE_BIG)
                    if (raw == NATIVE_MANIFEST) hasManifest = true
                }
            }
        } catch (e: IOException) {
            throw if (isOurMessage(e.message)) e else IOException(MSG_BROKEN)
        } catch (_: Exception) {
            throw IOException(MSG_BROKEN)
        }
        if (!hasManifest) throw IOException(MSG_NATIVE_MANIFEST)
    }

    /** Path entri arsip yang ditolak mesin: kosong, terlalu panjang, berkarakter terlarang, mutlak, atau berisi ".." / ".". */
    private fun isUnsafeEntry(raw: String): Boolean =
        raw.isEmpty() ||
            raw.toByteArray(Charsets.UTF_8).size > MAX_ENTRY_PATH_BYTES ||
            raw.any { it.isISOControl() || it == '\\' || it == ':' } ||
            raw.startsWith("/") ||
            raw.split('/').any { it == ".." || it == "." }

    private fun isOurMessage(message: String?): Boolean = message != null && message in OUR_MESSAGES

    private val OUR_MESSAGES = setOf(
        MSG_TYPE, MSG_NAME, MSG_FORMAT, MSG_TOO_BIG, MSG_BROKEN, MSG_TOO_MANY, MSG_UNSAFE, MSG_EXPANDED,
        MSG_META, MSG_NOT_RESOURCE, MSG_NOT_SHADER, MSG_NATIVE, MSG_NATIVE_MANIFEST, MSG_NATIVE_BIG,
        MSG_NO_ZIP, MSG_NO_HASH, MSG_SAVE
    )

    internal fun uniqueFile(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        for (n in 2..9999) {
            val candidate = File(dir, "$stem ($n)$ext")
            if (!candidate.exists()) return candidate
        }
        throw IOException(MSG_SAVE)
    }

    internal fun moveInto(staged: File, dest: File) {
        if (staged.renameTo(dest)) return
        val part = File(dest.parentFile, dest.name + ".part")
        try {
            staged.inputStream().use { input ->
                part.outputStream().use { output -> input.copyTo(output) }
            }
            if (!part.renameTo(dest)) throw IOException(MSG_SAVE)
        } catch (e: Exception) {
            part.delete()
            throw if (e is IOException) e else IOException(MSG_SAVE)
        }
        staged.delete()
    }

    // ------------------------------------------------------------------------------------------
    // Pilihan versi dari Modrinth
    // ------------------------------------------------------------------------------------------

    /** Berkas .zip yang dipakai dari satu versi (yang utama bila .zip, jika tidak .zip pertama). */
    fun pickFile(version: ModrinthVersion): ModrinthVersionFile? {
        val zips = version.files.filter { it.url.isNotBlank() && it.filename.lowercase().endsWith(".zip") }
        return zips.firstOrNull { it.primary } ?: zips.firstOrNull()
    }

    /**
     * Versi yang bisa dipasang (punya berkas .zip), dengan yang mencantumkan [mcVersion] di depan dan sisanya
     * mengikuti urutan dari Modrinth (terbaru lebih dulu). Sama seperti pilihan mesin sendiri.
     */
    fun usableVersions(all: List<ModrinthVersion>, mcVersion: String): List<ModrinthVersion> {
        val usable = all.filter { pickFile(it) != null }
        val matching = usable.filter { it.gameVersions.contains(mcVersion) }
        val others = usable.filter { !it.gameVersions.contains(mcVersion) }
        return matching + others
    }

    /** True bila berkas membawa sidik jari (sha512 atau sha1) yang bisa dicocokkan. */
    fun hasChecksum(file: ModrinthVersionFile): Boolean {
        val hashes = file.hashes ?: return false
        return !hashes["sha512"].isNullOrBlank() || !hashes["sha1"].isNullOrBlank()
    }

    // ------------------------------------------------------------------------------------------
    // Baca-tulis berkas pengaturan "kunci:nilai" per baris (sama seperti mesin)
    // ------------------------------------------------------------------------------------------

    private fun readText(file: File): String =
        try {
            if (file.isFile) file.readText() else ""
        } catch (_: Exception) {
            ""
        }

    /** Nilai `key:value` pertama di [text]; null bila barisnya tidak ada. */
    internal fun lineValue(text: String, key: String): String? {
        val prefix = "$key:"
        for (line in text.lines()) {
            if (line.startsWith(prefix)) return line.substring(prefix.length)
        }
        return null
    }

    /** Daftar teks JSON `["a","b"]`; kosong bila tidak ada atau bentuknya salah (sama seperti mesin). */
    internal fun parseStringList(value: String?): MutableList<String> {
        val out = ArrayList<String>()
        if (value == null) return out
        try {
            val parsed = JsonParser.parseString(value.trim())
            if (!parsed.isJsonArray) return ArrayList()
            for (element in parsed.asJsonArray) {
                if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return ArrayList()
                out.add(element.asString)
            }
        } catch (_: Exception) {
            return ArrayList()
        }
        return out
    }

    internal fun toJsonArray(values: List<String>): String {
        val array = JsonArray()
        for (v in values) array.add(v)
        return gson.toJson(array)
    }

    /**
     * Mengubah `key:value` di [file], atau menambah barisnya; baris lain tetap. Ditulis ke berkas sementara
     * lalu dipindah, jadi mesin tidak pernah membaca berkas yang setengah tertulis.
     */
    internal fun setLine(file: File, key: String, value: String) {
        val text = readText(file)
        val prefix = "$key:"
        var lines = text.lines()
        if (lines.isNotEmpty() && lines.last().isEmpty()) lines = lines.dropLast(1)
        var found = false
        val out = ArrayList<String>(lines.size + 1)
        for (line in lines) {
            if (!found && line.startsWith(prefix)) {
                found = true
                out.add(prefix + value)
            } else {
                out.add(line)
            }
        }
        if (!found) out.add(prefix + value)
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) throw IOException(MSG_SELECT)
        val temp = File(parent, file.name + ".amber-tmp")
        try {
            temp.writeText(out.joinToString("\n") + "\n")
            if (!temp.renameTo(file)) throw IOException(MSG_SELECT)
        } catch (e: Exception) {
            temp.delete()
            throw if (e is IOException) e else IOException(MSG_SELECT)
        }
    }
}
