package com.israadev.nuxlauncher.starling

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.israadev.nuxlauncher.FpsMode
import com.israadev.nuxlauncher.core.controls.ControlLayoutManager
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.ui.control.GameControlsLayer
import com.israadev.nuxlauncher.ui.control.MouseControlMode
import com.israadev.nuxlauncher.ui.control.SwitchableMouseLayout
import com.israadev.nuxlauncher.ui.dialogs.ExitConfirmDialog
import com.israadev.nuxlauncher.ui.dialogs.GameMenuSheet
import com.movtery.zalithlauncher.bridge.CURSOR_DISABLED
import com.movtery.zalithlauncher.bridge.CURSOR_ENABLED
import com.movtery.zalithlauncher.bridge.ZLBridgeStates
import kotlinx.coroutines.delay
import org.lwjgl.glfw.CallbackBridge
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode

/**
 * Kontrol dan menu Amber di atas mesin Starling (proses :starling): tombol, joystick, kamera, dan menu dalam game yang
 * sama dengan game Java ([GameControlsLayer], [SwitchableMouseLayout], [GameMenuSheet]), menggantikan tombol, tombol
 * hitam, dan pop-up milik mesin (yang dimatikan oleh AMBER_CONTROLS, patch amber5/0005). Yang ditekan masuk lewat
 * [CallbackBridge] -> [StarlingInput] -> [StarlingBridge]. Menu dan layar mesin tetap disentuh langsung: lapisan kamera
 * hanya ada saat bermain.
 */
object StarlingOverlay {
    /** Bit keadaan layar mesin (amber_input.rs). */
    private const val ALIVE = 1
    private const val MENU = 2

    private var dialog: Dialog? = null
    private var owner: OverlayOwner? = null

    /** Dipasang dari GameActivity milik Starling bila launcher memintanya (extra amber_controls). */
    fun attach(activity: Activity, bridge: StarlingBridge) {
        if (dialog != null) return
        try {
            SettingsManager.init(activity)
        } catch (_: Throwable) {
        }
        try {
            ControlLayoutManager.init(activity)
        } catch (_: Throwable) {
        }
        StarlingInput.bridge = bridge
        CallbackBridge.sink = StarlingInput
        val host = OverlayOwner().also { it.start() }
        val instanceName = activity.intent?.getStringExtra("amber_instance").orEmpty().ifBlank { "Starling" }
        val mcVersion = activity.intent?.getStringExtra("amber_mc_version").orEmpty()
        // Jendela terpisah di atas jendela mesin. Mesin (NativeActivity) mengambil alih permukaan jendela Activity-nya, jadi
        // View yang ditambahkan ke Activity itu tidak pernah tergambar; jendela milik Dialog punya permukaan sendiri.
        val overlay = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        val window = overlay.window ?: return
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        // Tidak mengambil fokus (tombol dan penunjuk tetap ke mesin) dan awalnya tidak menerima sentuhan: di menu dan layar
        // mesin sentuhan langsung ke mesin; jendela ini baru menerimanya saat bermain (setTouchable).
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.attributes = window.attributes.also {
            it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val decor = window.decorView
        decor.setViewTreeLifecycleOwner(host)
        decor.setViewTreeViewModelStoreOwner(host)
        decor.setViewTreeSavedStateRegistryOwner(host)
        val compose = ComposeView(activity).apply {
            setContent { OverlayContent(bridge, instanceName, mcVersion) }
        }
        overlay.setCancelable(false)
        overlay.setContentView(compose)
        try {
            overlay.show()
        } catch (error: Throwable) {
            log("Jendela kontrol gagal ditampilkan: $error")
            return
        }
        dialog = overlay
        owner = host
        log("Kontrol Amber dipasang di jendela terpisah (instance $instanceName)")
    }

    /** Dicatat ke log launcher (bisa diekspor) dan logcat: lapisan ini jalan di proses :starling yang tidak terlihat pemain. */
    private fun log(message: String) {
        try {
            LauncherLog.event("KontrolAmber", message)
        } catch (_: Throwable) {
        }
        try {
            android.util.Log.i("AmberControls", message)
        } catch (_: Throwable) {
        }
    }

    fun detach() {
        CallbackBridge.sink = null
        StarlingInput.bridge = null
        try {
            dialog?.dismiss()
        } catch (_: Throwable) {
        }
        dialog = null
        owner?.stop()
        owner = null
    }

    /** Jendela kontrol menerima sentuhan hanya saat bermain; selain itu sentuhan lewat langsung ke mesin. */
    private fun setTouchable(touchable: Boolean) {
        val window = dialog?.window ?: return
        if (touchable) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
        // Dicatat tiap pergantian (bukan tiap frame), supaya log menunjukkan apakah mesin melapor "bermain".
        log(if (touchable) "Bermain: kontrol Amber tampil dan menerima sentuhan" else "Menu/layar mesin: tombol Amber jadi jendela kecil, sentuhan lainnya langsung ke mesin")
    }

    @Composable
    private fun OverlayContent(bridge: StarlingBridge, instanceName: String, mcVersion: String) {
        val context = LocalContext.current
        val settings by SettingsManager.settings.collectAsState()
        val buttons by ControlLayoutManager.buttons.collectAsState()
        var state by remember { mutableIntStateOf(0) }
        var controlsVisible by remember { mutableStateOf(true) }
        var menuOpen by remember { mutableStateOf(false) }
        var exitAsk by remember { mutableStateOf(false) }

        // Keadaan layar mesin dibaca tiap ~50 ms: cukup halus untuk berganti antara kontrol bermain dan menu.
        LaunchedEffect(Unit) {
            var failed = false
            while (true) {
                val next = try {
                    bridge.state()
                } catch (error: Throwable) {
                    if (!failed) log("Keadaan mesin tidak terbaca (fungsi native hilang?): $error")
                    failed = true
                    0
                }
                if (next != state) {
                    if (state == 0) log("Mesin melapor berjalan (keadaan $next): kontrol Amber ditampilkan")
                    state = next
                }
                delay(50)
            }
        }
        val alive = state and ALIVE != 0
        val inMenu = state and MENU != 0
        val playing = alive && !inMenu
        // Lapisan Amber membaca mode kursor dari sini: tertangkap saat bermain, kursor biasa di menu.
        LaunchedEffect(playing) {
            ZLBridgeStates.changeCursorMode(if (playing) CURSOR_DISABLED else CURSOR_ENABLED)
            setTouchable(playing)
        }
        val mouseControlMode = if (settings.mouseControlMode == "CLICK") MouseControlMode.CLICK else MouseControlMode.SLIDE

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenWidth = constraints.maxWidth.toFloat()
            val screenHeight = constraints.maxHeight.toFloat()
            if (playing) {
                // Kamera dan ketukan hanya saat bermain; di menu sentuhan lewat ke mesin (tidak ada penyerap di sini).
                SwitchableMouseLayout(
                    modifier = Modifier.fillMaxSize(),
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    cursorMode = CURSOR_DISABLED,
                    controlMode = mouseControlMode,
                    cursorPosition = { Offset.Zero },
                    cursorSensitivity = settings.cursorSensitivity / 100f,
                    touchAttackEnabled = settings.touchAttackEnabled,
                    menuCaptureAllowed = false,
                    onHotbarSelect = { slot ->
                        val key = LwjglGlfwKeycode.GLFW_KEY_1 + slot
                        CallbackBridge.sendKeyPress(key, true)
                        CallbackBridge.sendKeyPress(key, false)
                    },
                    onCursorPositionChange = {}
                )
            }
            // Tombol tampil saat bermain dan juga di menu (inventori, chat, jeda), sesuai mode tampil tiap tombol. Saat bermain
            // mereka ada di jendela penuh ini; di menu tiap tombol jadi jendela kecil sendiri, supaya sentuhan di luar tombol
            // langsung ke mesin dan inventori tetap bisa disentuh.
            if (alive) {
                GameControlsLayer(
                    buttons = buttons,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    controlsVisible = controlsVisible,
                    fpsMode = FpsMode.NORMAL,
                    consoleVisible = false,
                    keyboardActive = false,
                    fps = { 0 },
                    perfText = { "" },
                    perfWarn = { false },
                    onFpsClick = {},
                    onToggleKeyboard = {},
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onOpenMenu = { menuOpen = true },
                    inMenu = inMenu,
                    fpsHudEnabled = false,
                    buttonsAsWindows = !playing
                )
            }
            GameMenuSheet(
                visible = menuOpen,
                onDismissRequest = { menuOpen = false },
                fps = { 0 },
                instanceName = instanceName,
                mcVersion = mcVersion,
                currentResolutionRatio = settings.resolutionRatio,
                onResolutionChange = {},
                fpsMode = FpsMode.NORMAL,
                onFpsModeChange = {},
                onOpenConsoleLog = {},
                cursorSensitivity = settings.cursorSensitivity,
                onCursorSensitivityChange = { SettingsManager.updateSettings(context, settings.copy(cursorSensitivity = it)) },
                captureSensitivity = settings.captureSensitivity,
                onCaptureSensitivityChange = { SettingsManager.updateSettings(context, settings.copy(captureSensitivity = it)) },
                mouseControlMode = mouseControlMode,
                onMouseControlModeChange = {
                    SettingsManager.updateSettings(
                        context,
                        settings.copy(mouseControlMode = if (it == MouseControlMode.CLICK) "CLICK" else "SLIDE")
                    )
                },
                isControlVisible = controlsVisible,
                onToggleControlVisibility = { controlsVisible = !controlsVisible },
                onRequestKeyboard = {},
                onSendKeycode = { code ->
                    CallbackBridge.sendKeyPress(code, true)
                    CallbackBridge.sendKeyPress(code, false)
                },
                onExitRequest = {
                    menuOpen = false
                    exitAsk = true
                },
                onOpenCustomGui = {}
            )
            if (exitAsk) {
                ExitConfirmDialog(
                    onCancel = { exitAsk = false },
                    onConfirm = {
                        exitAsk = false
                        bridge.exit()
                    }
                )
            }
        }
    }

    /** Pemilik siklus hidup untuk ComposeView di Activity bawaan sistem (NativeActivity bukan ComponentActivity). */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        fun start() {
            controller.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }
}
