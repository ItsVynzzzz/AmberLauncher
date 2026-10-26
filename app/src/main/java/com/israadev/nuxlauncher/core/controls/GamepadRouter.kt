package com.israadev.nuxlauncher.core.controls

import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.movtery.zalithlauncher.bridge.CURSOR_DISABLED
import com.movtery.zalithlauncher.bridge.LoggerBridge
import com.movtery.zalithlauncher.bridge.ZLBridgeStates
import com.movtery.zalithlauncher.game.sdl.SdlBridge
import com.movtery.zalithlauncher.game.sdl.handleGamepadKeyEvent
import com.movtery.zalithlauncher.game.sdl.handleGamepadMotionEvent
import org.libsdl.app.SDLControllerManager
import org.lwjgl.glfw.CallbackBridge

/**
 * Jalur controller di dalam game. Dipanggil dari GameActivity di thread utama.
 *
 * Satu controller hanya punya satu pemilik, dipilih otomatis:
 * 1. Mod yang membaca controller lewat SDL (Controlify) atau GLFW langsung: tombol dan stick
 *    diteruskan apa adanya, launcher tidak ikut menerjemahkan (supaya tidak dobel).
 * 2. Tanpa mod: controller diterjemahkan menjadi keyboard + mouse oleh [GamepadEmulator].
 *
 * Tidak ada pekerjaan saat controller diam: pencacah frame hanya berjalan selama stick digerakkan.
 */
object GamepadRouter {

    /** Diisi layar game: menggeser kursor menu (dalam piksel layar). */
    @Volatile
    var cursorMover: ((Float, Float) -> Unit)? = null

    private val emulator = GamepadEmulator(object : GamepadOutput {
        override fun key(glfwKey: Int, down: Boolean) = CallbackBridge.sendKeyPress(glfwKey, down)
        override fun mouse(button: Int, down: Boolean) = CallbackBridge.sendMouseButton(button, down)
        override fun scroll(amount: Double) = CallbackBridge.sendScroll(0.0, amount)
        override fun look(dx: Float, dy: Float) = CallbackBridge.sendCursorDelta(dx, dy)
        override fun cursor(dx: Float, dy: Float) {
            cursorMover?.invoke(dx, dy)
        }
    })

    private var emulating = false
    private var lastOwner = ""
    private var ticking = false
    private var lastFrameNanos = 0L

    // Sumbu stick kanan dan trigger berbeda-beda antar merek: dicari sekali per perangkat.
    private var axisDevice = Int.MIN_VALUE
    private var rightX = MotionEvent.AXIS_Z
    private var rightY = MotionEvent.AXIS_RZ
    private var leftTrigger = -1
    private var rightTrigger = -1

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val dt = if (lastFrameNanos == 0L) 0.016f else (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = frameTimeNanos
            applySettings()
            syncMode()
            if (emulator.tick(dt)) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                ticking = false
                lastFrameNanos = 0L
            }
        }
    }

    /** Kejadian tombol ini berasal dari controller? */
    fun isPadKey(event: KeyEvent): Boolean {
        val s = event.source
        if ((s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        ) return true
        return KeyEvent.isGamepadButton(event.keyCode)
    }

    /** Gerakan stick/trigger/hat dari controller? */
    fun isPadMotion(event: MotionEvent): Boolean =
        (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
            event.actionMasked == MotionEvent.ACTION_MOVE

    /** True bila kejadian sudah ditangani dan tidak perlu diteruskan ke jalur lain. */
    fun onKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        val pad = Pad.fromKeyCode(code)
        if (pad < 0 && !KeyEvent.isGamepadButton(code)) return false

        val action = event.action
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return true
        if (action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) return true

        if (modOwns(event.deviceId)) {
            stopEmulating()
            forwardKey(event)
            return true
        }
        if (pad >= 0 && SettingsManager.settings.value.gamepadEmulation) {
            note("emulasi keyboard+mouse")
            applySettings()
            syncMode()
            emulating = true
            emulator.button(pad, action == KeyEvent.ACTION_DOWN)
            ensureTicking()
        }
        return true
    }

    /** True bila gerakan sudah ditangani. */
    fun onMotion(event: MotionEvent): Boolean {
        if (modOwns(event.deviceId)) {
            stopEmulating()
            if (CallbackBridge.sGamepadDirectInput) handleGamepadMotionEvent(event)
            if (SdlBridge.sdlEnabled) {
                try {
                    SDLControllerManager.handleJoystickMotionEvent(event)
                } catch (_: Throwable) {
                }
            }
            return true
        }
        if (!SettingsManager.settings.value.gamepadEmulation) return false

        findAxes(event)
        applySettings()
        syncMode()
        emulating = true
        emulator.axes(
            lx = event.getAxisValue(MotionEvent.AXIS_X),
            ly = event.getAxisValue(MotionEvent.AXIS_Y),
            rx = event.getAxisValue(rightX),
            ry = event.getAxisValue(rightY),
            lt = if (leftTrigger >= 0) event.getAxisValue(leftTrigger) else 0f,
            rt = if (rightTrigger >= 0) event.getAxisValue(rightTrigger) else 0f,
            hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X),
            hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        )
        ensureTicking()
        return true
    }

    /** Layar dijeda, controller dicabut, atau game ditutup: lepaskan semua tombol yang tertahan. */
    fun reset() {
        stopEmulating()
    }

    private fun modOwns(deviceId: Int): Boolean {
        // Mod membaca lewat GLFW langsung: LWJGL yang sudah ditambal memberi tahu lewat onDirectInputEnable.
        if (CallbackBridge.sGamepadDirectInput) {
            note("diteruskan ke mod (GLFW)")
            return true
        }
        // Mod membaca lewat SDL (Controlify): SDL sudah mengenali controller ini.
        if (!SdlBridge.sdlEnabled) return false
        val known = try {
            SDLControllerManager.isKnownToSdl(deviceId)
        } catch (_: Throwable) {
            false
        }
        if (known) note("diteruskan ke mod (SDL)")
        return known
    }

    /** Catat ke log game saat pemilik controller berganti, untuk memudahkan pelacakan laporan. */
    private fun note(owner: String) {
        if (owner == lastOwner) return
        lastOwner = owner
        try {
            LoggerBridge.append("▷ [Controller] $owner")
        } catch (_: Throwable) {
        }
    }

    private fun forwardKey(event: KeyEvent) {
        if (CallbackBridge.sGamepadDirectInput) handleGamepadKeyEvent(event)
        if (SdlBridge.sdlEnabled) {
            try {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    SDLControllerManager.onNativePadDown(event.deviceId, event.keyCode, event.scanCode)
                } else {
                    SDLControllerManager.onNativePadUp(event.deviceId, event.keyCode, event.scanCode)
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun stopEmulating() {
        if (!emulating) return
        emulating = false
        emulator.reset()
    }

    private fun syncMode() {
        emulator.setGrabbed(ZLBridgeStates.cursorMode.value == CURSOR_DISABLED)
    }

    private fun applySettings() {
        val s = SettingsManager.settings.value
        emulator.lookScale = s.captureSensitivity / 100f
        emulator.cursorScale = s.cursorSensitivity / 100f
    }

    private fun ensureTicking() {
        if (ticking || !emulator.needsTick()) return
        ticking = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun findAxes(event: MotionEvent) {
        val id = event.deviceId
        if (id == axisDevice) return
        axisDevice = id
        val dev = event.device
        fun has(axis: Int) = dev?.getMotionRange(axis) != null
        rightX = if (has(MotionEvent.AXIS_RX)) MotionEvent.AXIS_RX else MotionEvent.AXIS_Z
        rightY = if (has(MotionEvent.AXIS_RY)) MotionEvent.AXIS_RY else MotionEvent.AXIS_RZ
        leftTrigger = when {
            has(MotionEvent.AXIS_LTRIGGER) -> MotionEvent.AXIS_LTRIGGER
            has(MotionEvent.AXIS_BRAKE) -> MotionEvent.AXIS_BRAKE
            else -> -1
        }
        rightTrigger = when {
            has(MotionEvent.AXIS_RTRIGGER) -> MotionEvent.AXIS_RTRIGGER
            has(MotionEvent.AXIS_GAS) -> MotionEvent.AXIS_GAS
            else -> -1
        }
    }
}
