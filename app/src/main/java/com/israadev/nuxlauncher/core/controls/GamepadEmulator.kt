package com.israadev.nuxlauncher.core.controls

import android.view.KeyEvent
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import kotlin.math.abs
import kotlin.math.hypot

/** Tombol logis controller, lepas dari kode Android supaya pemetaannya mudah diuji. */
object Pad {
    const val A = 0
    const val B = 1
    const val X = 2
    const val Y = 3
    const val L1 = 4
    const val R1 = 5
    const val L2 = 6
    const val R2 = 7
    const val L3 = 8
    const val R3 = 9
    const val START = 10
    const val SELECT = 11
    const val UP = 12
    const val DOWN = 13
    const val LEFT = 14
    const val RIGHT = 15
    const val COUNT = 16

    /** Kode tombol Android -> tombol logis, atau -1 bila bukan tombol yang dipetakan. */
    fun fromKeyCode(code: Int): Int = when (code) {
        KeyEvent.KEYCODE_BUTTON_A -> A
        KeyEvent.KEYCODE_BUTTON_B -> B
        KeyEvent.KEYCODE_BUTTON_X -> X
        KeyEvent.KEYCODE_BUTTON_Y -> Y
        KeyEvent.KEYCODE_BUTTON_L1 -> L1
        KeyEvent.KEYCODE_BUTTON_R1 -> R1
        KeyEvent.KEYCODE_BUTTON_L2 -> L2
        KeyEvent.KEYCODE_BUTTON_R2 -> R2
        KeyEvent.KEYCODE_BUTTON_THUMBL -> L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> R3
        KeyEvent.KEYCODE_BUTTON_START -> START
        KeyEvent.KEYCODE_BUTTON_SELECT -> SELECT
        KeyEvent.KEYCODE_DPAD_UP -> UP
        KeyEvent.KEYCODE_DPAD_DOWN -> DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> RIGHT
        else -> -1
    }
}

/** Tempat hasil emulasi dikirim (CallbackBridge di game, objek palsu di uji). */
interface GamepadOutput {
    fun key(glfwKey: Int, down: Boolean)
    fun mouse(button: Int, down: Boolean)
    fun scroll(amount: Double)

    /** Putar kamera (kursor game terkunci), dalam piksel. */
    fun look(dx: Float, dy: Float)

    /** Geser kursor menu, dalam piksel layar. */
    fun cursor(dx: Float, dy: Float)
}

/**
 * Menerjemahkan controller menjadi keyboard + mouse supaya Minecraft biasa (tanpa mod controller)
 * langsung bisa dimainkan. Dua mode, dipilih dari keadaan kursor game:
 * - bermain (kursor terkunci): stick kiri = WASD, stick kanan = kamera, A lompat, B jongkok,
 *   RT pukul, LT pasang/pakai, LB/RB ganti slot, Y inventaris, X tukar tangan, Start = Esc.
 * - menu (kursor bebas): stick menggeser kursor, A/RT klik kiri, X/LT klik kanan,
 *   Y Shift+klik (pindah tumpukan), B/Start = Esc, D-pad = tombol panah.
 *
 * Kelas ini tidak menyentuh Android selain konstanta tombol, jadi seluruhnya berjalan di JVM biasa.
 * Tidak ada apa pun yang berjalan saat controller diam: [tick] hanya dipanggil selama stick digerakkan.
 */
class GamepadEmulator(private val out: GamepadOutput) {

    /** Pengali kecepatan kamera dan kursor (1 = 100%). */
    var lookScale = 1.25f
    var cursorScale = 1f

    var grabbed = true
        private set

    private val keyDown = BooleanArray(Pad.COUNT)
    private val axisDown = BooleanArray(Pad.COUNT)
    private val active = BooleanArray(Pad.COUNT)
    private val issued = IntArray(Pad.COUNT)

    private var lx = 0f
    private var ly = 0f
    private var rx = 0f
    private var ry = 0f

    private var moveUp = false
    private var moveDown = false
    private var moveLeft = false
    private var moveRight = false

    /** Ganti mode (bermain/menu). Semua tombol yang sedang ditahan dilepas supaya tidak ada yang macet. */
    fun setGrabbed(value: Boolean) {
        if (value == grabbed) return
        releaseAll()
        grabbed = value
        updateMove()
    }

    /** Tombol fisik ditekan/dilepas (dari KeyEvent). */
    fun button(pad: Int, down: Boolean) {
        if (pad < 0 || pad >= Pad.COUNT) return
        keyDown[pad] = down
        refresh(pad)
    }

    /** Nilai sumbu terbaru. Dipanggil tiap MotionEvent joystick. */
    fun axes(lx: Float, ly: Float, rx: Float, ry: Float, lt: Float, rt: Float, hatX: Float, hatY: Float) {
        this.lx = lx
        this.ly = ly
        this.rx = rx
        this.ry = ry

        axisDown[Pad.L2] = hysteresis(axisDown[Pad.L2], lt, TRIGGER_ON, TRIGGER_OFF)
        axisDown[Pad.R2] = hysteresis(axisDown[Pad.R2], rt, TRIGGER_ON, TRIGGER_OFF)
        axisDown[Pad.LEFT] = hatX < -0.5f
        axisDown[Pad.RIGHT] = hatX > 0.5f
        axisDown[Pad.UP] = hatY < -0.5f
        axisDown[Pad.DOWN] = hatY > 0.5f
        refresh(Pad.L2)
        refresh(Pad.R2)
        refresh(Pad.LEFT)
        refresh(Pad.RIGHT)
        refresh(Pad.UP)
        refresh(Pad.DOWN)

        updateMove()
    }

    /** True selama stick digerakkan (butuh [tick] tiap frame). */
    fun needsTick(): Boolean {
        val right = hypot(rx, ry)
        if (grabbed) return right > DEAD_ZONE
        return right > DEAD_ZONE || hypot(lx, ly) > DEAD_ZONE
    }

    /**
     * Gerakkan kamera/kursor selama [dtSec] detik. Mengembalikan true bila masih perlu dipanggil
     * lagi di frame berikutnya.
     */
    fun tick(dtSec: Float): Boolean {
        var sx = rx
        var sy = ry
        if (!grabbed && hypot(lx, ly) > hypot(rx, ry)) {
            sx = lx
            sy = ly
        }
        val mag = hypot(sx, sy)
        if (mag <= DEAD_ZONE) return false

        val m = ((mag - DEAD_ZONE) / (1f - DEAD_ZONE)).coerceIn(0f, 1f)
        val curve = m * (0.4f + 0.6f * m)
        val dt = dtSec.coerceIn(0f, 0.05f)
        val scale = if (grabbed) LOOK_SPEED * lookScale else CURSOR_SPEED * cursorScale
        val step = curve * scale * dt
        val dx = sx / mag * step
        val dy = sy / mag * step
        if (grabbed) out.look(dx, dy) else out.cursor(dx, dy)
        return true
    }

    /** Lepas semua yang sedang ditahan emulator (tombol fisik tetap dicatat). */
    fun releaseAll() {
        for (b in 0 until Pad.COUNT) {
            val a = issued[b]
            if (a != 0) {
                issued[b] = 0
                perform(a, false)
            }
        }
        setMove(up = false, down = false, left = false, right = false)
    }

    /** Lupakan semuanya (aplikasi dijeda, controller dicabut, atau mod mengambil alih). */
    fun reset() {
        releaseAll()
        keyDown.fill(false)
        axisDown.fill(false)
        active.fill(false)
        lx = 0f
        ly = 0f
        rx = 0f
        ry = 0f
    }

    private fun refresh(pad: Int) {
        val now = keyDown[pad] || axisDown[pad]
        if (now == active[pad]) return
        active[pad] = now
        if (now) {
            val a = if (grabbed) gameAction(pad) else menuAction(pad)
            issued[pad] = a
            if (a != 0) perform(a, true)
        } else {
            val a = issued[pad]
            issued[pad] = 0
            if (a != 0) perform(a, false)
        }
    }

    private fun updateMove() {
        if (!grabbed) {
            setMove(up = false, down = false, left = false, right = false)
            return
        }
        setMove(
            up = hysteresis(moveUp, -ly, MOVE_ON, MOVE_OFF),
            down = hysteresis(moveDown, ly, MOVE_ON, MOVE_OFF),
            left = hysteresis(moveLeft, -lx, MOVE_ON, MOVE_OFF),
            right = hysteresis(moveRight, lx, MOVE_ON, MOVE_OFF)
        )
    }

    private fun setMove(up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
        if (up != moveUp) { moveUp = up; out.key(LwjglGlfwKeycode.GLFW_KEY_W, up) }
        if (down != moveDown) { moveDown = down; out.key(LwjglGlfwKeycode.GLFW_KEY_S, down) }
        if (left != moveLeft) { moveLeft = left; out.key(LwjglGlfwKeycode.GLFW_KEY_A, left) }
        if (right != moveRight) { moveRight = right; out.key(LwjglGlfwKeycode.GLFW_KEY_D, right) }
    }

    private fun perform(action: Int, down: Boolean) {
        val arg = action and 0xFFFF
        when (action ushr 16) {
            KIND_KEY -> out.key(arg, down)
            KIND_MOUSE -> out.mouse(arg, down)
            KIND_SHIFT_CLICK -> if (down) {
                out.key(LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, true)
                out.mouse(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, true)
            } else {
                out.mouse(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, false)
                out.key(LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, false)
            }
            KIND_SCROLL -> if (down) out.scroll(if (arg == SCROLL_PREV) 1.0 else -1.0)
        }
    }

    private fun gameAction(pad: Int): Int = when (pad) {
        Pad.A -> key(LwjglGlfwKeycode.GLFW_KEY_SPACE)
        Pad.B -> key(LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT)
        Pad.X -> key(LwjglGlfwKeycode.GLFW_KEY_F)
        Pad.Y -> key(LwjglGlfwKeycode.GLFW_KEY_E)
        Pad.L1, Pad.LEFT -> scroll(SCROLL_PREV)
        Pad.R1, Pad.RIGHT -> scroll(SCROLL_NEXT)
        Pad.L2 -> mouse(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT)
        Pad.R2 -> mouse(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT)
        Pad.L3 -> key(LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL)
        Pad.R3 -> mouse(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE)
        Pad.START -> key(LwjglGlfwKeycode.GLFW_KEY_ESCAPE)
        Pad.SELECT -> key(LwjglGlfwKeycode.GLFW_KEY_TAB)
        Pad.UP -> key(LwjglGlfwKeycode.GLFW_KEY_F5)
        Pad.DOWN -> key(LwjglGlfwKeycode.GLFW_KEY_Q)
        else -> 0
    }

    private fun menuAction(pad: Int): Int = when (pad) {
        Pad.A, Pad.R2 -> mouse(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT)
        Pad.X, Pad.L2 -> mouse(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT)
        Pad.Y -> (KIND_SHIFT_CLICK shl 16)
        Pad.B, Pad.START -> key(LwjglGlfwKeycode.GLFW_KEY_ESCAPE)
        Pad.L1 -> scroll(SCROLL_PREV)
        Pad.R1 -> scroll(SCROLL_NEXT)
        Pad.UP -> key(LwjglGlfwKeycode.GLFW_KEY_UP)
        Pad.DOWN -> key(LwjglGlfwKeycode.GLFW_KEY_DOWN)
        Pad.LEFT -> key(LwjglGlfwKeycode.GLFW_KEY_LEFT)
        Pad.RIGHT -> key(LwjglGlfwKeycode.GLFW_KEY_RIGHT)
        else -> 0
    }

    private fun key(code: Int) = (KIND_KEY shl 16) or code
    private fun mouse(button: Int) = (KIND_MOUSE shl 16) or button
    private fun scroll(direction: Int) = (KIND_SCROLL shl 16) or direction

    private fun hysteresis(wasOn: Boolean, value: Float, on: Float, off: Float): Boolean =
        if (wasOn) value > off else value > on

    companion object {
        private const val KIND_KEY = 1
        private const val KIND_MOUSE = 2
        private const val KIND_SHIFT_CLICK = 3
        private const val KIND_SCROLL = 4
        private const val SCROLL_PREV = 1
        private const val SCROLL_NEXT = 2

        const val DEAD_ZONE = 0.18f
        private const val MOVE_ON = 0.45f
        private const val MOVE_OFF = 0.30f
        private const val TRIGGER_ON = 0.55f
        private const val TRIGGER_OFF = 0.35f

        /** Piksel per detik pada defleksi penuh (100%). */
        private const val LOOK_SPEED = 1100f
        private const val CURSOR_SPEED = 1000f
    }
}

/**
 * Pengulang arah untuk navigasi menu launcher: satu tekanan langsung, lalu menahan stick
 * mengulang pelan-pelan seperti tombol panah keyboard.
 */
class DirectionRepeater(
    private val firstDelayMs: Long = 380L,
    private val repeatMs: Long = 110L
) {
    /** Arah aktif: kode tombol panah Android, atau 0 bila stick di tengah. */
    var dir = 0
        private set
    private var nextAt = 0L

    /** Arah baru. True bila harus langsung menekan satu kali. */
    fun set(newDir: Int, now: Long): Boolean {
        if (newDir == dir) return false
        dir = newDir
        if (newDir == 0) return false
        nextAt = now + firstDelayMs
        return true
    }

    /** True bila sudah waktunya mengulang. */
    fun due(now: Long): Boolean {
        if (dir == 0 || now < nextAt) return false
        nextAt = now + repeatMs
        return true
    }

    /** Jeda (ms) sampai pengulangan berikutnya. */
    fun delayUntilNext(now: Long): Long = if (dir == 0) 0L else (nextAt - now).coerceAtLeast(1L)

    companion object {
        private const val ON = 0.6f
        private const val OFF = 0.35f

        /** Stick/hat -> kode tombol panah Android (19 atas, 20 bawah, 21 kiri, 22 kanan) atau 0. */
        fun directionOf(x: Float, y: Float, current: Int): Int {
            val ax = abs(x)
            val ay = abs(y)
            val limit = if (current != 0) OFF else ON
            if (maxOf(ax, ay) < limit) return 0
            return if (ax > ay) {
                if (x > 0f) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            } else {
                if (y > 0f) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
            }
        }
    }
}
