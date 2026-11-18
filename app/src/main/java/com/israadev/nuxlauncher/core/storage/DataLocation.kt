package com.israadev.nuxlauncher.core.storage

import android.content.Context
import android.os.Environment
import com.israadev.nuxlauncher.core.crash.LauncherLog
import java.io.File

/**
 * Folder data launcher (instance, versi, library, aset, log): satu-satunya tempat yang menentukannya.
 * Aturan pemilihannya ada di [DataLocationLogic]; di sini hanya folder kandidat dari Android, penunjuk
 * di penyimpanan internal, dan penyimpanan hasil di memori.
 *
 * Folder baru: Android/data/<id aplikasi>/files/amberlauncher (tanpa izin apa pun, dihapus Android bersama
 * aplikasi). Pengguna lama tetap di folder nuxlauncher sampai memindahkannya lewat [StorageMigrationRunner].
 *
 * Hasilnya disimpan per proses (proses utama, :game, :forge membacanya sendiri dari berkas penunjuk yang sama).
 */
object DataLocation {

    private const val POINTER_FILE = "data_location.txt"
    private const val CLEANUP_FILE = "data_cleanup.txt"

    const val MODE_NEW = DataLocationLogic.MODE_NEW
    const val MODE_LEGACY = DataLocationLogic.MODE_LEGACY

    /** Dipegang [com.israadev.nuxlauncher.core.instance.InstanceManager.init] selama membaca; pemindahan menunggunya. */
    val lock = Any()

    /** True selama pemindahan berjalan: pemuatan ulang otomatis daftar instance dilewati. */
    @Volatile
    var migrating: Boolean = false

    @Volatile
    private var cached: DataLocationLogic.Decision? = null

    private fun candidates(context: Context): DataLocationLogic.Candidates {
        val ext = try {
            context.getExternalFilesDir(null)
        } catch (_: Throwable) {
            null
        }
        val pub = try {
            File(Environment.getExternalStorageDirectory(), DataLocationLogic.LEGACY_DIR_NAME)
        } catch (_: Throwable) {
            null
        }
        return DataLocationLogic.Candidates(
            newDir = File(ext ?: context.filesDir, DataLocationLogic.NEW_DIR_NAME),
            legacyPublic = pub,
            legacyAppExternal = ext?.let { File(it, DataLocationLogic.LEGACY_DIR_NAME) },
            legacyInternal = File(context.filesDir, DataLocationLogic.LEGACY_DIR_NAME)
        )
    }

    private fun pointerFile(context: Context) = File(context.filesDir, POINTER_FILE)

    private fun decision(context: Context): DataLocationLogic.Decision {
        val fast = cached
        if (fast != null) return fast
        return synchronized(this) {
            val again = cached
            if (again != null) {
                again
            } else {
                val app = context.applicationContext ?: context
                val d = DataLocationLogic.decide(
                    DataLocationLogic.readPointer(pointerFile(app)),
                    candidates(app)
                )
                if (d.mode == MODE_NEW) d.root.mkdirs()
                if (d.persist && !DataLocationLogic.writePointer(pointerFile(app), d.mode)) {
                    LauncherLog.w("DataLocation", "Penunjuk lokasi data gagal ditulis")
                }
                cached = d
                d
            }
        }
    }

    /** Folder data yang dipakai sekarang. */
    fun root(context: Context): File = decision(context).root

    /** [MODE_NEW] atau [MODE_LEGACY]. */
    fun mode(context: Context): String = decision(context).mode

    fun isLegacy(context: Context): Boolean = mode(context) == MODE_LEGACY

    /** Folder tujuan pemindahan: folder baru milik AmberLauncher. */
    fun newDir(context: Context): File = candidates(context.applicationContext ?: context).newDir

    /** Folder lama yang masih berisi data dan bisa dipindahkan, atau null. Membaca isi folder: jangan di thread utama. */
    fun legacySource(context: Context): File? =
        if (isLegacy(context)) DataLocationLogic.legacySource(candidates(context.applicationContext ?: context)) else null

    /** Membuang hasil yang tersimpan supaya dihitung ulang (mis. izin penyimpanan baru berubah). */
    fun invalidate() {
        cached = null
    }

    /**
     * Dipanggil mesin pemindah tepat setelah data baru berada di tempatnya: mencatat sisa data lama yang harus
     * dibersihkan (bila proses mati sebelum selesai menghapusnya) lalu mengganti penunjuk ke folder baru.
     */
    fun commitMigration(context: Context, source: File): Boolean {
        val app = context.applicationContext ?: context
        val cleanup = File(app.filesDir, CLEANUP_FILE)
        try {
            cleanup.writeText(source.absolutePath)
        } catch (_: Throwable) {
        }
        val ok = DataLocationLogic.writePointer(pointerFile(app), MODE_NEW)
        if (ok) invalidate() else cleanup.delete()
        return ok
    }

    /** Pemindahan selesai. Bila semua data lama sudah terhapus, catatan pembersihan tidak diperlukan lagi. */
    fun finishMigration(context: Context, leftover: Boolean) {
        if (!leftover) {
            try {
                File((context.applicationContext ?: context).filesDir, CLEANUP_FILE).delete()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Menyelesaikan penghapusan data lama yang terputus (proses dimatikan di tengah pemindahan). Hanya menghapus
     * folder lama yang dikenal, hanya setelah folder baru terbukti lengkap (ada penanda), dan hanya yang tercatat
     * saat pemindahan. Panggil di thread latar.
     */
    fun cleanupPending(context: Context) {
        try {
            if (migrating) return
            val app = context.applicationContext ?: context
            val file = File(app.filesDir, CLEANUP_FILE)
            if (!file.isFile) return
            val old = File(file.readText().trim())
            val c = candidates(app)
            val allowed = old.path.isNotEmpty() &&
                mode(app) == MODE_NEW &&
                File(c.newDir, StorageMigration.MARKER).isFile &&
                DataLocationLogic.isKnownLegacy(old, c)
            if (!allowed) {
                file.delete()
                return
            }
            if (!old.exists() || StorageMigration.deleteTree(old)) file.delete()
        } catch (_: Throwable) {
        }
    }
}
