package com.israadev.nuxlauncher.ui.control

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israadev.nuxlauncher.core.controls.TouchForwarder
import com.israadev.nuxlauncher.core.controls.TouchSink
import com.israadev.nuxlauncher.core.device.PhysicalMouseChecker
import com.israadev.nuxlauncher.ui.components.FocusableBox
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import com.movtery.zalithlauncher.bridge.CURSOR_DISABLED
import com.movtery.zalithlauncher.bridge.CURSOR_ENABLED
import com.movtery.zalithlauncher.bridge.ZLBridgeStates
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
    // Mod TouchController tersambung: sentuhan jari saat bermain juga diteruskan mentah ke penerima ini
    // (koordinat 0..1), sementara lapisan ini tetap bekerja seperti biasa.
    touchSink: TouchSink? = null,
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
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnLongPressEnd by rememberUpdatedState(onLongPressEnd)
    val currentOnCapturedMove by rememberUpdatedState(onCapturedMove)
    val currentOnCursorPositionChange by rememberUpdatedState(onCursorPositionChange)
    val currentOnPhysicalMouseModeChange by rememberUpdatedState(onPhysicalMouseModeChange)
    val currentOnMouse by rememberUpdatedState(onMouse)
    val currentOnTouch by rememberUpdatedState(onTouch)
    // Blok sentuh di bawah tidak pernah dimulai ulang, jadi nilai yang bisa berubah dibaca lewat State.
    val currentScreenWidth by rememberUpdatedState(screenWidth)
    val currentScreenHeight by rememberUpdatedState(screenHeight)

    val isCaptured = currentCursorMode == CURSOR_DISABLED

    // Mouse fisik sedang dipakai (diketahui dari event, bukan dari pengaturan). Menyembunyikan penunjuk
    // virtual launcher; yang terlihat adalah penunjuk sistem Android.
    var isPhysicalMouseMode by remember { mutableStateOf(PhysicalMouseChecker.physicalMouseConnected) }

    fun checkPhysicalMouseMode(using: Boolean) {
        if (isPhysicalMouseMode != using) {
            isPhysicalMouseMode = using
            currentOnPhysicalMouseModeChange(using)
        }
    }

    // Saat bermain (kursor game terkunci) mouse fisik ditangkap Android dan menggerakkan kamera.
    // Di menu mouse tidak ditangkap: Android memberi posisi absolut dan menggambar penunjuknya sendiri.
    val capturePointer = isCaptured

    // Bentuk penunjuk sistem mengikuti bentuk kursor game. State-nya hanya dibaca di FocusableBox,
    // jadi pergantian bentuk tidak menyusun ulang lapisan ini.
    val cursorShapeState = ZLBridgeStates.cursorShape.collectAsStateWithLifecycle()
    val hoverIcon = remember { derivedStateOf { cursorShapeState.value.composeIcon } }

    // Penerus tombol dan gulir mouse fisik ke game. Satu instance dipakai kedua jalur (menu dan bermain).
    val sendMouseButton = remember<(Int, Boolean) -> Unit> {
        { button, pressed ->
            val glfwBtn = MouseButtons.toGlfw(button)
            if (glfwBtn != null) {
                MouseDiag.buttonsSent++
                CallbackBridge.sendMouseButton(glfwBtn, pressed)
            } else {
                MouseDiag.buttonsDropped++
            }
        }
    }
    val sendMouseScroll = remember<(Offset) -> Unit> {
        { scroll ->
            MouseDiag.scrollSent++
            CallbackBridge.sendScroll(scroll.x.toDouble(), scroll.y.toDouble())
        }
    }

    val composeFocusCount by SdlBridge.composeFocus.collectAsStateWithLifecycle()

    fun sendScaledCursor(x: Float, y: Float) {
        val winW = CallbackBridge.windowWidth
        val winH = CallbackBridge.windowHeight
        val sw = currentScreenWidth
        val sh = currentScreenHeight
        val targetX = if (sw > 0 && winW > 0) x * (winW.toFloat() / sw) else x
        val targetY = if (sh > 0 && winH > 0) y * (winH.toFloat() / sh) else y
        CallbackBridge.sendCursorPos(targetX, targetY)
    }

    FocusableBox(
        modifier = modifier
            .fillMaxSize()
            // 1. Touchscreen input processing.
            // Kunci Unit: blok ini TIDAK dimulai ulang saat mode kursor atau mode kontrol berganti.
            // Dulu dimulai ulang tiap inventori dibuka/ditutup, sehingga jari yang sudah menempel
            // dilupakan dan baru bergerak lagi setelah diangkat. Semua nilai berubah dibaca lewat State.
            .pointerInput(Unit) {
                coroutineScope {
                    var activePointerId: PointerId? = null
                    var startPosition = Offset.Zero
                    var isDragging = false
                    var longPressTriggered = false
                    var longPressJob: Job? = null
                    // Slot hotbar yang disentuh jari ini (-1 = bukan hotbar). Hanya saat bermain.
                    var hotbarSlot = -1
                    // Jari yang sedang diteruskan ke mod.
                    val forwarder = TouchForwarder()
                    // Status kursor terkunci pada kejadian sebelumnya, untuk mendeteksi pergantian mode.
                    var lastCaptured = currentCursorMode == CURSOR_DISABLED

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()

                            // Mod TouchController (lihat touchSink di atas).
                            val sink = currentTouchSink
                            if (sink != null) {
                                // Berdampingan: sentuhan tetap diproses launcher di bawah (tidak dikonsumsi),
                                // dan salinannya dikirim ke mod hanya saat bermain (kursor terkunci). Di menu
                                // game launcher yang bekerja sendiri, kalau tidak klik akan terhitung dua kali.
                                val invW = if (currentScreenWidth > 0f) 1f / currentScreenWidth else 0f
                                val invH = if (currentScreenHeight > 0f) 1f / currentScreenHeight else 0f
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
                            }

                            // Mode kursor berganti (inventori dibuka/ditutup) saat jari sudah menempel: jari yang
                            // sama diteruskan ke mode baru tanpa harus diangkat dulu.
                            val capturedNow0 = currentCursorMode == CURSOR_DISABLED
                            if (capturedNow0 != lastCaptured) {
                                lastCaptured = capturedNow0
                                val heldId = activePointerId
                                if (heldId != null) {
                                    longPressJob?.cancel()
                                    if (longPressTriggered) {
                                        // Tombol kiri yang sedang ditahan dilepas, supaya tidak ikut tertahan di mode baru.
                                        longPressTriggered = false
                                        currentOnLongPressEnd()
                                    }
                                    hotbarSlot = -1
                                    // Dianggap sudah menggeser: jari langsung menggerakkan kamera/kursor, dan saat
                                    // diangkat tidak menghasilkan ketukan (pukul/klik) yang tidak disengaja.
                                    isDragging = true
                                    val heldChange = event.changes.firstFast { it.id == heldId }
                                    if (heldChange != null) {
                                        startPosition = heldChange.position
                                        if (!capturedNow0 && currentControlMode == MouseControlMode.CLICK) {
                                            currentOnCursorPositionChange(heldChange.position)
                                            sendScaledCursor(heldChange.position.x, heldChange.position.y)
                                        }
                                    }
                                }
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
                                    // Kejadian ini baru melewati ambang geser: gerak sebelumnya (di dalam ambang)
                                    // belum dipakai di beberapa mode, jadi dihitung dari titik awal agar tidak hilang.
                                    var crossedSlop = false
                                    if (!isDragging && distanceFromStart > viewConfiguration.touchSlop) {
                                        isDragging = true
                                        crossedSlop = true
                                        longPressJob?.cancel()
                                    }

                                    if (currentCursorMode == CURSOR_DISABLED) {
                                        // In gameplay: touch drag rotates the camera. Ketukan di hotbar tidak
                                        // menggoyang kamera selama jari belum benar-benar menggeser.
                                        if (hotbarSlot < 0 || isDragging) {
                                            val delta = if (crossedSlop && hotbarSlot >= 0) {
                                                moveChange.position - startPosition
                                            } else {
                                                moveChange.positionChange()
                                            }
                                            currentOnCapturedMove(delta)
                                        }
                                    } else {
                                        // In menu: move cursor
                                        if (currentControlMode == MouseControlMode.SLIDE) {
                                            if (isDragging || longPressTriggered) {
                                                val delta = if (crossedSlop && !longPressTriggered) {
                                                    moveChange.position - startPosition
                                                } else {
                                                    moveChange.positionChange()
                                                }
                                                val cur = currentCursorPos()
                                                val newX = (cur.x + delta.x * currentSensitivity).coerceIn(0f, currentScreenWidth)
                                                val newY = (cur.y + delta.y * currentSensitivity).coerceIn(0f, currentScreenHeight)
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
            // 2. Mouse fisik di menu game (kursor tidak terkunci): posisi absolut, klik, gulir.
            .then(
                Modifier.physicalMouseHover(
                    disabled = capturePointer,
                    onMouse = {
                        checkPhysicalMouseMode(true)
                        currentOnMouse()
                    },
                    onMouseMove = { pos ->
                        val newX = pos.x.coerceIn(0f, currentScreenWidth)
                        val newY = pos.y.coerceIn(0f, currentScreenHeight)
                        MouseDiag.cursorSent++
                        currentOnCursorPositionChange(Offset(newX, newY))
                        sendScaledCursor(newX, newY)
                    },
                    onMouseScroll = sendMouseScroll,
                    onMouseButton = sendMouseButton
                )
            ),
        requestKey = cursorMode to composeFocusCount,
        hoverIcon = hoverIcon
    )

    // 3. Pointer capture (aktif saat bermain / CURSOR_DISABLED): gerakan relatif jadi delta kamera.
    PhysicalMouseCapture(
        enabled = capturePointer,
        onMouse = {
            checkPhysicalMouseMode(true)
            currentOnMouse()
        },
        onMouseMove = { delta ->
            MouseDiag.deltaSent++
            currentOnCapturedMove(delta)
        },
        onMouseScroll = sendMouseScroll,
        onMouseButton = sendMouseButton
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
