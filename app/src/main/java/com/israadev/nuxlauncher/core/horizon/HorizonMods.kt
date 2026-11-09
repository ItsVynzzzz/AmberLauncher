package com.israadev.nuxlauncher.core.horizon

import com.israadev.nuxlauncher.core.models.InstalledModItem
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile

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

    private val OUR_MESSAGES = setOf(
        MSG_FORMAT, MSG_TOO_BIG, MSG_EXPANDED, MSG_NO_MANIFEST, MSG_BAD_MANIFEST, MSG_NO_CODE, MSG_WASM_BIG,
        MSG_FOLDER_CLASH, MSG_LIST, MSG_NOT_FOUND,
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

    /** Modul bawaan lalu berkas / folder mod di folder mesin, lengkap dengan status hidup dari horizon-mods.txt. */
    fun installed(home: File): List<InstalledModItem> {
        val game = HorizonContent.gameHome(home)
        val state = readList(game)
        val result = ArrayList<InstalledModItem>()
        for (b in BUNDLED) {
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
        val files = File(game, FOLDER).listFiles() ?: return result
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
        return result
    }

    /** True bila [item] boleh dihapus dari Amber: hanya berkas .hmod (modul bawaan dan mod folder tidak). */
    fun removable(item: InstalledModItem): Boolean =
        item.itemType == TYPE && item.filePath.endsWith(EXTENSION)

    // ------------------------------------------------------------------------------------------
    // Hidup / mati dan hapus
    // ------------------------------------------------------------------------------------------

    /** Menghidupkan atau mematikan satu mod ([fileName] dari daftar Terpasang). Berlaku saat game dibuka lagi. */
    fun setEnabled(home: File, fileName: String, on: Boolean) {
        val id = idOf(home, fileName) ?: throw IOException(MSG_NOT_FOUND)
        writeEnabled(HorizonContent.gameHome(home), id, on)
    }

    /** Menghapus berkas .hmod; pilihannya di horizon-mods.txt ikut dilepas. */
    fun delete(home: File, fileName: String): Boolean {
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
            return if (BUNDLED.any { it.id == id }) id else null
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
        return try {
            val manifest = inspect(staged, name)
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
    internal fun inspect(file: File, name: String): Manifest {
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
                    if (raw == WASM) {
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
