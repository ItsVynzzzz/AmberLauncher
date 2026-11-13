package com.israadev.nuxlauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.controls.MacroScript
import com.israadev.nuxlauncher.core.controls.MacroStep
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import com.israadev.nuxlauncher.core.controls.models.MacroRunMode
import com.israadev.nuxlauncher.ui.i18n.Text
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import java.util.Locale
import kotlin.math.roundToInt

// Warna editor tombol (gaya gelap-kuning yang sama dengan bagian makro lain di CustomGuiEditorScreen).
private val Gold = Color(0xFFFFD60A)
private val GoldSoft = Color(0xFFFFE566)
private val Cream = Color(0xFFE9E4CC)
private val PanelDark = Color(0xFF121212)
private val LineDark = Color(0xFF313131)
private val ChipDark = Color(0xFF1D1D1D)
private val Danger = Color(0xFFFF8A80)

/** Pilihan jenis makro (kotak kecil terisi kuning bila dipilih). */
@Composable
internal fun MacroTypeChip(
    label: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .background(if (chosen) Gold else Color(0xFF202020), shape)
            .border(1.dp, if (chosen) GoldSoft else LineDark, shape)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = if (chosen) FontWeight.Black else FontWeight.Bold,
            color = if (chosen) Color(0xFF111111) else Color.White,
            maxLines = 1
        )
    }
}

/** Slider variasi waktu acak, dipakai Turbo dan Urutan Aksi. 0 = mati. */
@Composable
internal fun MacroJitterSlider(percent: Int, onChange: (Int) -> Unit) {
    val p = percent.coerceIn(0, MacroScript.MAX_JITTER)
    MacroSlider(
        label = "Variasi Waktu Acak:",
        valueText = if (p == 0) "Mati" else "±$p%",
        value = p.toFloat(),
        range = 0f..MacroScript.MAX_JITTER.toFloat()
    ) { onChange(it.roundToInt().coerceIn(0, MacroScript.MAX_JITTER)) }
}

/**
 * Editor makro "Urutan Aksi": cara jalan, daftar langkah (key, klik, tahan, gulir, jeda, ketik), dan
 * templat siap pakai. Perubahan dikirim lewat [onChange] sebagai tombol baru. [onPickKey] membuka
 * pemilih tombol untuk langkah berjenis TAP / HOLD / DOWN / UP; hasil pilihannya dimasukkan oleh
 * pemanggil lewat [MacroScript.stepFromPick].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacroSequenceEditor(
    button: CustomControlButton,
    onChange: (CustomControlButton) -> Unit,
    onPickKey: (String) -> Unit
) {
    val tokens = button.macroSteps
    var expanded by remember(button.id) { mutableIntStateOf(-1) }
    val parsed = remember(tokens) { tokens.map { MacroScript.parse(it) } }
    val estimateMs = remember(tokens) { MacroScript.estimateMs(parsed.filterNotNull()) }
    val mode = if (MacroRunMode.isValid(button.macroRunMode)) button.macroRunMode else MacroRunMode.ONCE

    fun setTokens(list: List<String>) {
        onChange(button.copy(macroSteps = list))
    }

    fun addToken(token: String) {
        if (tokens.size < MacroScript.MAX_STEPS) setTokens(tokens + token)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // ---- Cara jalan ----
        Text("Cara Jalan:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                MacroRunMode.ONCE to "Sekali",
                MacroRunMode.HOLD to "Tahan",
                MacroRunMode.TOGGLE to "Nyala/Mati"
            ).forEach { (key, label) ->
                MacroTypeChip(
                    label = label,
                    chosen = mode == key,
                    onClick = { onChange(button.copy(macroRunMode = key)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            text = when (mode) {
                MacroRunMode.HOLD -> "Diulang terus selama tombol ditahan."
                MacroRunMode.TOGGLE -> "Diulang terus sampai tombol diketuk lagi."
                else -> "Jalan sekali setiap ketuk. Ketuk lagi saat berjalan untuk menghentikan."
            },
            fontSize = 8.sp,
            color = Cream
        )

        val repeat = button.macroRepeat.coerceIn(1, MacroScript.MAX_REPEAT)
        if (mode == MacroRunMode.ONCE) {
            MacroSlider(
                label = "Ulangi:",
                valueText = "${repeat}x",
                value = repeat.toFloat(),
                range = 1f..MacroScript.MAX_REPEAT.toFloat()
            ) { onChange(button.copy(macroRepeat = it.roundToInt().coerceIn(1, MacroScript.MAX_REPEAT))) }
        }
        if (mode != MacroRunMode.ONCE || repeat > 1) {
            val loopDelay = button.macroLoopDelayMs.coerceIn(0, MacroScript.MAX_LOOP_DELAY_MS)
            MacroSlider(
                label = "Jeda Antar Putaran:",
                valueText = "$loopDelay ms",
                value = loopDelay.toFloat(),
                range = 0f..2000f
            ) { onChange(button.copy(macroLoopDelayMs = (it / 50f).roundToInt() * 50)) }
        }
        MacroJitterSlider(button.macroJitterPercent) { onChange(button.copy(macroJitterPercent = it)) }
        Text(
            text = "Waktu tunggu dan tahan digeser acak sedikit supaya tidak terlalu kaku.",
            fontSize = 8.sp,
            color = Cream
        )

        // ---- Daftar langkah ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Langkah Makro:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("${tokens.size}/${MacroScript.MAX_STEPS}", fontSize = 8.sp, fontWeight = FontWeight.Black, color = GoldSoft)
            }
            if (tokens.isNotEmpty()) {
                Text(
                    text = "Hapus Semua",
                    fontSize = 8.sp,
                    color = Danger,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        expanded = -1
                        setTokens(emptyList())
                    }
                )
            }
        }

        if (tokens.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelDark, RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(6.dp))
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Belum ada langkah. Tambahkan lewat tombol di bawah, atau pilih templat.",
                    color = GoldSoft,
                    fontSize = 9.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tokens.forEachIndexed { index, token ->
                    MacroStepRow(
                        index = index,
                        count = tokens.size,
                        token = token,
                        step = parsed[index],
                        expanded = expanded == index,
                        onToggle = { expanded = if (expanded == index) -1 else index },
                        onMove = { delta ->
                            val target = index + delta
                            if (target in tokens.indices) {
                                val moved = tokens.toMutableList()
                                val item = moved.removeAt(index)
                                moved.add(target, item)
                                expanded = -1
                                setTokens(moved)
                            }
                        },
                        onDelete = {
                            val rest = tokens.toMutableList()
                            rest.removeAt(index)
                            expanded = -1
                            setTokens(rest)
                        },
                        onChangeStep = { newStep ->
                            val edited = tokens.toMutableList()
                            edited[index] = MacroScript.toToken(newStep)
                            setTokens(edited)
                        }
                    )
                }
            }
        }

        // ---- Tambah langkah ----
        Text("Tambah Langkah:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MacroAddChip("Tekan") { onPickKey("TAP") }
            MacroAddChip("Tahan") { onPickKey("HOLD") }
            MacroAddChip("Tekan terus") { onPickKey("DOWN") }
            MacroAddChip("Lepas") { onPickKey("UP") }
            MacroAddChip("Tunggu") { addToken("WAIT:${MacroScript.DEFAULT_WAIT_MS}") }
            MacroAddChip("Gulir") { addToken("SCROLL:1") }
            MacroAddChip("Ketik") {
                if (tokens.size < MacroScript.MAX_STEPS) {
                    expanded = tokens.size
                    addToken("TYPE:")
                }
            }
        }

        Text("Pindah Slot Hotbar:", fontSize = 8.sp, color = Cream)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (slot in 1..9) {
                MacroAddChip(label = slot.toString(), plus = false) {
                    addToken("TAP:${LwjglGlfwKeycode.GLFW_KEY_1 + slot - 1}")
                }
            }
        }

        // ---- Templat ----
        Text("Templat Cepat (mengganti urutan):", fontSize = 8.sp, color = Cream)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MacroScript.TEMPLATES.forEach { template ->
                MacroAddChip(label = template.title, plus = false) {
                    expanded = -1
                    onChange(
                        button.copy(
                            macroType = "SEQUENCE",
                            macroSteps = template.steps,
                            macroRunMode = template.runMode,
                            macroRepeat = template.repeat,
                            macroLoopDelayMs = template.loopDelayMs
                        )
                    )
                }
            }
        }

        // ---- Ringkasan & catatan ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Perkiraan satu putaran:", fontSize = 8.sp, color = Cream)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = String.format(Locale.US, "%.1f s", estimateMs / 1000f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = GoldSoft
            )
        }
        Text(
            text = "Tombol yang masih tertahan otomatis dilepas saat makro berhenti.",
            fontSize = 8.sp,
            color = Cream
        )
        Text(
            text = "Catatan: sebagian server melarang makro. Gunakan dengan bijak.",
            fontSize = 8.sp,
            color = Cream
        )
    }
}

/** Satu baris langkah: nomor, jenis + rincian, panah urut, hapus. Ketuk baris untuk mengatur lamanya/teksnya. */
@Composable
private fun MacroStepRow(
    index: Int,
    count: Int,
    token: String,
    step: MacroStep?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onChangeStep: (MacroStep) -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    val editable = step is MacroStep.Wait || step is MacroStep.KeyHold || step is MacroStep.MouseHold ||
        step is MacroStep.Type || step is MacroStep.Scroll

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelDark, shape)
            .border(1.dp, if (expanded) Gold else LineDark, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = editable) { onToggle() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0x33FFD60A), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("#${index + 1}", fontSize = 8.sp, fontWeight = FontWeight.Black, color = GoldSoft)
            }
            Spacer(modifier = Modifier.width(6.dp))
            if (step != null) {
                val (verb, detail) = MacroScript.label(step)
                Column(modifier = Modifier.weight(1f)) {
                    Text(verb, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = GoldSoft, maxLines = 1)
                    Text(
                        text = detail,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = token,
                    fontSize = 9.sp,
                    color = Danger,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (editable) {
                Text(if (expanded) "▾" else "✎", fontSize = 11.sp, color = GoldSoft)
                Spacer(modifier = Modifier.width(6.dp))
            }
            MacroMiniButton("▲", enabled = index > 0) { onMove(-1) }
            Spacer(modifier = Modifier.width(4.dp))
            MacroMiniButton("▼", enabled = index < count - 1) { onMove(1) }
            Spacer(modifier = Modifier.width(4.dp))
            MacroMiniButton("✕", danger = true) { onDelete() }
        }

        if (expanded && step != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (step) {
                    is MacroStep.Wait -> MacroSlider(
                        label = "Lama Tunggu:",
                        valueText = "${step.ms} ms",
                        value = step.ms.toFloat(),
                        range = MacroScript.MIN_WAIT_MS.toFloat()..5000f
                    ) { onChangeStep(MacroStep.Wait(snapMs(it))) }

                    is MacroStep.KeyHold -> MacroSlider(
                        label = "Lama Tahan:",
                        valueText = "${step.ms} ms",
                        value = step.ms.toFloat(),
                        range = MacroScript.MIN_HOLD_MS.toFloat()..5000f
                    ) { onChangeStep(step.copy(ms = snapMs(it))) }

                    is MacroStep.MouseHold -> MacroSlider(
                        label = "Lama Tahan:",
                        valueText = "${step.ms} ms",
                        value = step.ms.toFloat(),
                        range = MacroScript.MIN_HOLD_MS.toFloat()..5000f
                    ) { onChangeStep(step.copy(ms = snapMs(it))) }

                    is MacroStep.Scroll -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MacroTypeChip(
                            label = "Naik",
                            chosen = step.direction > 0,
                            onClick = { onChangeStep(MacroStep.Scroll(1)) },
                            modifier = Modifier.weight(1f)
                        )
                        MacroTypeChip(
                            label = "Turun",
                            chosen = step.direction < 0,
                            onClick = { onChangeStep(MacroStep.Scroll(-1)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    is MacroStep.Type -> {
                        Text(
                            "Teks / Perintah yang diketik otomatis:",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        BasicTextField(
                            value = step.text,
                            onValueChange = { v ->
                                onChangeStep(MacroStep.Type(v.filter { c -> c >= ' ' }.take(MacroScript.MAX_TEXT)))
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            cursorBrush = SolidColor(GoldSoft),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0C0C0C), RoundedCornerShape(6.dp))
                                .border(1.dp, LineDark, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            decorationBox = { innerTextField ->
                                if (step.text.isEmpty()) {
                                    Text(
                                        text = "Misal: /gamemode creative atau /home",
                                        color = Color(0xFF8C8774),
                                        fontSize = 10.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    else -> Unit
                }
            }
        }
    }
}

/** Waktu tersnap: kelipatan 10 ms di bawah 500 ms, kelipatan 50 ms di atasnya. */
private fun snapMs(v: Float): Int {
    val unit = if (v < 500f) 10 else 50
    return (v / unit).roundToInt() * unit
}

@Composable
private fun MacroMiniButton(
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(if (danger) Color(0xFF3B1E22) else Color(0xFF202020), CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = when {
                danger -> Danger
                enabled -> Color.White
                else -> Color(0xFF555555)
            }
        )
    }
}

/** Kotak kecil untuk menambah langkah / memilih templat. [plus] menambah tanda + di depan teks. */
@Composable
private fun MacroAddChip(label: String, plus: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier
            .background(ChipDark, shape)
            .border(0.5.dp, Color(0xFF2E2E2E), shape)
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (plus) {
            Text("+", fontSize = 9.sp, color = GoldSoft, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(label, fontSize = 8.sp, color = GoldSoft, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MacroSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(valueText, fontSize = 9.sp, fontWeight = FontWeight.Black, color = GoldSoft)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Gold,
                activeTrackColor = Gold,
                inactiveTrackColor = Color(0xFF2E2E2E)
            )
        )
    }
}
