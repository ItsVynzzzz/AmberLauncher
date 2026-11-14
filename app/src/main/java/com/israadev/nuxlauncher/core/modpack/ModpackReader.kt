package com.israadev.nuxlauncher.core.modpack

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.israadev.nuxlauncher.core.forge.ForgeFlavor
import com.israadev.nuxlauncher.core.forge.ForgeService
import com.israadev.nuxlauncher.core.mods.CurseForgeMapper
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

/**
 * Membaca satu berkas modpack (.mrpack Modrinth atau .zip CurseForge) untuk mengetahui instance apa yang
 * dibutuhkannya: versi Minecraft, loader, dan versi loader. Dipakai layar Buat Instance supaya instance
 * bisa dibuat langsung dari modpack, tanpa pengguna membuat instance kosong dulu.
 *
 * Hanya membaca daftar isi arsip (tidak mengunduh dan tidak mengekstrak apa pun), tanpa Android, jadi
 * bisa diuji di JVM biasa. Pemasangan isinya tetap dikerjakan pemasang modpack yang sudah ada.
 */
object ModpackReader {

    enum class Source { MODRINTH, CURSEFORGE }

    /** Isi penting sebuah modpack. [loader] salah satu "fabric", "forge", "neoforge", atau "vanilla". */
    class Info(
        val title: String,
        val version: String,
        val minecraft: String,
        val loader: String,
        val loaderVersion: String,
        /** Jumlah berkas yang terdaftar di modpack (mod, resource pack, dan sebagainya). */
        val fileCount: Int,
        val source: Source
    )

    /** Modpack tidak bisa dipakai; pesannya aman ditampilkan ke pengguna. */
    class ReadException(message: String) : Exception(message)

    /** Batas ukuran berkas daftar modpack yang dibaca ke memori (daftar aslinya hanya puluhan KB). */
    private const val MAX_INDEX_BYTES = 16 * 1024 * 1024

    private const val MSG_NOT_PACK =
        "Berkas ini bukan modpack yang dikenali. Pakai file .mrpack (Modrinth) atau .zip modpack CurseForge."
    private const val MSG_QUILT =
        "Modpack ini membutuhkan Quilt, sedangkan AmberLauncher saat ini hanya mendukung Fabric, Forge, dan NeoForge."
    private const val MSG_NO_MC = "Modpack ini tidak menyebut versi Minecraft-nya."
    private const val MSG_NO_LOADER_VERSION = "Modpack ini tidak menyebut versi loader-nya."

    /** Membaca [file]. Melempar [ReadException] bila bukan modpack yang bisa dipasang AmberLauncher. */
    fun read(file: File): Info {
        val zip = try {
            ZipFile(file)
        } catch (e: Exception) {
            throw ReadException(MSG_NOT_PACK)
        }
        zip.use {
            val indexEntry = zip.getEntry("modrinth.index.json")
                ?: zip.entries().asSequence().firstOrNull { it.name.endsWith("modrinth.index.json") }
            if (indexEntry != null) {
                val root = parseObject(zip, indexEntry) ?: throw ReadException(MSG_NOT_PACK)
                return fromModrinth(root, file)
            }
            val manifestEntry = zip.getEntry("manifest.json")
            if (manifestEntry != null) {
                val root = parseObject(zip, manifestEntry)
                val pack = root?.let { CurseForgeMapper.parseManifest(it) }
                if (pack != null) return fromCurseForge(pack, file)
            }
        }
        throw ReadException(MSG_NOT_PACK)
    }

    private fun fromModrinth(root: JsonObject, file: File): Info {
        val deps = root.get("dependencies")?.takeIf { it.isJsonObject }?.asJsonObject
        val minecraft = deps?.text("minecraft").orEmpty()
        if (minecraft.isEmpty()) throw ReadException(MSG_NO_MC)

        // Satu modpack hanya punya satu loader; bila ada beberapa, urutannya sama dengan pemasang modpack
        // (Fabric lebih dulu), supaya instance yang dibuat pasti lolos pemeriksaan pemasangnya.
        val loader: String
        val loaderVersion: String
        when {
            deps != null && deps.has("fabric-loader") -> { loader = "fabric"; loaderVersion = deps.text("fabric-loader").orEmpty() }
            deps != null && deps.has("neoforge") -> { loader = "neoforge"; loaderVersion = deps.text("neoforge").orEmpty() }
            deps != null && deps.has("forge") -> { loader = "forge"; loaderVersion = deps.text("forge").orEmpty() }
            deps != null && deps.has("quilt-loader") -> throw ReadException(MSG_QUILT)
            else -> { loader = "vanilla"; loaderVersion = "" }
        }

        val files = root.get("files")?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0
        return checked(
            Info(
                title = root.text("name").orEmpty().ifEmpty { file.nameWithoutExtension },
                version = root.text("versionId").orEmpty(),
                minecraft = minecraft,
                loader = loader,
                loaderVersion = loaderVersion,
                fileCount = files,
                source = Source.MODRINTH
            )
        )
    }

    private fun fromCurseForge(pack: CurseForgeMapper.PackManifest, file: File): Info {
        val minecraft = pack.minecraftVersion.trim()
        if (minecraft.isEmpty()) throw ReadException(MSG_NO_MC)
        val loader = when (val id = pack.loader) {
            null -> "vanilla"
            "fabric", "forge", "neoforge" -> id
            "quilt" -> throw ReadException(MSG_QUILT)
            else -> throw ReadException(
                "Modpack ini membutuhkan ${id.replaceFirstChar { it.uppercase() }}, " +
                    "sedangkan AmberLauncher saat ini hanya mendukung Fabric, Forge, dan NeoForge."
            )
        }
        return checked(
            Info(
                title = pack.name.trim().ifEmpty { file.nameWithoutExtension },
                version = pack.version.trim(),
                minecraft = minecraft,
                loader = loader,
                loaderVersion = if (loader == "vanilla") "" else pack.loaderVersion.trim(),
                fileCount = pack.files.size,
                source = Source.CURSEFORGE
            )
        )
    }

    /**
     * Memilih versi loader dari [available] (yang ditawarkan server untuk versi Minecraft itu) yang sesuai
     * dengan [wanted] dari modpack untuk Minecraft [mcVersion]. Cocok persis lebih dulu, lalu cocok setelah
     * imbuhan versi Minecraft dibuang ("1.7.10-10.13.4.1614-1.7.10" dengan "10.13.4.1614"). Null bila tidak
     * ada yang cocok; pemanggil lalu memakai versi bawaan dan memberi tahu pengguna.
     */
    fun matchLoaderVersion(wanted: String, available: List<String>, mcVersion: String): String? {
        val w = wanted.trim()
        if (w.isEmpty()) return null
        available.firstOrNull { it.equals(w, ignoreCase = true) }?.let { return it }
        val mc = mcVersion.trim()
        // Buang imbuhan versi Minecraft di depan/belakang, lalu bandingkan intinya saja
        fun core(v: String): String {
            var s = v.trim()
            if (mc.isNotEmpty()) {
                if (s.startsWith("$mc-", ignoreCase = true)) s = s.substring(mc.length + 1)
                if (s.endsWith("-$mc", ignoreCase = true)) s = s.substring(0, s.length - mc.length - 1)
            }
            return s
        }
        val wantedCore = core(w)
        if (wantedCore.isEmpty()) return null
        return available.firstOrNull { core(it).equals(wantedCore, ignoreCase = true) }
    }

    /** Pemeriksaan terakhir yang sama untuk kedua format: loader harus punya versi dan bisa dipasang Amber. */
    private fun checked(info: Info): Info {
        if (info.loader != "vanilla" && info.loaderVersion.isEmpty()) throw ReadException(MSG_NO_LOADER_VERSION)
        val flavor = ForgeFlavor.of(info.loader)
        if (flavor != null && !ForgeService.isMcSupported(info.minecraft, flavor)) {
            throw ReadException(
                "Modpack ini membutuhkan ${flavor.displayName} untuk Minecraft ${info.minecraft}, " +
                    "yang belum didukung AmberLauncher."
            )
        }
        return info
    }

    private fun parseObject(zip: ZipFile, entry: java.util.zip.ZipEntry): JsonObject? {
        return try {
            val bytes = readLimited(zip, entry)
            val root = JsonParser.parseString(String(bytes, Charsets.UTF_8))
            if (root.isJsonObject) root.asJsonObject else null
        } catch (e: Exception) {
            null
        }
    }

    /** Membaca satu entri zip ke memori, berhenti dengan galat bila lebih besar dari [MAX_INDEX_BYTES]. */
    private fun readLimited(zip: ZipFile, entry: java.util.zip.ZipEntry): ByteArray {
        val out = ByteArrayOutputStream()
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (out.size() + read > MAX_INDEX_BYTES) throw java.io.IOException("Daftar modpack terlalu besar")
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    /** Nilai teks properti [key] bila ada dan berupa nilai biasa; selain itu null. */
    private fun JsonObject.text(key: String): String? {
        val element: JsonElement = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return element.asString.trim().takeIf { it.isNotEmpty() }
    }
}
