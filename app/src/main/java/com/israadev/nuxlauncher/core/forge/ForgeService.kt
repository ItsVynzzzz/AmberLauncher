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
 * Daftar versi Forge/NeoForge dan alamat installer-nya.
 *
 * Yang didukung: Forge untuk Minecraft 1.7.10 ke atas dan NeoForge untuk 1.20.2 ke atas. Forge
 * 1.12.2 ke bawah (installer gaya lama, LaunchWrapper, LWJGL 2 lewat jembatan lwjglx) dipasang oleh
 * [ForgeLegacyInstaller]. Layanan ini tidak berjalan sendiri: tidak ada permintaan jaringan sampai
 * pengguna memilih Forge/NeoForge di dialog buat instance.
 */
object ForgeService {

    /** Versi Minecraft terendah yang didukung Forge. */
    const val MIN_MC_VERSION = "1.7.10"

    /** Mulai versi ini Forge memakai installer modern (processors); di bawahnya installer gaya lama. */
    private const val MODERN_FORGE_MC_VERSION = "1.13"

    private const val PROMOS_URL = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
    private const val BMCL_LIST_URL = "https://bmclapi2.bangbang93.com/forge/minecraft"
    private const val MAVEN_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge"

    private const val NEO_METADATA_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
    private const val NEO_BMCL_LIST_URL = "https://bmclapi2.bangbang93.com/neoforge/list"
    private const val NEO_MAVEN_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge"

    private val gson = Gson()
    private val releaseRegex = Regex("""^\d+\.\d+(\.\d+)?$""")

    /** Hasil daftar per jenis dan versi Minecraft; dialog buat instance sering memuat ulang daftar yang sama. */
    private val cache = ConcurrentHashMap<String, List<ForgeBuild>>()

    /** Seluruh versi NeoForge per versi Minecraft; satu berkas metadata melayani semua versi Minecraft. */
    @Volatile
    private var neoForgeByMc: Map<String, List<String>>? = null

    private data class BmclFile(val category: String? = null)

    private data class BmclForge(
        val version: String? = null,
        val mcversion: String? = null,
        val branch: String? = null,
        val files: List<BmclFile>? = null
    )

    /** True bila [mcVersion] adalah rilis resmi yang didukung [flavor] (snapshot tidak didukung). */
    fun isMcSupported(mcVersion: String, flavor: ForgeFlavor = ForgeFlavor.FORGE): Boolean {
        val v = mcVersion.trim()
        if (!releaseRegex.matches(v)) return false
        return !NuxVersionUtils.isLowerVer(v, flavor.minMcVersion)
    }

    /** True bila [mcVersion] adalah rilis Minecraft yang memakai Forge gaya lama (1.12.2 ke bawah). */
    fun isLegacyMc(mcVersion: String): Boolean {
        val v = mcVersion.trim()
        return releaseRegex.matches(v) && NuxVersionUtils.isLowerVer(v, MODERN_FORGE_MC_VERSION)
    }

    /**
     * Semua alamat installer yang layak dicoba, berurutan. Build Forge lama bercabang punya akhiran
     * cabang di nama artefaknya ("1.8.9-11.15.1.2318-1.8.9"); daftar dari mirror sudah memuatnya, tetapi
     * daftar promosi resmi tidak, jadi bentuk bersuffix dicoba juga.
     */
    fun installerUrls(mcVersion: String, version: String, flavor: ForgeFlavor = ForgeFlavor.FORGE): List<String> {
        val primary = installerUrl(mcVersion, version, flavor)
        if (flavor != ForgeFlavor.FORGE || !isLegacyMc(mcVersion) || version.endsWith("-$mcVersion")) {
            return listOf(primary)
        }
        return listOf(primary, installerUrl(mcVersion, "$version-$mcVersion", flavor))
    }

    /** Alamat installer resmi; mirror BMCLAPI ditambahkan otomatis oleh pengunduh. */
    fun installerUrl(mcVersion: String, version: String, flavor: ForgeFlavor = ForgeFlavor.FORGE): String =
        when (flavor) {
            ForgeFlavor.FORGE -> {
                val v = "$mcVersion-$version"
                "$MAVEN_URL/$v/forge-$v-installer.jar"
            }
            ForgeFlavor.NEOFORGE -> "$NEO_MAVEN_URL/$version/neoforge-$version-installer.jar"
        }

    /** Pilihan bawaan: build "recommended" bila ada, kalau tidak yang terbaru. */
    fun pickDefault(builds: List<ForgeBuild>): ForgeBuild? =
        builds.firstOrNull { it.recommended } ?: builds.firstOrNull { it.latest } ?: builds.firstOrNull()

    /**
     * Daftar build untuk [mcVersion], terbaru lebih dulu. Forge: daftar penuh dari BMCLAPI; bila
     * mirror itu gagal, dipakai build "recommended"/"latest" dari server resmi. NeoForge: dari
     * berkas metadata Maven resmi; bila gagal, dari BMCLAPI.
     */
    suspend fun getBuilds(
        mcVersion: String,
        flavor: ForgeFlavor = ForgeFlavor.FORGE
    ): Result<List<ForgeBuild>> = withContext(Dispatchers.IO) {
        if (!isMcSupported(mcVersion, flavor)) {
            return@withContext Result.failure(
                IllegalArgumentException("${flavor.displayName} hanya mendukung Minecraft ${flavor.minMcVersion} ke atas")
            )
        }
        val key = "${flavor.loaderId}:$mcVersion"
        cache[key]?.let { return@withContext Result.success(it) }

        val result = when (flavor) {
            ForgeFlavor.FORGE -> loadForgeBuilds(mcVersion)
            ForgeFlavor.NEOFORGE -> loadNeoForgeBuilds(mcVersion)
        }
        result.getOrNull()?.let { cache[key] = it }
        result
    }

    /**
     * Versi Minecraft yang punya build [flavor] dan didukung Amber. Satu permintaan (Forge: daftar
     * promosi resmi; NeoForge: berkas metadata Maven yang sama dengan daftar build). Dipakai layar buat
     * instance supaya hanya menampilkan versi yang memang punya loader itu. Gagal = layar memakai
     * perkiraan sendiri.
     */
    suspend fun getSupportedMcVersions(flavor: ForgeFlavor): Result<Set<String>> = withContext(Dispatchers.IO) {
        try {
            val found: Set<String> = when (flavor) {
                ForgeFlavor.FORGE -> fetchPromos().keys
                    .map { it.substringBeforeLast('-') } // "1.20.1-latest" menjadi "1.20.1"
                    .toSet()
                ForgeFlavor.NEOFORGE -> {
                    val grouped = neoForgeByMc ?: NeoForgeVersions
                        .groupByMc(NeoForgeVersions.parseMetadataVersions(httpGet(NEO_METADATA_URL)))
                        .also { if (it.isNotEmpty()) neoForgeByMc = it }
                    grouped.keys
                }
            }
            val supported = found.filter { isMcSupported(it, flavor) }.toSet()
            if (supported.isEmpty()) {
                Result.failure<Set<String>>(IOException("Tidak ada versi ${flavor.displayName} yang didukung"))
            } else {
                Result.success(supported)
            }
        } catch (e: Exception) {
            Result.failure<Set<String>>(e)
        }
    }

    private suspend fun loadForgeBuilds(mcVersion: String): Result<List<ForgeBuild>> {
        val (promos, all) = coroutineScope {
            val p = async { runCatching { fetchPromos() }.getOrNull() }
            val l = async { runCatching { fetchBmclList(mcVersion) } }
            Pair(p.await(), l.await())
        }

        val legacy = isLegacyMc(mcVersion)
        val versions = LinkedHashSet<String>()
        all.getOrNull()?.let { versions.addAll(it) }

        // Promosi resmi tidak memuat akhiran cabang Forge lama; dicocokkan dengan build dari mirror
        fun canonical(promo: String): String =
            if (legacy) versions.firstOrNull { it.substringBefore('-') == promo } ?: promo else promo

        val recommended = promos?.get("$mcVersion-recommended")?.let { canonical(it) }
        val latest = promos?.get("$mcVersion-latest")?.let { canonical(it) }

        // Promosi resmi selalu dimasukkan: dipakai penuh bila mirror gagal
        recommended?.let { versions.add(it) }
        latest?.let { versions.add(it) }

        if (versions.isEmpty()) {
            val cause = all.exceptionOrNull() ?: IOException("Tidak ada versi Forge untuk $mcVersion")
            return Result.failure(cause)
        }

        val builds = versions
            .sortedWith(Comparator { a, b -> NuxVersionUtils.compare(b, a) })
            .map { ForgeBuild(mcVersion, it, recommended = it == recommended, latest = it == latest) }
        return Result.success(builds)
    }

    private fun loadNeoForgeBuilds(mcVersion: String): Result<List<ForgeBuild>> {
        var failure: Throwable? = null

        // Metadata resmi memuat semua versi sekaligus, jadi cukup diambil sekali per sesi
        val official = neoForgeByMc ?: try {
            NeoForgeVersions.groupByMc(NeoForgeVersions.parseMetadataVersions(httpGet(NEO_METADATA_URL)))
                .takeIf { it.isNotEmpty() }
                ?.also { neoForgeByMc = it }
        } catch (e: Exception) {
            failure = e
            null
        }
        official?.get(mcVersion)?.let { versions ->
            val builds = NeoForgeVersions.toBuilds(mcVersion, versions)
            if (builds.isNotEmpty()) return Result.success(builds)
        }

        // Cadangan: mirror BMCLAPI, dipakai bila server resmi tak terjangkau
        try {
            val builds = NeoForgeVersions.toBuilds(
                mcVersion,
                NeoForgeVersions.parseBmclList(httpGet("$NEO_BMCL_LIST_URL/$mcVersion"))
            )
            if (builds.isNotEmpty()) return Result.success(builds)
        } catch (e: Exception) {
            if (failure == null) failure = e
        }
        return Result.failure(failure ?: IOException("Tidak ada versi NeoForge untuk $mcVersion"))
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
        val legacy = isLegacyMc(mcVersion)
        return items
            // Forge modern: build bercabang punya nama artefak berbeda dan dilewati. Forge lama: akhiran
            // cabang ikut ke nama versi ("11.15.1.2318-1.8.9"), supaya "<mc>-<versi>" menjadi nama artefak.
            .filter { legacy || it.branch.isNullOrBlank() }
            .filter { it.mcversion == null || it.mcversion == mcVersion }
            // Build lama tanpa installer tidak bisa dipasang
            .filter { !legacy || it.files.isNullOrEmpty() || it.files.any { f -> f.category == "installer" } }
            .mapNotNull { item ->
                val version = item.version?.trim()?.takeIf { v -> v.isNotEmpty() } ?: return@mapNotNull null
                val branch = item.branch?.trim()?.takeIf { legacy && it.isNotEmpty() }
                if (branch != null) "$version-$branch" else version
            }
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
