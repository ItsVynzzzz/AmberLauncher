package com.israadev.nuxlauncher.core.record

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.israadev.nuxlauncher.GameActivity
import com.israadev.nuxlauncher.R
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.ui.i18n.Tr

/**
 * Pembungkus foreground service untuk [ScreenRecorder]. Android mewajibkan tangkap layar berjalan
 * di service foreground bertipe mediaProjection. Service ini tidak melakukan pekerjaan berat
 * sendiri: hanya menampilkan notifikasi (dengan tombol Berhenti) dan memanggil [ScreenRecorder].
 * Berjalan di proses `:game` agar berbagi objek dengan GameActivity.
 */
class RecordService : Service() {

    companion object {
        private const val ACTION_START = "com.israadev.nuxlauncher.record.START"
        private const val ACTION_STOP = "com.israadev.nuxlauncher.record.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_W = "w"
        private const val EXTRA_H = "h"
        private const val EXTRA_DPI = "dpi"
        private const val CHANNEL_ID = "amber_record"
        private const val NOTIFICATION_ID = 4107

        /** Mikrofon dipakai hanya bila diminta di Pengaturan DAN izinnya sudah diberikan. */
        fun micAllowed(context: Context): Boolean =
            SettingsManager.settings.value.recordMic &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        /** Mulai merekam memakai hasil dialog izin tangkap layar. */
        fun start(context: Context, resultCode: Int, data: Intent, screenW: Int, screenH: Int, dpi: Int) {
            val intent = Intent(context, RecordService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_W, screenW)
                .putExtra(EXTRA_H, screenH)
                .putExtra(EXTRA_DPI, dpi)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Menutup service (dipanggil setelah rekaman berhenti). Aman dipanggil kapan saja. */
        fun requestStop(context: Context?) {
            if (context == null) return
            try {
                context.stopService(Intent(context, RecordService::class.java))
            } catch (_: Throwable) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (intent != null && action == ACTION_START) {
            handleStart(intent)
        } else if (action == ACTION_STOP) {
            ScreenRecorder.stop()
            stopSelf()
        } else {
            // Dihidupkan ulang sistem tanpa data izin: tidak ada yang bisa dilakukan.
            stopSelf()
        }
        // Izin tangkap layar sekali pakai, jadi service tidak boleh dihidupkan ulang otomatis.
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
        val useMic = micAllowed(this)

        // 1) Wajib: jadi foreground service SEBELUM mengambil MediaProjection (Android 14+).
        try {
            startAsForeground(useMic)
        } catch (_: Throwable) {
            stopSelf()
            return
        }
        if (data == null || resultCode != Activity.RESULT_OK) {
            stopSelf()
            return
        }

        // 2) Siapkan dan jalankan perekam. Bila gagal, ScreenRecorder sudah menampilkan alasannya.
        val ok = ScreenRecorder.start(
            context = this,
            resultCode = resultCode,
            data = data,
            screenW = intent.getIntExtra(EXTRA_W, 1280),
            screenH = intent.getIntExtra(EXTRA_H, 720),
            screenDpi = intent.getIntExtra(EXTRA_DPI, 320),
            settings = SettingsManager.settings.value,
            useMic = useMic
        )
        if (!ok) stopSelf()
    }

    private fun startAsForeground(useMic: Boolean) {
        val notification = buildNotification()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                if (useMic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NOTIFICATION_ID, notification, type)
            }
            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                Tr.t("Rekam layar"),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }

        val flagsImmutable = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openGame = PendingIntent.getActivity(
            this,
            0,
            Intent(this, GameActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flagsImmutable
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordService::class.java).setAction(ACTION_STOP),
            flagsImmutable
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record_notif)
            .setContentTitle(Tr.t("Merekam layar"))
            .setContentText(Tr.t("Ketuk Berhenti untuk menyimpan rekaman."))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setContentIntent(openGame)
            .addAction(R.drawable.ic_record_notif, Tr.t("Berhenti"), stop)
            .build()
    }

    override fun onDestroy() {
        // Jaga-jaga bila sistem mematikan service saat masih merekam: simpan berkasnya.
        if (ScreenRecorder.isRecording) ScreenRecorder.stop()
        super.onDestroy()
    }
}
