package com.israadev.nuxlauncher.ui.dialogs

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.israadev.nuxlauncher.FpsMode
import com.israadev.nuxlauncher.ui.components.AmberIcon
import com.israadev.nuxlauncher.ui.components.AmberSegmented
import com.israadev.nuxlauncher.ui.components.AmberSlider
import com.israadev.nuxlauncher.ui.components.AmberToggle
import com.israadev.nuxlauncher.ui.components.FpsReadout
import com.israadev.nuxlauncher.ui.components.amberTap
import com.israadev.nuxlauncher.ui.control.MouseControlMode
import com.israadev.nuxlauncher.ui.theme.Amber
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import kotlin.math.roundToInt

/**
 * Menu dalam game: panel yang meluncur dari kanan dan membiarkan game tetap terlihat di kiri.
 * Aksi yang paling sering dipakai ada di baris ikon paling atas, sisanya satu daftar bergulir
 * tanpa tab. Tanpa blur latar (blur memberatkan GPU saat game masih berjalan di belakangnya) dan
 * angka FPS hanya dibaca oleh lencana kecil di kepala panel.
 */
@Composable
fun GameMenuSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    fps: () -> Int,
    instanceName: String,
    mcVersion: String,
    currentResolutionRatio: Int,
    onResolutionChange: (Int) -> Unit,
    fpsMode: FpsMode,
    onFpsModeChange: (FpsMode) -> Unit,
    onOpenConsoleLog: () -> Unit,
    cursorSensitivity: Int,
    onCursorSensitivityChange: (Int) -> Unit,
    captureSensitivity: Int,
    onCaptureSensitivityChange: (Int) -> Unit,
    mouseControlMode: MouseControlMode,
    onMouseControlModeChange: (MouseControlMode) -> Unit,
    isControlVisible: Boolean,
    onToggleControlVisibility: () -> Unit,
    onRequestKeyboard: () -> Unit,
    onSendKeycode: (Int) -> Unit,
    onExitRequest: () -> Unit,
    onOpenCustomGui: () -> Unit
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val win = (dialogView.parent as? DialogWindowProvider)?.window
            if (win != null) {
                // Peredupan kita atur sendiri di bawah; tidak memakai blur latar.
                win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    win.attributes = win.attributes.apply {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
        }

        val slide = remember { Animatable(1f) }
        LaunchedEffect(Unit) { slide.animateTo(0f, tween(durationMillis = 170)) }
        val dismiss by rememberUpdatedState(onDismissRequest)
        val panelShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.40f))
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .graphicsLayer { translationX = slide.value * size.width }
                    .clip(panelShape)
                    .background(Amber.Panel)
                    .border(1.dp, Amber.Line, panelShape)
                    // Menelan ketukan di area kosong supaya tidak menutup menu
                    .pointerInput(Unit) { detectTapGestures { } }
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Kepala: judul, FPS, tutup
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Menu",
                            color = Amber.Text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$instanceName  \u00B7  $mcVersion",
                            color = Amber.TextDim,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FpsReadout(fps)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .amberTap(onDismissRequest),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .border(1.dp, Amber.LineStrong, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            AmberIcon("close", 14.dp, Amber.Text)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Aksi cepat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickTile(
                        modifier = Modifier.weight(1f),
                        icon = "keyboard",
                        label = "Keyboard",
                        onClick = {
                            onDismissRequest()
                            onRequestKeyboard()
                        }
                    )
                    QuickTile(
                        modifier = Modifier.weight(1f),
                        icon = "eye",
                        label = "Tombol",
                        active = isControlVisible,
                        slash = !isControlVisible,
                        onClick = onToggleControlVisibility
                    )
                    QuickTile(
                        modifier = Modifier.weight(1f),
                        icon = "sliders",
                        label = "Atur tombol",
                        onClick = {
                            onDismissRequest()
                            onOpenCustomGui()
                        }
                    )
                    QuickTile(
                        modifier = Modifier.weight(1f),
                        icon = "terminal",
                        label = "Log",
                        onClick = {
                            onDismissRequest()
                            onOpenConsoleLog()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    SectionTitle("TAMPILAN")
                    SliderRow(
                        label = "Resolusi render",
                        value = currentResolutionRatio,
                        min = 40,
                        max = 125,
                        step = 5,
                        onCommit = onResolutionChange
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AmberSegmented(
                        options = listOf("Hemat 50%", "Seimbang 75%", "Penuh 100%"),
                        selected = when (currentResolutionRatio) {
                            50 -> 0
                            75 -> 1
                            100 -> 2
                            else -> -1
                        },
                        onSelect = { index -> onResolutionChange(intArrayOf(50, 75, 100)[index]) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ToggleRow(
                        title = "Pin FPS di layar",
                        subtitle = "Tetap tampil saat tombol layar disembunyikan",
                        checked = fpsMode == FpsMode.PINNED,
                        onChange = { on -> onFpsModeChange(if (on) FpsMode.PINNED else FpsMode.NORMAL) }
                    )

                    SectionTitle("KAMERA DAN KURSOR")
                    SliderRow(
                        label = "Kecepatan kamera",
                        value = captureSensitivity,
                        min = 25,
                        max = 250,
                        step = 5,
                        onCommit = onCaptureSensitivityChange
                    )
                    SliderRow(
                        label = "Kecepatan kursor",
                        value = cursorSensitivity,
                        min = 25,
                        max = 250,
                        step = 5,
                        onCommit = onCursorSensitivityChange
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Mode sentuh",
                        color = Amber.TextDim,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AmberSegmented(
                        options = listOf("Geser", "Ketuk"),
                        selected = if (mouseControlMode == MouseControlMode.SLIDE) 0 else 1,
                        onSelect = { index ->
                            onMouseControlModeChange(
                                if (index == 0) MouseControlMode.SLIDE else MouseControlMode.CLICK
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Geser: layar jadi trackpad. Ketuk: jari langsung jadi kursor.",
                        color = Amber.TextFaint,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    SectionTitle("PINTAS")
                    val keys = listOf(
                        Triple("F3", "Debug", LwjglGlfwKeycode.GLFW_KEY_F3),
                        Triple("F5", "Kamera", LwjglGlfwKeycode.GLFW_KEY_F5),
                        Triple("ESC", "Jeda", LwjglGlfwKeycode.GLFW_KEY_ESCAPE),
                        Triple("T", "Chat", LwjglGlfwKeycode.GLFW_KEY_T),
                        Triple("TAB", "Pemain", LwjglGlfwKeycode.GLFW_KEY_TAB),
                        Triple("F1", "Sembunyi HUD", LwjglGlfwKeycode.GLFW_KEY_F1)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        keys.take(3).forEach { (label, desc, code) ->
                            KeyChip(Modifier.weight(1f), label, desc) { onSendKeycode(code) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        keys.drop(3).forEach { (label, desc, code) ->
                            KeyChip(Modifier.weight(1f), label, desc) { onSendKeycode(code) }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Satu-satunya jalan keluar dari game: tombol merah yang selalu terlihat di dasar panel.
                Spacer(modifier = Modifier.height(8.dp))
                ExitButton(
                    onClick = {
                        onDismissRequest()
                        onExitRequest()
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = text,
        color = Amber.YellowSoft,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
    Spacer(modifier = Modifier.height(6.dp))
}

/** Satu baris penggeser. Nilai sementara disimpan di sini supaya menggeser tidak menyusun ulang menu. */
@Composable
private fun SliderRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onCommit: (Int) -> Unit
) {
    var live by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Amber.Text,
                fontSize = 12.5.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${live.roundToInt()}%",
                color = Amber.Yellow,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
        AmberSlider(
            value = { live },
            range = min.toFloat()..max.toFloat(),
            step = step.toFloat(),
            onChange = { live = it },
            onFinished = { onCommit(live.roundToInt()) }
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Amber.Text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Amber.TextDim,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        AmberToggle(checked = checked, onChange = onChange)
    }
}

@Composable
private fun QuickTile(
    modifier: Modifier,
    icon: String,
    label: String,
    active: Boolean = false,
    slash: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(if (active) Amber.YellowTint else Amber.Fill)
            .border(1.dp, if (active) Amber.Yellow else Amber.Line, shape)
            .amberTap(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AmberIcon(icon, 20.dp, if (active) Amber.Yellow else Amber.Text, slash = slash)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (active) Amber.YellowSoft else Amber.TextDim,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun KeyChip(
    modifier: Modifier,
    label: String,
    desc: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(Amber.Fill)
            .border(1.dp, Amber.Line, shape)
            .amberTap(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = Amber.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = desc,
            color = Amber.TextDim,
            fontSize = 10.5.sp,
            maxLines = 1
        )
    }
}

/** Tombol keluar: merah penuh, tulisan gelap seperti tombol kuning di tempat lain. */
@Composable
private fun ExitButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(Amber.Danger)
            .amberTap(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        AmberIcon("power", 18.dp, Amber.Ink)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Keluar dari game",
            color = Amber.Ink,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
