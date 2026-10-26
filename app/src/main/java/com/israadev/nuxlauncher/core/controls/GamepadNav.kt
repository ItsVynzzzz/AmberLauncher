package com.israadev.nuxlauncher.core.controls

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import kotlin.math.abs

/**
 * Navigasi menu launcher dengan controller, tanpa mengubah satu pun layar:
 * pencegat tipis di Window.Callback (Activity maupun dialog) yang menerjemahkan
 * - A -> tombol tengah D-pad (memilih yang sedang disorot),
 * - B -> Kembali,
 * - stick kiri / hat -> tombol panah (menahan = mengulang),
 * - LB / RB -> pindah tab (hanya di layar utama).
 * Selebihnya (D-pad asli, keyboard, sentuhan) lewat seperti biasa, jadi pengguna tanpa controller
 * tidak merasakan perubahan apa pun.
 */
object GamepadNav {

    /** LB/RB di layar utama: -1 = tab sebelumnya, +1 = berikutnya. */
    @Volatile
    var bumperListener: ((Int) -> Unit)? = null

    /** Pasang pada [window]. Mengembalikan fungsi pelepas. Aman dipanggil dua kali pada jendela yang sama. */
    fun install(window: Window, allowBumper: Boolean): () -> Unit {
        val base = window.callback ?: return {}
        if (base is NavCallback) return {}
        val callback = NavCallback(base, window, allowBumper)
        window.callback = callback
        return {
            callback.stop()
            if (window.callback === callback) window.callback = base
        }
    }

    internal fun isPad(event: KeyEvent): Boolean {
        val s = event.source
        if ((s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        ) return true
        return KeyEvent.isGamepadButton(event.keyCode)
    }

    private class NavCallback(
        private val base: Window.Callback,
        private val window: Window,
        private val allowBumper: Boolean
    ) : Window.Callback by base {

        private val handler = Handler(Looper.getMainLooper())
        private val repeater = DirectionRepeater()
        private var dpadKeyAt = 0L

        private val repeatTick = object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis()
                if (repeater.dir == 0) return
                if (!window.decorView.hasWindowFocus()) {
                    repeater.set(0, now)
                    return
                }
                if (repeater.due(now)) press(repeater.dir)
                handler.postDelayed(this, repeater.delayUntilNext(now).coerceAtLeast(16L))
            }
        }

        fun stop() {
            handler.removeCallbacks(repeatTick)
            repeater.set(0, SystemClock.uptimeMillis())
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (GamepadNav.isPad(event)) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_A ->
                        return base.dispatchKeyEvent(remap(event, KeyEvent.KEYCODE_DPAD_CENTER))
                    KeyEvent.KEYCODE_BUTTON_B ->
                        return base.dispatchKeyEvent(remap(event, KeyEvent.KEYCODE_BACK))
                    KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> {
                        if (allowBumper && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            GamepadNav.bumperListener?.invoke(if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1) -1 else 1)
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> dpadKeyAt = SystemClock.uptimeMillis()
                }
            }
            return base.dispatchKeyEvent(event)
        }

        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
            if ((event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
                event.actionMasked == MotionEvent.ACTION_MOVE
            ) {
                onStick(event)
                return true
            }
            return base.dispatchGenericMotionEvent(event)
        }

        private fun onStick(event: MotionEvent) {
            val now = SystemClock.uptimeMillis()
            var x = event.getAxisValue(MotionEvent.AXIS_X)
            var y = event.getAxisValue(MotionEvent.AXIS_Y)
            // D-pad berupa hat: dipakai hanya bila controller ini tidak sekaligus mengirim tombol panah.
            if (now - dpadKeyAt > 300L) {
                val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
                val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
                if (abs(hx) > 0.5f) x = hx
                if (abs(hy) > 0.5f) y = hy
            }
            val dir = DirectionRepeater.directionOf(x, y, repeater.dir)
            if (dir == repeater.dir) return
            handler.removeCallbacks(repeatTick)
            if (repeater.set(dir, now)) {
                press(dir)
                handler.postDelayed(repeatTick, repeater.delayUntilNext(now).coerceAtLeast(16L))
            }
        }

        private fun press(keyCode: Int) {
            val t = SystemClock.uptimeMillis()
            base.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0))
            base.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0))
        }

        private fun remap(e: KeyEvent, code: Int): KeyEvent = KeyEvent(
            e.downTime, e.eventTime, e.action, code, e.repeatCount, e.metaState,
            e.deviceId, e.scanCode, e.flags, InputDevice.SOURCE_KEYBOARD
        )
    }
}
