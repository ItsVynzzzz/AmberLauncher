package com.israadev.nuxlauncher.core.horizon

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.israadev.nuxlauncher.core.models.ModrinthProject
import com.israadev.nuxlauncher.core.models.ModrinthSearchHit
import com.israadev.nuxlauncher.core.models.ModrinthSearchResponse
import com.israadev.nuxlauncher.core.models.ModrinthVersion
import com.israadev.nuxlauncher.core.models.ModrinthVersionFile
import java.io.IOException

/**
 * Katalog mod Horizon (registry resmi mesin): satu berkas `index.json` yang dibangun CI repo horizon-mods.
 * Aturan mengikuti docs/api/mod-index.md milik mesin (schema 1):
 * - Hanya proyek berjenis `mod` (berkas .hmod) yang dibaca di sini; jenis lain dilewati.
 * - Setiap berkas wajib: nama polos berakhiran .hmod, alamat `https://github.com/<pemilik>/<repo>/releases/download/
 *   <tag>/<nama>` (satu-satunya bentuk yang diterima mesin), ukuran, dan SHA-256 huruf kecil. Yang tidak lengkap dibuang.
 * - Daftar `revoked` (berkas yang ditarik) tidak pernah ditawarkan.
 * - Yang ditawarkan hanya versi untuk versi game mesin, dan hanya yang paling besar 32 MB (batas mesin di Android).
 *
 * Tanda tangan minisign tidak diperiksa di Amber; keutuhan dijaga dengan ukuran dan SHA-256 dari katalog
 * (HTTPS), yang dicocokkan lagi sebelum berkas dipasang.
 *
 * Tanpa Android dan tanpa jaringan: [parse] menerima teks JSON yang sudah diunduh, jadi mudah diuji.
 */
object HorizonRegistry {

    const val INDEX_URL = "https://horizon-rust.github.io/horizon-mods/index.json"

    /** Batas ukuran katalog yang dibaca (cegah respons raksasa memenuhi memori HP). */
    const val MAX_INDEX_BYTES = 8L * 1024L * 1024L

    // Pesan galat ditampilkan apa adanya ke pengguna; semuanya ada di kamus bahasa (mods.tsv).
    const val MSG_INDEX = "Katalog mod Horizon tidak bisa dibaca. Coba lagi nanti."
    const val MSG_NEWER = "Katalog Horizon memakai format yang lebih baru. Perbarui Amber."
    const val MSG_NO_FILE = "Tidak ada berkas .hmod yang cocok di versi ini."
    const val MSG_HASH = "Katalog tidak memberi sidik jari SHA-256, jadi unduhan tidak bisa diperiksa."
    const val MSG_URL = "Alamat unduhan mod tidak dikenali, jadi ditolak."
    const val MSG_SIZE = "Ukuran berkas mod tidak sama dengan yang tertulis di katalog, jadi tidak dipasang."

    private val ID_RE = Regex("[a-z0-9_]{1,64}")
    private val SHA256_RE = Regex("[0-9a-f]{64}")
    private val URL_RE = Regex(
        "https://github\\.com/[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}/releases/download/[^/?#\\s]{1,200}/[^/?#\\s]{1,200}"
    )

    class FileInfo(val name: String, val url: String, val size: Long, val sha256: String)

    class VersionInfo(
        val version: String,
        val published: String,
        val changelog: String,
        val side: String,
        val capabilities: List<String>,
        val game: String,
        val file: FileInfo
    )

    class ProjectInfo(
        val id: String,
        val name: String,
        val summary: String,
        val description: String,
        val authors: List<String>,
        val license: String,
        val source: String,
        val icon: String?,
        val downloads: Long,
        val versions: List<VersionInfo>
    )

    /** Berkas yang ditarik: [sha256] kosong berarti semua versi [id]. */
    class Revoked(val id: String?, val sha256: String?, val reason: String)

    class Index(val mods: List<ProjectInfo>, val revoked: List<Revoked>)

    /** Alamat unduhan yang diterima mesin: rilis GitHub, dan nama di ujung alamat sama dengan nama berkasnya. */
    fun acceptedUrl(url: String, name: String): Boolean = URL_RE.matches(url) && url.substringAfterLast('/') == name

    fun isSha256(value: String): Boolean = SHA256_RE.matches(value)

    // ------------------------------------------------------------------------------------------
    // Membaca katalog
    // ------------------------------------------------------------------------------------------

    fun parse(json: String): Index {
        val root = try {
            JsonParser.parseString(json)
        } catch (_: Exception) {
            throw IOException(MSG_INDEX)
        }
        if (!root.isJsonObject) throw IOException(MSG_INDEX)
        val obj = root.asJsonObject
        val schema = obj.long("schema") ?: throw IOException(MSG_INDEX)
        if (schema > 1L) throw IOException(MSG_NEWER)

        val mods = ArrayList<ProjectInfo>()
        val seen = HashSet<String>()
        for (element in obj.array("projects")) {
            val p = element.asObjectOrNull() ?: continue
            if (p.string("kind") != "mod") continue
            val id = p.string("id") ?: continue
            if (!ID_RE.matches(id) || !seen.add(id)) continue
            val versions = p.array("versions").mapNotNull { e -> e.asObjectOrNull()?.let { o -> parseVersion(o) } }
            if (versions.isEmpty()) continue
            val summary = p.string("summary").orEmpty()
            val icon = p.string("icon")?.takeIf { it.startsWith("https://") }
            mods.add(
                ProjectInfo(
                    id = id,
                    name = p.string("name")?.takeIf { it.isNotBlank() } ?: id,
                    summary = summary,
                    description = p.string("description")?.takeIf { it.isNotBlank() } ?: summary,
                    authors = p.array("authors").mapNotNull { if (it.isJsonPrimitive) it.asString else null },
                    license = p.string("license").orEmpty(),
                    source = p.string("source")?.takeIf { it.startsWith("https://") }.orEmpty(),
                    icon = icon,
                    downloads = p.long("downloads") ?: 0L,
                    versions = versions
                )
            )
        }

        val revoked = ArrayList<Revoked>()
        for (element in obj.array("revoked")) {
            val r = element.asObjectOrNull() ?: continue
            val id = r.string("id")
            val sha = r.string("sha256")?.lowercase()
            if (id == null && sha == null) continue
            revoked.add(Revoked(id, sha, r.string("reason").orEmpty()))
        }
        return Index(mods, revoked)
    }

    private fun parseVersion(v: JsonObject): VersionInfo? {
        val version = v.string("version")?.takeIf { it.isNotBlank() } ?: return null
        val game = v.string("game")?.takeIf { it.isNotBlank() } ?: return null
        val f = v.get("file")?.asObjectOrNull() ?: return null
        val name = f.string("name") ?: return null
        val url = f.string("url") ?: return null
        val size = f.long("size") ?: return null
        val sha = f.string("sha256") ?: return null
        if (!HorizonContent.plainName(name) || !name.endsWith(HorizonMods.EXTENSION)) return null
        if (!acceptedUrl(url, name)) return null
        if (size <= 0L || size > HorizonMods.MAX_FILE_BYTES || !isSha256(sha)) return null
        return VersionInfo(
            version = version,
            published = v.string("published").orEmpty(),
            changelog = v.string("changelog").orEmpty(),
            side = v.string("side") ?: "client",
            capabilities = v.array("capabilities").mapNotNull { if (it.isJsonPrimitive) it.asString else null },
            game = game,
            file = FileInfo(name, url, size, sha)
        )
    }

    // ------------------------------------------------------------------------------------------
    // Menyaring dan mencari
    // ------------------------------------------------------------------------------------------

    private fun isRevoked(index: Index, projectId: String, file: FileInfo): Boolean =
        index.revoked.any { (it.id == null || it.id == projectId) && (it.sha256 == null || it.sha256 == file.sha256) }

    /** Versi yang boleh dipasang untuk [game]: bukan yang ditarik, terbaru lebih dulu (urutan katalog). */
    fun usableVersions(index: Index, project: ProjectInfo, game: String): List<VersionInfo> =
        project.versions.filter { it.game == game && !isRevoked(index, project.id, it.file) }

    fun find(index: Index, id: String): ProjectInfo? = index.mods.firstOrNull { it.id == id }

    /** Mod konten murni (data dan aset, tanpa kode): aman dihidupkan langsung. */
    fun contentOnly(version: VersionInfo): Boolean = version.capabilities == listOf("registries")

    fun search(
        index: Index,
        query: String,
        game: String,
        sort: String,
        page: Int,
        limit: Int
    ): ModrinthSearchResponse {
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val scored = ArrayList<Pair<Int, ProjectInfo>>()
        for (project in index.mods) {
            if (usableVersions(index, project, game).isEmpty()) continue
            val name = project.name.lowercase()
            val haystack = listOf(name, project.id, project.summary.lowercase(), project.authors.joinToString(" ").lowercase())
                .joinToString(" ")
            if (!tokens.all { haystack.contains(it) }) continue
            val score = when {
                tokens.isEmpty() -> 0
                name == query.trim().lowercase() -> 3
                name.startsWith(tokens.first()) -> 2
                tokens.all { name.contains(it) || project.id.contains(it) } -> 1
                else -> 0
            }
            scored.add(score to project)
        }
        fun latest(p: ProjectInfo) = usableVersions(index, p, game).firstOrNull()?.published.orEmpty()
        val sorted = when (sort) {
            "downloads", "follows" -> scored.sortedWith(compareByDescending<Pair<Int, ProjectInfo>> { it.second.downloads }.thenBy { it.second.name.lowercase() })
            "newest", "updated" -> scored.sortedWith(compareByDescending<Pair<Int, ProjectInfo>> { latest(it.second) }.thenBy { it.second.name.lowercase() })
            else -> scored.sortedWith(compareByDescending<Pair<Int, ProjectInfo>> { it.first }.thenByDescending { it.second.downloads }.thenBy { it.second.name.lowercase() })
        }
        val safeLimit = limit.coerceAtLeast(1)
        val offset = ((page - 1).coerceAtLeast(0)) * safeLimit
        val hits = sorted.drop(offset).take(safeLimit).map { toHit(index, it.second, game) }
        return ModrinthSearchResponse(hits = hits, offset = offset, limit = safeLimit, totalHits = sorted.size)
    }

    // ------------------------------------------------------------------------------------------
    // Diubah ke bentuk yang dipakai layar Mod (sama dengan hasil Modrinth)
    // ------------------------------------------------------------------------------------------

    private fun toHit(index: Index, p: ProjectInfo, game: String): ModrinthSearchHit {
        val newest = usableVersions(index, p, game).firstOrNull()
        val tag = if (newest != null && contentOnly(newest)) "konten" else "berkode"
        return ModrinthSearchHit(
            projectId = p.id,
            projectType = "mod",
            slug = p.id,
            author = p.authors.firstOrNull(),
            title = p.name,
            description = p.summary,
            categories = listOf(tag),
            displayCategories = listOf(tag),
            versions = listOf(game),
            downloads = p.downloads,
            iconUrl = p.icon
        )
    }

    fun toProject(p: ProjectInfo, game: String): ModrinthProject {
        val extra = ArrayList<String>()
        if (p.license.isNotBlank()) extra.add(p.license)
        if (p.authors.isNotEmpty()) extra.add(p.authors.joinToString(", "))
        val body = (if (extra.isEmpty()) "" else extra.joinToString(" \u2022 ") + "\n\n") + p.description
        return ModrinthProject(
            id = p.id,
            slug = p.id,
            title = p.name,
            description = p.summary,
            body = body,
            categories = null,
            loaders = null,
            gameVersions = listOf(game),
            downloads = p.downloads,
            iconUrl = p.icon,
            gallery = null
        )
    }

    fun toVersions(index: Index, p: ProjectInfo, game: String): List<ModrinthVersion> =
        usableVersions(index, p, game).map { v ->
            ModrinthVersion(
                id = p.id + ":" + v.version,
                projectId = p.id,
                name = v.version,
                versionNumber = v.version,
                gameVersions = listOf(v.game),
                loaders = emptyList(),
                versionType = "release",
                files = listOf(
                    ModrinthVersionFile(
                        url = v.file.url,
                        filename = v.file.name,
                        primary = true,
                        size = v.file.size,
                        hashes = mapOf("sha256" to v.file.sha256)
                    )
                ),
                dependencies = null
            )
        }

    // ------------------------------------------------------------------------------------------
    // Pembantu JSON yang longgar (kolom tak dikenal dan tipe yang salah diabaikan, bukan galat)
    // ------------------------------------------------------------------------------------------

    private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun JsonObject.string(key: String): String? {
        val e = get(key) ?: return null
        return if (e.isJsonPrimitive && e.asJsonPrimitive.isString) e.asString else null
    }

    private fun JsonObject.long(key: String): Long? {
        val e = get(key) ?: return null
        return if (e.isJsonPrimitive && e.asJsonPrimitive.isNumber) {
            try {
                e.asLong
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun JsonObject.array(key: String): List<JsonElement> {
        val e = get(key) ?: return emptyList()
        return if (e.isJsonArray) e.asJsonArray.toList() else emptyList()
    }
}
