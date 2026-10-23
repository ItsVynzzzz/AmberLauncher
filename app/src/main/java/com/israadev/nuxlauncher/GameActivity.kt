package com.israadev.nuxlauncher

import android.content.Context
import com.israadev.nuxlauncher.core.crash.CrashManager
import com.israadev.nuxlauncher.core.renderer.NuxRendererRegistry
import com.israadev.nuxlauncher.ui.activities.ErrorActivity
import com.israadev.nuxlauncher.ui.components.GameLoadingOverlay
import com.israadev.nuxlauncher.ui.dialogs.ExitConfirmDialog
import com.israadev.nuxlauncher.ui.dialogs.GameConsolePanel
import com.israadev.nuxlauncher.ui.dialogs.GameMenuSheet
import com.israadev.nuxlauncher.ui.i18n.Tr
import com.israadev.nuxlauncher.ui.screens.CustomGuiEditorScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.israadev.nuxlauncher.core.record.RecordService
import com.israadev.nuxlauncher.core.record.ScreenRecorder
import android.system.Os
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.HapticFeedbackConstants
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.israadev.nuxlauncher.core.controls.ControlLayoutManager
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import com.israadev.nuxlauncher.core.game.MCOptions
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.core.game.input.EfficientAndroidLWJGLKeycode
import com.israadev.nuxlauncher.core.game.input.HidableInputLayout
import com.israadev.nuxlauncher.core.game.input.TouchCharInput
import com.israadev.nuxlauncher.ui.components.NuxBadge
import com.israadev.nuxlauncher.ui.components.NuxButton
import com.israadev.nuxlauncher.ui.components.NuxCard
import com.israadev.nuxlauncher.core.device.DeviceProfiles
import com.israadev.nuxlauncher.core.device.PhysicalMouseChecker
import com.israadev.nuxlauncher.core.perf.GamePerfMonitor
import com.israadev.nuxlauncher.ui.control.GameControlsLayer
import com.israadev.nuxlauncher.ui.control.MouseControlMode
import com.israadev.nuxlauncher.ui.control.SwitchableMouseLayout
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import com.movtery.zalithlauncher.bridge.CURSOR_DISABLED
import com.movtery.zalithlauncher.bridge.CURSOR_ENABLED
import com.movtery.zalithlauncher.bridge.LoggerBridge
import com.movtery.zalithlauncher.bridge.ZLBridge
import com.movtery.zalithlauncher.bridge.ZLBridgeStates
import com.movtery.zalithlauncher.bridge.ZLNativeInvoker
import com.oracle.dalvik.VMLauncher
import com.movtery.zalithlauncher.game.sdl.SdlBridge
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLSurface
import org.lwjgl.glfw.CallbackBridge
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class GameActivity : ComponentActivity(), SurfaceHolder.Callback {

    companion object {
        const val EXTRA_INSTANCE_NAME = "extra_instance_name"
        const val EXTRA_MC_VERSION = "extra_mc_version"
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_UUID = "extra_uuid"
        const val EXTRA_GAME_DIR = "extra_game_dir"
        const val EXTRA_ASSETS_DIR = "extra_assets_dir"
        const val EXTRA_ASSET_INDEX = "extra_asset_index"
        const val EXTRA_MAIN_CLASS = "extra_main_class"
        const val EXTRA_CLASSPATH = "extra_classpath"
        const val EXTRA_RUNTIME_NAME = "extra_runtime_name"
        const val EXTRA_RUNTIME_HOME = "extra_runtime_home"
        const val EXTRA_LWJGL_NATIVES_DIR = "extra_lwjgl_natives_dir"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
        const val EXTRA_USER_TYPE = "extra_user_type"
        const val EXTRA_AUTHLIB_INJECTOR_PATH = "extra_authlib_injector_path"
        const val EXTRA_AUTHLIB_URL = "extra_authlib_url"
        const val EXTRA_USE_WRAPPER = "extra_use_wrapper"
        const val EXTRA_LOADER = "extra_loader"
        const val EXTRA_LOADER_VERSION = "extra_loader_version"
        const val EXTRA_INSTALLED_MODS = "extra_installed_mods"
    }

    private val liveLogs = mutableStateListOf<String>()
    private var isGameStarted = false
    private var surfaceHolderRef: SurfaceHolder? = null
    private val isControlVisibleState = mutableStateOf(true)
    private val isGameRenderingState = mutableStateOf(false)

    private var isManualExit = false
    private var sessionStartTime = System.currentTimeMillis()
    private var gyroSensorManager: SensorManager? = null
    private var gyroSensorListener: SensorEventListener? = null
    private var lastGyroTimestamp = 0L

    private fun startGyroscope() {
        if (!SettingsManager.settings.value.gyroscopeEnabled || gyroSensorListener != null) return
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (ZLBridgeStates.cursorMode.value != CURSOR_DISABLED) {
                    lastGyroTimestamp = 0L
                    return
                }
                val previousTimestamp = lastGyroTimestamp
                lastGyroTimestamp = event.timestamp
                if (previousTimestamp == 0L) return

                val elapsedSeconds = (event.timestamp - previousTimestamp) / 1_000_000_000f
                if (elapsedSeconds <= 0f || elapsedSeconds > 0.1f) return

                val xRate = event.values[0]
                val yRate = event.values[1]
                @Suppress("DEPRECATION")
                val rotation = windowManager.defaultDisplay.rotation
                val (screenXRate, screenYRate) = when (rotation) {
                    android.view.Surface.ROTATION_0 -> xRate to yRate
                    android.view.Surface.ROTATION_90 -> yRate to -xRate
                    android.view.Surface.ROTATION_180 -> -xRate to -yRate
                    android.view.Surface.ROTATION_270 -> -yRate to xRate
                    else -> xRate to yRate
                }
                val sensitivity = SettingsManager.settings.value.captureSensitivity / 100f
                val pixelsPerRadian = 400f * sensitivity
                val deltaX = -screenYRate * elapsedSeconds * pixelsPerRadian
                val deltaY = screenXRate * elapsedSeconds * pixelsPerRadian
                if (kotlin.math.abs(deltaX) >= 0.01f || kotlin.math.abs(deltaY) >= 0.01f) {
                    CallbackBridge.sendCursorDelta(deltaX, deltaY)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensorManager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_GAME)) {
            gyroSensorManager = sensorManager
            gyroSensorListener = listener
        }
    }

    private fun stopGyroscope() {
        gyroSensorListener?.let { listener ->
            gyroSensorManager?.unregisterListener(listener)
        }
        gyroSensorListener = null
        gyroSensorManager = null
        lastGyroTimestamp = 0L
    }

    override fun onResume() {
        super.onResume()
        startGyroscope()
    }

    override fun onPause() {
        stopGyroscope()
        super.onPause()
    }

    // ---- Rekam layar bawaan -------------------------------------------------------------
    // Alurnya: (izin mikrofon/notifikasi bila perlu) -> dialog izin tangkap layar sistem ->
    // RecordService (foreground) -> ScreenRecorder (encoder perangkat keras). Peluncur hasil
    // harus didaftarkan sebelum Activity mulai, jadi dibuat sebagai properti.
    private var askedRecordPermissions = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            val (w, h, dpi) = screenForRecording()
            RecordService.start(this, result.resultCode, data, w, h, dpi)
        } else {
            com.israadev.nuxlauncher.ui.i18n.Toast.makeText(
                this, "Izin merekam layar tidak diberikan", com.israadev.nuxlauncher.ui.i18n.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == false) {
            com.israadev.nuxlauncher.ui.i18n.Toast.makeText(
                this, "Izin mikrofon ditolak, merekam tanpa suara", com.israadev.nuxlauncher.ui.i18n.Toast.LENGTH_SHORT
            ).show()
        }
        launchProjectionPrompt()
    }

    /** Ketuk tombol Rekam di menu: berhenti bila sedang merekam, selain itu mulai alur izin. */
    private fun toggleRecording() {
        if (ScreenRecorder.isRecording) {
            ScreenRecorder.stop()
            return
        }
        val needed = ArrayList<String>()
        if (SettingsManager.settings.value.recordMic &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Minta izin tambahan paling banyak sekali per sesi game supaya tidak mengganggu.
        if (needed.isNotEmpty() && !askedRecordPermissions) {
            askedRecordPermissions = true
            recordPermissionLauncher.launch(needed.toTypedArray())
        } else {
            launchProjectionPrompt()
        }
    }

    private fun launchProjectionPrompt() {
        try {
            val mpm = getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        } catch (e: Throwable) {
            com.israadev.nuxlauncher.ui.i18n.Toast.makeText(
                this, "Perekam layar tidak tersedia di HP ini", com.israadev.nuxlauncher.ui.i18n.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** Lebar, tinggi (landscape), dan densitas layar asli untuk menentukan ukuran rekaman. */
    private fun screenForRecording(): Triple<Int, Int, Int> {
        val (rawW, rawH) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
        return Triple(maxOf(rawW, rawH), minOf(rawW, rawH), resources.displayMetrics.densityDpi)
    }

    // Keluar dari layar game (Home, aplikasi lain): jeda rekaman supaya layar lain tidak ikut
    // terekam dan encoder tidak bekerja sia-sia. Kembali ke game: lanjut.
    override fun onStart() {
        super.onStart()
        ScreenRecorder.release(ScreenRecorder.Hold.SCREEN)
    }

    override fun onStop() {
        ScreenRecorder.hold(ScreenRecorder.Hold.SCREEN)
        super.onStop()
    }

    private fun handleGameExit(exitCode: Int, isSignal: Boolean, errorDetail: String? = null) {
        // Pindahkan baris log yang masih mengantre supaya laporan crash memuat baris terakhirnya
        flushPendingLogs()

        // Cek apakah game keluar bersih secara normal atas instruksi user (klik quit game / pause exit)
        val isNormalExit = isManualExit || (exitCode == 0 && !isSignal)

        if (!isNormalExit) {
            // Ini adalah CRASH (JVM Exit code != 0, fatal signal, OOM, atau exception)
            val crashReportsDir = File(gameDirPath, "crash-reports")
            val latestCrashReport = if (crashReportsDir.exists() && crashReportsDir.isDirectory) {
                crashReportsDir.listFiles { f -> f.isFile && f.name.startsWith("crash-") && f.name.endsWith(".txt") }
                    ?.maxByOrNull { it.lastModified() }
            } else null

            val hasRecentCrashReport = latestCrashReport != null && (latestCrashReport.lastModified() >= sessionStartTime - 10000L)
            val logFile = File(filesDir, "latestlog.txt")
            val effectiveLogPath = if (hasRecentCrashReport && latestCrashReport != null) latestCrashReport.absolutePath else if (logFile.exists()) logFile.absolutePath else ""

            val finalExitCode = if (exitCode != 0) exitCode else 1

            // 1. Rekam di CrashManager agar MainActivity juga tahu jika dibuka kembali
            CrashManager.recordCrash(
                context = this,
                instanceName = instanceName,
                mcVersion = mcVersion,
                exitCode = finalExitCode,
                isSignal = isSignal,
                gameDirPath = gameDirPath,
                liveLogs = liveLogs.toList(),
                exceptionDetail = errorDetail,
                loader = loader,
                loaderVersion = loaderVersion,
                installedMods = installedMods
            )

            // 2. Langsung luncurkan ErrorActivity mandiri (persis seperti Zalith Launcher)
            try {
                ErrorActivity.showGameCrash(
                    context = this,
                    exitCode = finalExitCode,
                    isSignal = isSignal,
                    logPath = effectiveLogPath,
                    instanceName = instanceName,
                    mcVersion = mcVersion
                )
            } catch (e: Throwable) {
                try {
                    val launcherIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(launcherIntent)
                } catch (_: Throwable) {}
            }
        } else {
            // Normal Exit
            CrashManager.onGameSessionEnded(this)
            try {
                val launcherIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(launcherIntent)
            } catch (_: Throwable) {}
        }

        terminateGameProcess()
    }

    private fun terminateGameProcess() {
        // Selesaikan berkas rekaman dulu; proses ini dimatikan paksa di bawah.
        try { ScreenRecorder.stopBlocking() } catch (_: Throwable) {}
        try {
            isManualExit = true
            CrashManager.onGameSessionEnded(this)
            ZLBridge.releaseBridgeWindow()
            com.israadev.nuxlauncher.core.skin.OfflineSkinServerManager.stopServer()
            com.israadev.nuxlauncher.core.account.offline.OfflineYggdrasilServer.activeInstance?.stop()
        } catch (_: Throwable) {}
        finish()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onDestroy() {
        stopGyroscope()
        super.onDestroy()
        // Selesaikan berkas rekaman dulu; proses ini dimatikan paksa di bawah.
        try { ScreenRecorder.stopBlocking() } catch (_: Throwable) {}
        try {
            isManualExit = true
            CrashManager.onGameSessionEnded(this)
            ZLBridge.releaseBridgeWindow()
            com.israadev.nuxlauncher.core.skin.OfflineSkinServerManager.stopServer()
            com.israadev.nuxlauncher.core.account.offline.OfflineYggdrasilServer.activeInstance?.stop()
        } catch (_: Throwable) {}
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private var instanceName = "Minecraft"
    private var mcVersion = "Unknown"
    private var username = "Player"
    private var uuid = "00000000-0000-0000-0000-000000000000"
    private var gameDirPath = ""
    private var assetsDirPath = ""
    private var assetIndexId = ""
    private var mainClass = "net.minecraft.client.main.Main"
    private var classpath = ""
    private var runtimeName = "jre-17"
    private var runtimeHomePath = ""
    private var lwjglNativesDirPath = ""
    private var accessToken = "0"
    private var userType = "mojang"
    private var authlibInjectorPath: String? = null
    private var authlibUrl: String? = null
    private var useWrapper = false
    private var loader = "vanilla"
    private var loaderVersion: String? = null
    private var installedMods: List<String> = emptyList()

    private fun getScaledDisplayDimensions(): Pair<Int, Int> {
        val settings = SettingsManager.settings.value
        val ratio = (settings.resolutionRatio / 100f).coerceIn(0.35f, 1.5f)
        val (rawW, rawH) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
        // GameActivity adalah sensorLandscape, pastikan lebar horizontal selalu lebih besar dari tinggi vertikal
        val screenW = maxOf(rawW, rawH)
        val screenH = minOf(rawW, rawH)
        val width = (screenW * ratio).roundToInt()
        val height = (screenH * ratio).roundToInt()
        return Pair(width, height)
    }

    private fun applyDynamicResolution(newRatio: Int) {
        val current = SettingsManager.settings.value
        val updated = current.copy(resolutionRatio = newRatio)
        SettingsManager.updateSettings(this, updated)

        val (targetW, targetH) = getScaledDisplayDimensions()
        surfaceHolderRef?.setFixedSize(targetW, targetH)
        CallbackBridge.sendUpdateWindowSize(targetW, targetH)
        LoggerBridge.append("▷ [In-Game Resolution] Dinamis diperbarui ke $newRatio% (${targetW}x${targetH})")
    }

    private fun applyImmersiveFullscreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val params = window.attributes
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = params
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val decor = window.peekDecorView() ?: try { window.decorView } catch (_: Throwable) { null }
            if (decor != null) {
                val insetsController = WindowCompat.getInsetsController(window, decor)
                insetsController.hide(
                    WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
                )
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                @Suppress("DEPRECATION")
                decor.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
        } catch (_: Throwable) {}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveFullscreen()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PhysicalMouseChecker.initChecker(this)
        SettingsManager.init(this)
        ControlLayoutManager.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && SettingsManager.settings.value.sustainedPerformanceMode) {
            window.setSustainedPerformanceMode(true)
        }
        CallbackBridge.sContext = this

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            window.attributes = params
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        instanceName = intent.getStringExtra(EXTRA_INSTANCE_NAME) ?: "Minecraft"
        mcVersion = intent.getStringExtra(EXTRA_MC_VERSION) ?: "Unknown"
        username = intent.getStringExtra(EXTRA_USERNAME) ?: "Player"
        uuid = intent.getStringExtra(EXTRA_UUID) ?: "00000000-0000-0000-0000-000000000000"
        gameDirPath = intent.getStringExtra(EXTRA_GAME_DIR) ?: filesDir.absolutePath
        assetsDirPath = intent.getStringExtra(EXTRA_ASSETS_DIR) ?: File(filesDir, "minecraft/assets").absolutePath
        assetIndexId = intent.getStringExtra(EXTRA_ASSET_INDEX) ?: mcVersion
        mainClass = intent.getStringExtra(EXTRA_MAIN_CLASS) ?: "net.minecraft.client.main.Main"
        classpath = intent.getStringExtra(EXTRA_CLASSPATH) ?: ""
        runtimeName = intent.getStringExtra(EXTRA_RUNTIME_NAME) ?: "jre-17"
        runtimeHomePath = intent.getStringExtra(EXTRA_RUNTIME_HOME) ?: ""
        lwjglNativesDirPath = intent.getStringExtra(EXTRA_LWJGL_NATIVES_DIR) ?: ""
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN) ?: "0"
        userType = intent.getStringExtra(EXTRA_USER_TYPE) ?: "mojang"
        authlibInjectorPath = intent.getStringExtra(EXTRA_AUTHLIB_INJECTOR_PATH)
        authlibUrl = intent.getStringExtra(EXTRA_AUTHLIB_URL)
        useWrapper = intent.getBooleanExtra(EXTRA_USE_WRAPPER, false)
        loader = intent.getStringExtra(EXTRA_LOADER) ?: "vanilla"
        loaderVersion = intent.getStringExtra(EXTRA_LOADER_VERSION)
        installedMods = intent.getStringArrayListExtra(EXTRA_INSTALLED_MODS) ?: CrashManager.getInstalledMods(gameDirPath)

        // Setup real-time native LoggerBridge
        setupLogger()

        // Setup exit callback
        ZLNativeInvoker.onExitCallback = { exitCode, isSignal ->
            runOnUiThread {
                liveLogs.add("[Amber Engine] Game process ended (code: $exitCode, signal: $isSignal)")
                handleGameExit(exitCode, isSignal)
            }
        }

        // Setup Graphic Output Listener (Mendeteksi Logo Mojang mulai dirender)
        CallbackBridge.setGraphicOutputListener {
            runOnUiThread {
                isGameRenderingState.value = true
                liveLogs.add("🎨 [Render Engine] First graphical frame rendered on Surface!")
                val midX = (CallbackBridge.windowWidth.takeIf { it > 0 } ?: 1280) / 2f
                val midY = (CallbackBridge.windowHeight.takeIf { it > 0 } ?: 720) / 2f
                CallbackBridge.sendCursorPos(midX, midY)
            }
        }

        val composeView = ComposeView(this).apply {
            setContent {
                GameScreen(
                    instanceName = instanceName,
                    mcVersion = mcVersion,
                    username = username,
                    mainClass = mainClass,
                    runtimeName = runtimeName,
                    liveLogs = liveLogs,
                    isControlVisibleState = isControlVisibleState,
                    isGameRenderingState = isGameRenderingState,
                    onSurfaceReady = { surfaceHolder ->
                        surfaceHolderRef = surfaceHolder
                        val (targetWidth, targetHeight) = getScaledDisplayDimensions()
                        surfaceHolder.setFixedSize(targetWidth, targetHeight)
                        surfaceHolder.addCallback(this@GameActivity)
                    },
                    onResolutionChange = { newRatio ->
                        applyDynamicResolution(newRatio)
                    },
                    onToggleRecord = { toggleRecording() },
                    onExit = {
                        isManualExit = true
                        terminateGameProcess()
                    }
                )
            }
        }

        setContentView(composeView)
        applyImmersiveFullscreen()
        applyDeviceDisplayProfile()
    }

    /**
     * Profil perangkat (hanya HP yang terdaftar): kunci layar ke 60 Hz selama game berjalan.
     * Layar Oppo A3x 90 Hz sedangkan game di HP ini jarang mencapai 90 FPS; pada 20/30/45 FPS
     * ritme tampil di 90 Hz tidak rata (frame bergantian tampil 1 dan 2 kali) dan terasa
     * patah-patah. 60 Hz kelipatan 20/30 sehingga ritmenya rata, dan sedikit lebih hemat
     * panas. Bisa dimatikan di Pengaturan > Profil perangkat untuk dibandingkan.
     */
    private fun applyDeviceDisplayProfile() {
        val profile = DeviceProfiles.detect() ?: return
        val st = SettingsManager.settings.value
        if (!st.deviceProfileEnabled || !st.deviceProfileLockRefresh) return
        try {
            val params = window.attributes
            params.preferredRefreshRate = profile.refreshRateHz
            val modeId = DeviceProfiles.findDisplayModeId(this, profile.refreshRateHz)
            if (modeId != 0) {
                params.preferredDisplayModeId = modeId
            }
            window.attributes = params
            LoggerBridge.append("▷ [Layar] Meminta ${profile.refreshRateHz.toInt()} Hz (modeId=$modeId)")
        } catch (e: Throwable) {
            try {
                LoggerBridge.append("▷ [Layar] Gagal mengunci refresh rate: ${e.message}")
            } catch (_: Throwable) {
            }
        }
    }

    // Log game dari thread native ditampung dulu, lalu dipindah ke daftar tampilan paling cepat
    // tiap 250 ms. Sebelumnya SETIAP baris log memicu runOnUiThread + gambar ulang seluruh layar
    // kontrol; saat game ramai mencatat log (chat server, peringatan renderer, mod) thread utama
    // kewalahan dan FPS ikut turun. Berkas latestlog.txt tetap menerima semua baris (ditulis native).
    private val pendingLogs = java.util.concurrent.ArrayBlockingQueue<String>(2000)
    private val logFlushScheduled = java.util.concurrent.atomic.AtomicBoolean(false)
    private val logHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val logFlushRunnable = Runnable { flushPendingLogs() }

    private fun flushPendingLogs() {
        logFlushScheduled.set(false)
        var moved = 0
        while (moved < 500) {
            val line = pendingLogs.poll() ?: break
            moved++
            liveLogs.add(line)
            // Deteksi sekunder jika frame grafik atau sistem audio game mulai aktif
            if (!isGameRenderingState.value) {
                if (line.contains("OpenAL initialized") ||
                    line.contains("Reloading ResourceManager") ||
                    line.contains("Sound engine started") ||
                    line.contains("Setting user: ")) {
                    isGameRenderingState.value = true
                }
            }
        }
        while (liveLogs.size > 200) {
            liveLogs.removeAt(0)
        }
        // Masih ada sisa (lonjakan log): jadwalkan putaran berikutnya.
        if (!pendingLogs.isEmpty() && logFlushScheduled.compareAndSet(false, true)) {
            logHandler.postDelayed(logFlushRunnable, 250L)
        }
    }

    private fun setupLogger() {
        val logFile = File(filesDir, "latestlog.txt")
        if (logFile.exists()) logFile.delete()
        logFile.createNewFile()

        LoggerBridge.setListener { text ->
            if (text != null) {
                pendingLogs.offer(text)
                if (logFlushScheduled.compareAndSet(false, true)) {
                    logHandler.postDelayed(logFlushRunnable, 250L)
                }
            }
        }

        try {
            LoggerBridge.start(logFile.absolutePath)
            LoggerBridge.appendTitle("AmberLauncher Game Session")
            LoggerBridge.append("▷ Starting $instanceName ($mcVersion) on $runtimeName")
        } catch (e: Throwable) {
            liveLogs.add("[LoggerBridge Init] ${e.message}")
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val (targetWidth, targetHeight) = getScaledDisplayDimensions()
        holder.setFixedSize(targetWidth, targetHeight)
        val surface = holder.surface
        // Profil perangkat: beri tahu sistem bahwa surface game cukup 60 Hz (Android 11+)
        val displayProfile = DeviceProfiles.detect()
        if (displayProfile != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val st = SettingsManager.settings.value
            if (st.deviceProfileEnabled && st.deviceProfileLockRefresh) {
                try {
                    surface.setFrameRate(displayProfile.refreshRateHz, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                } catch (_: Throwable) {
                }
            }
        }
        val rootLayout = window.decorView as? ViewGroup
        SdlBridge.prepareSurface(this, surface, rootLayout, holder)
        try {
            ZLBridge.setupBridgeWindow(surface)
            liveLogs.add("[Render Bridge] Native window bound to SurfaceView (${targetWidth}x${targetHeight}).")
        } catch (e: Throwable) {
            liveLogs.add("[Render Bridge Error] ${e.message}")
        }

        if (!isGameStarted) {
            isGameStarted = true
            startGameJVM()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val landscapeW = maxOf(width, height)
        val landscapeH = minOf(width, height)
        CallbackBridge.sendUpdateWindowSize(landscapeW, landscapeH)
        LoggerBridge.append("▷ [SurfaceChanged] Native surface buffer: ${landscapeW}x${landscapeH} (Scale: ${SettingsManager.settings.value.resolutionRatio}%)")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        try {
            val nativeSurface = SDLSurface.getNativeSurface()
            if (SdlBridge.beginSurfaceDestroy(holder, nativeSurface)) {
                if (SdlBridge.sdlEnabled) {
                    SDLActivity.getSDLSurface()?.surfaceDestroyed()
                }
                SdlBridge.unregisterSurface(nativeSurface)
            }
            ZLBridge.releaseBridgeWindow()
        } catch (_: Throwable) {}
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // If soft keyboard IME input view is active, allow standard handling
        if (TouchCharInput.isActive()) {
            return super.dispatchKeyEvent(event)
        }

        // Intercept right click from mouse being treated as KEYCODE_BACK
        val source = event.source
        if (source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE ||
            source and InputDevice.SOURCE_MOUSE_RELATIVE == InputDevice.SOURCE_MOUSE_RELATIVE) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                val isDown = event.action == KeyEvent.ACTION_DOWN
                CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT, isDown)
                return true
            }
        }

        // Physical keyboard key pressed -> automatically hide touch controls
        if (event.action == KeyEvent.ACTION_DOWN) {
            isControlVisibleState.value = false
        }

        val index = EfficientAndroidLWJGLKeycode.getIndexByKey(event.keyCode)
        if (index >= 0) {
            EfficientAndroidLWJGLKeycode.execKey(event, index)
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE) || event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)) {
            // Physical mouse action detected -> hide touch controls
            isControlVisibleState.value = false
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE) || event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)) {
            isControlVisibleState.value = false
        }
        return super.dispatchTouchEvent(event)
    }

    private fun startGameJVM() {
        thread(name = "Amber-JVM-Thread") {
            try {
                val nativeLibDir = applicationInfo.nativeLibraryDir
                val runtimeHome = File(runtimeHomePath)
                val gameDir = File(gameDirPath)
                val assetsDir = File(assetsDirPath)

                liveLogs.add("[AmberLauncher] Menyiapkan environment JVM...")

                val activeSettings = SettingsManager.settings.value
                val (targetWidth, targetHeight) = getScaledDisplayDimensions()

                // Jarak pandang awal mengikuti RAM HP. HP 4 GB ke bawah tetap 2 chunk seperti biasa;
                // HP yang lebih besar mendapat nilai lebih lega. Hanya dipakai bila options.txt belum
                // punya nilainya (pertama kali instance dijalankan), jadi pilihan pemain tidak ditimpa.
                val totalRamForOptions = SettingsManager.getTotalDeviceMemoryMb(this@GameActivity)
                val defaultRenderDistance = when {
                    totalRamForOptions >= 7000 -> "6"
                    totalRamForOptions >= 5000 -> "4"
                    else -> "2"
                }

                // Setup & Optimize Minecraft options.txt (Zalith pure behavior)
                MCOptions.setup(this@GameActivity, gameDir)
                MCOptions.apply {
                    set("fullscreen", "false")
                    set("touchscreen", "false")
                    set("options.narrator", "0")
                    set("narrator", "0")

                    // Ensure default low-end mobile performance options if enabled in settings
                    if (activeSettings.autoOptimizeMinecraft) {
                        if (!containsKey("clouds")) set("clouds", "false")
                        if (!containsKey("renderClouds")) set("renderClouds", "false")
                        if (!containsKey("entityShadows")) set("entityShadows", "false")
                        if (!containsKey("renderDistance")) set("renderDistance", defaultRenderDistance)
                        if (!containsKey("simulationDistance")) set("simulationDistance", "5")
                    }

                    set("overrideWidth", "$targetWidth")
                    set("overrideHeight", "$targetHeight")
                    save()
                }
                LoggerBridge.append("▷ [MCOptions] Minecraft options verified (res=${targetWidth}x${targetHeight}, autoOptimize=${activeSettings.autoOptimizeMinecraft})")

                // Verify & Ensure OpenJDK Runtime is fully installed
                if (!com.israadev.nuxlauncher.core.runtime.JavaRuntimeManager.isRuntimeInstalled(this@GameActivity, runtimeName)) {
                    liveLogs.add("[Amber Runtime] Mempersiapkan OpenJDK ($runtimeName)...")
                    LoggerBridge.append("▷ [Amber Runtime] Runtime $runtimeName missing or incomplete, extracting from assets...")
                    val extRes = kotlinx.coroutines.runBlocking {
                        com.israadev.nuxlauncher.core.runtime.JavaRuntimeManager.extractRuntime(this@GameActivity, runtimeName) { msg ->
                            liveLogs.add("[Amber Runtime] $msg")
                            LoggerBridge.append("▷ [Amber Runtime] $msg")
                        }
                    }
                    if (extRes.isFailure) {
                        val errMsg = extRes.exceptionOrNull()?.message ?: "Extraction failed"
                        liveLogs.add("[ERROR] Gagal mengekstrak OpenJDK $runtimeName: $errMsg")
                        LoggerBridge.append("[ERROR] Gagal mengekstrak OpenJDK: $errMsg")
                        throw Exception("Gagal menyiapkan OpenJDK ($runtimeName): $errMsg")
                    }
                    liveLogs.add("[Amber Runtime] OpenJDK ($runtimeName) siap digunakan!")
                }

                // Combined Library Path (Include Java runtime libraries for pojavexec dlopen)
                val javaLibDir = File(runtimeHome, "lib")
                val javaServerDir = File(runtimeHome, "lib/server")
                val javaJliDir = File(runtimeHome, "lib/jli")

                val javaLibPaths = listOf(javaLibDir, javaServerDir, javaJliDir)
                    .filter { it.exists() }
                    .map { it.absolutePath }

                // Resolve Selected Graphics Renderer
                try {
                    com.israadev.nuxlauncher.core.renderer.NuxRendererPluginManager.scanPlugins(this)
                } catch (e: Throwable) {
                    LoggerBridge.append("▷ [Renderer] Plugin scan error: ${e.message}")
                }

                val targetRenderer = NuxRendererRegistry.resolveRenderer(
                    selectedId = activeSettings.selectedRenderer,
                    mcVersion = mcVersion,
                    nativeLibDir = File(nativeLibDir),
                    context = this
                )
                val rendererId = targetRenderer.rendererId
                val rendererSoName = targetRenderer.libraryName

                LoggerBridge.append("▷ [Renderer] Active Backend: ${targetRenderer.displayName} ($rendererId)")
                LoggerBridge.append("▷ [Renderer] Library: $rendererSoName, Selection: ${activeSettings.selectedRenderer}, IsPlugin: ${targetRenderer.isPlugin}")

                val runtimeHomeStr = runtimeHome.absolutePath
                // Ditentukan dari bitness proses ini (bukan daftar ABI HP): APK 32-bit di HP 64-bit
                // tetap perlu /system/lib, bukan /system/lib64.
                val libDirName = if (android.os.Process.is64Bit()) "lib64" else "lib"

                // 1. Runtime linker library path (Dipakai oleh ZLBridge.setLdLibraryPath untuk memperbarui Android Bionic Linker)
                val runtimeLdPaths = mutableListOf<String>()
                if (targetRenderer.isPlugin && !targetRenderer.pluginNativePath.isNullOrBlank()) {
                    runtimeLdPaths.add(targetRenderer.pluginNativePath!!)
                }
                // Deteksi subdirektori arsitektur Java 8 (misal lib/aarch64/)
                val archSubDir = listOf("aarch64", "aarch32", "arm", "i386", "amd64", "x86_64")
                    .map { File("$runtimeHomeStr/lib", it) }
                    .firstOrNull { it.exists() && it.isDirectory }

                if (archSubDir != null) {
                    runtimeLdPaths.add("${archSubDir.absolutePath}/jli")
                    runtimeLdPaths.add("${archSubDir.absolutePath}/server")
                    runtimeLdPaths.add("${archSubDir.absolutePath}/client")
                    runtimeLdPaths.add(archSubDir.absolutePath)
                }

                runtimeLdPaths.add("$runtimeHomeStr/lib/jli")
                if (File("$runtimeHomeStr/jre").exists()) {
                    runtimeLdPaths.add("$runtimeHomeStr/jre/lib/server")
                    runtimeLdPaths.add("$runtimeHomeStr/jre/lib")
                } else {
                    runtimeLdPaths.add("$runtimeHomeStr/lib/server")
                    runtimeLdPaths.add("$runtimeHomeStr/lib")
                }
                if (lwjglNativesDirPath.isNotBlank()) runtimeLdPaths.add(lwjglNativesDirPath)
                runtimeLdPaths.add(nativeLibDir)
                runtimeLdPaths.add("/system/$libDirName")
                runtimeLdPaths.add("/vendor/$libDirName")
                runtimeLdPaths.add("/vendor/$libDirName/hw")
                runtimeLdPaths.add("/system_ext/$libDirName")

                val runtimeLdLibraryPath = runtimeLdPaths.distinct().joinToString(":")
                ZLBridge.setLdLibraryPath(runtimeLdLibraryPath)

                // 2. Game LD_LIBRARY_PATH (Identik dengan ZalithLauncher2 getLibraryPath() - JANGAN SAMPAI ADA FOLDER JRE lib/server / lib/jli!)
                // Jika LD_LIBRARY_PATH mengandung 'lib/server' atau 'lib/client', OpenJDK libjli.so akan mengira perlu re-exec
                // dan memanggil execve(bin/java) yang otomatis ditolak Android kernel (Permission denied)!
                val gameLdPaths = mutableListOf<String>()
                if (lwjglNativesDirPath.isNotBlank()) gameLdPaths.add(lwjglNativesDirPath)
                gameLdPaths.add("/system/$libDirName")
                gameLdPaths.add("/vendor/$libDirName")
                gameLdPaths.add("/vendor/$libDirName/hw")
                gameLdPaths.add("/system_ext/$libDirName")
                if (targetRenderer.isPlugin && !targetRenderer.pluginNativePath.isNullOrBlank()) {
                    gameLdPaths.add(targetRenderer.pluginNativePath!!)
                }
                gameLdPaths.add(nativeLibDir)
                val gameLdLibraryPath = gameLdPaths.distinct().joinToString(":")

                val glLibPath = if (targetRenderer.isPlugin && !targetRenderer.pluginNativePath.isNullOrBlank()) {
                    if (rendererSoName.startsWith("/")) rendererSoName else "${targetRenderer.pluginNativePath}/$rendererSoName"
                } else {
                    "$nativeLibDir/$rendererSoName"
                }

                val actualPojavRenderer = when {
                    rendererId.startsWith("opengles") -> rendererId
                    rendererId.startsWith("vulkan") -> rendererId
                    rendererId.startsWith("gallium") -> rendererId
                    rendererId == "custom_gallium" -> rendererId
                    else -> "opengles3"
                }
                Os.setenv("POJAV_NATIVEDIR", nativeLibDir, true)
                Os.setenv("POJAV_RENDERER", actualPojavRenderer, true)
                Os.setenv("POJAV_SDL_REUSE_WINDOW", "1", true)
                Os.setenv("SDL_OPENGL_LIBRARY", glLibPath, true)

                // Apply renderer specific environment variables
                targetRenderer.envVariables.forEach { (k, v) ->
                    Os.setenv(k, v, true)
                }

                // Apply Renderer V2 Environment Variables (persis Zalith fclPlugin_V2 & MobileGlues)
                try {
                    val v2Envs = com.israadev.nuxlauncher.core.renderer.v2.NuxRendererV2Manager.getEffectiveEnv(targetRenderer)
                    v2Envs.forEach { (k, v) ->
                        Os.setenv(k, v, true)
                        LoggerBridge.append("▷ [Renderer V2 Env] $k = $v")
                    }
                } catch (e: Exception) {
                    LoggerBridge.append("▷ [Renderer V2 Env Warning] Gagal menginjeksi V2 env: ${e.message}")
                }

                // Pastikan flag GLSL & Extension compatibility selalu aktif untuk non-GL4ES renderers
                // (Standar kompatibilitas Zalith Launcher 2 untuk MobileGL, Zink, Mesa, dan Custom Renderer)
                val isPureLegacyGl4es = targetRenderer.id == "gl4es" || targetRenderer.rendererId == "opengles2"
                if (!isPureLegacyGl4es) {
                    Os.setenv("allow_higher_compat_version", "true", true)
                    Os.setenv("allow_glsl_extension_directive_midshader", "true", true)
                    Os.setenv("force_glsl_extensions_warn", "true", true)
                    LoggerBridge.append("▷ [GLSL Compat] Injected allow_glsl_extension_directive_midshader=true, allow_higher_compat_version=true, force_glsl_extensions_warn=true")
                }

                // Apply EGL specific library path if specified (full absolute path for plugins)
                val eglPath = if (!targetRenderer.eglName.isNullOrBlank()) {
                    val rawEgl = targetRenderer.eglName!!
                    if (rawEgl.startsWith("/")) rawEgl
                    else if (targetRenderer.isPlugin && !targetRenderer.pluginNativePath.isNullOrBlank()) "${targetRenderer.pluginNativePath}/$rawEgl"
                    else "$nativeLibDir/$rawEgl"
                } else null

                if (!eglPath.isNullOrBlank()) {
                    Os.setenv("POJAVEXEC_EGL", eglPath, true)
                    Os.setenv("SDL_EGL_LIBRARY", eglPath, true)
                }

                // Apply Vulkan / Zink / Graphics API settings
                if (activeSettings.zinkPreferSystemDriver) {
                    Os.setenv("POJAV_ZINK_PREFER_SYSTEM_DRIVER", "1", true)
                    LoggerBridge.append("▷ [Renderer Config] POJAV_ZINK_PREFER_SYSTEM_DRIVER=1")
                }
                if (activeSettings.vsyncInZink) {
                    Os.setenv("POJAV_VSYNC_IN_ZINK", "1", true)
                    LoggerBridge.append("▷ [Renderer Config] POJAV_VSYNC_IN_ZINK=1")
                }
                if (activeSettings.graphicsApi != "DEFAULT") {
                    Os.setenv("POJAV_GRAPHICS_API", activeSettings.graphicsApi, true)
                    LoggerBridge.append("▷ [Renderer Config] POJAV_GRAPHICS_API=${activeSettings.graphicsApi}")
                }
                if (activeSettings.vulkanDriver != "auto" && activeSettings.vulkanDriver.isNotBlank()) {
                    Os.setenv("POJAV_VULKAN_DRIVER", activeSettings.vulkanDriver, true)
                    LoggerBridge.append("▷ [Renderer Config] POJAV_VULKAN_DRIVER=${activeSettings.vulkanDriver}")
                }

                // Pre-dlopen plugin libraries from the plugin's own directory ONLY
                if (targetRenderer.isPlugin && !targetRenderer.pluginNativePath.isNullOrBlank()) {
                    val pluginDir = File(targetRenderer.pluginNativePath)
                    targetRenderer.dlopenLibs.forEach { dlName ->
                        val dlFile = File(pluginDir, dlName)
                        if (dlFile.exists()) {
                            try {
                                ZLBridge.dlopen(dlFile.absolutePath)
                                LoggerBridge.append("▷ [Plugin DLOPEN] $dlName")
                            } catch (e: Throwable) {
                                LoggerBridge.append("▷ [Plugin DLOPEN Warning] $dlName: ${e.message}")
                            }
                        }
                    }
                    val targetSo = File(glLibPath)
                    if (targetSo.exists()) {
                        try {
                            ZLBridge.dlopen(targetSo.absolutePath)
                            LoggerBridge.append("▷ [Plugin GL Loaded] ${targetSo.absolutePath}")
                        } catch (e: Throwable) {
                            LoggerBridge.append("▷ [Plugin GL Warning] ${targetRenderer.libraryName}: ${e.message}")
                        }
                    }
                    if (!eglPath.isNullOrBlank() && eglPath != glLibPath) {
                        val eglFile = File(eglPath)
                        if (eglFile.exists()) {
                            try {
                                ZLBridge.dlopen(eglFile.absolutePath)
                                LoggerBridge.append("▷ [Plugin EGL Loaded] ${eglFile.absolutePath}")
                            } catch (e: Throwable) {
                                LoggerBridge.append("▷ [Plugin EGL Warning]: ${e.message}")
                            }
                        }
                    }
                }

                // Mesa GLSL Cache Directory
                if (rendererId.startsWith("gallium") || rendererId.contains("zink")) {
                    Os.setenv("MESA_GLSL_CACHE_DIR", cacheDir.absolutePath, true)
                }
                Os.setenv("JAVA_HOME", runtimeHome.absolutePath, true)
                Os.setenv("HOME", gameDir.absolutePath, true)
                Os.setenv("TMPDIR", cacheDir.absolutePath, true)
                Os.setenv("PATH", "${runtimeHome.absolutePath}/bin:" + (Os.getenv("PATH") ?: "/system/bin"), true)
                Os.setenv("LD_LIBRARY_PATH", gameLdLibraryPath, true)
                Os.setenv("AWTSTUB_WIDTH", "$targetWidth", true)
                Os.setenv("AWTSTUB_HEIGHT", "$targetHeight", true)
                Os.setenv("ALSOFT_DRIVERS", "opensl", true)

                // Android DNS Resolver Setup (Direct match with Zalith Launcher 2 & Pojav)
                val resolvConf = File(gameDir, "resolv.conf")
                val dnsServers = buildSet {
                    try {
                        val cm = getSystemService(android.net.ConnectivityManager::class.java)
                        val activeNet = cm?.activeNetwork
                        if (activeNet != null) {
                            val lp = cm.getLinkProperties(activeNet)
                            lp?.dnsServers
                                ?.mapNotNull { it.hostAddress?.takeIf(String::isNotEmpty) }
                                ?.filterNot { it.contains(':') }
                                ?.let { addAll(it) }
                        }
                    } catch (_: Throwable) {}
                    add("1.1.1.1")
                    add("1.0.0.1")
                }
                val configText = dnsServers.joinToString(separator = "\n") { "nameserver $it" }
                runCatching {
                    if (!resolvConf.exists() || resolvConf.readText().trim() != configText.trim()) {
                        resolvConf.writeText(configText)
                    }
                    val altResolv = File(filesDir, "resolv.conf")
                    if (!altResolv.exists() || altResolv.readText().trim() != configText.trim()) {
                        altResolv.writeText(configText)
                    }
                }
                try {
                    Os.setenv("RESOLV_CONF", resolvConf.absolutePath, true)
                } catch (_: Throwable) {}

                // Pre-dlopen Java Runtime core libraries (Identical to Zalith Launcher 2 dlopenJavaRuntime)
                val isJava8 = File(runtimeHome, "jre").exists() || File(runtimeHome, "lib/rt.jar").exists() || File(runtimeHome, "lib/rt.jar.pack").exists()
                val rtLibDir = if (File(runtimeHome, "jre/lib").exists()) File(runtimeHome, "jre/lib") else File(runtimeHome, "lib")
                val archLibDir = listOf("aarch64", "aarch32", "arm", "i386", "amd64", "x86_64")
                    .map { File(rtLibDir, it) }
                    .firstOrNull { it.exists() && it.isDirectory } ?: rtLibDir

                val rtJliDir = when {
                    File(rtLibDir, "jli/libjli.so").exists() -> File(rtLibDir, "jli")
                    File(archLibDir, "jli/libjli.so").exists() -> File(archLibDir, "jli")
                    else -> rtLibDir
                }
                val rtJvmDir = when {
                    File(rtLibDir, "server/libjvm.so").exists() -> File(rtLibDir, "server")
                    File(rtLibDir, "client/libjvm.so").exists() -> File(rtLibDir, "client")
                    File(archLibDir, "server/libjvm.so").exists() -> File(archLibDir, "server")
                    File(archLibDir, "client/libjvm.so").exists() -> File(archLibDir, "client")
                    else -> rtLibDir
                }

                val essentialLibs = listOf(
                    File(rtJliDir, "libjli.so"),
                    File(rtJvmDir, "libjvm.so"),
                    File(rtLibDir, "libfreetype.so"),
                    File(archLibDir, "libfreetype.so"),
                    File(rtLibDir, "libverify.so"),
                    File(archLibDir, "libverify.so"),
                    File(rtLibDir, "libjava.so"),
                    File(archLibDir, "libjava.so"),
                    File(rtLibDir, "libnet.so"),
                    File(archLibDir, "libnet.so"),
                    File(rtLibDir, "libnio.so"),
                    File(archLibDir, "libnio.so"),
                    File(rtLibDir, "libawt.so"),
                    File(archLibDir, "libawt.so"),
                    File(rtLibDir, "libawt_headless.so"),
                    File(archLibDir, "libawt_headless.so"),
                    File(rtLibDir, "libfontmanager.so"),
                    File(archLibDir, "libfontmanager.so")
                )
                essentialLibs.forEach { so ->
                    if (so.exists()) {
                        try {
                            ZLBridge.dlopen(so.absolutePath)
                            LoggerBridge.append("▷ [Pre-dlopen] Loaded ${so.name}")
                        } catch (e: Throwable) {
                            LoggerBridge.append("▷ [Pre-dlopen Warning] ${so.name}: ${e.message}")
                        }
                    }
                }

                // Pre-dlopen all remaining runtime shared libraries (.so) as Zalith does
                runtimeHome.walkTopDown().filter { it.isFile && it.name.endsWith(".so") }.forEach { so ->
                    try {
                        ZLBridge.dlopen(so.absolutePath)
                    } catch (_: Throwable) {}
                }

                // Pre-dlopen OpenAL engine (Matches Zalith dlopenEngine)
                val openalFile = File(nativeLibDir, "libopenal.so")
                if (openalFile.exists()) {
                    try {
                        ZLBridge.dlopen(openalFile.absolutePath)
                        LoggerBridge.append("▷ [Pre-dlopen] Loaded libopenal.so")
                    } catch (_: Throwable) {}
                }

                // Pre-dlopen internal renderer ONLY if NOT a plugin (prevents overriding plugin libraries with internal ones)
                if (!targetRenderer.isPlugin) {
                    try {
                        val rendererFile = File(nativeLibDir, rendererSoName)
                        if (rendererFile.exists()) {
                            ZLBridge.dlopen(rendererFile.absolutePath)
                            LoggerBridge.append("▷ [Pre-dlopen] Loaded internal renderer: $rendererSoName")
                        } else {
                            LoggerBridge.append("▷ [Pre-dlopen Warning] Renderer file not found: $rendererSoName")
                        }
                    } catch (e: Throwable) {
                        LoggerBridge.append("▷ [Pre-dlopen Warning] Renderer $rendererSoName: ${e.message}")
                    }

                    if (!eglPath.isNullOrBlank() && eglPath != glLibPath) {
                        val eglFile = File(eglPath)
                        if (eglFile.exists()) {
                            try {
                                ZLBridge.dlopen(eglFile.absolutePath)
                                LoggerBridge.append("▷ [Pre-dlopen] Loaded internal EGL: ${eglFile.name}")
                            } catch (_: Throwable) {}
                        }
                    }
                }

                // Pre-dlopen SPIRV-Cross if available
                val spirvLibFile = File(nativeLibDir, "libspirv-cross-c-shared.so")
                if (spirvLibFile.exists()) {
                    try {
                        ZLBridge.dlopen(spirvLibFile.absolutePath)
                        LoggerBridge.append("▷ [Pre-dlopen] Loaded libspirv-cross-c-shared.so")
                    } catch (e: Throwable) {
                        LoggerBridge.append("▷ [Pre-dlopen Warning] spirv: ${e.message}")
                    }
                }

                // Pre-dlopen LWJGL natives
                if (lwjglNativesDirPath.isNotBlank()) {
                    val lwjglDir = File(lwjglNativesDirPath)
                    listOf("liblwjgl.so", "liblwjgl_opengl.so", "liblwjgl_stb.so", "liblwjgl_tinyfd.so", "libfreetype.so", "libshaderc.so", "liblwjgl_vma.so", "libspirv-cross.so").forEach { libName ->
                        val so = File(lwjglDir, libName)
                        if (so.exists()) {
                            try {
                                ZLBridge.dlopen(so.absolutePath)
                                LoggerBridge.append("▷ [Pre-dlopen] Loaded ${so.name}")
                            } catch (e: Throwable) {
                                LoggerBridge.append("▷ [Pre-dlopen Warning] ${so.name}: ${e.message}")
                            }
                        }
                    }
                }

                ZLBridge.setupExitMethod(this@GameActivity)
                ZLBridge.initializeGameExitHook()
                ZLBridge.chdir(gameDir.absolutePath)

                // Build JVM Launch Arguments
                val effectiveLwjglDir = if (lwjglNativesDirPath.isNotBlank()) lwjglNativesDirPath else nativeLibDir
                val freetypeLib = if (lwjglNativesDirPath.isNotBlank() && File(lwjglNativesDirPath, "libfreetype.so").exists()) {
                    File(lwjglNativesDirPath, "libfreetype.so").absolutePath
                } else {
                    "$nativeLibDir/libfreetype.so"
                }

                val spirvLibPath = if (lwjglNativesDirPath.isNotBlank() && File(lwjglNativesDirPath, "libspirv-cross.so").exists()) {
                    File(lwjglNativesDirPath, "libspirv-cross.so").absolutePath
                } else if (File(nativeLibDir, "libspirv-cross-c-shared.so").exists()) {
                    File(nativeLibDir, "libspirv-cross-c-shared.so").absolutePath
                } else {
                    "libspirv-cross-c-shared.so"
                }

                // Pastikan permission executable (0755) pada bin/java dan runtime native libraries
                // (hanya dikerjakan penuh sekali per instalasi runtime; selanjutnya cukup cek satu penanda)
                com.israadev.nuxlauncher.core.runtime.JavaRuntimeManager.ensureExecutablePermissionsOnce(runtimeHome)

                val jvmArgs = mutableListOf<String>()
                jvmArgs.add("${runtimeHome.absolutePath}/bin/java")
                jvmArgs.add("-Djava.home=${runtimeHome.absolutePath}")
                jvmArgs.add("-Djava.io.tmpdir=${cacheDir.absolutePath}")
                jvmArgs.add("-Djava.library.path=$gameLdLibraryPath")
                jvmArgs.add("-Dorg.lwjgl.librarypath=$effectiveLwjglDir")
                jvmArgs.add("-Dorg.lwjgl.opengl.libname=$glLibPath")
                jvmArgs.add("-Dorg.lwjgl.openal.libname=$nativeLibDir/libopenal.so")
                jvmArgs.add("-Dorg.lwjgl.vulkan.libname=libvulkan.so")
                jvmArgs.add("-Dorg.lwjgl.freetype.libname=$freetypeLib")
                jvmArgs.add("-Dorg.lwjgl.spvc.libname=$spirvLibPath")
                jvmArgs.add("-Dorg.lwjgl.spvc.defaultname=spirv-cross-c-shared")
                jvmArgs.add("-Dorg.lwjgl.system.allocator=system")
                jvmArgs.add("-Djna.boot.library.path=$nativeLibDir")
                jvmArgs.add("-Dglfwstub.windowWidth=$targetWidth")
                jvmArgs.add("-Dglfwstub.windowHeight=$targetHeight")
                jvmArgs.add("-Dglfwstub.initEgl=false")
                jvmArgs.add("-Dos.name=Linux")
                jvmArgs.add("-Dos.version=Android-${Build.VERSION.RELEASE}")
                jvmArgs.add("-Duser.home=${gameDir.parentFile?.absolutePath ?: gameDir.absolutePath}")
                jvmArgs.add("-Dnet.minecraft.clientmodname=AmberLauncher")
                jvmArgs.add("-Dlog4j2.formatMsgNoLookups=true")
                jvmArgs.add("-Djava.rmi.server.useCodebaseOnly=true")
                jvmArgs.add("-Dcom.sun.jndi.rmi.object.trustURLCodebase=false")
                jvmArgs.add("-Dcom.sun.jndi.cosnaming.object.trustURLCodebase=false")
                jvmArgs.add("-Dfml.earlyprogresswindow=false")
                jvmArgs.add("-Dfml.ignoreInvalidMinecraftCertificates=true")
                jvmArgs.add("-Dfml.ignorePatchDiscrepancies=true")
                jvmArgs.add("-Dloader.disable_forked_guis=true")
                jvmArgs.add("-Djdk.lang.Process.launchMechanism=FORK")
                jvmArgs.add("-Dsodium.checks.issue2561=false")
                jvmArgs.add("-Dfile.encoding=UTF-8")
                jvmArgs.add("-Dsun.stdout.encoding=UTF-8")
                jvmArgs.add("-Dsun.stderr.encoding=UTF-8")

                // High-performance Android Network & Netty Socket stabilization (Direct match with Zalith)
                jvmArgs.add("-Dext.net.resolvPath=${resolvConf.absolutePath}")
                jvmArgs.add("-Djava.net.preferIPv4Stack=true")
                jvmArgs.add("-Djava.net.preferIPv6Addresses=false")
                jvmArgs.add("-Dio.netty.native.workdir=${cacheDir.absolutePath}")
                jvmArgs.add("-Djna.tmpdir=${cacheDir.absolutePath}")
                jvmArgs.add("-Dorg.lwjgl.system.SharedLibraryExtractPath=${cacheDir.absolutePath}")
                jvmArgs.add("-Dio.netty.tryReflectionSetAccessible=true")

                jvmArgs.add("-XX:ActiveProcessorCount=${Runtime.getRuntime().availableProcessors()}")

                // Profil khusus perangkat: hanya HP yang terdaftar (Oppo A3x 4G). Di HP lain
                // deviceProfile = null, jadi heap dan flag JVM di bawah sama persis seperti biasa.
                val deviceProfile = DeviceProfiles.detect()
                val deviceTotalRamMb = SettingsManager.getTotalDeviceMemoryMb(this)
                var heapInitMb = activeSettings.initialHeapMb
                var heapMaxMb = activeSettings.ramMb
                var profileFlags: List<String> = emptyList()
                LoggerBridge.append(
                    "▷ [Perangkat] ${DeviceProfiles.describeDevice()} | profil khusus: ${deviceProfile?.id ?: "tidak ada"}"
                )
                if (deviceProfile != null) {
                    if (activeSettings.deviceProfileEnabled) {
                        val gcMode = DeviceProfiles.normalizeGcMode(activeSettings.deviceProfileGcMode)
                        val heap = DeviceProfiles.resolveHeap(
                            deviceProfile, activeSettings.initialHeapMb, activeSettings.ramMb, deviceTotalRamMb, gcMode
                        )
                        heapInitMb = heap.first
                        heapMaxMb = heap.second
                        profileFlags = DeviceProfiles.jvmFlags(gcMode, activeSettings.customJvmArgs)
                        LoggerBridge.append(
                            "▷ [Profil] ${deviceProfile.displayName} AKTIF | GC=$gcMode | " +
                                "heap diminta ${activeSettings.ramMb} MB, dipakai $heapMaxMb MB | " +
                                "flag GC: ${if (profileFlags.isEmpty()) "(bawaan JVM)" else profileFlags.joinToString(" ")}"
                        )
                    } else {
                        LoggerBridge.append("▷ [Profil] ${deviceProfile.displayName} dikenali, profil DIMATIKAN di Pengaturan")
                    }
                } else {
                    // HP tanpa profil khusus: heap awal disamakan dengan heap maksimum (GC tidak perlu
                    // membesarkan heap sedikit demi sedikit) dan, di HP RAM kecil, generasi muda G1
                    // dibatasi supaya RAM tidak membengkak. Java 8 tidak diubah.
                    heapInitMb = DeviceProfiles.generalInitialHeapMb(activeSettings.initialHeapMb, heapMaxMb, isJava8)
                    profileFlags = DeviceProfiles.generalJvmFlags(deviceTotalRamMb, isJava8, activeSettings.customJvmArgs)
                }
                if (!android.os.Process.is64Bit()) {
                    // Proses 32-bit hanya punya ruang alamat 3-4 GB, dan sebagian sudah terpakai
                    // pustaka dan driver grafis. Heap lebih besar dari ini membuat JVM gagal start
                    // ("Could not reserve enough space"). Tidak berlaku di APK 64-bit.
                    val heapCap32Mb = 1280
                    if (heapMaxMb > heapCap32Mb) {
                        LoggerBridge.append("▷ [JVM] Proses 32-bit: heap maksimum dibatasi $heapCap32Mb MB (diminta $heapMaxMb MB)")
                        heapMaxMb = heapCap32Mb
                    }
                    if (heapInitMb > heapMaxMb) heapInitMb = heapMaxMb
                }
                jvmArgs.add("-Xms${heapInitMb}M")
                jvmArgs.add("-Xmx${heapMaxMb}M")
                profileFlags.forEach { jvmArgs.add(it) }
                // Log GC untuk pemantau kinerja (Java 9 ke atas; Java 8 tidak mengenal -Xlog). Berkas
                // lama dihapus dulu supaya tiap sesi mulai dari nol.
                val gcLogFile: File? = if (isJava8) null else File(cacheDir, "amber-gc.log")
                if (gcLogFile != null) {
                    gcLogFile.delete()
                    jvmArgs.add("-Xlog:gc:file=${gcLogFile.absolutePath}:uptime:filecount=0")
                }
                LoggerBridge.append(
                    "▷ [JVM] Heap awal $heapInitMb MB, maksimum $heapMaxMb MB | " +
                        "RAM HP total $deviceTotalRamMb MB, " +
                        "tersedia ${SettingsManager.getAvailableDeviceMemoryMb(this)} MB"
                )
                if (activeSettings.customJvmArgs.isNotBlank()) {
                    activeSettings.customJvmArgs.split(" ")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { jvmArgs.add(it) }
                }

                // Hanya salinan pribadi yang diekstrak dari aset aplikasi yang dipakai sebagai agent. Berkas di
                // penyimpanan bersama bisa diganti aplikasi lain, dan agent berjalan dengan hak penuh di dalam game.
                val patcherJar = File(filesDir, "components/launcher/MioLibPatcher.jar")
                if (patcherJar.exists()) {
                    jvmArgs.add("-javaagent:${patcherJar.absolutePath}")
                    LoggerBridge.append("▷ Enabled MioLibPatcher bytecode agent")
                }

                // Enable authlib-injector if using Ely.by or custom auth server
                if (!authlibInjectorPath.isNullOrBlank() && !authlibUrl.isNullOrBlank()) {
                    val authlibJar = File(authlibInjectorPath!!)
                    if (authlibJar.exists()) {
                        jvmArgs.add("-javaagent:${authlibJar.absolutePath}=$authlibUrl")
                        jvmArgs.add("-Dauthlibinjector.side=client")
                        LoggerBridge.append("▷ Enabled authlib-injector for external auth ($authlibUrl)")
                    }
                }

                if (!isJava8) {
                    jvmArgs.add("--add-opens=java.base/java.lang=ALL-UNNAMED")
                    jvmArgs.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED")
                    jvmArgs.add("--add-opens=java.base/java.util=ALL-UNNAMED")
                    jvmArgs.add("--add-opens=java.base/java.text=ALL-UNNAMED")
                    jvmArgs.add("--add-opens=java.base/java.net=ALL-UNNAMED")
                    jvmArgs.add("--add-opens=jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED")
                    jvmArgs.add("--add-exports=jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED")
                    jvmArgs.add("--add-opens=java.desktop/sun.awt=ALL-UNNAMED")
                    jvmArgs.add("--add-opens=java.desktop/java.awt=ALL-UNNAMED")
                    jvmArgs.add("--add-opens=java.desktop/sun.font=ALL-UNNAMED")
                    jvmArgs.add("--add-opens=java.desktop/sun.java2d=ALL-UNNAMED")

                    if (mainClass.contains(".")) {
                        val pkg = mainClass.substring(0, mainClass.lastIndexOf("."))
                        jvmArgs.add("--add-exports")
                        jvmArgs.add("$pkg/$pkg=ALL-UNNAMED")
                    }
                }

                // Sanitasi classpath: buang duplikat dan pastikan tidak ada bentrok versi ASM jika menjalankan Fabric/Knot
                val effectiveClasspath = if (mainClass.contains("knot") || mainClass.contains("fabric")) {
                    val entries = classpath.split(File.pathSeparator).filter { it.isNotBlank() }
                    val hasModernAsm = entries.any { it.contains("org/ow2/asm") && !it.contains("/9.6/") }
                    if (hasModernAsm) {
                        entries.filterNot { it.contains("org/ow2/asm") && it.contains("/9.6/") }
                    } else {
                        entries
                    }.distinct().joinToString(File.pathSeparator)
                } else {
                    classpath.split(File.pathSeparator).filter { it.isNotBlank() }.distinct().joinToString(File.pathSeparator)
                }

                jvmArgs.add("-cp")
                jvmArgs.add(effectiveClasspath)
                if (useWrapper) {
                    jvmArgs.add("mio.Wrapper")
                }
                jvmArgs.add(mainClass)
                jvmArgs.add("--username")
                jvmArgs.add(username)
                jvmArgs.add("--version")
                jvmArgs.add(mcVersion)
                jvmArgs.add("--gameDir")
                jvmArgs.add(gameDir.absolutePath)
                jvmArgs.add("--assetsDir")
                jvmArgs.add(assetsDir.absolutePath)
                jvmArgs.add("--assetIndex")
                jvmArgs.add(assetIndexId)
                jvmArgs.add("--uuid")
                jvmArgs.add(uuid)
                jvmArgs.add("--accessToken")
                jvmArgs.add(accessToken)
                jvmArgs.add("--userType")
                jvmArgs.add(userType)
                jvmArgs.add("--versionType")
                jvmArgs.add("release")

                LoggerBridge.appendTitle("JVM Launch Command")
                jvmArgs.forEach { LoggerBridge.append("▷ $it") }

                CrashManager.onGameSessionStarted(
                    context = this,
                    instanceName = instanceName,
                    mcVersion = mcVersion,
                    rendererId = activeSettings.selectedRenderer,
                    gameDirPath = gameDir.absolutePath,
                    loader = loader,
                    loaderVersion = loaderVersion,
                    installedMods = installedMods
                )
                liveLogs.add("[Amber Engine] Memulai eksekusi VMLauncher.launchJVM()...")
                // Pemantau hanya mencatat ke log tiap 30 detik; tidak mengubah apa pun pada game.
                val perfMonitor = GamePerfMonitor(gcLogFile)
                perfMonitor.start()
                val exitCode = try {
                    VMLauncher.launchJVM(jvmArgs.toTypedArray())
                } finally {
                    perfMonitor.stop()
                }
                liveLogs.add("[Amber Engine] JVM selesai dengan kode keluar: $exitCode")
                runOnUiThread {
                    handleGameExit(exitCode, false)
                }
            } catch (e: Throwable) {
                LoggerBridge.append("[ERROR JVM Launch] ${e.message}")
                e.printStackTrace()
                runOnUiThread {
                    handleGameExit(-1, false, e.stackTraceToString())
                }
            }
        }
    }
}

enum class FpsMode {
    NORMAL,    // Siklus 3 / Awal: Mengikuti visibilitas GUI (isControlVisible), log tertutup
    PINNED,    // Siklus 1: FPS di-pin, tetap tampil meski GUI di-hide, log tertutup
    SHOW_LOG   // Siklus 2: Memunculkan log in-game (isConsoleVisible = true)
}

/** Jendela UI digambar lebih dari ini per detik = dianggap sibuk dan dilaporkan di samping FPS. */
private const val UI_BUSY_PER_SEC = 12

/**
 * Info kondisi HP yang ditampilkan di samping angka FPS supaya penyebab FPS rendah terbaca
 * langsung di layar: "sisa RAM xxxMB" (memori kosong HP; kalau tinggal sedikit sistem mulai
 * menukar memori / menutup aplikasi) dan "PANAS" (prosesor diperlambat karena HP panas).
 * Pasangan hasil: (teks, peringatan?). Teks kosong = info tidak tersedia.
 */
private fun buildPerfHint(context: Context): Pair<String, Boolean> {
    var text = ""
    var warn = false
    try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        if (am != null) {
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val availMb = (mi.availMem / (1024L * 1024L)).toInt()
            // Dibulatkan per 50 MB supaya angka tidak berubah-ubah terus
            text = "sisa RAM ${(availMb / 50) * 50}MB"
            if (mi.lowMemory || availMb < 450) warn = true
        }
    } catch (_: Throwable) {
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (pm != null && pm.currentThermalStatus >= android.os.PowerManager.THERMAL_STATUS_MODERATE) {
                text = if (text.isEmpty()) "PANAS" else "$text · PANAS"
                warn = true
            }
        } catch (_: Throwable) {
        }
    }
    // Hz layar yang benar-benar aktif: hanya di HP yang punya profil khusus (HP lain tidak berubah).
    // Dipakai untuk memastikan kunci 60 Hz berlaku; kalau tertulis 90Hz berarti sistem menolaknya.
    try {
        val hz = DeviceProfiles.refreshRateLabel(context)
        if (hz.isNotEmpty()) {
            text = if (text.isEmpty()) hz else "$text · $hz"
        }
    } catch (_: Throwable) {
    }
    return Pair(localizeHint(text), warn)
}

/** Menerjemahkan kata di teks kondisi HP bila bahasa bukan Indonesia (HUD digambar langsung, bukan lewat Text). */
private fun localizeHint(text: String): String {
    if (text.isEmpty() || Tr.effective == Tr.ID) return text
    return text.replace("sisa RAM", Tr.t("sisa RAM")).replace("PANAS", Tr.t("PANAS"))
}

@Composable
fun GameScreen(
    instanceName: String,
    mcVersion: String,
    username: String,
    mainClass: String,
    runtimeName: String,
    liveLogs: List<String>,
    isControlVisibleState: MutableState<Boolean>,
    isGameRenderingState: MutableState<Boolean>,
    onSurfaceReady: (SurfaceHolder) -> Unit,
    onResolutionChange: (Int) -> Unit = {},
    onToggleRecord: () -> Unit = {},
    onExit: () -> Unit
) {
    var isControlVisible by isControlVisibleState
    var isGameRendering by isGameRenderingState
    var isManualLoadingDismissed by remember { mutableStateOf(false) }
    val showLoadingOverlay = !isGameRendering && !isManualLoadingDismissed

    var showInGameSettingsDialog by remember { mutableStateOf(false) }
    var isCustomGuiEditorActive by remember { mutableStateOf(false) }
    var isKeyboardRequested by remember { mutableStateOf(false) }
    var isConsoleVisible by remember { mutableStateOf(false) }
    var isConsoleExpanded by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var currentFps by remember { mutableIntStateOf(0) }
    var fpsMode by remember { mutableStateOf(FpsMode.NORMAL) }
    val context = LocalContext.current

    // Handler siklus 3-klik tombol FPS
    val cycleFpsMode: () -> Unit = {
        when (fpsMode) {
            FpsMode.NORMAL -> {
                fpsMode = FpsMode.PINNED
                isConsoleVisible = false
                Toast.makeText(context, "📌 FPS Di-Pin (Tetap tampil saat GUI disembunyikan)", Toast.LENGTH_SHORT).show()
            }
            FpsMode.PINNED -> {
                fpsMode = FpsMode.SHOW_LOG
                isConsoleVisible = true
                Toast.makeText(context, "📜 Menampilkan Live Log Minecraft", Toast.LENGTH_SHORT).show()
            }
            FpsMode.SHOW_LOG -> {
                fpsMode = FpsMode.NORMAL
                isConsoleVisible = false
                Toast.makeText(context, "FPS Mode Normal (Mengikuti visibilitas GUI)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Info kondisi HP di samping FPS (sisa RAM / PANAS); amber bila kondisinya mepet
    var perfText by remember { mutableStateOf("") }
    var perfWarn by remember { mutableStateOf(false) }

    // Info RAM/panas hanya tampil di teks FPS, jadi hanya dihitung saat teks FPS terlihat.
    val wantPerfHint by rememberUpdatedState(isControlVisible || fpsMode == FpsMode.PINNED)

    // Hitung berapa kali jendela UI digambar per detik. Saat bermain angkanya harus nyaris nol
    // (hanya satu kali per detik untuk teks FPS); kalau tinggi, UI ikut berebut CPU/GPU dengan
    // game dan angkanya muncul di samping FPS ("UI n/s"), jadi penyebab FPS turun bisa terbaca.
    val uiDraws = remember { IntArray(1) }
    var uiBusy by remember { mutableIntStateOf(0) }
    val rootView = LocalView.current
    DisposableEffect(rootView) {
        val listener = ViewTreeObserver.OnDrawListener { uiDraws[0]++ }
        val vto = rootView.viewTreeObserver
        if (vto.isAlive) vto.addOnDrawListener(listener)
        onDispose {
            val t = rootView.viewTreeObserver
            if (t.isAlive) t.removeOnDrawListener(listener)
        }
    }

    // Periodically update FPS counter from native engine (1x per detik: tiap angka baru = satu
    // penggambaran UI, jadi tidak perlu lebih sering dari yang bisa dibaca mata)
    LaunchedEffect(Unit) {
        var tick = 0
        var lastDraws = uiDraws[0]
        while (isActive) {
            try {
                currentFps = CallbackBridge.getCurrentFps()
            } catch (_: Throwable) {
            }
            val draws = uiDraws[0]
            val busy = if (draws - lastDraws > UI_BUSY_PER_SEC) draws - lastDraws else 0
            lastDraws = draws
            if (busy != uiBusy) uiBusy = busy
            if (tick % 3 == 0 && wantPerfHint) {
                // Panggilan ke sistem (memori, suhu) dikerjakan di luar thread utama.
                val hint = withContext(Dispatchers.Default) {
                    try { buildPerfHint(context) } catch (_: Throwable) { Pair("", false) }
                }
                if (hint.first != perfText) perfText = hint.first
                if (hint.second != perfWarn) perfWarn = hint.second
            }
            tick++
            delay(1000)
        }
    }

    val gameCursorMode by ZLBridgeStates.cursorMode.collectAsState()
    val launcherSettings by SettingsManager.settings.collectAsState()
    val customButtons by ControlLayoutManager.buttons.collectAsState()
    val mouseControlMode = if (launcherSettings.mouseControlMode == "CLICK") MouseControlMode.CLICK else MouseControlMode.SLIDE

    // Rekam mode "Game saja": perekam menangkap seluruh layar, jadi tombol layar dibuat transparan
    // selama merekam (tetap bisa disentuh, hanya tidak tergambar) supaya tidak ikut terekam.
    val recState by ScreenRecorder.state.collectAsState()
    val ghostControls = recState.recording && !launcherSettings.recordWithControls
    // Tanda mulai/berhenti merekam berupa getar singkat (toast akan ikut terekam).
    val hostView = LocalView.current
    var wasRecording by remember { mutableStateOf(false) }
    LaunchedEffect(recState.recording) {
        if (recState.recording != wasRecording) {
            wasRecording = recState.recording
            hostView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        var cursorX by remember { mutableFloatStateOf(0f) }
        var cursorY by remember { mutableFloatStateOf(0f) }
        var isMousePressed by remember { mutableStateOf(false) }

        LaunchedEffect(screenWidth, screenHeight) {
            if (cursorX == 0f && screenWidth > 0f) {
                cursorX = screenWidth / 2f
                cursorY = screenHeight / 2f
                val winW = CallbackBridge.windowWidth
                val winH = CallbackBridge.windowHeight
                val targetX = if (screenWidth > 0 && winW > 0) cursorX * (winW.toFloat() / screenWidth) else cursorX
                val targetY = if (screenHeight > 0 && winH > 0) cursorY * (winH.toFloat() / screenHeight) else cursorY
                CallbackBridge.sendCursorPos(targetX, targetY)
            }
        }

        // 1. OpenGL/Vulkan Surface View (Pure rendering surface)
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    onSurfaceReady(holder)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isCustomGuiEditorActive) {
            // Live In-Game Custom GUI Editor (Overlay di atas SurfaceView game dengan latar transparan blur)
            CustomGuiEditorScreen(
                isIngame = true,
                onNavigateBack = {
                    isCustomGuiEditorActive = false
                }
            )
        } else {
            // Physical Mouse Mode State (Zalith-style: auto-hide virtual pointer when physical mouse is used)
            var isPhysicalMouseMode by remember {
            mutableStateOf(
                if (PhysicalMouseChecker.physicalMouseConnected) {
                    launcherSettings.physicalMouseMode
                } else {
                    false
                }
            )
        }

        // 2. Zalith-Style Touchpad & Input Controller Layer
        SwitchableMouseLayout(
            modifier = Modifier.fillMaxSize(),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            cursorMode = gameCursorMode,
            controlMode = mouseControlMode,
            cursorPosition = { Offset(cursorX, cursorY) },
            cursorSensitivity = launcherSettings.cursorSensitivity / 100f,
            touchAttackEnabled = launcherSettings.touchAttackEnabled,
            requestPointerCapture = !launcherSettings.physicalMouseMode,
            onCursorPositionChange = { newPos ->
                cursorX = newPos.x
                cursorY = newPos.y
            },
            onPhysicalMouseModeChange = { isPhysical ->
                isPhysicalMouseMode = isPhysical
            },
            onMouse = {
                isPhysicalMouseMode = true
                isControlVisible = false
            },
            onTouch = {
                isPhysicalMouseMode = false
            },
            onTap = { pos ->
                val winW = CallbackBridge.windowWidth
                val winH = CallbackBridge.windowHeight
                val targetX = if (screenWidth > 0 && winW > 0) pos.x * (winW.toFloat() / screenWidth) else pos.x
                val targetY = if (screenHeight > 0 && winH > 0) pos.y * (winH.toFloat() / screenHeight) else pos.y
                CallbackBridge.putMouseEventWithCoords(
                    LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT,
                    targetX,
                    targetY
                )
            },
            onLongPress = {
                isMousePressed = true
                CallbackBridge.putMouseEvent(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, true)
            },
            onLongPressEnd = {
                isMousePressed = false
                CallbackBridge.putMouseEvent(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, false)
            },
            onCapturedMove = { delta ->
                val sens = launcherSettings.captureSensitivity / 100f
                CallbackBridge.sendCursorDelta(delta.x * sens, delta.y * sens)
            }
        )

        // 2b. Game Loading Screen Overlay with Launcher Tips (Muncul sebelum logo Mojang)
        GameLoadingOverlay(
            visible = showLoadingOverlay,
            instanceName = instanceName,
            mcVersion = mcVersion,
            // Daftar log hanya dibaca saat layar loading tampil; selebihnya layar tidak ikut
            // digambar ulang setiap ada log baru.
            latestLog = if (showLoadingOverlay) (liveLogs.lastOrNull() ?: "") else "",
            onClose = { isManualLoadingDismissed = true },
            onViewLog = {
                fpsMode = FpsMode.SHOW_LOG
                isConsoleVisible = true
            },
            modifier = Modifier.fillMaxSize()
        )

        // Hidden IME soft keyboard layer
        if (isKeyboardRequested) {
            HidableInputLayout(
                onClose = {
                    isKeyboardRequested = false
                }
            )
        }

        // 3. Visible Desktop Virtual Cursor Pointer (Zalith Style - automatically visible ONLY in menus, hidden when external mouse is active)
        val shouldShowPointer = if (mouseControlMode == MouseControlMode.CLICK && launcherSettings.hideMouseInClickMode) {
            false
        } else if (PhysicalMouseChecker.physicalMouseConnected && isPhysicalMouseMode && launcherSettings.physicalMouseMode) {
            // Sembunyikan mouse virtual jika mouse fisik eksternal terhubung & sedang digunakan
            false
        } else {
            gameCursorMode == CURSOR_ENABLED
        }

        // Posisi kursor dibaca hanya saat menggeser lapisan kursor (lihat VirtualCursorPointer),
        // jadi menggerakkan kursor tidak menyusun ulang layar game.
        if (shouldShowPointer) {
            VirtualCursorPointer(
                x = { cursorX },
                y = { cursorY },
                isPressed = { isMousePressed },
                cursorSizeDp = launcherSettings.mouseSizeDp
            )
        }

        // 3. Panel log mesin (hanya saat dibuka; daftar log dibaca di dalam panelnya saja)
        if (isConsoleVisible) {
            GameConsolePanel(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                fps = { currentFps },
                logs = liveLogs,
                expanded = isConsoleExpanded,
                onToggleExpanded = { isConsoleExpanded = !isConsoleExpanded },
                onFpsClick = { showInGameSettingsDialog = true },
                onClose = {
                    isConsoleVisible = false
                    fpsMode = FpsMode.NORMAL
                }
            )
        }

        // 4. Tombol layar (gaya dan logikanya ada di ui/control/ControlOverlay.kt). Dibungkus Box
        // penuh layar agar bisa dibuat transparan (alpha 0, tetap menerima sentuhan) saat merekam
        // "Game saja". Box tanpa penangan sentuhan tidak menghalangi sentuhan ke lapisan di bawahnya.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (ghostControls) Modifier.graphicsLayer { alpha = 0f } else Modifier)
        ) {
            GameControlsLayer(
                buttons = customButtons,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                controlsVisible = isControlVisible,
                fpsMode = fpsMode,
                consoleVisible = isConsoleVisible,
                keyboardActive = isKeyboardRequested,
                mouseButtonCameraLook = launcherSettings.mouseButtonCameraLook,
                fps = { currentFps },
                perfText = {
                    if (uiBusy > 0) {
                        if (perfText.isEmpty()) "UI $uiBusy/s" else "$perfText \u00B7 UI $uiBusy/s"
                    } else {
                        perfText
                    }
                },
                perfWarn = { perfWarn || uiBusy > 0 },
                onFpsClick = { showInGameSettingsDialog = true },
                onToggleKeyboard = { isKeyboardRequested = !isKeyboardRequested },
                onToggleControls = { isControlVisible = !isControlVisible },
                onOpenMenu = { showInGameSettingsDialog = true }
            )
        }

        // Konfirmasi keluar
        if (showExitConfirmDialog) {
            ExitConfirmDialog(
                onCancel = { showExitConfirmDialog = false },
                onConfirm = {
                    showExitConfirmDialog = false
                    onExit()
                }
            )
        }

        // Menu dalam game (ketuk gerigi atau angka FPS untuk membuka; keluar lewat tombol merah di dalamnya)
        GameMenuSheet(
            visible = showInGameSettingsDialog,
            onDismissRequest = { showInGameSettingsDialog = false },
            fps = { currentFps },
            instanceName = instanceName,
            mcVersion = mcVersion,
            currentResolutionRatio = launcherSettings.resolutionRatio,
            onResolutionChange = { newRatio ->
                onResolutionChange(newRatio)
            },
            fpsMode = fpsMode,
            onFpsModeChange = { newMode ->
                fpsMode = newMode
            },
            onOpenConsoleLog = {
                fpsMode = FpsMode.SHOW_LOG
                isConsoleVisible = true
            },
            cursorSensitivity = launcherSettings.cursorSensitivity,
            onCursorSensitivityChange = { newSens ->
                SettingsManager.updateSettings(
                    context,
                    launcherSettings.copy(cursorSensitivity = newSens)
                )
            },
            captureSensitivity = launcherSettings.captureSensitivity,
            onCaptureSensitivityChange = { newSens ->
                SettingsManager.updateSettings(
                    context,
                    launcherSettings.copy(captureSensitivity = newSens)
                )
            },
            mouseControlMode = mouseControlMode,
            onMouseControlModeChange = { newMode ->
                SettingsManager.updateSettings(
                    context,
                    launcherSettings.copy(mouseControlMode = if (newMode == MouseControlMode.CLICK) "CLICK" else "SLIDE")
                )
            },
            isControlVisible = isControlVisible,
            onToggleControlVisibility = {
                isControlVisible = !isControlVisible
            },
            onRequestKeyboard = {
                isKeyboardRequested = true
            },
            onSendKeycode = { keycode ->
                CallbackBridge.sendKeyPress(keycode, true)
                CallbackBridge.sendKeyPress(keycode, false)
            },
            onExitRequest = {
                showExitConfirmDialog = true
            },
            onOpenCustomGui = {
                showInGameSettingsDialog = false
                isCustomGuiEditorActive = true
            },
            onToggleRecord = onToggleRecord,
            recordWithControls = launcherSettings.recordWithControls,
            onRecordWithControlsChange = { withControls ->
                SettingsManager.updateSettings(
                    context,
                    launcherSettings.copy(recordWithControls = withControls)
                )
            }
        )
        }
    }
}

/**
 * Kursor panah untuk menu game. Posisinya diberikan lewat lambda dan hanya dibaca saat lapisan
 * digeser (tanpa menyusun ulang dan tanpa menggambar ulang panahnya); bentuk panah dibuat sekali.
 * Belum ada posisi (0, 0) = tidak terlihat.
 */
@Composable
fun VirtualCursorPointer(
    x: () -> Float,
    y: () -> Float,
    isPressed: () -> Boolean = { false },
    cursorSizeDp: Int = 24,
    modifier: Modifier = Modifier
) {
    val d = LocalDensity.current.density
    val arrow = remember(d) {
        Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, 22f * d)
            lineTo(5.5f * d, 16.5f * d)
            lineTo(10.5f * d, 24.5f * d)
            lineTo(13.5f * d, 23f * d)
            lineTo(8.5f * d, 15f * d)
            lineTo(15.5f * d, 15f * d)
            close()
        }
    }
    val arrowShadow = remember(arrow, d) {
        Path().apply { addPath(arrow, Offset(2f * d, 2f * d)) }
    }
    val outline = remember(d) {
        Stroke(width = 1.8f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }

    Box(
        modifier = modifier
            .size(cursorSizeDp.dp)
            .graphicsLayer {
                val px = x()
                val py = y()
                translationX = px
                translationY = py
                alpha = if (px > 0f && py > 0f) 1f else 0f
            }
            .drawBehind {
                val k = (cursorSizeDp / 24f) * (if (isPressed()) 0.88f else 1.0f)
                scale(k, pivot = Offset.Zero) {
                    drawPath(path = arrowShadow, color = Color.Black.copy(alpha = 0.45f), style = Fill)
                    drawPath(path = arrow, color = Color.White, style = Fill)
                    drawPath(path = arrow, color = Color.Black, style = outline)
                }
            }
    )
}
