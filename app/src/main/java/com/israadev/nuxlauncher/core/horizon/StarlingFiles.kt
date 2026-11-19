package com.israadev.nuxlauncher.core.horizon

import android.content.Context
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.download.MinecraftDownloader
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.core.utils.SafePaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Berkas resmi Minecraft untuk Starling (client, server, library, dan aset, sekitar 500 MiB), diunduh oleh
 * launcher sendiri dengan dialog unduhan Amber, bukan oleh layar penyiapan milik mesin.
 *
 * Ini menggantikan satu langkah di mesin, bukan mesinnya: mesin (accounts::download_files di sumber Starling)
 * hanya mengambil berkas yang didaftar Mojang, mencocokkan SHA-1 tiap berkas dengan daftar itu, menaruhnya di tata
 * letak launcher resmi di `files/starling/minecraft/` (`versions/`, `libraries/`, `assets/`), lalu menulis
 * `starling-files.json`. Di sini langkah yang sama dikerjakan dengan aturan yang sama (sumber, hash, penyaringan
 * library untuk Linux, nama berkas), dan penanda itu ditulis paling akhir dengan isi yang sama. Begitu penandanya
 * ada, mesin menganggap berkasnya sudah siap dan langsung ke langkah berikutnya (menyiapkan game di HP), tanpa
 * menanyakan akun atau mengunduh lagi.
 *
 * Patokannya (versi, hash berkas versi, hash client dan server) adalah salinan blok `minecraft` dari
 * `research-lock.json` sumber Starling, ada di `assets/starling-pins.json`, dan dijaga sama dengan sumber yang
 * dibangun oleh scripts/build-horizon-engine.sh. Tanpa berkas itu (atau bila versinya bukan versi yang dikerjakan
 * Starling), [needsDownload] false dan semuanya jalan seperti sebelumnya: mesin mengunduh sendiri.
 *
 * Bisa dilanjutkan: berkas yang sudah ada dengan ukuran yang benar dilewati, dan unduhan yang terputus tidak
 * meninggalkan berkas setengah jadi (MinecraftDownloader menulis ke berkas sementara lalu memindahkannya setelah
 * hash cocok).
 */
object StarlingFiles {
    private const val PINS_ASSET = "starling-pins.json"

    /** Penanda "semua berkas ada dan cocok": nama dan bentuk yang sama dengan yang ditulis mesin. */
    private const val FILES_DONE = "starling-files.json"

    private const val META_URL = "https://piston-meta.mojang.com"
    private const val RESOURCES_URL = "https://resources.download.minecraft.net"

    /** Kegagalan berkas sebanyak ini menghentikan unduhan (dilanjutkan oleh unduhan berikutnya). */
    private const val MAX_FAILED_FILES = 3

    /** Cadangan ruang kosong di atas ukuran unduhan, untuk berkas sementara dan penyiapan mesin berikutnya. */
    private const val SPACE_MARGIN_BYTES = 256L * 1024L * 1024L

    private const val REPORT_INTERVAL_MS = 300L

    /** SHA-1 dari isi kosong: berkas 0 byte tidak bisa lewat MinecraftDownloader (ia menolak berkas kosong). */
    private const val EMPTY_SHA1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709"

    /** Patokan berkas resmi: versi game dan hash berkas yang dipatok Starling. */
    class Pins(
        val version: String,
        /** Hash berkas versi: yang pertama diunduh, sisanya yang pernah diterbitkan ulang Mojang. */
        val manifests: List<String>,
        val clientSha1: String,
        val serverSha1: String
    )

    /** Kemajuan unduhan, untuk dialog. */
    class Progress(
        val filesDone: Int,
        val filesTotal: Int,
        val bytesDone: Long,
        val bytesTotal: Long,
        /** Kecepatan yang sudah diperhalus, byte per detik; 0 bila belum terukur. */
        val bytesPerSecond: Long,
        /** True selama daftar berkas masih disiapkan (belum ada total). */
        val preparing: Boolean
    ) {
        val fraction: Float
            get() = if (bytesTotal > 0L) (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f
    }

    private class Item(val url: String, val file: File, val sha1: String, val size: Long)

    private val pinsLock = Any()
    private var pinsLoaded = false
    private var cachedPins: Pins? = null

    /** Patokan dari assets, atau null bila tidak ada, rusak, atau bukan untuk versi yang dikerjakan Starling. */
    fun pins(context: Context): Pins? = synchronized(pinsLock) {
        if (!pinsLoaded) {
            pinsLoaded = true
            cachedPins = try {
                val json = JSONObject(
                    context.applicationContext.assets.open(PINS_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
                )
                readPins(json)
            } catch (e: Exception) {
                LauncherLog.w("StarlingFiles", "Patokan berkas Starling tidak terbaca: ${e.message}")
                null
            }
        }
        cachedPins
    }

    private fun readPins(json: JSONObject): Pins? {
        val version = json.optString("version")
        val manifests = ArrayList<String>()
        manifests += json.optString("manifest_sha1")
        json.optJSONArray("accepted_manifest_sha1")?.let { list ->
            for (i in 0 until list.length()) manifests += list.optString(i)
        }
        val client = json.optString("client_sha1")
        val server = json.optString("server_sha1")
        val valid = version == StarlingEngine.SUPPORTED_MC_VERSION &&
            manifests.all { isSha1(it) } && isSha1(client) && isSha1(server)
        return if (valid) Pins(version, manifests, client, server) else null
    }

    private fun isSha1(text: String): Boolean =
        text.length == 40 && text.all { it in '0'..'9' || it in 'a'..'f' }

    /** Folder berkas resmi milik mesin (tata letak launcher resmi). */
    internal fun root(context: Context): File = File(StarlingEngine.engineHome(context), "minecraft")

    /** True bila berkasnya sudah lengkap (penanda ada), oleh launcher ini atau oleh mesin. */
    fun installed(context: Context): Boolean = StarlingEngine.gameInstalled(context)

    /** True bila launcher harus mengunduh berkas resmi dulu sebelum Starling dibuka. */
    fun needsDownload(context: Context): Boolean = !installed(context) && pins(context) != null

    /**
     * True bila Starling boleh dibuka sekarang: berkasnya lengkap, atau launcher tidak bisa mengunduhnya (tanpa
     * patokan) sehingga mesin mengunduh sendiri seperti sebelumnya. Dipakai tombol PLAY/UNDUH di Home.
     */
    fun ready(context: Context): Boolean = !needsDownload(context)

    /**
     * Mengunduh semua berkas resmi lalu menulis penanda. [onProgress] dipanggil dari utas latar sekitar tiga kali
     * per detik. Dibatalkan lewat pembatalan coroutine (berkas yang sedang berjalan selesai dulu, yang sudah
     * jadi tetap dipakai pada unduhan berikutnya). Gagal berarti Result.failure dengan pesan untuk pemain.
     */
    suspend fun download(context: Context, onProgress: (Progress) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val pins = pins(context) ?: throw IOException("Patokan berkas Starling tidak tersedia di aplikasi ini.")
                runDownload(context.applicationContext, pins, onProgress)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LauncherLog.w("StarlingFiles", "Unduhan berkas Starling gagal: ${e.message}", e)
                Result.failure(e)
            }
        }

    private suspend fun runDownload(context: Context, pins: Pins, onProgress: (Progress) -> Unit) {
        val root = root(context)
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Folder data Starling tidak bisa dibuat.")
        cleanStaleParts(root)
        // Penanda lama dihapus lebih dulu: unduhan yang terputus tidak boleh tampak selesai.
        File(root, FILES_DONE).delete()
        onProgress(Progress(0, 0, 0L, 0L, 0L, preparing = true))

        val downloader = MinecraftDownloader(context)
        val v = pins.version

        // 1. Berkas versi (hash-nya dipatok), dari piston-meta
        val manifestFile = File(root, "versions/$v/$v.json")
        val manifestBytes = readIfHashIn(manifestFile, pins.manifests) ?: run {
            downloader.downloadFile(
                url = "$META_URL/v1/packages/${pins.manifests[0]}/$v.json",
                targetFile = manifestFile,
                retries = 4,
                expectedSha1 = pins.manifests[0]
            )
            manifestFile.readBytes()
        }
        currentCoroutineContext().ensureActive()
        val json = try {
            JSONObject(manifestBytes.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            throw IOException("Berkas versi $v dari Mojang tidak valid.")
        }

        // 2. Daftar semua berkas yang harus ada: client, server, library, indeks aset, dan objek aset
        val items = LinkedHashMap<String, Item>()
        fun add(item: Item) {
            items.putIfAbsent(item.file.absolutePath, item)
        }

        val downloads = json.optJSONObject("downloads") ?: throw IOException("Berkas versi $v tidak mendaftar unduhan.")
        fun jarItem(key: String, file: File, pinned: String): Item {
            val d = downloads.optJSONObject(key) ?: throw IOException("Berkas versi $v tidak mendaftar unduhan $key.")
            val url = d.optString("url")
            val sha1 = d.optString("sha1")
            if (url.isBlank() || sha1.isBlank()) throw IOException("Berkas versi $v tidak mendaftar unduhan $key.")
            if (sha1 != pinned) throw IOException("Berkas versi $v memuat jar $key yang lain dari yang dipatok Starling.")
            return Item(url, file, sha1, d.optLong("size"))
        }

        val clientJar = File(root, "versions/$v/$v.jar")
        val serverJar = File(root, "versions/$v/$v-server.jar")
        add(jarItem("client", clientJar, pins.clientSha1))
        add(jarItem("server", serverJar, pins.serverSha1))

        val librariesDir = File(root, "libraries")
        json.optJSONArray("libraries")?.let { libraries ->
            for (i in 0 until libraries.length()) {
                val library = libraries.optJSONObject(i) ?: continue
                val artifact = library.optJSONObject("downloads")?.optJSONObject("artifact") ?: continue
                val path = artifact.optString("path")
                val url = artifact.optString("url")
                val sha1 = artifact.optString("sha1")
                if (path.isBlank() || url.isBlank() || sha1.isBlank()) continue
                if (!libraryApplies(library)) continue
                val target = SafePaths.resolveInside(librariesDir, path) ?: continue
                add(Item(url, target, sha1, artifact.optLong("size")))
            }
        }

        val assetIndex = json.optJSONObject("assetIndex") ?: throw IOException("Berkas versi $v tidak mendaftar indeks aset.")
        val indexId = assetIndex.optString("id")
        val indexUrl = assetIndex.optString("url")
        val indexSha1 = assetIndex.optString("sha1")
        if (indexId.isBlank() || indexUrl.isBlank() || indexSha1.isBlank()) {
            throw IOException("Berkas versi $v tidak mendaftar indeks aset.")
        }
        val indexFile = SafePaths.resolveInside(File(root, "assets/indexes"), "$indexId.json")
            ?: throw IOException("Nama indeks aset tidak valid.")
        val indexBytes = readIfHashIn(indexFile, listOf(indexSha1)) ?: run {
            downloader.downloadFile(url = indexUrl, targetFile = indexFile, retries = 4, expectedSha1 = indexSha1)
            indexFile.readBytes()
        }
        currentCoroutineContext().ensureActive()
        val objects = try {
            JSONObject(indexBytes.toString(Charsets.UTF_8)).optJSONObject("objects")
        } catch (_: Exception) {
            null
        } ?: throw IOException("Indeks aset dari Mojang tidak valid.")
        val objectsDir = File(root, "assets/objects")
        val names = objects.keys()
        while (names.hasNext()) {
            val entry = objects.optJSONObject(names.next()) ?: continue
            val hash = entry.optString("hash")
            if (!isSha1(hash)) continue
            val prefix = hash.substring(0, 2)
            add(Item("$RESOURCES_URL/$prefix/$hash", File(objectsDir, "$prefix/$hash"), hash, entry.optLong("size")))
        }

        // 3. Ruang kosong: cukup untuk sisa unduhan dan cadangan
        val pending = items.values.filter { !isComplete(it) }
        val pendingBytes = pending.sumOf { it.size.coerceAtLeast(0L) }
        val totalBytes = items.values.sumOf { it.size.coerceAtLeast(0L) }
        val free = root.usableSpace
        if (pendingBytes + SPACE_MARGIN_BYTES > free) {
            val needMb = (pendingBytes + SPACE_MARGIN_BYTES - free) / (1024L * 1024L) + 1L
            throw IOException("Ruang penyimpanan tidak cukup: butuh sekitar $needMb MB lagi.")
        }

        // 4. Unduh: yang besar lebih dulu, beberapa sekaligus
        val bytes = AtomicLong(totalBytes - pendingBytes)
        val files = AtomicInteger(items.size - pending.size)
        val failed = AtomicInteger(0)
        val firstError = AtomicReference<String?>(null)
        val queue = ConcurrentLinkedQueue(pending.sortedByDescending { it.size })
        val workers = if (SettingsManager.getTotalDeviceMemoryMb(context) < 2500) 8 else 16

        fun report(preparing: Boolean, speed: Long) = onProgress(
            Progress(files.get(), items.size, bytes.get().coerceAtMost(totalBytes), totalBytes, speed, preparing)
        )
        report(preparing = false, speed = 0L)

        coroutineScope {
            val reporter = launch {
                var lastBytes = bytes.get()
                var lastTime = System.nanoTime()
                var speed = 0.0
                while (true) {
                    delay(REPORT_INTERVAL_MS)
                    val now = System.nanoTime()
                    val current = bytes.get()
                    val seconds = (now - lastTime) / 1_000_000_000.0
                    if (seconds > 0.0) {
                        val instant = ((current - lastBytes) / seconds).coerceAtLeast(0.0)
                        speed = if (speed == 0.0) instant else speed * 0.7 + instant * 0.3
                    }
                    lastBytes = current
                    lastTime = now
                    report(preparing = false, speed = speed.toLong())
                }
            }
            val jobs = List(workers) {
                async {
                    while (failed.get() < MAX_FAILED_FILES) {
                        ensureActive()
                        val item = queue.poll() ?: break
                        item.file.parentFile?.mkdirs()
                        if (item.size == 0L && item.sha1 == EMPTY_SHA1) {
                            item.file.writeBytes(ByteArray(0))
                            files.incrementAndGet()
                            continue
                        }
                        var counted = 0L
                        try {
                            downloader.downloadFile(
                                url = item.url,
                                targetFile = item.file,
                                expectedSize = item.size,
                                onProgress = { read, _ ->
                                    // Percobaan ulang memulai dari nol: selisihnya negatif, jadi hitungan tetap benar
                                    bytes.addAndGet(read - counted)
                                    counted = read
                                },
                                retries = 3,
                                expectedSha1 = item.sha1
                            )
                            files.incrementAndGet()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            bytes.addAndGet(-counted)
                            firstError.compareAndSet(null, "${item.file.name}: ${e.message}")
                            if (failed.incrementAndGet() <= MAX_FAILED_FILES) {
                                LauncherLog.w("StarlingFiles", "Berkas gagal: ${item.file.name}: ${e.message}")
                            }
                        }
                    }
                }
            }
            try {
                jobs.awaitAll()
            } finally {
                reporter.cancel()
            }
        }
        currentCoroutineContext().ensureActive()
        if (failed.get() > 0) {
            throw IOException(
                "Sebagian berkas gagal diunduh. Periksa koneksi internet lalu tekan UNDUH lagi untuk melanjutkan. " +
                    "(${firstError.get() ?: "tidak diketahui"})"
            )
        }
        // Semuanya harus ada sebelum penanda ditulis
        val missing = items.values.firstOrNull { !it.file.isFile }
        if (missing != null) throw IOException("Berkas ${missing.file.name} belum ada setelah diunduh.")

        // 5. Penanda: isi dan nama sama dengan yang ditulis mesin, ditulis paling akhir lewat berkas sementara
        val done = JSONObject()
            .put("version", v)
            .put("client", clientJar.absolutePath)
            .put("server", serverJar.absolutePath)
        val temp = File(root, "$FILES_DONE.tmp")
        temp.writeText(done.toString())
        val marker = File(root, FILES_DONE)
        marker.delete()
        if (!temp.renameTo(marker)) {
            temp.delete()
            throw IOException("$FILES_DONE tidak bisa disimpan.")
        }
        report(preparing = false, speed = 0L)
        LauncherLog.event("Unduhan", "Berkas resmi Starling $v lengkap (${items.size} berkas, ${totalBytes / (1024L * 1024L)} MB)")
    }

    /** Berkas ada dan ukurannya sama dengan daftar Mojang (berkas hanya dipindah ke tempatnya setelah hash cocok). */
    private fun isComplete(item: Item): Boolean = item.file.isFile && item.file.length() == item.size

    /** Isi berkas bila hash SHA-1-nya salah satu dari [hashes]; selain itu null. */
    private fun readIfHashIn(file: File, hashes: List<String>): ByteArray? {
        if (!file.isFile) return null
        val data = try {
            file.readBytes()
        } catch (_: Exception) {
            return null
        }
        val digest = MessageDigest.getInstance("SHA-1").digest(data).joinToString("") { "%02x".format(it) }
        return if (digest in hashes) data else null
    }

    /**
     * Aturan library Mojang untuk HP: dihitung sebagai Linux (seperti yang dibaca horizon-prepare), dan hanya
     * artefak biasa (tanpa klasifier native). Aturan yang sama dengan starling_account::files::library_applies.
     */
    private fun libraryApplies(library: JSONObject): Boolean {
        val rules = library.optJSONArray("rules") ?: return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            val osName = rule.optJSONObject("os")?.optString("name")?.takeIf { it.isNotEmpty() }
            if (osName == null || osName == "linux") {
                allowed = rule.optString("action") == "allow"
            }
        }
        return allowed
    }

    /** Menghapus berkas sementara unduhan (`.part_`) yang sudah lama tidak disentuh. */
    private fun cleanStaleParts(root: File) {
        val cutoff = System.currentTimeMillis() - 10L * 60L * 1000L
        try {
            root.walkTopDown()
                .filter { it.isFile && it.name.contains(".part_") && it.lastModified() < cutoff }
                .forEach { it.delete() }
        } catch (_: Throwable) {
        }
    }
}
