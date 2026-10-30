package com.israadev.nuxlauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.components.NuxDialog
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.AmberGlassKind
import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.amberGlass

// =============================================================================
// KOMPONEN KACA BERSAMA untuk layar Mod, Kelola konten, Akun, dan Pengaturan
// =============================================================================

/** Tombol ikon bulat di dalam pil navigasi. */
@Composable
internal fun AmberRoundIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = AmberUi.Text,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Pil navigasi kiri atas yang sama di layar Mod, Akun, dan Pengaturan: panah kembali ke Home,
 * lalu tiga tujuan (Mod, Akun, Pengaturan). Tujuan yang sedang dibuka tampil sebagai chip
 * berlabel, tujuan lain berupa ikon yang bisa diketuk.
 * [active] salah satu dari "mods", "accounts", "settings".
 */
@Composable
internal fun AmberNavPill(
    active: String,
    activeLabel: String,
    onBack: () -> Unit,
    onMods: () -> Unit,
    onAccounts: () -> Unit,
    onSettings: () -> Unit,
    activeIcon: ImageVector? = null
) {
    val items = listOf(
        Triple("mods", Icons.Outlined.FileDownload, onMods),
        Triple("accounts", Icons.Outlined.AccountCircle, onAccounts),
        Triple("settings", Icons.Outlined.Tune, onSettings)
    )
    val descriptions = mapOf("mods" to "Mod", "accounts" to "Akun", "settings" to "Pengaturan")
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .amberGlass(CircleShape)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AmberRoundIcon(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            description = "Kembali ke Home",
            onClick = onBack
        )
        items.forEach { (id, icon, onClick) ->
            if (id == active) {
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(AmberUi.SurfaceRaised, CircleShape)
                        .border(1.dp, AmberUi.BorderStrong, CircleShape)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = activeIcon ?: icon,
                        contentDescription = null,
                        tint = AmberUi.Text,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activeLabel,
                        color = AmberUi.Text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                }
            } else {
                AmberRoundIcon(
                    icon = icon,
                    description = descriptions[id] ?: id,
                    onClick = onClick
                )
            }
        }
    }
}

/** Kartu panjang berkaca: satu kelompok baris pengaturan atau satu blok isi. */
@Composable
internal fun AmberCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .amberGlass(shape)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        content = content
    )
}

/** Judul kecil kelompok pengaturan di atas kartu (mis. Memori, Grafis). */
@Composable
internal fun AmberSectionLabel(text: String) {
    Text(
        text = text,
        color = AmberUi.TextDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        maxLines = 1,
        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 8.dp)
    )
}

/** Kolom cari berbentuk pil di kanan atas. */
@Composable
internal fun AmberSearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .amberGlass(CircleShape)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = AmberUi.Text,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = AmberUi.TextDim,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = AmberUi.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(AmberUi.AccentSoft),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Hapus teks",
                    tint = AmberUi.TextDim,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}


/** Menu jenis addon di sisi kiri (Modpack, Mod, Shader, Resource Pack, Datapack). */
@Composable
internal fun AmberSideItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier
                        .background(AmberUi.Accent, CircleShape)
                        .border(1.dp, AmberUi.AccentEdge, CircleShape)
                } else {
                    Modifier.amberGlass(CircleShape)
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else AmberUi.AccentSoft,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = AmberUi.Text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


/** Pil filter: label redup + nilai tebal + panah, mis. "Kategori Semua ⌄". */
@Composable
internal fun AmberFilterPill(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .amberGlass(CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AmberUi.TextDim,
            fontSize = 13.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            color = AmberUi.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp)
        )
        if (showChevron) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = AmberUi.TextDim,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


/** Tombol pil: terisi biru (aksi utama) atau hanya garis tepi (aksi biasa). */
@Composable
internal fun AmberPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    filled: Boolean = true,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .height(40.dp)
            .clip(CircleShape)
            .background(if (filled) AmberUi.Accent else Color.Transparent, CircleShape)
            .border(1.dp, if (danger) AmberUi.Danger.copy(alpha = 0.6f) else if (filled) AmberUi.AccentEdge else AmberUi.BorderStrong, CircleShape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (danger) AmberUi.Danger else AmberUi.Text,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = if (danger) AmberUi.Danger else AmberUi.Text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}


/** Satu baris pilihan di dalam dialog pemilih. Terpilih = isian biru + centang. */
@Composable
internal fun AmberPickRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 42.dp)
            .clip(shape)
            .background(if (selected) AmberUi.Accent else NuxColors.SurfaceWhite, shape)
            .border(1.dp, if (selected) AmberUi.AccentEdge else NuxColors.CardBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AmberUi.Text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 2,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


/** Daftar pilihan dalam dialog kaca (pengganti menu tarik-turun): instance dan world. */
@Composable
internal fun AmberPickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    NuxDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.55f)
            .wrapContentHeight(),
        compact = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = NuxColors.DarkGray
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                options.forEach { (id, label) ->
                    AmberPickRow(
                        label = label,
                        selected = id == selectedId,
                        onClick = { onPick(id) }
                    )
                }
            }
        }
    }
}

