package com.israadev.nuxlauncher.core.storage

import android.app.ActivityManager
import android.content.Context
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.instance.InstanceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Menjalankan pemindahan data ke Android/data dan menyimpan keadaannya untuk dialog
 * (lihat ui/dialogs/AmberStorageMigration.kt). Pekerjaannya berjalan di lingkup aplikasi, jadi layar
 * diputar atau Activity dibuat ulang tidak menghentikannya. Mesinnya sendiri ada di [StorageMigration].
 */
object StorageMigrationRunner {

    enum class FailKind { GAME_RUNNING, NO_SOURCE, TARGET_NOT_EMPTY, NOT_ENOUGH_SPACE, ERROR }

    sealed interface UiState {
        object Idle : UiState

        /** Memeriksa lokasi data; belum ada yang ditampilkan. */
        object Checking : UiState

        /** Penjelasan untung-rugi. [sizeBytes] dan [freeBytes] null selama masih dihitung. */
        class Offer(
            val source: File,
            val target: File,
            val sizeBytes: Long?,
            val freeBytes: Long?
        ) : UiState

        class Running(
            val phase: StorageMigration.Phase,
            val doneBytes: Long,
            val totalBytes: Long
        ) : UiState

        class Done(val moved: Boolean, val leftover: Boolean, val path: String) : UiState

        object Cancelled : UiState

        class Failed(val kind: FailKind, val neededBytes: Long, val availableBytes: Long) : UiState
    }

    private const val PREFS_NAME = "amber_launcher_prefs"
    private const val KEY_OFFER_DECLINED = "storage_migration_declined"

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Naik setelah pemindahan berhasil; akar UI memakainya untuk membangun ulang layar dengan folder baru. */
    private val _epoch = MutableStateFlow(0)
    val epoch: StateFlow<Int> = _epoch.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val cancelFlag = AtomicBoolean(false)

    /** True bila tawaran otomatis belum pernah ditolak. */
    fun offerWasDeclined(context: Context): Boolean = try {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OFFER_DECLINED, false)
    } catch (_: Throwable) {
        false
    }

    private fun rememberDeclined(context: Context) {
        try {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_OFFER_DECLINED, true).apply()
        } catch (_: Throwable) {
        }
    }

    /**
     * Menampilkan penjelasan pemindahan. Tidak melakukan apa-apa bila ada dialog pemindahan lain yang
     * sedang tampil atau data sudah di folder baru. Aman dipanggil dari thread mana pun.
     */
    fun offer(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            if (_state.value != UiState.Idle) return
            _state.value = UiState.Checking
        }
        job = scope.launch {
            try {
                if (!DataLocation.isLegacy(app)) {
                    _state.value = UiState.Idle
                    return@launch
                }
                if (isGameProcessAlive(app)) {
                    _state.value = UiState.Failed(FailKind.GAME_RUNNING, 0L, 0L)
                    return@launch
                }
                val source = DataLocation.legacySource(app)
                if (source == null) {
                    _state.value = UiState.Failed(FailKind.NO_SOURCE, 0L, 0L)
                    return@launch
                }
                val target = DataLocation.newDir(app)
                _state.value = UiState.Offer(source, target, null, null)
                val size = StorageMigration.measure(source) { _state.value !is UiState.Offer }
                val free = try {
                    (target.parentFile ?: target).usableSpace
                } catch (_: Throwable) {
                    null
                }
                val current = _state.value
                if (size != null && current is UiState.Offer && current.source == source) {
                    // Hanya bila tampilannya belum berubah (mis. pengguna sudah menekan Pindahkan)
                    _state.compareAndSet(current, UiState.Offer(source, target, size, free))
                }
            } catch (e: Throwable) {
                LauncherLog.w("StorageMigration", "Gagal menyiapkan tawaran: ${e.message}")
                _state.value = UiState.Failed(FailKind.ERROR, 0L, 0L)
            }
        }
    }

    /** Tombol Tetap di penjelasan: data tetap di folder lama, dan tawaran otomatis tidak muncul lagi. */
    fun decline(context: Context) {
        rememberDeclined(context)
        job?.cancel()
        synchronized(this) {
            if (_state.value is UiState.Offer || _state.value == UiState.Checking) _state.value = UiState.Idle
        }
    }

    /** Menutup dialog hasil (selesai, dibatalkan, gagal). */
    fun dismiss() {
        if (_state.value is UiState.Running) return
        _state.value = UiState.Idle
    }

    /** Tombol Batal saat pemindahan berjalan. Berlaku sampai data baru dipakai; sesudahnya diabaikan mesin. */
    fun cancel() {
        cancelFlag.set(true)
    }

    /** Tombol Pindahkan di penjelasan. */
    fun start(context: Context) {
        val app = context.applicationContext
        val offer: UiState.Offer = synchronized(this) {
            val s = _state.value
            if (s !is UiState.Offer) return
            _state.value = UiState.Running(StorageMigration.Phase.PREPARING, 0L, 0L)
            s
        }
        cancelFlag.set(false)
        job?.cancel()
        job = scope.launch {
            try {
                if (isGameProcessAlive(app)) {
                    _state.value = UiState.Failed(FailKind.GAME_RUNNING, 0L, 0L)
                    return@launch
                }
                // Menunggu pemuatan daftar instance yang sedang berjalan, lalu menahan yang berikutnya
                synchronized(DataLocation.lock) { DataLocation.migrating = true }
                val result = try {
                    StorageMigration.run(
                        offer.source,
                        offer.target,
                        StorageMigration.Hooks(
                            isCancelled = { cancelFlag.get() },
                            onProgress = { p ->
                                _state.value = UiState.Running(p.phase, p.doneBytes, p.totalBytes)
                            },
                            commit = { DataLocation.commitMigration(app, offer.source) }
                        )
                    )
                } finally {
                    DataLocation.migrating = false
                }
                finish(app, result)
            } catch (e: Throwable) {
                LauncherLog.w("StorageMigration", "Galat tak terduga: ${e.message}")
                _state.value = UiState.Failed(FailKind.ERROR, 0L, 0L)
            }
        }
    }

    private fun finish(app: Context, result: StorageMigration.Result) {
        when (result) {
            is StorageMigration.Result.Success -> {
                DataLocation.finishMigration(app, result.leftover)
                LauncherLog.i(
                    "StorageMigration",
                    "Selesai: moved=${result.moved} bytes=${result.bytes} leftover=${result.leftover}"
                )
                try {
                    InstanceManager.init(app)
                } catch (e: Throwable) {
                    LauncherLog.w("StorageMigration", "Muat ulang instance gagal: ${e.message}")
                }
                _epoch.value = _epoch.value + 1
                _state.value = UiState.Done(
                    moved = result.moved,
                    leftover = result.leftover,
                    path = DataLocation.root(app).absolutePath
                )
            }
            is StorageMigration.Result.Cancelled -> {
                LauncherLog.i("StorageMigration", "Dibatalkan pengguna")
                // Pengguna sudah berubah pikiran: tawaran otomatis tidak muncul lagi (tetap bisa dari Pengaturan)
                rememberDeclined(app)
                _state.value = UiState.Cancelled
            }
            is StorageMigration.Result.Failed -> {
                LauncherLog.w("StorageMigration", "Gagal: ${result.reason} ${result.detail}")
                val kind = when (result.reason) {
                    StorageMigration.Reason.SOURCE_MISSING -> FailKind.NO_SOURCE
                    StorageMigration.Reason.TARGET_NOT_EMPTY -> FailKind.TARGET_NOT_EMPTY
                    StorageMigration.Reason.NOT_ENOUGH_SPACE -> FailKind.NOT_ENOUGH_SPACE
                    else -> FailKind.ERROR
                }
                _state.value = UiState.Failed(kind, result.neededBytes, result.availableBytes)
            }
        }
    }

    /** Proses game, Forge, atau mesin Rust masih hidup: data sedang dipakai, jangan dipindahkan. */
    private fun isGameProcessAlive(context: Context): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.runningAppProcesses?.any {
            it.processName.endsWith(":game") || it.processName.endsWith(":horizon") || it.processName.endsWith(":forge")
        } == true
    } catch (_: Throwable) {
        false
    }

    /** True bila tawaran otomatis perlu ditampilkan: data masih di lokasi lama, dan belum pernah ditolak. */
    fun shouldAutoOffer(context: Context): Boolean =
        !offerWasDeclined(context) && DataLocation.legacySource(context) != null
}
