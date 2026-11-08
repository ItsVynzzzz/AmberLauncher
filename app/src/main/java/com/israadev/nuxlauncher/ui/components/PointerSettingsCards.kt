package com.israadev.nuxlauncher.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.control.PointerStyle
import com.israadev.nuxlauncher.ui.control.drawPointer
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.screens.AmberPillButton
import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.movtery.zalithlauncher.bridge.CursorShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Kartu pengaturan penunjuk (kursor) launcher, satu baris per bentuk kursor game: pratinjau, ganti gambar,
 * atur hotspot, dan kembalikan ke bawaan. Ukuran kursor diatur di slider "Ukuran kursor virtual".
 */
@Composable
fun PointerSettingsCards(
    hotspots: Map<String, Int>,
    onHotspotsChange: (Map<String, Int>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickShape by remember { mutableStateOf<CursorShape?>(null) }
    var openShape by remember { mutableStateOf<CursorShape?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val shape = pickShape
        pickShape = null
        if (uri != null && shape != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { PointerStyle.importImage(context, shape, uri) }
                if (ok) {
                    PointerStyle.bump()
                } else {
                    Toast.makeText(context, "Gambar tidak bisa dibaca", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    PointerStyle.shapes.forEachIndexed { index, shape ->
        if (index > 0) SettingDivider()

        val title = PointerStyle.title(shape)
        val summary = PointerStyle.summary(shape)
        val savedX = PointerStyle.hotspotX(hotspots, shape)
        val savedY = PointerStyle.hotspotY(hotspots, shape)
        // Nilai yang sedang digeser: dipakai pratinjau dan slider, baru ditulis ke pengaturan saat jari diangkat.
        var hotX by remember(shape, savedX) { mutableIntStateOf(savedX) }
        var hotY by remember(shape, savedY) { mutableIntStateOf(savedY) }
        val hasCustomImage = PointerStyle.bitmap(context, shape) != null
        val hasCustomHotspot = PointerStyle.hasHotspot(hotspots, shape)
        val editing = openShape == shape

        SettingFrame(title, summary) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PointerPreview(shape = shape, hotX = hotX, hotY = hotY, showHotspot = editing)
                    Spacer(modifier = Modifier.width(12.dp))
                    SettingRowText(title = title, desc = summary, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmberPillButton(
                        text = if (hasCustomImage) "Ganti gambar" else "Pilih gambar",
                        onClick = {
                            pickShape = shape
                            picker.launch("image/*")
                        },
                        filled = false
                    )
                    AmberPillButton(
                        text = if (editing) "Tutup hotspot" else "Atur hotspot",
                        onClick = { openShape = if (editing) null else shape },
                        filled = false
                    )
                    if (hasCustomImage || hasCustomHotspot) {
                        AmberPillButton(
                            text = "Reset",
                            onClick = {
                                if (hasCustomImage) PointerStyle.removeImage(context, shape)
                                if (hasCustomHotspot) onHotspotsChange(PointerStyle.withoutHotspot(hotspots, shape))
                            },
                            filled = false,
                            danger = true
                        )
                    }
                }
                if (editing) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Hotspot adalah titik yang dihitung sebagai ujung kursor saat mengklik (titik merah di pratinjau).",
                        color = AmberUi.TextDim,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    HotspotSlider(
                        label = "Hotspot X",
                        value = hotX,
                        onChange = { hotX = it },
                        onFinished = { onHotspotsChange(PointerStyle.withHotspot(hotspots, shape, hotX, hotY)) }
                    )
                    HotspotSlider(
                        label = "Hotspot Y",
                        value = hotY,
                        onChange = { hotY = it },
                        onFinished = { onHotspotsChange(PointerStyle.withHotspot(hotspots, shape, hotX, hotY)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HotspotSlider(label: String, value: Int, onChange: (Int) -> Unit, onFinished: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = AmberUi.TextDim,
            fontSize = 12.sp,
            modifier = Modifier.width(76.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onFinished,
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = AmberUi.AccentSoft,
                inactiveTrackColor = AmberUi.BorderStrong,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Text(
            text = "$value%",
            color = AmberUi.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp)
        )
    }
}

/** Pratinjau kecil kursor (gambar kustom atau bentuk bawaan); titik merah menandai hotspot saat diatur. */
@Composable
private fun PointerPreview(shape: CursorShape, hotX: Int, hotY: Int, showHotspot: Boolean) {
    val context = LocalContext.current
    val d = LocalDensity.current.density
    val custom = PointerStyle.bitmap(context, shape)
    val glyph = PointerStyle.glyphFor(shape, d)
    val hx = hotX / 100f
    val hy = hotY / 100f
    Canvas(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AmberUi.SurfaceRaised)
    ) {
        val sizePx = 32f * d
        translate(left = 8f * d, top = 8f * d) {
            drawPointer(glyph, custom, d, sizePx, hx, hy, false)
            if (showHotspot) {
                drawCircle(
                    color = Color(0xFFFF3B30),
                    radius = 2.5f * d,
                    center = Offset(hx * sizePx, hy * sizePx)
                )
            }
        }
    }
}
