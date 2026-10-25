package com.israadev.nuxlauncher.ui.control

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israadev.nuxlauncher.core.controls.TouchForwarder
import com.israadev.nuxlauncher.core.controls.TouchSink
import com.israadev.nuxlauncher.core.device.PhysicalMouseChecker
import com.israadev.nuxlauncher.ui.components.FocusableBox
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import com.movtery.zalithlauncher.bridge.CURSOR_DISABLED
import com.movtery.zalithlauncher.bridge.CURSOR_ENABLED
import com.movtery.zalithlauncher.game.sdl.SdlBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.lwjgl.glfw.CallbackBridge

enum class MouseControlMode {
    SLIDE, // Trackpad mode: Drag to move cursor, tap anywhere to click at cursor
    CLICK  // Direct touch: Tap or drag directly sets cursor to finger and clicks
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SwitchableMouseLayout(
    modifier: Modifier = Modifier,
    screenWidth: Float,
    screenHeight: Float,
    cursorMode: Int,
    controlMode: MouseControlMode,
    // Lambda supaya posisi kursor dibaca hanya saat jari bergerak, bukan saat menyusun ulang.
    cursorPosition: () -> Offset,
    cursorSensitivity: Float = 1.0f,
    touchAttackEnabled: Boolean = true,
    // Hotbar sentuh: mengembalikan slot 0..8 di titik sentuh (atau -1). Null = fitur mati.
    hotbarSlotAt: ((Offset) -> Int)? = null,
    onHotbarSelect: (Int) -> Unit = {},
    // Mod TouchController aktif: semua sentuhan jari diteruskan mentah ke penerima ini (koordinat
    // 0..1) dan lapisan ini tidak menggerakkan kamera atau kursor sendiri. Mouse fisik tetap jalan.
    touchSink: TouchSink? = null,
    // true = hanya mod yang memegang sentuhan (kamera/kursor launcher dimatikan). false = launcher
    // bekerja seperti biasa dan jari yang menyentuh area game saat bermain juga dikirim ke mod.
    touchExclusive: Boolean = false,
    requestPointerCapture: Boolean = false,
    onCursorPositionChange: (Offset) -> Unit,
    onPhysicalMouseModeChange: (Boolean) -> Unit = {},
    onMouse: () -> Unit = {},
    onTouch: () -> Unit = {},
    onTap: (Offset) -> Unit = { pos ->
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
    onLongPress: (Offset) -> Unit = {
        CallbackBridge.putMouseEvent(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, true)
    },
    onLongPressEnd: () -> Unit = {
        CallbackBridge.putMouseEvent(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, false)
    },
    onCapturedMove: (Offset) -> Unit = { delta ->
        CallbackBridge.sendCursorDelta(delta.x, delta.y)
    }
) {
    val coroutineScope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    val currentControlMode by rememberUpdatedState(controlMode)
    val currentCursorMode by rememberUpdatedState(cursorMode)
    val currentCursorPos by rememberUpdatedState(cursorPosition)
    val currentSensitivity by rememberUpdatedState(cursorSensitivity)
    val currentTouchAttack by rememberUpdatedState(touchAttackEnabled)
    val currentHotbarSlotAt by rememberUpdatedState(hotbarSlotAt)
    val currentOnHotbarSelect by rememberUpdatedState(onHotbarSelect)
    val currentTouchSink by rememberUpdatedState(touchSink)
    val currentTouchExclusive by rememberUpdatedState(touchExclusive)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnLongPressEnd by rememberUpdatedState(onLongPressEnd)
    val currentOnCapturedMove by rememberUpdatedState(onCapturedMove)
    val currentOnCursorPositionChange by rememberUpdatedState(onCursorPositionChange)
    val currentOnPhysicalMouseModeChange by rememberUpdatedState(onPhysicalMouseModeChange)
    val currentOnMouse by rememberUpdatedState(onMouse)
    val currentOnTouch by rememberUpdatedState(onTouch)

    val isCaptured = currentCursorMode == CURSOR_DISABLED

    var isPhysicalMouseMode by remember {
        mutableStateOf(
            if (PhysicalMouseChecker.physicalMouseConnected) {
                !requestPointerCapture
            } else {
                false
            }
        )
    }

    fun checkPhysicalMouseMode(using: Boolean) {
        val newMode = !requestPointerCapture && using
        if (isPhysicalMouseMode != newMode) {
            isPhysicalMouseMode = newMode
            currentOnPhysicalMouseModeChange(newMode)
        }
    }

    // In gameplay mode (CURSOR_DISABLED), pointer capture is mandatory so external mouse controls camera
    val capturePointer = isCaptured || requestPointerCapture

    val composeFocusCount by SdlBridge.composeFocus.collectAsStateWithLifecycle()

    fun sendScaledCursor(x: Float, y: Float) {
        val winW = CallbackBridge.windowWidth
        val winH = CallbackBridge.windowHeight
        val targetX = if (screenWidth > 0 && winW > 0) x * (winW.toFloat() / screenWidth) else x
        val targetY = if (screenHeight > 0 && winH > 0) y * (winH.toFloat() / screenHeight) else y
        CallbackBridge.sendCursorPos(targetX, targetY)
    }

    FocusableBox(
        modifier = modifier
            .fillMaxSize()
            // 1. Touchscreen input processing
            .pointerInput(cursorMode, controlMode) {
                coroutineScope {
                    var activePointerId: PointerId? = null
                    var startPosition = Offset.Zero
                    var isDragging = false
                    var longPressTriggered = false
                    var longPressJob: Job? = null
                    // Slot hotbar yang disentuh jari ini (-1 = bukan hotbar). Hanya saat bermain.
                    var hotbarSlot = -1
                    // Jari yang sedang diteruskan ke mod pada mode berdampingan.
                    val forwarder = TouchForwarder()

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()

                            // Mod TouchController (lihat touchSink/touchExclusive di atas).
                            val sink = currentTouchSink
                            if (sink != null && !currentTouchExclusive) {
                                // Berdampingan: sentuhan tetap diproses launcher di bawah (tidak dikonsumsi),
                                // dan salinannya dikirim ke mod hanya saat bermain (kursor terkunci). Di menu
                                // game launcher yang bekerja sendiri, kalau tidak klik akan terhitung dua kali.
                                val invW = if (screenWidth > 0f) 1f / screenWidth else 0f
                                val invH = if (screenHeight > 0f) 1f / screenHeight else 0f
                                val fChanges = event.changes
                                for (ci in fChanges.indices) {
                                    val c = fChanges[ci]
                                    if (c.type != PointerType.Touch) continue
                                    val key = c.id.value
                                    if (c.changedToDown()) {
                                        // Jari yang sudah dipakai tombol launcher (sudah dikonsumsi) tidak ikut dikirim.
                                        if (!c.isConsumed && currentCursorMode == CURSOR_DISABLED) {
                                            forwarder.down(sink, key, c.position.x * invW, c.position.y * invH)
                                        }
                                    } else if (c.changedToUpIgnoreConsumed()) {
                                        forwarder.up(sink, key)
                                    } else if (c.pressed && c.positionChanged()) {
                                        forwarder.move(sink, key, c.position.x * invW, c.position.y * invH)
                                    }
                                }
                            } else if (sink != null) {
                                if (activePointerId != null) {
                                    longPressJob?.cancel()
                                    activePointerId = null
                                    hotbarSlot = -1
                                    isDragging = false
                                }
                                val sinkChanges = event.changes
                                val invW = if (screenWidth > 0f) 1f / screenWidth else 0f
                                val invH = if (screenHeight > 0f) 1f / screenHeight else 0f
                                for (ci in sinkChanges.indices) {
                                    val c = sinkChanges[ci]
                                    if (c.type != PointerType.Touch) continue
                                    if (c.changedToDown()) {
                                        checkPhysicalMouseMode(false)
                                        currentOnTouch()
                                        sink.onDown(c.id.value, c.position.x * invW, c.position.y * invH)
                                        c.consume()
                                    } else if (c.changedToUpIgnoreConsumed()) {
                                        sink.onUp(c.id.value)
                                        c.consume()
                                    } else if (c.pressed && c.positionChanged()) {
                                        sink.onMove(c.id.value, c.position.x * invW, c.position.y * invH)
                                        c.consume()
                                    }
                                }
                                continue
                            }

                            // Touch Down (perulangan biasa: tanpa membuat daftar baru di tiap kejadian sentuh)
                            val changes = event.changes
                            for (ci in changes.indices) {
                                val downEvent = changes[ci]
                                if (!downEvent.changedToDown() || downEvent.type != PointerType.Touch) {
                                    continue
                                }

                                checkPhysicalMouseMode(false)
                                currentOnTouch()

                                if (activePointerId == null && !downEvent.isConsumed) {
                                    activePointerId = downEvent.id
                                    startPosition = downEvent.position
                                    isDragging = false
                                    longPressTriggered = false

                                    val capturedNow = currentCursorMode == CURSOR_DISABLED
                                    hotbarSlot = if (capturedNow) {
                                        currentHotbarSlotAt?.invoke(downEvent.position) ?: -1
                                    } else {
                                        -1
                                    }

                                    if (!capturedNow && currentControlMode == MouseControlMode.CLICK) {
                                        currentOnCursorPositionChange(downEvent.position)
                                        sendScaledCursor(downEvent.position.x, downEvent.position.y)
                                    }

                                    longPressJob?.cancel()
                                    longPressJob = launch {
                                        delay(viewConfiguration.longPressTimeoutMillis)
                                        // Saat bermain (kursor terkunci), tahan di layar hanya memukul bila diizinkan
                                        if (!isDragging && activePointerId == downEvent.id && hotbarSlot < 0 &&
                                            (!capturedNow || currentTouchAttack)
                                        ) {
                                            longPressTriggered = true
                                            val targetPos = if (capturedNow) {
                                                Offset.Zero
                                            } else if (currentControlMode == MouseControlMode.CLICK) {
                                                startPosition
                                            } else {
                                                currentCursorPos()
                                            }
                                            currentOnLongPress(targetPos)
                                        }
                                    }
                                }
                            }

                            // Touch Move
                            activePointerId?.let { pointerId ->
                                event.changes.firstFast { it.id == pointerId && it.positionChanged() && !it.isConsumed }?.let { moveChange ->
                                    val distanceFromStart = (moveChange.position - startPosition).getDistance()
                                    if (!isDragging && distanceFromStart > viewConfiguration.touchSlop) {
                                        isDragging = true
                                        longPressJob?.cancel()
                                    }

                                    if (currentCursorMode == CURSOR_DISABLED) {
                                        // In gameplay: touch drag rotates the camera. Ketukan di hotbar tidak
                                        // menggoyang kamera selama jari belum benar-benar menggeser.
                                        if (hotbarSlot < 0 || isDragging) {
                                            val delta = moveChange.positionChange()
                                            currentOnCapturedMove(delta)
                                        }
                                    } else {
                                        // In menu: move cursor
                                        if (currentControlMode == MouseControlMode.SLIDE) {
                                            if (isDragging || longPressTriggered) {
                                                val delta = moveChange.positionChange()
                                                val cur = currentCursorPos()
                                                val newX = (cur.x + delta.x * currentSensitivity).coerceIn(0f, screenWidth)
                                                val newY = (cur.y + delta.y * currentSensitivity).coerceIn(0f, screenHeight)
                                                val newPos = Offset(newX, newY)
                                                currentOnCursorPositionChange(newPos)
                                                sendScaledCursor(newX, newY)
                                            }
                                        } else {
                                            currentOnCursorPositionChange(moveChange.position)
                                            sendScaledCursor(moveChange.position.x, moveChange.position.y)
                                        }
                                    }
                                    moveChange.consume()
                                }
                            }

                            // Touch Up
                            activePointerId?.let { pointerId ->
                                event.changes.firstFast { it.id == pointerId && it.changedToUpIgnoreConsumed() }?.let { upChange ->
                                    longPressJob?.cancel()
                                    if (longPressTriggered) {
                                        currentOnLongPressEnd()
                                    } else if (!isDragging) {
                                        if (hotbarSlot >= 0 && currentCursorMode == CURSOR_DISABLED) {
                                            // Ketuk hotbar = pilih slot (gaya Bedrock)
                                            currentOnHotbarSelect(hotbarSlot)
                                        } else if (currentCursorMode == CURSOR_DISABLED) {
                                            // Ketuk layar saat bermain = pukul; hanya bila diizinkan di Pengaturan
                                            if (currentTouchAttack) {
                                                CallbackBridge.putMouseEvent(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT)
                                            }
                                        } else {
                                            val clickPos = if (currentControlMode == MouseControlMode.CLICK) {
                                                upChange.position
                                            } else {
                                                currentCursorPos()
                                            }
                                            currentOnTap(clickPos)
                                        }
                                    }
                                    upChange.consume()
                                    activePointerId = null
                                    hotbarSlot = -1
                                }
                            }

                            if (!event.changes.anyPressed()) {
                                activePointerId = null
                                hotbarSlot = -1
                                isDragging = false
                                longPressJob?.cancel()
                            }
                        }
                    }
                }
            }
            // 2. Physical mouse events in menu mode (when not captured)
            .then(
                Modifier.mouseEventModifier(
                    disabled = capturePointer,
                    onMouse = {
                        checkPhysicalMouseMode(true)
                        currentOnMouse()
                    },
                    onMouseMove = { pos ->
                        val newX = pos.x.coerceIn(0f, screenWidth)
                        val newY = pos.y.coerceIn(0f, screenHeight)
                        val newPos = Offset(newX, newY)
                        currentOnCursorPositionChange(newPos)
                        sendScaledCursor(newX, newY)
                    },
                    onMouseScroll = { scroll ->
                        CallbackBridge.sendScroll(scroll.x.toDouble(), scroll.y.toDouble())
                    },
                    onMouseButton = { button, pressed ->
                        val glfwBtn = when (button) {
                            MotionEvent.BUTTON_PRIMARY -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT
                            MotionEvent.BUTTON_SECONDARY, MotionEvent.BUTTON_STYLUS_SECONDARY -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT
                            MotionEvent.BUTTON_TERTIARY -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE
                            MotionEvent.BUTTON_BACK -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT
                            else -> null
                        }
                        if (glfwBtn != null) {
                            CallbackBridge.sendMouseButton(glfwBtn, pressed)
                        }
                    }
                )
            ),
        requestKey = cursorMode to composeFocusCount
    )

    // 3. Hardware Pointer Capture (Active when in game / CURSOR_DISABLED)
    SimpleMouseCapture(
        enabled = capturePointer,
        onMouse = {
            checkPhysicalMouseMode(true)
            currentOnMouse()
        },
        onMouseMove = { delta ->
            currentOnCapturedMove(delta)
        },
        onMouseScroll = { scroll ->
            CallbackBridge.sendScroll(scroll.x.toDouble(), scroll.y.toDouble())
        },
        onMouseButton = { button, pressed ->
            val glfwBtn = when (button) {
                MotionEvent.BUTTON_PRIMARY -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT
                MotionEvent.BUTTON_SECONDARY, MotionEvent.BUTTON_STYLUS_SECONDARY -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT
                MotionEvent.BUTTON_TERTIARY -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE
                MotionEvent.BUTTON_BACK -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT
                else -> null
            }
            if (glfwBtn != null) {
                CallbackBridge.sendMouseButton(glfwBtn, pressed)
            }
        }
    )
}

/** Pengganti firstOrNull untuk jalur sentuhan: perulangan berindeks tanpa membuat iterator. */
internal inline fun List<PointerInputChange>.firstFast(predicate: (PointerInputChange) -> Boolean): PointerInputChange? {
    for (i in indices) {
        val c = this[i]
        if (predicate(c)) return c
    }
    return null
}

internal fun List<PointerInputChange>.anyPressed(): Boolean {
    for (i in indices) {
        if (this[i].pressed) return true
    }
    return false
}

@Composable
private fun SimpleMouseCapture(
    enabled: Boolean,
    onMouse: () -> Unit,
    onMouseMove: (Offset) -> Unit,
    onMouseScroll: (Offset) -> Unit,
    onMouseButton: (button: Int, pressed: Boolean) -> Unit
) {
    val view = LocalView.current
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

        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (enabled && hasFocus) {
                view.requestPointerCapture()
            }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        syncCaptureState()

        if (enabled) {
            val pointerListener = View.OnCapturedPointerListener { _, event ->
                currentOnMouse()
                when (event.actionMasked) {
                    MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                        val relX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                        val relY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                        var deltaX = relX
                        var deltaY = relY

                        val historySize = event.historySize
                        for (i in 0 until historySize) {
                            deltaX += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, i)
                            deltaY += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, i)
                        }

                        // Fallback only if relative axes are not provided and event.x/y is relative
                        if (deltaX == 0f && deltaY == 0f && (event.x != 0f || event.y != 0f)) {
                            if (Math.abs(event.x) < 300f && Math.abs(event.y) < 300f) {
                                deltaX = event.x
                                deltaY = event.y
                            }
                        }

                        currentOnMouseMove(Offset(deltaX, deltaY))
                        true
                    }
                    MotionEvent.ACTION_SCROLL -> {
                        val scrollX = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                        val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                        currentOnMouseScroll(Offset(scrollX, scrollY))
                        true
                    }
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_BUTTON_PRESS -> {
                        currentOnMouseButton(event.actionButton, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_BUTTON_RELEASE -> {
                        currentOnMouseButton(event.actionButton, false)
                        true
                    }
                    else -> false
                }
            }
            view.setOnCapturedPointerListener(pointerListener)
        } else {
            view.setOnCapturedPointerListener(null)
        }

        onDispose {
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            view.setOnCapturedPointerListener(null)
        }
    }
}

/**
 * Intercept hardware mouse events (movement, clicks, scroll wheel) in uncaptured / menu mode
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.mouseEventModifier(
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

    var lastButtons by remember { mutableIntStateOf(0) }

    pointerInteropFilter { event ->
        if (currentDisabled) {
            return@pointerInteropFilter false
        }

        val isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE) ||
                event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)
        val isStylus = event.isFromSource(InputDevice.SOURCE_STYLUS)
        if (!isMouse && !isStylus) {
            return@pointerInteropFilter false
        }

        currentOnMouse()

        val buttons = event.buttonState
        val changed = lastButtons xor buttons

        fun dispatchButton(button: Int) {
            if (changed and button != 0) {
                val pressed = buttons and button != 0
                currentOnMouseButton(button, pressed)
            }
        }

        dispatchButton(MotionEvent.BUTTON_PRIMARY)
        dispatchButton(MotionEvent.BUTTON_SECONDARY)
        dispatchButton(MotionEvent.BUTTON_TERTIARY)
        dispatchButton(MotionEvent.BUTTON_BACK)
        dispatchButton(MotionEvent.BUTTON_FORWARD)
        dispatchButton(MotionEvent.BUTTON_STYLUS_SECONDARY)

        lastButtons = buttons

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_MOVE -> {
                currentOnMouseMove(Offset(event.x, event.y))
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
