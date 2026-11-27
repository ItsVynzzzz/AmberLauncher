package com.israadev.nuxlauncher.core.horizon

import com.israadev.nuxlauncher.core.models.InstalledModItem
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile
import org.json.JSONObject

/**
 * Mod untuk mesin Rust (Horizon): modul bawaan mesin dan berkas `.hmod`. Mod Java (.jar Fabric / Forge) tidak
 * bisa berjalan di mesin ini; yang bisa adalah mod konten (data dan aset) dan mod berkode WASM milik Horizon.
 *
 * Aturan mengikuti mesin (crates/horizon-mods, horizon-client-render/src/mod_list.rs, content_files.rs):
 * - Berkas ada di `horizon-mods/` di folder game (sebelah options.txt), bernama `<id>.hmod`.
 * - Hidup / mati ada di `horizon-mods.txt`, satu baris `mod.<id>:true` per mod yang hidup. Baris lain di berkas
 *   itu (misalnya pengaturan tombol mod) ditulis kembali apa adanya. Mod yang tidak tercantum dianggap mati.
 * - Empat modul bawaan (zoom, minimap, recipes, smartf3) memakai berkas dan aturan hidup / mati yang sama,
 *   tanpa berkas `.hmod`.
 * - Isi .hmod diperiksa seperti mesin: zip dengan manifest `horizon-mod.toml` di dasar arsip; kode `mod.wasm`
 *   wajib kecuali manifest memberi kemampuan `registries` (mod konten murni).
 *
 * Starling (mesin Rust kedua, files/starling) punya aturan sendiri, dikenali dari nama [home] ([StarlingEngine.HOME_DIR]):
 * - Modul bawaannya modul native ([BUNDLED_STARLING]), bukan empat modul Horizon. Yang tampil hanya yang benar-benar
 *   dikompilasi ke dalam pustaka mesin oleh build Amber (native_mods di starling-engine.properties, dicatat di
 *   assets/native-mods/compiled.txt; lihat [StarlingEngine.compiledNativeMods]).
 * - Mod WebAssembly (`mod.wasm`) tidak jalan lagi di sana: `.hmod` berkode WASM ditolak, dan `.hmod` yang membawa kode
 *   native (`horizon-native.json`) diterima.
 * - Mod native `.hnmod` (zip berisi `horizon-native.json` dan mixin) dipasang di `horizon-native-mods/<id>.hnmod`.
 *   Mod native tidak bisa dikompilasi di HP (Compile di dalam game dimatikan oleh patch amber5): mod masuk ke game lewat
 *   build mesin Amber, jadi berkas .hnmod yang dipasang di sini hanya disimpan.
 *
 * Tanpa Android: hanya berkas dan teks, jadi mudah diuji. [home] selalu folder data mesin.
 */
object HorizonMods {

    const val TYPE = "mods"
    const val FOLDER = "horizon-mods"
    const val LIST_FILE = "horizon-mods.txt"
    const val EXTENSION = ".hmod"

    /** Awalan nama berkas untuk modul bawaan di daftar Terpasang (bukan berkas sungguhan). */
    const val BUILTIN = "builtin:"

    const val MAX_FILE_BYTES = 32L shl 20

    // Starling: mod native (.hnmod). Aturannya mengikuti horizon_mods::index::native::Bundle::read milik mesin.
    const val NATIVE_FOLDER = "horizon-native-mods"
    const val NATIVE_EXTENSION = ".hnmod"

    /** Awalan nama berkas untuk mod native di daftar Terpasang (menandai berkas di [NATIVE_FOLDER]). */
    const val NATIVE_PREFIX = "native:"
    private const val NATIVE_MANIFEST = "horizon-native.json"
    private const val MAX_NATIVE_BYTES = 64L shl 20
    private const val MAX_NATIVE_FILES = 512
    private const val MAX_EXPANDED_BYTES = 64L shl 20
    private const val MAX_WASM_BYTES = 32L shl 20
    private const val MAX_MANIFEST_BYTES = 1L shl 20
    private const val MAX_ENTRIES = 32768
    private const val MAX_ENTRY_PATH_BYTES = 1024
    private const val MANIFEST = "horizon-mod.toml"
    private const val WASM = "mod.wasm"

    // Pesan galat ditampilkan apa adanya ke pengguna; semuanya ada di kamus bahasa (mods.tsv).
    const val MSG_FORMAT = "Mod mesin Rust berformat .hmod, bukan .jar Fabric atau Forge."
    const val MSG_TOO_BIG = "Berkas mod lebih dari 32 MB, terlalu besar untuk mesin Rust."
    const val MSG_EXPANDED = "Isi mod terlalu besar setelah dibuka (lebih dari 64 MB)."
    const val MSG_NO_MANIFEST = "Ini bukan mod Horizon: horizon-mod.toml tidak ada di dasar arsip."
    const val MSG_BAD_MANIFEST = "Manifest mod (horizon-mod.toml) tidak valid atau tidak didukung."
    const val MSG_NO_CODE =
        "Mod ini tidak membawa kode (mod.wasm) dan bukan mod konten (kemampuan registries), jadi tidak bisa dipasang."
    const val MSG_WASM_BIG = "Kode mod (mod.wasm) lebih dari 32 MB."
    const val MSG_FOLDER_CLASH = "Sudah ada mod berbentuk folder dengan id yang sama. Hapus yang lama dulu."
    const val MSG_LIST = "Gagal menyimpan pilihan mod ke pengaturan mesin."
    const val MSG_NEED_64 = "Mod hanya bisa dihidupkan di APK 64-bit."
    const val MSG_NOT_FOUND = "Mod tidak ditemukan atau manifest-nya tidak bisa dibaca."
    const val MSG_WASM_GONE =
        "Starling tidak lagi menjalankan mod WebAssembly (mod.wasm). Mod ini harus dibuat ulang sebagai mod native (.hnmod)."
    const val MSG_NATIVE_BAD =
        "Ini bukan mod native yang bisa dibaca Starling: horizon-native.json tidak ada atau tidak sah."
    const val MSG_NATIVE_TOGGLE =
        "Mod native tidak dihidupkan dari sini: mod masuk ke game lewat build mesin Amber (native_mods), bukan lewat Compile di HP."
    const val MSG_NATIVE_ONLY_STARLING = "Mod native (.hnmod) hanya didukung mesin Starling. Buat instance Starling untuk memakainya."
    private const val NATIVE_NOTE =
        "Mod native (.hnmod). Belum ikut di game: mod native masuk lewat build mesin Amber, bukan Compile di HP."

    private val OUR_MESSAGES = setOf(
        MSG_FORMAT, MSG_TOO_BIG, MSG_EXPANDED, MSG_NO_MANIFEST, MSG_BAD_MANIFEST, MSG_NO_CODE, MSG_WASM_BIG,
        MSG_FOLDER_CLASH, MSG_LIST, MSG_NOT_FOUND, MSG_WASM_GONE, MSG_NATIVE_BAD, MSG_NATIVE_TOGGLE, MSG_NATIVE_ONLY_STARLING,
        HorizonContent.MSG_NAME, HorizonContent.MSG_TOO_MANY, HorizonContent.MSG_UNSAFE, HorizonContent.MSG_SAVE
    )

    /** Modul yang sudah ada di dalam APK mesin (semuanya mati sampai dihidupkan). */
    class Bundled(val id: String, val title: String, val description: String)

    val BUNDLED = listOf(
        Bundled("zoom", "Zoom", "Tahan tombol Z untuk zoom; bisa diatur di Options > Mods dalam game"),
        Bundled("minimap", "Minimap", "Peta kecil yang bisa diatur di sudut layar"),
        Bundled("recipes", "Resep", "Resep dan kegunaan item di samping inventori"),
        Bundled("smartf3", "SmartF3", "Modul F3 yang bisa diatur untuk layar debug")
    )

    /**
     * Modul native bawaan Starling-extras: ANDROID_BUNDLED di mod_list.rs, sama dengan BUILTIN_NATIVE_MODS di
     * packaging/android/build_apk.py. Daftar ini hanya nama dan id; modul yang ADA di pustaka mesin ini diberi tahu oleh
     * build-nya (parameter `compiled` di [installed]). Mati sampai dihidupkan, dengan baris `mod.<id>:true` yang sama
     * seperti modul bawaan Horizon. Minimap dan worldmap sudah dihapus di sana, jadi tidak ada di daftar ini.
     */
    val BUNDLED_STARLING = listOf(
        "zoom" to "Zoom",
        "recipes" to "Resep",
        "smartf3" to "SmartF3",
        "freecam_plus" to "Freecam+",
        "connected_textures" to "Connected Textures",
        "armorhud" to "Armor HUD",
        "jade" to "Jade",
        "basic_hud" to "Basic HUD",
        "appleskin" to "AppleSkin",
        "visuality_horizon" to "Visuality",
        "punchy" to "Punchy",
        "freelook" to "Freelook",
        "cinematic_interactions" to "Cinematic Interactions",
        "firstpersonmodel" to "First-person Model",
        "eatinganimation" to "Eating Animation",
        "veinminer" to "Veinminer",
        "liteminer" to "LiteMiner",
        "rightharvest" to "RightClickHarvest",
        "customhud" to "Beautiful Day Counter",
        "carryblock" to "Carry Block",
        "amber_lyrics" to "Amber Lyrics"
    ).map { (id, title) ->
        Bundled(id, title, "Modul native bawaan Starling. Pengaturannya ada di Options > Mods dalam game.")
    }

    /** Daftar modul bawaan untuk mesin pemilik [home]: Starling punya daftarnya sendiri. */
    private fun bundledFor(home: File): List<Bundled> =
        if (home.name == StarlingEngine.HOME_DIR) BUNDLED_STARLING else BUNDLED

    /** Isi manifest yang dipakai Amber. [hasCode] = ada mod.wasm di dalam arsip. */
    data class Manifest(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val capabilities: List<String>,
        val hasCode: Boolean = false
    )

    // ------------------------------------------------------------------------------------------
    // Daftar terpasang
    // ------------------------------------------------------------------------------------------

    /**
     * Modul bawaan lalu berkas / folder mod di folder mesin, lengkap dengan status hidup dari horizon-mods.txt.
     * [compiled] (hanya Starling): id modul native yang ada di pustaka mesin; modul bawaan di luar himpunan itu tidak
     * ditampilkan karena sakelarnya tidak akan berbuat apa-apa. null = tampilkan semua.
     */
    fun installed(home: File, compiled: Set<String>? = null): List<InstalledModItem> {
        val game = HorizonContent.gameHome(home)
        val state = readList(game)
        val result = ArrayList<InstalledModItem>()
        for (b in bundledFor(home)) {
            if (compiled != null && home.name == StarlingEngine.HOME_DIR && b.id !in compiled) continue
            result.add(
                InstalledModItem(
                    id = BUILTIN + b.id,
                    filename = b.id,
                    originalFilename = BUILTIN + b.id,
                    name = b.title,
                    description = b.description,
                    version = "Bawaan",
                    category = "Mod",
                    isEnabled = state.enabled.contains(b.id) && !state.disabled.contains(b.id),
                    sizeBytes = 0L,
                    itemType = TYPE,
                    filePath = ""
                )
            )
        }
        val files = File(game, FOLDER).listFiles() ?: emptyArray()
        val found = ArrayList<InstalledModItem>()
        for (file in files) {
            val name = file.name
            if (name.startsWith(".")) continue
            val manifest = when {
                file.isFile && name.endsWith(EXTENSION) -> readManifest(file)
                file.isDirectory && File(file, MANIFEST).isFile -> readFolderManifest(file)
                else -> continue
            }
            val stem = if (file.isFile) name.removeSuffix(EXTENSION) else name
            found.add(
                InstalledModItem(
                    id = name,
                    filename = name,
                    originalFilename = name,
                    name = manifest?.name ?: stem,
                    description = manifest?.description.orEmpty(),
                    version = manifest?.version ?: "Lokal",
                    category = "Mod",
                    isEnabled = manifest != null && state.enabled.contains(manifest.id),
                    sizeBytes = if (file.isFile) file.length() else 0L,
                    itemType = TYPE,
                    filePath = file.absolutePath
                )
            )
        }
        result.addAll(found.sortedBy { it.name.lowercase() })
        if (home.name == StarlingEngine.HOME_DIR) result.addAll(installedNative(game))
        return result
    }

    /** Mod native (.hnmod) yang terpasang di [NATIVE_FOLDER]; tidak bisa dihidupkan dari Amber, hanya dihapus. */
    private fun installedNative(game: File): List<InstalledModItem> {
        val files = File(game, NATIVE_FOLDER).listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && !it.name.startsWith(".") && it.name.endsWith(NATIVE_EXTENSION) }
            .map { file ->
                val manifest = readNativeManifest(file)
                InstalledModItem(
                    id = NATIVE_PREFIX + file.name,
                    filename = file.name,
                    originalFilename = NATIVE_PREFIX + file.name,
                    name = manifest?.let { displayName(it.id) } ?: file.name.removeSuffix(NATIVE_EXTENSION),
                    description = if (manifest != null) NATIVE_NOTE else MSG_NATIVE_BAD,
                    version = manifest?.version ?: "Lokal",
                    category = "Mod",
                    isEnabled = false,
                    sizeBytes = file.length(),
                    itemType = TYPE,
                    filePath = file.absolutePath
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    /** Nama mod dari id-nya, satu-satunya yang dikatakan bundelnya (nama aslinya ada di kodenya): carry_block jadi Carry block. */
    private fun displayName(id: String): String {
        val words = id.replace('_', ' ').replace('-', ' ')
        return words.replaceFirstChar { it.uppercase() }
    }

    /** True bila [item] boleh dihapus dari Amber: hanya berkas .hmod (modul bawaan dan mod folder tidak). */
    fun removable(item: InstalledModItem): Boolean =
        item.itemType == TYPE && (item.filePath.endsWith(EXTENSION) || item.filePath.endsWith(NATIVE_EXTENSION))

    // ------------------------------------------------------------------------------------------
    // Hidup / mati dan hapus
    // ------------------------------------------------------------------------------------------

    /** Menghidupkan atau mematikan satu mod ([fileName] dari daftar Terpasang). Berlaku saat game dibuka lagi. */
    fun setEnabled(home: File, fileName: String, on: Boolean) {
        // Mod native (.hnmod) tidak ada saklarnya: mod masuk ke game lewat build mesin Amber, bukan di HP.
        if (fileName.startsWith(NATIVE_PREFIX)) throw IOException(MSG_NATIVE_TOGGLE)
        val id = idOf(home, fileName) ?: throw IOException(MSG_NOT_FOUND)
        writeEnabled(HorizonContent.gameHome(home), id, on)
    }

    /** Menghapus berkas .hmod; pilihannya di horizon-mods.txt ikut dilepas. */
    fun delete(home: File, fileName: String): Boolean {
        if (fileName.startsWith(NATIVE_PREFIX)) {
            val native = fileName.removePrefix(NATIVE_PREFIX)
            if (!HorizonContent.plainName(native) || !native.endsWith(NATIVE_EXTENSION)) return false
            val target = File(File(HorizonContent.gameHome(home), NATIVE_FOLDER), native)
            return target.isFile && target.delete()
        }
        if (fileName.startsWith(BUILTIN) || !HorizonContent.plainName(fileName) || !fileName.endsWith(EXTENSION)) {
            return false
        }
        val game = HorizonContent.gameHome(home)
        val file = File(File(game, FOLDER), fileName)
        if (!file.isFile) return false
        val id = readManifest(file)?.id
        if (!file.delete()) return false
        if (id != null) {
            try {
                writeEnabled(game, id, false)
            } catch (_: Exception) {
                // Berkasnya sudah terhapus; baris yang tersisa tidak merusak apa pun.
            }
        }
        return true
    }

    /** Id mod untuk satu nama di daftar Terpasang (modul bawaan, berkas .hmod, atau folder mod). */
    internal fun idOf(home: File, fileName: String): String? {
        if (fileName.startsWith(BUILTIN)) {
            val id = fileName.removePrefix(BUILTIN)
            return if (bundledFor(home).any { it.id == id }) id else null
        }
        if (!HorizonContent.plainName(fileName)) return null
        val target = File(File(HorizonContent.gameHome(home), FOLDER), fileName)
        return when {
            target.isFile && fileName.endsWith(EXTENSION) -> readManifest(target)?.id
            target.isDirectory -> readFolderManifest(target)?.id
            else -> null
        }
    }

    internal class ListState(val enabled: Set<String>, val disabled: Set<String>)

    private fun readList(game: File): ListState {
        val text = try {
            val file = File(game, LIST_FILE)
            if (file.isFile && file.length() < 1_000_000L) file.readText() else ""
        } catch (_: Exception) {
            ""
        }
        return parseList(text)
    }

    /** Sama seperti ModList::parse milik mesin: `mod.<id>:true` hidup, `mod.<id>:false` mati. */
    internal fun parseList(text: String): ListState {
        val on = LinkedHashSet<String>()
        val off = LinkedHashSet<String>()
        for (line in text.lines()) {
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val key = line.substring(0, colon).trim()
            if (!key.startsWith("mod.")) continue
            val id = key.removePrefix("mod.")
            when (line.substring(colon + 1).trim()) {
                "true" -> on.add(id)
                "false" -> off.add(id)
            }
        }
        return ListState(on, off)
    }

    /** Isi baru horizon-mods.txt: semua baris `mod.<id>:...` diganti satu baris `true` (atau dibuang bila mati). */
    internal fun withEnabled(text: String, id: String, on: Boolean): String {
        var lines = text.lines()
        if (lines.isNotEmpty() && lines.last().isEmpty()) lines = lines.dropLast(1)
        val key = "mod.$id"
        val out = ArrayList<String>(lines.size + 1)
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon >= 0 && line.substring(0, colon).trim() == key) continue
            out.add(line)
        }
        if (on) out.add("$key:true")
        return if (out.isEmpty()) "" else out.joinToString("\n") + "\n"
    }

    private fun writeEnabled(game: File, id: String, on: Boolean) {
        val file = File(game, LIST_FILE)
        val old = try {
            if (file.isFile) file.readText() else ""
        } catch (_: Exception) {
            throw IOException(MSG_LIST)
        }
        if (!file.exists() && !on) return
        val next = withEnabled(old, id, on)
        if (next == old) return
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) throw IOException(MSG_LIST)
        val temp = File(parent, file.name + ".amber-tmp")
        try {
            temp.writeText(next)
            if (!temp.renameTo(file)) throw IOException(MSG_LIST)
        } catch (e: Exception) {
            temp.delete()
            throw if (e is IOException) e else IOException(MSG_LIST)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Pasang berkas (unduhan atau impor)
    // ------------------------------------------------------------------------------------------

    /**
     * Memeriksa [staged] (berkas sementara) seperti mesin, lalu memindahkannya ke `horizon-mods/<id>.hmod`.
     * Mod dengan id yang sama diganti (pembaruan) dan pilihan hidup / matinya dipertahankan. Mod baru yang hanya
     * berisi konten langsung dihidupkan; mod berkode dibiarkan mati sampai pengguna menghidupkannya sendiri.
     * Berkas sementara tidak dihapus di sini. Mengembalikan nama berkas akhir.
     */
    fun install(staged: File, home: File, name: String): Result<String> {
        if (name.lowercase().endsWith(NATIVE_EXTENSION)) return installNative(staged, home, name)
        return try {
            val manifest = inspect(staged, name, home.name == StarlingEngine.HOME_DIR)
            val game = HorizonContent.gameHome(home)
            val dir = File(game, FOLDER)
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException(HorizonContent.MSG_SAVE)

            val listing = dir.listFiles() ?: emptyArray()
            if (listing.any { it.isDirectory && readFolderManifest(it)?.id == manifest.id }) {
                throw IOException(MSG_FOLDER_CLASH)
            }
            val wanted = File(dir, manifest.id + EXTENSION)
            val sameId = listing.filter {
                it.isFile && it.name.endsWith(EXTENSION) && readManifest(it)?.id == manifest.id
            }
            val dest = if (wanted.exists() && wanted !in sameId) HorizonContent.uniqueFile(dir, wanted.name) else wanted
            if (!HorizonContent.plainName(dest.name)) throw IOException(HorizonContent.MSG_NAME)

            HorizonContent.moveInto(staged, dest)
            for (old in sameId) if (old != dest) old.delete()
            if (sameId.isEmpty() && !manifest.hasCode) {
                try {
                    writeEnabled(game, manifest.id, true)
                } catch (_: Exception) {
                    // Terpasang tetapi belum hidup; pengguna bisa menyalakannya dari daftar.
                }
            }
            Result.success(dest.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pemeriksaan seperti content_files::validate dan horizon_mods::Package::read milik mesin: nama polos
     * berakhiran .hmod, paling besar 32 MB, paling banyak 32768 entri, path entri aman, isi paling besar 64 MB,
     * `horizon-mod.toml` di dasar arsip dan sah, `mod.wasm` paling besar 32 MB, dan kode wajib ada kecuali
     * mod konten. Isi kode WASM tidak dibaca di sini; mesin memeriksanya saat memuat.
     */
    internal fun inspect(file: File, name: String, starling: Boolean = false): Manifest {
        if (!HorizonContent.plainName(name)) throw IOException(HorizonContent.MSG_NAME)
        if (!name.lowercase().endsWith(EXTENSION)) throw IOException(MSG_FORMAT)
        if (file.length() > MAX_FILE_BYTES) throw IOException(MSG_TOO_BIG)

        var manifestText: String? = null
        var hasCode = false
        try {
            ZipFile(file).use { zip ->
                if (zip.size() > MAX_ENTRIES) throw IOException(HorizonContent.MSG_TOO_MANY)
                val seen = HashSet<String>()
                var total = 0L
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val raw = entry.name
                    if (raw.isEmpty() ||
                        raw.toByteArray(Charsets.UTF_8).size > MAX_ENTRY_PATH_BYTES ||
                        raw.any { it.isISOControl() || it == '\\' || it == ':' } ||
                        raw.startsWith("/") ||
                        raw.split('/').any { it == ".." || it == "." } ||
                        !seen.add(raw)
                    ) {
                        throw IOException(HorizonContent.MSG_UNSAFE)
                    }
                    val size = entry.size
                    if (size < 0L) throw IOException(HorizonContent.MSG_BROKEN)
                    total += size
                    if (total > MAX_EXPANDED_BYTES) throw IOException(MSG_EXPANDED)
                    // Starling: mod WebAssembly tidak jalan lagi; kode native (horizon-native.json) menggantikannya.
                    if (starling && raw == NATIVE_MANIFEST) hasCode = true
                    if (raw == WASM) {
                        if (starling) throw IOException(MSG_WASM_GONE)
                        hasCode = true
                        if (size > MAX_WASM_BYTES) throw IOException(MSG_WASM_BIG)
                    }
                    if (raw == MANIFEST) {
                        if (size > MAX_MANIFEST_BYTES) throw IOException(MSG_BAD_MANIFEST)
                        manifestText = decodeUtf8(zip.getInputStream(entry).use { it.readBytes() })
                    }
                }
            }
        } catch (e: IOException) {
            // IOException dari pemeriksaan di atas berisi pesan kita; zip rusak dari pustaka diganti pesan umum.
            throw if (e.message.let { it != null && it in OUR_MESSAGES }) e else IOException(HorizonContent.MSG_BROKEN)
        } catch (_: Exception) {
            throw IOException(HorizonContent.MSG_BROKEN)
        }

        val text = manifestText ?: throw IOException(MSG_NO_MANIFEST)
        val manifest = parseManifest(text)
        if (!hasCode && "registries" !in manifest.capabilities) throw IOException(MSG_NO_CODE)
        return manifest.copy(hasCode = hasCode)
    }

    /**
     * Memasang mod native `.hnmod` (hanya Starling) ke `horizon-native-mods/<id>.hnmod`. Bundel diperiksa seperti
     * mesin (Bundle::read): zip dengan `horizon-native.json` yang sah, path aman, tanpa `mod.wasm`, paling banyak 512
     * berkas dan 64 MB setelah dibuka. Kodenya tidak dijalankan di sini; bundel hanya disimpan, karena mod native masuk
     * ke game lewat build mesin Amber (bukan Compile di HP). Mod dengan id yang sama diganti. Mengembalikan nama berkas akhir.
     */
    fun installNative(staged: File, home: File, name: String): Result<String> {
        return try {
            if (home.name != StarlingEngine.HOME_DIR) throw IOException(MSG_NATIVE_ONLY_STARLING)
            val manifest = inspectNative(staged, name)
            val dir = File(HorizonContent.gameHome(home), NATIVE_FOLDER)
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException(HorizonContent.MSG_SAVE)
            val dest = File(dir, manifest.id + NATIVE_EXTENSION)
            if (!HorizonContent.plainName(dest.name)) throw IOException(HorizonContent.MSG_NAME)
            HorizonContent.moveInto(staged, dest)
            Result.success(dest.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal class NativeManifest(val id: String, val version: String, val game: String)

    internal fun inspectNative(file: File, name: String): NativeManifest {
        if (!HorizonContent.plainName(name)) throw IOException(HorizonContent.MSG_NAME)
        if (!name.lowercase().endsWith(NATIVE_EXTENSION)) throw IOException(MSG_FORMAT)
        if (file.length() > MAX_NATIVE_BYTES) throw IOException(MSG_TOO_BIG)

        var manifestText: String? = null
        try {
            ZipFile(file).use { zip ->
                if (zip.size() > MAX_ENTRIES) throw IOException(HorizonContent.MSG_TOO_MANY)
                val seen = HashSet<String>()
                var total = 0L
                var nativeFiles = 0
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val raw = entry.name
                    if (raw.isEmpty() ||
                        raw.toByteArray(Charsets.UTF_8).size > MAX_ENTRY_PATH_BYTES ||
                        raw.any { it.isISOControl() || it == '\\' || it == ':' } ||
                        raw.startsWith("/") ||
                        raw.split('/').any { it.isEmpty() || it == ".." || it == "." } ||
                        !seen.add(raw)
                    ) {
                        throw IOException(HorizonContent.MSG_UNSAFE)
                    }
                    if (raw == WASM) throw IOException(MSG_WASM_GONE)
                    // Berkas konten (manifest mod, assets/, data/) dibaca mesin sebagai konten, bukan sebagai build native.
                    if (raw == MANIFEST || raw.startsWith("assets/") || raw.startsWith("data/")) continue
                    nativeFiles++
                    if (nativeFiles > MAX_NATIVE_FILES) throw IOException(HorizonContent.MSG_TOO_MANY)
                    val size = entry.size
                    if (size < 0L) throw IOException(HorizonContent.MSG_BROKEN)
                    total += size
                    if (total > MAX_EXPANDED_BYTES) throw IOException(MSG_EXPANDED)
                    if (raw == NATIVE_MANIFEST) {
                        if (size > MAX_MANIFEST_BYTES) throw IOException(MSG_NATIVE_BAD)
                        manifestText = decodeUtf8(zip.getInputStream(entry).use { it.readBytes() })
                    }
                }
            }
        } catch (e: IOException) {
            throw if (e.message.let { it != null && it in OUR_MESSAGES }) e else IOException(HorizonContent.MSG_BROKEN)
        } catch (_: Exception) {
            throw IOException(HorizonContent.MSG_BROKEN)
        }
        return parseNativeManifest(manifestText ?: throw IOException(MSG_NATIVE_BAD))
    }

    /** Manifest `horizon-native.json` seperti Manifest::check milik mesin: skema 1, id sah, dan dua revisi 40 digit hex. */
    internal fun parseNativeManifest(text: String): NativeManifest {
        val json = try {
            JSONObject(text)
        } catch (_: Exception) {
            throw IOException(MSG_NATIVE_BAD)
        }
        // Mesin menolak kunci yang tidak dikenalnya (deny_unknown_fields).
        val allowed = setOf("schema", "id", "version", "game", "horizon", "mixins", "hooks")
        val keys = json.keys()
        while (keys.hasNext()) if (keys.next() !in allowed) throw IOException(MSG_NATIVE_BAD)
        fun string(key: String): String = (json.opt(key) as? String) ?: throw IOException(MSG_NATIVE_BAD)
        val id = string("id")
        val version = string("version")
        val game = string("game")
        val horizon = string("horizon")
        val mixins = string("mixins")
        fun revision(value: String) = value.length == 40 && value.all { it in '0'..'9' || it in 'a'..'f' }
        val validId = id.length in 1..64 && id.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }
        if ((json.opt("schema") as? Number)?.toInt() != 1 ||
            !validId || version.isEmpty() || game.isEmpty() || !revision(horizon) || !revision(mixins)
        ) {
            throw IOException(MSG_NATIVE_BAD)
        }
        if (json.has("hooks") && !json.isNull("hooks")) {
            val hooks = (json.opt("hooks") as? Number)?.toLong()
            if (hooks == null || hooks <= 0L) throw IOException(MSG_NATIVE_BAD)
        }
        return NativeManifest(id, version, game)
    }

    /** Manifest dari berkas .hnmod yang sudah terpasang, atau null bila tidak bisa dibaca. */
    private fun readNativeManifest(file: File): NativeManifest? =
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(NATIVE_MANIFEST)
                if (entry == null || entry.size < 0L || entry.size > MAX_MANIFEST_BYTES) {
                    null
                } else {
                    parseNativeManifest(decodeUtf8(zip.getInputStream(entry).use { it.readBytes() }))
                }
            }
        } catch (_: Exception) {
            null
        }

    /** Manifest dari berkas .hmod yang sudah terpasang, atau null bila tidak bisa dibaca. */
    internal fun readManifest(file: File): Manifest? =
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(MANIFEST)
                if (entry == null || entry.size < 0L || entry.size > MAX_MANIFEST_BYTES) {
                    null
                } else {
                    parseManifest(decodeUtf8(zip.getInputStream(entry).use { it.readBytes() }))
                        .copy(hasCode = zip.getEntry(WASM) != null)
                }
            }
        } catch (_: Exception) {
            null
        }

    private fun readFolderManifest(dir: File): Manifest? =
        try {
            val file = File(dir, MANIFEST)
            if (!file.isFile || file.length() > MAX_MANIFEST_BYTES) {
                null
            } else {
                parseManifest(decodeUtf8(file.readBytes())).copy(hasCode = File(dir, WASM).isFile)
            }
        } catch (_: Exception) {
            null
        }

    private fun decodeUtf8(bytes: ByteArray): String =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            throw IOException(MSG_BAD_MANIFEST)
        }

    // ------------------------------------------------------------------------------------------
    // Manifest (horizon-mod.toml): aturan sama dengan horizon_mods::Manifest::parse
    // ------------------------------------------------------------------------------------------

    private val CAPABILITIES = setOf(
        "game-read", "chat", "hud", "storage", "command", "registries", "ui", "render", "input", "players",
        "world", "recipes"
    )
    private val SERVER_CAPABILITIES = setOf("storage", "players", "world")
    private val EXPERIMENTAL = setOf("ui", "render", "input", "recipes")
    private val RESERVED_NAMESPACES = setOf("minecraft", "c", "horizon")
    private val MANIFEST_KEYS = setOf(
        "id", "name", "version", "description", "authors", "api", "side", "capabilities", "namespaces",
        "experimental"
    )

    internal fun parseManifest(text: String): Manifest {
        val map = parseToml(text)
        if (map.keys.any { it !in MANIFEST_KEYS }) throw IOException(MSG_BAD_MANIFEST)

        fun string(key: String, required: Boolean): String {
            val value = map[key] ?: if (required) throw IOException(MSG_BAD_MANIFEST) else return ""
            return value as? String ?: throw IOException(MSG_BAD_MANIFEST)
        }

        fun list(key: String): List<String> {
            val value = map[key] ?: return emptyList()
            @Suppress("UNCHECKED_CAST")
            return (value as? List<String>) ?: throw IOException(MSG_BAD_MANIFEST)
        }

        val id = string("id", true)
        val name = string("name", true)
        val version = string("version", true)
        val description = string("description", false)
        val api = string("api", true)
        val side = if (map.containsKey("side")) string("side", true) else "client"
        val capabilities = list("capabilities")
        val namespaces = list("namespaces")
        val experimental = list("experimental")
        list("authors")

        val idOk = id.length in 1..64 && id.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }
        if (!idOk) throw IOException(MSG_BAD_MANIFEST)
        if (api != "0.1" && api != "0.2" && api != "0.3") throw IOException(MSG_BAD_MANIFEST)
        if (side != "client" && side != "server" && side != "both") throw IOException(MSG_BAD_MANIFEST)
        if (capabilities.any { it !in CAPABILITIES } || experimental.any { it !in CAPABILITIES }) {
            throw IOException(MSG_BAD_MANIFEST)
        }
        if ("registries" in capabilities && api == "0.1") throw IOException(MSG_BAD_MANIFEST)
        val server = side == "server"
        if (server != (api == "0.3")) throw IOException(MSG_BAD_MANIFEST)
        if (capabilities.any { (it in SERVER_CAPABILITIES) != server && it != "storage" }) {
            throw IOException(MSG_BAD_MANIFEST)
        }
        if (capabilities.any { it in EXPERIMENTAL && it !in experimental }) throw IOException(MSG_BAD_MANIFEST)
        if (namespaces.isNotEmpty() && "registries" !in capabilities) throw IOException(MSG_BAD_MANIFEST)
        for ((i, ns) in namespaces.withIndex()) {
            val charsOk = ns.length in 1..64 &&
                ns.all { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '.' || it == '-' }
            if (!charsOk || ns in RESERVED_NAMESPACES || ns == id || namespaces.subList(0, i).contains(ns)) {
                throw IOException(MSG_BAD_MANIFEST)
            }
        }
        return Manifest(id, name, version, description, capabilities)
    }

    /**
     * Bagian TOML yang dipakai horizon-mod.toml: `kunci = "teks"` dan `kunci = ["a", "b"]`, dengan komentar,
     * teks satu baris atau tiga tanda kutip, dan larik yang memanjang beberapa baris. Selain itu (tabel, angka,
     * kunci bertitik, kunci dobel) ditolak, sama seperti mesin yang menolak kolom tak dikenal.
     */
    internal fun parseToml(source: String): Map<String, Any> {
        val text = source.removePrefix("\uFEFF")
        val n = text.length
        val out = LinkedHashMap<String, Any>()
        var i = 0

        fun fail(): Nothing = throw IOException(MSG_BAD_MANIFEST)

        fun skipSpaces() {
            while (i < n && (text[i] == ' ' || text[i] == '\t')) i++
        }

        fun skipAll() {
            while (i < n) {
                val c = text[i]
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    i++
                } else if (c == '#') {
                    while (i < n && text[i] != '\n') i++
                } else {
                    break
                }
            }
        }

        fun readEscape(sb: StringBuilder) {
            // text[i] berada tepat setelah tanda '\'
            if (i >= n) fail()
            when (val e = text[i++]) {
                'b' -> sb.append('\b')
                't' -> sb.append('\t')
                'n' -> sb.append('\n')
                'f' -> sb.append('\u000c')
                'r' -> sb.append('\r')
                '"' -> sb.append('"')
                '\\' -> sb.append('\\')
                'u', 'U' -> {
                    val len = if (e == 'u') 4 else 8
                    if (i + len > n) fail()
                    val code = text.substring(i, i + len).toIntOrNull(16) ?: fail()
                    if (!Character.isValidCodePoint(code) || code in 0xD800..0xDFFF) fail()
                    sb.appendCodePoint(code)
                    i += len
                }
                else -> fail()
            }
        }

        fun readString(): String {
            val quote = text[i]
            val triple = text.startsWith("$quote$quote$quote", i)
            val sb = StringBuilder()
            if (triple) {
                i += 3
                if (i < n && text[i] == '\r' && i + 1 < n && text[i + 1] == '\n') i += 2 else if (i < n && text[i] == '\n') i++
                while (true) {
                    if (i >= n) fail()
                    if (text.startsWith("$quote$quote$quote", i)) {
                        i += 3
                        // Tanda kutip ekstra tepat sebelum penutup (paling banyak dua) ikut menjadi isi.
                        var extra = 0
                        while (extra < 2 && i < n && text[i] == quote) {
                            sb.append(quote)
                            i++
                            extra++
                        }
                        return sb.toString()
                    }
                    val c = text[i++]
                    if (c == '\\' && quote == '"') {
                        if (i < n && (text[i] == '\n' || text[i] == '\r' || text[i] == ' ' || text[i] == '\t')) {
                            // Garis miring di akhir baris: buang baris baru dan spasi sesudahnya.
                            var j = i
                            while (j < n && (text[j] == ' ' || text[j] == '\t')) j++
                            if (j < n && (text[j] == '\n' || text[j] == '\r')) {
                                while (j < n && (text[j] == ' ' || text[j] == '\t' || text[j] == '\n' || text[j] == '\r')) j++
                                i = j
                                continue
                            }
                            fail()
                        }
                        readEscape(sb)
                    } else {
                        sb.append(c)
                    }
                }
            }
            i++
            while (true) {
                if (i >= n) fail()
                val c = text[i++]
                when {
                    c == quote -> return sb.toString()
                    c == '\n' || c == '\r' -> fail()
                    c == '\\' && quote == '"' -> readEscape(sb)
                    else -> sb.append(c)
                }
            }
        }

        fun readKey(): String {
            if (i < n && (text[i] == '"' || text[i] == '\'')) return readString()
            val start = i
            while (i < n && (text[i].isLetterOrDigit() && text[i].code < 128 || text[i] == '_' || text[i] == '-')) i++
            if (i == start) fail()
            return text.substring(start, i)
        }

        while (true) {
            skipAll()
            if (i >= n) break
            if (text[i] == '[') fail()
            val key = readKey()
            skipSpaces()
            if (i >= n || text[i] != '=') fail()
            i++
            skipSpaces()
            if (i >= n) fail()
            val value: Any = when (text[i]) {
                '"', '\'' -> readString()
                '[' -> {
                    i++
                    val items = ArrayList<String>()
                    while (true) {
                        skipAll()
                        if (i >= n) fail()
                        if (text[i] == ']') {
                            i++
                            break
                        }
                        if (text[i] != '"' && text[i] != '\'') fail()
                        items.add(readString())
                        skipAll()
                        if (i >= n) fail()
                        if (text[i] == ',') {
                            i++
                        } else if (text[i] != ']') {
                            fail()
                        }
                    }
                    items
                }
                else -> fail()
            }
            if (out.containsKey(key)) fail()
            out[key] = value
            skipSpaces()
            if (i < n && text[i] == '#') while (i < n && text[i] != '\n') i++
            if (i < n && text[i] == '\r') i++
            if (i < n && text[i] != '\n') fail()
        }
        return out
    }
}
