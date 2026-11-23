package com.israadev.nuxlauncher.discord

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import com.israadev.nuxlauncher.BuildConfig
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.ui.i18n.Tr
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pintu ke kode C++ (cpp/discord_bridge.cpp). Hanya dipanggil dari thread kerja milik [DiscordPresence].
 * Semua teks dikirim sebagai byte UTF-8 supaya emoji dan huruf non-Latin aman.
 */
object NativeDiscord {
    external fun nativeInit(appId: Long): Boolean
    external fun nativeUpdate(
        details: ByteArray, state: ByteArray, startMs: Long,
        largeImage: ByteArray, largeText: ByteArray, smallImage: ByteArray, smallText: ByteArray
    ): Boolean
    external fun nativeClear()
    external fun nativeTick()
    external fun nativeShutdown()
    external fun nativeTakeResult(): ByteArray
    external fun nativeTakeLog(): ByteArray
    external fun nativeTakeDropped(): Boolean
}

/** Penghubung [PresenceEngine] ke kode native; library jembatan baru dimuat saat pertama dibutuhkan. */
private object NativeBackend : PresenceBackend {
    private var loaded = false

    override fun init(appId: Long): Boolean {
        if (!loaded) {
            System.loadLibrary("amber_discord")
            loaded = true
        }
        return NativeDiscord.nativeInit(appId)
    }

    override fun update(info: PresenceInfo): Boolean = NativeDiscord.nativeUpdate(
        info.details.toByteArray(Charsets.UTF_8), info.state.toByteArray(Charsets.UTF_8), info.startMs,
        info.largeImage.toByteArray(Charsets.UTF_8), info.largeText.toByteArray(Charsets.UTF_8),
        info.smallImage.toByteArray(Charsets.UTF_8), info.smallText.toByteArray(Charsets.UTF_8)
    )

    override fun clear() = NativeDiscord.nativeClear()
    override fun tick() = NativeDiscord.nativeTick()
    override fun shutdown() = NativeDiscord.nativeShutdown()
    override fun takeResult(): String = String(NativeDiscord.nativeTakeResult(), Charsets.UTF_8)
    override fun takeLog(): String = String(NativeDiscord.nativeTakeLog(), Charsets.UTF_8)
    override fun takeDropped(): Boolean = NativeDiscord.nativeTakeDropped()
}

private class HandlerScheduler(private val handler: Handler) : PresenceScheduler {
    override fun now(): Long = SystemClock.elapsedRealtime()
    override fun postDelayed(task: Runnable, delayMs: Long) {
        handler.postDelayed(task, delayMs)
    }

    override fun cancel(task: Runnable) = handler.removeCallbacks(task)
    override fun cancelAll() = handler.removeCallbacksAndMessages(null)
}

/**
 * Discord Rich Presence lewat Discord Social SDK resmi, TANPA login: presence dikirim ke aplikasi Discord
 * yang terpasang di HP ini. Tidak ada token atau password akun Discord yang dipakai.
 *
 * Logika menjaga presence tetap tampil (mengirim ulang, jeda hemat, pemulihan saat koneksi putus) ada di
 * [PresenceEngine]; di sini hanya bagian Android: SDK opsional, satu thread kerja berprioritas latar belakang,
 * dan penutupan rapi.
 *
 * Hemat untuk HP lemah:
 *  - Tanpa app/libs/discord_partner_sdk.aar saat build, [supported] tetap false dan semua fungsi di sini
 *    tidak melakukan apa-apa.
 *  - Tanpa aplikasi Discord di HP, tidak ada thread dan tidak ada library yang dimuat.
 *  - Setelah [stopBlocking], [stop], atau bila presence berhenti dengan sendirinya, thread kerja ditutup.
 *
 * Semua panggilan ke SDK dikerjakan satu thread (HandlerThread) karena SDK mengharapkan callback-nya
 * dijalankan dari thread yang sama.
 */
object DiscordPresence {
    /** Application ID aplikasi "AmberLauncher" di Discord Developer Portal (bukan rahasia). */
    const val APP_ID = 1571290310213107794L

    private const val TAG = "Discord"
    private const val DISCORD_PACKAGE = "com.discord"

    /** true kalau SDK Discord ikut terpasang di build ini dan aplikasi Discord ada di HP ini. */
    @Volatile
    var supported = false
        private set

    private class Worker(val thread: HandlerThread, val handler: Handler, val engine: PresenceEngine)

    @Volatile
    private var worker: Worker? = null

    /** Game Java yang sedang berjalan; dipakai untuk menyusun ulang presence saat server berubah. */
    private class GameInfo(
        val mcVersion: String,
        val loader: String,
        val instanceName: String,
        val options: DiscordOptions,
        val startMs: Long
    )

    @Volatile
    private var game: GameInfo? = null

    // Hanya disentuh dari thread log game.
    private var serverState: ServerState = ServerState.None

    /**
     * Dipanggil dari activity yang menjalankan game. SDK perlu Activity untuk mengenali aplikasi ini, dan kelas
     * pembukanya (DiscordSocialSdkInit) harus dimuat SEBELUM jembatan kita supaya library SDK dimuat lewat
     * System.loadLibrary dan menjalankan JNI_OnLoad-nya. Dipanggil lewat refleksi supaya kode ini tetap
     * bisa dikompilasi walau SDK tidak ada.
     */
    fun attach(activity: Activity) {
        try {
            if (!isDiscordInstalled(activity)) {
                supported = false
                LauncherLog.i(TAG, "Aplikasi Discord tidak ditemukan di HP ini, presence tidak dipakai")
                return
            }
            val init = Class.forName("com.discord.socialsdk.DiscordSocialSdkInit")
            init.getMethod("setEngineActivity", Activity::class.java).invoke(null, activity)
            supported = true
            LauncherLog.i(TAG, "SDK Discord siap (${activity.javaClass.simpleName})")
        } catch (e: ClassNotFoundException) {
            supported = false
            LauncherLog.i(TAG, "SDK Discord tidak ikut di build ini")
        } catch (e: Throwable) {
            supported = false
            LauncherLog.w(TAG, "SDK Discord tidak bisa disiapkan: ${e.message}", e)
        }
    }

    /**
     * Dipanggil GameActivity (loader Java) saat game dibuka. [options] adalah pilihan pemain (alamat server, nama
     * instance, ikon server); alamat server dibaca dari log game lewat [onGameLogLine].
     */
    fun startGame(activity: Activity, mcVersion: String, loader: String, instanceName: String, options: DiscordOptions) {
        attach(activity)
        val g = GameInfo(mcVersion, loader, instanceName, options, System.currentTimeMillis())
        game = g
        serverState = ServerState.None
        update(buildGame(g, ServerState.None))
    }

    /** Bahasa teks presence: pilihan bahasa di aplikasi, atau bahasa HP bila "Ikuti sistem". */
    internal fun languageNow(): String = PresenceText.resolve(Tr.choice, Locale.getDefault())

    private fun buildGame(g: GameInfo, server: ServerState): PresenceInfo =
        PresenceComposer.game(g.mcVersion, g.loader, g.instanceName, g.options, languageNow(), server, g.startMs)

    /**
     * Dipanggil untuk setiap baris log game (dari thread log). Hampir selalu langsung kembali: tanpa game aktif
     * atau tanpa izin menampilkan server, tidak ada yang dikerjakan.
     */
    fun onGameLogLine(text: String) {
        val g = game ?: return
        val next = when (val event = ServerLog.parse(text)) {
            is ServerEvent.Joined -> if (g.options.showServer) ServerState.Server(event.display) else ServerState.None
            ServerEvent.Singleplayer -> ServerState.Singleplayer
            ServerEvent.Left -> ServerState.None
            null -> return
        }
        if (next == serverState) return
        serverState = next
        LauncherLog.i(TAG, "Keadaan game berubah: ${next.javaClass.simpleName}")
        update(buildGame(g, next))
    }

    /** Menampilkan [info] di profil Discord. null = hapus presence. */
    fun update(info: PresenceInfo?) {
        if (!supported) {
            LauncherLog.i(TAG, "Presence dilewati: SDK atau aplikasi Discord tidak tersedia")
            return
        }
        if (info != null) LauncherLog.i(TAG, "Presence diminta: ${info.details} / ${info.state}")
        val w = ensureWorker()
        w.handler.post { w.engine.update(info) }
    }

    /**
     * Seperti [stopBlocking] tetapi langsung kembali (tanpa menunggu). Dipakai launcher saat layarnya ditinggalkan
     * (mis. game dibuka), supaya peralihan layar tidak tertahan.
     */
    fun stop() {
        game = null
        val w = worker ?: return
        w.handler.post { w.engine.stop() }
    }

    /**
     * Menghapus presence, melepas klien SDK, dan menutup thread kerja. Menunggu sampai [timeoutMs] supaya
     * selesai sebelum proses game dimatikan; bila tidak pernah dipakai langsung kembali.
     */
    fun stopBlocking(timeoutMs: Long = 800L) {
        game = null
        val w = worker ?: return
        val done = CountDownLatch(1)
        val posted = w.handler.post {
            try {
                w.engine.stop()
            } finally {
                done.countDown()
            }
        }
        if (!posted) return
        try {
            done.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Dipanggil HorizonActivity (proses :horizon, mesin Rust). [requested] datang dari Intent yang disusun
     * launcher: true bila pemain menyalakan Discord Rich Presence. Semua alasan melewati presence dicatat
     * di log launcher supaya mudah dilacak bila presence tidak muncul.
     */
    fun onEngineStart(activity: Activity, requested: Boolean, mcVersion: String) {
        if (!BuildConfig.HAS_DISCORD_SDK) return
        try {
            if (!requested) {
                LauncherLog.i(TAG, "Mesin Rust: presence tidak diminta launcher (toggle mati)")
                return
            }
            LauncherLog.i(TAG, "Mesin Rust: menyalakan presence")
            attach(activity)
            update(PresenceComposer.game(mcVersion, "horizon", "", DiscordOptions(), languageNow(), ServerState.None, System.currentTimeMillis()))
        } catch (e: Throwable) {
            LauncherLog.w(TAG, "Mesin Rust: presence gagal dinyalakan: ${e.message}", e)
        }
    }

    private fun isDiscordInstalled(activity: Activity): Boolean = try {
        @Suppress("DEPRECATION")
        activity.packageManager.getPackageInfo(DISCORD_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Throwable) {
        true // tidak bisa dipastikan: biarkan SDK yang mencoba
    }

    @Synchronized
    private fun ensureWorker(): Worker {
        worker?.let { return it }
        val thread = HandlerThread("amber-discord", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        val handler = Handler(thread.looper)
        val engine = PresenceEngine(
            backend = NativeBackend,
            scheduler = HandlerScheduler(handler),
            appId = APP_ID,
            log = { LauncherLog.i(TAG, it) },
            warn = { message, error -> LauncherLog.w(TAG, message, error) },
            onFinished = { finishWorker(thread) }
        )
        val w = Worker(thread, handler, engine)
        worker = w
        return w
    }

    /** Dipanggil dari thread kerja saat engine berhenti total: lepaskan referensi dan tutup thread-nya. */
    private fun finishWorker(thread: HandlerThread) {
        synchronized(this) {
            if (worker?.thread === thread) worker = null
        }
        thread.looper.quitSafely()
    }
}
