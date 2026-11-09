package com.israadev.nuxlauncher.core.mods

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.horizon.HorizonContent
import com.israadev.nuxlauncher.core.horizon.HorizonEngine
import com.israadev.nuxlauncher.core.horizon.HorizonMods
import com.israadev.nuxlauncher.core.horizon.HorizonRegistry
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.models.*
import com.israadev.nuxlauncher.core.utils.SafePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

data class WorldInfo(
    val folderName: String,
    val displayName: String,
    val lastPlayed: Long = 0L,
    val iconFile: File? = null
)

/** Modpack tidak cocok dengan instance atau loader yang didukung; pesannya aman ditampilkan ke pengguna. */
private class ModpackIncompatibleException(message: String) : Exception(message)

/** Satu berkas di dalam modrinth.index.json yang sudah lolos pemeriksaan path dan alamat. */
private data class MrpackFileEntry(
    val url: String,
    val relPath: String,
    val hashes: Map<String, String>
)

object NuxModManager {

    private val gson = Gson()
    // Dipakai juga oleh CurseForgeApi (satu klien, satu kolam sambungan)
    internal val httpClient = OkHttpClient.Builder()
        .dns(com.israadev.nuxlauncher.core.network.NuxDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        // Jangan ikuti pengalihan dari HTTPS ke HTTP polos (isi unduhan bisa diganti di jalan)
        .followSslRedirects(false)
        .build()

    internal const val USER_AGENT = "AmberLauncher/1.0 (+https://github.com/ItsVynzzzz/AmberLauncher)"

    /** Batas total isi yang boleh diekstrak dari satu modpack (cegah arsip "bom" yang memenuhi penyimpanan). */
    private const val MAX_MODPACK_EXTRACT_BYTES = 1_500L * 1024L * 1024L
    private const val MODRINTH_API_BASE = "https://api.modrinth.com/v2"

    /** Sumber unduhan di layar Mod. Horizon hanya untuk instance mesin Rust (mod, shader asli, resource pack). */
    const val SOURCE_MODRINTH = "modrinth"
    const val SOURCE_CURSEFORGE = "curseforge"
    const val SOURCE_HORIZON = "horizon"

    /** Loader yang didukung launcher saat ini. Ganti di sini bila nanti loader lain ditambahkan. */
    private const val SUPPORTED_LOADER = "fabric"
    private val SEARCH_SORT_INDEXES = setOf("relevance", "downloads", "follows", "newest", "updated")

    /**
     * Loader yang dipakai untuk menyaring mod dan modpack. Instance Vanilla (atau tanpa instance)
     * ikut disaring ke loader yang didukung, supaya pengguna tidak memasang mod Forge yang pasti gagal.
     */
    internal fun effectiveLoader(instance: Instance?): String {
        val loader = instance?.loader?.trim()?.lowercase().orEmpty()
        return if (loader.isEmpty() || loader == "vanilla") SUPPORTED_LOADER else loader
    }

    /**
     * Membaca daftar dunia (saves) yang ada di dalam instance
     */
    fun getInstanceWorlds(context: Context, instance: Instance): List<WorldInfo> {
        val gameDir = InstanceManager.getInstanceGameDir(context, instance)
        val savesDir = File(gameDir, "saves")
        if (!savesDir.exists() || !savesDir.isDirectory) return emptyList()

        val dirs = savesDir.listFiles { file -> file.isDirectory } ?: return emptyList()
        return dirs.map { worldFolder ->
            val iconFile = File(worldFolder, "icon.png").takeIf { it.exists() }
            val levelDat = File(worldFolder, "level.dat")
            val lastPlayed = if (levelDat.exists()) levelDat.lastModified() else worldFolder.lastModified()
            WorldInfo(
                folderName = worldFolder.name,
                displayName = worldFolder.name,
                lastPlayed = lastPlayed,
                iconFile = iconFile
            )
        }.sortedByDescending { it.lastPlayed }
    }

    /**
     * Menghasilkan folder target sesuai jenis addon dan instance Minecraft
     */
    fun getTargetDir(context: Context, instance: Instance, itemType: String, worldName: String? = null): File {
        val gameDir = InstanceManager.getInstanceGameDir(context, instance)
        val dir = when (itemType) {
            "datapacks" -> {
                if (!worldName.isNullOrBlank()) {
                    File(gameDir, "saves/$worldName/datapacks")
                } else {
                    File(gameDir, "datapacks")
                }
            }
            "resourcepacks" -> File(gameDir, "resourcepacks")
            "shaderpacks" -> File(gameDir, "shaderpacks")
            "modpacks" -> File(gameDir, "modpacks")
            else -> File(gameDir, "mods")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Membaca semua item yang telah terpasang di dalam instance untuk jenis addon tertentu
     */
    suspend fun getInstalledItems(
        context: Context,
        instance: Instance,
        itemType: String,
        worldName: String? = null
    ): List<InstalledModItem> = withContext(Dispatchers.IO) {
        // Mesin Rust: shader dan resource pack ada di folder mesin, bukan di folder instance Java
        if (HorizonEngine.isEngine(instance.loader)) {
            return@withContext HorizonContent.installed(HorizonEngine.engineHome(context), itemType)
        }
        val targetDir = getTargetDir(context, instance, itemType, worldName)
        val gameDir = InstanceManager.getInstanceGameDir(context, instance)
        if (!targetDir.exists() || !targetDir.isDirectory) return@withContext emptyList()

        val files = targetDir.listFiles() ?: return@withContext emptyList()
        val result = mutableListOf<InstalledModItem>()

        for (file in files) {
            if (file.isDirectory && itemType != "datapacks") continue
            // Sisa unduhan yang terputus bukan item yang terpasang
            if (file.isFile && file.name.endsWith(".part")) continue

            val originalFilename = file.name
            val isEnabled = !originalFilename.endsWith(".disabled")
            val cleanFilename = if (!isEnabled) originalFilename.removeSuffix(".disabled") else originalFilename
            val fileStem = File(cleanFilename).nameWithoutExtension

            var title = fileStem
            var version = "Lokal"
            var description = cleanFilename

            // Coba ekstrak metadata dari file JAR mod jika bertipe mods
            if (itemType == "mods" && cleanFilename.endsWith(".jar", ignoreCase = true) && file.isFile) {
                try {
                    ZipFile(file).use { zip ->
                        // Fabric (fabric.mod.json), Forge (mods.toml), dan NeoForge (neoforge.mods.toml)
                        ModMetadata.read(zip)?.let { info ->
                            info.name?.takeIf { it.isNotBlank() }?.let { title = it }
                            info.version?.takeIf { it.isNotBlank() }?.let { version = it }
                            info.description?.takeIf { it.isNotBlank() }?.let { description = it }
                        }
                    }
                } catch (_: Exception) {
                    // Abaikan jika JAR tidak dapat diekstrak, fallback ke nama file
                }
            } else if (itemType == "modpacks" && (cleanFilename.endsWith(".mrpack", ignoreCase = true) || cleanFilename.endsWith(".zip", ignoreCase = true)) && file.isFile) {
                val manifest = loadModpackManifest(gameDir, cleanFilename)
                if (manifest != null) {
                    if (manifest.modpackName.isNotBlank()) title = manifest.modpackName
                    if (manifest.version.isNotBlank()) version = manifest.version
                    val modCount = manifest.installedFiles.count { it.startsWith("mods/") || it.endsWith(".jar", ignoreCase = true) }
                    description = if (modCount > 0) "$modCount mod terpasang" else cleanFilename
                } else {
                    try {
                        ZipFile(file).use { zip ->
                            val indexEntry = zip.getEntry("modrinth.index.json")
                                ?: zip.entries().asSequence().firstOrNull { it.name.endsWith("modrinth.index.json") }
                            if (indexEntry != null) {
                                zip.getInputStream(indexEntry).bufferedReader().use { reader ->
                                    val json = JsonParser.parseReader(reader).asJsonObject
                                    if (json.has("name")) title = json.get("name").asString
                                    if (json.has("versionId")) version = json.get("versionId").asString
                                    if (json.has("summary")) description = json.get("summary").asString
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            result.add(
                InstalledModItem(
                    id = cleanFilename,
                    filename = cleanFilename,
                    originalFilename = originalFilename,
                    name = title,
                    description = description,
                    version = version,
                    category = itemType.replaceFirstChar { it.uppercase() },
                    isEnabled = isEnabled,
                    sizeBytes = if (file.isFile) file.length() else 0L,
                    itemType = itemType,
                    filePath = file.absolutePath
                )
            )
        }

        result.sortedBy { it.name.lowercase() }
    }

    /**
     * Mengubah status aktif/nonaktif mod dengan menambah atau melepas ekstensi .disabled
     */
    suspend fun toggleItemEnabled(
        context: Context,
        instance: Instance,
        itemType: String,
        item: InstalledModItem,
        worldName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetDir = getTargetDir(context, instance, itemType, worldName)
            val currentFile = File(targetDir, item.originalFilename)
            val gameDir = InstanceManager.getInstanceGameDir(context, instance)

            if (itemType == "modpacks") {
                val cleanFilename = if (item.originalFilename.endsWith(".disabled")) {
                    item.originalFilename.removeSuffix(".disabled")
                } else {
                    item.originalFilename
                }

                // Ambil daftar file modpack dari manifest tracking atau arsip
                val manifest = loadModpackManifest(gameDir, cleanFilename)
                val fileList = manifest?.installedFiles ?: getModpackInstalledFilesFromArchive(currentFile)

                if (item.isEnabled) {
                    // Dinonaktifkan: rename semua mod jar milik modpack menjadi .disabled
                    for (relPath in fileList) {
                        if (relPath.startsWith("mods/") || relPath.endsWith(".jar", ignoreCase = true)) {
                            val activeFile = resolveModpackFile(gameDir, relPath) ?: continue
                            val disabledFile = resolveModpackFile(gameDir, "$relPath.disabled") ?: continue
                            if (activeFile.exists()) {
                                activeFile.renameTo(disabledFile)
                            }
                        }
                    }
                    val newFile = File(targetDir, "$cleanFilename.disabled")
                    if (currentFile.exists() && currentFile.name != newFile.name) {
                        currentFile.renameTo(newFile)
                    }
                } else {
                    // Diaktifkan: rename semua mod jar .disabled milik modpack kembali ke normal
                    for (relPath in fileList) {
                        if (relPath.startsWith("mods/") || relPath.endsWith(".jar", ignoreCase = true)) {
                            val disabledFile = resolveModpackFile(gameDir, "$relPath.disabled") ?: continue
                            val activeFile = resolveModpackFile(gameDir, relPath) ?: continue
                            if (disabledFile.exists()) {
                                disabledFile.renameTo(activeFile)
                            }
                        }
                    }
                    val newFile = File(targetDir, cleanFilename)
                    if (currentFile.exists() && currentFile.name != newFile.name) {
                        currentFile.renameTo(newFile)
                    }
                }
                return@withContext true
            }

            if (!currentFile.exists()) return@withContext false

            val newFilename = if (item.isEnabled) {
                "${item.filename}.disabled"
            } else {
                item.filename
            }

            val newFile = File(targetDir, newFilename)
            currentFile.renameTo(newFile)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Menghapus file mod / addon dari instance
     */
    suspend fun deleteItem(
        context: Context,
        instance: Instance,
        itemType: String,
        item: InstalledModItem,
        worldName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (HorizonEngine.isEngine(instance.loader)) {
            return@withContext HorizonContent.delete(HorizonEngine.engineHome(context), itemType, item.originalFilename)
        }
        try {
            val targetDir = getTargetDir(context, instance, itemType, worldName)
            val file = File(targetDir, item.originalFilename)
            val gameDir = InstanceManager.getInstanceGameDir(context, instance)

            if (itemType == "modpacks") {
                val cleanFilename = if (item.originalFilename.endsWith(".disabled")) {
                    item.originalFilename.removeSuffix(".disabled")
                } else {
                    item.originalFilename
                }

                // 1. Baca manifest modpack (atau fallback dari arsip zip jika metadata belum ada)
                val manifest = loadModpackManifest(gameDir, cleanFilename)
                val fileList = (manifest?.installedFiles ?: getModpackInstalledFilesFromArchive(file)).toMutableSet()

                // 2. Hapus HANYA file-file yang terpasang oleh modpack ini (baik aktif maupun disabled)
                // Daftar ini bisa berasal dari berkas yang diubah orang lain: hanya path di dalam folder instance
                // (dan bukan dunia/saves) yang boleh dihapus.
                for (relPath in fileList) {
                    val activeFile = resolveModpackFile(gameDir, relPath)
                    if (activeFile != null && activeFile.exists()) {
                        if (activeFile.isDirectory) activeFile.deleteRecursively() else activeFile.delete()
                    }
                    val disabledFile = resolveModpackFile(gameDir, "$relPath.disabled")
                    if (disabledFile != null && disabledFile.exists()) {
                        if (disabledFile.isDirectory) disabledFile.deleteRecursively() else disabledFile.delete()
                    }
                }

                // 3. Hapus file metadata manifest
                val metaFile = getModpackMetadataFile(gameDir, cleanFilename)
                if (metaFile.exists()) {
                    metaFile.delete()
                }

                // 4. Hapus file modpack itu sendiri (baik .mrpack maupun .mrpack.disabled)
                if (file.exists()) {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
                val cleanArchive = File(targetDir, cleanFilename)
                if (cleanArchive.exists()) cleanArchive.delete()
                val disabledArchive = File(targetDir, "$cleanFilename.disabled")
                if (disabledArchive.exists()) disabledArchive.delete()

                true
            } else {
                if (file.exists()) {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                } else {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Mengimpor file lokal dari HP pengguna ke dalam folder instance
     */
    suspend fun importLocalFile(
        context: Context,
        instance: Instance,
        itemType: String,
        uri: Uri,
        worldName: String? = null,
        onProgress: ((statusText: String, current: Int, total: Int) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            var fileName = "imported_addon"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }
            // Nama berkas datang dari aplikasi lain: buang bagian folder ("../") sebelum dipakai sebagai path
            fileName = SafePaths.fileName(fileName)

            // Mesin Rust: diperiksa dengan aturan mesin lalu masuk ke folder mesin
            if (HorizonEngine.isEngine(instance.loader)) {
                return@withContext importEngineFile(context, uri, itemType, fileName)
            }

            // Validasi format ekstensi file sesuai tipe
            val lowerName = fileName.lowercase()
            when (itemType) {
                "mods" -> {
                    if (!lowerName.endsWith(".jar")) {
                        return@withContext Result.failure(IllegalArgumentException("File mod harus berformat .jar"))
                    }
                }
                "modpacks" -> {
                    if (!lowerName.endsWith(".mrpack") && !lowerName.endsWith(".zip")) {
                        return@withContext Result.failure(IllegalArgumentException("File modpack harus berformat .mrpack atau .zip"))
                    }
                }
                "shaderpacks", "resourcepacks", "datapacks" -> {
                    if (!lowerName.endsWith(".zip")) {
                        return@withContext Result.failure(IllegalArgumentException("File $itemType harus berformat .zip"))
                    }
                }
            }

            val targetDir = getTargetDir(context, instance, itemType, worldName)
            val destFile = File(targetDir, fileName)

            onProgress?.invoke("Menyalin file $fileName...", 0, 0)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Gagal membaca file sumber"))

            // Jika modpack (.mrpack atau .zip), baca daftar mod dan unduh semua file seperti versi Windows!
            if (itemType == "modpacks" && (lowerName.endsWith(".mrpack") || lowerName.endsWith(".zip"))) {
                onProgress?.invoke("Membaca daftar modpack...", 0, 0)
                processMrpackFile(instance, destFile, context) { statusText, cur, tot ->
                    onProgress?.invoke(statusText, cur, tot)
                }
            }

            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // MESIN RUST (Horizon): hanya Shader dan Resource Pack, langsung ke folder dan pengaturan mesin
    // ---------------------------------------------------------------------------------------------

    /**
     * Memilih atau melepas satu shader / resource pack lewat berkas pengaturan mesin (bukan ganti nama berkas).
     * Berlaku saat game dibuka lagi. Galatnya berisi pesan yang aman ditampilkan ke pengguna.
     */
    suspend fun toggleEngineItem(context: Context, itemType: String, item: InstalledModItem): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Mesin tidak menjalankan mod di APK 32-bit; mematikan tetap boleh
                if (itemType == HorizonContent.TYPE_MODS && !item.isEnabled && !android.os.Process.is64Bit()) {
                    throw java.io.IOException(HorizonMods.MSG_NEED_64)
                }
                HorizonContent.setEnabled(
                    HorizonEngine.engineHome(context),
                    itemType,
                    item.originalFilename,
                    !item.isEnabled
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Mengunduh satu versi untuk mesin Rust: hanya berkas .zip yang punya sidik jari, diperiksa dengan aturan
     * mesin, lalu dipindah ke folder mesin. Dependensi sengaja diabaikan (shader dan resource pack tidak
     * memakainya). Berkas yang sudah ada dengan nama sama dilewati.
     */
    private fun downloadForEngine(
        context: Context,
        version: ModrinthVersion,
        itemType: String,
        onProgress: (String) -> Unit
    ): Result<List<String>> {
        // Mod selalu dari katalog Horizon; shader dan resource pack bila versinya berasal dari katalog itu
        if (itemType == HorizonContent.TYPE_MODS || HorizonRegistry.isRegistryVersion(version)) {
            return downloadHorizonFile(context, version, itemType, onProgress)
        }
        var staged: File? = null
        return try {
            if (!HorizonContent.supports(itemType)) throw java.io.IOException(HorizonContent.MSG_TYPE)
            val picked = HorizonContent.pickFile(version) ?: throw java.io.IOException(HorizonContent.MSG_NO_ZIP)
            if (picked.size > HorizonContent.MAX_FILE_BYTES) throw java.io.IOException(HorizonContent.MSG_TOO_BIG)
            if (!HorizonContent.hasChecksum(picked)) throw java.io.IOException(HorizonContent.MSG_NO_HASH)

            // Nama berkas datang dari respons server: pastikan tidak berisi folder ("../")
            val name = SafePaths.fileName(picked.filename)
            val home = HorizonEngine.engineHome(context)
            if (HorizonContent.exists(home, itemType, name)) {
                Result.success(emptyList())
            } else {
                onProgress("Mengunduh $name...")
                val stageDir = File(context.cacheDir, "horizon-dl")
                if (!stageDir.isDirectory && !stageDir.mkdirs()) throw java.io.IOException(HorizonContent.MSG_SAVE)
                val target = File(stageDir, name)
                staged = target
                downloadFile(picked.url, target)
                verifyHashes(target, picked.hashes)
                onProgress("Memeriksa $name...")
                val finalName = HorizonContent.install(target, home, itemType, name).getOrThrow()
                Result.success(listOf(finalName))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            staged?.delete()
        }
    }

    /**
     * Mengunduh satu berkas dari katalog Horizon (mod, shader asli, atau resource pack): alamat harus rilis GitHub,
     * ukuran dan SHA-256 harus sama dengan katalog, lalu berkasnya diperiksa dengan aturan mesin sebelum masuk ke
     * folder tujuan. Dependensi tidak ada. Mod yang sama diganti (id manifest), shader dan resource pack yang
     * namanya sudah ada dilewati.
     */
    private fun downloadHorizonFile(
        context: Context,
        version: ModrinthVersion,
        itemType: String,
        onProgress: (String) -> Unit
    ): Result<List<String>> {
        var staged: File? = null
        return try {
            val kind = HorizonRegistry.kindFor(itemType) ?: throw java.io.IOException(HorizonContent.MSG_TYPE)
            val extension = HorizonRegistry.extensionOf(kind) ?: throw java.io.IOException(HorizonContent.MSG_TYPE)
            val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull()
                ?: throw java.io.IOException(HorizonRegistry.MSG_NO_FILE)
            val name = SafePaths.fileName(file.filename)
            val sha256 = file.hashes?.get("sha256")?.trim()?.lowercase().orEmpty()
            if (!name.endsWith(extension)) throw java.io.IOException(HorizonRegistry.MSG_NO_FILE)
            if (!HorizonRegistry.acceptedUrl(file.url, name)) throw java.io.IOException(HorizonRegistry.MSG_URL)
            if (!HorizonRegistry.isSha256(sha256)) throw java.io.IOException(HorizonRegistry.MSG_HASH)
            if (file.size <= 0L || file.size > HorizonRegistry.maxBytes(kind)) {
                throw java.io.IOException(if (kind == HorizonRegistry.KIND_MOD) HorizonMods.MSG_TOO_BIG else HorizonContent.MSG_TOO_BIG)
            }

            val home = HorizonEngine.engineHome(context)
            if (kind != HorizonRegistry.KIND_MOD && HorizonContent.exists(home, itemType, name)) {
                Result.success(emptyList())
            } else {
                val stageDir = File(context.cacheDir, "horizon-dl")
                if (!stageDir.isDirectory && !stageDir.mkdirs()) throw java.io.IOException(HorizonContent.MSG_SAVE)
                val target = File(stageDir, name)
                staged = target
                onProgress("Mengunduh $name...")
                downloadFile(file.url, target, file.size)
                if (target.length() != file.size) throw java.io.IOException(HorizonRegistry.MSG_SIZE)
                verifyHashes(target, file.hashes)
                onProgress("Memeriksa $name...")
                val finalName = HorizonContent.install(target, home, itemType, name).getOrThrow()
                Result.success(listOf(finalName))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            staged?.delete()
        }
    }

    /** Katalog Horizon disimpan di memori 10 menit supaya mencari dan membuka detail tidak mengunduhnya terus. */
    @Volatile
    private var horizonIndexCache: Pair<Long, HorizonRegistry.Index>? = null
    private const val HORIZON_INDEX_TTL_MS = 10L * 60L * 1000L

    private fun horizonIndex(): HorizonRegistry.Index {
        val cached = horizonIndexCache
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.first < HORIZON_INDEX_TTL_MS) return cached.second
        val index = try {
            val request = Request.Builder()
                .url(HorizonRegistry.INDEX_URL)
                .header("User-Agent", USER_AGENT)
                .build()
            val text = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException(HorizonRegistry.MSG_INDEX)
                val body = response.body ?: throw java.io.IOException(HorizonRegistry.MSG_INDEX)
                val out = java.io.ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (out.size() + read > HorizonRegistry.MAX_INDEX_BYTES) {
                            throw java.io.IOException(HorizonRegistry.MSG_INDEX)
                        }
                        out.write(buffer, 0, read)
                    }
                }
                out.toString(Charsets.UTF_8.name())
            }
            HorizonRegistry.parse(text)
        } catch (e: java.io.IOException) {
            // Galat jaringan dari pustaka berbahasa Inggris; ganti dengan pesan yang sudah diterjemahkan
            throw if (e.message == HorizonRegistry.MSG_NEWER) e else java.io.IOException(HorizonRegistry.MSG_INDEX)
        }
        horizonIndexCache = now to index
        return index
    }

    /** Impor berkas dari HP untuk mesin Rust: disalin ke berkas sementara (paling besar 256 MB), diperiksa, lalu dipasang. */
    private fun importEngineFile(context: Context, uri: Uri, itemType: String, fileName: String): Result<String> {
        var staged: File? = null
        return try {
            if (!HorizonContent.supports(itemType)) throw java.io.IOException(HorizonContent.MSG_TYPE)
            val stageDir = File(context.cacheDir, "horizon-dl")
            if (!stageDir.isDirectory && !stageDir.mkdirs()) throw java.io.IOException(HorizonContent.MSG_SAVE)
            val target = File(stageDir, fileName)
            staged = target
            val input = context.contentResolver.openInputStream(uri)
                ?: throw java.io.IOException("Gagal membuka berkas yang dipilih")
            input.use { HorizonContent.copyBounded(it, target) }
            HorizonContent.install(target, HorizonEngine.engineHome(context), itemType, fileName)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            staged?.delete()
        }
    }

    /**
     * Pencarian Modrinth dengan Auto-Version dan Auto-Loader filter
     */
    suspend fun searchProjects(
        query: String,
        itemType: String,
        instance: Instance?,
        category: String? = null,
        filterByGameVersion: Boolean = true,
        page: Int = 1,
        limit: Int = 18,
        sortIndex: String = "relevance",
        filterByLoader: Boolean = true,
        source: String = SOURCE_MODRINTH
    ): Result<ModrinthSearchResponse> = withContext(Dispatchers.IO) {
        // Mesin Rust: Modrinth atau katalog Horizon, tanpa CurseForge dan tanpa saringan versi Minecraft
        val engineSearch = HorizonEngine.isEngine(instance?.loader)
        // Mod untuk mesin Rust selalu dari katalog Horizon; shader dan resource pack dari Horizon bila sumber itu dipilih
        val horizonKind = if (engineSearch && (itemType == HorizonContent.TYPE_MODS || source == SOURCE_HORIZON)) {
            HorizonRegistry.kindFor(itemType)
        } else {
            null
        }
        if (horizonKind != null) {
            return@withContext try {
                Result.success(
                    HorizonRegistry.search(
                        horizonIndex(), horizonKind, query, HorizonEngine.SUPPORTED_MC_VERSION, sortIndex, page, limit
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        if (source == SOURCE_CURSEFORGE && !engineSearch) {
            return@withContext CurseForgeApi.search(
                queryText = query,
                itemType = itemType,
                instance = instance,
                filterByGameVersion = filterByGameVersion,
                page = page,
                limit = limit,
                sortIndex = sortIndex,
                filterByLoader = filterByLoader
            )
        }
        try {
            val facets = mutableListOf<List<String>>()

            // 1. Project type facet
            val projectType = when (itemType) {
                "resourcepacks" -> "resourcepack"
                "shaderpacks" -> "shader"
                "datapacks" -> "datapack"
                "modpacks" -> "modpack"
                else -> "mod"
            }
            facets.add(listOf("project_type:$projectType"))

            // 2. Category facet (jika dipilih user)
            if (!category.isNullOrBlank()) {
                facets.add(listOf("categories:$category"))
            }

            // 3. Game Version filter (Auto-Version dari instance)
            if (filterByGameVersion && !engineSearch && instance != null && instance.mcVersion.isNotBlank()) {
                facets.add(listOf("versions:${instance.mcVersion}"))
            }

            // 4. Loader filter (loader instance; Vanilla atau tanpa instance memakai loader yang didukung)
            if (filterByLoader && (itemType == "mods" || itemType == "modpacks")) {
                facets.add(listOf("categories:${effectiveLoader(instance)}"))
            }

            val facetsJson = gson.toJson(facets)
            val offset = (page - 1) * limit

            // Urutan hasil: hanya nilai yang dikenal Modrinth, selain itu kembali ke relevansi
            val index = if (sortIndex in SEARCH_SORT_INDEXES) sortIndex else "relevance"

            val url = StringBuilder("$MODRINTH_API_BASE/search?")
                .append("query=").append(URLEncoder.encode(query, "UTF-8"))
                .append("&limit=").append(limit)
                .append("&offset=").append(offset)
                .append("&index=").append(index)
                .append("&facets=").append(URLEncoder.encode(facetsJson, "UTF-8"))
                .toString()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Modrinth API error code: ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val searchRes = gson.fromJson(body, ModrinthSearchResponse::class.java)
            Result.success(searchRes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengambil detail lengkap suatu project dari Modrinth
     */
    suspend fun getProjectDetails(
        projectId: String,
        horizon: Boolean = false
    ): Result<ModrinthProject> = withContext(Dispatchers.IO) {
        // Proyek dari katalog Horizon (mesin Rust): detail diambil dari katalog, bukan Modrinth
        if (horizon) {
            return@withContext try {
                val project = HorizonRegistry.find(horizonIndex(), projectId)
                    ?: throw java.io.IOException(HorizonRegistry.MSG_INDEX)
                Result.success(HorizonRegistry.toProject(project, HorizonEngine.SUPPORTED_MC_VERSION))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        if (CurseForgeMapper.isCurseForgeId(projectId)) {
            return@withContext CurseForgeApi.projectDetails(projectId)
        }
        try {
            val url = "$MODRINTH_API_BASE/project/$projectId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gagal mengambil detail project: ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val project = gson.fromJson(body, ModrinthProject::class.java)
            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengambil seluruh daftar rilis versi untuk project tertentu
     */
    suspend fun getProjectVersions(projectId: String): Result<List<ModrinthVersion>> = withContext(Dispatchers.IO) {
        try {
            val url = "$MODRINTH_API_BASE/project/$projectId/version"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gagal mengambil versi project: ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val listType = object : TypeToken<List<ModrinthVersion>>() {}.type
            val versions: List<ModrinthVersion> = gson.fromJson(body, listType)
            Result.success(versions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengambil dan menyaring secara ketat versi yang kompatibel dengan instance aktif (Auto-Version)
     */
    suspend fun getCompatibleVersions(
        projectId: String,
        instance: Instance?,
        itemType: String,
        filterByGameVersion: Boolean = true,
        source: String = SOURCE_MODRINTH
    ): Result<List<ModrinthVersion>> = withContext(Dispatchers.IO) {
        // Mesin Rust: mod (dan shader/resource pack bila sumbernya Horizon) dari katalog Horizon, versi yang cocok
        // dengan versi game mesin
        val horizonKind = if (HorizonEngine.isEngine(instance?.loader) &&
            (itemType == HorizonContent.TYPE_MODS || source == SOURCE_HORIZON)
        ) {
            HorizonRegistry.kindFor(itemType)
        } else {
            null
        }
        if (horizonKind != null) {
            return@withContext try {
                val index = horizonIndex()
                val project = HorizonRegistry.find(index, projectId, horizonKind)
                    ?: throw java.io.IOException(HorizonRegistry.MSG_INDEX)
                val list = HorizonRegistry.toVersions(index, project, HorizonEngine.SUPPORTED_MC_VERSION)
                if (list.isEmpty()) Result.failure(java.io.IOException(HorizonRegistry.MSG_NO_FILE)) else Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        // Mesin Rust: semua versi yang punya berkas .zip; yang mencantumkan versi game mesin diurutkan lebih dulu
        if (HorizonEngine.isEngine(instance?.loader)) {
            return@withContext getProjectVersions(projectId)
                .map { HorizonContent.usableVersions(it, HorizonEngine.SUPPORTED_MC_VERSION) }
        }
        if (CurseForgeMapper.isCurseForgeId(projectId)) {
            return@withContext CurseForgeApi.compatibleVersions(projectId, instance, itemType, filterByGameVersion)
        }
        val allVersResult = getProjectVersions(projectId)
        if (allVersResult.isFailure) return@withContext allVersResult

        val allVersions = allVersResult.getOrNull() ?: emptyList()
        if (instance == null) return@withContext Result.success(allVersions)

        val compatible = allVersions.filter { v ->
            // Cek kompatibilitas versi Minecraft
            val matchesVersion = !filterByGameVersion || instance.mcVersion.isBlank() || v.gameVersions.contains(instance.mcVersion)

            // Cek kompatibilitas Loader jika tipe mods atau modpacks
            val matchesLoader = if (itemType == "mods" || itemType == "modpacks") {
                val instLoader = effectiveLoader(instance)
                v.loaders.any { it.equals(instLoader, ignoreCase = true) }
            } else {
                true
            }

            matchesVersion && matchesLoader
        }

        Result.success(compatible)
    }

    /**
     * Memeriksa struktur isi file ZIP secara cerdas untuk menentukan jenis addon Minecraft
     */
    fun inspectZipType(zipFile: File): String {
        try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries()
                var hasShaders = false
                var hasPackMcmeta = false
                var hasAssets = false
                var hasData = false
                var hasModpackIndex = false
                var hasCurseforgeManifest = false
                var hasModsFolder = false
                var hasJarFiles = false

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name.lowercase()
                    if (name.startsWith("shaders/") || name.contains("/shaders/")) {
                        hasShaders = true
                    }
                    if (name == "pack.mcmeta" || name.endsWith("/pack.mcmeta")) {
                        hasPackMcmeta = true
                    }
                    if (name.startsWith("assets/") || name.contains("/assets/")) {
                        hasAssets = true
                    }
                    if (name.startsWith("data/") || name.contains("/data/")) {
                        hasData = true
                    }
                    if (name.endsWith("modrinth.index.json")) {
                        hasModpackIndex = true
                    }
                    if (name.endsWith("manifest.json")) {
                        hasCurseforgeManifest = true
                    }
                    if (name.startsWith("mods/") || name.contains("/mods/")) {
                        hasModsFolder = true
                    }
                    if (name.endsWith(".jar")) {
                        hasJarFiles = true
                    }
                }

                return when {
                    hasModpackIndex || hasCurseforgeManifest -> "modpacks"
                    hasShaders -> "shaderpacks"
                    hasPackMcmeta && hasAssets -> "resourcepacks"
                    hasPackMcmeta && hasData -> "datapacks"
                    hasModsFolder || hasJarFiles -> "modpacks"
                    else -> "unknown"
                }
            }
        } catch (e: Exception) {
            LauncherLog.e("NuxModManager", "Gagal inspect ZIP: ${e.message}", e)
            return "unknown"
        }
    }

    /**
     * Sistem Auto-Download Dependensi:
     * Mengunduh file utama versi target, lalu secara rekursif mencari dan mengunduh dependensi wajib (required)
     * yang kompatibel dengan instance dan belum terpasang.
     */
    suspend fun downloadVersionAndDependencies(
        context: Context,
        instance: Instance,
        version: ModrinthVersion,
        itemType: String,
        worldName: String? = null,
        onProgress: (statusText: String) -> Unit = {}
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (HorizonEngine.isEngine(instance.loader)) {
            return@withContext downloadForEngine(context, version, itemType, onProgress)
        }
        val visitedProjects = mutableSetOf<String>()
        val downloadedFiles = mutableListOf<String>()

        try {
            // Ambil daftar file yang sudah terpasang agar tidak mendownload ulang
            val installedList = getInstalledItems(context, instance, itemType, worldName)
            val installedFilenames = installedList.map { it.filename.lowercase() }.toMutableSet()

            suspend fun resolveAndDownload(targetVer: ModrinthVersion) {
                if (targetVer.files.isEmpty()) return

                // 1. Pilih primary file atau file pertama
                val primaryFile = targetVer.files.firstOrNull { it.primary } ?: targetVer.files.first()
                // Nama berkas datang dari respons server: pastikan tidak berisi folder ("../")
                val filename = SafePaths.fileName(primaryFile.filename)

                if (targetVer.projectId.isNotBlank() && visitedProjects.contains(targetVer.projectId)) {
                    return
                }
                if (targetVer.projectId.isNotBlank()) {
                    visitedProjects.add(targetVer.projectId)
                }

                // Download file jika belum ada
                if (!installedFilenames.contains(filename.lowercase())) {
                    onProgress("Mengunduh $filename...")
                    val targetDir = getTargetDir(context, instance, itemType, worldName)
                    val destFile = File(targetDir, filename)

                    downloadFile(primaryFile.url, destFile)
                    verifyHashes(destFile, primaryFile.hashes)
                    installedFilenames.add(filename.lowercase())
                    downloadedFiles.add(filename)

                    if (itemType == "modpacks" && (filename.endsWith(".mrpack", ignoreCase = true) || filename.endsWith(".zip", ignoreCase = true))) {
                        onProgress("Mengekstrak dan menyiapkan modpack...")
                        processMrpackFile(instance, destFile, context) { status, _, _ ->
                            onProgress(status)
                        }
                    }
                }

                // 2. Periksa dependensi wajib (required dependencies)
                val requiredDeps = targetVer.dependencies?.filter {
                    it.dependencyType.equals("required", ignoreCase = true) &&
                            (!it.versionId.isNullOrBlank() || !it.projectId.isNullOrBlank())
                } ?: emptyList()

                for (dep in requiredDeps) {
                    try {
                        if (!dep.projectId.isNullOrBlank() && visitedProjects.contains(dep.projectId)) {
                            continue
                        }

                        var depVersionObj: ModrinthVersion? = null

                        // Cari berdasarkan version_id jika ada
                        if (!dep.versionId.isNullOrBlank()) {
                            val vUrl = "$MODRINTH_API_BASE/version/${dep.versionId}"
                            val req = Request.Builder().url(vUrl).header("User-Agent", USER_AGENT).build()
                            val resp = httpClient.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val vBody = resp.body?.string()
                                if (vBody != null) {
                                    depVersionObj = gson.fromJson(vBody, ModrinthVersion::class.java)
                                }
                            }
                        }

                        // Jika tidak ada version_id atau gagal, cari berdasarkan project_id versi yang kompatibel
                        if (depVersionObj == null && !dep.projectId.isNullOrBlank()) {
                            val compRes = getCompatibleVersions(dep.projectId, instance, itemType)
                            val compList = compRes.getOrNull()
                            if (!compList.isNullOrEmpty()) {
                                depVersionObj = compList.first()
                            }
                        }

                        if (depVersionObj != null && depVersionObj.files.isNotEmpty()) {
                            val depPrimary = depVersionObj.files.firstOrNull { it.primary } ?: depVersionObj.files.first()
                            if (!installedFilenames.contains(SafePaths.fileName(depPrimary.filename).lowercase())) {
                                onProgress("Menyelesaikan dependensi: ${depPrimary.filename}...")
                                resolveAndDownload(depVersionObj)
                            }
                        }
                    } catch (e: Exception) {
                        // Jangan batalkan seluruh instalasi jika salah satu dependensi sekunder gagal diakses
                    }
                }
            }

            resolveAndDownload(version)
            Result.success(downloadedFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun processMrpackFile(
        instance: Instance,
        mrpackFile: File,
        context: Context,
        onProgress: (statusText: String, current: Int, total: Int) -> Unit = { _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        try {
            val gameDir = InstanceManager.getInstanceGameDir(context, instance)
            val cleanFilename = mrpackFile.name.removeSuffix(".disabled")
            val installedRelativeFiles = mutableListOf<String>()
            var packTitle = mrpackFile.nameWithoutExtension
            var packVersion = ""
            var skippedFiles = 0
            var failedFiles = 0
            var extractedBytes = 0L
            // Catatan tambahan di pesan akhir (mis. mod CurseForge yang unduhannya ditutup penulis)
            var packNote = ""

            ZipFile(mrpackFile).use { zip ->
                // 1. Cari modrinth.index.json
                val indexEntry = zip.getEntry("modrinth.index.json")
                    ?: zip.entries().asSequence().firstOrNull { it.name.endsWith("modrinth.index.json") }
                // Bukan mrpack: mungkin modpack CurseForge (manifest.json di akar zip)
                val cfPack = if (indexEntry == null) readCurseForgeManifest(zip) else null

                if (indexEntry != null) {
                    val prefix = if (indexEntry.name.contains("/")) indexEntry.name.substringBeforeLast("/") + "/" else ""
                    val filesToDownload = mutableListOf<MrpackFileEntry>()

                    zip.getInputStream(indexEntry).bufferedReader().use { reader ->
                        val indexJson = JsonParser.parseReader(reader).asJsonObject
                        // Tolak modpack yang tidak cocok sebelum ada file yang diunduh
                        checkModpackCompatibility(indexJson, instance)
                        if (indexJson.has("name")) {
                            packTitle = indexJson.get("name").asString
                        }
                        if (indexJson.has("versionId")) {
                            packVersion = indexJson.get("versionId").asString
                        }

                        if (indexJson.has("files")) {
                            val files = indexJson.getAsJsonArray("files")
                            for (element in files) {
                                try {
                                    val fileObj = element.asJsonObject
                                    val env = fileObj.getAsJsonObject("env")
                                    if (env != null && env.has("client") && env.get("client").asString.equals("unsupported", ignoreCase = true)) {
                                        continue
                                    }
                                    val relPath = fileObj.get("path")?.asString ?: continue
                                    val downloads = fileObj.getAsJsonArray("downloads")
                                    if (downloads == null || downloads.size() == 0) continue
                                    val dlUrl = downloads.get(0).asString

                                    // Path harus tetap di dalam folder instance, dan sumber unduhan harus HTTPS
                                    // dari host modpack yang dikenal. Selain itu dilewati.
                                    if (resolveModpackFile(gameDir, relPath) == null ||
                                        !SafePaths.isAllowedModpackDownloadUrl(dlUrl)
                                    ) {
                                        skippedFiles++
                                        LauncherLog.w("NuxModManager", "File modpack dilewati (path/alamat tidak aman): $relPath")
                                        continue
                                    }

                                    val hashes = mutableMapOf<String, String>()
                                    val hashObj = fileObj.getAsJsonObject("hashes")
                                    if (hashObj != null) {
                                        for ((algo, value) in hashObj.entrySet()) {
                                            if (value.isJsonPrimitive) hashes[algo.lowercase()] = value.asString
                                        }
                                    }
                                    filesToDownload.add(MrpackFileEntry(dlUrl, relPath.replace('\\', '/'), hashes))
                                } catch (e: Exception) {
                                    skippedFiles++
                                }
                            }
                        }
                    }

                    val totalFiles = filesToDownload.size
                    onProgress("Membaca daftar modpack ($totalFiles mod ditemukan)...", 0, totalFiles)
                    var currentIdx = 0
                    for (entry in filesToDownload) {
                        currentIdx++
                        val targetFile = resolveModpackFile(gameDir, entry.relPath)
                        if (targetFile == null) {
                            skippedFiles++
                            continue
                        }
                        if (!targetFile.exists()) {
                            targetFile.parentFile?.mkdirs()
                            onProgress("Mengunduh mod ($currentIdx/$totalFiles): ${targetFile.name}...", currentIdx, totalFiles)
                            try {
                                downloadFile(entry.url, targetFile)
                                verifyHashes(targetFile, entry.hashes)
                                installedRelativeFiles.add(entry.relPath)
                            } catch (e: Exception) {
                                failedFiles++
                                LauncherLog.e("NuxModManager", "Gagal unduh file modpack: ${entry.url}", e)
                            }
                        } else {
                            installedRelativeFiles.add(entry.relPath)
                            onProgress("Mod sudah ada ($currentIdx/$totalFiles): ${targetFile.name}", currentIdx, totalFiles)
                        }
                    }

                    // 2. Extract overrides/ and client-overrides/
                    onProgress("Mengekstrak konfigurasi modpack...", totalFiles, totalFiles)
                    val overridesPrefix = "${prefix}overrides/"
                    val clientOverridesPrefix = "${prefix}client-overrides/"
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        val rel = when {
                            name.startsWith(overridesPrefix) -> name.removePrefix(overridesPrefix)
                            name.startsWith(clientOverridesPrefix) -> name.removePrefix(clientOverridesPrefix)
                            else -> null
                        }
                        if (rel != null && rel.isNotBlank() && !rel.startsWith("saves/")) {
                            val outFile = resolveModpackFile(gameDir, rel)
                            if (outFile == null) {
                                skippedFiles++
                                LauncherLog.w("NuxModManager", "Isi modpack dilewati (path tidak aman): $name")
                                continue
                            }
                            installedRelativeFiles.add(rel)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                extractedBytes = extractZipEntry(zip, entry, outFile, extractedBytes)
                            }
                        }
                    }
                } else if (cfPack != null) {
                    // Modpack CurseForge: daftar mod ada di manifest.json, isi tambahan di folder overrides/
                    checkCurseForgePackCompatibility(cfPack, instance)
                    if (cfPack.name.isNotBlank()) packTitle = cfPack.name
                    packVersion = cfPack.version

                    onProgress("Mencari alamat unduhan mod dari CurseForge...", 0, 0)
                    val wanted = cfPack.files
                    val resolved = CurseForgeApi.resolveFiles(wanted.map { it.fileId })
                    val classIds = CurseForgeApi.classIdsOf(wanted.map { it.projectId })

                    val totalFiles = wanted.size
                    var currentIdx = 0
                    var blockedFiles = 0
                    for (packFile in wanted) {
                        currentIdx++
                        val info = resolved[packFile.fileId]
                        val dlUrl = info?.url
                        if (info == null || dlUrl == null) {
                            // Penulis menutup unduhan lewat API (atau file sudah dihapus): dihormati, tidak dipaksa
                            blockedFiles++
                            skippedFiles++
                            continue
                        }
                        val folder = CurseForgeMapper.folderFor(classIds[packFile.projectId], info.fileName)
                        val relPath = folder + "/" + SafePaths.fileName(info.fileName)
                        val targetFile = resolveModpackFile(gameDir, relPath)
                        if (targetFile == null || !SafePaths.isAllowedModpackDownloadUrl(dlUrl)) {
                            skippedFiles++
                            LauncherLog.w("NuxModManager", "File modpack dilewati (path/alamat tidak aman): $relPath")
                            continue
                        }
                        if (!targetFile.exists()) {
                            targetFile.parentFile?.mkdirs()
                            onProgress("Mengunduh mod ($currentIdx/$totalFiles): ${targetFile.name}...", currentIdx, totalFiles)
                            try {
                                downloadFile(dlUrl, targetFile)
                                verifyHashes(targetFile, info.sha1?.let { mapOf("sha1" to it) })
                                installedRelativeFiles.add(relPath)
                            } catch (e: Exception) {
                                failedFiles++
                                LauncherLog.e("NuxModManager", "Gagal unduh file modpack: $dlUrl", e)
                            }
                        } else {
                            installedRelativeFiles.add(relPath)
                            onProgress("Mod sudah ada ($currentIdx/$totalFiles): ${targetFile.name}", currentIdx, totalFiles)
                        }
                    }
                    if (blockedFiles > 0) {
                        packNote = " $blockedFiles mod tidak bisa diunduh karena penulisnya menutup unduhan lewat launcher. " +
                            "Pasang manual dari situs CurseForge bila perlu."
                    }

                    onProgress("Mengekstrak konfigurasi modpack...", totalFiles, totalFiles)
                    val overridesPrefix = cfPack.overrides + "/"
                    val overrideEntries = zip.entries()
                    while (overrideEntries.hasMoreElements()) {
                        val entry = overrideEntries.nextElement()
                        val name = entry.name
                        if (!name.startsWith(overridesPrefix)) continue
                        val rel = name.removePrefix(overridesPrefix)
                        if (rel.isBlank() || rel.startsWith("saves/")) continue
                        val outFile = resolveModpackFile(gameDir, rel)
                        if (outFile == null) {
                            skippedFiles++
                            LauncherLog.w("NuxModManager", "Isi modpack dilewati (path tidak aman): $name")
                            continue
                        }
                        installedRelativeFiles.add(rel)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            extractedBytes = extractZipEntry(zip, entry, outFile, extractedBytes)
                        }
                    }
                } else {
                    // Fallback jika file zip modpack bukan standar Modrinth mrpack
                    // (misal zip kumpulan mod yang langsung berisi folder mods/ atau file .jar)
                    onProgress("Mengekstrak file arsip modpack...", 0, 0)
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (entry.isDirectory) continue

                        val relPath: String? = when {
                            name.startsWith("mods/") || name.startsWith("config/") ||
                            name.startsWith("shaderpacks/") || name.startsWith("resourcepacks/") ||
                            name.startsWith("datapacks/") -> name
                            name.endsWith(".jar", ignoreCase = true) && !name.contains("/") -> "mods/" + SafePaths.fileName(name)
                            else -> null
                        }
                        if (relPath == null) continue

                        val outFile = resolveModpackFile(gameDir, relPath)
                        if (outFile == null) {
                            skippedFiles++
                            LauncherLog.w("NuxModManager", "Isi arsip dilewati (path tidak aman): $name")
                            continue
                        }
                        installedRelativeFiles.add(relPath)
                        extractedBytes = extractZipEntry(zip, entry, outFile, extractedBytes)
                    }
                }
            }

            // Simpan manifest modpack untuk pelacakan delete & toggle
            val manifest = ModpackManifest(
                modpackName = packTitle,
                version = packVersion,
                archiveFilename = cleanFilename,
                installedFiles = installedRelativeFiles.distinct()
            )
            saveModpackManifest(gameDir, manifest)

            val installedModCount = installedRelativeFiles.count { it.startsWith("mods/") || it.endsWith(".jar", ignoreCase = true) }
            val problemCount = failedFiles + skippedFiles
            val doneMessage = if (problemCount == 0) {
                "Selesai! $installedModCount mod berhasil dipasang."
            } else {
                "Selesai dengan catatan: $installedModCount mod terpasang, $problemCount file gagal atau dilewati. Coba pasang ulang bila ada mod yang kurang."
            }
            onProgress(doneMessage + packNote, installedRelativeFiles.size, installedRelativeFiles.size)
        } catch (e: Exception) {
            LauncherLog.e("NuxModManager", "Error processing mrpack: ${e.message}", e)
            if (e is ModpackIncompatibleException || e is CurseForgeApi.KeyException) {
                // Modpack ditolak (atau kunci CurseForge bermasalah): buang arsipnya supaya tidak tampil
                // sebagai modpack yang terpasang
                try {
                    mrpackFile.delete()
                } catch (_: Exception) {
                }
            }
            throw e
        }
    }

    /** Membaca manifest.json di akar zip; null bila bukan modpack CurseForge. */
    private fun readCurseForgeManifest(zip: ZipFile): CurseForgeMapper.PackManifest? {
        val entry = zip.getEntry("manifest.json") ?: return null
        return try {
            zip.getInputStream(entry).bufferedReader().use { reader ->
                val root = JsonParser.parseReader(reader)
                if (root.isJsonObject) CurseForgeMapper.parseManifest(root.asJsonObject) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Seperti [checkModpackCompatibility] tetapi untuk modpack CurseForge: loader dan versi Minecraft
     * harus cocok dengan instance sebelum ada file yang diunduh.
     */
    private fun checkCurseForgePackCompatibility(pack: CurseForgeMapper.PackManifest, instance: Instance) {
        val loader = pack.loader
        if (loader == "quilt") {
            throw ModpackIncompatibleException(
                "Modpack ini membutuhkan Quilt, sedangkan AmberLauncher saat ini hanya mendukung Fabric, Forge, dan NeoForge."
            )
        }
        if ((loader == "forge" || loader == "fabric" || loader == "neoforge") &&
            !instance.loader.equals(loader, ignoreCase = true)
        ) {
            val label = if (loader == "neoforge") "NeoForge" else loader.replaceFirstChar { it.uppercase() }
            throw ModpackIncompatibleException(
                "Modpack ini membutuhkan $label. " +
                    "Buat instance $label dulu, lalu pasang modpack di instance itu."
            )
        }
        val packMc = pack.minecraftVersion
        if (packMc.isNotBlank() && instance.mcVersion.isNotBlank() && packMc != instance.mcVersion) {
            throw ModpackIncompatibleException(
                "Modpack ini untuk Minecraft $packMc, sedangkan instance ini memakai ${instance.mcVersion}. " +
                    "Pilih atau buat instance dengan versi yang sama."
            )
        }
    }

    /**
     * Memeriksa modpack Modrinth sebelum ada file yang diunduh: loader dan versi Minecraft-nya harus
     * cocok dengan instance. Bila tidak cocok, dilempar [ModpackIncompatibleException] dengan pesan jelas.
     */
    private fun checkModpackCompatibility(indexJson: com.google.gson.JsonObject, instance: Instance) {
        val deps = indexJson.getAsJsonObject("dependencies") ?: return

        val otherLoader = listOf("quilt-loader" to "Quilt")
            .firstOrNull { deps.has(it.first) }
        // Satu modpack hanya punya satu loader; bila ada beberapa, Fabric diutamakan
        val requiredLoader = when {
            deps.has("fabric-loader") -> "fabric" to "Fabric"
            deps.has("neoforge") -> "neoforge" to "NeoForge"
            deps.has("forge") -> "forge" to "Forge"
            else -> null
        }

        if (requiredLoader == null && otherLoader != null) {
            throw ModpackIncompatibleException(
                "Modpack ini membutuhkan ${otherLoader.second}, sedangkan AmberLauncher saat ini hanya mendukung Fabric, Forge, dan NeoForge."
            )
        }
        if (requiredLoader != null && !instance.loader.equals(requiredLoader.first, ignoreCase = true)) {
            throw ModpackIncompatibleException(
                "Modpack ini membutuhkan ${requiredLoader.second}. " +
                    "Buat instance ${requiredLoader.second} dulu, lalu pasang modpack di instance itu."
            )
        }

        val packMc = deps.get("minecraft")?.takeIf { it.isJsonPrimitive }?.asString
        if (!packMc.isNullOrBlank() && instance.mcVersion.isNotBlank() && packMc != instance.mcVersion) {
            throw ModpackIncompatibleException(
                "Modpack ini untuk Minecraft $packMc, sedangkan instance ini memakai ${instance.mcVersion}. " +
                    "Pilih atau buat instance dengan versi yang sama."
            )
        }
    }

    /**
     * Mengunduh [url] ke [destFile]. Hanya HTTPS. Unduhan ditulis ke berkas sementara (.part) dulu dan
     * baru dipindah setelah selesai, jadi unduhan yang terputus tidak tertinggal sebagai berkas rusak.
     */
    private fun downloadFile(url: String, destFile: File, maxBytes: Long = Long.MAX_VALUE) {
        if (!url.trim().startsWith("https://", ignoreCase = true)) {
            throw java.io.IOException("Alamat unduhan ditolak karena tidak memakai HTTPS")
        }
        val request = Request.Builder()
            .url(url.trim())
            .header("User-Agent", USER_AGENT)
            .build()

        val partFile = File(destFile.parentFile, destFile.name + ".part")
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Gagal mengunduh file: HTTP ${response.code}")
                }
                val body = response.body ?: throw Exception("Body stream kosong")
                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            // Sumber yang mengirim lebih banyak dari yang dijanjikan dihentikan di tengah jalan
                            if (total > maxBytes) throw java.io.IOException(HorizonRegistry.MSG_SIZE)
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            if (destFile.exists()) destFile.delete()
            if (!partFile.renameTo(destFile)) {
                throw java.io.IOException("Gagal menyimpan file unduhan")
            }
        } catch (e: Exception) {
            partFile.delete()
            throw e
        }
    }

    /**
     * Mencocokkan sidik jari (sha256 dari katalog Horizon, atau sha512, atau sha1 bila tidak ada) berkas dengan yang diumumkan sumbernya.
     * Bila tidak cocok, berkas dihapus dan dilempar IOException. Tanpa sidik jari, tidak ada yang diperiksa.
     */
    private fun verifyHashes(file: File, hashes: Map<String, String>?) {
        val map = hashes ?: return
        val sha256 = map["sha256"]?.trim().orEmpty()
        val sha512 = map["sha512"]?.trim().orEmpty()
        val sha1 = map["sha1"]?.trim().orEmpty()
        val algorithm: String
        val expected: String
        when {
            sha256.isNotEmpty() -> { algorithm = "SHA-256"; expected = sha256 }
            sha512.isNotEmpty() -> { algorithm = "SHA-512"; expected = sha512 }
            sha1.isNotEmpty() -> { algorithm = "SHA-1"; expected = sha1 }
            else -> return
        }

        val digest = java.security.MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected, ignoreCase = true)) {
            file.delete()
            throw java.io.IOException("Berkas ${file.name} rusak atau berubah di jalan (sidik jari tidak cocok), jadi tidak dipasang.")
        }
    }

    /**
     * Menyalin satu entri zip ke [outFile] dan mengembalikan total byte yang sudah diekstrak.
     * Berhenti dengan IOException bila total melewati [MAX_MODPACK_EXTRACT_BYTES] (arsip "bom").
     */
    private fun extractZipEntry(zip: ZipFile, entry: java.util.zip.ZipEntry, outFile: File, usedBytes: Long): Long {
        var total = usedBytes
        outFile.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_MODPACK_EXTRACT_BYTES) {
                        throw java.io.IOException("Isi modpack terlalu besar, pemasangan dibatalkan.")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        return total
    }

    /**
     * File di dalam [gameDir] untuk path milik modpack. Null bila path tidak aman (keluar folder instance)
     * atau menyentuh dunia permainan (saves), supaya modpack tidak bisa menimpa atau menghapus dunia.
     */
    private fun resolveModpackFile(gameDir: File, relPath: String): File? {
        val normalized = relPath.replace('\\', '/')
        if (normalized == "saves" || normalized.startsWith("saves/")) return null
        return SafePaths.resolveInside(gameDir, relPath)
    }

    fun getModpackMetadataFile(gameDir: File, cleanFilename: String): File {
        val metaDir = File(gameDir, "modpacks/.metadata")
        if (!metaDir.exists()) metaDir.mkdirs()
        val baseName = if (cleanFilename.endsWith(".disabled")) cleanFilename.removeSuffix(".disabled") else cleanFilename
        return File(metaDir, "$baseName.json")
    }

    fun saveModpackManifest(gameDir: File, manifest: ModpackManifest) {
        try {
            val metaFile = getModpackMetadataFile(gameDir, manifest.archiveFilename)
            metaFile.writeText(gson.toJson(manifest))
        } catch (e: Exception) {
            LauncherLog.e("NuxModManager", "Gagal menyimpan metadata modpack: ${e.message}", e)
        }
    }

    fun loadModpackManifest(gameDir: File, cleanFilename: String): ModpackManifest? {
        try {
            val metaFile = getModpackMetadataFile(gameDir, cleanFilename)
            if (metaFile.exists() && metaFile.isFile) {
                val json = metaFile.readText()
                return gson.fromJson(json, ModpackManifest::class.java)
            }
        } catch (e: Exception) {
            LauncherLog.e("NuxModManager", "Gagal membaca metadata modpack: ${e.message}", e)
        }
        return null
    }

    fun getModpackInstalledFilesFromArchive(archiveFile: File): List<String> {
        val files = mutableListOf<String>()
        if (!archiveFile.exists() || !archiveFile.isFile) return files
        try {
            ZipFile(archiveFile).use { zip ->
                val indexEntry = zip.getEntry("modrinth.index.json")
                    ?: zip.entries().asSequence().firstOrNull { it.name.endsWith("modrinth.index.json") }
                if (indexEntry != null) {
                    val prefix = if (indexEntry.name.contains("/")) indexEntry.name.substringBeforeLast("/") + "/" else ""
                    zip.getInputStream(indexEntry).bufferedReader().use { reader ->
                        val indexJson = JsonParser.parseReader(reader).asJsonObject
                        if (indexJson.has("files")) {
                            for (el in indexJson.getAsJsonArray("files")) {
                                val rel = el.asJsonObject.get("path")?.asString
                                if (!rel.isNullOrBlank()) files.add(rel)
                            }
                        }
                    }
                    val overridesPrefix = "${prefix}overrides/"
                    val clientOverridesPrefix = "${prefix}client-overrides/"
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        val name = entry.name
                        val rel = when {
                            name.startsWith(overridesPrefix) -> name.removePrefix(overridesPrefix)
                            name.startsWith(clientOverridesPrefix) -> name.removePrefix(clientOverridesPrefix)
                            else -> null
                        }
                        if (rel != null && rel.isNotBlank() && !rel.startsWith("saves/")) {
                            files.add(rel)
                        }
                    }
                } else {
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        val name = entry.name
                        when {
                            name.startsWith("mods/") || name.startsWith("config/") ||
                            name.startsWith("shaderpacks/") || name.startsWith("resourcepacks/") ||
                            name.startsWith("datapacks/") -> {
                                files.add(name)
                            }
                            name.endsWith(".jar", ignoreCase = true) && !name.contains("/") -> {
                                files.add("mods/$name")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LauncherLog.e("NuxModManager", "Error getModpackInstalledFilesFromArchive: ${e.message}", e)
        }
        // Path dari arsip luar tidak boleh keluar dari folder instance (mis. "../../")
        return files.distinct().filter { SafePaths.isSafeRelative(it) }
    }
}
