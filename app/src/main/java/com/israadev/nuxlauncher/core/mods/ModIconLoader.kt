package com.israadev.nuxlauncher.core.mods

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Mengambil gambar kecil (ikon) tiap mod / resource pack dari dalam filenya sendiri, tanpa internet.
 * - Mod Fabric/Quilt: field "icon" di fabric.mod.json / quilt.mod.json.
 * - Mod Forge/NeoForge: "logoFile" di mods.toml, atau mcmod.info untuk mod lama.
 * - Resource pack & datapack: pack.png di akar zip atau folder.
 * Hasil diperkecil (maks 96px), disimpan di cache memori kecil dan di folder cache supaya daftar
 * dengan ratusan mod tidak membuka ulang file jar setiap kali digulir. Bila tidak ada gambar,
 * hasilnya null dan tampilan memakai inisial nama.
 */
object ModIconLoader {

    private const val MAX_PX = 96
    private const val MAX_BYTES = 1_500_000L

    private val memory = LruCache<String, Bitmap>(64)
    private val missing = ConcurrentHashMap.newKeySet<String>()

    private fun keyOf(file: File): String =
        "${file.absolutePath}|${file.length()}|${file.lastModified()}"

    private fun sha1(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Hasil dari memori (tanpa membuka file), atau null bila belum pernah dimuat. */
    fun peek(path: String): Bitmap? {
        if (path.isEmpty()) return null
        val file = File(path)
        if (!file.exists()) return null
        return memory.get(keyOf(file))
    }

    suspend fun load(context: Context, file: File, itemType: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val key = keyOf(file)
        val cached = memory.get(key)
        if (cached != null) return@withContext cached
        if (missing.contains(key)) return@withContext null

        val dir = File(context.cacheDir, "mod_icons")
        if (!dir.exists()) dir.mkdirs()
        val hash = sha1(key)
        val pngFile = File(dir, "$hash.png")
        val noneFile = File(dir, "$hash.none")

        if (pngFile.exists()) {
            val fromDisk = decodeScaled(pngFile.readBytes())
            if (fromDisk != null) {
                memory.put(key, fromDisk)
                return@withContext fromDisk
            }
        }
        if (noneFile.exists()) {
            missing.add(key)
            return@withContext null
        }

        val bytes = try {
            extractIconBytes(file, itemType)
        } catch (_: Exception) {
            null
        }
        val bitmap = if (bytes != null) decodeScaled(bytes) else null
        if (bitmap == null) {
            missing.add(key)
            try {
                noneFile.createNewFile()
            } catch (_: Exception) {
            }
            return@withContext null
        }

        memory.put(key, bitmap)
        try {
            FileOutputStream(pngFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        } catch (_: Exception) {
        }
        bitmap
    }

    private fun extractIconBytes(file: File, itemType: String): ByteArray? {
        if (file.isDirectory) {
            val packPng = File(file, "pack.png")
            return if (packPng.isFile && packPng.length() < MAX_BYTES) packPng.readBytes() else null
        }
        if (!file.isFile) return null
        return ZipFile(file).use { zip -> findIconInZip(zip, itemType) }
    }

    private fun readEntry(zip: ZipFile, path: String): ByteArray? {
        val entry = zip.getEntry(path.trimStart('/')) ?: return null
        if (entry.isDirectory || entry.size > MAX_BYTES) return null
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    private fun readText(zip: ZipFile, path: String): String? {
        val entry = zip.getEntry(path) ?: return null
        if (entry.isDirectory || entry.size > 512_000L) return null
        return zip.getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private fun findIconInZip(zip: ZipFile, itemType: String): ByteArray? {
        if (itemType != "mods") {
            return readEntry(zip, "pack.png")
        }

        val candidates = mutableListOf<String>()

        readText(zip, "fabric.mod.json")?.let { text ->
            try {
                val root = JsonParser.parseString(text).asJsonObject
                candidates.addAll(iconPaths(root.get("icon")))
            } catch (_: Exception) {
            }
        }
        readText(zip, "quilt.mod.json")?.let { text ->
            try {
                val root = JsonParser.parseString(text).asJsonObject
                val metadata = root.getAsJsonObject("quilt_loader")?.getAsJsonObject("metadata")
                candidates.addAll(iconPaths(metadata?.get("icon")))
            } catch (_: Exception) {
            }
        }
        for (tomlPath in listOf("META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
            readText(zip, tomlPath)?.let { text ->
                val match = Regex("logoFile\\s*=\\s*[\"']([^\"']+)[\"']").find(text)
                if (match != null) candidates.add(match.groupValues[1])
            }
        }
        readText(zip, "mcmod.info")?.let { text ->
            val match = Regex("\"logoFile\"\\s*:\\s*\"([^\"]+)\"").find(text)
            if (match != null) candidates.add(match.groupValues[1])
        }
        candidates.addAll(listOf("pack.png", "icon.png", "logo.png"))

        for (candidate in candidates) {
            val bytes = readEntry(zip, candidate)
            if (bytes != null) return bytes
        }
        return null
    }

    /** Field "icon" bisa berupa satu path atau peta ukuran -> path; ambil yang terbesar dulu. */
    private fun iconPaths(element: JsonElement?): List<String> {
        if (element == null || element.isJsonNull) return emptyList()
        if (element.isJsonPrimitive) return listOf(element.asString)
        if (element.isJsonObject) {
            return element.asJsonObject.entrySet()
                .sortedByDescending { it.key.toIntOrNull() ?: 0 }
                .mapNotNull { entry ->
                    val value = entry.value
                    if (value.isJsonPrimitive) value.asString else null
                }
        }
        return emptyList()
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_PX && bounds.outHeight / (sample * 2) >= MAX_PX) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        val longestSide = max(bitmap.width, bitmap.height)
        if (longestSide <= MAX_PX) return bitmap
        val ratio = MAX_PX.toFloat() / longestSide
        val newWidth = max(1, (bitmap.width * ratio).roundToInt())
        val newHeight = max(1, (bitmap.height * ratio).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
