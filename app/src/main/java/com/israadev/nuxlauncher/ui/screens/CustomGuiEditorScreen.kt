package com.israadev.nuxlauncher.ui.screens

import com.israadev.nuxlauncher.ui.i18n.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import com.israadev.nuxlauncher.ui.control.drawControlButton
import com.israadev.nuxlauncher.ui.control.drawControlJoystick
import com.israadev.nuxlauncher.ui.control.drawControlScroll
import com.israadev.nuxlauncher.ui.control.skinSpecFor
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.israadev.nuxlauncher.core.controls.ControlLayoutManager
import com.israadev.nuxlauncher.core.controls.ControlLimits
import com.israadev.nuxlauncher.core.controls.KeycodeCatalog
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import com.israadev.nuxlauncher.ui.components.NuxBadge
import com.israadev.nuxlauncher.ui.components.NuxButton
import com.israadev.nuxlauncher.ui.components.NuxCard
import com.israadev.nuxlauncher.ui.components.NuxTextField
import com.israadev.nuxlauncher.ui.dialogs.NuxKeyPickerDialog
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Seperti coerceIn, tapi tidak melempar galat bila rentangnya terbalik (tombol lebih besar dari
 * layar, mungkin sejak batas ukuran dinaikkan): pusatnya dipasang di tengah rentang.
 */
private fun Float.clampCenter(lo: Float, hi: Float): Float =
    if (lo > hi) (lo + hi) / 2f else coerceIn(lo, hi)

@Composable
fun CustomGuiEditorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    isIngame: Boolean = false
) {
    val context = LocalContext.current
    val savedButtons by ControlLayoutManager.buttons.collectAsState()

    // Working local copy of buttons for live editing
    var buttonsList by remember { mutableStateOf(savedButtons.ifEmpty { ControlLayoutManager.getDefaultButtons() }) }
    var selectedButtonId by remember { mutableStateOf<String?>(null) }
    var showKeyPickerForButtonId by remember { mutableStateOf<String?>(null) }
    var keyPickerTargetMode by remember { mutableStateOf("MAIN") } // "MAIN" or "COMBO"
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var manualFlipSide by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        ControlLayoutManager.init(context)
        if (ControlLayoutManager.buttons.value.isNotEmpty()) {
            buttonsList = ControlLayoutManager.buttons.value
        }
    }

    LaunchedEffect(savedButtons) {
        if (savedButtons.isNotEmpty() && buttonsList.isEmpty()) {
            buttonsList = savedButtons
        }
    }

    val selectedButton = remember(buttonsList, selectedButtonId) {
        buttonsList.firstOrNull { it.id == selectedButtonId }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(if (isIngame) Color.Black.copy(alpha = 0.40f) else Color(0xFF0E0E0E))
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        // 1. Blueprint Dot Grid Canvas (Subtle in-game so live game is visible, Tactical Dark in launcher)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 32.dp.toPx()
            val dotColor = if (isIngame) Color.White.copy(alpha = 0.20f) else Color(0xFF242424)
            val guideLineColor = if (isIngame) Color.White.copy(alpha = 0.15f) else Color(0xFF242424).copy(alpha = 0.6f)
            var x = step
            while (x < size.width) {
                var y = step
                while (y < size.height) {
                    drawCircle(dotColor, radius = 1.5.dp.toPx(), center = Offset(x, y))
                    y += step
                }
                x += step
            }

            // Center guide lines
            drawLine(
                color = guideLineColor,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = guideLineColor,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx()
            )
        }

        // 2. Interactive Draggable Buttons on Screen
        buttonsList.forEach { btn ->
            val isSelected = btn.id == selectedButtonId
            val btnWidthPx = with(density) { btn.widthDp.dp.toPx() }
            val btnHeightPx = with(density) { btn.heightDp.dp.toPx() }

            // Convert percentage coordinates to pixel center
            val centerX = screenWidthPx * (btn.xPercent / 100f)
            val centerY = screenHeightPx * (btn.yPercent / 100f)

            val leftPx = (centerX - btnWidthPx / 2f).coerceIn(0f, (screenWidthPx - btnWidthPx).coerceAtLeast(0f))
            val topPx = (centerY - btnHeightPx / 2f).coerceIn(0f, (screenHeightPx - btnHeightPx).coerceAtLeast(0f))

            val leftDp = with(density) { leftPx.toDp() }
            val topDp = with(density) { topPx.toDp() }

            val spec = remember(btn) { skinSpecFor(btn) }

            Box(
                modifier = Modifier
                    .offset(x = leftDp, y = topDp)
                    .size(width = btn.widthDp.dp, height = btn.heightDp.dp)
                    // Gambar yang sama persis dengan di dalam game (ControlSkin).
                    .drawBehind {
                        when {
                            btn.isJoystick -> drawControlJoystick(btn.opacity, Offset.Zero, false, false, isSelected)
                            btn.isScroll -> drawControlScroll(spec, false, isSelected)
                            else -> drawControlButton(spec, false, isSelected)
                        }
                    }
                    .pointerInput(btn.id) {
                        detectTapGestures {
                            manualFlipSide = null
                            selectedButtonId = if (selectedButtonId == btn.id) null else btn.id
                        }
                    }
                    .pointerInput(btn.id, screenWidthPx, screenHeightPx) {
                        detectDragGestures(
                            onDragStart = {
                                manualFlipSide = null
                                selectedButtonId = btn.id
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val currentBtn = buttonsList.firstOrNull { it.id == btn.id } ?: return@detectDragGestures
                                val curCenterX = screenWidthPx * (currentBtn.xPercent / 100f)
                                val curCenterY = screenHeightPx * (currentBtn.yPercent / 100f)

                                // Ukuran dibaca dari tombol saat ini (bukan yang ditangkap saat composition),
                                // supaya batas geser ikut berubah setelah slider ukuran digeser. Tombol yang
                                // lebih besar dari layar dipusatkan dan tidak membuat galat.
                                val halfW = with(density) { currentBtn.widthDp.dp.toPx() } / 2f
                                val halfH = with(density) { currentBtn.heightDp.dp.toPx() } / 2f
                                val newCenterX = (curCenterX + dragAmount.x).clampCenter(halfW, screenWidthPx - halfW)
                                val newCenterY = (curCenterY + dragAmount.y).clampCenter(halfH, screenHeightPx - halfH)

                                val newXPercent = (newCenterX / screenWidthPx) * 100f
                                val newYPercent = (newCenterY / screenHeightPx) * 100f

                                buttonsList = buttonsList.map {
                                    if (it.id == btn.id) it.copy(xPercent = newXPercent, yPercent = newYPercent) else it
                                }
                            }
                        )
                    }
            )
        }

        // 3. TOP ACTION BAR (Clean & Un-clipped Floating Header)
        // Top-Left: Kembali / Selesai In-Game Button
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(if (isIngame) Color(0xFFFFD60A) else Color(0xFF171717), RoundedCornerShape(8.dp))
                .border(1.2.dp, if (isIngame) Color(0xFFE9E4CC) else Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                .clickable {
                    if (isIngame) {
                        ControlLayoutManager.saveButtons(context, buttonsList)
                        Toast.makeText(context, "✓ Layout tombol in-game disimpan!", Toast.LENGTH_SHORT).show()
                    }
                    onNavigateBack()
                }
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isIngame) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = if (isIngame) "Selesai" else "Kembali",
                    tint = if (isIngame) Color(0xFF111111) else Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isIngame) "SELESAI" else "KEMBALI",
                    color = if (isIngame) Color(0xFF111111) else Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }
        }

        // Top-Center: Compact Floating Action Pill (+ Tambah, Reset, Simpan)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(Color(0xFF171717), RoundedCornerShape(10.dp))
                .border(1.5.dp, Color(0xFF2E2E2E), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // + TAMBAH
            Box(
                modifier = Modifier
                    .background(Color(0xFF3A3A3A), RoundedCornerShape(6.dp))
                    .clickable {
                        val newBtn = CustomControlButton(
                            id = "btn_" + UUID.randomUUID().toString().take(6),
                            name = "NEW",
                            keyCode = LwjglGlfwKeycode.GLFW_KEY_SPACE,
                            xPercent = 50f,
                            yPercent = 50f,
                            widthDp = 52,
                            heightDp = 48
                        )
                        buttonsList = buttonsList + newBtn
                        selectedButtonId = newBtn.id
                        manualFlipSide = null
                        // Langsung pilih fungsinya, tidak perlu cari tombol "Ubah" dulu.
                        keyPickerTargetMode = "MAIN"
                        showKeyPickerForButtonId = newBtn.id
                        Toast.makeText(context, "Tombol baru ditambahkan di tengah layar!", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ TOMBOL", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.White)
            }

            // + JOYSTICK
            Box(
                modifier = Modifier
                    .background(Color(0xFF262626).copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFFFFD60A), RoundedCornerShape(6.dp))
                    .clickable {
                        val newJoy = CustomControlButton(
                            id = "joy_" + UUID.randomUUID().toString().take(6),
                            name = "JOYSTICK",
                            isJoystick = true,
                            xPercent = 20f,
                            yPercent = 70f,
                            widthDp = 130,
                            heightDp = 130,
                            cornerRadiusDp = 65,
                            opacity = 0.85f
                        )
                        buttonsList = buttonsList + newJoy
                        selectedButtonId = newJoy.id
                        manualFlipSide = null
                        Toast.makeText(context, "Joystick WASD ditambahkan!", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("🕹 + JOYSTICK", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color(0xFFFFE566))
            }

            // RESET
            Box(
                modifier = Modifier
                    .background(Color(0xFF381C1C), RoundedCornerShape(6.dp))
                    .clickable { showResetConfirmDialog = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("RESET", color = Color(0xFFFF8A80), fontWeight = FontWeight.Black, fontSize = 11.sp)
            }

            // SIMPAN
            Box(
                modifier = Modifier
                    .background(Color(0xFFFFD60A), RoundedCornerShape(6.dp))
                    .clickable {
                        ControlLayoutManager.saveButtons(context, buttonsList)
                        Toast.makeText(context, "✓ Layout GUI berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("SIMPAN", color = Color(0xFF111111), fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        }

        // 4. SMART AUTO-DOCKING INSPECTOR PANEL (NEVER COVERS THE SELECTED BUTTON!)
        if (selectedButton != null) {
            // If the selected button is on the right half, auto-dock panel on the LEFT side.
            // If the button is on the left half, auto-dock panel on the RIGHT side.
            val shouldDockLeft = manualFlipSide ?: (selectedButton.xPercent > 48f)

            val panelAlignment = if (shouldDockLeft) Alignment.CenterStart else Alignment.CenterEnd
            val panelPadding = if (shouldDockLeft) PaddingValues(start = 16.dp) else PaddingValues(end = 16.dp)

            Box(
                modifier = Modifier
                    .align(panelAlignment)
                    .padding(panelPadding)
            ) {
                NuxCard(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(0.85f),
                    backgroundColor = Color(0xFF171717),
                    shadowOffset = 4.dp,
                    cornerRadius = 14.dp,
                    fillMaxHeight = true
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Inspector Header with Flip-Side & Close
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PENGATURAN TOMBOL",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Posisi: ${selectedButton.xPercent.roundToInt()}% x ${selectedButton.yPercent.roundToInt()}%",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFE566)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Flip Side Button (allows user to move panel to other side manually)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(0xFF242424), CircleShape)
                                        .border(1.dp, Color(0xFF3D3D3D), CircleShape)
                                        .clickable { manualFlipSide = !shouldDockLeft },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⇄", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFE566))
                                }

                                // Close Button
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(0xFF3B1E22), CircleShape)
                                        .border(1.dp, Color(0xFF5E2D32), CircleShape)
                                        .clickable { selectedButtonId = null },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✕", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF8A80))
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFF2E2E2E), thickness = 1.dp)

                        // 1. Label Name Field
                        Text("Label Teks Tombol:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE9E4CC))
                        if (selectedButton.isSystem) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF202020).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedButton.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("🔒 Tetap", color = Color(0xFFFFE566), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF202020), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                if (selectedButton.name.isEmpty()) {
                                    Text("Label tombol...", color = Color(0xFF8C8774), fontSize = 13.sp)
                                }
                                BasicTextField(
                                    value = selectedButton.name,
                                    onValueChange = { newName ->
                                        buttonsList = buttonsList.map { if (it.id == selectedButton.id) it.copy(name = newName) else it }
                                    },
                                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                    cursorBrush = SolidColor(Color(0xFFFFD60A)),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (selectedButton.isJoystick) {
                            Text("Tipe Kontrol:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE9E4CC))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF181818), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("🕹 Virtual Analog Joystick (WASD)", color = Color(0xFFFFE566), fontWeight = FontWeight.Black, fontSize = 11.sp)
                                    Text("Analog 8-arah halus menggerakkan karakter menggantikan tombol W, A, S, D.", color = Color(0xFFE9E4CC), fontSize = 9.sp)
                                }
                            }

                            // Diameter Slider
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ukuran Joystick (Diameter):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE9E4CC))
                                Text("${selectedButton.widthDp} dp", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFE566))
                            }
                            Slider(
                                value = selectedButton.widthDp.toFloat(),
                                onValueChange = { newD ->
                                    val d = newD.roundToInt()
                                    buttonsList = buttonsList.map {
                                        if (it.id == selectedButton.id) it.copy(widthDp = d, heightDp = d, cornerRadiusDp = d / 2) else it
                                    }
                                },
                                valueRange = ControlLimits.MIN_JOYSTICK_DP.toFloat()..ControlLimits.MAX_JOYSTICK_DP.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFFD60A),
                                    activeTrackColor = Color(0xFFFFD60A),
                                    inactiveTrackColor = Color(0xFF2E2E2E)
                                )
                            )

                            // Opacity Slider
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Transparansi (Opacity):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE9E4CC))
                                Text("${(selectedButton.opacity * 100).roundToInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFE566))
                            }
                            Slider(
                                value = selectedButton.opacity,
                                onValueChange = { newOp ->
                                    buttonsList = buttonsList.map { if (it.id == selectedButton.id) it.copy(opacity = (newOp * 100).roundToInt() / 100f) else it }
                                },
                                valueRange = 0.15f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFFD60A),
                                    activeTrackColor = Color(0xFFFFD60A),
                                    inactiveTrackColor = Color(0xFF2E2E2E)
                                )
                            )

                            // Lari otomatis (khusus joystick)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Lari otomatis:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Dorong sampai tepi lingkaran untuk berlari. Matikan bila ingin berlari lewat tombol CTRL sendiri.", fontSize = 8.sp, color = Color(0xFFE9E4CC))
                                }
                                Switch(
                                    checked = selectedButton.autoSprint,
                                    onCheckedChange = { on ->
                                        buttonsList = buttonsList.map { if (it.id == selectedButton.id) it.copy(autoSprint = on) else it }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF111111),
                                        checkedTrackColor = Color(0xFFFFD60A),
                                        uncheckedThumbColor = Color(0xFF757575),
                                        uncheckedTrackColor = Color(0xFF2E2E2E)
                                    )
                                )
                            }
                        } else {
                            // 2. Mapped Keycode / Input
                            Text("Tombol / Aksi Input:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE9E4CC))
                            if (selectedButton.isSystem) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF202020).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    val actionDesc = when (selectedButton.systemAction) {
                                        "FPS" -> "Indikator FPS In-Game"
                                        "KEYBOARD" -> "Toggle Keyboard Layar"
                                        "HIDE_GUI" -> "Sembunyikan/Tampilkan GUI"
                                        "MENU", "CLOSE" -> "Buka Menu (pengaturan dan keluar)"
                                        else -> "Aksi Sistem Launcher"
                                    }
                                    Text(actionDesc, color = Color(0xFFFFE566), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            } else {
                                val mappedKeyName = remember(selectedButton) {
                                    if (selectedButton.isScroll) {
                                        "Scroll Wheel (Slide Naik/Turun)"
                                    } else if (selectedButton.isMouseButton) {
                                        when (selectedButton.mouseButton) {
                                            LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT -> "Mouse Kiri (Attack)"
                                            LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT -> "Mouse Kanan (Use)"
                                            LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE -> "Mouse Tengah (Pick)"
                                            else -> "Mouse Btn ${selectedButton.mouseButton}"
                                        }
                                    } else {
                                        KeycodeCatalog.ALL_KEYS.firstOrNull { it.keyCode == selectedButton.keyCode }?.displayName
                                            ?: "Key Code ${selectedButton.keyCode}"
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF202020), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(8.dp))
                                        .clickable {
                                            keyPickerTargetMode = "MAIN"
                                            showKeyPickerForButtonId = selectedButton.id
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(mappedKeyName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ubah >", color = Color(0xFFFFE566), fontWeight = FontWeight.Black, fontSize = 10.sp)
                                    }
                                }
                            }

                            // 3. Width Slider
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Lebar (Width):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE9E4CC))
                                Text("${selectedButton.widthDp} dp", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFE566))
                            }
                            Slider(
                                value = selectedButton.widthDp.toFloat(),
                                onValueChange = { newW ->
                                    buttonsList = buttonsList.map { if (it.id == selectedButton.id) it.copy(widthDp = newW.roundToInt()) else it }
                                },
                                valueRange = ControlLimits.MIN_SIZE_DP.toFloat()..ControlLimits.MAX_WIDTH_DP.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFFD60A),
                                    activeTrackColor = Color(0xFFFFD60A),
                                    inactiveTrackColor = Color(0xFF2E2E2E)
                                )
                            )

                            // 4. Height Slider
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tinggi (Height):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE9E4CC))
                                Text("${selectedButton.heightDp} dp", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFE566))
                            }
                            Slider(
                                value = selectedButton.heightDp.toFloat(),
                                onValueChange = { newH ->
                                    buttonsList = buttonsList.map { if (it.id == selectedButton.id) it.copy(heightDp = newH.roundToInt()) else it }
                                },
                                valueRange = ControlLimits.MIN_SIZE_DP.toFloat()..ControlLimits.MAX_HEIGHT_DP.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFFD60A),
                                    activeTrackColor = Color(0xFFFFD60A),
                                    inactiveTrackColor = Color(0xFF2E2E2E)
                                )
                            )

                            // 5. Opacity Slider
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Transparansi (Opacity):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE9E4CC))
                                Text("${(selectedButton.opacity * 100).roundToInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFE566))
                            }
                            Slider(
                                value = selectedButton.opacity,
                                onValueChange = { newOp ->
                                    buttonsList = buttonsList.map { if (it.id == selectedButton.id) it.copy(opacity = (newOp * 100).roundToInt() / 100f) else it }
                                },
                                valueRange = 0.15f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFFD60A),
                                    activeTrackColor = Color(0xFFFFD60A),
                                    inactiveTrackColor = Color(0xFF2E2E2E)
                                )
                            )

                            // 6. Corner Radius Slider
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Kebulatan Sudut:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE9E4CC))
                                Text("${selectedButton.cornerRadiusDp} dp", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFE566))
                            }
                            Slider(
                                value = selectedButton.cornerRadiusDp.toFloat(),
                                onValueChange = { newR ->
                                    buttonsList = buttonsList.map { if (it.id == selectedButton.id) it.copy(cornerRadiusDp = newR.roundToInt()) else it }
                                },
                                valueRange = 0f..ControlLimits.MAX_CORNER_DP.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFFD60A),
                                    activeTrackColor = Color(0xFFFFD60A),
                                    inactiveTrackColor = Color(0xFF2E2E2E)
                                )
                            )
                        }

                        // 7. Toggle Mode Switch (Only for normal key buttons)
                        if (!selectedButton.isSystem && !selectedButton.isJoystick) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Mode Toggle:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Sekali tap untuk kunci", fontSize = 8.sp, color = Color(0xFFE9E4CC))
                                }
                                Switch(
                                    checked = selectedButton.isToggle,
                                    onCheckedChange = { isToggled ->
                                        buttonsList = buttonsList.map { if (it.id == selectedButton.id) it.copy(isToggle = isToggled) else it }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF111111),
                                        checkedTrackColor = Color(0xFFFFD60A),
                                        uncheckedThumbColor = Color(0xFF757575),
                                        uncheckedTrackColor = Color(0xFF2E2E2E)
                                    )
                                )
                            }
                        }

                        // 8. Fitur Makro (Macro Settings)
                        if (!selectedButton.isSystem && !selectedButton.isScroll && !selectedButton.isJoystick) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF181818), RoundedCornerShape(10.dp))
                                    .border(1.dp, if (selectedButton.isMacro) Color(0xFFFFD60A) else Color(0xFF2E2E2E), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Macro Header & Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("⚡ FITUR MAKRO", fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (selectedButton.isMacro) Color(0xFFFFE566) else Color.White)
                                            if (selectedButton.isMacro) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0x33FFD60A), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text("AKTIF", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFE566))
                                                }
                                            }
                                        }
                                        Text("Jadikan tombol ini sebagai makro otomatis", fontSize = 8.sp, color = Color(0xFFE9E4CC))
                                    }
                                    Switch(
                                        checked = selectedButton.isMacro,
                                        onCheckedChange = { isMacroEnabled ->
                                            buttonsList = buttonsList.map {
                                                if (it.id == selectedButton.id) it.copy(isMacro = isMacroEnabled) else it
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF111111),
                                            checkedTrackColor = Color(0xFFFFD60A),
                                            uncheckedThumbColor = Color(0xFF757575),
                                            uncheckedTrackColor = Color(0xFF2E2E2E)
                                        )
                                    )
                                }

                                if (selectedButton.isMacro) {
                                    HorizontalDivider(color = Color(0xFF252525), thickness = 1.dp)

                                    // Macro Mode Selector
                                    Text("Pilih Mode Makro:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE9E4CC))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val types = listOf(
                                            Triple("COMMAND", "⌨ Chat/Perintah", "Ketik otomatis"),
                                            Triple("COMBO", "🔗 Kombinasi", "Multi-Key"),
                                            Triple("TURBO", "⚡ Turbo Click", "Auto-clicker")
                                        )
                                        types.forEach { (typeKey, typeLabel, _) ->
                                            val isChosen = selectedButton.macroType == typeKey
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(
                                                        if (isChosen) Color(0xFFFFD60A) else Color(0xFF202020),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isChosen) Color(0xFFFFE566) else Color(0xFF313131),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .clickable {
                                                        buttonsList = buttonsList.map {
                                                            if (it.id == selectedButton.id) it.copy(macroType = typeKey) else it
                                                        }
                                                    }
                                                    .padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = typeLabel,
                                                    fontSize = 8.sp,
                                                    fontWeight = if (isChosen) FontWeight.Black else FontWeight.Bold,
                                                    color = if (isChosen) Color(0xFF111111) else Color.White,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    // Mode 1: COMMAND
                                    if (selectedButton.macroType == "COMMAND") {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Teks / Perintah yang diketik otomatis:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            BasicTextField(
                                                value = selectedButton.macroCommand,
                                                onValueChange = { newCmd ->
                                                    buttonsList = buttonsList.map {
                                                        if (it.id == selectedButton.id) it.copy(macroCommand = newCmd) else it
                                                    }
                                                },
                                                textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                                cursorBrush = SolidColor(Color(0xFFFFE566)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFF121212), RoundedCornerShape(6.dp))
                                                    .border(1.dp, Color(0xFF313131), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                decorationBox = { innerTextField ->
                                                    if (selectedButton.macroCommand.isEmpty()) {
                                                        Text(
                                                            text = "Misal: /gamemode creative atau /home",
                                                            color = Color(0xFF8C8774),
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            )

                                            // Quick Chips
                                            Text("Template Cepat:", fontSize = 8.sp, color = Color(0xFFE9E4CC))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                listOf("/gamemode c", "/gamemode s", "/spawn", "/home").forEach { sample ->
                                                    Box(
                                                        modifier = Modifier
                                                            .background(Color(0xFF1D1D1D), RoundedCornerShape(4.dp))
                                                            .border(0.5.dp, Color(0xFF2E2E2E), RoundedCornerShape(4.dp))
                                                            .clickable {
                                                                buttonsList = buttonsList.map {
                                                                    if (it.id == selectedButton.id) it.copy(macroCommand = sample) else it
                                                                }
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Text(sample, fontSize = 8.sp, color = Color(0xFFFFE566), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Mode 2: COMBO (Multi-key sequence executed top to bottom)
                                    if (selectedButton.macroType == "COMBO") {
                                        val comboKeysList = remember(selectedButton.macroComboKeys, selectedButton.macroComboKey) {
                                            if (selectedButton.macroComboKeys.isNotEmpty()) {
                                                selectedButton.macroComboKeys
                                            } else if (selectedButton.macroComboKey != 0) {
                                                listOf(selectedButton.macroComboKey)
                                            } else {
                                                emptyList()
                                            }
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Urutan Key Kombinasi:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                if (comboKeysList.isNotEmpty()) {
                                                    Text(
                                                        text = "Hapus Semua",
                                                        fontSize = 8.sp,
                                                        color = Color(0xFFFF8A80),
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.clickable {
                                                            buttonsList = buttonsList.map {
                                                                if (it.id == selectedButton.id) it.copy(macroComboKeys = emptyList(), macroComboKey = 0) else it
                                                            }
                                                        }
                                                    )
                                                }
                                            }

                                            if (comboKeysList.isEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF121212), RoundedCornerShape(6.dp))
                                                        .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(6.dp))
                                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "Belum ada key kombinasi. Tambahkan key di bawah.",
                                                        color = Color(0xFFFFE566),
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            } else {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    comboKeysList.forEachIndexed { index, keyCode ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(Color(0xFF121212), RoundedCornerShape(6.dp))
                                                                .border(1.dp, Color(0xFF313131), RoundedCornerShape(6.dp))
                                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(Color(0x33FFD60A), RoundedCornerShape(4.dp))
                                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text("#${index + 1}", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFE566))
                                                                }
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(
                                                                    text = KeycodeCatalog.getKeyName(keyCode),
                                                                    color = Color.White,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 10.sp,
                                                                    maxLines = 1
                                                                )
                                                            }
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(20.dp)
                                                                    .background(Color(0xFF3B1E22), CircleShape)
                                                                    .clickable {
                                                                        val updated = comboKeysList.toMutableList().apply { removeAt(index) }
                                                                        buttonsList = buttonsList.map {
                                                                            if (it.id == selectedButton.id) {
                                                                                it.copy(macroComboKeys = updated, macroComboKey = updated.firstOrNull() ?: 0)
                                                                            } else it
                                                                        }
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text("✕", fontSize = 9.sp, color = Color(0xFFFF8A80), fontWeight = FontWeight.Black)
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Add Key Button
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFF202020), RoundedCornerShape(6.dp))
                                                    .border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        keyPickerTargetMode = "COMBO"
                                                        showKeyPickerForButtonId = selectedButton.id
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("+", fontSize = 12.sp, color = Color(0xFFFFE566), fontWeight = FontWeight.Black)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Tambah Key ke Kombinasi", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Quick Combo Chips
                                            Text("Tambah Cepat:", fontSize = 8.sp, color = Color(0xFFE9E4CC))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                listOf(
                                                    "F3" to LwjglGlfwKeycode.GLFW_KEY_F3,
                                                    "B" to LwjglGlfwKeycode.GLFW_KEY_B,
                                                    "SHIFT" to LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT,
                                                    "CTRL" to LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL,
                                                    "ALT" to LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT,
                                                    "Q" to LwjglGlfwKeycode.GLFW_KEY_Q
                                                ).forEach { (label, code) ->
                                                    Box(
                                                        modifier = Modifier
                                                            .background(Color(0xFF1D1D1D), RoundedCornerShape(4.dp))
                                                            .border(0.5.dp, Color(0xFF2E2E2E), RoundedCornerShape(4.dp))
                                                            .clickable {
                                                                val updated = comboKeysList + code
                                                                buttonsList = buttonsList.map {
                                                                    if (it.id == selectedButton.id) {
                                                                        it.copy(macroComboKeys = updated, macroComboKey = updated.firstOrNull() ?: 0)
                                                                    } else it
                                                                }
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Text("+ $label", fontSize = 8.sp, color = Color(0xFFFFE566), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Text("Dieksekusi berurutan dari atas (#1) ke bawah saat ditekan.", fontSize = 8.sp, color = Color(0xFFE9E4CC))
                                        }
                                    }

                                    // Mode 3: TURBO
                                    if (selectedButton.macroType == "TURBO") {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Interval Auto-Click:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                val cps = (1000f / selectedButton.macroTurboIntervalMs.coerceAtLeast(1L)).roundToInt()
                                                Text("${selectedButton.macroTurboIntervalMs} ms (~$cps CPS)", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFE566))
                                            }
                                            Slider(
                                                value = selectedButton.macroTurboIntervalMs.toFloat(),
                                                onValueChange = { newInterval ->
                                                    buttonsList = buttonsList.map {
                                                        if (it.id == selectedButton.id) it.copy(macroTurboIntervalMs = newInterval.toLong()) else it
                                                    }
                                                },
                                                valueRange = 40f..500f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Color(0xFFFFD60A),
                                                    activeTrackColor = Color(0xFFFFD60A),
                                                    inactiveTrackColor = Color(0xFF2E2E2E)
                                                )
                                            )
                                            Text("Tombol akan menekan & melepas berulang-ulang sangat cepat secara otomatis saat ditekan.", fontSize = 8.sp, color = Color(0xFFE9E4CC))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // 8. Delete Button (or System locked notice)
                        if (!selectedButton.isSystem) {
                            NuxButton(
                                onClick = {
                                    buttonsList = buttonsList.filter { it.id != selectedButton.id }
                                    selectedButtonId = null
                                    Toast.makeText(context, "Tombol dihapus", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                backgroundColor = Color(0xFF3E181B),
                                contentColor = Color(0xFFFF8A80),
                                shadowOffset = 2.dp,
                                cornerRadius = 8.dp
                            ) {
                                Text("HAPUS TOMBOL", color = Color(0xFFFF8A80), fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF232323), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🔒 Tombol sistem tidak dapat dihapus",
                                    color = Color(0xFFE9E4CC),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. In-Game Mode Floating Status Badge
        if (isIngame) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(Color(0xCC111111), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0x66FFD60A), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFFFFD60A), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "MODE IN-GAME · Geser tombol sesuai HUD game lalu klik SELESAI",
                        color = Color(0xFFFFE566),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Keycode Picker Dialog (pilih kategori lalu ketuk tombol)
    if (showKeyPickerForButtonId != null) {
        val targetId = showKeyPickerForButtonId!!
        val isComboMode = keyPickerTargetMode == "COMBO"
        val targetButton = buttonsList.firstOrNull { it.id == targetId }

        NuxKeyPickerDialog(
            comboMode = isComboMode,
            onDismiss = { showKeyPickerForButtonId = null },
            isCurrent = { k ->
                if (isComboMode || targetButton == null) false
                else if (targetButton.isScroll) k.isScroll
                else if (targetButton.isMouseButton) k.isMouseButton && k.mouseButton == targetButton.mouseButton
                else !k.isScroll && !k.isMouseButton && k.keyCode == targetButton.keyCode
            },
            onPick = { keyOpt ->
                buttonsList = buttonsList.map {
                    if (it.id == targetId) {
                        if (isComboMode) {
                            val existing = it.macroComboKeys.ifEmpty {
                                if (it.macroComboKey != 0) listOf(it.macroComboKey) else emptyList()
                            }
                            val updated = existing + keyOpt.keyCode
                            it.copy(macroComboKeys = updated, macroComboKey = updated.firstOrNull() ?: 0)
                        } else {
                            it.copy(
                                keyCode = keyOpt.keyCode,
                                isMouseButton = keyOpt.isMouseButton,
                                mouseButton = keyOpt.mouseButton,
                                isScroll = keyOpt.isScroll,
                                name = if (keyOpt.isScroll) "SCROLL"
                                else if (it.name == "NEW" || it.name == "BTN" || it.name == "SCROLL") {
                                    keyOpt.displayName.substringBefore(" ").take(6)
                                } else it.name
                            )
                        }
                    } else it
                }
                showKeyPickerForButtonId = null
            }
        )
    }

    // Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        Dialog(onDismissRequest = { showResetConfirmDialog = false }) {
            NuxCard(
                modifier = Modifier.width(320.dp),
                backgroundColor = Color(0xFF171717),
                shadowOffset = 6.dp,
                cornerRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Reset Layout GUI?", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                    Text("Semua posisi tombol dan kustomisasi akan dikembalikan ke tata letak awal standar AmberLauncher.", fontSize = 12.sp, color = Color(0xFFE9E4CC))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NuxButton(
                            onClick = { showResetConfirmDialog = false },
                            modifier = Modifier.weight(1f).height(44.dp),
                            backgroundColor = Color(0xFF242424),
                            contentColor = Color.White,
                            shadowOffset = 2.dp
                        ) {
                            Text("BATAL", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.White)
                        }

                        NuxButton(
                            onClick = {
                                buttonsList = ControlLayoutManager.getDefaultButtons()
                                selectedButtonId = null
                                showResetConfirmDialog = false
                                Toast.makeText(context, "Layout direset ke standar", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            backgroundColor = Color(0xFFC62828),
                            contentColor = Color.White,
                            shadowOffset = 2.dp
                        ) {
                            Text("RESET", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
