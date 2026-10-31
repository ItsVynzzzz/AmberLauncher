package com.israadev.nuxlauncher.core.mods

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.israadev.nuxlauncher.core.models.ModrinthDependency
import com.israadev.nuxlauncher.core.models.ModrinthGalleryItem
import com.israadev.nuxlauncher.core.models.ModrinthProject
import com.israadev.nuxlauncher.core.models.ModrinthSearchHit
import com.israadev.nuxlauncher.core.models.ModrinthVersion
import com.israadev.nuxlauncher.core.models.ModrinthVersionFile

/**
 * Pemetaan data CurseForge ke model yang dipakai layar Mod (bentuknya model Modrinth), sehingga daftar,
 * jendela detail, dan pengunduh tidak perlu tahu sumbernya. Murni JSON ke model, tanpa jaringan dan
 * tanpa Android, jadi mudah diuji.
 *
 * Semua id proyek CurseForge diberi awalan [ID_PREFIX] ("cf:123"). Awalan itulah yang memberi tahu
 * NuxModManager harus bertanya ke CurseForge, bukan ke Modrinth, untuk detail, versi, dan dependensi.
 *
 * JSON dibaca tanpa refleksi (JsonObject), jadi aman dari R8 dan tidak mengalokasi kelas tambahan.
 */
object CurseForgeMapper {
    const val ID_PREFIX = "cf:"

    /** Id game Minecraft di CurseForge. */
    const val GAME_ID = 432

    /** Pesan untuk mod yang penulisnya melarang unduhan lewat aplikasi pihak ketiga. */
    const val BLOCKED_NOTE = "Penulis melarang unduhan lewat launcher. Unduh manual dari situs CurseForge."

    fun isCurseForgeId(id: String?): Boolean = id != null && id.startsWith(ID_PREFIX)

    fun prefixed(modId: Long): String = "$ID_PREFIX$modId"

    /** Id numerik dari "cf:123", atau null bila bukan id CurseForge yang sah. */
    fun modIdOf(prefixedId: String?): Long? {
        if (prefixedId == null || !prefixedId.startsWith(ID_PREFIX)) return null
        return prefixedId.substring(ID_PREFIX.length).toLongOrNull()
    }

    /** classId CurseForge untuk jenis item di launcher. */
    fun classIdFor(itemType: String): Int = when (itemType) {
        "modpacks" -> 4471
        "resourcepacks" -> 12
        "shaderpacks" -> 6552
        "datapacks" -> 6945
        else -> 6
    }

    /** Nilai project_type yang dipakai layar (sama dengan istilah Modrinth). */
    fun projectTypeFor(itemType: String): String = when (itemType) {
        "resourcepacks" -> "resourcepack"
        "shaderpacks" -> "shader"
        "datapacks" -> "datapack"
        "modpacks" -> "modpack"
        else -> "mod"
    }

    /** modLoaderType CurseForge untuk nama loader; 0 = tidak dikenal (tanpa penyaringan). */
    fun loaderTypeFor(loader: String?): Int = when (loader?.trim()?.lowercase()) {
        "forge" -> 1
        "fabric" -> 4
        "quilt" -> 5
        "neoforge" -> 6
        else -> 0
    }

    private fun loaderNameFor(type: Int): String? = when (type) {
        1 -> "forge"
        4 -> "fabric"
        5 -> "quilt"
        6 -> "neoforge"
        else -> null
    }

    /** sortField CurseForge untuk pilihan urutan di layar. 1 = Featured, 2 = Popularity, 3 = LastUpdated, 6 = TotalDownloads. */
    fun sortFieldFor(sortIndex: String): Int = when (sortIndex) {
        "downloads" -> 6
        "follows" -> 2
        "newest", "updated" -> 3
        else -> 1
    }

    // ------------------------------------------------------------------
    // Pembaca JSON yang tahan JsonNull dan tipe tak terduga
    // ------------------------------------------------------------------

    private fun JsonObject.str(name: String): String? {
        val e = get(name) ?: return null
        return if (e.isJsonPrimitive) e.asString else null
    }

    private fun JsonObject.long(name: String): Long? {
        val e = get(name) ?: return null
        if (!e.isJsonPrimitive) return null
        return try {
            e.asLong
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.int(name: String): Int? = long(name)?.toInt()

    private fun JsonObject.obj(name: String): JsonObject? {
        val e = get(name) ?: return null
        return if (e.isJsonObject) e.asJsonObject else null
    }

    private fun JsonObject.objects(name: String): List<JsonObject> {
        val e = get(name) ?: return emptyList()
        if (!e.isJsonArray) return emptyList()
        val out = ArrayList<JsonObject>(e.asJsonArray.size())
        for (item in e.asJsonArray) {
            if (item.isJsonObject) out.add(item.asJsonObject)
        }
        return out
    }

    /** true / false bila ada nilai boolean yang sah, null bila kosong atau JsonNull. */
    private fun JsonObject.bool(name: String): Boolean? {
        val e: JsonElement = get(name) ?: return null
        if (!e.isJsonPrimitive || !e.asJsonPrimitive.isBoolean) return null
        return e.asBoolean
    }

    /** Isi larik "data" pada respons CurseForge (hanya yang berupa objek). */
    fun dataObjects(root: JsonObject): List<JsonObject> = root.objects("data")

    /** Objek tunggal pada "data" (mis. GET /mods/{id}), atau null. */
    fun dataObject(root: JsonObject): JsonObject? = root.obj("data")

    /** Total hasil pada "pagination.totalCount", atau 0. */
    fun totalCountOf(root: JsonObject): Int = root.obj("pagination")?.int("totalCount") ?: 0

    // ------------------------------------------------------------------
    // Mod -> hasil pencarian dan detail proyek
    // ------------------------------------------------------------------

    /** Loader yang dikenal dari latestFilesIndexes (modLoader), tanpa duplikat. */
    private fun loadersOfMod(mod: JsonObject): List<String> {
        val out = LinkedHashSet<String>()
        for (idx in mod.objects("latestFilesIndexes")) {
            val type = idx.int("modLoader") ?: continue
            loaderNameFor(type)?.let { out.add(it) }
        }
        return out.toList()
    }

    private fun gameVersionsOfMod(mod: JsonObject): List<String> {
        val out = LinkedHashSet<String>()
        for (idx in mod.objects("latestFilesIndexes")) {
            idx.str("gameVersion")?.let { if (it.isNotBlank()) out.add(it) }
        }
        return out.toList()
    }

    /** Penulis melarang unduhan pihak ketiga bila allowModDistribution bernilai false (null = tidak diketahui). */
    fun isDistributionBlocked(mod: JsonObject): Boolean = mod.bool("allowModDistribution") == false

    private fun iconOf(mod: JsonObject): String? {
        val logo = mod.obj("logo") ?: return null
        return (logo.str("thumbnailUrl")?.takeIf { it.isNotBlank() } ?: logo.str("url"))?.takeIf { it.isNotBlank() }
    }

    fun toSearchHit(mod: JsonObject, itemType: String): ModrinthSearchHit? {
        val id = mod.long("id") ?: return null
        val name = mod.str("name")?.takeIf { it.isNotBlank() } ?: return null
        val topics = mod.objects("categories").mapNotNull { it.str("slug") }.filter { it.isNotBlank() }
        val loaders = loadersOfMod(mod)
        val summary = mod.str("summary").orEmpty()
        val description = if (isDistributionBlocked(mod)) "$BLOCKED_NOTE $summary".trim() else summary
        val tags = (topics + loaders).distinct()
        return ModrinthSearchHit(
            projectId = prefixed(id),
            projectType = projectTypeFor(itemType),
            slug = mod.str("slug"),
            author = mod.objects("authors").firstOrNull()?.str("name"),
            title = name,
            description = description,
            categories = tags,
            displayCategories = tags,
            versions = gameVersionsOfMod(mod),
            downloads = mod.long("downloadCount") ?: 0L,
            iconUrl = iconOf(mod),
            color = null
        )
    }

    fun toProject(mod: JsonObject): ModrinthProject? {
        val id = mod.long("id") ?: return null
        val summary = mod.str("summary").orEmpty()
        val gallery = mod.objects("screenshots").mapNotNull { shot ->
            val url = shot.str("url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ModrinthGalleryItem(url = url, title = shot.str("title"), description = shot.str("description"))
        }
        return ModrinthProject(
            id = prefixed(id),
            slug = mod.str("slug").orEmpty(),
            title = mod.str("name").orEmpty(),
            description = if (isDistributionBlocked(mod)) "$BLOCKED_NOTE $summary".trim() else summary,
            body = null,
            categories = mod.objects("categories").mapNotNull { it.str("slug") },
            loaders = loadersOfMod(mod),
            gameVersions = gameVersionsOfMod(mod),
            downloads = mod.long("downloadCount") ?: 0L,
            iconUrl = iconOf(mod),
            gallery = gallery
        )
    }

    // ------------------------------------------------------------------
    // File -> versi yang bisa diunduh
    // ------------------------------------------------------------------

    private val KNOWN_LOADER_NAMES = setOf("forge", "fabric", "quilt", "neoforge", "liteloader", "cauldron")

    /** Daftar gameVersions CurseForge mencampur versi Minecraft dan nama loader; yang pertama diawali angka. */
    private fun minecraftVersionsOf(file: JsonObject): List<String> {
        val e = file.get("gameVersions")
        if (e == null || !e.isJsonArray) return emptyList()
        val out = ArrayList<String>()
        for (item in e.asJsonArray) {
            if (!item.isJsonPrimitive) continue
            val v = item.asString.trim()
            if (v.isNotEmpty() && v[0].isDigit()) out.add(v)
        }
        return out
    }

    private fun loaderNamesOf(file: JsonObject): List<String> {
        val e = file.get("gameVersions")
        if (e == null || !e.isJsonArray) return emptyList()
        val out = ArrayList<String>()
        for (item in e.asJsonArray) {
            if (!item.isJsonPrimitive) continue
            val v = item.asString.trim().lowercase()
            if (v in KNOWN_LOADER_NAMES) out.add(v)
        }
        return out
    }

    /** Alamat unduhan sebuah file, atau null bila penulis menutup unduhan lewat API. */
    fun downloadUrlOf(file: JsonObject): String? = file.str("downloadUrl")?.trim()?.takeIf { it.isNotEmpty() }

    private fun sha1Of(file: JsonObject): String? {
        for (h in file.objects("hashes")) {
            if (h.int("algo") == 1) {
                val v = h.str("value")?.trim()
                if (!v.isNullOrEmpty()) return v
            }
        }
        return null
    }

    private fun dependencyTypeOf(relationType: Int?): String = when (relationType) {
        3 -> "required"
        5 -> "incompatible"
        1, 6 -> "embedded"
        else -> "optional"
    }

    /** Null bila file tidak bisa diunduh lewat launcher (tanpa alamat unduhan atau tidak tersedia). */
    fun toVersion(file: JsonObject): ModrinthVersion? {
        val fileId = file.long("id") ?: return null
        val url = downloadUrlOf(file) ?: return null
        if (file.bool("isAvailable") == false) return null
        val fileName = file.str("fileName")?.takeIf { it.isNotBlank() } ?: return null
        val display = file.str("displayName")?.takeIf { it.isNotBlank() } ?: fileName

        val hashes = HashMap<String, String>()
        sha1Of(file)?.let { hashes["sha1"] = it }

        val deps = file.objects("dependencies").mapNotNull { dep ->
            val depMod = dep.long("modId") ?: return@mapNotNull null
            ModrinthDependency(
                versionId = null,
                projectId = prefixed(depMod),
                fileName = null,
                dependencyType = dependencyTypeOf(dep.int("relationType"))
            )
        }

        val releaseType = when (file.int("releaseType")) {
            2 -> "beta"
            3 -> "alpha"
            else -> "release"
        }

        val modId = file.long("modId")
        return ModrinthVersion(
            id = fileId.toString(),
            projectId = if (modId != null) prefixed(modId) else "",
            name = display,
            versionNumber = display,
            gameVersions = minecraftVersionsOf(file),
            loaders = loaderNamesOf(file),
            versionType = releaseType,
            files = listOf(
                ModrinthVersionFile(
                    url = url,
                    filename = fileName,
                    primary = true,
                    size = file.long("fileLength") ?: 0L,
                    hashes = if (hashes.isEmpty()) null else hashes
                )
            ),
            dependencies = deps
        )
    }

    /** Tanggal unggah (teks ISO 8601, urut sama dengan urutan waktu) untuk mengurutkan file. */
    fun fileDateOf(file: JsonObject): String = file.str("fileDate").orEmpty()

    fun releaseRankOf(file: JsonObject): Int = (file.int("releaseType") ?: 1).coerceIn(1, 3)

    /**
     * Urutan daftar versi: rilis stabil dulu, lalu beta, lalu alpha; di dalam tiap kelompok yang terbaru
     * di depan. Versi pertama itulah yang dipasang tombol "Pasang" di daftar, jadi tidak boleh alpha
     * selama masih ada rilis stabil.
     */
    fun orderedFiles(files: List<JsonObject>): List<JsonObject> =
        files.sortedWith(
            compareBy<JsonObject> { releaseRankOf(it) }
                .thenByDescending { fileDateOf(it) }
        )

    /**
     * Apakah loader file cocok dengan [wantedLoader]. File yang tidak menyebut loader apa pun (mod lama
     * atau pack yang tidak menandai loader) dianggap cocok, supaya tidak ikut tersaring habis.
     */
    fun loaderMatches(versionLoaders: List<String>, wantedLoader: String?): Boolean {
        if (wantedLoader.isNullOrBlank()) return true
        if (versionLoaders.isEmpty()) return true
        return versionLoaders.any { it.equals(wantedLoader, ignoreCase = true) }
    }

    // ------------------------------------------------------------------
    // Modpack CurseForge: manifest.json di dalam zip
    // ------------------------------------------------------------------

    class PackFile(val projectId: Long, val fileId: Long, val required: Boolean)

    class PackManifest(
        val name: String,
        val version: String,
        val minecraftVersion: String,
        /** Loader utama modpack ("forge", "fabric", "neoforge", "quilt"), atau null bila tidak disebut. */
        val loader: String?,
        /** Nama folder isi tambahan (biasanya "overrides"). */
        val overrides: String,
        val files: List<PackFile>
    )

    /** Null bila [root] bukan manifest modpack Minecraft dari CurseForge. */
    fun parseManifest(root: JsonObject): PackManifest? {
        val mc = root.obj("minecraft") ?: return null
        val filesElement = root.get("files")
        if (filesElement == null || !filesElement.isJsonArray) return null
        val type = root.str("manifestType")
        if (type != null && !type.equals("minecraftModpack", ignoreCase = true)) return null

        val loaders = mc.objects("modLoaders")
        val primary = loaders.firstOrNull { it.bool("primary") == true } ?: loaders.firstOrNull()
        val loader = primary?.str("id")?.substringBefore('-')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        val files = ArrayList<PackFile>()
        for (item in filesElement.asJsonArray) {
            if (!item.isJsonObject) continue
            val f = item.asJsonObject
            val pid = f.long("projectID") ?: continue
            val fid = f.long("fileID") ?: continue
            files.add(PackFile(pid, fid, f.bool("required") != false))
        }

        return PackManifest(
            name = root.str("name").orEmpty(),
            version = root.str("version").orEmpty(),
            minecraftVersion = mc.str("version").orEmpty(),
            loader = loader,
            overrides = root.str("overrides")?.trim()?.trim('/')?.takeIf { it.isNotEmpty() } ?: "overrides",
            files = files
        )
    }

    /** Satu file modpack yang sudah dicari alamat unduhannya lewat API. */
    class ResolvedFile(
        val fileId: Long,
        val modId: Long,
        val fileName: String,
        /** Null = penulis menutup unduhan lewat API. */
        val url: String?,
        val sha1: String?
    )

    fun toResolvedFile(file: JsonObject): ResolvedFile? {
        val id = file.long("id") ?: return null
        val name = file.str("fileName")?.takeIf { it.isNotBlank() } ?: return null
        return ResolvedFile(
            fileId = id,
            modId = file.long("modId") ?: 0L,
            fileName = name,
            url = downloadUrlOf(file),
            sha1 = sha1Of(file)
        )
    }

    /** Folder tujuan (relatif ke folder game) untuk file modpack menurut classId proyeknya. */
    fun folderFor(classId: Int?, fileName: String): String = when (classId) {
        12 -> "resourcepacks"
        6552 -> "shaderpacks"
        6 -> "mods"
        else -> if (fileName.endsWith(".jar", ignoreCase = true)) "mods" else "resourcepacks"
    }

    /** classId proyek dari objek mod hasil POST /v1/mods. */
    fun classIdOfMod(mod: JsonObject): Pair<Long, Int>? {
        val id = mod.long("id") ?: return null
        val cls = mod.int("classId") ?: return null
        return id to cls
    }
}
