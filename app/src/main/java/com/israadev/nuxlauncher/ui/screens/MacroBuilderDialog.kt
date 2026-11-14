package com.israadev.nuxlauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.controls.KeycodeCatalog
import com.israadev.nuxlauncher.core.controls.MacroScript
import com.israadev.nuxlauncher.core.controls.MacroStep
import com.israadev.nuxlauncher.core.controls.MacroTemplate
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import com.israadev.nuxlauncher.core.controls.models.MacroRunMode
import com.israadev.nuxlauncher.ui.components.AmberActionButton
import com.israadev.nuxlauncher.ui.components.AmberChoicePill
import com.israadev.nuxlauncher.ui.components.AmberField
import com.israadev.nuxlauncher.ui.components.AmberPage
import com.israadev.nuxlauncher.ui.components.AmberPageLabel
import com.israadev.nuxlauncher.ui.components.AmberPanel
import com.israadev.nuxlauncher.ui.components.AmberTag
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.israadev.nuxlauncher.ui.theme.amberGlass
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode

// Tangga nilai untuk tombol - dan +: tiap ketukan loncat ke nilai "bulat" berikutnya (milidetik).
private val WAIT_LADDER = listOf(
    10, 20, 50, 100, 150, 200, 250, 300, 400, 500, 600, 750,
    1000, 1250, 1500, 2000, 2500, 3000, 4000, 5000, 7500, 10000
)
private val HOLD_LADDER = listOf(
    20, 50, 100, 200, 300, 500, 750, 1000, 1500, 2000, 3000, 5000, 7500, 10000, 15000, 20000, 30000
)
private val LOOP_LADDER = listOf(0, 50, 100, 200, 300, 500, 750, 1000, 1500, 2000, 3000, 5000, 10000)

// Pilihan cepat di bawah tombol - dan +.
private val WAIT_PRESETS = listOf(100, 200, 500, 1000, 2000)
private val HOLD_PRESETS = listOf(200, 500, 1000, 2000, 5000)
private val LOOP_PRESETS = listOf(0, 100, 300, 500, 1000, 2000)
private val REPEAT_PRESETS = listOf(1, 2, 3, 5, 10, 20)

private const val JITTER_STEP = 5

private fun mbNext(ladder: List<Int>, v: Int): Int = ladder.firstOrNull { it > v } ?: ladder.last()

private fun mbPrev(ladder: List<Int>, v: Int): Int = ladder.lastOrNull { it < v } ?: ladder.first()

/** Satu kotak "tambah aksi". */
private class MbTile(
    val emoji: String,
    val title: String,
    val hint: String,
    val onClick: () -> Unit
)

/** Teks pendek untuk satu aksi di daftar: (jenis aksi, tombol/rincian). Keduanya kunci terjemahan. */
private fun mbDescribe(step: MacroStep): Pair<String, String> = when (step) {
    is MacroStep.Tap -> "Tekan" to KeycodeCatalog.getKeyName(step.key)
    is MacroStep.KeyDown -> "Tahan Terus" to KeycodeCatalog.getKeyName(step.key)
    is MacroStep.KeyUp -> "Lepas" to KeycodeCatalog.getKeyName(step.key)
    is MacroStep.KeyHold -> "Tahan" to KeycodeCatalog.getKeyName(step.key)
    is MacroStep.Click -> "Klik" to MacroScript.mouseName(step.button)
    is MacroStep.MouseDown -> "Tahan Terus" to MacroScript.mouseName(step.button)
    is MacroStep.MouseUp -> "Lepas" to MacroScript.mouseName(step.button)
    is MacroStep.MouseHold -> "Tahan" to MacroScript.mouseName(step.button)
    is MacroStep.Scroll -> "Pindah slot hotbar" to (if (step.direction > 0) "Slot sebelumnya" else "Slot berikutnya")
    is MacroStep.Wait -> "Tunggu" to "${macroSeconds(step.ms)}s"
    is MacroStep.Type -> "Ketik di chat" to step.text.ifBlank { "(kosong)" }
}

private fun mbModeTitle(mode: String): String = when (mode) {
    MacroRunMode.HOLD -> "Selama Ditahan"
    MacroRunMode.TOGGLE -> "Nyala / Mati"
    else -> "Sekali Jalan"
}

/**
 * Layar penuh untuk menyusun makro "Urutan Aksi" dengan bahasa sehari-hari: 1) tambah aksi,
 * 2) pilih cara jalan, atau mulai dari contoh siap pakai. Semua perubahan langsung dikirim lewat
 * [onChange] (tidak ada tombol simpan); "Selesai" hanya menutup layar.
 *
 * [onPickKey] membuka pemilih tombol untuk aksi berjenis TAP / HOLD / DOWN / UP; hasil pilihannya
 * dimasukkan oleh pemanggil lewat [MacroScript.stepFromPick].
 */
@Composable
fun MacroBuilderDialog(
    button: CustomControlButton,
    onChange: (CustomControlButton) -> Unit,
    onPickKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = button.macroSteps
    val parsed = remember(tokens) { tokens.map { MacroScript.parse(it) } }
    val estimateMs = remember(tokens) { MacroScript.estimateMs(parsed.filterNotNull()) }
    val mode = if (MacroRunMode.isValid(button.macroRunMode)) button.macroRunMode else MacroRunMode.ONCE
    val repeat = button.macroRepeat.coerceIn(1, MacroScript.MAX_REPEAT)

    var tab by remember { mutableIntStateOf(0) }
    val stepsScroll = rememberScrollState()
    val sideScroll = rememberScrollState()
    var prevCount by remember { mutableIntStateOf(tokens.size) }

    // Aksi baru selalu masuk di bawah daftar: gulir ke bawah supaya langsung kelihatan.
    LaunchedEffect(tokens.size) {
        if (tokens.size > prevCount) {
            withFrameNanos { }
            stepsScroll.animateScrollTo(stepsScroll.maxValue)
        }
        prevCount = tokens.size
    }

    fun showLimit() {
        Toast.makeText(context, "Maksimal ${MacroScript.MAX_STEPS} aksi", Toast.LENGTH_SHORT).show()
    }

    fun setTokens(list: List<String>) {
        onChange(button.copy(macroSteps = list))
    }

    fun addToken(token: String) {
        if (tokens.size >= MacroScript.MAX_STEPS) showLimit() else setTokens(tokens + token)
    }

    fun pick(kind: String) {
        if (tokens.size >= MacroScript.MAX_STEPS) showLimit() else onPickKey(kind)
    }

    AmberPage(
        onDismiss = onDismiss,
        title = "Pembuat Makro",
        icon = Icons.Outlined.Tune,
        subtitle = "Susun aksi, lalu pilih kapan makro dijalankan",
        actions = { _ ->
            AmberActionButton(
                text = "Selesai",
                onClick = onDismiss,
                icon = Icons.Outlined.Check,
                height = 40.dp
            )
        }
    ) { info ->
        val narrow = info.narrow

        fun applyTemplate(t: MacroTemplate) {
            onChange(
                button.copy(
                    macroType = "SEQUENCE",
                    macroSteps = t.steps,
                    macroRunMode = t.runMode,
                    macroRepeat = t.repeat,
                    macroLoopDelayMs = t.loopDelayMs
                )
            )
            Toast.makeText(context, "Contoh dipakai", Toast.LENGTH_SHORT).show()
            if (narrow) tab = 0
        }

        Column(modifier = Modifier.fillMaxSize()) {
            MbSummary(
                count = tokens.size,
                estimateMs = estimateMs,
                mode = mode,
                repeat = repeat,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp)
            )

            if (narrow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Aksi", "Cara Jalan", "Contoh").forEachIndexed { index, label ->
                        AmberChoicePill(
                            text = label,
                            selected = tab == index,
                            onClick = { tab = index },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(stepsScroll)
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (tab) {
                        0 -> MbStepsSection(
                            tokens = tokens,
                            parsed = parsed,
                            columns = 2,
                            onSetTokens = { setTokens(it) },
                            onAdd = { addToken(it) },
                            onPick = { pick(it) },
                            onShowExamples = { tab = 2 }
                        )

                        1 -> MbRunSection(button = button, onChange = onChange)
                        else -> MbExamplesSection(
                            hasSteps = tokens.isNotEmpty(),
                            onApply = { applyTemplate(it) }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .verticalScroll(stepsScroll)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        MbStepsSection(
                            tokens = tokens,
                            parsed = parsed,
                            columns = 3,
                            onSetTokens = { setTokens(it) },
                            onAdd = { addToken(it) },
                            onPick = { pick(it) },
                            onShowExamples = null
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(sideScroll)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        MbRunSection(button = button, onChange = onChange)
                        MbExamplesSection(
                            hasSteps = tokens.isNotEmpty(),
                            onApply = { applyTemplate(it) }
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Ringkasan
// =============================================================================

/** Satu kartu ringkas: berapa aksi, kira-kira berapa lama, dan kapan makro berjalan. */
@Composable
private fun MbSummary(
    count: Int,
    estimateMs: Long,
    mode: String,
    repeat: Int,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .amberGlass(shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = if (count == 0) {
                "Belum ada aksi. Mulai dari contoh atau tambah aksi sendiri."
            } else {
                "$count aksi · sekitar ${macroSeconds(estimateMs.toInt())} detik per putaran"
            },
            color = AmberUi.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = macroRunSentence(mode, repeat),
            color = AmberUi.TextDim,
            fontSize = 12.sp
        )
    }
}

// =============================================================================
// Bagian 1: daftar aksi + kotak tambah aksi
// =============================================================================

@Composable
private fun MbSectionTitle(
    number: String?,
    title: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (number != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(AmberUi.Accent, CircleShape)
                    .border(1.dp, AmberUi.AccentEdge, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    color = AmberUi.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column {
            Text(
                text = title,
                color = AmberUi.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = hint, color = AmberUi.TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MbStepsSection(
    tokens: List<String>,
    parsed: List<MacroStep?>,
    columns: Int,
    onSetTokens: (List<String>) -> Unit,
    onAdd: (String) -> Unit,
    onPick: (String) -> Unit,
    onShowExamples: (() -> Unit)?
) {
    var showMore by remember { mutableStateOf(false) }
    var showSlots by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MbSectionTitle(
            number = "1",
            title = "Susun aksi",
            hint = "Aksi dijalankan berurutan dari atas ke bawah.",
            modifier = Modifier.weight(1f)
        )
        if (tokens.isNotEmpty()) {
            if (confirmClear) {
                Text(
                    text = "Batal",
                    color = AmberUi.TextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { confirmClear = false }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
                Text(
                    text = "Yakin hapus?",
                    color = AmberUi.Danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            confirmClear = false
                            onSetTokens(emptyList())
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            } else {
                Text(
                    text = "Hapus Semua",
                    color = AmberUi.Danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { confirmClear = true }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (tokens.isEmpty()) {
        MbEmptyState(onShowExamples = onShowExamples)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tokens.forEachIndexed { index, token ->
                MbStepCard(
                    index = index,
                    count = tokens.size,
                    token = token,
                    step = parsed.getOrNull(index),
                    onMove = { delta ->
                        val target = index + delta
                        if (target in tokens.indices) {
                            val moved = tokens.toMutableList()
                            val item = moved.removeAt(index)
                            moved.add(target, item)
                            onSetTokens(moved)
                        }
                    },
                    onDelete = {
                        val rest = tokens.toMutableList()
                        rest.removeAt(index)
                        onSetTokens(rest)
                    },
                    onChangeStep = { newStep ->
                        val edited = tokens.toMutableList()
                        edited[index] = MacroScript.toToken(newStep)
                        onSetTokens(edited)
                    }
                )
            }
        }
    }

    // ---- Tambah aksi ----
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AmberPageLabel("Tambah aksi baru")

        val primary = listOf(
            MbTile("👆", "Tekan / Klik", "Tombol keyboard atau klik mouse") { onPick("TAP") },
            MbTile("✋", "Tahan", "Tekan dan tahan beberapa detik") { onPick("HOLD") },
            MbTile("⏱️", "Tunggu", "Jeda sebelum aksi berikutnya") {
                onAdd("WAIT:${MacroScript.DEFAULT_WAIT_MS}")
            },
            MbTile("🎒", "Pindah Slot", "Ganti item yang dipegang") { showSlots = !showSlots },
            MbTile("💬", "Ketik Chat", "Kirim pesan atau perintah") { onAdd("TYPE:") }
        )
        val more = listOf(
            MbTile("📌", "Tahan Terus", "Tahan sampai dilepas atau makro berhenti") { onPick("DOWN") },
            MbTile("🔓", "Lepas", "Lepas tombol yang sedang ditahan") { onPick("UP") },
            MbTile("🔄", "Geser Slot", "Ke slot sebelum atau sesudahnya") { onAdd("SCROLL:1") }
        )
        val toggle = MbTile(
            "⋯",
            if (showMore) "Sembunyikan" else "Lainnya",
            if (showMore) "Tutup aksi lanjutan" else "Aksi lanjutan"
        ) { showMore = !showMore }
        val tiles = primary + (if (showMore) more else emptyList()) + toggle

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(columns).forEach { rowTiles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTiles.forEach { tile -> MbTileCard(tile, Modifier.weight(1f)) }
                    repeat(columns - rowTiles.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }

    if (showSlots) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Pilih nomor slot hotbar yang dituju",
                color = AmberUi.TextDim,
                fontSize = 12.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (slot in 1..9) {
                    MbSlotPill(slot.toString(), Modifier.weight(1f)) {
                        onAdd("TAP:${LwjglGlfwKeycode.GLFW_KEY_1 + slot - 1}")
                    }
                }
            }
        }
    }
}

@Composable
private fun MbEmptyState(onShowExamples: (() -> Unit)?) {
    AmberPanel(padding = 16.dp) {
        Text(
            text = "Belum ada aksi",
            color = AmberUi.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tambahkan aksi lewat kotak di bawah, atau mulai cepat dari contoh siap pakai.",
            color = AmberUi.TextDim,
            fontSize = 13.sp
        )
        if (onShowExamples != null) {
            Spacer(modifier = Modifier.height(12.dp))
            AmberActionButton(
                text = "Lihat contoh siap pakai",
                onClick = onShowExamples,
                filled = false,
                height = 40.dp
            )
        }
    }
}

@Composable
private fun MbTileCard(tile: MbTile, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .height(104.dp)
            .clip(shape)
            .amberGlass(shape)
            .clickable { tile.onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = tile.emoji, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = tile.title,
            color = AmberUi.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = tile.hint,
            color = AmberUi.TextDim,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MbSlotPill(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .amberGlass(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = AmberUi.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Satu aksi: nomor, jenis + tombol, panah urut, hapus; di bawahnya pengaturan langsung (lama, teks). */
@Composable
private fun MbStepCard(
    index: Int,
    count: Int,
    token: String,
    step: MacroStep?,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onChangeStep: (MacroStep) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AmberUi.SurfaceRaised, shape)
            .border(1.dp, AmberUi.Border, shape)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(AmberUi.Accent.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = AmberUi.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (step != null) {
                val (caption, detail) = mbDescribe(step)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = caption,
                        color = AmberUi.TextDim,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Text(
                        text = detail,
                        color = AmberUi.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aksi tidak dikenal",
                        color = AmberUi.Danger,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Text(
                        text = token,
                        color = AmberUi.TextDim,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            MbIconButton(Icons.Filled.KeyboardArrowUp, "Naik", enabled = index > 0) { onMove(-1) }
            MbIconButton(Icons.Filled.KeyboardArrowDown, "Turun", enabled = index < count - 1) { onMove(1) }
            MbIconButton(Icons.Outlined.Delete, "Hapus", danger = true) { onDelete() }
        }
        if (step != null && mbHasEditor(step)) {
            Box(modifier = Modifier.padding(end = 6.dp)) {
                MbStepEditor(step = step, onChangeStep = onChangeStep)
            }
        }
    }
}

@Composable
private fun MbIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                danger -> AmberUi.Danger
                enabled -> AmberUi.Text
                else -> AmberUi.Border
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Aksi yang punya pengaturan langsung (lama, arah, teks). */
private fun mbHasEditor(step: MacroStep): Boolean =
    step is MacroStep.Wait || step is MacroStep.KeyHold || step is MacroStep.MouseHold ||
        step is MacroStep.Scroll || step is MacroStep.Type

/** Pengaturan langsung di bawah satu aksi. Aksi sederhana (tekan, klik, lepas) tidak punya. */
@Composable
private fun MbStepEditor(step: MacroStep, onChangeStep: (MacroStep) -> Unit) {
    when (step) {
        is MacroStep.Wait -> MbTimeEditor(
            label = "Berapa lama menunggu?",
            ms = step.ms,
            ladder = WAIT_LADDER,
            presets = WAIT_PRESETS
        ) { onChangeStep(MacroStep.Wait(it)) }

        is MacroStep.KeyHold -> MbTimeEditor(
            label = "Berapa lama ditahan?",
            ms = step.ms,
            ladder = HOLD_LADDER,
            presets = HOLD_PRESETS
        ) { onChangeStep(step.copy(ms = it)) }

        is MacroStep.MouseHold -> MbTimeEditor(
            label = "Berapa lama ditahan?",
            ms = step.ms,
            ladder = HOLD_LADDER,
            presets = HOLD_PRESETS
        ) { onChangeStep(step.copy(ms = it)) }

        is MacroStep.Scroll -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AmberChoicePill(
                text = "Slot sebelumnya",
                selected = step.direction > 0,
                onClick = { onChangeStep(MacroStep.Scroll(1)) },
                modifier = Modifier.weight(1f)
            )
            AmberChoicePill(
                text = "Slot berikutnya",
                selected = step.direction < 0,
                onClick = { onChangeStep(MacroStep.Scroll(-1)) },
                modifier = Modifier.weight(1f)
            )
        }

        is MacroStep.Type -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AmberField(
                value = step.text,
                onValueChange = { v ->
                    onChangeStep(MacroStep.Type(v.filter { c -> c >= ' ' }.take(MacroScript.MAX_TEXT)))
                },
                placeholder = "Misal: /gamemode creative atau /home"
            )
            Text(
                text = "Diawali / untuk perintah, tanpa / untuk pesan chat biasa.",
                color = AmberUi.TextDim,
                fontSize = 12.sp
            )
        }

        else -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MbTimeEditor(
    label: String,
    ms: Int,
    ladder: List<Int>,
    presets: List<Int>,
    onChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, color = AmberUi.TextDim, fontSize = 12.sp)
        MbStepper(
            valueText = macroSeconds(ms),
            unit = "detik",
            canDown = ms > ladder.first(),
            canUp = ms < ladder.last(),
            onDown = { onChange(mbPrev(ladder, ms)) },
            onUp = { onChange(mbNext(ladder, ms)) }
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { p ->
                AmberChoicePill(
                    text = "${macroSeconds(p)}s",
                    selected = ms == p,
                    onClick = { onChange(p) }
                )
            }
        }
    }
}

/** Tombol - [angka] + besar yang mudah diketuk. */
@Composable
private fun MbStepper(
    valueText: String,
    unit: String?,
    canDown: Boolean,
    canUp: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MbStepButton("−", canDown, onDown)
        Column(
            modifier = Modifier.widthIn(min = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = valueText,
                color = AmberUi.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (unit != null) {
                Text(text = unit, color = AmberUi.TextDim, fontSize = 12.sp, maxLines = 1)
            }
        }
        MbStepButton("+", canUp, onUp)
    }
}

@Composable
private fun MbStepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.35f)
            .size(44.dp)
            .clip(CircleShape)
            .amberGlass(CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = AmberUi.Text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// =============================================================================
// Bagian 2: cara jalan
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MbRunSection(
    button: CustomControlButton,
    onChange: (CustomControlButton) -> Unit
) {
    val mode = if (MacroRunMode.isValid(button.macroRunMode)) button.macroRunMode else MacroRunMode.ONCE
    val repeat = button.macroRepeat.coerceIn(1, MacroScript.MAX_REPEAT)
    val loopDelay = button.macroLoopDelayMs.coerceIn(0, MacroScript.MAX_LOOP_DELAY_MS)
    val jitter = button.macroJitterPercent.coerceIn(0, MacroScript.MAX_JITTER)
    var advanced by remember { mutableStateOf(jitter > 0) }

    MbSectionTitle(
        number = "2",
        title = "Kapan makro berjalan?",
        hint = "Pilih cara tombol ini dipakai."
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MbRunCard(
            emoji = "▶️",
            title = mbModeTitle(MacroRunMode.ONCE),
            description = "Jalan sekali setiap ketuk. Ketuk lagi saat berjalan untuk menghentikan.",
            selected = mode == MacroRunMode.ONCE,
            onClick = { onChange(button.copy(macroRunMode = MacroRunMode.ONCE)) }
        )
        MbRunCard(
            emoji = "👇",
            title = mbModeTitle(MacroRunMode.HOLD),
            description = "Diulang terus selama tombol ditahan.",
            selected = mode == MacroRunMode.HOLD,
            onClick = { onChange(button.copy(macroRunMode = MacroRunMode.HOLD)) }
        )
        MbRunCard(
            emoji = "🔁",
            title = mbModeTitle(MacroRunMode.TOGGLE),
            description = "Diulang terus sampai tombol diketuk lagi.",
            selected = mode == MacroRunMode.TOGGLE,
            onClick = { onChange(button.copy(macroRunMode = MacroRunMode.TOGGLE)) }
        )
    }

    if (mode == MacroRunMode.ONCE) {
        MbSettingCard(
            title = "Diulang berapa kali?",
            hint = "Berapa kali urutan dijalankan setiap ketukan."
        ) {
            MbStepper(
                valueText = repeat.toString(),
                unit = "kali",
                canDown = repeat > 1,
                canUp = repeat < MacroScript.MAX_REPEAT,
                onDown = { onChange(button.copy(macroRepeat = repeat - 1)) },
                onUp = { onChange(button.copy(macroRepeat = repeat + 1)) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                REPEAT_PRESETS.forEach { p ->
                    AmberChoicePill(
                        text = "${p}x",
                        selected = repeat == p,
                        onClick = { onChange(button.copy(macroRepeat = p)) }
                    )
                }
            }
        }
    }

    if (mode != MacroRunMode.ONCE || repeat > 1) {
        MbSettingCard(
            title = "Jeda antar putaran",
            hint = "Waktu istirahat sebelum urutan diulang lagi."
        ) {
            MbStepper(
                valueText = if (loopDelay == 0) "Tanpa jeda" else macroSeconds(loopDelay),
                unit = if (loopDelay == 0) null else "detik",
                canDown = loopDelay > LOOP_LADDER.first(),
                canUp = loopDelay < LOOP_LADDER.last(),
                onDown = { onChange(button.copy(macroLoopDelayMs = mbPrev(LOOP_LADDER, loopDelay))) },
                onUp = { onChange(button.copy(macroLoopDelayMs = mbNext(LOOP_LADDER, loopDelay))) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LOOP_PRESETS.forEach { p ->
                    AmberChoicePill(
                        text = "${macroSeconds(p)}s",
                        selected = loopDelay == p,
                        onClick = { onChange(button.copy(macroLoopDelayMs = p)) }
                    )
                }
            }
        }
    }

    // ---- Lanjutan (tersembunyi supaya layar tidak ramai) ----
    MbAdvancedHeader(open = advanced) { advanced = !advanced }
    if (advanced) {
        MbSettingCard(
            title = "Waktu acak",
            hint = "Geser waktu tunggu dan tahan sedikit secara acak supaya terasa lebih alami."
        ) {
            MbStepper(
                valueText = if (jitter == 0) "Mati" else "±$jitter%",
                unit = null,
                canDown = jitter > 0,
                canUp = jitter < MacroScript.MAX_JITTER,
                onDown = {
                    onChange(button.copy(macroJitterPercent = (jitter - JITTER_STEP).coerceAtLeast(0)))
                },
                onUp = {
                    onChange(
                        button.copy(
                            macroJitterPercent = (jitter + JITTER_STEP).coerceAtMost(MacroScript.MAX_JITTER)
                        )
                    )
                }
            )
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Tombol yang masih tertahan otomatis dilepas saat makro berhenti.",
            color = AmberUi.TextDim,
            fontSize = 12.sp
        )
        Text(
            text = "Catatan: sebagian server melarang makro. Gunakan dengan bijak.",
            color = AmberUi.TextDim,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MbRunCard(
    emoji: String,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .background(AmberUi.Accent.copy(alpha = 0.22f), shape)
                        .border(1.5.dp, AmberUi.AccentEdge, shape)
                } else {
                    Modifier
                        .background(AmberUi.SurfaceRaised, shape)
                        .border(1.dp, AmberUi.Border, shape)
                }
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 24.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AmberUi.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = description, color = AmberUi.TextDim, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, if (selected) AmberUi.AccentEdge else AmberUi.BorderStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(AmberUi.AccentEdge, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun MbSettingCard(
    title: String,
    hint: String,
    content: @Composable ColumnScope.() -> Unit
) {
    AmberPanel(padding = 14.dp) {
        Text(
            text = title,
            color = AmberUi.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(text = hint, color = AmberUi.TextDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun MbAdvancedHeader(open: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Pengaturan lanjutan",
            color = AmberUi.TextDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = AmberUi.TextDim,
            modifier = Modifier
                .size(22.dp)
                .rotate(if (open) 180f else 0f)
        )
    }
}

// =============================================================================
// Contoh siap pakai
// =============================================================================

@Composable
private fun MbExamplesSection(hasSteps: Boolean, onApply: (MacroTemplate) -> Unit) {
    var pending by remember { mutableIntStateOf(-1) }

    MbSectionTitle(
        number = null,
        title = "Contoh siap pakai",
        hint = "Ketuk contoh untuk memakainya, lalu ubah sesukamu."
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MacroScript.TEMPLATES.forEachIndexed { index, template ->
            MbExampleCard(
                template = template,
                confirming = pending == index,
                onClick = {
                    if (hasSteps) {
                        pending = index
                    } else {
                        onApply(template)
                    }
                },
                onConfirm = {
                    pending = -1
                    onApply(template)
                },
                onCancel = { pending = -1 }
            )
        }
    }
}

@Composable
private fun MbExampleCard(
    template: MacroTemplate,
    confirming: Boolean,
    onClick: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .amberGlass(shape)
            .clickable { onClick() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(AmberUi.SurfaceRaised, CircleShape)
                    .border(1.dp, AmberUi.Border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = template.emoji, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = template.title,
                    color = AmberUi.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (template.description.isNotEmpty()) {
                    Text(text = template.description, color = AmberUi.TextDim, fontSize = 12.sp)
                }
                AmberTag(text = mbModeTitle(template.runMode))
            }
        }
        if (confirming) {
            Text(
                text = "Mengganti urutan yang sekarang. Lanjutkan?",
                color = AmberUi.Text,
                fontSize = 13.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AmberActionButton(
                    text = "Ya, ganti",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    height = 40.dp
                )
                AmberActionButton(
                    text = "Batal",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    filled = false,
                    height = 40.dp
                )
            }
        }
    }
}
