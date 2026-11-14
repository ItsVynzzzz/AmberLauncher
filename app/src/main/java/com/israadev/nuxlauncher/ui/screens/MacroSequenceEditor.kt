package com.israadev.nuxlauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.controls.MacroScript
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import com.israadev.nuxlauncher.core.controls.models.MacroRunMode
import com.israadev.nuxlauncher.ui.i18n.Text
import java.util.Locale
import kotlin.math.roundToInt

// Warna editor tombol (gaya gelap-kuning yang sama dengan bagian makro lain di CustomGuiEditorScreen).
private val Gold = Color(0xFFFFD60A)
private val GoldSoft = Color(0xFFFFE566)
private val Cream = Color(0xFFE9E4CC)
private val PanelDark = Color(0xFF121212)
private val LineDark = Color(0xFF313131)

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

/** Waktu dalam detik untuk tampilan: 1, 0.5, 0.05 (angka bulat tanpa desimal, selain itu 1-2 desimal). */
internal fun macroSeconds(ms: Int): String = when {
    ms % 1000 == 0 -> (ms / 1000).toString()
    ms % 100 == 0 -> String.format(Locale.US, "%.1f", ms / 1000.0)
    else -> String.format(Locale.US, "%.2f", ms / 1000.0)
}

/** Kalimat sehari-hari tentang kapan makro berjalan. Dipakai kartu ringkas dan Pembuat Makro. */
internal fun macroRunSentence(mode: String, repeat: Int): String = when (mode) {
    MacroRunMode.HOLD -> "Diulang terus selama tombol ditahan."
    MacroRunMode.TOGGLE -> "Diulang terus sampai tombol diketuk lagi."
    else -> if (repeat > 1) {
        "Diulang $repeat kali setiap ketuk. Ketuk lagi saat berjalan untuk menghentikan."
    } else {
        "Jalan sekali setiap ketuk. Ketuk lagi saat berjalan untuk menghentikan."
    }
}

/**
 * Kartu ringkas "Urutan Aksi" di panel editor tombol: berapa aksi, kira-kira berapa lama, kapan
 * berjalan, dan satu tombol besar yang membuka [MacroBuilderDialog] (tempat menyusun aksinya).
 */
@Composable
fun MacroSequenceSummary(
    button: CustomControlButton,
    onOpen: () -> Unit
) {
    val tokens = button.macroSteps
    val estimateMs = remember(tokens) { MacroScript.estimateMs(MacroScript.parseAll(tokens)) }
    val mode = if (MacroRunMode.isValid(button.macroRunMode)) button.macroRunMode else MacroRunMode.ONCE
    val repeat = button.macroRepeat.coerceIn(1, MacroScript.MAX_REPEAT)
    val shape = RoundedCornerShape(6.dp)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelDark, shape)
                .border(1.dp, LineDark, shape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = if (tokens.isEmpty()) {
                    "Belum ada aksi"
                } else {
                    "${tokens.size} aksi \u00b7 sekitar ${macroSeconds(estimateMs.toInt())} detik per putaran"
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = GoldSoft
            )
            Text(
                text = macroRunSentence(mode, repeat),
                fontSize = 8.sp,
                color = Cream
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Gold, RoundedCornerShape(8.dp))
                .clickable { onOpen() }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (tokens.isEmpty()) "Buat Urutan Aksi" else "Atur Urutan Aksi",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF111111),
                maxLines = 1
            )
        }
        Text(
            text = "Catatan: sebagian server melarang makro. Gunakan dengan bijak.",
            fontSize = 8.sp,
            color = Cream
        )
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
