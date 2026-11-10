package com.israadev.nuxlauncher.core.horizon

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.crash.LogRedactor
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.i18n.Tr
import java.io.File

/**
 * Laporan performa yang direkam mesin Rust (pengaturan "Rekam performa"). Mesin menulis satu berkas JSON per baris
 * (`performance/horizon-performance-<waktu>.jsonl`, paling besar 8 MiB, maksimal 3 berkas) di folder data mesin.
 * Berkas itu tidak berisi akun, chat, nama dunia, atau posisi pemain.
 *
 * Di sini laporan terbaru hanya dicari dan dibagikan; launcher tidak pernah mengubah atau menghapus laporan mesin.
 * Salinan untuk dibagikan dibuat di latar (bukan di utas UI) ke folder cache yang sama dengan log game.
 */
object RustPerformanceReport {
    private const val TAG = "RustPerformance"
    private const val FOLDER = "performance"
    private const val PREFIX = "horizon-performance-"
    private const val EXTENSION = ".jsonl"
    private const val SHARE_FOLDER = "shared_logs"

    private const val MSG_NONE = "Belum ada laporan performa. Nyalakan Rekam performa, mainkan sebentar, lalu coba lagi."
    private const val MSG_NO_APP = "Tidak ada aplikasi untuk membagikan file ini."
    private const val MSG_FAILED = "Gagal membagikan file: "
    private const val CHOOSER_TITLE = "Bagikan laporan performa"
    private const val SUBJECT = "Laporan performa AmberLauncher - "

    /**
     * Laporan terbaru di [dir], atau null. Urutannya sama dengan mesin: nama berkas memuat waktu pembuatan, jadi
     * nama terbesar adalah yang terbaru. Berkas kosong dilewati.
     */
    internal fun latestIn(dir: File): File? =
        dir.listFiles { f -> f.isFile && f.length() > 0L && f.name.startsWith(PREFIX) && f.name.endsWith(EXTENSION) }
            ?.maxByOrNull { it.name }

    /** Laporan terbaru milik mesin, atau null bila belum pernah merekam. */
    fun latest(context: Context): File? = latestIn(File(HorizonEngine.engineHome(context), FOLDER))

    /** Membuka pilihan aplikasi untuk membagikan laporan terbaru. Aman dipanggil dari ketukan tombol. */
    fun share(context: Context) {
        val app = context.applicationContext
        val worker = Thread({
            var copy: File? = null
            var error: String? = null
            try {
                copy = prepareCopy(app)
            } catch (e: Exception) {
                LauncherLog.w(TAG, "Laporan performa gagal disiapkan: ${e.message}", e)
                error = e.message ?: e.javaClass.simpleName
            }
            Handler(Looper.getMainLooper()).post { deliver(app, copy, error) }
        }, "rust-report-share")
        worker.isDaemon = true
        worker.start()
    }

    /** Menyalin laporan terbaru ke folder berbagi (token disamarkan seperti log lain). Null bila tidak ada laporan. */
    private fun prepareCopy(context: Context): File? {
        val report = latest(context) ?: return null
        val shareDir = File(context.cacheDir, SHARE_FOLDER).apply { mkdirs() }
        // Salinan laporan lama dibuang supaya cache tidak menumpuk (satu laporan bisa sampai 8 MiB).
        shareDir.listFiles { f -> f.name.startsWith(PREFIX) }?.forEach { it.delete() }
        val copy = File(shareDir, report.name)
        report.useLines(Charsets.UTF_8) { lines ->
            copy.bufferedWriter(Charsets.UTF_8).use { writer ->
                for (line in lines) {
                    writer.write(LogRedactor.redactLine(line))
                    writer.newLine()
                }
            }
        }
        return copy
    }

    /** Dijalankan di utas UI: menampilkan pesan atau pilihan aplikasi. */
    private fun deliver(context: Context, copy: File?, error: String?) {
        if (copy == null) {
            val message = if (error != null) MSG_FAILED + error else MSG_NONE
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", copy)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, SUBJECT + copy.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(send, Tr.t(CHOOSER_TITLE)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, MSG_NO_APP, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, MSG_FAILED + (e.message ?: e.javaClass.simpleName), Toast.LENGTH_SHORT).show()
        }
    }
}
