package com.israadev.nuxlauncher.ui.control

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import com.movtery.zalithlauncher.game.sdl.SdlBridge
import kotlin.math.hypot

/*
 * Mouse fisik (mouse sistem Android), mengikuti cara Zalith Launcher 2.
 *
 * Dua jalur, dan hanya satu yang aktif pada satu waktu:
 *  - Menu game (kursor Minecraft tampil): Android tidak menangkap mouse. Posisi absolut dari event
 *    hover/move dikirim sebagai posisi kursor GLFW, dan penunjuk yang terlihat adalah penunjuk sistem
 *    Android (bentuknya mengikuti bentuk kursor game). Launcher tidak menggambar penunjuk sendiri.
 *  - Bermain (kursor Minecraft terkunci): mouse ditangkap lewat pointer capture, gerakan relatif dikirim
 *    sebagai delta kamera.
 *
 * Semuanya digerakkan event, tanpa polling dan tanpa thread tambahan.
 */

/** Tombol Android -> tombol GLFW. Tombol samping (BACK/FORWARD) sengaja tidak dipetakan. */
internal object MouseButtons {
    fun toGlfw(button: Int): Int? = when (button) {
        MotionEvent.BUTTON_PRIMARY -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT
        MotionEvent.BUTTON_SECONDARY, MotionEvent.BUTTON_STYLUS_SECONDARY -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT
        MotionEvent.BUTTON_TERTIARY -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE
        // BUTTON_BACK dari mouse sering datang juga sebagai KEYCODE_BACK; itu sudah diubah jadi klik kanan
        // di GameActivity.dispatchKeyEvent. Dipetakan lagi di sini akan membuat klik kanan terkirim dua kali.
        else -> null
    }
}

/**
 * Pelacak tombol mouse yang ditekan. Hanya mengirim PERUBAHAN status, jadi snapshot yang berulang
 * tidak menghasilkan klik ganda. Murni (tanpa Compose), mudah diuji.
 *
 * Mode tangkap kadang mengirim ACTION_DOWN/UP dengan actionButton = 0; status tombol selalu diambil
 * dari buttonState, bukan dari actionButton.
 */
internal class CapturedMouseButtons(private val onMouseButton: (Int, Boolean) -> Unit) {
    private var heldButtons = 0

    fun onEvent(action: Int, actionButton: Int, buttonState: Int) {
        if (action == MotionEvent.ACTION_CANCEL) {
            reset()
            return
        }

        val buttons = when (action) {
            MotionEvent.ACTION_BUTTON_PRESS -> buttonState or actionButton
            MotionEvent.ACTION_BUTTON_RELEASE -> buttonState and actionButton.inv()
            else -> buttonState
        }
        update(buttons and SUPPORTED_BUTTONS)
    }

    /** Jalur menu: buttonState dari event hover/move dipakai apa adanya. */
    fun onSnapshot(buttonState: Int) {
        update(buttonState and SUPPORTED_BUTTONS)
    }

    /** Lepaskan semua tombol yang masih tertahan (sekali saja per tombol). */
    fun reset() {
        update(0)
    }

    private fun update(buttons: Int) {
        val changed = heldButtons xor buttons
        heldButtons = buttons
        if (changed == 0) return

        fun dispatchButton(button: Int) {
            if (changed and button != 0) {
                onMouseButton(button, buttons and button != 0)
            }
        }

        dispatchButton(MotionEvent.BUTTON_PRIMARY)
        dispatchButton(MotionEvent.BUTTON_SECONDARY)
        dispatchButton(MotionEvent.BUTTON_TERTIARY)
        dispatchButton(MotionEvent.BUTTON_BACK)
        dispatchButton(MotionEvent.BUTTON_FORWARD)
        dispatchButton(MotionEvent.BUTTON_STYLUS_SECONDARY)
    }

    companion object {
        private const val SUPPORTED_BUTTONS = MotionEvent.BUTTON_PRIMARY or
            MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY or
            MotionEvent.BUTTON_BACK or MotionEvent.BUTTON_FORWARD or
            MotionEvent.BUTTON_STYLUS_SECONDARY
    }
}

/**
 * Catatan diagnosis. Menulis SATU baris per jalur per sesi (maksimal beberapa baris), dipicu event
 * pertama di jalur itu. Tidak ada biaya per event. Dipakai untuk melihat dari log perangkat mana
 * mouse masuk lewat jalur apa, karena mouse yang "tidak bekerja" biasanya soal jalur masuknya.
 */
internal object MouseDiag {
    @Volatile
    var sink: ((String) -> Unit)? = null
    private val seen = HashSet<String>()

    fun once(path: String, source: Int, toolType: Int, action: Int, buttons: Int) {
        val out = sink ?: return
        synchronized(seen) {
            if (!seen.add(path)) return
        }
        out("[Mouse] jalur=$path sumber=0x${Integer.toHexString(source)} alat=$toolType aksi=$action tombol=$buttons")
    }
}

/**
 * Menangkap mouse fisik saat game mengunci kursor (bermain). Gerakan relatif jadi delta kamera.
 * Touchpad yang ikut tertangkap diterjemahkan: selisih posisi jadi gerakan, dua jari jadi gulir
 * dan ketuk dua jari jadi klik kanan.
 */
@Composable
internal fun PhysicalMouseCapture(
    enabled: Boolean,
    onMouse: () -> Unit,
    onMouseMove: (Offset) -> Unit,
    onMouseScroll: (Offset) -> Unit,
    onMouseButton: (button: Int, pressed: Boolean) -> Unit
) {
    val view by rememberUpdatedState(LocalView.current)
    val currentOnMouse by rememberUpdatedState(onMouse)
    val currentOnMouseMove by rememberUpdatedState(onMouseMove)
    val currentOnMouseScroll by rememberUpdatedState(onMouseScroll)
    val currentOnMouseButton by rememberUpdatedState(onMouseButton)

    fun syncCaptureState() {
        if (enabled) {
            if (view.hasWindowFocus()) {
                view.requestPointerCapture()
            }
        } else {
            view.releasePointerCapture()
        }
    }

    val composeFocus by SdlBridge.composeFocus.collectAsStateWithLifecycle()
    LaunchedEffect(composeFocus) {
        syncCaptureState()
    }

    DisposableEffect(view, enabled) {
        view.setOnCapturedPointerListener(null)
        val physicalButtons = CapturedMouseButtons { button, pressed ->
            currentOnMouseButton(button, pressed)
        }

        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (enabled && hasFocus) {
                view.requestPointerCapture()
            } else if (!hasFocus) {
                physicalButtons.reset()
            }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)

        syncCaptureState()

        if (enabled) {
            // Event touchpad membawa x/y absolut di permukaan pad, bukan selisih. Bila sumbu relatif
            // tidak tersedia, selisih dihitung dari dua posisi berurutan.
            var touchpadLastPos: Offset? = null
            val twoFingerGesture = TouchpadTwoFingerGesture()
            // Touchpad gaya sentuh melaporkan sentuhan pad sebagai DOWN/UP; selama menempel dianggap klik kiri.
            var touchpadLeftPressed = false
            // Bila klik kiri sudah dibatalkan saat dua jari turun, lepasnya jari terakhir tidak dikirim lagi.
            var touchpadLeftSuppressed = false

            val pointerListener = View.OnCapturedPointerListener { _, event ->
                val isTouchpad = event.isFromSource(InputDevice.SOURCE_TOUCHPAD)
                MouseDiag.once(
                    if (isTouchpad) "tangkap-touchpad" else "tangkap",
                    event.source, event.getToolType(0), event.actionMasked, event.buttonState
                )
                if (!isTouchpad) {
                    physicalButtons.onEvent(event.actionMasked, event.actionButton, event.buttonState)
                }

                if (isTouchpad) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_POINTER_DOWN,
                        MotionEvent.ACTION_HOVER_ENTER -> touchpadLastPos = Offset(event.x, event.y)

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_POINTER_UP,
                        MotionEvent.ACTION_HOVER_EXIT -> touchpadLastPos = null
                    }

                    // Cadangan dua jari bila firmware tidak membuat event gulir/klik kanan sendiri.
                    when (event.actionMasked) {
                        MotionEvent.ACTION_POINTER_DOWN ->
                            if (event.pointerCount >= 2 && !twoFingerGesture.isActive()) {
                                twoFingerGesture.start(event)
                                if (touchpadLeftPressed) {
                                    currentOnMouseButton(MotionEvent.BUTTON_PRIMARY, false)
                                    touchpadLeftPressed = false
                                    touchpadLeftSuppressed = true
                                }
                            }

                        MotionEvent.ACTION_MOVE ->
                            if (twoFingerGesture.isActive()) {
                                if (event.pointerCount >= 2) {
                                    twoFingerGesture.onMove(event)?.let { notches ->
                                        if (notches.x != 0f || notches.y != 0f) {
                                            currentOnMouseScroll(notches)
                                        }
                                    }
                                }
                                // Gerak dua jari dihitung sebagai gulir, bukan memutar kamera.
                                return@OnCapturedPointerListener true
                            }

                        MotionEvent.ACTION_POINTER_UP ->
                            if (twoFingerGesture.isActive() && twoFingerGesture.onFingerUp(event)) {
                                currentOnMouseButton(MotionEvent.BUTTON_SECONDARY, true)
                                currentOnMouseButton(MotionEvent.BUTTON_SECONDARY, false)
                            }

                        MotionEvent.ACTION_UP ->
                            if (twoFingerGesture.isActive()) {
                                twoFingerGesture.end()
                                if (touchpadLeftSuppressed) {
                                    touchpadLeftSuppressed = false
                                    return@OnCapturedPointerListener true
                                }
                            }

                        MotionEvent.ACTION_CANCEL ->
                            if (twoFingerGesture.isActive()) {
                                twoFingerGesture.end()
                                touchpadLeftPressed = false
                                touchpadLeftSuppressed = false
                            }
                    }
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                        var deltaX = 0f
                        var deltaY = 0f

                        val relX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                        val relY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                        if (isTouchpad) {
                            if (relX != 0f || relY != 0f) {
                                deltaX += relX
                                deltaY += relY
                            } else {
                                touchpadLastPos?.let { last ->
                                    deltaX += event.x - last.x
                                    deltaY += event.y - last.y
                                }
                                touchpadLastPos = Offset(event.x, event.y)
                            }
                        } else {
                            deltaX += if (relX != 0f) relX else event.x
                            deltaY += if (relY != 0f) relY else event.y
                        }

                        val historySize = event.historySize
                        for (i in 0 until historySize) {
                            deltaX += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, i)
                            deltaY += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, i)
                        }

                        currentOnMouse()
                        currentOnMouseMove(Offset(deltaX, deltaY))
                        true
                    }
                    MotionEvent.ACTION_SCROLL -> {
                        currentOnMouseScroll(
                            Offset(
                                event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                                event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                            )
                        )
                        true
                    }
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_BUTTON_PRESS -> {
                        if (isTouchpad && event.actionMasked == MotionEvent.ACTION_DOWN) {
                            touchpadLeftPressed = true
                            touchpadLeftSuppressed = false
                        }
                        if (isTouchpad) currentOnMouseButton(event.actionButton, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_BUTTON_RELEASE -> {
                        if (isTouchpad && event.actionMasked == MotionEvent.ACTION_UP) {
                            touchpadLeftPressed = false
                        }
                        if (isTouchpad) currentOnMouseButton(event.actionButton, false)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> !isTouchpad
                    else -> false
                }
            }

            view.setOnCapturedPointerListener(pointerListener)
        } else {
            view.setOnCapturedPointerListener(null)
        }

        onDispose {
            physicalButtons.reset()
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            view.setOnCapturedPointerListener(null)
        }
    }
}

/**
 * Mouse fisik di menu game (kursor tidak terkunci): posisi absolut, klik, dan roda gulir.
 * Menolak event yang bukan mouse/stylus supaya sentuhan jari tetap lewat ke penangan sentuh.
 *
 * Tombol yang masih tertahan dilepas saat modifier ini dinonaktifkan (mis. klik "Play" lalu game
 * langsung menangkap mouse), supaya tidak ada tombol yang tersangkut dan klik pertama sesudahnya
 * tidak hilang.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.physicalMouseHover(
    disabled: Boolean,
    onMouse: () -> Unit = {},
    onMouseMove: (Offset) -> Unit = {},
    onMouseScroll: (Offset) -> Unit = {},
    onMouseButton: (Int, Boolean) -> Unit = { _, _ -> },
) = composed {
    val currentDisabled by rememberUpdatedState(disabled)
    val currentOnMouse by rememberUpdatedState(onMouse)
    val currentOnMouseMove by rememberUpdatedState(onMouseMove)
    val currentOnMouseScroll by rememberUpdatedState(onMouseScroll)
    val currentOnMouseButton by rememberUpdatedState(onMouseButton)

    val buttons = remember {
        CapturedMouseButtons { button, pressed -> currentOnMouseButton(button, pressed) }
    }

    LaunchedEffect(disabled) {
        if (disabled) buttons.reset()
    }

    pointerInteropFilter { event ->
        if (currentDisabled) {
            return@pointerInteropFilter false
        }

        val isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE)
        // Stylus (Chromebook, Samsung, dsb.) diperlakukan seperti mouse.
        val isStylus = event.isFromSource(InputDevice.SOURCE_STYLUS)
        if (!isMouse && !isStylus) {
            return@pointerInteropFilter false
        }

        MouseDiag.once(
            if (isMouse) "menu-mouse" else "menu-stylus",
            event.source, event.getToolType(0), event.actionMasked, event.buttonState
        )
        currentOnMouse()

        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            buttons.reset()
            return@pointerInteropFilter true
        }

        buttons.onSnapshot(event.buttonState)

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_MOVE -> {
                currentOnMouseMove(Offset(event.x, event.y))
            }

            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isStylus) {
                    currentOnMouseButton(MotionEvent.BUTTON_PRIMARY, true)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                if (isStylus) {
                    currentOnMouseButton(MotionEvent.BUTTON_PRIMARY, false)
                }
            }

            MotionEvent.ACTION_SCROLL -> {
                currentOnMouseScroll(
                    Offset(
                        event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                        event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                    )
                )
            }
        }
        true
    }
}

/**
 * Cadangan dua jari di touchpad: tarik dua jari = roda gulir, ketuk dua jari = klik kanan.
 * Ambang dan jarak per langkah gulir mengikuti ukuran permukaan pad.
 */
private class TouchpadTwoFingerGesture {
    private var active = false
    private var tapCandidate = true
    private var downTime = 0L
    private var startCentroid: Offset? = null
    private var lastCentroid: Offset? = null
    private var remainderX = 0f
    private var remainderY = 0f
    private var tapSlop = DEFAULT_TAP_SLOP
    private var scrollNotch = DEFAULT_SCROLL_NOTCH

    fun isActive() = active

    /** Jari kedua turun: mulai gestur. */
    fun start(event: MotionEvent) {
        active = true
        tapCandidate = true
        downTime = event.eventTime
        remainderX = 0f
        remainderY = 0f

        event.device?.let { device ->
            device.getMotionRange(MotionEvent.AXIS_X)?.let {
                val width = it.max - it.min
                if (width > 0f) tapSlop = width * 0.02f
            }
            device.getMotionRange(MotionEvent.AXIS_Y)?.let {
                val height = it.max - it.min
                if (height > 0f) scrollNotch = height * 0.12f
            }
        }

        startCentroid = centroid(event)
        lastCentroid = startCentroid
    }

    /** Null selama masih dalam batas ketukan; sesudahnya jumlah langkah gulir (horizontal, vertikal). */
    fun onMove(event: MotionEvent): Offset? {
        val centroid = centroid(event)
        val last = lastCentroid
        lastCentroid = centroid
        val start = startCentroid
        if (last == null || start == null) return null

        if (tapCandidate && hypot(centroid.x - start.x, centroid.y - start.y) > tapSlop) {
            tapCandidate = false
        }
        if (tapCandidate) return null

        remainderX += centroid.x - last.x
        remainderY -= centroid.y - last.y
        val notchesX = (remainderX / scrollNotch).toInt()
        val notchesY = (remainderY / scrollNotch).toInt()
        remainderX -= notchesX * scrollNotch
        remainderY -= notchesY * scrollNotch
        return Offset(notchesX.toFloat(), notchesY.toFloat())
    }

    /** Satu jari terangkat: true bila masih memenuhi syarat ketukan dua jari (hanya sekali). */
    fun onFingerUp(event: MotionEvent): Boolean {
        val isTap = active && tapCandidate && event.eventTime - downTime <= TAP_TIMEOUT_MS
        if (isTap) tapCandidate = false
        return isTap
    }

    fun end() {
        active = false
        lastCentroid = null
    }

    private fun centroid(event: MotionEvent): Offset {
        var x = 0f
        var y = 0f
        for (i in 0 until event.pointerCount) {
            x += event.getX(i)
            y += event.getY(i)
        }
        return Offset(x / event.pointerCount, y / event.pointerCount)
    }

    companion object {
        private const val DEFAULT_TAP_SLOP = 12f
        private const val DEFAULT_SCROLL_NOTCH = 60f
        private const val TAP_TIMEOUT_MS = 300L
    }
}
