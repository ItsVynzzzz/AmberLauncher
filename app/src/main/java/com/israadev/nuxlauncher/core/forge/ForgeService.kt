package com.israadev.nuxlauncher.core.forge

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.israadev.nuxlauncher.core.fabric.FabricService
import com.israadev.nuxlauncher.core.utils.NuxVersionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Daftar versi Forge dan alamat installer-nya.
 *
 * Hanya Forge modern (Minecraft 1.13 ke atas) yang didukung: Amber memakai LWJGL 3, sedangkan
 * Forge untuk 1.12.2 ke bawah butuh LWJGL 2. Layanan ini tidak berjalan sendiri: tidak ada
 * permintaan jaringan sampai pengguna memilih Forge di dialog buat instance.
 */
object ForgeService {

    /** Versi Minecraft terendah yang didukung. */
    const val MIN_MC_VERSION = "1.13"

    private const val PROMOS_URL = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
    private const val BMCL_LIST_URL = "https://bmclapi2.bangbang93.com/forge/minecraft"
    private const val MAVEN_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge"

    private val gson = Gson()
    private val releaseRegex = Regex("""^\d+\.\d+(\.\d+)?$""")

    /** Hasil daftar per versi Minecraft; dialog buat instance sering memuat ulang daftar yang sama. */
    private val cache = ConcurrentHashMap<String, List<ForgeBuild>>()

    private data class BmclForge(
        val version: String? = null,
        val mcversion: String? = null,
        val branch: String? = null
    )

    /** True bila [mcVersion] adalah rilis resmi 1.13 ke atas (snapshot tidak didukung Forge). */
    fun isMcSupported(mcVersion: String): Boolean {
        val v = mcVersion.trim()
        if (!releaseRegex.matches(v)) return false
        return !NuxVersionUtils.isLowerVer(v, MIN_MC_VERSION)
    }

    /** Alamat installer resmi; mirror BMCLAPI ditambahkan otomatis oleh pengunduh. */
    fun installerUrl(mcVersion: String, forgeVersion: String): String {
        val v = "$mcVersion-$forgeVersion"
        return "$MAVEN_URL/$v/forge-$v-installer.jar"
    }

    /** Pilihan bawaan: build "recommended" bila ada, kalau tidak yang terbaru. */
    fun pickDefault(builds: List<ForgeBuild>): ForgeBuild? =
        builds.firstOrNull { it.recommended } ?: builds.firstOrNull { it.latest } ?: builds.firstOrNull()

    /**
     * Daftar build Forge untuk [mcVersion], terbaru lebih dulu. Daftar penuh dari BMCLAPI; bila
     * mirror itu gagal, dipakai build "recommended"/"latest" dari server resmi Forge.
     */
    suspend fun getBuilds(mcVersion: String): Result<List<ForgeBuild>> = withContext(Dispatchers.IO) {
        if (!isMcSupported(mcVersion)) {
            return@withContext Result.failure(
                IllegalArgumentException("Forge hanya mendukung Minecraft $MIN_MC_VERSION ke atas")
            )
        }
        cache[mcVersion]?.let { return@withContext Result.success(it) }

        val (promos, all) = coroutineScope {
            val p = async { runCatching { fetchPromos() }.getOrNull() }
            val l = async { runCatching { fetchBmclList(mcVersion) } }
            Pair(p.await(), l.await())
        }

        val recommended = promos?.get("$mcVersion-recommended")
        val latest = promos?.get("$mcVersion-latest")

        val versions = LinkedHashSet<String>()
        all.getOrNull()?.let { versions.addAll(it) }
        // Promosi resmi selalu dimasukkan: dipakai penuh bila mirror gagal
        recommended?.let { versions.add(it) }
        latest?.let { versions.add(it) }

        if (versions.isEmpty()) {
            val cause = all.exceptionOrNull() ?: IOException("Tidak ada versi Forge untuk $mcVersion")
            return@withContext Result.failure(cause)
        }

        val builds = versions
            .sortedWith(Comparator { a, b -> NuxVersionUtils.compare(b, a) })
            .map { ForgeBuild(mcVersion, it, recommended = it == recommended, latest = it == latest) }
        cache[mcVersion] = builds
        Result.success(builds)
    }

    /** Peta "<mc>-recommended" / "<mc>-latest" -> versi Forge, dari server resmi. */
    private fun fetchPromos(): Map<String, String> {
        val body = httpGet(PROMOS_URL)
        val promos = JsonParser.parseString(body).asJsonObject.getAsJsonObject("promos")
            ?: return emptyMap()
        return promos.entrySet().mapNotNull { (k, v) ->
            if (v.isJsonPrimitive) k to v.asString else null
        }.toMap()
    }

    private fun fetchBmclList(mcVersion: String): List<String> {
        val body = httpGet("$BMCL_LIST_URL/$mcVersion")
        val type = object : TypeToken<List<BmclForge>>() {}.type
        val items: List<BmclForge> = gson.fromJson(body, type) ?: emptyList()
        return items
            .filter { it.branch.isNullOrBlank() } // build bercabang (gaya lama) punya nama artefak berbeda
            .filter { it.mcversion == null || it.mcversion == mcVersion }
            .mapNotNull { it.version?.trim()?.takeIf { v -> v.isNotEmpty() } }
            .distinct()
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AmberLauncher/1.0 (Android)")
            .build()
        FabricService.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} dari $url")
            return response.body?.string() ?: throw IOException("Respons kosong dari $url")
        }
    }
}
