package com.israadev.nuxlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import com.israadev.nuxlauncher.ui.dialogs.AmberStorageIntro
import com.israadev.nuxlauncher.ui.dialogs.AmberUpdatePrompt
import com.israadev.nuxlauncher.ui.i18n.AmberLanguageHost
import com.israadev.nuxlauncher.ui.screens.DashboardScreen
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxResponsiveTheme

class MainActivity : ComponentActivity() {

    // Pekerjaan berat (baca file, pindai aplikasi) dijalankan di thread latar satu per satu,
    // supaya layar tidak membeku saat dibuka atau saat kembali dari pemilih foto.
    private val bgExecutor: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor()
    private var storageWasGranted = false

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

        hideSystemBars()
        checkAndRequestStoragePermissions()
        storageWasGranted = storageAccessGranted()

        // Android 11 ke atas: jelaskan dulu alasan izinnya, cukup satu kali (tidak menagih tiap dibuka).
        val showStorageIntro = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager() &&
            !getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_STORAGE_INTRO_DONE, false)

        handleIncomingAddonIntent(intent)

        // Initialize core engines
        AccountManager.init(this)
        InstanceManager.init(this)
        SettingsManager.init(this)
        com.israadev.nuxlauncher.core.device.PhysicalMouseChecker.initChecker(this)
        com.israadev.nuxlauncher.core.controls.ControlLayoutManager.init(this)
        val appCtx = applicationContext
        bgExecutor.execute {
            try {
                com.israadev.nuxlauncher.core.renderer.NuxRendererPluginManager.scanPlugins(appCtx)
            } catch (_: Throwable) {}
            try {
                com.israadev.nuxlauncher.core.crash.CrashManager.checkAndNotify(appCtx)
            } catch (_: Throwable) {}
        }

        setContent {
            AmberLanguageHost {
                NuxResponsiveTheme {
                    MaterialTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = NuxColors.Background
                        ) {
                            DashboardScreen()
                            AmberStorageIntro(
                                initiallyVisible = showStorageIntro,
                                onOpenSettings = {
                                    markStorageIntroDone()
                                    openAllFilesAccessSettings()
                                },
                                onFinished = { markStorageIntroDone() }
                            )
                            // Jangan menumpuk dengan dialog izin penyimpanan di pembukaan pertama
                            AmberUpdatePrompt(enabled = !showStorageIntro)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        val appCtx = applicationContext
        // Muat ulang instance hanya bila izin penyimpanan baru saja berubah (bukan tiap kali
        // kembali ke aplikasi, mis. setelah memilih foto) dan tidak di thread utama.
        val granted = storageAccessGranted()
        if (granted != storageWasGranted) {
            storageWasGranted = granted
            bgExecutor.execute {
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
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onDestroy() {
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
    }
}
