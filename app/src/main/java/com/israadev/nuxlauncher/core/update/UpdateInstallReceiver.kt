package com.israadev.nuxlauncher.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Menerima hasil PackageInstaller untuk pembaruan sendiri (lihat [UpdateInstaller.startInstall]).
 * Tidak diekspor: hanya sistem yang memegang PendingIntent-nya.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateInstaller.ACTION_RESULT) return
        val app = context.applicationContext
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Sistem meminta persetujuan pengguna: tampilkan layar konfirmasi pemasangan milik Android.
            val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirm != null) {
                try {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    app.startActivity(confirm)
                } catch (t: Throwable) {
                    UpdateManager.onInstallResult(
                        app,
                        PackageInstaller.STATUS_FAILURE,
                        "layar konfirmasi tidak bisa dibuka"
                    )
                }
            }
            return
        }
        UpdateManager.onInstallResult(app, status, message)
    }
}
