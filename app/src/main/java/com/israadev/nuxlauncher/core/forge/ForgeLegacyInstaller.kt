package com.israadev.nuxlauncher.core.forge

import com.israadev.nuxlauncher.core.utils.SafePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

/**
 * Memasang Forge gaya lama (Minecraft 1.12.2 ke bawah).
 *
 * Installer lama tidak punya processors dan tidak perlu dijalankan: `install_profile.json` memuat
 * `versionInfo` (daftar library, kelas utama LaunchWrapper, dan argumen `--tweakClass`) dan jar Forge
 * utama dibawa di dalam installer. Pemasangannya hanya dua hal: mengunduh library yang disebut profil,
 * lalu mengambil jar Forge utama dari installer ke folder libraries. Profil `versionInfo` disimpan
 * oleh [ForgeInstaller] sebagai penanda "terpasang" dan dibaca [ForgeLaunch] saat game dimulai.
 *
 * Tidak memakai Android sama sekali, jadi bisa diuji di JVM biasa; unduhan disuntikkan lewat `fetch`.
 */
object ForgeLegacyInstaller {

    /** Jumlah unduhan library paralel; library Forge lama kecil dan jumlahnya sedikit. */
    private const val THREADS = 6

    private const val MOJANG_LIBRARIES = "https://libraries.minecraft.net/"
    private const val FORGE_MAVEN = "https://maven.minecraftforge.net/"
    private const val MAVEN_CENTRAL = "https://repo1.maven.org/maven2/"

    /** Berkas penanda di folder Forge bila jar utamanya ada di folder lain (lihat [install]). */
    private const val INSTALLED_AS = "installed-as.txt"

    /** Satu library yang harus ada di disk; [urls] dicoba berurutan sampai ada yang berhasil. */
    class LibraryJob(val name: String, val path: String, val urls: List<String>)

    class Plan(
        /** Path jar Forge utama relatif terhadap folder libraries. */
        val mainJarPath: String,
        /** Nama jar Forge utama di dalam installer; null bila installer tidak menyebutnya. */
        val mainJarEntry: String?,
        /** Alamat jar Forge utama bila ternyata tidak ada di dalam installer. */
        val mainJarUrls: List<String>,
        val libraries: List<LibraryJob>
    )

    /** True bila [profile] berasal dari installer gaya lama (tanpa processors, dengan `versionInfo`). */
    fun isLegacyProfile(profile: ForgeInstallProfile): Boolean =
        profile.processors == null && profile.install != null && profile.versionInfo != null

    /**
     * Menyusun rencana pemasangan dari [profile]. Melempar [IllegalStateException] bila installer untuk
     * versi Minecraft lain atau tidak memuat jar Forge utama.
     */
    fun plan(profile: ForgeInstallProfile, mcVersion: String): Plan {
        val install = profile.install ?: throw IllegalStateException("Installer Forge ini tidak dikenali")
        val info = profile.versionInfo ?: throw IllegalStateException("Installer Forge ini tidak dikenali")

        val target = install.minecraft?.takeIf { it.isNotBlank() } ?: info.inheritsFrom?.takeIf { it.isNotBlank() }
        if (target != null && target != mcVersion) {
            throw IllegalStateException("Installer Forge untuk Minecraft $target, bukan $mcVersion")
        }

        val libs = info.libraries ?: emptyList()
        val coordinate = install.path?.takeIf { it.isNotBlank() }
            ?: libs.firstOrNull { it.name?.startsWith("net.minecraftforge:forge:") == true }?.name
        val mainPath = ForgeMaven.pathOf(coordinate)
            ?: throw IllegalStateException("Installer Forge ini tidak memuat jar Forge utama")
        if (!SafePaths.isSafeRelative(mainPath)) throw IOException("Path library Forge tidak aman: $mainPath")

        val universalPath = ForgeMaven.pathOf("$coordinate:universal")
        val mainUrls = if (universalPath != null) listOf(FORGE_MAVEN + universalPath) else emptyList()

        return Plan(
            mainJarPath = mainPath,
            mainJarEntry = install.filePath?.trim()?.trimStart('/')?.takeIf { it.isNotEmpty() },
            mainJarUrls = mainUrls,
            libraries = libraryJobs(libs, mainPath)
        )
    }

    /**
     * Library yang perlu diunduh. Dilewati: LWJGL (milik Amber), library berisi native (diurus Amber),
     * library khusus server, dan jar Forge utama (dibawa installer).
     */
    private fun libraryJobs(libs: List<ForgeLibrary>, mainPath: String): List<LibraryJob> {
        val jobs = LinkedHashMap<String, LibraryJob>()
        for (lib in libs) {
            val name = lib.name?.takeIf { it.isNotBlank() } ?: continue
            if (name.contains("org.lwjgl")) continue
            if (lib.natives != null) continue
            if (lib.clientreq == false) continue
            val path = ForgeMaven.libraryPath(lib) ?: continue
            if (path == mainPath || jobs.containsKey(path)) continue
            if (!SafePaths.isSafeRelative(path)) throw IOException("Path library Forge tidak aman: $path")
            jobs[path] = LibraryJob(name, path, candidateUrls(lib, path))
        }
        return jobs.values.toList()
    }

    /**
     * Alamat yang dicoba untuk satu library: alamat dari profil dulu, lalu server library Mojang, Maven
     * Forge, dan Maven Central. Library Forge lama tersebar di beberapa server dan alamat di profilnya
     * sering sudah tidak berlaku (http, atau files.minecraftforge.net yang sudah pindah).
     */
    internal fun candidateUrls(lib: ForgeLibrary, path: String): List<String> {
        val urls = LinkedHashSet<String>()
        ForgeMaven.libraryUrl(lib)?.let { urls.add(normalizeUrl(it)) }
        urls.add(MOJANG_LIBRARIES + path)
        urls.add(FORGE_MAVEN + path)
        urls.add(MAVEN_CENTRAL + path)
        return urls.toList()
    }

    private fun normalizeUrl(url: String): String {
        var u = url.trim()
        if (u.startsWith("http://")) u = "https://" + u.removePrefix("http://")
        return u.replace("https://files.minecraftforge.net/maven", "https://maven.minecraftforge.net")
    }

    /**
     * Menjalankan [plan]: mengunduh library lalu menaruh jar Forge utama di [libDir]. [fetch] mengunduh
     * satu alamat ke satu berkas dan melempar bila gagal. [report] menerima kemajuan 0..1 dan pesannya.
     * [formatFailure] membentuk pesan galat bila ada library yang gagal diunduh.
     *
     * Aman diulang: library dan jar yang sudah ada dipakai lagi.
     */
    suspend fun install(
        installerJar: File,
        plan: Plan,
        mcVersion: String,
        forgeVersion: String,
        libDir: File,
        fetch: (url: String, target: File) -> Unit,
        formatFailure: (failed: List<String>) -> String,
        report: (fraction: Float, message: String) -> Unit
    ) {
        downloadLibraries(plan.libraries, libDir, fetch, formatFailure, report)

        report(0.92f, "Memasang Forge $forgeVersion...")
        val main = SafePaths.resolveInside(libDir, plan.mainJarPath)
            ?: throw IOException("Path library Forge tidak aman: ${plan.mainJarPath}")
        if (!isUsableJar(main)) {
            main.parentFile?.mkdirs()
            main.delete()
            val extracted = plan.mainJarEntry != null && extractEntry(installerJar, plan.mainJarEntry, main)
            if (!extracted) fetchFirst(plan.mainJarUrls, main, fetch)
            if (!isUsableJar(main)) {
                main.delete()
                throw IOException("Jar Forge utama rusak atau tidak lengkap")
            }
        }

        // isInstalled() mencari folder libraries/net/minecraftforge/forge/<mc>-<versi>. Bila nama artefak
        // sebenarnya berbeda dari versi yang dipilih (daftar promosi tidak memuat akhiran cabang), folder
        // itu tidak ada dan instance akan dianggap belum terpasang selamanya, jadi diberi penanda.
        SafePaths.resolveInside(libDir, ForgeFlavor.FORGE.libraryDir(mcVersion, forgeVersion))?.let { dir ->
            if (dir.list().isNullOrEmpty()) {
                dir.mkdirs()
                File(dir, INSTALLED_AS).writeText(plan.mainJarPath)
            }
        }
    }

    private suspend fun downloadLibraries(
        jobs: List<LibraryJob>,
        libDir: File,
        fetch: (url: String, target: File) -> Unit,
        formatFailure: (failed: List<String>) -> String,
        report: (fraction: Float, message: String) -> Unit
    ) {
        if (jobs.isEmpty()) return
        val failed = CopyOnWriteArrayList<String>()
        val done = AtomicInteger(0)
        val gate = Semaphore(THREADS)
        coroutineScope {
            jobs.map { job ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val target = SafePaths.resolveInside(libDir, job.path)
                        if (target == null) {
                            failed.add(job.name)
                        } else if (!target.exists() || target.length() == 0L) {
                            try {
                                target.parentFile?.mkdirs()
                                fetchFirst(job.urls, target, fetch)
                            } catch (e: Exception) {
                                failed.add(job.name)
                            }
                        }
                        val n = done.incrementAndGet()
                        report(n.toFloat() / jobs.size * 0.9f, "Mengunduh library Forge ($n/${jobs.size})...")
                    }
                }
            }.awaitAll()
        }
        if (failed.isNotEmpty()) throw IOException(formatFailure(failed.toList()))
    }

    /** Mencoba [urls] berurutan; melempar galat terakhir bila semuanya gagal. */
    internal fun fetchFirst(urls: List<String>, target: File, fetch: (url: String, target: File) -> Unit) {
        var failure: Exception? = null
        for (url in urls) {
            try {
                fetch(url, target)
                if (target.exists() && target.length() > 0L) return
                failure = IOException("Unduhan kosong: $url")
            } catch (e: Exception) {
                failure = e
            }
        }
        throw failure ?: IOException("Tidak ada alamat unduhan")
    }

    /** Menyalin [entryName] dari [zipFile] ke [target] lewat berkas sementara. False bila entri tidak ada. */
    private fun extractEntry(zipFile: File, entryName: String, target: File): Boolean {
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry(entryName) ?: return false
            val tmp = File(target.parentFile, target.name + ".part")
            zip.getInputStream(entry).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            return true
        }
    }

    /** Berkas ada, tidak kosong, dan bisa dibuka sebagai jar (zip) yang berisi sesuatu. */
    private fun isUsableJar(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        return try {
            ZipFile(file).use { it.entries().hasMoreElements() }
        } catch (_: Exception) {
            false
        }
    }
}
