package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes

/**
 * Bahan penyusun layar Pengaturan gaya baru: baris lega dengan judul + keterangan di kiri dan
 * kontrol di kanan, pemisah tipis, chip pilihan setinggi 38dp, dan tab samping.
 * Semua target sentuh minimal 44-48dp dan huruf terkecil 12sp supaya enak dipakai di HP.
 */

/** Garis pemisah tipis antar baris pengaturan. */
@Composable
fun SettingDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NuxColors.LightGray)
    )
}

/** Judul baris + keterangan kecil di bawahnya. */
@Composable
fun SettingRowText(
    title: String,
    desc: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = NuxColors.DarkGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        if (!desc.isNullOrBlank()) {
            Text(
                text = desc,
                color = NuxColors.GrayNeutral,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

/** Chip pilihan berbentuk pil. Terpilih = isian kuning dengan teks Ink. */
@Composable
fun SettingChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(if (selected) NuxColors.Yellow else Color.Transparent, shape)
            .border(
                NuxSizes.GlassBorder,
                if (selected) NuxColors.Yellow else NuxColors.CardBorder,
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) NuxColors.Ink else NuxColors.DarkGray,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Baris saklar: ketuk di mana saja pada baris untuk mengubah. */
@Composable
fun SettingSwitchRow(
    title: String,
    desc: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingRowText(title = title, desc = desc, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NuxColors.Ink,
                checkedTrackColor = NuxColors.Yellow,
                uncheckedThumbColor = NuxColors.GrayNeutral,
                uncheckedTrackColor = NuxColors.SurfaceElevated
            )
        )
    }
}

/** Baris pilihan: judul + keterangan, lalu deretan chip (bisa digeser bila kepanjangan). */
@Composable
fun <T> SettingChoiceRow(
    title: String,
    desc: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
    ) {
        SettingRowText(title = title, desc = desc)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                SettingChip(
                    text = label,
                    selected = value == selected,
                    onClick = { onSelect(value) }
                )
            }
        }
    }
}

/** Baris slider: judul di kiri, nilai di kanan (kuning, merah bila berlebihan), slider di bawah. */
@Composable
fun SettingSliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    desc: String? = null,
    warn: Boolean = false,
    footer: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingRowText(title = title, desc = desc, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = valueText,
                color = if (warn) NuxColors.ErrorRed else NuxColors.Yellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = NuxColors.Yellow,
                activeTrackColor = NuxColors.Yellow,
                inactiveTrackColor = NuxColors.CardBorder,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        if (footer != null) {
            footer()
        }
    }
}

/** Tab samping (ikon + nama). Terpilih = tint kuning dengan tepi kuning tipis. */
@Composable
fun SettingTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(if (selected) NuxColors.YellowPale else Color.Transparent, shape)
            .border(
                NuxSizes.GlassBorder,
                if (selected) NuxColors.Yellow.copy(alpha = 0.45f) else Color.Transparent,
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) NuxColors.Yellow else NuxColors.GrayNeutral,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (selected) NuxColors.Yellow else NuxColors.DarkGray,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Tombol ikon bulat 44dp (tutup, hapus, dll). ring = lingkaran kaca di belakang ikon. */
@Composable
fun NuxIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NuxColors.GrayNeutral,
    ring: Boolean = true,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (ring) {
                    Modifier
                        .background(NuxColors.SurfaceElevated, CircleShape)
                        .border(NuxSizes.GlassBorder, NuxColors.CardBorder, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Baris pilihan satu-dari-banyak dalam dialog (daftar versi, loader). Terpilih = tint kuning + centang. */
@Composable
fun NuxPickRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 42.dp)
            .clip(shape)
            .background(if (selected) NuxColors.YellowPale else NuxColors.SurfaceWhite, shape)
            .border(
                NuxSizes.GlassBorder,
                if (selected) NuxColors.Yellow.copy(alpha = 0.55f) else NuxColors.CardBorder,
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) NuxColors.Yellow else NuxColors.DarkGray,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 2,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = NuxColors.Yellow,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
