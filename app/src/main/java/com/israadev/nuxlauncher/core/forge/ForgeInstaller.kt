package com.israadev.nuxlauncher.core.forge

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.israadev.nuxlauncher.core.download.MinecraftDownloader
import com.israadev.nuxlauncher.core.fabric.FabricService
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.runtime.JavaRuntimeManager
import com.israadev.nuxlauncher.core.utils.SafePaths
import com.israadev.nuxlauncher.ui.i18n.Tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipFile

/**
 * Memasang Forge atau NeoForge ke folder versions/libraries milik Amber (keduanya memakai format
 * installer yang sama; bedanya diatur oleh [ForgeFlavor]).
 *
 * Alurnya sengaja dibuat hemat: semua unduhan (installer, library, mappings Mojang) dikerjakan di
 * sini, paralel dan dengan cek sidik jari, lalu installer resminya dijalankan offline di
 * proses terpisah (:forge) hanya untuk memverifikasi dan membuat jar Minecraft yang ditambal.
 * Pemasangan hanya terjadi sekali per versi; sesudahnya tidak ada biaya apa pun saat
 * launcher berjalan, dan proses :forge sudah mati.
 *
 * Penanda "terpasang" adalah berkas versions/<mc>/<forge|neoforge>-<versi>.json, ditulis paling akhir.
 */
object ForgeInstaller {

    private const val TAG = "ForgeInstaller"

    /** Rentang progress yang dipakai pemasangan Forge di dalam proses unduh instance. */
    private const val P_START = 0.92f
    private const val P_END = 0.995f

    private const val LIB_THREADS = 16

    /** Jeda sebelum installer dijalankan ulang, supaya proses :forge sebelumnya sudah dimatikan sistem. */
    private const val RESTART_DELAY_MS = 600L

    /** Ruang kosong minimum (MB): library, jar hasil tambalan, dan berkas sementara processor. */
    private const val MIN_FREE_MB = 400L

    private val gson = Gson()

    /** Satu pemasangan pada satu waktu: folder kerja dan proses :forge hanya ada satu. */
    private val installLock = Mutex()

    /** Berkas profil yang dipasang untuk [forgeVersion], atau (bila kosong) yang pertama ditemukan. */
    fun findProfileFile(
        context: Context,
        mcVersion: String,
        forgeVersion: String,
        flavor: ForgeFlavor = ForgeFlavor.FORGE
    ): File? {
        val vDir = File(InstanceManager.getVersionsDir(context), mcVersion)
        val exact = if (forgeVersion.isNotBlank()) File(vDir, SafePaths.fileName(flavor.markerName(forgeVersion))) else null
        if (exact != null && exact.exists() && exact.length() > 0L) return exact
        if (forgeVersion.isNotBlank()) return null
        val prefix = flavor.loaderId + "-"
        return vDir.listFiles { f -> f.name.startsWith(prefix) && f.name.endsWith(".json") && f.length() > 0L }
            ?.minByOrNull { it.name }
    }

    /**
     * Murah dan aman dipanggil dari komposisi UI: hanya mengecek keberadaan berkas, tanpa membaca
     * isinya. Penanda profil ditulis paling akhir, jadi keberadaannya berarti pemasangan selesai.
     */
    fun isInstalled(
        context: Context,
        mcVersion: String,
        forgeVersion: String,
        flavor: ForgeFlavor = ForgeFlavor.FORGE
    ): Boolean {
        val profile = findProfileFile(context, mcVersion, forgeVersion, flavor) ?: return false
        val version = if (forgeVersion.isNotBlank()) forgeVersion
        else profile.name.removePrefix(flavor.loaderId + "-").removeSuffix(".json")
        val forgeDir = File(InstanceManager.getLibrariesDir(context), flavor.libraryDir(mcVersion, version))
        return forgeDir.isDirectory && !forgeDir.list().isNullOrEmpty()
    }

    /**
     * Memastikan Forge/NeoForge [forgeVersion] untuk [mcVersion] terpasang. Versi Minecraft (client
     * jar, library, aset) harus sudah diunduh lebih dulu. Aman dipanggil ulang: bila sudah
     * terpasang, langsung selesai.
     */
    suspend fun ensureInstalled(
        context: Context,
        downloader: MinecraftDownloader,
        mcVersion: String,
        forgeVersion: String,
        onProgress: (Float, String) -> Unit,
        flavor: ForgeFlavor = ForgeFlavor.FORGE
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val name = flavor.displayName
            if (forgeVersion.isBlank()) throw IllegalStateException("Versi $name belum dipilih")
            if (isInstalled(context, mcVersion, forgeVersion, flavor)) return@withContext Result.success(Unit)
            if (!ForgeService.isMcSupported(mcVersion, flavor)) {
                throw IllegalStateException("$name hanya mendukung Minecraft ${flavor.minMcVersion} ke atas")
            }
            installLock.withLock {
                // Pemasangan lain mungkin baru selesai selagi menunggu giliran
                if (!isInstalled(context, mcVersion, forgeVersion, flavor)) {
                    install(context, downloader, mcVersion, forgeVersion, flavor, onProgress)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Pemasangan ${flavor.displayName} gagal", e)
            Result.failure(e)
        }
    }

    private fun progress(fraction: Float): Float = P_START + fraction.coerceIn(0f, 1f) * (P_END - P_START)

    private suspend fun install(
        context: Context,
        downloader: MinecraftDownloader,
        mcVersion: String,
        forgeVersion: String,
        flavor: ForgeFlavor,
        onProgress: (Float, String) -> Unit
    ) {
        val name = flavor.displayName
        val versionsDir = InstanceManager.getVersionsDir(context)
        val libDir = InstanceManager.getLibrariesDir(context)
        val nuxDir = InstanceManager.getNuxDir(context)

        // Lebih baik berhenti di awal dengan pesan jelas daripada gagal di tengah processor karena penuh
        val freeMb = libDir.apply { mkdirs() }.usableSpace / (1024L * 1024L)
        if (freeMb < MIN_FREE_MB) {
            throw IOException("Ruang penyimpanan tidak cukup untuk memasang $name (tersisa $freeMb MB, butuh minimal $MIN_FREE_MB MB)")
        }

        // 1. Installer resmi
        onProgress(progress(0f), "Mengunduh installer $name $forgeVersion...")
        val installerJar = File(
            context.cacheDir,
            "forge/" + SafePaths.fileName("${flavor.loaderId}-$mcVersion-$forgeVersion-installer.jar")
        )
        installerJar.parentFile?.mkdirs()
        if (!isUsableInstaller(installerJar)) {
            val url = ForgeService.installerUrl(mcVersion, forgeVersion, flavor)
            installerJar.delete()
            downloader.downloadFile(
                url = url,
                targetFile = installerJar,
                expectedSha1 = fetchSha1(url),
                onProgress = { read, total ->
                    val frac = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
                    onProgress(progress(frac * 0.08f), "Mengunduh installer $name $forgeVersion...")
                }
            )
            if (!isUsableInstaller(installerJar)) {
                installerJar.delete()
                throw IOException("Installer $name $forgeVersion rusak atau tidak lengkap")
            }
        }

        val (profile, versionJsonText) = readInstallerMeta(installerJar, name)
        if (profile.processors == null || profile.json == null && versionJsonText.isBlank()) {
            throw IllegalStateException("Format installer $name ini tidak dikenali (Amber butuh $name ${flavor.minMcVersion} ke atas)")
        }
        if (!profile.minecraft.isNullOrBlank() && profile.minecraft != mcVersion) {
            throw IllegalStateException("Installer $name untuk Minecraft ${profile.minecraft}, bukan $mcVersion")
        }
        val forgeVersionJson = gson.fromJson(versionJsonText, ForgeVersionJson::class.java)
            ?: throw IllegalStateException("version.json di installer $name tidak terbaca")
        val clientProcessorCount = (profile.processors ?: emptyList())
            .count { it.sides.isNullOrEmpty() || it.sides.any { s -> s.equals("client", ignoreCase = true) } }

        // 2. Library (installer berjalan offline, jadi semuanya harus sudah ada dan cocok sidik jarinya)
        prefetchLibraries(downloader, libDir, forgeVersionJson, profile, name, onProgress)

        // 3. Mappings Mojang (Forge 1.17+): diunduh di sini agar installer tidak butuh jaringan
        prefetchMojangMappings(downloader, libDir, versionsDir, mcVersion, profile)

        // 4. Installer resmi mensyaratkan launcher_profiles.json di folder tujuan
        ensureLauncherProfiles(nuxDir)

        // 5. Jalankan installer (offline) di proses :forge
        val mojangJson = File(File(versionsDir, mcVersion), "$mcVersion.json")
        val recommendedRuntime = JavaRuntimeManager.getRecommendedRuntime(mcVersion, readJavaMajor(mojangJson))
        // Installer Forge lama paling aman di Java 8; NeoForge (Minecraft 1.20.2 ke atas) butuh Java 17+
        val runtimes = linkedSetOf<String>()
        if (flavor == ForgeFlavor.FORGE) runtimes.add("jre-8")
        runtimes.add(recommendedRuntime)
        val commands = ForgeInstallerCli.commands(
            flavor,
            if (flavor == ForgeFlavor.FORGE) null else ForgeInstallerCli.readHints(installerJar),
            nuxDir.absolutePath
        )

        var lastFailure = "Installer $name gagal"
        var success = false
        runtimeLoop@ for (runtime in runtimes) {
            if (!JavaRuntimeManager.isRuntimeInstalled(context, runtime)) {
                onProgress(progress(0.45f), "Menyiapkan Java ($runtime)...")
                val ext = JavaRuntimeManager.extractRuntime(context, runtime) { f, msg ->
                    onProgress(progress(0.45f + f.coerceIn(0f, 1f) * 0.05f), msg)
                }
                if (ext.isFailure) {
                    lastFailure = "Gagal menyiapkan Java $runtime: ${ext.exceptionOrNull()?.message}"
                    continue@runtimeLoop
                }
            }
            for ((index, command) in commands.withIndex()) {
                if (index > 0) delay(RESTART_DELAY_MS) // beri waktu proses :forge sebelumnya benar-benar mati
                onProgress(progress(0.50f), "Memasang $name $forgeVersion...")
                var processorsDone = 0
                val result = ForgeInstallRunner.run(context, installerJar, command, runtime, name) { line ->
                    if (line.startsWith("=====")) processorsDone++
                    val frac = if (clientProcessorCount > 0) (processorsDone.toFloat() / clientProcessorCount).coerceIn(0f, 1f) else 0f
                    onProgress(progress(0.50f + frac * 0.48f), "Memasang $name $forgeVersion ($processorsDone/$clientProcessorCount)...")
                }
                if (result.exitCode == 0) {
                    success = true
                    break@runtimeLoop
                }
                lastFailure = result.describeFailure()
                if (ForgeInstallerCli.isWrongJava(result.log)) continue@runtimeLoop // coba Java berikutnya
                if (!ForgeInstallerCli.isUnknownOption(result.log)) break@runtimeLoop // galat sungguhan
                // opsi ditolak: coba bentuk perintah berikutnya
            }
        }
        if (!success) throw IOException(lastFailure)

        // 6. Pastikan hasil utamanya benar-benar ada sebelum menandai selesai
        val patchedPath = ForgeMaven.pathOfDataEntry(profile.data?.get("PATCHED")?.client)
        if (patchedPath != null) {
            val patched = SafePaths.resolveInside(libDir, patchedPath)
            if (patched == null || !patched.exists() || patched.length() == 0L) {
                throw IOException("Installer $name selesai tetapi jar Minecraft hasil tambalan tidak ditemukan ($patchedPath)")
            }
        }

        // 7. Tandai selesai: salin profilnya ke versions/<mc>/<forge|neoforge>-<versi>.json (ditulis paling akhir)
        val marker = File(File(versionsDir, mcVersion), SafePaths.fileName(flavor.markerName(forgeVersion)))
        val tmp = File(marker.parentFile, marker.name + ".tmp")
        tmp.writeText(versionJsonText)
        if (marker.exists()) marker.delete()
        if (!tmp.renameTo(marker)) {
            tmp.copyTo(marker, overwrite = true)
            tmp.delete()
        }

        // 8. Rapikan: folder versi buatan installer dan installer itu sendiri tidak dipakai lagi
        val installedId = profile.version?.takeIf { it.isNotBlank() }
        if (installedId != null && installedId != mcVersion) {
            val extraDir = SafePaths.resolveInside(versionsDir, SafePaths.fileName(installedId))
            if (extraDir != null && extraDir.isDirectory) extraDir.deleteRecursively()
        }
        installerJar.delete()
        onProgress(P_END, "$name $forgeVersion terpasang")
    }

    // ---------------------------------------------------------------- unduhan

    private class LibJob(val name: String, val url: String, val target: File, val sha1: String?, val size: Long)

    private suspend fun prefetchLibraries(
        downloader: MinecraftDownloader,
        libDir: File,
        versionJson: ForgeVersionJson,
        profile: ForgeInstallProfile,
        name: String,
        onProgress: (Float, String) -> Unit
    ) {
        val jobs = ((versionJson.libraries ?: emptyList()) + (profile.libraries ?: emptyList()))
            .mapNotNull { lib ->
                // Tanpa alamat berarti library itu dibawa di dalam installer dan diekstrak oleh installer
                val url = ForgeMaven.libraryUrl(lib)?.replaceFirst("http://", "https://") ?: return@mapNotNull null
                val path = ForgeMaven.libraryPath(lib) ?: return@mapNotNull null
                val target = SafePaths.resolveInside(libDir, path)
                    ?: throw IOException("Path library $name tidak aman: $path")
                LibJob(lib.name ?: path, url, target, lib.downloads?.artifact?.sha1, lib.downloads?.artifact?.size ?: 0L)
            }
            .distinctBy { it.target.path }

        if (jobs.isEmpty()) return
        val failed = CopyOnWriteArrayList<String>()
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val gate = Semaphore(LIB_THREADS)
        coroutineScope {
            jobs.map { job ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        if (!isUpToDate(job.target, job.sha1, job.size)) {
                            job.target.parentFile?.mkdirs()
                            try {
                                downloader.downloadFile(job.url, job.target, job.size, expectedSha1 = job.sha1)
                            } catch (e: Exception) {
                                failed.add(job.name)
                            }
                        }
                        val n = done.incrementAndGet()
                        onProgress(progress(0.08f + (n.toFloat() / jobs.size) * 0.32f), "Mengunduh library $name ($n/${jobs.size})...")
                    }
                }
            }.awaitAll()
        }
        if (failed.isNotEmpty()) {
            val shown = failed.take(3).joinToString(", ")
            val more = if (failed.size > 3) " " + Tr.t("dan ${failed.size - 3} lainnya") else ""
            val list = shown + more
            throw IOException("Gagal mengunduh library $name: $list. Periksa koneksi lalu coba lagi.")
        }
    }

    private fun prefetchMojangMappings(
        downloader: MinecraftDownloader,
        libDir: File,
        versionsDir: File,
        mcVersion: String,
        profile: ForgeInstallProfile
    ) {
        val path = ForgeMaven.pathOfDataEntry(profile.data?.get("MOJMAPS")?.client) ?: return
        val target = SafePaths.resolveInside(libDir, path) ?: throw IOException("Path mappings tidak aman: $path")
        if (target.exists() && target.length() > 0L) return

        val mojangJson = File(File(versionsDir, mcVersion), "$mcVersion.json")
        val mappings = JsonParser.parseString(mojangJson.readText()).asJsonObject
            .getAsJsonObject("downloads")?.getAsJsonObject("client_mappings")
            ?: throw IllegalStateException("Mojang tidak menyediakan mappings untuk Minecraft $mcVersion")
        val url = mappings.get("url")?.asString ?: throw IllegalStateException("Alamat mappings Mojang kosong")
        target.parentFile?.mkdirs()
        downloader.downloadFile(
            url = url,
            targetFile = target,
            expectedSize = mappings.get("size")?.asLong ?: 0L,
            expectedSha1 = mappings.get("sha1")?.asString
        )
    }

    private fun ensureLauncherProfiles(nuxDir: File) {
        val f = File(nuxDir, "launcher_profiles.json")
        if (f.exists() && f.length() > 0L) return
        f.writeText("{\"profiles\":{},\"version\":3}")
    }

    // ---------------------------------------------------------------- bantuan

    private fun isUpToDate(file: File, sha1: String?, size: Long): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        if (sha1.isNullOrBlank()) return size <= 0L || file.length() == size
        return sha1Of(file).equals(sha1.trim(), ignoreCase = true)
    }

    private fun sha1Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Berkas `.sha1` di samping installer (server resmi lalu mirror); null bila tidak bisa diambil. */
    private fun fetchSha1(installerUrl: String): String? {
        val candidates = listOf(
            "$installerUrl.sha1",
            "$installerUrl.sha1"
                .replace("https://maven.minecraftforge.net", "https://bmclapi2.bangbang93.com/maven")
                .replace("https://maven.neoforged.net/releases", "https://bmclapi2.bangbang93.com/maven")
        ).distinct()
        for (url in candidates) {
            try {
                val request = Request.Builder().url(url).header("User-Agent", "AmberLauncher/1.0 (Android)").build()
                FabricService.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val text = response.body?.string()?.trim()?.take(40)?.lowercase() ?: return@use
                    if (text.length == 40 && text.all { it in '0'..'9' || it in 'a'..'f' }) return text
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun isUsableInstaller(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            ZipFile(file).use { it.getEntry("install_profile.json") != null }
        } catch (_: Exception) {
            false
        }
    }

    private fun readInstallerMeta(installerJar: File, name: String): Pair<ForgeInstallProfile, String> {
        ZipFile(installerJar).use { zip ->
            val profileEntry = zip.getEntry("install_profile.json")
                ?: throw IllegalStateException("install_profile.json tidak ada di installer $name")
            val profileText = zip.getInputStream(profileEntry).bufferedReader().use { it.readText() }
            val profile = gson.fromJson(profileText, ForgeInstallProfile::class.java)
                ?: throw IllegalStateException("install_profile.json tidak terbaca")
            val jsonName = (profile.json ?: "/version.json").trimStart('/')
            val jsonEntry = zip.getEntry(jsonName)
                ?: throw IllegalStateException("$jsonName tidak ada di installer $name")
            val versionText = zip.getInputStream(jsonEntry).bufferedReader().use { it.readText() }
            return Pair(profile, versionText)
        }
    }

    private fun readJavaMajor(mojangJson: File): Int? = try {
        JsonParser.parseString(mojangJson.readText()).asJsonObject
            .getAsJsonObject("javaVersion")?.get("majorVersion")?.asInt
    } catch (_: Exception) {
        null
    }
}
