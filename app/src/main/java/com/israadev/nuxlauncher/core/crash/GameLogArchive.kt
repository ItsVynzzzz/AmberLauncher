package com.israadev.nuxlauncher.core.crash

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import com.israadev.nuxlauncher.core.account.AccountManager
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

    private val metaLock = Any()

    /** Folder tempat log disimpan (terlihat di pengelola berkas: nuxlauncher/logs). */
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
        gameDir: String
    ) {
        val props = Properties()
        props.setProperty(KEY_START, startTime.toString())
        props.setProperty(KEY_INSTANCE, instance)
        props.setProperty(KEY_MC, mcVersion)
        props.setProperty(KEY_LOADER, loader)
        props.setProperty(KEY_LOADER_VERSION, loaderVersion.orEmpty())
        props.setProperty(KEY_GAME_DIR, gameDir)
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
        return archiveSession(context)
    }

    /** Menyimpan sesi terakhir (menimpa simpanan sebelumnya untuk sesi yang sama). */
    fun archiveSession(context: Context): File? {
        val props = readMeta(context) ?: return null
        val source = File(context.filesDir, LOG_NAME)
        var result: File? = null
        try {
            if (source.exists() && source.length() > 0L) {
                val start = props.getProperty(KEY_START)?.toLongOrNull() ?: source.lastModified()
                val dir = logsDir(context)
                if (!dir.exists()) dir.mkdirs()
                val out = File(dir, ARCHIVE_PREFIX + fileStamp(start) + "_" + safeName(props.getProperty(KEY_INSTANCE)) + ".txt")
                val tmp = File(dir, out.name + ".tmp")
                tmp.bufferedWriter(Charsets.UTF_8).use { w -> writeReport(context, w, props, source, start) }
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

        w.section("Peringatan dan galat sistem (logcat, hanya milik aplikasi ini)")
        val logcat = LauncherLog.logcatWarnings(LOGCAT_LINES)
        if (logcat.isBlank()) w.line("(tidak tersedia di HP ini atau kosong)") else writeLines(w, logcat.trimEnd('\n'))
        w.flush()
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
