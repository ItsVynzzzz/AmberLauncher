package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Tr
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.controls.KeyOption
import com.israadev.nuxlauncher.core.controls.KeycodeCatalog
import com.israadev.nuxlauncher.ui.components.NuxDialog
import com.israadev.nuxlauncher.ui.components.NuxIconButton
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes

/** Urutan kategori di pemilih: yang paling sering dipakai di depan, yang jarang di belakang. */
private val CATEGORY_ORDER = listOf(
    "Mouse",
    "Gerakan",
    "Aksi",
    "Angka & Hotbar",
    "Fungsi (F1-F12)",
    "Modifier & Kontrol",
    "Navigasi",
    "Alfabet (A - Z)",
    "Simbol",
    "Numpad"
)

private fun shortCategory(category: String): String = when (category) {
    "Angka & Hotbar" -> "Hotbar"
    "Fungsi (F1-F12)" -> "F1–F12"
    "Modifier & Kontrol" -> "Modifier"
    "Alfabet (A - Z)" -> "A–Z"
    else -> category
}

/** Kategori yang isinya cuma satu huruf/angka dibuat kotak kecil supaya muat tanpa gulir. */
private fun isSmallCategory(category: String): Boolean =
    category.startsWith("Alfabet") || category.startsWith("Angka")

/** "W (Maju)" -> ("W", "Maju"); tanpa kurung -> (nama, ""). */
private fun splitLabel(name: String): Pair<String, String> {
    val open = name.indexOf(" (")
    if (open < 0 || !name.endsWith(")")) return name to ""
    val main = name.substring(0, open).trim()
    val sub = name.substring(open + 2, name.length - 1).trim()
    return main to sub
}

/**
 * Pemilih tombol keyboard / mouse untuk editor kontrol. Pilih kategori dulu (Mouse, Gerakan, Aksi,
 * Hotbar, F1-F12, ...), lalu ketuk kotak tombolnya. Tiap kategori muat di satu layar jadi tidak perlu
 * menggulir daftar panjang. Pencarian tetap ada (ikon kaca pembesar) tapi tidak wajib.
 *
 * @param comboMode true = memilih tombol kedua untuk kombinasi (tanpa mouse/scroll).
 * @param isCurrent menandai pilihan yang sedang dipakai tombol; kategorinya dibuka lebih dulu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NuxKeyPickerDialog(
    comboMode: Boolean,
    onPick: (KeyOption) -> Unit,
    onDismiss: () -> Unit,
    isCurrent: (KeyOption) -> Boolean = { false }
) {
    val keys = remember(comboMode) {
        if (comboMode) {
            KeycodeCatalog.ALL_KEYS.filter { !it.isScroll && !it.isMouseButton }
        } else {
            KeycodeCatalog.ALL_KEYS
        }
    }
    val categories = remember(keys) {
        val present = keys.map { it.category }.distinct()
        CATEGORY_ORDER.filter { it in present } + present.filter { it !in CATEGORY_ORDER }
    }
    var selected by remember(categories) {
        val currentCategory = keys.firstOrNull { isCurrent(it) }?.category
        mutableStateOf(currentCategory ?: categories.firstOrNull() ?: "")
    }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val shown = remember(keys, selected, searching, query) {
        if (searching && query.isNotBlank()) {
            val q = query.trim().lowercase()
            keys.filter {
                Tr.t(it.displayName).lowercase().contains(q) ||
                    it.displayName.lowercase().contains(q) ||
                    Tr.t(it.category).lowercase().contains(q) ||
                    Tr.t(shortCategory(it.category)).lowercase().contains(q)
            }
        } else {
            keys.filter { it.category == selected }
        }
    }
    val small = !(searching && query.isNotBlank()) && isSmallCategory(selected)

    val gridState = rememberLazyGridState()
    LaunchedEffect(selected, query, searching) { gridState.scrollToItem(0) }

    NuxDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 680.dp),
        fillMaxHeight = true,
        compact = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // Judul + cari + tutup
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (comboMode) "Tombol kedua" else "Pilih tombol",
                        color = NuxColors.DarkGray,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        text = if (comboMode) {
                            "Ditekan bersamaan dengan tombol utama"
                        } else {
                            "Ketuk kategori, lalu ketuk tombolnya"
                        },
                        color = NuxColors.GrayNeutral,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                NuxIconButton(
                    icon = Icons.Outlined.Search,
                    description = "Cari tombol",
                    onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    },
                    tint = if (searching) NuxColors.Yellow else NuxColors.GrayNeutral
                )
                Spacer(modifier = Modifier.width(6.dp))
                NuxIconButton(
                    icon = Icons.Outlined.Close,
                    description = "Tutup",
                    onClick = onDismiss
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (searching) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { category ->
                        CategoryChip(
                            text = shortCategory(category),
                            selected = category == selected,
                            onClick = { selected = category }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (shown.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (query.isBlank()) "Ketik nama tombol, misalnya Shift atau F3" else "Tidak ada yang cocok",
                            color = NuxColors.GrayNeutral,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (small) 60.dp else 120.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 6.dp)
                    ) {
                        items(shown) { option ->
                            KeyTile(
                                option = option,
                                current = isCurrent(option),
                                small = small,
                                onClick = { onPick(option) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(if (selected) NuxColors.Yellow else Color.Transparent, shape)
            .border(
                NuxSizes.GlassBorder,
                if (selected) NuxColors.Yellow else NuxColors.CardBorder,
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
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

@Composable
private fun KeyTile(
    option: KeyOption,
    current: Boolean,
    small: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    // Diterjemahkan dulu baru dipecah, karena teks hanya diterjemahkan secara utuh.
    val (rawMain, rawSub) = splitLabel(Tr.t(option.displayName))
    // Simbol: tampilkan lambangnya besar-besar, nama katanya kecil di bawah.
    val symbolFirst = option.category == "Simbol" && rawSub.isNotEmpty()
    val main = if (symbolFirst) rawSub else rawMain
    val sub = if (small) "" else if (symbolFirst) rawMain else rawSub

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (small) 46.dp else 54.dp)
            .clip(shape)
            .background(if (current) NuxColors.Yellow else NuxColors.SurfaceWhite, shape)
            .border(
                NuxSizes.GlassBorder,
                if (current) NuxColors.Yellow else NuxColors.CardBorder,
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = main,
            color = if (current) NuxColors.Ink else NuxColors.DarkGray,
            fontSize = if (small) 15.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (sub.isNotEmpty()) {
            Text(
                text = sub,
                color = if (current) NuxColors.Ink.copy(alpha = 0.7f) else NuxColors.GrayNeutral,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (_: Exception) {
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(40.dp)
            .background(NuxColors.SurfaceInput, shape)
            .border(NuxSizes.GlassBorder, NuxColors.CardBorder, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "Cari tombol (W, Shift, F3, Tab...)",
                    color = NuxColors.GrayNeutral.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = NuxColors.DarkGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(NuxColors.Yellow),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(NuxColors.SurfaceElevated, CircleShape)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", fontSize = 10.sp, color = NuxColors.DarkGray, fontWeight = FontWeight.Bold)
            }
        }
    }
}
