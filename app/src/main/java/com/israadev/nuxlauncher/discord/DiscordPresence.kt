package com.israadev.nuxlauncher.discord

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.israadev.nuxlauncher.core.crash.LauncherLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pintu ke kode C++ (cpp/discord_bridge.cpp). Hanya dipanggil dari thread kerja milik [DiscordPresence].
 * Semua teks dikirim sebagai byte UTF-8 supaya emoji dan huruf non-Latin aman.
 */
object NativeDiscord {
    external fun nativeInit(appId: Long): Boolean
    external fun nativeUpdate(details: ByteArray, state: ByteArray, startMs: Long): Boolean
    external fun nativeClear()
    external fun nativeTick()
    external fun nativeShutdown()
    external fun nativeTakeResult(): ByteArray
    external fun nativeTakeLog(): ByteArray
}

/** Isi Rich Presence yang ditampilkan di profil Discord. [startMs] <= 0 berarti tanpa penghitung waktu. */
data class PresenceInfo(val details: String, val state: String, val startMs: Long)

/**
 * Discord Rich Presence lewat Discord Social SDK resmi, TANPA login: presence dikirim ke aplikasi Discord
 * yang terpasang di HP ini. Tidak ada token atau password akun Discord yang dipakai.
 *
 * Dirancang ringan untuk HP lemah:
 *  - SDK-nya opsional. Tanpa app/libs/discord_partner_sdk.aar saat build, [supported] tetap false dan semua
 *    fungsi di sini tidak melakukan apa-apa.
 *  - Tanpa aplikasi Discord di HP, tidak ada thread dan tidak ada library yang dimuat.
 *  - Satu thread kerja berprioritas latar belakang. Waktu bermain dihitung oleh Discord sendiri dari
 *    [PresenceInfo.startMs], jadi tidak ada pembaruan berkala.
 *  - Callback SDK dijalankan tiap [TICK_FAST_MS] hanya sampai presence pertama berhasil, sesudahnya cukup
 *    tiap [TICK_SLOW_MS]. Bila presence tak kunjung berhasil dalam [GIVE_UP_MS] (mis. Discord belum login),
 *    semuanya dihentikan dan thread ditutup.
 *  - Sesudah [stopBlocking] atau menyerah, thread kerja ditutup dan klien SDK dilepas.
 *
 * Semua panggilan ke SDK dikerjakan satu thread (HandlerThread) karena SDK mengharapkan callback-nya
 * dijalankan dari thread yang sama.
 */
object DiscordPresence {
    /** Application ID aplikasi "AmberLauncher" di Discord Developer Portal (bukan rahasia). */
    const val APP_ID = 1571290310213107794L

    private const val TAG = "Discord"
    private const val DISCORD_PACKAGE = "com.discord"
    private const val TICK_FAST_MS = 500L
    private const val TICK_SLOW_MS = 5_000L
    private const val GIVE_UP_MS = 60_000L

    /** Discord membatasi seberapa sering presence boleh diganti; pembaruan yang lebih rapat ditunda. */
    private const val MIN_GAP_MS = 15_000L

    /** true kalau SDK Discord ikut terpasang di build ini dan aplikasi Discord ada di HP ini. */
    @Volatile
    var supported = false
        private set

    private var libLoaded = false

    @Volatile
    private var handler: Handler? = null

    // --- keadaan di bawah ini hanya disentuh dari thread kerja
    private var initialized = false
    private var wanted: PresenceInfo? = null
    private var dirty = false
    private var lastSentAt = 0L
    private var firstSentAt = 0L
    private var settled = false
    private var lastResult = ""

    private val tickTask = Runnable { tick() }
    private val flushTask = Runnable { flush() }

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
                return
            }
            val init = Class.forName("com.discord.socialsdk.DiscordSocialSdkInit")
            init.getMethod("setEngineActivity", Activity::class.java).invoke(null, activity)
            supported = true
        } catch (e: ClassNotFoundException) {
            supported = false // build tanpa SDK Discord: wajar, tidak perlu dicatat
        } catch (e: Throwable) {
            supported = false
            LauncherLog.w(TAG, "SDK Discord tidak bisa disiapkan: ${e.message}", e)
        }
    }

    /** Menampilkan [info] di profil Discord. null = hapus presence. */
    fun update(info: PresenceInfo?) {
        if (!supported) return
        ensureHandler().post {
            wanted = info
            dirty = true
            flush()
        }
    }

    /**
     * Menghapus presence, melepas klien SDK, dan menutup thread kerja. Menunggu sampai [timeoutMs] supaya
     * selesai sebelum proses game dimatikan; bila tidak pernah dipakai langsung kembali.
     */
    fun stopBlocking(timeoutMs: Long = 800L) {
        val h = handler ?: return
        val done = CountDownLatch(1)
        val posted = h.post {
            try {
                shutdown()
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

    /** Teks presence untuk game yang sedang berjalan. Nama instance dan akun pemain sengaja tidak ikut. */
    fun gamePresence(mcVersion: String, loader: String, startMs: Long): PresenceInfo {
        val version = mcVersion.trim().takeIf { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) }
        val details = if (version != null) "Minecraft $version" else "Minecraft"
        val state = when (loader.trim().lowercase()) {
            "", "vanilla" -> "Vanilla"
            "fabric" -> "Fabric"
            "forge" -> "Forge"
            "neoforge", "neo_forge", "neo-forge" -> "NeoForge"
            "quilt" -> "Quilt"
            else -> loader.trim().replaceFirstChar { it.uppercase() }
        }
        return PresenceInfo(details, state, startMs)
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
    private fun ensureHandler(): Handler {
        handler?.let { return it }
        val thread = HandlerThread("amber-discord", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        val h = Handler(thread.looper)
        handler = h
        return h
    }

    // ------------------------------------------------------------ di thread kerja

    private fun flush() {
        val h = handler ?: return
        if (!dirty) return
        val info = wanted
        val now = SystemClock.elapsedRealtime()
        if (info != null && lastSentAt != 0L && now - lastSentAt < MIN_GAP_MS) {
            h.removeCallbacks(flushTask)
            h.postDelayed(flushTask, MIN_GAP_MS - (now - lastSentAt))
            return
        }
        dirty = false
        try {
            if (info == null) {
                if (initialized) NativeDiscord.nativeClear()
            } else {
                if (!libLoaded) {
                    System.loadLibrary("amber_discord")
                    libLoaded = true
                }
                if (!initialized) {
                    initialized = NativeDiscord.nativeInit(APP_ID)
                    if (!initialized) {
                        drain()
                        giveUp("klien SDK gagal dibuat")
                        return
                    }
                    firstSentAt = now
                    h.removeCallbacks(tickTask)
                    h.postDelayed(tickTask, TICK_FAST_MS)
                }
                NativeDiscord.nativeUpdate(
                    fit(info.details).toByteArray(Charsets.UTF_8),
                    fit(info.state).toByteArray(Charsets.UTF_8),
                    info.startMs
                )
            }
            lastSentAt = now
        } catch (e: Throwable) {
            LauncherLog.w(TAG, "Discord RPC dihentikan: ${e.message}", e)
            giveUp("galat: ${e.javaClass.simpleName}")
        }
    }

    private fun tick() {
        val h = handler ?: return
        if (!initialized) return
        try {
            NativeDiscord.nativeTick()
            drain()
            if (lastResult == "ok") settled = true
            if (!settled && SystemClock.elapsedRealtime() - firstSentAt > GIVE_UP_MS) {
                giveUp("presence tidak berhasil dalam ${GIVE_UP_MS / 1000} detik (Discord belum login?)")
                return
            }
        } catch (e: Throwable) {
            LauncherLog.w(TAG, "Discord RPC dihentikan: ${e.message}", e)
            giveUp("galat: ${e.javaClass.simpleName}")
            return
        }
        h.postDelayed(tickTask, if (settled) TICK_SLOW_MS else TICK_FAST_MS)
    }

    private fun giveUp(reason: String) {
        LauncherLog.i(TAG, "Discord RPC berhenti: $reason")
        shutdown()
    }

    private fun shutdown() {
        handler?.removeCallbacksAndMessages(null)
        try {
            if (initialized) {
                NativeDiscord.nativeShutdown()
                drain()
            }
        } catch (e: Throwable) {
            LauncherLog.w(TAG, "Discord RPC gagal ditutup rapi: ${e.message}", e)
        }
        initialized = false
        wanted = null
        dirty = false
        lastSentAt = 0L
        firstSentAt = 0L
        settled = false
        lastResult = ""
        synchronized(this) { handler = null }
        Looper.myLooper()?.quitSafely()
    }

    /** Memindahkan hasil terakhir dan log SDK ke log launcher (hanya yang baru atau berubah). */
    private fun drain() {
        val result = String(NativeDiscord.nativeTakeResult(), Charsets.UTF_8)
        if (result.isNotEmpty() && result != lastResult) {
            lastResult = result
            LauncherLog.i(TAG, "Presence: $result")
        }
        val log = String(NativeDiscord.nativeTakeLog(), Charsets.UTF_8)
        if (log.isNotBlank()) {
            for (line in log.lineSequence()) if (line.isNotBlank()) LauncherLog.w(TAG, line)
        }
    }

    /** Discord menerima teks 2 sampai 128 karakter. */
    private fun fit(text: String): String {
        var t = text.replace('\n', ' ').trim()
        if (t.length > 100) t = t.take(100)
        if (t.isNotEmpty() && Character.isHighSurrogate(t.last())) t = t.dropLast(1)
        return if (t.length < 2) "Minecraft" else t
    }
}
