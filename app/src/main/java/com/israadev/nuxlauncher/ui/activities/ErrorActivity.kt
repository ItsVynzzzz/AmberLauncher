package com.israadev.nuxlauncher.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.israadev.nuxlauncher.MainActivity
import com.israadev.nuxlauncher.core.crash.CrashManager
import com.israadev.nuxlauncher.core.crash.GameCrashInfo
import com.israadev.nuxlauncher.ui.components.AmberCrashPanel
import com.israadev.nuxlauncher.ui.i18n.Tr
import com.israadev.nuxlauncher.ui.theme.Amber
import com.israadev.nuxlauncher.ui.theme.NuxReadable
import java.io.File

/**
 * Halaman crash mandiri: dibuka saat game berhenti mendadak (dari proses game) atau saat launcher
 * sendiri mengalami kesalahan tak terduga. Tampilannya sama dengan dialog crash di Beranda
 * ([AmberCrashPanel]).
 */
class ErrorActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CRASH_TYPE = "EXTRA_CRASH_TYPE"
        const val EXTRA_EXIT_CODE = "EXTRA_EXIT_CODE"
        const val EXTRA_IS_SIGNAL = "EXTRA_IS_SIGNAL"
        const val EXTRA_LOG_PATH = "EXTRA_LOG_PATH"
        const val EXTRA_INSTANCE_NAME = "EXTRA_INSTANCE_NAME"
        const val EXTRA_MC_VERSION = "EXTRA_MC_VERSION"
        const val EXTRA_THROWABLE_STRING = "EXTRA_THROWABLE_STRING"

        fun showGameCrash(
            context: Context,
            exitCode: Int,
            isSignal: Boolean,
            logPath: String,
            instanceName: String = "Minecraft",
            mcVersion: String = "Unknown"
        ) {
            val intent = Intent(context, ErrorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_CRASH_TYPE, GameCrashInfo.CRASH_TYPE_GAME)
                putExtra(EXTRA_EXIT_CODE, exitCode)
                putExtra(EXTRA_IS_SIGNAL, isSignal)
                putExtra(EXTRA_LOG_PATH, logPath)
                putExtra(EXTRA_INSTANCE_NAME, instanceName)
                putExtra(EXTRA_MC_VERSION, mcVersion)
            }
            context.startActivity(intent)
        }

        fun showLauncherCrash(
            context: Context,
            throwable: Throwable
        ) {
            val stackTrace = android.util.Log.getStackTraceString(throwable)
            val intent = Intent(context, ErrorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_CRASH_TYPE, GameCrashInfo.CRASH_TYPE_LAUNCHER)
                putExtra(EXTRA_THROWABLE_STRING, stackTrace)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashType = intent.getStringExtra(EXTRA_CRASH_TYPE) ?: GameCrashInfo.CRASH_TYPE_GAME
        val exitCode = intent.getIntExtra(EXTRA_EXIT_CODE, 1)
        val isSignal = intent.getBooleanExtra(EXTRA_IS_SIGNAL, false)
        val logPath = intent.getStringExtra(EXTRA_LOG_PATH) ?: ""
        val instanceName = intent.getStringExtra(EXTRA_INSTANCE_NAME) ?: "Minecraft"
        val mcVersion = intent.getStringExtra(EXTRA_MC_VERSION) ?: "Unknown"
        val throwableString = intent.getStringExtra(EXTRA_THROWABLE_STRING)

        val logFile = if (logPath.isNotBlank()) File(logPath) else File(filesDir, "latestlog.txt")

        // CrashManager sudah menulis catatan lengkap (pemuat mod, daftar mod, potongan log) sebelum
        // halaman ini dibuka. Game berjalan di proses lain, jadi dibaca dari berkas.
        val saved = if (crashType == GameCrashInfo.CRASH_TYPE_GAME) {
            CrashManager.readSavedCrash(this)?.takeIf { it.instanceName == instanceName }
        } else {
            null
        }

        // Memuat konten log untuk ditampilkan
        val logContent = when {
            throwableString != null -> throwableString
            saved != null && saved.logSnippet.isNotBlank() -> saved.logSnippet
            logFile.exists() -> {
                try {
                    val lines = logFile.readLines()
                    if (lines.size > 300) lines.takeLast(300).joinToString("\n")
                    else lines.joinToString("\n")
                } catch (e: Exception) {
                    Tr.t("Gagal membaca file log: ${e.message}")
                }
            }
            else -> ""
        }

        val info = GameCrashInfo(
            instanceName = instanceName,
            mcVersion = mcVersion,
            exitCode = exitCode,
            isSignal = isSignal,
            logSnippet = logContent,
            fullLogPath = logPath,
            crashType = crashType,
            loader = saved?.loader ?: "vanilla",
            loaderVersion = saved?.loaderVersion,
            installedMods = saved?.installedMods.orEmpty()
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Amber.Ink
                ) {
                    NuxReadable(scale = 0.9f) {
                        AmberCrashPanel(
                            info = info,
                            logText = logContent,
                            logFile = logFile,
                            onClose = { finish() },
                            onBackToLauncher = {
                                val mainIntent = Intent(this@ErrorActivity, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                                startActivity(mainIntent)
                                finish()
                            },
                            modifier = Modifier
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    }
                }
            }
        }
    }
}
