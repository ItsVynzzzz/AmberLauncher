package com.israadev.nuxlauncher.ui.control

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.israadev.nuxlauncher.FpsMode
import com.israadev.nuxlauncher.core.controls.models.ControlShowMode
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import com.movtery.zalithlauncher.bridge.CURSOR_DISABLED
import com.movtery.zalithlauncher.bridge.ZLBridgeStates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.lwjgl.glfw.CallbackBridge
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lapisan semua tombol layar di atas game. Tiap tombol hanya satu simpul yang menggambar dirinya
 * sendiri di kanvas ([ControlSkin]); posisi dihitung saat tata letak, bukan saat penyusunan.
 * Angka FPS dibaca lewat lambda di dalam teks FPS saja, jadi pembaruan 2x per detik tidak
 * menyusun ulang tombol lain. Tombol gerigi hanya membuka menu; keluar dari game lewat tombol
 * merah di dalam menu.
 *
 * Tiap tombol punya lapisan gambar (RenderNode) sendiri. Tanpa itu, satu tombol yang berubah
 * (ditekan, angka FPS baru, kenop joystick bergeser) membuat thread utama merekam ulang gambar
 * SEMUA tombol, jadi makin banyak tombol makin berat tiap sentuhan. Dengan lapisan sendiri hanya
 * tombol yang berubah yang direkam ulang; tombol lain cukup memakai daftar gambar yang sudah ada.
 *
 * Dua mode tampil: tiap tombol biasa punya [CustomControlButton.showMode] (selalu / hanya saat main /
 * hanya saat menu). [inMenu] true bila game sedang menampilkan kursor (inventori, chat, jeda). Tombol
 * yang tidak cocok dengan mode saat ini tidak disusun sama sekali, jadi tidak membebani layar.
 */
@Composable
fun GameControlsLayer(
    buttons: List<CustomControlButton>,
    screenWidth: Float,
    screenHeight: Float,
    controlsVisible: Boolean,
    fpsMode: FpsMode,
    consoleVisible: Boolean,
    keyboardActive: Boolean,
    fps: () -> Int,
    perfText: () -> String,
    perfWarn: () -> Boolean,
    onFpsClick: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onToggleControls: () -> Unit,
    onOpenMenu: () -> Unit,
    // Mod TouchController dipakai: kontrol bawaan disembunyikan, hanya gerigi menu (dan FPS bila
    // disematkan) yang tersisa. Tombol yang tidak tampil tidak ikut disusun sama sekali.
    touchControllerMode: Boolean = false,
    // Game sedang menampilkan kursor (menu/GUI). Dibaca dari status kursor game, bukan dari layar.
    inMenu: Boolean = false
) {
    val density = LocalDensity.current
    val tc = touchControllerMode
    buttons.forEach { btn ->
        key(btn.id) {
            val widthPx = with(density) { btn.widthDp.dp.toPx() }
            val heightPx = with(density) { btn.heightDp.dp.toPx() }
            val left = (screenWidth * (btn.xPercent / 100f) - widthPx / 2f)
                .coerceIn(0f, (screenWidth - widthPx).coerceAtLeast(0f))
            val top = (screenHeight * (btn.yPercent / 100f) - heightPx / 2f)
                .coerceIn(0f, (screenHeight - heightPx).coerceAtLeast(0f))
            // graphicsLayer harus sebelum size/drawBehind milik tombol, supaya gambarnya masuk ke lapisan ini.
            val place = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .graphicsLayer { }

            if (btn.isSystem) {
                when (btn.systemAction) {
                    "FPS" -> {
                        val show = when (fpsMode) {
                            FpsMode.NORMAL -> controlsVisible && !consoleVisible && !tc
                            FpsMode.PINNED -> !consoleVisible
                            FpsMode.SHOW_LOG -> false
                        }
                        if (show) {
                            FpsText(btn, place, fpsMode == FpsMode.PINNED, fps, perfText, perfWarn, onFpsClick)
                        }
                    }
                    "KEYBOARD" -> if (controlsVisible && !tc) {
                        SystemIconButton(btn, place, active = keyboardActive, onTap = onToggleKeyboard)
                    }
                    // Selalu tampil supaya tombol layar bisa dimunculkan lagi.
                    "HIDE_GUI" -> if (!tc) {
                        SystemIconButton(btn, place, slash = !controlsVisible, onTap = onToggleControls)
                    }
                    // Gerigi: hanya membuka menu. "CLOSE" = nama lama tombol ini di tata letak tersimpan.
                    "MENU", "CLOSE" -> if (controlsVisible || tc) {
                        SystemIconButton(btn, place, onTap = onOpenMenu)
                    }
                }
            } else if (controlsVisible && !tc && ControlShowMode.visible(btn.showMode, inMenu)) {
                if (btn.isJoystick) {
                    CustomVirtualJoystick(button = btn, modifier = place)
                } else {
                    CustomVirtualButton(button = btn, modifier = place)
                }
            }
            Unit
        }
    }
}

/**
 * Wadah makro "ketik perintah". Mengetik / atau T membuka chat, yang mengganti mode ke menu dan
 * bisa membuat tombolnya hilang di tengah jalan; kalau pengetikan ikut terhenti bersama tombolnya,
 * perintahnya setengah terkirim. Karena itu pengetikan jalan di wadah sendiri, bukan di komposisi tombol.
 */
private val macroTypingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    fontFeatureSettings = "tnum"
}

/**
 * Angka FPS minimalis: teks saja tanpa kotak atau garis. Warnanya mengikuti kondisi (putih,
 * kuning, merah muda). Info RAM, panas, atau beban UI hanya muncul bila perlu. Ketuk membuka menu.
 *
 * Semua nilai dibaca di dalam fase gambar saja: angka baru hanya menggambar ulang teks ini, tanpa
 * menyusun ulang atau mengukur ulang tata letak.
 */
@Composable
private fun FpsText(
    btn: CustomControlButton,
    modifier: Modifier,
    pinned: Boolean,
    fps: () -> Int,
    perfText: () -> String,
    perfWarn: () -> Boolean,
    onClick: () -> Unit
) {
    val click by rememberUpdatedState(onClick)
    Box(
        modifier = modifier
            .size(width = btn.widthDp.dp, height = btn.heightDp.dp)
            .drawBehind { drawFpsReadout(fps(), pinned, if (perfWarn()) perfText() else "") }
            .pointerInput(Unit) { detectTapGestures { click() } }
    )
}

private fun drawFpsPiece(cv: android.graphics.Canvas, s: String, x: Float, y: Float, size: Float, spacing: Float, color: Color, shadowPx: Float) {
    fpsPaint.textSize = size
    fpsPaint.letterSpacing = spacing
    fpsPaint.color = Color.Black.copy(alpha = color.alpha * 0.55f).toArgb()
    cv.drawText(s, x, y + shadowPx, fpsPaint)
    fpsPaint.color = color.toArgb()
    cv.drawText(s, x, y, fpsPaint)
}

private fun DrawScope.drawFpsReadout(value: Int, pinned: Boolean, extra: String) {
    val tint = when {
        value <= 0 -> Color.White.copy(alpha = 0.45f)
        value >= 50 -> Color.White.copy(alpha = 0.86f)
        value >= 25 -> ControlSkin.YellowSoft
        else -> Color(0xFFFFA399)
    }
    val numSize = 14.dp.toPx()
    val labSize = 8.dp.toPx()
    val gap = 3.dp.toPx()
    val shadow = 1.dp.toPx()
    val num = if (value > 0) value.toString() else "--"

    fpsPaint.textSize = numSize
    fpsPaint.letterSpacing = 0f
    val numW = fpsPaint.measureText(num)
    fpsPaint.textSize = labSize
    fpsPaint.letterSpacing = 0.1f
    val labW = fpsPaint.measureText("FPS")
    val x0 = (size.width - (numW + gap + labW)) / 2f
    val base = size.height / 2f + numSize * 0.36f

    drawIntoCanvas { c ->
        val cv = c.nativeCanvas
        drawFpsPiece(cv, num, x0, base, numSize, 0f, tint, shadow)
        drawFpsPiece(
            cv, "FPS", x0 + numW + gap, base, labSize, 0.1f,
            if (pinned) ControlSkin.Yellow else Color.White.copy(alpha = 0.50f), shadow
        )
        if (extra.isNotEmpty()) {
            drawFpsPiece(
                cv, extra, x0 + numW + gap + labW + 6.dp.toPx(), base, labSize, 0f,
                ControlSkin.Yellow.copy(alpha = 0.85f), shadow
            )
        }
    }
}

/** Tombol sistem berbentuk ikon (keyboard, sembunyikan GUI, gerigi menu). */
@Composable
private fun SystemIconButton(
    btn: CustomControlButton,
    modifier: Modifier,
    active: Boolean = false,
    slash: Boolean = false,
    onTap: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val spec = remember(btn, slash) { skinSpecFor(btn, slash = slash) }
    val tap by rememberUpdatedState(onTap)

    Box(
        modifier = modifier
            .size(width = btn.widthDp.dp, height = btn.heightDp.dp)
            .drawBehind { drawControlButton(spec, pressed || active) }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { tap() }
                )
            }
    )
}

/**
 * Tombol virtual biasa, widget gulir hotbar, dan tombol makro. Tombol serang/pakai/lompat bisa
 * digeser sambil ditahan untuk memutar kamera (satu jempol untuk menggali sambil membidik), tetapi
 * hanya bila "Geser kamera dari tombol" dinyalakan di Pengaturan. Bawaannya mati: jari yang sedikit
 * bergeser saat menahan tombol (PvP) tidak boleh menggeser pandangan. Memutar kamera dengan jari
 * lain di area kosong layar tetap bekerja seperti biasa.
 */
@Composable
fun CustomVirtualButton(
    button: CustomControlButton,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var turboJob by remember { mutableStateOf<Job?>(null) }
    val spec = remember(button) { skinSpecFor(button) }

    // Nilai terbaru tombol dibaca saat dilepas dari layar, bukan salinan saat pertama disusun.
    val currentButton by rememberUpdatedState(button)

    DisposableEffect(button.id) {
        onDispose {
            turboJob?.cancel()
            // Tombol kunci (toggle) yang sedang aktif dilepas saat tombolnya hilang dari layar
            // (mis. kontrol disembunyikan). Tanpa ini tombol (Shift/Ctrl, dll.) tetap tertahan di
            // game padahal tombol di layar sudah terlihat mati, dan butuh dua ketukan untuk memperbaikinya.
            // Kombinasi tombol yang sedang ditahan juga dilepas (mis. tombol hilang karena mode
            // berganti ke menu saat jari masih di atasnya), kalau tidak tombolnya tetap tertahan.
            val b = currentButton
            val plainToggle = !b.isScroll && (
                (b.isToggle && !(b.isMacro && b.macroType == "COMMAND")) ||
                    (b.isMacro && b.macroType == "COMBO")
                )
            if (plainToggle && isPressed) {
                if (b.isMacro && b.macroType == "COMBO") {
                    val keys = if (b.macroComboKeys.isNotEmpty()) b.macroComboKeys
                    else if (b.macroComboKey != 0) listOf(b.macroComboKey) else emptyList()
                    if (b.isMouseButton) CallbackBridge.putMouseEvent(b.mouseButton, false)
                    else CallbackBridge.sendKeyPress(b.keyCode, false)
                    for (k in keys.asReversed()) CallbackBridge.sendKeyPress(k, false)
                } else if (b.isMouseButton) {
                    CallbackBridge.putMouseEvent(b.mouseButton, false)
                } else {
                    CallbackBridge.sendKeyPress(b.keyCode, false)
                }
            }
        }
    }

    if (button.isScroll) {
        val density = LocalDensity.current
        val thresholdPx = with(density) { 14.dp.toPx() }

        Box(
            modifier = modifier
                .size(width = button.widthDp.dp, height = button.heightDp.dp)
                .drawBehind { drawControlScroll(spec, isPressed) }
                .pointerInput(button.id) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPressed = true
                            val startY = down.position.y
                            var totalDragY = 0f
                            var hasDragged = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstFast { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!hasDragged) {
                                        // Ketuk: setengah atas gulir naik, setengah bawah gulir turun
                                        val h = size.height
                                        if (startY < h / 2f) {
                                            CallbackBridge.sendScroll(0.0, 1.0)
                                        } else {
                                            CallbackBridge.sendScroll(0.0, -1.0)
                                        }
                                    }
                                    break
                                }

                                val dy = change.position.y - change.previousPosition.y
                                totalDragY += dy
                                if (abs(totalDragY) > 6f) {
                                    hasDragged = true
                                }

                                if (totalDragY <= -thresholdPx) {
                                    // Geser ke atas: slot hotbar sebelumnya
                                    CallbackBridge.sendScroll(0.0, 1.0)
                                    totalDragY = 0f
                                } else if (totalDragY >= thresholdPx) {
                                    // Geser ke bawah: slot hotbar berikutnya
                                    CallbackBridge.sendScroll(0.0, -1.0)
                                    totalDragY = 0f
                                }
                                change.consume()
                            }
                            isPressed = false
                        }
                    }
                }
        )
        return
    }

    fun sendPress(down: Boolean) {
        if (button.isMouseButton) {
            CallbackBridge.putMouseEvent(button.mouseButton, down)
        } else {
            CallbackBridge.sendKeyPress(button.keyCode, down)
        }
    }

    // Tombol biasa (bukan toggle, bukan makro): tahan = aktif.
    val simple = !button.isMacro && !button.isToggle
    // Tombol yang boleh memutar kamera bila digeser (hanya bila dinyalakan di Pengaturan, lihat di bawah).
    val lookDrag = button.isMouseButton || button.keyCode == LwjglGlfwKeycode.GLFW_KEY_SPACE

    val base = modifier
        .size(width = button.widthDp.dp, height = button.heightDp.dp)
        .drawBehind { drawControlButton(spec, isPressed) }

    Box(
        modifier = if (simple) {
            base.pointerInput(button.id, button.isMouseButton, button.mouseButton, button.keyCode) {
                val slop = 6.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    sendPress(true)
                    // Dibaca tiap jari menyentuh tombol, jadi mengubah pengaturan langsung berlaku.
                    val canLook = lookDrag && SettingsManager.settings.value.buttonLookDrag
                    var last = down.position
                    var looking = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstFast { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val delta = change.position - last
                            last = change.position
                            if (canLook) {
                                if (!looking && (change.position - down.position).getDistance() > slop) {
                                    looking = true
                                }
                                if (looking && ZLBridgeStates.cursorMode.value == CURSOR_DISABLED) {
                                    val sens = SettingsManager.settings.value.captureSensitivity / 100f
                                    CallbackBridge.sendCursorDelta(delta.x * sens, delta.y * sens)
                                }
                            }
                            change.consume()
                        }
                    } finally {
                        isPressed = false
                        sendPress(false)
                    }
                }
            }
        } else {
            base.pointerInput(button.id, button.isToggle, button.isMacro, button.macroType, button.macroCommand, button.macroComboKey, button.macroComboKeys, button.macroTurboIntervalMs) {
                detectTapGestures(
                    onPress = {
                        if (button.isMacro) {
                            when (button.macroType) {
                                "COMMAND" -> {
                                    val cmd = button.macroCommand.trim()
                                    if (cmd.isNotEmpty()) {
                                        isPressed = true
                                        macroTypingScope.launch {
                                            try {
                                                if (cmd.startsWith("/")) {
                                                    CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_SLASH, true)
                                                    delay(20)
                                                    CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_SLASH, false)
                                                    delay(50)
                                                    val chars = cmd.substring(1)
                                                    for (ch in chars) {
                                                        CallbackBridge.sendChar(ch, 0)
                                                        delay(10)
                                                    }
                                                } else {
                                                    CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_T, true)
                                                    delay(20)
                                                    CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_T, false)
                                                    delay(50)
                                                    for (ch in cmd) {
                                                        CallbackBridge.sendChar(ch, 0)
                                                        delay(10)
                                                    }
                                                }
                                                delay(40)
                                                CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ENTER, true)
                                                delay(25)
                                                CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ENTER, false)
                                            } finally {
                                                delay(50)
                                                isPressed = false
                                            }
                                        }
                                        tryAwaitRelease()
                                    }
                                }
                                "COMBO" -> {
                                    val comboKeys = if (button.macroComboKeys.isNotEmpty()) {
                                        button.macroComboKeys
                                    } else if (button.macroComboKey != 0) {
                                        listOf(button.macroComboKey)
                                    } else emptyList()

                                    if (button.isToggle) {
                                        isPressed = !isPressed
                                        if (isPressed) {
                                            for (k in comboKeys) {
                                                CallbackBridge.sendKeyPress(k, true)
                                                delay(12)
                                            }
                                            sendPress(true)
                                        } else {
                                            sendPress(false)
                                            for (k in comboKeys.reversed()) {
                                                CallbackBridge.sendKeyPress(k, false)
                                                delay(12)
                                            }
                                        }
                                    } else {
                                        isPressed = true
                                        for (k in comboKeys) {
                                            CallbackBridge.sendKeyPress(k, true)
                                            delay(12)
                                        }
                                        sendPress(true)
                                        tryAwaitRelease()
                                        sendPress(false)
                                        for (k in comboKeys.reversed()) {
                                            CallbackBridge.sendKeyPress(k, false)
                                            delay(12)
                                        }
                                        isPressed = false
                                    }
                                }
                                "TURBO" -> {
                                    val interval = button.macroTurboIntervalMs.coerceAtLeast(30L)
                                    val downTime = (interval / 2).coerceAtLeast(15L)
                                    val upTime = (interval - downTime).coerceAtLeast(15L)

                                    if (button.isToggle) {
                                        isPressed = !isPressed
                                        if (isPressed) {
                                            turboJob = coroutineScope.launch {
                                                while (isActive) {
                                                    sendPress(true)
                                                    delay(downTime)
                                                    sendPress(false)
                                                    delay(upTime)
                                                }
                                            }
                                        } else {
                                            turboJob?.cancel()
                                            turboJob = null
                                            sendPress(false)
                                        }
                                    } else {
                                        isPressed = true
                                        val job = coroutineScope.launch {
                                            while (isActive) {
                                                sendPress(true)
                                                delay(downTime)
                                                sendPress(false)
                                                delay(upTime)
                                            }
                                        }
                                        tryAwaitRelease()
                                        job.cancel()
                                        sendPress(false)
                                        isPressed = false
                                    }
                                }
                                else -> {
                                    if (button.isToggle) {
                                        isPressed = !isPressed
                                        sendPress(isPressed)
                                    } else {
                                        isPressed = true
                                        sendPress(true)
                                        tryAwaitRelease()
                                        isPressed = false
                                        sendPress(false)
                                    }
                                }
                            }
                        } else {
                            if (button.isToggle) {
                                isPressed = !isPressed
                                sendPress(isPressed)
                            } else {
                                isPressed = true
                                sendPress(true)
                                tryAwaitRelease()
                                isPressed = false
                                sendPress(false)
                            }
                        }
                    }
                )
            }
        }
    )
}

/**
 * Joystick WASD dengan kunci lari: dorong sampai tepi lingkaran supaya lari otomatis (Ctrl
 * ditahan) selama maju masih ditahan. Dilepas = berhenti. Lari otomatis bisa dimatikan per joystick
 * di editor tombol ([CustomControlButton.autoSprint]).
 */
@Composable
fun CustomVirtualJoystick(
    button: CustomControlButton,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val sizePx = with(density) { button.widthDp.dp.toPx() }
    val radiusPx = sizePx / 2f
    val knobRadiusPx = radiusPx * 0.36f
    val maxDragDistance = radiusPx - knobRadiusPx

    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var isTouching by remember { mutableStateOf(false) }
    var sprint by remember { mutableStateOf(false) }
    // Dibaca saat jari bergerak, jadi perubahan dari editor langsung dipakai tanpa menyusun ulang.
    val autoSprint by rememberUpdatedState(button.autoSprint)

    // Posisi kenop yang terakhir digambar. Kenop hanya digambar ulang bila bergeser cukup jauh:
    // jari yang ditahan hampir selalu bergetar 1-2 piksel, dan tiap penggambaran ulang berarti
    // satu frame UI penuh yang berebut GPU dan CPU dengan game.
    val shown = remember { FloatArray(2) }
    val knobStepPx = with(density) { 2.5.dp.toPx() }

    // Status tombol arah disimpan biasa (bukan state) supaya menggeser kenop tidak menyusun ulang.
    val keys = remember { BooleanArray(4) } // W, A, S, D
    val ctrlDown = remember { BooleanArray(1) }

    fun updateKeys(needW: Boolean, needA: Boolean, needS: Boolean, needD: Boolean) {
        if (needW != keys[0]) {
            keys[0] = needW
            CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_W, needW)
        }
        if (needA != keys[1]) {
            keys[1] = needA
            CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_A, needA)
        }
        if (needS != keys[2]) {
            keys[2] = needS
            CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_S, needS)
        }
        if (needD != keys[3]) {
            keys[3] = needD
            CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_D, needD)
        }
    }

    fun setSprint(on: Boolean) {
        if (on != ctrlDown[0]) {
            ctrlDown[0] = on
            CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL, on)
        }
        if (sprint != on) sprint = on
    }

    fun releaseAll() {
        updateKeys(false, false, false, false)
        setSprint(false)
        shown[0] = 0f
        shown[1] = 0f
        knobOffset = Offset.Zero
        isTouching = false
    }

    DisposableEffect(button.id) {
        onDispose {
            releaseAll()
        }
    }

    Box(
        modifier = modifier
            .size(button.widthDp.dp)
            .drawBehind { drawControlJoystick(button.opacity, knobOffset, isTouching, sprint) }
            .pointerInput(button.id) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isTouching = true
                        val center = Offset(size.width / 2f, size.height / 2f)

                        fun processPosition(pos: Offset) {
                            val rawDelta = pos - center
                            val dist = hypot(rawDelta.x.toDouble(), rawDelta.y.toDouble()).toFloat()
                            val clampedDist = dist.coerceAtMost(maxDragDistance)
                            val angleRad = atan2(rawDelta.y.toDouble(), rawDelta.x.toDouble())

                            val kx = if (dist > 0f) (cos(angleRad) * clampedDist).toFloat() else 0f
                            val ky = if (dist > 0f) (sin(angleRad) * clampedDist).toFloat() else 0f
                            val ddx = kx - shown[0]
                            val ddy = ky - shown[1]
                            if (ddx * ddx + ddy * ddy >= knobStepPx * knobStepPx) {
                                shown[0] = kx
                                shown[1] = ky
                                knobOffset = Offset(kx, ky)
                            }

                            val deadzone = maxDragDistance * 0.20f
                            if (clampedDist < deadzone) {
                                updateKeys(false, false, false, false)
                                setSprint(false)
                            } else {
                                val deg = Math.toDegrees(angleRad)
                                val w = deg in -157.5..-22.5
                                val s = deg in 22.5..157.5
                                val d = deg in -67.5..67.5
                                val a = deg !in -112.5..112.5

                                updateKeys(w, a, s, d)
                                // Kunci lari: sampai tepi lingkaran sambil maju; berhenti saat tidak maju lagi.
                                // Bila lari otomatis dimatikan di editor, joystick tidak pernah menahan Ctrl.
                                if (!w || !autoSprint) {
                                    setSprint(false)
                                } else if (dist >= radiusPx * 0.98f) {
                                    setSprint(true)
                                }
                            }
                        }

                        processPosition(down.position)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstFast { it.id == down.id } ?: break
                            if (!change.pressed) {
                                break
                            }
                            processPosition(change.position)
                            change.consume()
                        }
                        releaseAll()
                    }
                }
            }
    )
}
