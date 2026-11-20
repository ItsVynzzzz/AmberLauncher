package com.israadev.nuxlauncher.discord

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import com.israadev.nuxlauncher.BuildConfig
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.settings.SettingsManager

/**
 * Discord Rich Presence saat launcher terbuka dan pemain belum bermain ("Getting ready to play"), di proses utama.
 * Opsional lewat Pengaturan > Tampilan > Discord, dan bawaannya mati.
 *
 * Hanya berjalan selama MainActivity di depan. Begitu layarnya ditinggalkan (mis. game dibuka), presence dihapus
 * dan klien SDK dilepas dengan segera, jadi tidak ada pekerjaan latar yang tersisa saat bermain dan tidak
 * bentrok dengan presence game (proses :game atau :horizon yang memakai Application ID yang sama).
 * Pada build tanpa SDK Discord semua fungsi di sini langsung kembali.
 */
object DiscordLauncherPresence {
    private const val TAG = "Discord"

    private var sessionStartMs = 0L

    /** Dipanggil dari MainActivity.onResume. */
    fun onResume(activity: Activity) {
        if (!BuildConfig.HAS_DISCORD_SDK) return
        try {
            if (!SettingsManager.settings.value.discordRpcLauncherEnabled) return
            // Game masih berjalan di latar: presence game yang berlaku, jangan ditimpa.
            if (isGameProcessAlive(activity)) {
                LauncherLog.i(TAG, "Launcher: game masih berjalan, presence launcher dilewati")
                return
            }
            if (sessionStartMs == 0L) sessionStartMs = System.currentTimeMillis()
            DiscordPresence.attach(activity)
            DiscordPresence.update(PresenceInfo("Getting ready to play", "In the launcher", sessionStartMs))
        } catch (e: Throwable) {
            LauncherLog.w(TAG, "Presence launcher gagal: ${e.message}", e)
        }
    }

    /** Dipanggil dari MainActivity.onPause: layar ditinggalkan, presence launcher dihapus dan klien dilepas. */
    fun onPause() {
        if (!BuildConfig.HAS_DISCORD_SDK) return
        try {
            sessionStartMs = 0L
            DiscordPresence.stop()
        } catch (_: Throwable) {
        }
    }

    private fun isGameProcessAlive(context: Context): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.runningAppProcesses?.any { it.processName.endsWith(":game") || it.processName.endsWith(":horizon") } == true
    } catch (_: Throwable) {
        false
    }
}
