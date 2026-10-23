package com.israadev.nuxlauncher.core.update

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.StatFs
import com.israadev.nuxlauncher.AppLinks
import com.israadev.nuxlauncher.core.settings.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Pusat auto-update: memegang keadaan yang dilihat UI ([state], [promptOpen]) dan menjalankan
 * alurnya: cek rilis -> unduh -> periksa APK -> pasang lewat PackageInstaller.
 *
 * Kapan mengecek:
 *  - Tiap aplikasi dibuka atau kembali ke depan ([onForeground]), bila "Periksa otomatis" menyala.
 *    Pertama dijawab dari daftar rilis tersimpan (langsung, tanpa internet), lalu ditanyakan ke GitHub
 *    paling sering tiap 3 menit. Pertanyaannya memakai ETag, jadi biayanya nyaris nol.
 *  - Kapan saja lewat tombol "Periksa pembaruan" di Pengaturan ([checkNow]); tombol ini mengabaikan
 *    jeda, versi yang dilewati, dan pengaturan otomatis, dan selalu menjelaskan hasilnya.
 *
 * Semua fungsi publik dipanggil dari thread utama.
 */
object UpdateManager {
    private const val AUTO_MIN_INTERVAL_MS = 3L * 60 * 1000
    private const val INSTALL_REVERT_DELAY_MS = 1_500L
    private const val SESSION_COPY_MARGIN_BYTES = 64L * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Pop-up pembaruan sedang diminta tampil. Ditutup pengguna lewat Nanti/Lewati/Tutup. */
    private val _promptOpen = MutableStateFlow(false)
    val promptOpen: StateFlow<Boolean> = _promptOpen.asStateFlow()

    private var foreground = false
    private var lastAttemptAt = 0L
    private var cleanedUp = false
    private var dismissedTag: String? = null
    private var downloadJob: Job? = null
    private var downloadToken: ApkDownloader.CancelToken? = null
    private var pendingInstall: Pair<AppUpdate, File>? = null
    private var backgroundedWhileInstalling = false

    // ---- Dipanggil dari MainActivity -------------------------------------------------------

    /** Aplikasi tampil di depan (onResume). */
    fun onForeground(context: Context) {
        val app = context.applicationContext
        foreground = true

        val s = _state.value
        if (s is UpdateState.Installing && backgroundedWhileInstalling) {
            // Kembali dari layar konfirmasi Android. Bila hasilnya tidak kunjung datang, anggap
            // dibatalkan supaya pengguna bisa mencoba lagi.
            backgroundedWhileInstalling = false
            scope.launch {
                delay(INSTALL_REVERT_DELAY_MS)
                val pending = pendingInstall
                if (_state.value is UpdateState.Installing && pending != null) {
                    _state.value = UpdateState.ReadyToInstall(pending.first, pending.second)
                    _promptOpen.value = true
                }
            }
            return
        }
        // Kembali dari layar izin "pasang aplikasi": lanjutkan bila izinnya sudah diberikan.
        if (s is UpdateState.NeedsInstallPermission && UpdateInstaller.canInstall(app)) {
            install(app)
            return
        }
        if (!SettingsManager.settings.value.autoCheckUpdates) return
        scope.launch { autoCheck(app) }
    }

    /** Aplikasi tidak lagi tampil (onStop). */
    fun onBackground() {
        foreground = false
        if (_state.value is UpdateState.Installing) backgroundedWhileInstalling = true
    }

    // ---- Pengecekan ------------------------------------------------------------------------

    /** Tombol "Periksa pembaruan". */
    fun checkNow(context: Context) {
        val app = context.applicationContext
        when (_state.value) {
            is UpdateState.Checking -> return
            is UpdateState.Downloading,
            is UpdateState.ReadyToInstall,
            is UpdateState.NeedsInstallPermission,
            is UpdateState.Installing -> {
                _promptOpen.value = true
                return
            }
            else -> Unit
        }
        scope.launch { runCheck(app, manual = true) }
    }

    private suspend fun autoCheck(app: Context) {
        if (!cleanedUp) {
            cleanedUp = true
            withContext(Dispatchers.IO) {
                try {
                    UpdateInstaller.cleanup(app, installedVersion(app))
                } catch (_: Exception) {
                }
            }
        }
        when (_state.value) {
            is UpdateState.Checking,
            is UpdateState.Downloading,
            is UpdateState.ReadyToInstall,
            is UpdateState.NeedsInstallPermission,
            is UpdateState.Installing,
            is UpdateState.ActionFailed -> return
            else -> Unit
        }

        // 1) Dari daftar rilis tersimpan: pop-up bisa muncul seketika, juga saat offline.
        val cached = withContext(Dispatchers.IO) { UpdateChecker.evaluateCached(app) }
        if (cached is UpdateChecker.Result.Found) applyFound(app, cached.update, manual = false)

        // 2) Tanya GitHub (dengan ETag), tidak lebih sering dari tiap 3 menit dan tidak saat kena batas.
        val now = System.currentTimeMillis()
        if (now - lastAttemptAt < AUTO_MIN_INTERVAL_MS) return
        if (now < UpdateStore.blockedUntil(app)) return
        runCheck(app, manual = false)
    }

    private suspend fun runCheck(app: Context, manual: Boolean) {
        if (manual) _state.value = UpdateState.Checking
        lastAttemptAt = System.currentTimeMillis()
        val result = UpdateChecker.check(app)

        // Selama menunggu GitHub, pengguna mungkin sudah mulai mengunduh/memasang: jangan ditimpa.
        when (_state.value) {
            is UpdateState.Downloading,
            is UpdateState.ReadyToInstall,
            is UpdateState.NeedsInstallPermission,
            is UpdateState.Installing -> return
            else -> Unit
        }
        when (result) {
            is UpdateChecker.Result.Found -> applyFound(app, result.update, manual)
            UpdateChecker.Result.UpToDate -> _state.value = UpdateState.UpToDate(System.currentTimeMillis())
            is UpdateChecker.Result.NoApk -> _state.value = UpdateState.NoApk(result.tag, result.pageUrl)
            // Kegagalan pengecekan otomatis diam-diam; yang manual dijelaskan di Pengaturan.
            is UpdateChecker.Result.Failed -> if (manual) _state.value = UpdateState.Failed(result.failure)
        }
    }

    private fun applyFound(app: Context, update: AppUpdate, manual: Boolean) {
        val prev = _state.value
        val same = prev is UpdateState.Available && prev.update.tag == update.tag
        if (!same) _state.value = UpdateState.Available(update)
        if (manual) {
            dismissedTag = null
            _promptOpen.value = true
        } else if (!same && !UpdateChecker.isSkipped(app, update.tag) && dismissedTag != update.tag) {
            _promptOpen.value = true
        }
    }

    // ---- Pop-up ----------------------------------------------------------------------------

    fun openPrompt() {
        _promptOpen.value = true
    }

    /** "Nanti" / "Tutup": tidak ditawarkan lagi sampai aplikasi dibuka ulang. */
    fun dismissPrompt() {
        _promptOpen.value = false
        val s = _state.value
        if (s is UpdateState.Available) dismissedTag = s.update.tag
    }

    /** "Lewati": versi ini tidak ditawarkan otomatis lagi; versi yang lebih baru tetap. */
    fun skipVersion(context: Context) {
        val s = _state.value
        if (s is UpdateState.Available) UpdateChecker.skip(context.applicationContext, s.update.tag)
        _promptOpen.value = false
    }

    /** Alamat halaman rilis untuk tombol cadangan "Buka GitHub". */
    fun releasePageUrl(): String {
        val page = when (val s = _state.value) {
            is UpdateState.Available -> s.update.pageUrl
            is UpdateState.Downloading -> s.update.pageUrl
            is UpdateState.ReadyToInstall -> s.update.pageUrl
            is UpdateState.NeedsInstallPermission -> s.update.pageUrl
            is UpdateState.Installing -> s.update.pageUrl
            is UpdateState.ActionFailed -> s.update.pageUrl
            is UpdateState.NoApk -> s.pageUrl
            else -> null
        }
        return page ?: "https://github.com/${AppLinks.GITHUB_REPO}/releases"
    }

    // ---- Unduh -----------------------------------------------------------------------------

    /** Tombol "Unduh" (juga "Coba lagi" setelah unduhan gagal; unduhan dilanjutkan dari bagian yang ada). */
    fun startDownload(context: Context) {
        val app = context.applicationContext
        val update = when (val s = _state.value) {
            is UpdateState.Available -> s.update
            is UpdateState.ActionFailed -> {
                if (s.apk != null) {
                    install(app) // berkasnya sudah utuh, cukup dipasang ulang
                    return
                }
                s.update
            }
            is UpdateState.ReadyToInstall, is UpdateState.NeedsInstallPermission -> {
                install(app)
                return
            }
            else -> return
        }
        if (downloadJob?.isActive == true) return
        val token = ApkDownloader.CancelToken()
        downloadToken = token
        downloadJob = scope.launch { runDownload(app, update, token) }
    }

    /** Tombol "Batal" saat mengunduh. Bagian yang sudah terunduh disimpan untuk dilanjutkan nanti. */
    fun cancelDownload() {
        val s = _state.value
        downloadToken?.cancel()
        downloadJob?.cancel()
        if (s is UpdateState.Downloading) _state.value = UpdateState.Available(s.update)
    }

    private suspend fun runDownload(app: Context, update: AppUpdate, token: ApkDownloader.CancelToken) {
        val apk = update.apk
        val target = UpdateInstaller.apkFile(app, apk.name)
        _state.value = UpdateState.Downloading(update, 0L, apk.sizeBytes ?: 0L)

        val failure: UpdateFailure? = try {
            withContext(Dispatchers.IO) {
                try {
                    ApkDownloader.download(
                        client = downloadClient,
                        url = apk.url,
                        target = target,
                        expectedSize = apk.sizeBytes,
                        expectedSha256 = apk.sha256,
                        userAgent = "AmberLauncher/${UpdateChecker.currentVersionName(app)}",
                        token = token,
                        freeSpace = { freeBytes(target.parentFile ?: app.cacheDir) },
                        spareBytes = (apk.sizeBytes ?: 0L) + SESSION_COPY_MARGIN_BYTES,
                        onProgress = { done, total ->
                            _state.update { cur ->
                                if (cur is UpdateState.Downloading && cur.update.tag == update.tag) {
                                    UpdateState.Downloading(update, done, total)
                                } else {
                                    cur
                                }
                            }
                        }
                    )
                    UpdateInstaller.verifyApk(app, target)?.also { target.delete() }
                } catch (e: ApkDownloader.DownloadException) {
                    e.failure
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            UpdateFailure(FailureKind.DOWNLOAD, "Unduhan gagal: ${t.message ?: t.javaClass.simpleName}")
        }

        if (failure != null) {
            _state.value = UpdateState.ActionFailed(update, failure, null)
            _promptOpen.value = true
            return
        }
        _state.value = UpdateState.ReadyToInstall(update, target)
        if (foreground) {
            install(app) // pengguna menunggu: langsung serahkan ke Android
        } else {
            _promptOpen.value = true // pengguna sedang di aplikasi lain: tanyakan saat kembali
        }
    }

    private fun freeBytes(dir: File): Long = try {
        dir.mkdirs()
        StatFs(dir.path).availableBytes
    } catch (_: Throwable) {
        Long.MAX_VALUE
    }

    // ---- Pasang ----------------------------------------------------------------------------

    /** Tombol "Pasang" (juga lanjutan otomatis setelah unduhan selesai atau izin diberikan). */
    fun install(context: Context) {
        val app = context.applicationContext
        val (update, apk) = when (val s = _state.value) {
            is UpdateState.ReadyToInstall -> s.update to s.apk
            is UpdateState.NeedsInstallPermission -> s.update to s.apk
            is UpdateState.ActionFailed -> {
                val file = s.apk ?: return
                s.update to file
            }
            else -> return
        }

        if (!apk.isFile) {
            _state.value = UpdateState.ActionFailed(
                update,
                UpdateFailure(FailureKind.DOWNLOAD, "Berkas pembaruan hilang. Ketuk Coba lagi untuk mengunduh ulang."),
                null
            )
            _promptOpen.value = true
            return
        }
        if (UpdateInstaller.isGameRunning(app)) {
            _state.value = UpdateState.ActionFailed(
                update,
                UpdateFailure(
                    FailureKind.GAME_RUNNING,
                    "Game masih berjalan. Tutup game dulu supaya tidak ada data yang hilang, lalu coba lagi."
                ),
                apk
            )
            _promptOpen.value = true
            return
        }
        if (!UpdateInstaller.canInstall(app)) {
            _state.value = UpdateState.NeedsInstallPermission(update, apk)
            _promptOpen.value = true
            return
        }

        pendingInstall = update to apk
        backgroundedWhileInstalling = false
        _state.value = UpdateState.Installing(update)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { UpdateInstaller.startInstall(app, apk) }
                // Selanjutnya hasilnya datang lewat UpdateInstallReceiver -> onInstallResult.
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value = UpdateState.ActionFailed(
                    update,
                    UpdateFailure(FailureKind.INSTALL_FAILED, "Pemasangan gagal: ${t.message ?: t.javaClass.simpleName}"),
                    apk
                )
                _promptOpen.value = true
            }
        }
    }

    /** Membuka halaman izin "Izinkan dari sumber ini" untuk aplikasi ini. */
    fun openInstallPermissionSettings(context: Context): Boolean =
        UpdateInstaller.openInstallPermissionSettings(context)

    /** Hasil pemasangan dari [UpdateInstallReceiver] (selain permintaan konfirmasi). */
    fun onInstallResult(context: Context, status: Int, message: String?) {
        val pending = pendingInstall
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                pendingInstall = null
                _state.value = UpdateState.Idle
                _promptOpen.value = false
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                if (pending != null) {
                    _state.value = UpdateState.ReadyToInstall(pending.first, pending.second, "Pemasangan dibatalkan.")
                    _promptOpen.value = true
                } else {
                    _state.value = UpdateState.Idle
                }
            }
            else -> {
                val failure = UpdateInstaller.failureForStatus(status, message)
                if (pending != null) {
                    _state.value = UpdateState.ActionFailed(pending.first, failure, pending.second)
                    _promptOpen.value = true
                } else {
                    _state.value = UpdateState.Failed(failure)
                }
            }
        }
    }

    private fun installedVersion(app: Context): AppVersion =
        AppVersion.parse(UpdateChecker.currentVersionName(app)) ?: AppVersion.parse("0.0.0")!!
}
