package com.israadev.nuxlauncher.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.israadev.nuxlauncher.ui.i18n.Tr

/**
 * Menjaga proses launcher tetap hidup selama Minecraft diunduh. Tanpa ini, di HP RAM kecil Android cepat
 * mematikan launcher begitu pengguna pindah aplikasi atau layar mati, dan unduhan berhenti di tengah jalan.
 *
 * Service ini tidak mengunduh apa pun: unduhan tetap berjalan di [MinecraftDownloader]. Service hanya
 * memberi prioritas proses (notifikasi latar depan) dan menahan CPU tetap menyala. Dimulai dan dihentikan
 * lewat [start] dan [stop]; kegagalan memulainya tidak mengganggu unduhan.
 */
class DownloadKeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "amber_download"
        private const val NOTIFICATION_ID = 4109

        /** Batas aman wake lock bila [stop] tidak pernah terpanggil (mis. galat tak terduga). */
        private const val WAKE_LOCK_TIMEOUT_MS = 2L * 60L * 60L * 1000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DownloadKeepAliveService::class.java))
            } catch (_: Throwable) {
                // Sistem menolak service latar depan (mis. aplikasi sedang di latar belakang): unduhan jalan seperti biasa.
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DownloadKeepAliveService::class.java))
            } catch (_: Throwable) {
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        acquireWakeLock()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startAsForeground() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    Tr.t("Unduhan game"),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
                nm.createNotificationChannel(channel)
            }
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            val contentIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(Tr.t("Mengunduh Minecraft"))
                .setContentText(Tr.t("Unduhan lanjut di latar belakang. Jangan tutup paksa aplikasi."))
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Throwable) {
            // Tetap jalan sebagai service biasa; unduhan hanya lebih rentan dihentikan sistem.
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AmberLauncher:download").apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        } catch (_: Throwable) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) {
        }
        wakeLock = null
    }
}
