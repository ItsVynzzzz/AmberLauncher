package com.israadev.nuxlauncher.core.crash

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Looper
import com.israadev.nuxlauncher.core.account.AccountManager
import com.israadev.nuxlauncher.core.horizon.HorizonContent
import com.israadev.nuxlauncher.core.horizon.StarlingEngine
import com.israadev.nuxlauncher.core.horizon.RustSettingsStore
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.core.update.UpdateChecker
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import org.json.JSONObject

/**
 * Menyimpan log setiap sesi game ke folder `logs` di data launcher, supaya log tidak hilang saat
 * game dijalankan lagi (berkas log sesi berjalan selalu ditimpa) dan bisa dibagikan dengan satu ketukan
 * dari Pengaturan > Log game.
 *
 * Tiap berkas berisi ringkasan sesi (versi launcher, HP, instance, mod, shader), kejadian di dalam launcher
 * sebelum dan selama sesi itu ([LauncherLog]), lalu log lengkapnya, sudah tanpa token atau kata sandi.
 * Disimpan saat game keluar; bila proses game dimatikan Android sebelum sempat menyimpan, sesi itu
 * disimpan pada peluncuran berikutnya atau saat Log game dibuka.
 *
 * Masalah yang terjadi di luar game (game belum sempat berjalan) tidak punya log game; untuk itu ada
 * laporan launcher ([saveLauncherReport]) yang berisi ringkasan perangkat, kejadian launcher, dan
 * peringatan sistem.
 *
 * Sesi mesin Starling ([ENGINE_STARLING]) disimpan dengan cara yang sama, tetapi isinya disusun dari berkas milik mesin
 * (logs/latest.log, performance/, horizon-graphics.txt) dan dari riwayat Android tentang kenapa prosesnya berhenti,
 * karena mesinnya berjalan di proses :starling dan tidak menulis ke latestlog.txt milik launcher.
 *
 * Aman dipanggil dari proses game maupun proses launcher.
 */
object GameLogArchive {

    private const val KEEP_COUNT = 10
    private const val KEEP_LAUNCHER_COUNT = 3
    private const val LAUNCHER_LOOKBACK_MS = 20L * 60L * 1000L
    private const val MAX_LAUNCHER_BYTES = 160L * 1024L
    private const val MAX_LAUNCHER_REPORT_BYTES = 320L * 1024L
    private const val LOGCAT_LINES = 500
    private const val MAX_LOG_BYTES = 4L * 1024L * 1024L
    private const val HEAD_BYTES = 192L * 1024L
    private const val MAX_EXTRA_BYTES = 128L * 1024L
    private const val MAX_CONFIG_BYTES = 8L * 1024L
    private const val MAX_LISTED = 80
    private const val MAX_ENGINE_LOG_BYTES = 160L * 1024L
    private const val MAX_ENGINE_SHADER_LINES = 80
    private val ENGINE_SETTING_KEYS = listOf(
        "renderer", "render_scale", "super_perf_mode", "battery_perf_mode", "max_fps_mode",
        "disable_game_animations", "fast_chunk_upload", "experimental_leaf_culling", "record_performance",
        "performance_hints", "worker_limit", "cache_budget_mib", "profile"
    )

    private const val LOG_NAME = "latestlog.txt"
    private const val META_NAME = "last-session.properties"
    const val ARCHIVE_PREFIX = "amber-log_"

    /** Pengganti nama instance pada nama berkas laporan launcher (tanda kurung tidak mungkin muncul dari nama instance). */
    private const val LAUNCHER_TAG = "(launcher)"

    private const val KEY_START = "start"
    private const val KEY_INSTANCE = "instance"
    private const val KEY_MC = "mc"
    private const val KEY_LOADER = "loader"
    private const val KEY_LOADER_VERSION = "loaderVersion"
    private const val KEY_GAME_DIR = "gameDir"
    private const val KEY_MG_DIR = "mgDir"
    private const val KEY_ARCHIVED = "archived"
    private const val KEY_ENGINE = "engine"

    /** Nilai KEY_ENGINE untuk sesi mesin Starling (kosong untuk sesi game Java). */
    const val ENGINE_STARLING = "starling"

    private const val MAX_SESSION_JSON_BYTES = 8L * 1024L
    private const val MAX_PERF_FRAMES_CHARS = 900
    private val STARLING_OPTION_KEYS = setOf(
        "renderDistance", "simulationDistance", "maxFps", "graphicsMode", "particles", "biomeBlendRadius",
        "entityDistanceScaling", "mipmapLevels", "enableVsync", "guiScale", "fov", "renderClouds", "ao",
        "entityShadows", "fancyGraphics", "cloudRange", "menuBackgroundBlurriness"
    )

    private val metaLock = Any()

    /** Folder tempat log disimpan (di folder data AmberLauncher: logs). */
    fun logsDir(context: Context): File = File(InstanceManager.getNuxDir(context), "logs")

    /** Log yang tersimpan, yang terbaru di depan. */
    fun list(context: Context): List<File> =
        logsDir(context)
            .listFiles { f -> f.isFile && f.name.startsWith(ARCHIVE_PREFIX) && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    /** Apakah berkas ini laporan launcher (bukan log sesi game). */
    fun isLauncherReport(file: File): Boolean = file.name.endsWith("_" + LAUNCHER_TAG + ".txt")

    /**
     * Pecah nama berkas menjadi (waktu, nama instance) untuk ditampilkan.
     * Nama berkas: amber-log_2026-10-26_06-30-11_NamaInstance.txt
     */
    fun describe(file: File): Pair<String, String> {
        val core = file.name.removePrefix(ARCHIVE_PREFIX).removeSuffix(".txt")
        val parts = core.split('_', limit = 3)
        if (parts.size < 3) return core to ""
        val time = parts[0] + " " + parts[1].take(5).replace('-', ':')
        return time to parts[2]
    }

    // ---------------------------------------------------------------------------------------------
    // Pencatatan sesi
    // ---------------------------------------------------------------------------------------------

    /** Dipanggil saat sesi game dimulai: mencatat sesi ini sebagai "belum disimpan". */
    fun beginSession(
        context: Context,
        startTime: Long,
        instance: String,
        mcVersion: String,
        loader: String,
        loaderVersion: String?,
        gameDir: String,
        engine: String = ""
    ) {
        val props = Properties()
        props.setProperty(KEY_START, startTime.toString())
        props.setProperty(KEY_INSTANCE, instance)
        props.setProperty(KEY_MC, mcVersion)
        props.setProperty(KEY_LOADER, loader)
        props.setProperty(KEY_LOADER_VERSION, loaderVersion.orEmpty())
        props.setProperty(KEY_GAME_DIR, gameDir)
        if (engine.isNotBlank()) props.setProperty(KEY_ENGINE, engine)
        writeMeta(context, props)
    }

    /** Mencatat folder log MobileGlues bila renderer memakainya (dibaca dari lingkungan proses game). */
    fun noteMgDir(context: Context, dir: String) {
        synchronized(metaLock) {
            val props = readMeta(context) ?: return
            props.setProperty(KEY_MG_DIR, dir)
            writeMeta(context, props)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Menyimpan log
    // ---------------------------------------------------------------------------------------------

    /** Menyimpan sesi terakhir bila belum pernah disimpan. Mengembalikan berkas baru, atau null. */
    fun archiveIfNeeded(context: Context): File? {
        val props = readMeta(context) ?: return null
        if (props.getProperty(KEY_ARCHIVED) == "1") return null
        // Sesi Starling yang masih berjalan belum selesai: laporannya disusun setelah prosesnya berhenti.
        if (isEngineSession(props) && StarlingEngine.isProcessAlive(context)) return null
        return archiveSession(context)
    }

    /** Menyimpan sesi terakhir (menimpa simpanan sebelumnya untuk sesi yang sama). */
    fun archiveSession(context: Context): File? {
        val props = readMeta(context) ?: return null
        val engine = isEngineSession(props)
        val source = if (engine) File(props.getProperty(KEY_GAME_DIR).orEmpty(), "logs/latest.log") else File(context.filesDir, LOG_NAME)
        var result: File? = null
        try {
            // Sesi mesin tetap disimpan walau latest.log kosong: sebab proses berhenti dan pengaturannya justru paling
            // berguna saat mesin gagal sebelum sempat menulis log.
            if (engine || (source.exists() && source.length() > 0L)) {
                val start = props.getProperty(KEY_START)?.toLongOrNull()
                    ?: source.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                val dir = logsDir(context)
                if (!dir.exists()) dir.mkdirs()
                val out = File(dir, ARCHIVE_PREFIX + fileStamp(start) + "_" + safeName(props.getProperty(KEY_INSTANCE)) + ".txt")
                val tmp = File(dir, out.name + ".tmp")
                tmp.bufferedWriter(Charsets.UTF_8).use { w ->
                    if (engine) writeStarlingReport(context, w, props, source, start) else writeReport(context, w, props, source, start)
                }
                if (out.exists()) out.delete()
                if (tmp.renameTo(out)) result = out else tmp.delete()
                prune(dir)
            }
        } catch (_: Throwable) {
            // Penyimpanan log tidak boleh mengganggu game atau peluncur.
        }
        // Tandai sudah ditangani walau gagal, agar tidak diulang tiap kali.
        props.setProperty(KEY_ARCHIVED, "1")
        writeMeta(context, props)
        return result
    }

    /**
     * Membuat laporan khusus launcher (tanpa game): ringkasan perangkat, instance, akun (hanya jenisnya),
     * kejadian launcher, dan peringatan sistem. Untuk masalah yang muncul sebelum game dimulai.
     * Jangan dipanggil dari thread utama (membaca logcat). Mengembalikan berkas laporan, atau null bila gagal.
     */
    fun saveLauncherReport(context: Context): File? {
        return try {
            val dir = logsDir(context)
            if (!dir.exists()) dir.mkdirs()
            val now = System.currentTimeMillis()
            val out = File(dir, ARCHIVE_PREFIX + fileStamp(now) + "_" + LAUNCHER_TAG + ".txt")
            val tmp = File(dir, out.name + ".tmp")
            tmp.bufferedWriter(Charsets.UTF_8).use { w -> writeLauncherReport(context, w, now) }
            if (out.exists()) out.delete()
            if (tmp.renameTo(out)) {
                prune(dir)
                out
            } else {
                tmp.delete()
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun prune(dir: File) {
        val all = dir.listFiles { f -> f.isFile && f.name.startsWith(ARCHIVE_PREFIX) } ?: return
        val sorted = all.sortedByDescending { it.lastModified() }
        // Log game dan laporan launcher dihitung terpisah, supaya laporan launcher tidak menggeser log game.
        sorted.filter { !isLauncherReport(it) }.drop(KEEP_COUNT).forEach { it.delete() }
        sorted.filter { isLauncherReport(it) }.drop(KEEP_LAUNCHER_COUNT).forEach { it.delete() }
    }

    /** Menghapus semua log tersimpan. Mengembalikan jumlah berkas yang terhapus. */
    fun deleteAll(context: Context): Int {
        var n = 0
        list(context).forEach { if (it.delete()) n++ }
        LauncherLog.clear(context)
        return n
    }

    // ---------------------------------------------------------------------------------------------
    // Isi laporan
    // ---------------------------------------------------------------------------------------------

    private fun writeReport(context: Context, w: Writer, props: Properties, source: File, start: Long) {
        val gameDir = props.getProperty(KEY_GAME_DIR).orEmpty()
        val clock = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        w.line("===== AmberLauncher: laporan sesi game =====")
        w.line("Disimpan     : " + clock.format(Date()))
        w.line("Mulai        : " + clock.format(Date(start)))
        val lastWrite = source.lastModified()
        if (lastWrite > start) {
            val secs = (lastWrite - start) / 1000L
            w.line("Log terakhir : " + clock.format(Date(lastWrite)) + " (sekitar " + (secs / 60L) + " menit " + (secs % 60L) + " detik)")
        }
        w.line("Launcher     : " + UpdateChecker.currentVersionName(context) + " (build " +
            UpdateChecker.currentVersionCode(context) + ", " + (if (UpdateChecker.wants32BitApk()) "32-bit" else "64-bit") + ")")
        w.line("Perangkat    : " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE +
            " (API " + Build.VERSION.SDK_INT + "), " + Build.SUPPORTED_ABIS.joinToString("/") + ", RAM " + totalRamMb(context) + " MB")
        w.line("Instance     : " + props.getProperty(KEY_INSTANCE).orEmpty())
        val loaderVersion = props.getProperty(KEY_LOADER_VERSION).orEmpty()
        w.line("Minecraft    : " + props.getProperty(KEY_MC).orEmpty() + " | Loader: " +
            props.getProperty(KEY_LOADER).orEmpty() + (if (loaderVersion.isNotBlank()) " $loaderVersion" else ""))
        try {
            val s = SettingsManager.settings.value
            w.line("Pengaturan   : renderer=" + s.selectedRenderer + ", RAM game=" + s.ramMb + " MB, resolusi=" + s.resolutionRatio + "%")
        } catch (_: Throwable) {
        }

        // Kejadian di dalam launcher sebelum dan selama sesi ini (persiapan, unduhan, login, galat sebelum game)
        w.section("Log launcher (sebelum dan selama sesi ini)")
        val launcherText = LauncherLog.recentText(context, start - LAUNCHER_LOOKBACK_MS, MAX_LAUNCHER_BYTES)
        if (launcherText.isBlank()) w.line("(tidak ada kejadian tercatat)") else writeLines(w, launcherText)

        if (gameDir.isNotBlank()) {
            writeModsAndShaders(w, File(gameDir))
        }

        // Log MobileGlues (berisi galat penerjemahan shader), hanya yang ditulis selama sesi ini
        writeMobileGluesFiles(w, props.getProperty(KEY_MG_DIR).orEmpty(), start)

        // Laporan crash Minecraft bila ada yang terbit selama sesi ini
        if (gameDir.isNotBlank()) {
            val reports = File(gameDir, "crash-reports")
            val newest = reports.listFiles { f -> f.isFile && f.name.startsWith("crash-") && f.name.endsWith(".txt") }
                ?.maxByOrNull { it.lastModified() }
            if (newest != null && newest.lastModified() >= start - 10_000L) {
                w.section("Laporan crash Minecraft (" + newest.name + ")")
                writeHead(w, newest, MAX_EXTRA_BYTES)
            }
        }

        w.section("Log peluncur dan game")
        writeMainLog(w, source)
        w.flush()
    }

    private fun writeLauncherReport(context: Context, w: Writer, now: Long) {
        val clock = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        w.line("===== AmberLauncher: laporan launcher =====")
        w.line("Disimpan     : " + clock.format(Date(now)))
        w.line("Launcher     : " + UpdateChecker.currentVersionName(context) + " (build " +
            UpdateChecker.currentVersionCode(context) + ", " + (if (UpdateChecker.wants32BitApk()) "32-bit" else "64-bit") + ")")
        w.line("Perangkat    : " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE +
            " (API " + Build.VERSION.SDK_INT + "), " + Build.SUPPORTED_ABIS.joinToString("/") + ", RAM " + totalRamMb(context) + " MB")
        try {
            val s = SettingsManager.settings.value
            w.line("Pengaturan   : renderer=" + s.selectedRenderer + ", RAM game=" + s.ramMb + " MB, resolusi=" + s.resolutionRatio + "%")
        } catch (_: Throwable) {
        }
        w.line("Penyimpanan  : " + storageText(context))

        try {
            val list = InstanceManager.instances.value
            w.line("Instance     : " + list.size + " terdaftar")
            for (inst in list.take(MAX_LISTED)) {
                w.line("  " + inst.name + " | " + inst.mcVersion + " | " + inst.loader +
                    (if (inst.loaderVersion.isNotBlank()) " " + inst.loaderVersion else "") +
                    " | java=" + inst.javaRuntime + " | diunduh=" + yesNo(inst.isDownloaded))
            }
        } catch (_: Throwable) {
            w.line("Instance     : (tidak tersedia)")
        }
        try {
            val types = AccountManager.accounts.value.groupingBy { it.safeAccountType }.eachCount()
            w.line("Akun         : " +
                (if (types.isEmpty()) "belum ada" else types.entries.joinToString(", ") { it.key + " x" + it.value }))
        } catch (_: Throwable) {
            w.line("Akun         : (tidak tersedia)")
        }

        w.section("Kejadian di dalam launcher")
        val events = LauncherLog.recentText(context, 0L, MAX_LAUNCHER_REPORT_BYTES)
        if (events.isBlank()) w.line("(tidak ada kejadian tercatat)") else writeLines(w, events)

        writeEngineSection(context, w)
        writeStarlingEngineSection(context, w)

        w.section("Peringatan dan galat sistem (logcat, hanya milik aplikasi ini)")
        val logcat = LauncherLog.logcatWarnings(LOGCAT_LINES)
        if (logcat.isBlank()) w.line("(tidak tersedia di HP ini atau kosong)") else writeLines(w, logcat.trimEnd('\n'))
        w.flush()
    }

    /**
     * Mesin Rust (Horizon) berjalan di proses lain dan mencatat dirinya sendiri di `performance/diagnostic.log`
     * (sudah disamarkan oleh mesin): pengaturan yang dibaca, shader yang dimuat (baris "shader pack ..." memuat
     * nama folder dan daftar programnya, atau alasan gagalnya), dan peringatan. Dua bagian pertama berguna saat
     * shader tidak tampil seperti seharusnya. Tidak ditulis bila mesin belum pernah dijalankan.
     */
    private fun writeEngineSection(context: Context, w: Writer) {
        try {
            val home = StarlingEngine.engineHome(context)
            if (!home.isDirectory) return
            val game = HorizonContent.gameHome(home)
            w.section("Mesin Rust (Starling)")

            val config = File(home, "android-launcher.json")
            val shown = ArrayList<String>()
            if (config.isFile && config.length() < 64L * 1024L) {
                val json = JSONObject(config.readText())
                for (key in ENGINE_SETTING_KEYS) {
                    if (json.has(key)) shown.add(key + "=" + json.opt(key))
                }
            }
            w.line("Pengaturan mesin: " + if (shown.isEmpty()) "(bawaan mesin)" else shown.joinToString(", "))

            for (folder in listOf("shaderpacks", "resourcepacks")) {
                val items = File(game, folder).listFiles()?.sortedBy { it.name.lowercase() }.orEmpty()
                w.line("Folder " + folder + ": " + if (items.isEmpty()) "kosong atau tidak ada" else "")
                items.take(MAX_LISTED).forEach {
                    w.line("  " + it.name + if (it.isDirectory) " (folder)" else " (" + sizeText(it.length()) + ")")
                }
            }

            val graphics = File(game, "horizon-graphics.txt")
            if (graphics.isFile) {
                w.line("")
                w.line("-- horizon-graphics.txt (pickedShaderPack = shader yang dipilih) --")
                writeHead(w, graphics, MAX_CONFIG_BYTES)
            }

            val log = File(home, "performance/diagnostic.log")
            if (log.isFile && log.length() > 0L) {
                val shaderLines = log.useLines { lines ->
                    lines.filter { it.contains("shader pack", ignoreCase = true) }.toList()
                }
                w.section("Mesin Rust: baris shader (" + shaderLines.size + ")")
                if (shaderLines.isEmpty()) {
                    w.line("(tidak ada baris shader; shader belum dipilih atau belum sempat dimuat)")
                } else {
                    writeLines(w, shaderLines.takeLast(MAX_ENGINE_SHADER_LINES).joinToString("\n"))
                }
                w.section("Mesin Rust: log terakhir")
                writeTail(w, log, MAX_ENGINE_LOG_BYTES)
            } else {
                w.line("Log mesin: belum ada (mesin belum pernah dijalankan di HP ini).")
            }
        } catch (_: Throwable) {
            w.line("(log mesin Rust tidak bisa dibaca)")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Sesi mesin Starling
    // ---------------------------------------------------------------------------------------------

    private fun isEngineSession(props: Properties): Boolean = props.getProperty(KEY_ENGINE) == ENGINE_STARLING

    /** Laporan satu sesi Starling: ringkasan, kejadian launcher, sebab proses berhenti, pengaturan, performa, dan log. */
    private fun writeStarlingReport(context: Context, w: Writer, props: Properties, source: File, start: Long) {
        val clock = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val gameDir = File(props.getProperty(KEY_GAME_DIR).orEmpty())
        val home = StarlingEngine.engineHome(context)

        w.line("===== AmberLauncher: laporan sesi Starling =====")
        w.line("Disimpan     : " + clock.format(Date()))
        w.line("Mulai        : " + clock.format(Date(start)))
        if (source.isFile && source.lastModified() > start) {
            val secs = (source.lastModified() - start) / 1000L
            w.line("Log terakhir : " + clock.format(Date(source.lastModified())) + " (sekitar " + (secs / 60L) + " menit " + (secs % 60L) + " detik)")
        }
        writeEnvironmentLines(context, w)
        w.line("Instance     : " + props.getProperty(KEY_INSTANCE).orEmpty())
        w.line("Minecraft    : " + props.getProperty(KEY_MC).orEmpty() + " | Mesin: Starling (Rust)")
        try {
            val r = RustSettingsStore.readNow(context)
            w.line("Pengaturan Rust Amber: renderer=" + r.renderer + ", skala=" + r.renderScale + "%, mode=" + r.perfMode +
                ", pekerja=" + r.workerLimit + ", kurangi animasi=" + yesNo(r.gameAnimationsOff) + ", daun=" + yesNo(r.leafCulling) +
                ", rekam performa=" + yesNo(r.recordPerformance))
        } catch (_: Throwable) {
        }
        try {
            val compiled = StarlingEngine.compiledNativeMods(context).sorted()
            w.line("Mod native di pustaka mesin: " + if (compiled.isEmpty()) "(tidak ada)" else compiled.joinToString(", "))
        } catch (_: Throwable) {
        }

        w.section("Log launcher (sebelum dan selama sesi ini)")
        val launcherText = LauncherLog.recentText(context, start - LAUNCHER_LOOKBACK_MS, MAX_LAUNCHER_BYTES)
        if (launcherText.isBlank()) w.line("(tidak ada kejadian tercatat)") else writeLines(w, launcherText)

        writeStarlingProcess(context, w, start)
        writeStarlingSessionState(w, home)
        writeStarlingSettings(w, gameDir)
        writeStarlingPerformance(w, home, start)
        writeStarlingDiagnosticLog(w, home)

        // Laporan crash Starling bila ada yang terbit selama sesi ini
        val reports = File(gameDir, "crash-reports")
        val newest = reports.listFiles { f -> f.isFile && f.name.startsWith("crash-") && f.name.endsWith(".txt") }
            ?.maxByOrNull { it.lastModified() }
        if (newest != null && newest.lastModified() >= start - 10_000L) {
            w.section("Laporan crash Starling (" + newest.name + ")")
            writeHead(w, newest, MAX_EXTRA_BYTES)
        }

        w.section("Log mesin (logs/latest.log)")
        if (source.isFile && source.length() > 0L) {
            writeMainLog(w, source)
        } else {
            w.line("(logs/latest.log tidak ada atau kosong: mesin belum sempat menulis log)")
        }

        w.section("Peringatan dan galat sistem (logcat, hanya milik aplikasi ini)")
        if (Looper.myLooper() == Looper.getMainLooper()) {
            w.line("(dilewati: laporan ini disusun di thread utama; buka Pengaturan > Log game untuk menyimpannya ulang)")
        } else {
            val logcat = LauncherLog.logcatWarnings(LOGCAT_LINES)
            if (logcat.isBlank()) w.line("(tidak tersedia di HP ini atau kosong)") else writeLines(w, logcat.trimEnd('\n'))
        }
        w.flush()
    }

    private fun writeEnvironmentLines(context: Context, w: Writer) {
        w.line("Launcher     : " + UpdateChecker.currentVersionName(context) + " (build " +
            UpdateChecker.currentVersionCode(context) + ", " + (if (UpdateChecker.wants32BitApk()) "32-bit" else "64-bit") + ")")
        w.line("Perangkat    : " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE +
            " (API " + Build.VERSION.SDK_INT + "), " + Build.SUPPORTED_ABIS.joinToString("/") + ", RAM " + totalRamMb(context) + " MB")
    }

    /**
     * Bagian Starling pada laporan launcher (di luar sesi): keadaan sesi terakhir, pengaturan, dan log diagnostik.
     * Tidak ditulis bila mesin belum pernah dijalankan.
     */
    private fun writeStarlingEngineSection(context: Context, w: Writer) {
        try {
            val home = StarlingEngine.engineHome(context)
            if (!home.isDirectory) return
            w.section("Mesin Starling")
            w.line("Proses mesin berjalan sekarang: " + yesNo(StarlingEngine.isProcessAlive(context)))
            writeStarlingSessionState(w, home)
            writeStarlingSettings(w, HorizonContent.gameHome(home))
            writeStarlingDiagnosticLog(w, home)
        } catch (_: Throwable) {
            w.line("(data mesin Starling tidak bisa dibaca)")
        }
    }

    /**
     * Sebab terakhir Android menghentikan proses :starling (Android 11 ke atas). LOW_MEMORY berarti HP kehabisan RAM,
     * CRASH_NATIVE adalah crash di kode Rust atau driver GPU, EXIT_SELF keluar biasa dari mesin, dan USER_REQUESTED
     * dihentikan dari pengaturan atau daftar aplikasi.
     */
    private fun writeStarlingProcess(context: Context, w: Writer, start: Long) {
        w.section("Mesin Starling: proses Android")
        w.line("Proses mesin berjalan sekarang: " + yesNo(StarlingEngine.isProcessAlive(context)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val name = context.packageName + StarlingEngine.PROCESS_SUFFIX
                val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 12)
                    .filter { it.processName == name }
                    .take(3)
                if (exits.isEmpty()) {
                    w.line("(Android belum mencatat proses mesin yang berhenti)")
                } else {
                    val clock = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    for (e in exits) {
                        val whose = if (e.timestamp >= start) "sesi ini" else "sebelum sesi ini"
                        w.line(clock.format(Date(e.timestamp)) + " [" + whose + "] " + exitReasonName(e.reason) +
                            ", status=" + e.status + ", pss=" + e.pss + " KB, rss=" + e.rss + " KB, " + e.description.orEmpty())
                    }
                }
            } catch (_: Throwable) {
                w.line("(riwayat proses tidak bisa dibaca)")
            }
        } else {
            w.line("(riwayat penyebab proses berhenti butuh Android 11 ke atas)")
        }
    }

    private fun exitReasonName(reason: Int): String = when (reason) {
        1 -> "EXIT_SELF (mesin keluar sendiri)"
        2 -> "SIGNALED (dimatikan sinyal)"
        3 -> "LOW_MEMORY (HP kehabisan RAM)"
        4 -> "CRASH (Java)"
        5 -> "CRASH_NATIVE (crash kode native)"
        6 -> "ANR (tidak merespons)"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED (dihentikan pengguna)"
        11 -> "USER_STOPPED"
        12 -> "DEPENDENCY_DIED"
        13 -> "OTHER"
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> "UNKNOWN ($reason)"
    }

    /**
     * Keadaan sesi yang dicatat mesin sendiri (diagnostics.rs): fase terakhir, `clean_exit`, dan pemakaian memori. Sesi
     * yang berakhir tanpa `clean_exit` true dimatikan di tengah jalan; `phase` menunjukkan sampai mana ia sempat.
     */
    private fun writeStarlingSessionState(w: Writer, home: File) {
        for ((file, title) in listOf(
            "current-session.json" to "sesi ini atau terakhir",
            "previous-session.json" to "sesi sebelumnya"
        )) {
            val f = File(home, "performance/$file")
            if (!f.isFile || f.length() == 0L) continue
            w.section("Mesin Starling: $file ($title)")
            writeHead(w, f, MAX_SESSION_JSON_BYTES)
        }
    }

    /**
     * Pengaturan yang menentukan beban mesin: berkas horizon-* di folder game dan beberapa kunci options.txt (jarak
     * render, FPS, grafik). Pengaturan Amber yang diteruskan ([com.israadev.nuxlauncher.core.horizon.StarlingSettingsSync])
     * dicatat di amber-applied.json.
     */
    private fun writeStarlingSettings(w: Writer, gameDir: File) {
        w.section("Mesin Starling: pengaturan")
        for (name in listOf("horizon-graphics.txt", "horizon-extras.txt", "horizon-mods.txt", "amber-applied.json")) {
            val f = File(gameDir, name)
            if (!f.isFile || f.length() == 0L) continue
            w.line("-- $name --")
            writeHead(w, f, MAX_CONFIG_BYTES)
        }
        val options = File(gameDir, "options.txt")
        if (options.isFile && options.length() < 256L * 1024L) {
            val kept = options.readLines(Charsets.UTF_8).filter { it.substringBefore(':') in STARLING_OPTION_KEYS }
            w.line("-- options.txt (hanya kunci grafik dan performa) --")
            if (kept.isEmpty()) w.line("(tidak ada kunci yang dikenal)") else kept.forEach { w.line(it) }
        }
    }

    /**
     * Rekaman performa terbaru mesin (performance/horizon-performance-*.jsonl, dinyalakan dengan Rekam performa di
     * Pengaturan > Rust). Hanya ringkasan: jumlah sampel, jumlah kejadian lambat, dan ringkasan frame tiga sampel
     * terakhir; berkas lengkapnya dibagikan dari tombol "Bagikan laporan performa".
     */
    private fun writeStarlingPerformance(w: Writer, home: File, start: Long) {
        w.section("Mesin Starling: rekaman performa")
        try {
            val report = File(home, "performance")
                .listFiles { f -> f.isFile && f.length() > 0L && f.name.startsWith("horizon-performance-") && f.name.endsWith(".jsonl") }
                ?.maxByOrNull { it.name }
            if (report == null) {
                w.line("Belum ada rekaman. Nyalakan Rekam performa di Pengaturan > Rust, mainkan sebentar, lalu simpan ulang log.")
                return
            }
            w.line(report.name + " (" + sizeText(report.length()) + ", " +
                (if (report.lastModified() >= start - 5_000L) "dari sesi ini" else "dari sesi sebelumnya") + ")")
            var samples = 0
            var slowEvents = 0
            val lastFrames = ArrayList<String>()
            report.useLines(Charsets.UTF_8) { lines ->
                for (line in lines) {
                    if (!line.contains("\"type\":\"sample\"")) continue
                    val row = try {
                        JSONObject(line)
                    } catch (_: Exception) {
                        continue
                    }
                    samples++
                    slowEvents += row.optJSONArray("slow_events")?.length() ?: 0
                    row.optJSONObject("frames")?.let {
                        lastFrames.add(it.toString().take(MAX_PERF_FRAMES_CHARS))
                        if (lastFrames.size > 3) lastFrames.removeAt(0)
                    }
                }
            }
            w.line("Sampel per detik: $samples, kejadian lambat tercatat: $slowEvents")
            lastFrames.forEach { w.line("frames: $it") }
        } catch (_: Throwable) {
            w.line("(rekaman performa tidak bisa dibaca)")
        }
    }

    /** Akhir performance/diagnostic.log milik mesin (pengaturan yang dibaca, peringatan, dan kegagalan, sudah disamarkan). */
    private fun writeStarlingDiagnosticLog(w: Writer, home: File) {
        val log = File(home, "performance/diagnostic.log")
        if (!log.isFile || log.length() == 0L) return
        w.section("Mesin Starling: diagnostic.log (akhir)")
        writeTail(w, log, MAX_ENGINE_LOG_BYTES)
    }

    private fun storageText(context: Context): String {
        val allFiles = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) yesNo(Environment.isExternalStorageManager()) else "(tidak berlaku)"
        } catch (_: Throwable) {
            "?"
        }
        val nux = try {
            InstanceManager.getNuxDir(context)
        } catch (_: Throwable) {
            null
        }
        val free = try {
            sizeText(Environment.getExternalStorageDirectory().usableSpace)
        } catch (_: Throwable) {
            "?"
        }
        return "izin semua berkas=" + allFiles + ", folder data=" + (nux?.absolutePath ?: "?") +
            " (ada=" + yesNo(nux?.exists()) + ", bisa ditulis=" + yesNo(nux?.canWrite()) + "), ruang kosong=" + free
    }

    private fun yesNo(value: Boolean?): String = when (value) {
        null -> "?"
        true -> "ya"
        false -> "tidak"
    }

    private fun writeModsAndShaders(w: Writer, gameDir: File) {
        val mods = File(gameDir, "mods").listFiles { f -> f.isFile && f.name.endsWith(".jar") }
            ?.map { it.name }?.sortedBy { it.lowercase() }.orEmpty()
        w.section("Mod terpasang (" + mods.size + ")")
        if (mods.isEmpty()) {
            w.line("(tidak ada)")
        } else {
            mods.take(MAX_LISTED).forEach { w.line(it) }
            if (mods.size > MAX_LISTED) w.line("... dan " + (mods.size - MAX_LISTED) + " lainnya")
        }

        w.section("Shader")
        val packs = File(gameDir, "shaderpacks").listFiles()?.sortedBy { it.name.lowercase() }.orEmpty()
        if (packs.isEmpty()) {
            w.line("Folder shaderpacks: kosong atau tidak ada")
        } else {
            w.line("Folder shaderpacks:")
            packs.take(MAX_LISTED).forEach { w.line("  " + it.name + if (it.isDirectory) " (folder)" else " (" + sizeText(it.length()) + ")") }
        }
        for (rel in listOf("config/iris.properties", "config/oculus.properties", "optionsshaders.txt")) {
            val f = File(gameDir, rel)
            if (f.isFile) {
                w.line("")
                w.line("-- " + rel + " --")
                writeHead(w, f, MAX_CONFIG_BYTES)
            }
        }
    }

    private fun writeMobileGluesFiles(w: Writer, mgDirFromGame: String, start: Long) {
        val dirs = LinkedHashSet<File>()
        if (mgDirFromGame.isNotBlank()) dirs.add(File(mgDirFromGame))
        try {
            dirs.add(File(Environment.getExternalStorageDirectory(), "MobileGlues"))
        } catch (_: Throwable) {
        }
        for (dir in dirs) {
            if (!dir.isDirectory) continue
            val files = dir.listFiles { f -> f.isFile }.orEmpty().sortedBy { it.name }
            val config = files.firstOrNull { it.name.equals("config.json", ignoreCase = true) }
            if (config != null) {
                w.section("Konfigurasi MobileGlues (" + config.absolutePath + ")")
                writeHead(w, config, MAX_CONFIG_BYTES)
            }
            for (f in files) {
                val n = f.name.lowercase()
                val isLog = n.endsWith(".log") || n == "log.txt"
                if (isLog && f.length() > 0L && f.lastModified() >= start - 5_000L) {
                    w.section("Log MobileGlues (" + f.absolutePath + ")")
                    writeTail(w, f, MAX_EXTRA_BYTES)
                }
            }
        }
    }

    private fun writeMainLog(w: Writer, source: File) {
        val len = source.length()
        if (len <= MAX_LOG_BYTES) {
            writeHead(w, source, MAX_LOG_BYTES)
            return
        }
        writeHead(w, source, HEAD_BYTES)
        w.line("")
        w.line("[... bagian tengah log dipotong; berikut akhir log ...]")
        w.line("")
        writeTail(w, source, MAX_LOG_BYTES - HEAD_BYTES)
    }

    /** Menulis awal berkas (maksimal [maxBytes]); baris terakhir yang terpotong dibuang. */
    private fun writeHead(w: Writer, f: File, maxBytes: Long) {
        val len = f.length()
        val take = minOf(len, maxBytes).toInt()
        if (take <= 0) return
        val buf = ByteArray(take)
        FileInputStream(f).use { input ->
            var read = 0
            while (read < take) {
                val n = input.read(buf, read, take - read)
                if (n < 0) break
                read += n
            }
        }
        var text = String(buf, Charsets.UTF_8)
        if (len > take) {
            val lastNl = text.lastIndexOf('\n')
            if (lastNl > 0) text = text.substring(0, lastNl)
        }
        writeLines(w, text)
    }

    /** Menulis akhir berkas (maksimal [maxBytes]); baris pertama yang terpotong dibuang. */
    private fun writeTail(w: Writer, f: File, maxBytes: Long) {
        RandomAccessFile(f, "r").use { raf ->
            val len = raf.length()
            val startAt = maxOf(0L, len - maxBytes)
            val size = (len - startAt).toInt()
            if (size <= 0) return
            raf.seek(startAt)
            val buf = ByteArray(size)
            raf.readFully(buf)
            var text = String(buf, Charsets.UTF_8)
            if (startAt > 0L) {
                val nl = text.indexOf('\n')
                if (nl >= 0) text = text.substring(nl + 1)
            }
            writeLines(w, text)
        }
    }

    private fun writeLines(w: Writer, text: String) {
        for (raw in text.split('\n')) {
            w.write(LogRedactor.redactLine(raw.trimEnd('\r')))
            w.write("\n")
        }
    }

    private fun Writer.line(text: String) {
        write(text)
        write("\n")
    }

    private fun Writer.section(title: String) {
        write("\n===== " + title + " =====\n")
    }

    // ---------------------------------------------------------------------------------------------
    // Pembantu
    // ---------------------------------------------------------------------------------------------

    private fun fileStamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(millis))

    private fun safeName(raw: String?): String {
        val cleaned = raw.orEmpty().replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(24)
        return cleaned.ifBlank { "game" }
    }

    private fun sizeText(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024L -> (bytes / 1024L).toString() + " KB"
        else -> "$bytes B"
    }

    private fun totalRamMb(context: Context): Long = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        info.totalMem / (1024L * 1024L)
    } catch (_: Throwable) {
        0L
    }

    private fun metaFile(context: Context): File = File(context.filesDir, META_NAME)

    private fun readMeta(context: Context): Properties? {
        synchronized(metaLock) {
            val f = metaFile(context)
            if (!f.isFile) return null
            return try {
                val p = Properties()
                FileInputStream(f).use { p.load(it) }
                p
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun writeMeta(context: Context, props: Properties) {
        synchronized(metaLock) {
            try {
                FileOutputStream(metaFile(context)).use { props.store(it, null) }
            } catch (_: Throwable) {
            }
        }
    }
}
