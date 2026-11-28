package com.israadev.nuxlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.israadev.nuxlauncher.core.account.AccountManager
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.mods.NuxAddonImportManager
import com.israadev.nuxlauncher.core.mods.PendingAddonImport
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.core.storage.DataLocation
import com.israadev.nuxlauncher.core.storage.StorageMigrationRunner
import com.israadev.nuxlauncher.discord.DiscordLauncherPresence
import com.israadev.nuxlauncher.core.update.UpdateManager
import com.israadev.nuxlauncher.ui.components.launcherPointer
import com.israadev.nuxlauncher.ui.dialogs.AmberStorageIntro
import com.israadev.nuxlauncher.ui.dialogs.AmberStorageMigration
import com.israadev.nuxlauncher.ui.dialogs.AmberUpdatePrompt
import com.israadev.nuxlauncher.ui.dialogs.AmberWhatsNewPrompt
import com.israadev.nuxlauncher.ui.i18n.AmberLanguageHost
import com.israadev.nuxlauncher.ui.screens.DashboardScreen
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxResponsiveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // Pekerjaan berat (baca file, pindai aplikasi) dijalankan di thread latar satu per satu,
    // supaya layar tidak membeku saat dibuka atau saat kembali dari pemilih foto.
    private val bgExecutor: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor()
    private var storageWasGranted = false
    private var navUninstall: (() -> Unit)? = null

    private companion object {
        const val PREFS_NAME = "amber_launcher_prefs"
        const val KEY_STORAGE_INTRO_DONE = "storage_intro_done"
    }

    private fun storageAccessGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applySystemBars()
        // Controller: A pilih, B kembali, stick/D-pad pindah sorotan, LB/RB ganti tab.
        navUninstall = com.israadev.nuxlauncher.core.controls.GamepadNav.install(window, allowBumper = true)
        // Folder data baru (Android/data) tidak butuh izin penyimpanan. Izinnya hanya diminta bila data masih di
        // lokasi lama (folder nuxlauncher di penyimpanan utama), yang memang butuh izin akses semua file.
        val legacyStorage = DataLocation.isLegacy(this)
        if (legacyStorage) checkAndRequestStoragePermissions()
        storageWasGranted = storageAccessGranted()

        // Android 11 ke atas: jelaskan dulu alasan izinnya, cukup satu kali (tidak menagih tiap dibuka).
        val showStorageIntro = legacyStorage &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager() &&
            !getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_STORAGE_INTRO_DONE, false)

        handleIncomingAddonIntent(intent)

        // Initialize core engines (waktu tiap langkah dicatat ke log launcher untuk mencari yang lambat di HP kelas bawah)
        val initStart = System.nanoTime()
        AccountManager.init(this)
        val tAccounts = System.nanoTime()
        InstanceManager.init(this)
        val tInstances = System.nanoTime()
        SettingsManager.init(this)
        val tSettings = System.nanoTime()
        // Di proses launcher, pengaturan ditulis di thread latar (slider tidak lagi menunggu fsync di thread UI)
        SettingsManager.enableDeferredWrites()
        // Terapkan warna aksen dan gaya tampilan tersimpan sebelum layar pertama digambar
        SettingsManager.settings.value.let {
            com.israadev.nuxlauncher.ui.theme.AmberUi.setAccent(it.accentColor)
            com.israadev.nuxlauncher.ui.theme.AmberUi.setStyle(it.uiStyle)
            com.israadev.nuxlauncher.ui.theme.AmberUi.setSimpleTint(it.simpleHue, it.simpleTone)
            com.israadev.nuxlauncher.ui.theme.AmberUi.applyDim(it.bgDim / 100f)
        }
        com.israadev.nuxlauncher.core.device.PhysicalMouseChecker.initChecker(this)
        com.israadev.nuxlauncher.core.controls.ControlLayoutManager.init(this)
        val tAll = System.nanoTime()
        val appCtx = applicationContext
        bgExecutor.execute {
            try {
                com.israadev.nuxlauncher.core.renderer.NuxRendererPluginManager.scanPlugins(appCtx)
            } catch (_: Throwable) {}
            // Pemindahan data yang terputus sebelum sempat menghapus folder lama: selesaikan di latar
            try {
                DataLocation.cleanupPending(appCtx)
            } catch (_: Throwable) {}
            // Sisa berkas sementara di cache (salinan modpack tak terpakai, log yang sudah dibagikan, dll.)
            try {
                com.israadev.nuxlauncher.core.utils.CacheSweeper.sweep(appCtx.cacheDir)
            } catch (_: Throwable) {}
            try {
                com.israadev.nuxlauncher.core.crash.CrashManager.checkAndNotify(appCtx)
            } catch (_: Throwable) {}
        }

        setContent {
            AmberLanguageHost {
                NuxResponsiveTheme {
                    MaterialTheme {
                        com.israadev.nuxlauncher.ui.components.GamepadFocusProvider {
                        // Penunjuk mouse fisik (gambar, ukuran, hotspot dari Pengaturan > Penunjuk kursor).
                        com.israadev.nuxlauncher.ui.components.LauncherPointerHost {
                        Surface(
                            modifier = Modifier.fillMaxSize().launcherPointer(),
                            color = NuxColors.Background
                        ) {
                            // Pop-up pembaruan menunggu sampai dialog izin penyimpanan selesai (pembukaan pertama)
                            var introDone by remember { mutableStateOf(!showStorageIntro) }
                            // Setelah data dipindahkan, layar dibangun ulang supaya tidak memegang jalur folder lama
                            val dataEpoch by StorageMigrationRunner.epoch.collectAsState()
                            key(dataEpoch) {
                                DashboardScreen()
                            }
                            AmberStorageIntro(
                                initiallyVisible = showStorageIntro,
                                onOpenSettings = {
                                    introDone = true
                                    markStorageIntroDone()
                                    openAllFilesAccessSettings()
                                },
                                onFinished = {
                                    introDone = true
                                    markStorageIntroDone()
                                }
                            )
                            // Pengguna lama ditawari memindahkan data ke Android/data, sesudah dialog izin selesai
                            LaunchedEffect(introDone) {
                                if (introDone) {
                                    val offerIt = withContext(Dispatchers.IO) {
                                        try {
                                            StorageMigrationRunner.shouldAutoOffer(appCtx)
                                        } catch (_: Throwable) {
                                            false
                                        }
                                    }
                                    if (offerIt) StorageMigrationRunner.offer(appCtx)
                                }
                            }
                            AmberStorageMigration()
                            val migrationIdle by remember {
                                StorageMigrationRunner.state.map { it is StorageMigrationRunner.UiState.Idle }
                            }.collectAsState(initial = true)
                            AmberUpdatePrompt(enabled = introDone && migrationIdle)
                            // Catatan "Yang baru" setelah pembaruan, menunggu dialog lain selesai dan tidak menumpuk di atas pop-up pembaruan
                            val updateOpen by com.israadev.nuxlauncher.core.update.UpdateManager.promptOpen.collectAsState()
                            AmberWhatsNewPrompt(enabled = introDone && migrationIdle && !updateOpen)
                        }
                        }
                        }
                    }
                }
            }
        }

        // Catatan waktu start dingin: dari proses dimulai sampai kerangka layar pertama siap digambar.
        // Hanya pada pembuatan pertama; Activity yang dibuat ulang (mis. setelah proses dimatikan) tidak mewakili start dingin.
        if (savedInstanceState == null) window.decorView.post {
            val sinceProcessMs = android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()
            com.israadev.nuxlauncher.core.crash.LauncherLog.i(
                "Mulai",
                "Layar pertama siap ${sinceProcessMs} ms setelah proses dimulai (akun ${(tAccounts - initStart) / 1_000_000} ms, " +
                    "instance ${(tInstances - tAccounts) / 1_000_000} ms, pengaturan ${(tSettings - tInstances) / 1_000_000} ms, " +
                    "total init ${(tAll - initStart) / 1_000_000} ms)"
            )
            reportFullyDrawn()
        }
    }

    override fun onResume() {
        super.onResume()
        applySystemBars()
        val appCtx = applicationContext
        // Game berjalan di proses lain dan bisa mengubah pengaturan (mis. overlay streamer yang diatur dari
        // menu di dalam game): baca ulang bila berkasnya berubah, supaya tidak tertimpa salinan lama di sini.
        bgExecutor.execute { try { SettingsManager.syncFromDisk() } catch (_: Throwable) {} }
        // Muat ulang instance hanya bila izin penyimpanan baru saja berubah (bukan tiap kali
        // kembali ke aplikasi, mis. setelah memilih foto) dan tidak di thread utama.
        val granted = storageAccessGranted()
        if (granted != storageWasGranted) {
            storageWasGranted = granted
            bgExecutor.execute {
                DataLocation.invalidate()
                try { InstanceManager.init(appCtx) } catch (_: Throwable) {}
                // Izin baru diberikan: coba pulihkan layout kontrol dari cadangan (jika ada)
                try { com.israadev.nuxlauncher.core.controls.ControlLayoutManager.init(appCtx) } catch (_: Throwable) {}
            }
        }
        // Cek apakah game sebelumnya crash (membaca file log, jadi di thread latar)
        bgExecutor.execute {
            try {
                com.israadev.nuxlauncher.core.crash.CrashManager.checkAndNotify(appCtx)
            } catch (_: Throwable) {}
        }
        // Auto-update: tiap aplikasi tampil di depan, cek versi baru (murah: ETag + daftar tersimpan)
        UpdateManager.onForeground(appCtx)
        // Discord Rich Presence di launcher (opsional; langsung kembali bila mati atau build tanpa SDK)
        DiscordLauncherPresence.onResume(this)
    }

    override fun onPause() {
        // Layar ditinggalkan (mis. game dibuka): hapus presence launcher sebelum presence game muncul
        DiscordLauncherPresence.onPause()
        super.onPause()
    }

    override fun onStop() {
        // Aplikasi keluar dari layar (bisa dimatikan sistem kapan saja): tuntaskan penulisan pengaturan yang tertunda
        SettingsManager.flush()
        UpdateManager.onBackground()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applySystemBars()
        }
    }

    override fun onDestroy() {
        navUninstall?.invoke()
        navUninstall = null
        super.onDestroy()
        bgExecutor.shutdown()
        com.israadev.nuxlauncher.core.skin.OfflineSkinServerManager.stopServer()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            window.attributes = params
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }

    /**
     * Bar status dan navigasi Android selalu disembunyikan selama launcher terbuka, apa pun kondisinya
     * (termasuk saat ada mouse, keyboard, atau gamepad terhubung). Bar hanya muncul sebentar bila pengguna
     * menggesernya dari tepi layar atau dimunculkan sistem Android sendiri, lalu hilang lagi.
     * Dipanggil ulang tiap layar dibuka, dilanjutkan, dan tiap jendela mendapat fokus (mis. setelah dialog
     * atau papan ketik), karena sebagian HP memunculkan bar kembali saat itu.
     */
    private fun applySystemBars() {
        hideSystemBars()
    }

    private fun markStorageIntroDone() {
        try {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_STORAGE_INTRO_DONE, true).apply()
        } catch (_: Exception) {
        }
    }

    /** Membuka halaman izin "akses semua file" (Android 11 ke atas). Aman bila halamannya tidak ada. */
    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun checkAndRequestStoragePermissions() {
        // Android 11 ke atas ditangani dialog penjelasan (AmberStorageIntro); di bawahnya memakai
        // dialog izin standar Android.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val needed = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingAddonIntent(intent)
    }

    private fun handleIncomingAddonIntent(intent: Intent?) {
        if (intent == null) return
        val uri = intent.data ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }) ?: return

        // Intent ini bisa dikirim aplikasi apa pun: tolak alamat yang menunjuk ke data pribadi launcher
        if (!NuxAddonImportManager.isAcceptableSource(this, uri)) return

        val aliasClass = intent.component?.className
        val fileName = NuxAddonImportManager.getFileName(this, uri)
        val resolvedType = NuxAddonImportManager.resolveTypeFromAliasOrFile(this, uri, aliasClass)

        NuxAddonImportManager.setPendingImport(
            PendingAddonImport(
                uri = uri,
                fileName = fileName,
                suggestedType = resolvedType
            )
        )
        // Sudah ditangani. Bila Activity dibuat ulang (mode gelap, keyboard dicolok, ganti ukuran font), Android memberi
        // intent yang sama; tanpa ini dialog impor yang sudah ditutup atau selesai muncul lagi.
        intent.data = null
        intent.removeExtra(Intent.EXTRA_STREAM)
    }
}
