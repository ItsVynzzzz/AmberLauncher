package com.israadev.nuxlauncher.core.mods

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.israadev.nuxlauncher.BuildConfig
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.models.ModrinthProject
import com.israadev.nuxlauncher.core.models.ModrinthSearchResponse
import com.israadev.nuxlauncher.core.models.ModrinthVersion
import com.israadev.nuxlauncher.core.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

/**
 * Klien CurseForge (https://api.curseforge.com/v1). Hasilnya dipetakan oleh [CurseForgeMapper] ke
 * model yang sama dengan Modrinth.
 *
 * Kunci API: kunci yang diisi pengguna di aplikasi dipakai lebih dulu; bila kosong dipakai kunci yang
 * dipasang saat build (BuildConfig.CURSEFORGE_API_KEY, dari Secrets). Kunci hanya dikirim ke
 * api.curseforge.com lewat header x-api-key, tidak pernah ke server unduhan, dan tidak pernah dicatat
 * atau dimasukkan ke pesan galat.
 *
 * Mod yang penulisnya menutup unduhan lewat API (downloadUrl kosong) sengaja tidak dipaksa diunduh:
 * pilihan penulis dihormati dan pengguna diberi tahu.
 */
object CurseForgeApi {
    private const val BASE = "https://api.curseforge.com/v1"

    /** Batas CurseForge: satu halaman maksimal 50 item, dan index + pageSize tidak boleh lewat 10.000. */
    private const val MAX_PAGE_SIZE = 50
    private const val MAX_RESULT_WINDOW = 10_000

    /** Jumlah id per permintaan massal (POST /mods/files dan /mods). */
    private const val BULK_CHUNK = 100

    /** Kunci API belum diisi atau ditolak server; layar menawarkan mengisi ulang kunci. */
    class KeyException(message: String) : Exception(message)

    /** Kunci aktif: isian pengguna, atau kunci bawaan build. Kosong = belum ada. */
    fun apiKey(): String {
        val own = SettingsManager.settings.value.curseForgeApiKey.trim()
        return if (own.isNotEmpty()) own else BuildConfig.CURSEFORGE_API_KEY.trim()
    }

    fun isConfigured(): Boolean = apiKey().isNotEmpty()

    // ------------------------------------------------------------------
    // Permintaan dasar
    // ------------------------------------------------------------------

    private fun query(vararg params: Pair<String, String?>): String {
        val sb = StringBuilder()
        for ((name, value) in params) {
            if (value.isNullOrEmpty()) continue
            sb.append(if (sb.isEmpty()) '?' else '&')
            sb.append(name).append('=').append(URLEncoder.encode(value, "UTF-8"))
        }
        return sb.toString()
    }

    private fun authed(url: String): Request.Builder {
        val key = apiKey()
        if (key.isEmpty()) throw KeyException("Kunci API CurseForge belum diisi.")
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("x-api-key", key)
            .header("User-Agent", NuxModManager.USER_AGENT)
    }

    private fun httpError(code: Int): Exception = when (code) {
        401, 403 -> KeyException("Kunci API CurseForge ditolak (kode $code). Periksa kuncinya lalu isi ulang.")
        429 -> Exception("CurseForge membatasi permintaan. Tunggu sebentar lalu coba lagi.")
        else -> Exception("CurseForge API error: kode $code")
    }

    private fun execute(request: Request): JsonObject {
        return NuxModManager.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpError(response.code)
            val body = response.body?.string() ?: throw Exception("Respons kosong dari CurseForge")
            val parsed = JsonParser.parseString(body)
            if (!parsed.isJsonObject) throw Exception("Respons CurseForge tidak dikenali")
            parsed.asJsonObject
        }
    }

    private fun gameVersionOf(instance: Instance?, filterByGameVersion: Boolean): String? =
        if (filterByGameVersion && instance != null && instance.mcVersion.isNotBlank()) instance.mcVersion else null

    // ------------------------------------------------------------------
    // Cari, detail, versi
    // ------------------------------------------------------------------

    suspend fun search(
        queryText: String,
        itemType: String,
        instance: Instance?,
        filterByGameVersion: Boolean,
        page: Int,
        limit: Int,
        sortIndex: String,
        filterByLoader: Boolean
    ): Result<ModrinthSearchResponse> = withContext(Dispatchers.IO) {
        try {
            val size = limit.coerceIn(1, MAX_PAGE_SIZE)
            val offset = (page - 1).coerceAtLeast(0) * size
            if (offset + size > MAX_RESULT_WINDOW) {
                return@withContext Result.failure(Exception("CurseForge hanya menampilkan $MAX_RESULT_WINDOW hasil pertama. Persempit pencarian."))
            }
            val gameVersion = gameVersionOf(instance, filterByGameVersion)
            // CurseForge hanya menyaring loader bila versi Minecraft ikut dikirim
            val loaderType = if (gameVersion != null && filterByLoader && itemType == "mods") {
                CurseForgeMapper.loaderTypeFor(NuxModManager.effectiveLoader(instance))
            } else {
                0
            }

            val url = BASE + "/mods/search" + query(
                "gameId" to CurseForgeMapper.GAME_ID.toString(),
                "classId" to CurseForgeMapper.classIdFor(itemType).toString(),
                "searchFilter" to queryText.trim().takeIf { it.isNotEmpty() },
                "gameVersion" to gameVersion,
                "modLoaderType" to loaderType.takeIf { it != 0 }?.toString(),
                "sortField" to CurseForgeMapper.sortFieldFor(sortIndex).toString(),
                "sortOrder" to "desc",
                "index" to offset.toString(),
                "pageSize" to size.toString()
            )
            val root = execute(authed(url).build())
            val hits = CurseForgeMapper.dataObjects(root).mapNotNull { CurseForgeMapper.toSearchHit(it, itemType) }
            val total = CurseForgeMapper.totalCountOf(root).coerceIn(0, MAX_RESULT_WINDOW)
            Result.success(ModrinthSearchResponse(hits = hits, offset = offset, limit = size, totalHits = total))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun projectDetails(prefixedId: String): Result<ModrinthProject> = withContext(Dispatchers.IO) {
        try {
            val id = CurseForgeMapper.modIdOf(prefixedId)
                ?: return@withContext Result.failure(Exception("Id CurseForge tidak valid"))
            val root = execute(authed("$BASE/mods/$id").build())
            val mod = CurseForgeMapper.dataObject(root)
            val project = if (mod != null) CurseForgeMapper.toProject(mod) else null
            if (project == null) {
                Result.failure(Exception("Detail CurseForge kosong"))
            } else {
                Result.success(project)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * File yang cocok dengan instance dan bisa diunduh lewat launcher, rilis stabil lebih dulu lalu yang
     * terbaru. Gagal dengan pesan jelas bila semua file ditutup unduhannya oleh penulis.
     */
    suspend fun compatibleVersions(
        prefixedId: String,
        instance: Instance?,
        itemType: String,
        filterByGameVersion: Boolean
    ): Result<List<ModrinthVersion>> = withContext(Dispatchers.IO) {
        try {
            val id = CurseForgeMapper.modIdOf(prefixedId)
                ?: return@withContext Result.failure(Exception("Id CurseForge tidak valid"))
            val gameVersion = gameVersionOf(instance, filterByGameVersion)
            val wantedLoader = if (instance != null && itemType == "mods") NuxModManager.effectiveLoader(instance) else null
            val loaderType = if (gameVersion != null && wantedLoader != null) CurseForgeMapper.loaderTypeFor(wantedLoader) else 0

            val url = "$BASE/mods/$id/files" + query(
                "gameVersion" to gameVersion,
                "modLoaderType" to loaderType.takeIf { it != 0 }?.toString(),
                "index" to "0",
                "pageSize" to MAX_PAGE_SIZE.toString()
            )
            val root = execute(authed(url).build())
            val raw = CurseForgeMapper.dataObjects(root)
            val usable = CurseForgeMapper.orderedFiles(raw)
                .mapNotNull { CurseForgeMapper.toVersion(it) }
                .filter { CurseForgeMapper.loaderMatches(it.loaders, wantedLoader) }

            if (usable.isEmpty() && raw.isNotEmpty() && raw.all { CurseForgeMapper.downloadUrlOf(it) == null }) {
                Result.failure(Exception(CurseForgeMapper.BLOCKED_NOTE))
            } else {
                Result.success(usable)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------
    // Modpack: cari alamat unduhan banyak file sekaligus (blocking, panggil dari thread IO)
    // ------------------------------------------------------------------

    private fun idsBody(field: String, ids: List<Long>): okhttp3.RequestBody {
        val arr = JsonArray()
        for (id in ids) arr.add(id)
        val obj = JsonObject()
        obj.add(field, arr)
        return obj.toString().toRequestBody("application/json".toMediaType())
    }

    /** Alamat unduhan, nama, dan sidik jari tiap file (kunci = id file). File yang tidak dikenal CurseForge tidak ada di hasil. */
    fun resolveFiles(fileIds: List<Long>): Map<Long, CurseForgeMapper.ResolvedFile> {
        val out = HashMap<Long, CurseForgeMapper.ResolvedFile>()
        for (chunk in fileIds.distinct().chunked(BULK_CHUNK)) {
            val request = authed("$BASE/mods/files")
                .post(idsBody("fileIds", chunk))
                .build()
            val root = execute(request)
            for (f in CurseForgeMapper.dataObjects(root)) {
                CurseForgeMapper.toResolvedFile(f)?.let { out[it.fileId] = it }
            }
        }
        return out
    }

    /** classId tiap proyek (kunci = id proyek). Hanya pelengkap: bila gagal, hasilnya kosong. */
    fun classIdsOf(modIds: List<Long>): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        for (chunk in modIds.distinct().chunked(BULK_CHUNK)) {
            try {
                val request = authed("$BASE/mods")
                    .post(idsBody("modIds", chunk))
                    .build()
                val root = execute(request)
                for (m in CurseForgeMapper.dataObjects(root)) {
                    CurseForgeMapper.classIdOfMod(m)?.let { out[it.first] = it.second }
                }
            } catch (e: Exception) {
                if (e is KeyException) throw e
            }
        }
        return out
    }
}
