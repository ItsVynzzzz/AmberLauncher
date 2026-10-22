package com.israadev.nuxlauncher.core.update

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

/**
 * Sisi Android dari pembaruan: tempat berkas, pemeriksaan APK sebelum dipasang, izin "pasang aplikasi",
 * dan pemasangan lewat PackageInstaller. Hasil pemasangan dikirim ke [UpdateInstallReceiver].
 *
 * Android tidak mengizinkan aplikasi biasa memasang dirinya sendiri tanpa persetujuan: pemasangan
 * pertama selalu meminta satu ketukan konfirmasi dari sistem. Di Android 12 ke atas pembaruan
 * berikutnya bisa berjalan tanpa konfirmasi bila sistem mengizinkan.
 */
object UpdateInstaller {
    const val ACTION_RESULT = "com.israadev.nuxlauncher.UPDATE_INSTALL_RESULT"

    private const val UPDATES_DIR = "updates"
    private const val SESSION_FILE_NAME = "AmberLauncher.apk"

    // ---- Berkas ---------------------------------------------------------------------------

    fun updatesDir(context: Context): File = File(context.cacheDir, UPDATES_DIR)

    /** Berkas tujuan unduhan; nama dari GitHub dibersihkan supaya tidak bisa keluar dari folder. */
    fun apkFile(context: Context, assetName: String): File {
        val safe = assetName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "update.apk" }
        return File(updatesDir(context), safe)
    }

    /**
     * Menghapus sisa unduhan yang tidak berguna lagi: APK versi yang sudah terpasang atau lebih lama,
     * berkas bernama aneh, dan unduhan setengah jadi yang lebih dari 14 hari. Jalankan di thread latar.
     */
    fun cleanup(context: Context, installed: AppVersion) {
        val dir = updatesDir(context)
        val files = dir.listFiles() ?: return
        val pattern = Regex("""^AmberLauncher-(.+?)(?:-32bit)?\.apk(\.part)?$""")
        val now = System.currentTimeMillis()
        for (f in files) {
            if (!f.isFile) continue
            val m = pattern.matchEntire(f.name)
            val version = m?.let { AppVersion.parse(it.groupValues[1]) }
            val stalePart = f.name.endsWith(".part") && now - f.lastModified() > 14L * 24 * 3600 * 1000
            if (version == null || version <= installed || stalePart) {
                try {
                    f.delete()
                } catch (_: Exception) {
                }
            }
        }
    }

    // ---- Keadaan perangkat ----------------------------------------------------------------

    /** Proses game (`:game`) hidup selama sesi game berlangsung; pemasangan akan mematikannya. */
    fun isGameRunning(context: Context): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val name = context.packageName + ":game"
        am.runningAppProcesses?.any { it.processName == name } == true
    } catch (_: Throwable) {
        false
    }

    fun canInstall(context: Context): Boolean = try {
        context.packageManager.canRequestPackageInstalls()
    } catch (_: Throwable) {
        false
    }

    /** Membuka halaman "Izinkan dari sumber ini" milik aplikasi. */
    fun openInstallPermissionSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        if (findActivity(context) == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun findActivity(context: Context): android.app.Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is android.app.Activity) return c
            c = c.baseContext
        }
        return null
    }

    // ---- Pemeriksaan APK ------------------------------------------------------------------

    /**
     * Memastikan berkas ini benar-benar pembaruan AmberLauncher yang bisa dipasang: nama paket sama,
     * versi lebih baru, dan ditandatangani kunci yang sama dengan aplikasi terpasang (kalau tidak,
     * Android menolaknya dengan pesan yang sulit dipahami). Bila tanda tangan tidak bisa dibaca,
     * pemeriksaan itu dilewati dan keputusan diserahkan ke Android. Null = aman dipasang.
     */
    fun verifyApk(context: Context, apk: File): UpdateFailure? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive: PackageInfo = try {
            pm.getPackageArchiveInfo(apk.absolutePath, flags)
        } catch (_: Throwable) {
            null
        } ?: return UpdateFailure(
            FailureKind.CORRUPT_FILE,
            "Berkas pembaruan tidak bisa dibaca sebagai APK. Ketuk Coba lagi untuk mengunduh ulang."
        )

        if (archive.packageName != context.packageName) {
            return UpdateFailure(FailureKind.WRONG_APP, "Berkas ini bukan APK AmberLauncher.")
        }

        val installed: PackageInfo = try {
            pm.getPackageInfo(context.packageName, flags)
        } catch (_: Throwable) {
            return null
        }
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            return UpdateFailure(FailureKind.NOT_NEWER, "APK ini tidak lebih baru dari versi yang terpasang.")
        }

        val newCerts = certificateHashes(archive)
        val oldCerts = certificateHashes(installed)
        if (newCerts.isNotEmpty() && oldCerts.isNotEmpty() && newCerts != oldCerts) {
            return UpdateFailure(
                FailureKind.WRONG_SIGNATURE,
                "Tanda tangan APK berbeda dari aplikasi yang terpasang, jadi Android akan menolak pembaruan ini."
            )
        }
        return null
    }

    private fun certificateHashes(info: PackageInfo): Set<String> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo
            when {
                signing == null -> null
                signing.hasMultipleSigners() -> signing.apkContentsSigners
                else -> signing.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        if (signatures == null) return emptySet()
        return signatures.mapTo(HashSet()) { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    // ---- Pemasangan -----------------------------------------------------------------------

    /**
     * Menyerahkan [apk] ke PackageInstaller. Mengembalikan setelah sesi di-commit; hasilnya
     * (butuh konfirmasi, berhasil, atau gagal) datang ke [UpdateInstallReceiver]. Melempar exception
     * bila sesi tidak bisa dibuat atau berkas tidak bisa ditulis. Jalankan di thread latar.
     */
    fun startInstall(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller

        // Buang sesi sisa percobaan sebelumnya supaya tidak menumpuk.
        try {
            for (s in installer.mySessions) {
                try {
                    installer.abandonSession(s.sessionId)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Dipakai sistem bila memenuhi syarat (Android 12+, pembaruan aplikasi sendiri); bila tidak,
                // sistem tetap meminta konfirmasi lewat STATUS_PENDING_USER_ACTION.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            session.openWrite(SESSION_FILE_NAME, 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out, 256 * 1024) }
                session.fsync(out)
            }
            val callback = Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_RESULT)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            // Sistem mengisi hasil pemasangan ke Intent ini, jadi harus mutable (Android 12+).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pending.intentSender)
        } catch (t: Throwable) {
            try {
                session.abandon()
            } catch (_: Exception) {
            }
            throw t
        } finally {
            session.close()
        }
    }

    /** Menerjemahkan kode hasil PackageInstaller (bukan sukses/butuh konfirmasi/dibatalkan) jadi kalimat. */
    fun failureForStatus(status: Int, detail: String?): UpdateFailure {
        val extra = detail?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return when (status) {
            PackageInstaller.STATUS_FAILURE_BLOCKED ->
                UpdateFailure(FailureKind.INSTALL_BLOCKED, "Pemasangan diblokir oleh sistem atau Play Protect.$extra")
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                UpdateFailure(
                    FailureKind.WRONG_SIGNATURE,
                    "Android menolak pembaruan ini: tanda tangan atau versinya tidak cocok dengan aplikasi yang terpasang.$extra"
                )
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateFailure(FailureKind.INSTALL_FAILED, "APK ini tidak cocok dengan HP kamu.$extra")
            PackageInstaller.STATUS_FAILURE_INVALID ->
                UpdateFailure(FailureKind.CORRUPT_FILE, "APK rusak atau tidak valid. Ketuk Coba lagi untuk mengunduh ulang.$extra")
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                UpdateFailure(FailureKind.STORAGE_FULL, "Ruang penyimpanan tidak cukup untuk memasang pembaruan.$extra")
            else ->
                UpdateFailure(FailureKind.INSTALL_FAILED, "Pemasangan gagal.$extra")
        }
    }
}
