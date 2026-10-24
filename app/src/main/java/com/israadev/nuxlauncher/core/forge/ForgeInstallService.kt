package com.israadev.nuxlauncher.core.forge

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.system.Os
import androidx.core.app.NotificationCompat
import com.israadev.nuxlauncher.core.runtime.JavaRuntimeManager
import com.israadev.nuxlauncher.ui.i18n.Tr
import com.movtery.zalithlauncher.bridge.LoggerBridge
import com.movtery.zalithlauncher.bridge.ZLBridge
import com.movtery.zalithlauncher.bridge.ZLNativeInvoker
import com.oracle.dalvik.VMLauncher
import java.io.File
import java.io.Writer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Menjalankan installer resmi Forge/NeoForge (misalnya `SimpleInstaller --installClient <folder> --offline`)
 * di JVM bawaan Amber.
 *
 * Berjalan di proses `:forge` yang terpisah dari launcher dan dari game, karena sebuah proses hanya
 * bisa membuat satu JVM dan JVM yang selesai mematikan prosesnya. Proses ini hidup hanya selama
 * pemasangan: setelah selesai (atau dibatalkan) service berhenti dan prosesnya dimatikan, sehingga
 * tidak ada RAM maupun CPU yang tersisa. Service foreground supaya pemasangan tidak dihentikan
 * sistem bila pengguna berpindah aplikasi.
 *
 * Hubungan dengan proses utama lewat berkas di [workDir]: [LOG_NAME] (log installer) dan
 * [RESULT_NAME] (kode keluar, ditulis paling akhir).
 */
class ForgeInstallService : Service() {

    companion object {
        const val EXTRA_INSTALLER = "forge_installer"
        const val EXTRA_MAIN_CLASS = "forge_main_class"
        const val EXTRA_ARGS = "forge_args"
        const val EXTRA_LABEL = "forge_label"
        const val EXTRA_RUNTIME_HOME = "forge_runtime_home"

        const val LOG_NAME = "installer.log"
        const val RESULT_NAME = "result.txt"

        private const val CHANNEL_ID = "amber_forge_install"
        private const val NOTIFICATION_ID = 4108

        fun workDir(context: Context): File = File(context.filesDir, "forge-install")
    }

    private val started = AtomicBoolean(false)
    private val resultWritten = AtomicBoolean(false)
    private val logLock = Any()

    @Volatile
    private var logWriter: Writer? = null

    /** Nama loader ("Forge"/"NeoForge") untuk notifikasi dan awalan baris log. */
    @Volatile
    private var label = "Forge"

    /**
     * Terikat dari proses utama. Ikatan inilah yang menjamin installer jalan walau startForegroundService
     * ditolak (Android 12+ melarang memulainya saat aplikasi di latar belakang, misalnya pengguna
     * berpindah aplikasi selagi game diunduh), karena bindService tidak dibatasi seperti itu.
     */
    override fun onBind(intent: Intent?): IBinder {
        begin(intent)
        return Binder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_LABEL)?.takeIf { it.isNotBlank() }?.let { label = it }
        startAsForeground()
        if (!begin(intent)) stopSelf()
        return START_NOT_STICKY
    }

    /** Memulai thread installer sekali saja. False bila [intent] tidak membawa parameter yang lengkap. */
    private fun begin(intent: Intent?): Boolean {
        val installer = intent?.getStringExtra(EXTRA_INSTALLER)
        val mainClass = intent?.getStringExtra(EXTRA_MAIN_CLASS)
        val installerArgs = intent?.getStringArrayExtra(EXTRA_ARGS)
        val runtimeHome = intent?.getStringExtra(EXTRA_RUNTIME_HOME)
        if (installer == null || mainClass == null || installerArgs == null || runtimeHome == null) return false
        intent?.getStringExtra(EXTRA_LABEL)?.takeIf { it.isNotBlank() }?.let { label = it }
        if (started.compareAndSet(false, true)) {
            thread(name = "Amber-Forge-Installer") {
                runInstaller(File(installer), mainClass, installerArgs.toList(), File(runtimeHome))
            }
        }
        return true
    }

    private fun startAsForeground() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    Tr.t("Pemasangan Forge"),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
                nm.createNotificationChannel(channel)
            }
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(Tr.t("Memasang $label"))
                .setContentText(Tr.t("Proses ini hanya berjalan sekali per versi $label."))
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Throwable) {
            // Tetap lanjut sebagai service biasa; pemasangan hanya lebih rentan dihentikan sistem
        }
    }

    // ---------------------------------------------------------------- JVM

    private fun runInstaller(installer: File, mainClass: String, installerArgs: List<String>, runtimeHome: File) {
        val work = workDir(this)
        work.mkdirs()
        try {
            logWriter = File(work, LOG_NAME).bufferedWriter()
        } catch (_: Throwable) {
        }
        try {
            ZLNativeInvoker.onExitCallback = { exitCode, _ ->
                log("[$label] JVM keluar dengan kode $exitCode")
                finish(exitCode)
            }
            LoggerBridge.setListener { text -> if (text != null) log(text) }

            // Logger native membuka berkasnya tanpa O_CREAT (sama seperti GameActivity.setupLogger),
            // jadi berkasnya harus sudah ada; kalau tidak, start() gagal dengan
            // "No such file or directory" dan stdout/stderr sudah telanjur dialihkan ke pipa tanpa pembaca.
            val nativeOut = File(work, "native.out")
            nativeOut.delete()
            nativeOut.createNewFile()
            if (!nativeOut.exists()) throw java.io.IOException("Tidak bisa membuat ${nativeOut.absolutePath}")
            LoggerBridge.start(nativeOut.absolutePath)

            val args = prepareJvm(installer, mainClass, installerArgs, runtimeHome, work)
            log("[$label] Menjalankan installer: ${args.joinToString(" ")}")
            val code = VMLauncher.launchJVM(args.toTypedArray())
            finish(code)
        } catch (t: Throwable) {
            // Nama kelas ikut ditulis supaya penyebabnya terbaca dari pesan galat di layar
            log("[$label] ERROR: ${t.javaClass.simpleName}: ${t.message ?: ""}".trimEnd(' ', ':'))
            t.stackTrace.take(8).forEach { log("    at $it") }
            finish(-1)
        } finally {
            stopSelf()
        }
    }

    /** Menyiapkan lingkungan JVM (jalur pustaka, pre-dlopen runtime) dan mengembalikan argumen peluncuran. */
    private fun prepareJvm(
        installer: File,
        mainClass: String,
        installerArgs: List<String>,
        runtimeHome: File,
        work: File
    ): List<String> {
        val nativeLibDir = applicationInfo.nativeLibraryDir
        val rh = runtimeHome.absolutePath
        val libDirName = if (android.os.Process.is64Bit()) "lib64" else "lib"

        // Jalur linker untuk memuat pustaka JRE (sama dengan jalur proses game, tanpa renderer/LWJGL)
        val archSubDir = listOf("aarch64", "aarch32", "arm", "i386", "amd64", "x86_64")
            .map { File("$rh/lib", it) }
            .firstOrNull { it.isDirectory }
        val runtimeLd = mutableListOf<String>()
        if (archSubDir != null) {
            runtimeLd.add("${archSubDir.absolutePath}/jli")
            runtimeLd.add("${archSubDir.absolutePath}/server")
            runtimeLd.add("${archSubDir.absolutePath}/client")
            runtimeLd.add(archSubDir.absolutePath)
        }
        runtimeLd.add("$rh/lib/jli")
        if (File("$rh/jre").exists()) {
            runtimeLd.add("$rh/jre/lib/server")
            runtimeLd.add("$rh/jre/lib")
        } else {
            runtimeLd.add("$rh/lib/server")
            runtimeLd.add("$rh/lib")
        }
        runtimeLd.add(nativeLibDir)
        runtimeLd.add("/system/$libDirName")
        runtimeLd.add("/vendor/$libDirName")
        runtimeLd.add("/vendor/$libDirName/hw")
        runtimeLd.add("/system_ext/$libDirName")
        ZLBridge.setLdLibraryPath(runtimeLd.distinct().joinToString(":"))

        // LD_LIBRARY_PATH proses tidak boleh memuat folder JRE lib/server atau lib/jli
        // (libjli akan mengira perlu menjalankan ulang bin/java, yang ditolak Android)
        val processLd = listOf(
            "/system/$libDirName", "/vendor/$libDirName", "/vendor/$libDirName/hw",
            "/system_ext/$libDirName", nativeLibDir
        ).joinToString(":")

        Os.setenv("POJAV_NATIVEDIR", nativeLibDir, true)
        Os.setenv("POJAV_RENDERER", "opengles3", true)
        Os.setenv("JAVA_HOME", rh, true)
        Os.setenv("HOME", work.absolutePath, true)
        Os.setenv("TMPDIR", cacheDir.absolutePath, true)
        Os.setenv("PATH", "$rh/bin:" + (Os.getenv("PATH") ?: "/system/bin"), true)
        Os.setenv("LD_LIBRARY_PATH", processLd, true)

        preDlopenRuntime(runtimeHome)

        ZLBridge.setupExitMethod(this)
        ZLBridge.initializeGameExitHook()
        ZLBridge.chdir(work.absolutePath)

        JavaRuntimeManager.ensureExecutablePermissionsOnce(runtimeHome)

        val totalMb = try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            (info.totalMem / (1024 * 1024)).toInt()
        } catch (_: Throwable) {
            4096
        }
        // Cukup untuk processor Forge/NeoForge, tetapi tidak merebut RAM HP kecil
        var heapMb = (totalMb / 4).coerceIn(384, 768)
        if (!android.os.Process.is64Bit()) heapMb = minOf(heapMb, 640)

        val jvmArgs = listOf(
            "$rh/bin/java",
            "-Djava.home=$rh",
            "-Djava.io.tmpdir=${cacheDir.absolutePath}",
            "-Djava.library.path=$processLd",
            "-Djava.awt.headless=true",
            "-Dos.name=Linux",
            "-Dos.version=Android-${Build.VERSION.RELEASE}",
            "-Duser.home=${work.absolutePath}",
            "-Dfile.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8",
            "-Dsun.stderr.encoding=UTF-8",
            "-Djava.net.preferIPv4Stack=true",
            "-Djdk.lang.Process.launchMechanism=FORK",
            "-XX:+IgnoreUnrecognizedVMOptions",
            "-XX:+UseSerialGC",
            "-Xms64M",
            "-Xmx${heapMb}M",
            "-cp", installer.absolutePath,
            mainClass
        )
        return jvmArgs + installerArgs
    }

    /** Memuat pustaka inti JRE lebih dulu dengan path absolut, sama seperti proses game. */
    private fun preDlopenRuntime(runtimeHome: File) {
        val rtLibDir = if (File(runtimeHome, "jre/lib").exists()) File(runtimeHome, "jre/lib") else File(runtimeHome, "lib")
        val archLibDir = listOf("aarch64", "aarch32", "arm", "i386", "amd64", "x86_64")
            .map { File(rtLibDir, it) }
            .firstOrNull { it.isDirectory } ?: rtLibDir
        val jliDir = when {
            File(rtLibDir, "jli/libjli.so").exists() -> File(rtLibDir, "jli")
            File(archLibDir, "jli/libjli.so").exists() -> File(archLibDir, "jli")
            else -> rtLibDir
        }
        val jvmDir = when {
            File(rtLibDir, "server/libjvm.so").exists() -> File(rtLibDir, "server")
            File(rtLibDir, "client/libjvm.so").exists() -> File(rtLibDir, "client")
            File(archLibDir, "server/libjvm.so").exists() -> File(archLibDir, "server")
            File(archLibDir, "client/libjvm.so").exists() -> File(archLibDir, "client")
            else -> rtLibDir
        }
        val essential = listOf(
            File(jliDir, "libjli.so"),
            File(jvmDir, "libjvm.so"),
            File(rtLibDir, "libverify.so"), File(archLibDir, "libverify.so"),
            File(rtLibDir, "libjava.so"), File(archLibDir, "libjava.so"),
            File(rtLibDir, "libnet.so"), File(archLibDir, "libnet.so"),
            File(rtLibDir, "libnio.so"), File(archLibDir, "libnio.so")
        )
        essential.forEach { so ->
            if (so.exists()) {
                try {
                    ZLBridge.dlopen(so.absolutePath)
                } catch (_: Throwable) {
                }
            }
        }
        // Sisa pustaka runtime (zip, management, dll.) dimuat juga, persis seperti proses game
        runtimeHome.walkTopDown().filter { it.isFile && it.name.endsWith(".so") }.forEach { so ->
            try {
                ZLBridge.dlopen(so.absolutePath)
            } catch (_: Throwable) {
            }
        }
    }

    // ---------------------------------------------------------------- berkas komunikasi

    private fun log(text: String) {
        synchronized(logLock) {
            try {
                val w = logWriter ?: return
                w.write(text.trimEnd('\n', '\r'))
                w.write("\n")
                w.flush()
            } catch (_: Throwable) {
            }
        }
    }

    /** Menulis kode keluar sekali saja; berkas ini yang ditunggu proses utama. */
    private fun finish(exitCode: Int) {
        if (!resultWritten.compareAndSet(false, true)) return
        try {
            val work = workDir(this)
            val tmp = File(work, "$RESULT_NAME.tmp")
            tmp.writeText(exitCode.toString())
            tmp.renameTo(File(work, RESULT_NAME))
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        synchronized(logLock) {
            try {
                logWriter?.close()
            } catch (_: Throwable) {
            }
            logWriter = null
        }
        // JVM tidak bisa dibuat ulang di proses yang sama dan tidak bisa dihentikan dari luar:
        // mematikan proses :forge adalah satu-satunya cara bersih (juga saat pemasangan dibatalkan).
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
