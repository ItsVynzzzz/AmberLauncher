package com.israadev.nuxlauncher.core.download

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.utils.FriendlyError
import com.israadev.nuxlauncher.ui.i18n.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Pemasangan instance yang sedang berjalan (siapkan Java, unduh game, pasang modpack).
 *
 * Dulu pekerjaannya dimiliki layar Beranda (`rememberCoroutineScope`), jadi ikut batal begitu layarnya hilang dari
 * komposisi atau Activity dibuat ulang; unduhan 400 MB+ di HP berjaringan lambat terhenti diam-diam dan harus diulang.
 * Sekarang dipegang di sini (hidup selama proses), dan layar hanya mengamati [active], [targetName], dan [progress].
 * Hanya satu pemasangan pada satu waktu.
 */
object InstallSession {

    /** Kemajuan; hanya dibaca dialog unduhan, supaya tiap kemajuan tidak menggambar ulang seluruh layar Beranda. */
    class Progress {
        var progress by mutableFloatStateOf(0f)
        var message by mutableStateOf("")
    }

    val progress = Progress()

    /** Kegagalan terakhir yang perlu dilihat pengguna (ditampilkan sebagai dialog, bukan Toast yang cepat hilang). */
    class Failure(
        val title: String,
        val message: String,
        val detail: String,
        /** Instance yang bisa dicoba lagi lewat tombol "Coba lagi"; null bila tidak ada. */
        val retryInstanceId: String?
    )

    var failure by mutableStateOf<Failure?>(null)
        private set

    fun reportFailure(title: String, error: Throwable?, retryInstanceId: String? = null) {
        if (error != null) LauncherLog.w("Unduhan", "$title: ${error.javaClass.simpleName}: ${error.message}", error)
        val info = FriendlyError.describe(error)
        failure = Failure(title, info.message, info.detail, retryInstanceId)
    }

    fun dismissFailure() {
        failure = null
    }

    /** True selama ada pemasangan berjalan. Aman dibaca di komposisi. */
    var active by mutableStateOf(false)
        private set

    var targetName by mutableStateOf("")
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    /**
     * Memulai [block] di latar (thread utama; pekerjaan beratnya sendiri berpindah ke IO). Mengembalikan false bila sudah
     * ada pemasangan lain. [keepAlive] menyalakan penjaga proses selama unduhan besar; matikan untuk pekerjaan singkat.
     * Isi [block] memakai Context aplikasi, bukan Activity, supaya tidak menahan Activity di memori.
     */
    fun start(
        context: Context,
        name: String,
        initialMessage: String,
        keepAlive: Boolean = true,
        block: suspend CoroutineScope.() -> Unit
    ): Boolean {
        if (active) return false
        val app = context.applicationContext
        failure = null
        active = true
        targetName = name
        progress.progress = 0f
        progress.message = initialMessage
        if (keepAlive) DownloadKeepAliveService.start(app)

        val started = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Kehabisan memori tidak aman dilanjutkan; galat lain jangan sampai menjatuhkan launcher.
                if (t is VirtualMachineError) throw t
                LauncherLog.e("Unduhan", "Pemasangan $name berhenti karena galat tak terduga", t)
                Toast.makeText(app, "Pemasangan berhenti: ${t.message ?: t.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
        }
        job = started
        started.invokeOnCompletion {
            // Selesai, gagal, atau dibatalkan. Bila sudah digantikan pemasangan lain, jangan menyentuh statusnya.
            if (job === started) {
                job = null
                active = false
            }
            if (keepAlive) DownloadKeepAliveService.stop(app)
        }
        return true
    }

    /** Membatalkan pemasangan berjalan. Berkas yang sudah selesai tetap dipakai pada UNDUH berikutnya. */
    fun cancel(): Boolean {
        val running = job ?: return false
        job = null
        active = false
        running.cancel()
        return true
    }
}
