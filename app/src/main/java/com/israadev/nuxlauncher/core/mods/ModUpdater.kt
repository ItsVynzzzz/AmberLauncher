package com.israadev.nuxlauncher.core.mods

import android.content.Context
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.horizon.HorizonEngine
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.models.ModrinthVersion
import com.israadev.nuxlauncher.core.utils.SafePaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/** Satu mod terpasang yang punya versi lebih baru di Modrinth. */
data class ModUpdate(
    /** Nama berkas di disk sekarang (berakhiran .disabled bila modnya dinonaktifkan). */
    val fileName: String,
    val name: String,
    val fromVersion: String,
    val toVersion: String,
    /** Nama berkas versi baru (sudah dibersihkan, selalu berakhiran .jar). */
    val newFileName: String,
    val newUrl: String,
    val newSize: Long,
    val newHashes: Map<String, String>,
    /** sha1 berkas yang sekarang terpasang; dicek lagi sebelum diganti. */
    val oldSha1: String,
    val enabled: Boolean
)

/** Mod yang punya versi baru tetapi sengaja tidak dipasang otomatis. */
data class ModUpdateSkip(val name: String, val reason: ModUpdateSkipReason)

data class ModUpdateCheck(
    val updates: List<ModUpdate>,
    /** Jumlah mod (berkas .jar berbeda) yang diperiksa. */
    val total: Int,
    val upToDate: Int,
    /** Mod yang tidak dikenal Modrinth (CurseForge, buatan sendiri, atau sudah dimodifikasi). */
    val unknown: Int,
    val skipped: List<ModUpdateSkip>,
    val checkedAt: Long
)

data class ModUpdateApplyResult(val updated: List<ModUpdate>, val failed: List<ModUpdate>)

/**
 * Memeriksa dan memasang pembaruan untuk semua mod di sebuah instance sekaligus.
 *
 * Cara kerja: sidik jari sha1 tiap jar dikirim ke Modrinth (dua permintaan untuk seluruh mod, bukan satu per mod).
 * Hanya mod yang dikenal Modrinth yang bisa diperiksa; mod dari CurseForge atau buatan sendiri dilewati.
 * Pembaruan diunduh ke berkas sementara, diverifikasi sidik jarinya, baru kemudian menggantikan berkas lama,
 * jadi unduhan yang gagal atau dibatalkan tidak pernah merusak mod yang sudah ada.
 */
object ModUpdater {
    private const val TAG = "ModUpdater"
    private const val PREFS = "amber_mod_update"
    private const val MAX_MOD_BYTES = 512L * 1024L * 1024L
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private class CachedHash(val size: Long, val modified: Long, val sha1: String)

    /** sha1 per berkas, dikunci dengan ukuran + waktu ubah, supaya pemeriksaan berikutnya tidak membaca ulang semua jar. */
    private val hashCache = ConcurrentHashMap<String, CachedHash>()

    private class LocalMod(val file: File, val sha1: String)

    /** Daftar mod tidak berlaku untuk instance dengan mesin Rust. */
    fun isSupported(instance: Instance?): Boolean =
        instance != null && !HorizonEngine.isEngine(instance.loader)

    /** True bila pemeriksaan otomatis untuk [instanceId] sudah waktunya dijalankan lagi. */
    fun isAutoCheckDue(context: Context, instanceId: String): Boolean {
        val last = try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("last_$instanceId", 0L)
        } catch (_: Exception) {
            0L
        }
        return ModUpdatePlanner.isCheckDue(last, System.currentTimeMillis())
    }

    private fun markChecked(context: Context, instanceId: String) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong("last_$instanceId", System.currentTimeMillis()).apply()
        } catch (_: Exception) {
            // Hanya untuk membatasi frekuensi pemeriksaan otomatis; gagal menyimpan tidak berbahaya
        }
    }

    /**
     * Memeriksa semua mod di folder mods instance. Mengembalikan kegagalan (mis. tanpa internet) alih-alih
     * melempar galat; pembatalan coroutine tetap diteruskan.
     */
    suspend fun check(context: Context, instance: Instance): Result<ModUpdateCheck> = withContext(Dispatchers.IO) {
        try {
            if (!isSupported(instance)) throw IOException("Instance ini tidak memakai daftar mod biasa")
            val mcVersion = instance.mcVersion.trim()
            if (mcVersion.isEmpty()) throw IOException("Versi Minecraft instance tidak diketahui")
            val loader = NuxModManager.effectiveLoader(instance)
            val job = currentCoroutineContext()[Job]

            val modsDir = File(InstanceManager.getInstanceGameDir(context, instance), "mods")
            val files = modsDir.listFiles { f -> f.isFile && ModUpdatePlanner.isModJarName(f.name) }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
            val now = System.currentTimeMillis()
            if (files.isEmpty()) {
                markChecked(context, instance.id)
                return@withContext Result.success(ModUpdateCheck(emptyList(), 0, 0, 0, emptyList(), now))
            }

            val local = ArrayList<LocalMod>(files.size)
            for (file in files) {
                job?.ensureActive()
                local.add(LocalMod(file, sha1Of(file)))
            }
            val hashes = local.map { it.sha1 }.distinct()

            // 1) Versi yang sekarang terpasang (dipakai mengenali proyek dan mencegah penurunan versi)
            val currentByHash = HashMap<String, ModrinthVersion>()
            for (chunk in hashes.chunked(ModUpdatePlanner.MAX_HASHES_PER_REQUEST)) {
                job?.ensureActive()
                val body = ModUpdatePlanner.versionsRequestBody(chunk)
                currentByHash.putAll(ModUpdatePlanner.parseVersionMap(postJson("/version_files", body)))
            }

            // 2) Versi terbaru yang cocok dengan loader dan versi Minecraft instance, hanya untuk mod yang dikenal
            val known = hashes.filter { it in currentByHash }
            val latestByHash = HashMap<String, ModrinthVersion>()
            for (chunk in known.chunked(ModUpdatePlanner.MAX_HASHES_PER_REQUEST)) {
                job?.ensureActive()
                val body = ModUpdatePlanner.updateRequestBody(chunk, loader, mcVersion)
                latestByHash.putAll(ModUpdatePlanner.parseVersionMap(postJson("/version_files/update", body)))
            }

            val installedProjects = currentByHash.values.map { it.projectId }.filter { it.isNotBlank() }.toSet()
            val updates = ArrayList<ModUpdate>()
            val skipped = ArrayList<ModUpdateSkip>()
            val seen = HashSet<String>()
            var upToDate = 0
            var unknown = 0
            for (mod in local) {
                // Dua berkas yang isinya sama dihitung sekali
                if (!seen.add(mod.sha1)) continue
                val current = currentByHash[mod.sha1]
                when (val decision = ModUpdatePlanner.decide(mod.sha1, current, latestByHash[mod.sha1], installedProjects)) {
                    is ModUpdateDecision.Unknown -> unknown++
                    is ModUpdateDecision.UpToDate -> upToDate++
                    is ModUpdateDecision.Skip -> skipped.add(ModUpdateSkip(displayName(mod.file), decision.reason))
                    is ModUpdateDecision.Update -> {
                        val newFileName = SafePaths.fileName(decision.file.filename)
                        if (!newFileName.endsWith(".jar", ignoreCase = true)) {
                            skipped.add(ModUpdateSkip(displayName(mod.file), ModUpdateSkipReason.NO_FILE))
                        } else {
                            updates.add(
                                ModUpdate(
                                    fileName = mod.file.name,
                                    name = displayName(mod.file),
                                    fromVersion = current?.versionNumber.orEmpty(),
                                    toVersion = decision.version.versionNumber,
                                    newFileName = newFileName,
                                    newUrl = decision.file.url,
                                    newSize = decision.file.size,
                                    newHashes = decision.file.hashes.orEmpty(),
                                    oldSha1 = mod.sha1,
                                    enabled = !mod.file.name.endsWith(".disabled", ignoreCase = true)
                                )
                            )
                        }
                    }
                }
            }
            markChecked(context, instance.id)
            Result.success(ModUpdateCheck(updates, seen.size, upToDate, unknown, skipped, now))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LauncherLog.w(TAG, "Pemeriksaan pembaruan mod gagal: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Memasang [updates] satu per satu. Satu mod yang gagal tidak menghentikan yang lain. Bila dibatalkan,
     * mod yang sudah selesai tetap terpasang dan berkas sementara dibersihkan.
     */
    suspend fun apply(
        context: Context,
        instance: Instance,
        updates: List<ModUpdate>,
        onProgress: (done: Int, total: Int, name: String) -> Unit = { _, _, _ -> }
    ): ModUpdateApplyResult = withContext(Dispatchers.IO) {
        val gameDir = InstanceManager.getInstanceGameDir(context, instance)
        val modsDir = File(gameDir, "mods")
        val job = currentCoroutineContext()[Job]
        val updated = ArrayList<ModUpdate>()
        val failed = ArrayList<ModUpdate>()
        val renames = LinkedHashMap<String, String>()
        try {
            for ((index, update) in updates.withIndex()) {
                job?.ensureActive()
                onProgress(index, updates.size, update.name)
                try {
                    replaceOne(job, modsDir, update)
                    updated.add(update)
                    renames["mods/" + ModUpdatePlanner.cleanName(update.fileName)] = "mods/" + update.newFileName
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed.add(update)
                    LauncherLog.w(TAG, "Gagal memperbarui ${update.name}: ${e.message}", e)
                }
            }
            onProgress(updates.size, updates.size, "")
        } finally {
            // Modpack mencatat berkasnya; tanpa ini, menghapus modpack akan meninggalkan jar baru dan mencari jar lama
            if (renames.isNotEmpty()) {
                withContext(NonCancellable) { retargetManifests(gameDir, renames) }
            }
        }
        ModUpdateApplyResult(updated, failed)
    }

    /** Mengunduh versi baru ke berkas sementara, memverifikasinya, lalu menukarnya dengan berkas lama. */
    private fun replaceOne(job: Job?, modsDir: File, u: ModUpdate) {
        val oldFile = File(modsDir, u.fileName)
        if (!oldFile.isFile) throw IOException("Berkas lama tidak ditemukan")
        if (!sha1Of(oldFile).equals(u.oldSha1, ignoreCase = true)) {
            throw IOException("Berkas ${u.fileName} berubah sejak diperiksa")
        }
        val newName = u.newFileName
        if (newName != SafePaths.fileName(newName) || !newName.endsWith(".jar", ignoreCase = true)) {
            throw IOException("Nama berkas baru tidak valid")
        }
        if (u.newHashes.isEmpty()) throw IOException("Berkas baru tanpa sidik jari tidak dipasang")

        // Nama tujuan tidak boleh sudah dipakai berkas lain, baik dalam bentuk aktif maupun nonaktif
        val finalFile = File(modsDir, if (u.enabled) newName else "$newName.disabled")
        for (candidate in listOf(File(modsDir, newName), File(modsDir, "$newName.disabled"))) {
            if (candidate.exists() && candidate.name != oldFile.name) {
                throw IOException("Berkas $newName sudah ada")
            }
        }

        val tmp = File(modsDir, "$newName.upd.part")
        try {
            NuxModManager.downloadFile(
                url = u.newUrl,
                destFile = tmp,
                maxBytes = if (u.newSize > 0L) u.newSize + 1024L * 1024L else MAX_MOD_BYTES,
                expectedBytes = u.newSize,
                onBytes = { _, _ -> job?.ensureActive() }
            )
            NuxModManager.verifyHashes(tmp, u.newHashes)
            // Memastikan jar utuh sebelum menggantikan yang lama
            ZipFile(tmp).close()
            job?.ensureActive()
            commit(oldFile, finalFile, tmp)
        } finally {
            tmp.delete()
        }
        hashCache.remove(oldFile.absolutePath)
    }

    /** Menukar berkas lama dengan [tmp] tanpa pernah meninggalkan mod hilang bila salah satu langkah gagal. */
    private fun commit(oldFile: File, finalFile: File, tmp: File) {
        if (oldFile.name == finalFile.name) {
            // Nama sama, isi berbeda: simpan yang lama dulu sebagai cadangan
            val backup = File(oldFile.parentFile, oldFile.name + ".bak.part")
            backup.delete()
            if (!oldFile.renameTo(backup)) throw IOException("Gagal menyiapkan penggantian berkas")
            if (!tmp.renameTo(finalFile)) {
                backup.renameTo(oldFile)
                throw IOException("Gagal menyimpan berkas baru")
            }
            backup.delete()
        } else {
            if (!tmp.renameTo(finalFile)) throw IOException("Gagal menyimpan berkas baru")
            if (!oldFile.delete()) {
                // Jangan biarkan dua versi mod yang sama berdampingan
                finalFile.delete()
                throw IOException("Gagal menghapus berkas lama")
            }
        }
    }

    /** Mengarahkan catatan modpack dari nama jar lama ke nama jar baru. */
    private fun retargetManifests(gameDir: File, renames: Map<String, String>) {
        try {
            val metaDir = File(gameDir, "modpacks/.metadata")
            val manifests = metaDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return
            for (metaFile in manifests) {
                val manifest = NuxModManager.loadModpackManifest(gameDir, metaFile.name.removeSuffix(".json")) ?: continue
                var changed = false
                val files = manifest.installedFiles.map { path ->
                    val target = renames[path.replace('\\', '/')]
                    if (target != null) {
                        changed = true
                        target
                    } else {
                        path
                    }
                }
                if (changed) NuxModManager.saveModpackManifest(gameDir, manifest.copy(installedFiles = files.distinct()))
            }
        } catch (e: Exception) {
            LauncherLog.w(TAG, "Gagal memperbarui catatan modpack: ${e.message}", e)
        }
    }

    private fun postJson(path: String, body: String): String {
        val request = Request.Builder()
            .url(NuxModManager.MODRINTH_API_BASE + path)
            .header("User-Agent", NuxModManager.USER_AGENT)
            .post(body.toRequestBody(JSON_TYPE))
            .build()
        return NuxModManager.httpClient.newCall(request).execute().use { response ->
            if (response.code == 404) {
                // Tidak ada satu pun sidik jari yang dikenal: sama artinya dengan jawaban kosong
                "{}"
            } else {
                if (!response.isSuccessful) throw IOException("Modrinth menjawab HTTP ${response.code}")
                response.body?.string() ?: throw IOException("Jawaban Modrinth kosong")
            }
        }
    }

    private fun sha1Of(file: File): String {
        val key = file.absolutePath
        val size = file.length()
        val modified = file.lastModified()
        hashCache[key]?.let { if (it.size == size && it.modified == modified) return it.sha1 }
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        hashCache[key] = CachedHash(size, modified, hex)
        return hex
    }

    /** Nama mod dari metadata jar, atau nama berkas tanpa ekstensi. */
    private fun displayName(file: File): String {
        val clean = ModUpdatePlanner.cleanName(file.name)
        val stem = if (clean.endsWith(".jar", ignoreCase = true)) clean.dropLast(4) else clean
        return try {
            ZipFile(file).use { zip -> ModMetadata.read(zip)?.name?.takeIf { it.isNotBlank() } } ?: stem
        } catch (_: Exception) {
            stem
        }
    }
}
