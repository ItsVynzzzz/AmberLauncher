package com.israadev.nuxlauncher.core.instance

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.modpack.ModpackStaging
import com.israadev.nuxlauncher.core.storage.DataLocation
import com.israadev.nuxlauncher.core.models.Instance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

object InstanceManager {
    private val gson = Gson()
    private const val STAGING_PREFIX = ".copying-"
    // Sisa ruang yang harus tersedia selain ukuran salinan, supaya HP tidak penuh sampai tidak bisa dipakai
    private const val FREE_SPACE_MARGIN_BYTES = 256L * 1024 * 1024
    private val _instances = MutableStateFlow<List<Instance>>(emptyList())
    val instances: StateFlow<List<Instance>> = _instances.asStateFlow()

    private val _selectedInstance = MutableStateFlow<Instance?>(null)
    val selectedInstance: StateFlow<Instance?> = _selectedInstance.asStateFlow()

    /**
     * Folder data launcher. Aturan pemilihannya (folder baru milik AmberLauncher di Android/data, atau
     * folder nuxlauncher lama bagi pengguna lama yang belum memindahkan datanya) ada di [DataLocation].
     */
    fun getNuxDir(context: Context): File = DataLocation.root(context)

    fun getAssetsDir(context: Context): File {
        val dir = File(getNuxDir(context), "assets")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getLibrariesDir(context: Context): File {
        val dir = File(getNuxDir(context), "libraries")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getVersionsDir(context: Context): File {
        val dir = File(getNuxDir(context), "versions")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getInstancesDir(context: Context): File {
        val dir = File(getNuxDir(context), "instances")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Nama folder instance di instances/: dirName dari daftar (sumber kebenaran), atau id bila instance belum
     * punya dirName (buatan versi lama). Salinan Instance yang dipegang layar bisa basi (dibuat sebelum nama
     * folder diganti), jadi daftar didahulukan; salinan yang diberikan hanya dipakai bila belum ada di daftar.
     */
    private fun folderNameOf(instance: Instance): String {
        val current = _instances.value.firstOrNull { it.id == instance.id } ?: instance
        return current.dirName?.takeIf { InstanceFolders.isSafe(it) } ?: instance.id
    }

    private fun folderNameById(instanceId: String): String {
        val current = _instances.value.firstOrNull { it.id == instanceId }
        return current?.dirName?.takeIf { InstanceFolders.isSafe(it) } ?: instanceId
    }

    fun getInstanceDir(context: Context, instance: Instance): File {
        val dir = File(getInstancesDir(context), folderNameOf(instance))
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getInstanceDirById(context: Context, instanceId: String): File {
        val dir = File(getInstancesDir(context), folderNameById(instanceId))
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getInstanceGameDir(context: Context, instance: Instance): File {
        val dir = File(getInstanceDir(context, instance), "minecraft")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun openInstanceFolder(context: Context, instance: Instance) {
        val targetDir = getInstanceDir(context, instance)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        openFolder(context, targetDir)
    }

    fun openFolder(context: Context, folder: File) {
        if (!folder.exists()) {
            folder.mkdirs()
        }

        val primaryStorage = android.os.Environment.getExternalStorageDirectory().absolutePath
        val relativePath = if (folder.absolutePath.startsWith(primaryStorage)) {
            folder.absolutePath.removePrefix(primaryStorage).trimStart('/')
        } else {
            folder.name
        }

        // 1. ZArchiver (priority for Android Minecraft community)
        val pm = context.packageManager
        val isZArchiverInstalled = try {
            pm.getPackageInfo("ru.zdevs.zarchiver", 0)
            true
        } catch (_: Exception) {
            false
        }

        if (isZArchiverInstalled) {
            try {
                val oldPolicy = android.os.StrictMode.getVmPolicy()
                try {
                    android.os.StrictMode.setVmPolicy(android.os.StrictMode.VmPolicy.Builder().build())
                    val zarchiverIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setClassName("ru.zdevs.zarchiver", "ru.zdevs.zarchiver.ZArchiver")
                        setDataAndType(android.net.Uri.fromFile(folder), "resource/folder")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(zarchiverIntent)
                    return
                } finally {
                    android.os.StrictMode.setVmPolicy(oldPolicy)
                }
            } catch (e: Exception) {
                LauncherLog.w("InstanceManager", "Failed to launch ZArchiver: ${e.message}")
            }
        }

        // 2. DocumentsUI (System Files)
        try {
            val documentUri = android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:$relativePath"
            )
            val docIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(documentUri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(docIntent)
            return
        } catch (e: Exception) {
            LauncherLog.w("InstanceManager", "Failed to launch DocumentsUI: ${e.message}")
        }

        // 3. DocumentsUI alternate MIME
        try {
            val documentUri = android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:$relativePath"
            )
            val docIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(documentUri, "vnd.android.document/directory")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(docIntent)
            return
        } catch (e: Exception) {
            LauncherLog.w("InstanceManager", "Failed to launch DocumentsUI vnd: ${e.message}")
        }

        // 4. FileProvider Chooser
        try {
            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                folder
            )
            val chooserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "resource/folder")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(chooserIntent, com.israadev.nuxlauncher.ui.i18n.Tr.t("Buka Folder")).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            return
        } catch (e: Exception) {
            LauncherLog.w("InstanceManager", "Failed to launch FileProvider: ${e.message}")
        }

        // 5. Fallback: Copy to clipboard
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Folder Path", folder.absolutePath)
            clipboard.setPrimaryClip(clip)
            com.israadev.nuxlauncher.ui.i18n.Toast.makeText(
                context,
                "Lokasi folder disalin ke clipboard:\n${folder.absolutePath}",
                com.israadev.nuxlauncher.ui.i18n.Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            com.israadev.nuxlauncher.ui.i18n.Toast.makeText(context, "Folder: ${folder.absolutePath}", com.israadev.nuxlauncher.ui.i18n.Toast.LENGTH_LONG).show()
        }
    }

    // Alias for backward compatibility
    fun getMinecraftDir(context: Context): File {
        return getNuxDir(context)
    }

    private fun getInstancesFile(context: Context): File {
        return File(getNuxDir(context), "instances.json")
    }

    private fun migrateOldStorage(context: Context) {
        try {
            val targetDir = getNuxDir(context)

            // Previous possible locations
            val oldLocations = listOf(
                File(context.dataDir ?: context.filesDir, "nuxlauncher"),
                File(context.filesDir, "nuxlauncher"),
                File(context.filesDir, ".minecraft")
            )

            for (oldLoc in oldLocations) {
                if (!oldLoc.exists()) continue
                if (oldLoc.canonicalPath == targetDir.canonicalPath) continue

                // 1. Assets
                val oldAssets = File(oldLoc, "assets")
                val newAssets = File(targetDir, "assets")
                if (oldAssets.exists()) {
                    if (!newAssets.exists() || newAssets.list().isNullOrEmpty()) {
                        oldAssets.copyRecursively(newAssets, overwrite = true)
                    }
                }

                // 2. Libraries
                val oldLibs = File(oldLoc, "libraries")
                val newLibs = File(targetDir, "libraries")
                if (oldLibs.exists()) {
                    if (!newLibs.exists() || newLibs.list().isNullOrEmpty()) {
                        oldLibs.copyRecursively(newLibs, overwrite = true)
                    }
                }

                // 3. Versions
                val oldVersions = File(oldLoc, "versions")
                val newVersions = File(targetDir, "versions")
                if (oldVersions.exists()) {
                    if (!newVersions.exists() || newVersions.list().isNullOrEmpty()) {
                        oldVersions.copyRecursively(newVersions, overwrite = true)
                    }
                }

                // 4. Instances
                val oldInstances = File(oldLoc, "instances")
                if (oldInstances.exists()) {
                    val newInstances = File(targetDir, "instances")
                    if (!newInstances.exists()) newInstances.mkdirs()
                    oldInstances.listFiles()?.forEach { instFolder ->
                        if (instFolder.isDirectory) {
                            val targetInstDir = File(newInstances, instFolder.name)
                            if (!targetInstDir.exists() || targetInstDir.list().isNullOrEmpty()) {
                                instFolder.copyRecursively(targetInstDir, overwrite = true)
                            }
                        }
                    }
                }

                // 5. instances.json
                val oldInstJson = File(oldLoc, "instances.json")
                val newInstJson = File(targetDir, "instances.json")
                if (oldInstJson.exists() && (!newInstJson.exists() || newInstJson.length() == 0L)) {
                    oldInstJson.copyTo(newInstJson, overwrite = true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private const val PREFS_NAME = "nux_launcher_prefs"
    private const val KEY_LAST_SELECTED_INSTANCE = "last_selected_instance_id"
    private var appContext: Context? = null

    private fun saveSelectedInstanceId(context: Context, id: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAST_SELECTED_INSTANCE, id).apply()
            File(context.filesDir, "selected_instance_id.txt").writeText(id)
        } catch (_: Exception) {}
    }

    private fun loadSelectedInstanceId(context: Context): String? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val id = prefs.getString(KEY_LAST_SELECTED_INSTANCE, null)
            if (!id.isNullOrBlank()) return id
            val file = File(context.filesDir, "selected_instance_id.txt")
            if (file.exists()) {
                val text = file.readText().trim()
                if (text.isNotEmpty()) return text
            }
        } catch (_: Exception) {}
        return null
    }

    fun init(context: Context) {
        // Selama data dipindahkan ke folder baru, pemuatan ulang menunggu pemindahnya sendiri (DataLocation.lock)
        synchronized(DataLocation.lock) {
            if (DataLocation.migrating) return
            initLocked(context)
        }
    }

    private fun initLocked(context: Context) {
        val startNs = System.nanoTime()
        appContext = context.applicationContext
        migrateOldStorage(context)

        val file = getInstancesFile(context)
        val loadedList = mutableListOf<Instance>()
        // Daftar persis seperti terbaca dari berkas; null bila berkas tidak ada atau tidak terbaca.
        var rawLoaded: List<Instance>? = null
        if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<List<Instance>>() {}.type
                val list: List<Instance>? = gson.fromJson(json, type)
                if (list != null) {
                    rawLoaded = list
                    loadedList.addAll(list.filterNot { it.id == "instance_default" || it.name == "Minecraft 1.20.4" })
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Scan instances directory for isolated instance.json (like Windows version)
        val instancesDir = getInstancesDir(context)
        // Folder yang sudah dimiliki instance di daftar (lewat id atau dirName) bukan instance yang hilang dari daftar
        val knownFolders = HashSet<String>()
        for (inst in loadedList) {
            knownFolders.add(inst.id)
            inst.dirName?.let { knownFolders.add(it) }
        }
        val folderInstances = mutableListOf<Pair<String, Instance>>()
        instancesDir.listFiles()?.forEach { instFolder ->
            if (instFolder.isDirectory) {
                val configFile = File(instFolder, "instance.json")
                if (configFile.exists() && instFolder.name !in knownFolders) {
                    try {
                        val inst = gson.fromJson(configFile.readText(), Instance::class.java)
                        if (inst != null) {
                            folderInstances.add(instFolder.name to inst)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // Id tidak boleh ganda (lihat mergeInstances): dua id sama membuat layar pemilih instance crash
        val mergedList = mergeInstances(loadedList, folderInstances)

        // Nama folder mengikuti nama instance (instance lama berfolder "inst_<acak>" dipindah di sini)
        val namedList = nameFolders(context, instancesDir, mergedList)

        // Check if downloaded client JAR and loader exist for each instance
        val verifiedList = namedList.map { inst ->
            inst.copy(isDownloaded = isInstanceDownloadedIn(context, inst, File(instancesDir, folderOf(inst))))
        }

        _instances.value = verifiedList

        // Preserve and restore the user's last selected instance
        val savedId = loadSelectedInstanceId(context)
        val currentSelectedId = _selectedInstance.value?.id ?: savedId

        val targetInstance = verifiedList.find { it.id == currentSelectedId }
            ?: (if (savedId != null) verifiedList.find { it.id == savedId } else null)
            ?: verifiedList.firstOrNull()

        _selectedInstance.value = targetInstance
        targetInstance?.let { saveSelectedInstanceId(context, it.id) }

        // Dulu setiap init menulis ulang instances.json dan instance.json SEMUA instance (masing-masing dengan fsync) di
        // thread UI, padahal biasanya tidak ada yang berubah. Di HP kelas bawah itu bisa memakan ratusan milidetik di
        // setiap pembukaan. Sekarang hanya menulis bila daftarnya memang berubah atau ada instance.json yang hilang.
        val needsSave = rawLoaded == null || verifiedList != rawLoaded || folderInstances.isNotEmpty() ||
            verifiedList.any { !File(File(instancesDir, folderOf(it)), "instance.json").exists() }
        if (needsSave) save(context)
        LauncherLog.i(
            "Mulai",
            "InstanceManager.init ${(System.nanoTime() - startNs) / 1_000_000} ms: ${verifiedList.size} instance, " +
                if (needsSave) "daftar disimpan ulang" else "tidak ada yang perlu ditulis"
        )
    }

    /** Nama folder menurut data instance itu sendiri (dipakai saat daftar belum dipasang, mis. di init). */
    private fun folderOf(inst: Instance): String =
        inst.dirName?.takeIf { InstanceFolders.isSafe(it) } ?: inst.id

    /** Game, mesin, Forge, atau pemasangan sedang berjalan: folder instance sedang dipakai, jangan dipindah. */
    private fun foldersBusy(context: Context): Boolean {
        if (com.israadev.nuxlauncher.core.download.InstallSession.active) return true
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses?.any {
                it.processName.endsWith(":game") || it.processName.endsWith(":horizon") ||
                    it.processName.endsWith(":forge") || it.processName.endsWith(":starling")
            } == true
        } catch (_: Throwable) {
            // Tidak bisa memastikan: lebih aman tidak memindahkan apa pun
            true
        }
    }

    /** Memindah folder satu tingkat di dalam instances/. false bila gagal; isi folder tidak pernah disalin atau dihapus. */
    private fun moveFolder(instancesDir: File, from: String, to: String): Boolean {
        return try {
            val src = File(instancesDir, from)
            val dst = File(instancesDir, to)
            if (!src.isDirectory || dst.exists()) false else src.renameTo(dst)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Menyamakan nama folder dengan nama instance. Instance lama (folder berupa id acak "inst_...") dipindah satu
     * kali di sini, begitu juga instance yang namanya sempat diganti saat game atau unduhan berjalan. Bila ada yang
     * berjalan, atau pemindahan gagal, folder dibiarkan di tempatnya (tidak ada data yang hilang) dan dicoba lagi
     * pada pembukaan berikutnya. Instance yang lebih tua mendapat nama polos lebih dulu; yang kembar diberi _2, _3.
     */
    private fun nameFolders(context: Context, instancesDir: File, list: List<Instance>): List<Instance> {
        if (list.isEmpty()) return list
        val taken = HashSet<String>()
        instancesDir.list()?.forEach { taken.add(it.lowercase()) }
        for (inst in list) taken.add(folderOf(inst).lowercase())

        var busy: Boolean? = null
        val changed = HashMap<String, Instance>()
        for (inst in list.sortedBy { it.createdTime }) {
            val current = folderOf(inst)
            // Belum ada foldernya (instance yang isinya belum pernah dibuat): tidak ada yang perlu dipindah
            if (!File(instancesDir, current).isDirectory) continue
            val target = InstanceFolders.targetFolder(inst.name, current, taken) ?: continue
            if (busy == null) busy = foldersBusy(context)
            if (busy == true) break
            if (moveFolder(instancesDir, current, target)) {
                taken.remove(current.lowercase())
                taken.add(target.lowercase())
                changed[inst.id] = inst.copy(dirName = target)
                LauncherLog.i("InstanceManager", "Folder instance dipindah: $current -> $target")
            } else {
                LauncherLog.w("InstanceManager", "Folder instance belum bisa dipindah: $current -> $target")
            }
        }
        return if (changed.isEmpty()) list else list.map { changed[it.id] ?: it }
    }

    fun isInstanceDownloaded(context: Context, inst: Instance): Boolean =
        isInstanceDownloadedIn(context, inst, getInstanceDir(context, inst))

    private fun isInstanceDownloadedIn(context: Context, inst: Instance, instDir: File): Boolean {
        // Mesin Rust tidak memakai client.jar maupun library Java: tidak ada yang perlu diunduh launcher
        if (com.israadev.nuxlauncher.core.horizon.HorizonEngine.isEngine(inst.loader)) return true
        // Starling: berkas resmi game (±500 MiB) diunduh launcher lewat tombol UNDUH; tanpa patokan mesin mengunduh sendiri
        if (com.israadev.nuxlauncher.core.horizon.StarlingEngine.isEngine(inst.loader)) {
            return com.israadev.nuxlauncher.core.horizon.StarlingFiles.ready(context)
        }
        // Instance dari modpack yang isinya belum selesai dipasang belum siap main: UNDUH melanjutkannya
        if (ModpackStaging.hasPending(instDir)) return false
        val versionsDir = getVersionsDir(context)
        val vDir = File(versionsDir, inst.mcVersion)
        val jarFile = File(vDir, "${inst.mcVersion}.jar")
        val jsonFile = File(vDir, "${inst.mcVersion}.json")
        if (!jarFile.exists() || jarFile.length() == 0L || !jsonFile.exists() || jsonFile.length() == 0L) {
            return false
        }
        if (inst.loader.equals("fabric", ignoreCase = true)) {
            val fabricJson = if (inst.loaderVersion.isNotBlank()) {
                File(vDir, "fabric-${inst.loaderVersion}.json")
            } else {
                vDir.listFiles { f -> f.name.startsWith("fabric-") && f.name.endsWith(".json") }?.firstOrNull()
            }
            if (fabricJson == null || !fabricJson.exists() || fabricJson.length() == 0L) {
                return false
            }
            val libDir = getLibrariesDir(context)
            val fabricLoaderDir = File(libDir, "net/fabricmc/fabric-loader")
            if (!fabricLoaderDir.exists() || fabricLoaderDir.listFiles().isNullOrEmpty()) {
                return false
            }
        }
        val forgeFlavor = com.israadev.nuxlauncher.core.forge.ForgeFlavor.of(inst.loader)
        if (forgeFlavor != null) {
            // Hanya cek keberadaan berkas (fungsi ini dipanggil dari komposisi UI)
            if (!com.israadev.nuxlauncher.core.forge.ForgeInstaller.isInstalled(context, inst.mcVersion, inst.loaderVersion, forgeFlavor)) {
                return false
            }
        }
        return true
    }


    fun createInstance(context: Context, newInstance: Instance) {
        // Folder baru diberi nama dari nama instance (mudah dicari di pengelola berkas), tanpa menimpa folder lain
        val instance = synchronized(this) {
            val taken = HashSet<String>()
            getInstancesDir(context).list()?.forEach { taken.add(it.lowercase()) }
            for (other in _instances.value) if (other.id != newInstance.id) taken.add(folderOf(other).lowercase())
            val chosen = newInstance.dirName?.takeIf { InstanceFolders.isSafe(it) }
                ?: InstanceFolders.unique(InstanceFolders.sanitize(newInstance.name), taken)
            newInstance.copy(dirName = chosen)
        }

        // Create isolated instance folder structure
        val instDir = File(getInstancesDir(context), folderOf(instance))
        val gameDir = File(instDir, "minecraft")
        instDir.mkdirs()
        gameDir.mkdirs()

        // Write isolated instance.json
        try {
            File(instDir, "instance.json").writeText(gson.toJson(instance))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val list = _instances.value.toMutableList()
        // Id yang sama tidak boleh masuk dua kali (mis. init() sempat menemukan foldernya lebih dulu)
        list.removeAll { it.id == instance.id }
        list.add(0, instance)
        _instances.value = list
        _selectedInstance.value = instance
        saveSelectedInstanceId(context, instance.id)
        save(context)
    }

    /** Menyematkan atau melepas sematan instance. Yang disematkan tampil paling atas di pemilih instance. */
    fun setPinned(context: Context, instanceId: String, pinned: Boolean) {
        val current = _instances.value.firstOrNull { it.id == instanceId } ?: return
        if (current.pinned == pinned) return
        updateInstance(context, current.copy(pinned = pinned))
    }

    /** Urutan tampil di pemilih: yang disematkan dulu, sisanya tetap berurutan seperti di daftar (pengurutan stabil). */
    fun pinnedFirst(list: List<Instance>): List<Instance> =
        if (list.none { it.pinned }) list else list.sortedByDescending { it.pinned }

    // ---- Menggandakan instance ------------------------------------------------------------------------

    sealed class DuplicateResult {
        data class Done(val instance: Instance) : DuplicateResult()
        data class Failed(val message: String) : DuplicateResult()
    }

    // Hanya satu penggandaan sekaligus, dan nama folder sementaranya (agar tidak ikut tersapu).
    @Volatile private var duplicating = false
    private val stagingInUse = HashSet<String>()

    /**
     * Menggandakan [source] menjadi instance baru bernama "<nama> (salinan)": semua berkas instance (mod, dunia,
     * resource pack, pengaturan, ...) disalin, kecuali log dan laporan crash. Berkas game bersama (versi, library,
     * aset) tidak ikut karena memang dipakai bersama, jadi salinannya tidak memakan ruang sebesar instance
     * pertamanya. Instance asli tidak disentuh dan pilihan instance aktif tidak berubah.
     *
     * Berjalan di thread sendiri (dunia besar bisa menyalin lama); [onResult] dipanggil di thread utama.
     * Disalin dulu ke folder sementara dan baru dinamai dan didaftarkan setelah selesai, jadi instance setengah
     * jadi tidak pernah muncul di daftar atau bisa dijalankan. Ditolak bila game atau pemasangan sedang berjalan
     * (berkas bisa sedang ditulis) atau ruang penyimpanan tidak cukup.
     */
    fun duplicateInstance(context: Context, source: Instance, onResult: (DuplicateResult) -> Unit) {
        val app = context.applicationContext
        if (duplicating) {
            onResult(DuplicateResult.Failed("Masih menggandakan instance lain. Tunggu sampai selesai."))
            return
        }
        if (foldersBusy(app)) {
            onResult(DuplicateResult.Failed("Game atau pemasangan sedang berjalan. Coba lagi setelah selesai."))
            return
        }
        duplicating = true
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Thread({
            var copy: Instance? = null
            var failure = "Gagal menggandakan instance."
            try {
                val outcome = copyInstanceFiles(app, source)
                copy = outcome.first
                outcome.second?.let { failure = it }
            } catch (e: Throwable) {
                LauncherLog.w("InstanceManager", "Gagal menggandakan instance: ${e.message}")
            }
            main.post {
                duplicating = false
                val made = copy
                if (made == null) {
                    onResult(DuplicateResult.Failed(failure))
                } else {
                    // createInstance langsung memilih instance baru; pilihan semula dikembalikan
                    val previous = _selectedInstance.value
                    createInstance(app, made)
                    previous?.let { prev ->
                        _instances.value.firstOrNull { it.id == prev.id }?.let { selectInstance(it) }
                    }
                    onResult(DuplicateResult.Done(made))
                }
            }
        }, "amber-duplicate").start()
    }

    /** Menyalin isi instance ke folder barunya. Mengembalikan instance baru, atau pesan kegagalan. */
    private fun copyInstanceFiles(context: Context, source: Instance): Pair<Instance?, String?> {
        val instancesDir = getInstancesDir(context)
        val current = _instances.value.firstOrNull { it.id == source.id } ?: source
        val srcDir = File(instancesDir, folderOf(current))

        val usedNames = _instances.value.map { it.name.lowercase() }.toHashSet()
        var newName = "${current.name} (salinan)"
        var n = 2
        while (newName.lowercase() in usedNames) {
            newName = "${current.name} (salinan $n)"
            n++
        }
        val newId = "inst_${UUID.randomUUID()}"
        val taken = HashSet<String>()
        instancesDir.list()?.forEach { taken.add(it.lowercase()) }
        for (other in _instances.value) taken.add(folderOf(other).lowercase())
        val dirName = InstanceFolders.unique(InstanceFolders.sanitize(newName), taken)

        // Sisa folder sementara dari penyalinan yang terputus (aplikasi ditutup di tengah jalan) dibersihkan dulu
        instancesDir.listFiles()?.forEach { f ->
            if (f.isDirectory && f.name.startsWith(STAGING_PREFIX)) {
                val inUse = synchronized(stagingInUse) { f.name in stagingInUse }
                if (!inUse) f.deleteRecursively()
            }
        }

        val staging = File(instancesDir, STAGING_PREFIX + newId)
        synchronized(stagingInUse) { stagingInUse.add(staging.name) }
        try {
            if (srcDir.isDirectory) {
                val needed = treeSize(srcDir, "")
                val free = android.os.StatFs(instancesDir.path).availableBytes
                if (free < needed + FREE_SPACE_MARGIN_BYTES) {
                    return null to "Ruang penyimpanan tidak cukup untuk menggandakan instance ini."
                }
                copyTree(srcDir, staging, "")
            } else {
                staging.mkdirs()
            }
            val finalDir = File(instancesDir, dirName)
            if (finalDir.exists() || !staging.renameTo(finalDir)) {
                staging.deleteRecursively()
                return null to "Gagal menggandakan instance."
            }
            val copy = current.copy(
                id = newId,
                name = newName,
                dirName = dirName,
                createdTime = System.currentTimeMillis(),
                pinned = false
            )
            return copy to null
        } catch (e: Throwable) {
            staging.deleteRecursively()
            LauncherLog.w("InstanceManager", "Gagal menyalin instance: ${e.message}")
            return null to "Gagal menggandakan instance."
        } finally {
            synchronized(stagingInUse) { stagingInUse.remove(staging.name) }
        }
    }

    /** Yang tidak ikut disalin: instance.json (dibuat ulang untuk instance baru), log, dan laporan crash. */
    private fun skipOnDuplicate(rel: String): Boolean =
        rel == "instance.json" || rel == "minecraft/logs" || rel == "minecraft/crash-reports"

    private fun relOf(parentRel: String, name: String): String = if (parentRel.isEmpty()) name else "$parentRel/$name"

    private fun treeSize(file: File, rel: String): Long {
        if (rel.isNotEmpty() && skipOnDuplicate(rel)) return 0L
        if (file.isFile) return file.length()
        var total = 0L
        file.listFiles()?.forEach { total += treeSize(it, relOf(rel, it.name)) }
        return total
    }

    private fun copyTree(src: File, dst: File, rel: String) {
        if (rel.isNotEmpty() && skipOnDuplicate(rel)) return
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { copyTree(it, File(dst, it.name), relOf(rel, it.name)) }
        } else {
            src.inputStream().use { input ->
                dst.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            dst.setLastModified(src.lastModified())
        }
    }

    fun selectInstance(instance: Instance) {
        _selectedInstance.value = instance
        appContext?.let { saveSelectedInstanceId(it, instance.id) }
    }

    fun updateInstance(context: Context, newValue: Instance) {
        // instance.json ikut ditulis oleh save() di bawah (atomik, dan dilewati bila tidak berubah)
        val updated = synchronized(this) {
            val current = _instances.value.firstOrNull { it.id == newValue.id }
            // Nama folder hanya diatur di sini: salinan Instance dari layar bisa basi, jadi dirName-nya tidak dipercaya
            var dirName = current?.dirName
            if (current != null && newValue.name != current.name) {
                renamedFolder(context, current, newValue.name)?.let { dirName = it }
            }
            newValue.copy(dirName = dirName)
        }
        val list = _instances.value.map { if (it.id == updated.id) updated else it }
        _instances.value = list
        if (_selectedInstance.value?.id == updated.id) {
            _selectedInstance.value = updated
        }
        save(context)
    }

    /** Memindah folder [current] mengikuti nama baru. Mengembalikan dirName baru, atau null bila folder tidak berubah. */
    private fun renamedFolder(context: Context, current: Instance, newName: String): String? {
        val instancesDir = getInstancesDir(context)
        val from = folderOf(current)
        val taken = HashSet<String>()
        instancesDir.list()?.forEach { taken.add(it.lowercase()) }
        for (other in _instances.value) taken.add(folderOf(other).lowercase())
        val to = InstanceFolders.targetFolder(newName, from, taken) ?: return null
        // Folder yang sedang dipakai tidak dipindah; init() menyusulkannya pada pembukaan berikutnya
        if (foldersBusy(context)) return null
        if (!moveFolder(instancesDir, from, to)) return null
        LauncherLog.i("InstanceManager", "Folder instance dipindah: $from -> $to")
        return to
    }

    fun deleteInstance(context: Context, instanceId: String) {
        // Nama folder dicari dulu, sebelum instance-nya keluar dari daftar
        val instDir = File(getInstancesDir(context), folderNameById(instanceId))
        val list = _instances.value.filterNot { it.id == instanceId }
        _instances.value = list
        if (_selectedInstance.value?.id == instanceId) {
            val nextInst = list.firstOrNull()
            _selectedInstance.value = nextInst
            nextInst?.let { saveSelectedInstanceId(context, it.id) }
        }
        save(context)

        // Delete isolated instance directory and all contents from storage
        try {
            if (instDir.exists()) {
                instDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Isi terakhir yang ditulis proses ini, supaya berkas yang tidak berubah tidak ditulis ulang.
    // Dikaitkan dengan jalurnya: setelah data dipindahkan ke folder lain, berkas di sana tetap ditulis sendiri.
    private var lastInstancesJson: String? = null
    private var lastInstancesPath: String? = null
    private val lastInstanceJson = HashMap<String, String>()

    @Synchronized
    private fun save(context: Context) {
        try {
            val file = getInstancesFile(context)
            val json = gson.toJson(_instances.value)
            // instances.json adalah sumber kebenaran daftar: ditulis dengan fsync, dan hanya bila isinya berubah.
            if (json != lastInstancesJson || file.path != lastInstancesPath || !file.exists()) {
                com.israadev.nuxlauncher.core.utils.AtomicFiles.writeText(file, json)
                lastInstancesJson = json
                lastInstancesPath = file.path
            }
            // instance.json per folder hanyalah salinan yang bisa dibuat ulang dari daftar di atas (dipakai saat
            // memulihkan instance yang hilang dari daftar): cukup rename atomik tanpa fsync, dan lewati yang tidak berubah.
            _instances.value.forEach { inst ->
                try {
                    val target = File(getInstanceDir(context, inst), "instance.json")
                    val instJson = gson.toJson(inst)
                    if (lastInstanceJson[target.path] == instJson && target.exists()) return@forEach
                    com.israadev.nuxlauncher.core.utils.AtomicFiles.writeText(target, instJson, sync = false)
                    lastInstanceJson[target.path] = instJson
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
