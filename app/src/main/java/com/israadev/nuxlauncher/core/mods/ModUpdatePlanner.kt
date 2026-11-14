package com.israadev.nuxlauncher.core.mods

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.israadev.nuxlauncher.core.models.ModrinthVersion
import com.israadev.nuxlauncher.core.models.ModrinthVersionFile
import java.time.Instant

/** Alasan sebuah pembaruan mod sengaja tidak dipasang otomatis. */
enum class ModUpdateSkipReason {
    /** Versi baru tidak punya berkas .jar yang bisa dipakai atau tidak punya sidik jari untuk diverifikasi. */
    NO_FILE,

    /** Versi baru mewajibkan mod lain yang belum terpasang (dipasang manual lewat halaman mod). */
    NEW_DEPENDENCY,

    /** Versi baru menyatakan tidak cocok dengan mod lain yang sudah terpasang. */
    INCOMPATIBLE
}

/** Hasil keputusan untuk satu mod terpasang. */
internal sealed class ModUpdateDecision {
    /** Berkas tidak dikenal Modrinth (dari CurseForge, buatan sendiri, atau sudah dimodifikasi). */
    object Unknown : ModUpdateDecision()

    object UpToDate : ModUpdateDecision()

    class Update(val version: ModrinthVersion, val file: ModrinthVersionFile) : ModUpdateDecision()

    class Skip(val reason: ModUpdateSkipReason) : ModUpdateDecision()
}

/**
 * Bagian murni (tanpa Android dan tanpa jaringan) dari pemeriksaan pembaruan mod: menyusun isi permintaan
 * ke Modrinth, membaca jawabannya, dan memutuskan apakah sebuah mod perlu dan aman diperbarui.
 */
internal object ModUpdatePlanner {

    /** Jumlah sidik jari maksimal per permintaan, supaya badan permintaan tetap kecil. */
    const val MAX_HASHES_PER_REQUEST = 100

    /** Jarak minimal antar pemeriksaan otomatis untuk satu instance. */
    const val AUTO_CHECK_GAP_MS = 6L * 60L * 60L * 1000L

    private val gson = Gson()
    private val versionMapType = object : TypeToken<Map<String, ModrinthVersion?>>() {}.type

    fun isModJarName(name: String): Boolean =
        name.endsWith(".jar", ignoreCase = true) || name.endsWith(".jar.disabled", ignoreCase = true)

    fun cleanName(name: String): String = name.removeSuffix(".disabled")

    /** Isi POST /version_files: cari versi mod berdasarkan sidik jari berkasnya. */
    fun versionsRequestBody(hashes: List<String>): String {
        val body = JsonObject()
        body.add("hashes", stringArray(hashes))
        body.addProperty("algorithm", "sha1")
        return body.toString()
    }

    /** Isi POST /version_files/update: versi terbaru yang cocok dengan loader dan versi Minecraft instance. */
    fun updateRequestBody(hashes: List<String>, loader: String, mcVersion: String): String {
        val body = JsonObject()
        body.add("hashes", stringArray(hashes))
        body.addProperty("algorithm", "sha1")
        body.add("loaders", stringArray(listOf(loader)))
        body.add("game_versions", stringArray(listOf(mcVersion)))
        return body.toString()
    }

    private fun stringArray(items: List<String>): JsonArray {
        val array = JsonArray()
        for (item in items) array.add(item)
        return array
    }

    /** Membaca jawaban Modrinth (peta sidik jari -> versi). Kunci disamakan menjadi huruf kecil. */
    fun parseVersionMap(json: String): Map<String, ModrinthVersion> {
        val parsed: Map<String, ModrinthVersion?> = gson.fromJson(json, versionMapType) ?: return emptyMap()
        val result = HashMap<String, ModrinthVersion>(parsed.size)
        for ((hash, version) in parsed) {
            // Lewati nilai yang bukan objek versi utuh
            if (version == null || version.id.isBlank()) continue
            result[hash.lowercase()] = version
        }
        return result
    }

    /** True bila sudah waktunya memeriksa lagi. Jam yang mundur dianggap sudah waktunya. */
    fun isCheckDue(lastCheckMs: Long, nowMs: Long, gapMs: Long = AUTO_CHECK_GAP_MS): Boolean =
        lastCheckMs <= 0L || nowMs < lastCheckMs || nowMs - lastCheckMs >= gapMs

    private fun stabilityRank(type: String): Int = when (type.lowercase()) {
        "release" -> 0
        "beta" -> 1
        else -> 2
    }

    private fun parseInstant(text: String): Instant? {
        if (text.isBlank()) return null
        return try {
            Instant.parse(text.trim())
        } catch (_: Exception) {
            null
        }
    }

    /** True bila [latest] terbukti tidak lebih baru dari [current] (cegah pembaruan yang malah menurunkan versi). */
    private fun notNewer(latest: ModrinthVersion, current: ModrinthVersion): Boolean {
        val a = parseInstant(latest.datePublished) ?: return false
        val b = parseInstant(current.datePublished) ?: return false
        return !a.isAfter(b)
    }

    /** Berkas .jar utama dari sebuah versi, atau null bila tidak ada. */
    fun pickJar(version: ModrinthVersion): ModrinthVersionFile? {
        val jars = version.files.filter { it.filename.endsWith(".jar", ignoreCase = true) }
        return jars.firstOrNull { it.primary } ?: jars.firstOrNull()
    }

    private fun hasVerifiableHash(file: ModrinthVersionFile): Boolean {
        val hashes = file.hashes ?: return false
        return listOf("sha256", "sha512", "sha1").any { !hashes[it].isNullOrBlank() }
    }

    private fun projectsOf(version: ModrinthVersion, type: String): Set<String> =
        version.dependencies.orEmpty()
            .filter { it.dependencyType.equals(type, ignoreCase = true) && !it.projectId.isNullOrBlank() }
            .mapNotNull { it.projectId }
            .toSet()

    /**
     * Memutuskan nasib satu mod.
     *
     * @param sha1 sidik jari berkas yang terpasang
     * @param current versi yang cocok dengan sidik jari itu di Modrinth (null = tidak dikenal)
     * @param latest versi terbaru yang cocok dengan loader dan versi Minecraft instance (null = tidak ada)
     * @param installedProjects id proyek Modrinth dari semua mod terpasang yang dikenal
     */
    fun decide(
        sha1: String,
        current: ModrinthVersion?,
        latest: ModrinthVersion?,
        installedProjects: Set<String>
    ): ModUpdateDecision {
        if (current == null) return ModUpdateDecision.Unknown
        if (latest == null) return ModUpdateDecision.UpToDate
        if (latest.id == current.id) return ModUpdateDecision.UpToDate
        if (latest.files.any { it.hashes?.get("sha1").equals(sha1, ignoreCase = true) }) {
            return ModUpdateDecision.UpToDate
        }
        // Jangan menurunkan versi, dan jangan menukar rilis stabil dengan beta/alpha
        if (notNewer(latest, current)) return ModUpdateDecision.UpToDate
        if (stabilityRank(latest.versionType) > stabilityRank(current.versionType)) return ModUpdateDecision.UpToDate

        val file = pickJar(latest) ?: return ModUpdateDecision.Skip(ModUpdateSkipReason.NO_FILE)
        if (!hasVerifiableHash(file) || !file.url.trim().startsWith("https://", ignoreCase = true)) {
            return ModUpdateDecision.Skip(ModUpdateSkipReason.NO_FILE)
        }

        // Dependensi wajib yang baru muncul dan belum terpasang: jangan ditebak, biarkan pengguna memutuskan
        val newRequired = projectsOf(latest, "required") - projectsOf(current, "required")
        if (newRequired.any { it !in installedProjects }) {
            return ModUpdateDecision.Skip(ModUpdateSkipReason.NEW_DEPENDENCY)
        }
        val newIncompatible = projectsOf(latest, "incompatible") - projectsOf(current, "incompatible")
        if (newIncompatible.any { it in installedProjects && it != latest.projectId }) {
            return ModUpdateDecision.Skip(ModUpdateSkipReason.INCOMPATIBLE)
        }
        return ModUpdateDecision.Update(latest, file)
    }
}
