package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Tr
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.controls.KeyCap
import com.israadev.nuxlauncher.core.controls.KeyOption
import com.israadev.nuxlauncher.core.controls.KeyboardLayout
import com.israadev.nuxlauncher.core.controls.KeycodeCatalog
import com.israadev.nuxlauncher.ui.components.NuxDialog
import com.israadev.nuxlauncher.ui.components.NuxIconButton
import com.israadev.nuxlauncher.ui.theme.NuxColors

private val KEY_SHAPE = RoundedCornerShape(10.dp)
private val TAB_NUMBERS = arrayOf("①", "②", "③")

/** "W (Maju)" -> ("W", "Maju"); tanpa kurung -> (nama, ""). */
private fun splitLabel(name: String): Pair<String, String> {
    val open = name.indexOf(" (")
    if (open < 0 || !name.endsWith(")")) return name to ""
    val main = name.substring(0, open).trim()
    val sub = name.substring(open + 2, name.length - 1).trim()
    return main to sub
}

private fun List<List<KeyCap>>.hasCurrent(isCurrent: (KeyOption) -> Boolean): Boolean =
    any { row -> row.any { cap -> cap.option?.let(isCurrent) == true } }

/**
 * Pemilih tombol berbentuk keyboard. Halaman 1 = keyboard utama (Esc, F1-F12, angka, huruf,
 * modifier, Space); halaman 2 = navigasi, panah, dan numpad; halaman 3 = mouse dan scroll (tidak ada
 * di mode kombinasi). Tinggi tiap baris dan lebar tiap tombol ikut ukuran dialog, jadi muat di layar
 * pendek tanpa menggulir.
 *
 * Susunan tombol berupa data statis ([KeyboardLayout]) dan digambar dengan Row/Column biasa tanpa
 * daftar lazy atau state per tombol, jadi membuka dialog ringan dan tidak ada yang berubah saat idle.
 *
 * @param comboMode true = memilih tombol kedua untuk kombinasi (tanpa mouse/scroll).
 * @param isCurrent menandai pilihan yang sedang dipakai tombol; halamannya dibuka lebih dulu.
 */
@Composable
fun NuxKeyPickerDialog(
    comboMode: Boolean,
    onPick: (KeyOption) -> Unit,
    onDismiss: () -> Unit,
    isCurrent: (KeyOption) -> Boolean = { false }
) {
    val mouseKeys = remember(comboMode) {
        if (comboMode) emptyList() else KeycodeCatalog.ALL_KEYS.filter { it.isMouseButton || it.isScroll }
    }
    val tabTitles = remember(comboMode) {
        if (comboMode) listOf("Utama", "Navigasi & Numpad") else listOf("Utama", "Navigasi & Numpad", "Mouse")
    }
    var tab by remember {
        mutableIntStateOf(
            when {
                mouseKeys.any(isCurrent) -> 2
                KeyboardLayout.NAV.hasCurrent(isCurrent) -> 1
                else -> 0
            }
        )
    }

    NuxDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 820.dp),
        fillMaxHeight = true,
        compact = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (comboMode) "Tombol kedua" else "Pilih tombol",
                        color = NuxColors.DarkGray,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        text = if (comboMode) "Ditekan bersamaan dengan tombol utama" else "Ketuk tombol yang diinginkan",
                        color = NuxColors.GrayNeutral,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                NuxIconButton(
                    icon = Icons.Outlined.Close,
                    description = "Tutup",
                    onClick = onDismiss
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                tabTitles.forEachIndexed { index, title ->
                    PickerTab(
                        number = TAB_NUMBERS[index],
                        title = title,
                        selected = tab == index,
                        onClick = { tab = index },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NuxColors.CardBorder)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                when (tab) {
                    0 -> KeyboardPage(KeyboardLayout.MAIN, isCurrent, onPick)
                    1 -> KeyboardPage(KeyboardLayout.NAV, isCurrent, onPick)
                    else -> MousePage(mouseKeys, isCurrent, onPick)
                }
            }
        }
    }
}

@Composable
private fun PickerTab(
    number: String,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = if (selected) NuxColors.Yellow else NuxColors.GrayNeutral
    Box(
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(text = number, color = ink, fontSize = 15.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = ink,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) NuxColors.Yellow else Color.Transparent)
        )
    }
}

/**
 * Satu halaman keyboard. Tiap baris memakai bobot yang sama untuk tinggi, tiap tombol memakai
 * bobot lebarnya ([KeyCap.w]), sehingga kolom antar baris selalu sejajar di ukuran layar apa pun.
 */
@Composable
private fun KeyboardPage(
    rows: List<List<KeyCap>>,
    isCurrent: (KeyOption) -> Boolean,
    onPick: (KeyOption) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        for (row in rows) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                for (cap in row) {
                    val option = cap.option
                    if (option == null) {
                        Spacer(
                            modifier = Modifier
                                .weight(cap.w)
                                .fillMaxHeight()
                        )
                    } else {
                        KeyCapTile(
                            label = cap.label,
                            current = isCurrent(option),
                            onClick = { onPick(option) },
                            modifier = Modifier
                                .weight(cap.w)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCapTile(
    label: String,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Jarak antar tombol dibuat dari padding di dalam bobot, bukan spacedBy, supaya lebar kolom
    // antar baris tetap persis sejajar.
    Box(modifier = modifier.padding(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(KEY_SHAPE)
                .background(if (current) NuxColors.Yellow else NuxColors.SurfaceElevated)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            // Nama tombol fisik (Esc, Enter, Shift, ...) sengaja tidak diterjemahkan.
            androidx.compose.material3.Text(
                text = label,
                color = if (current) NuxColors.Ink else NuxColors.DarkGray,
                fontSize = if (label.length > 3) 11.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Mouse kiri/kanan/tengah, tombol 4-5, dan scroll: tiga kolom, nama di atas dan fungsinya di bawah. */
@Composable
private fun MousePage(
    keys: List<KeyOption>,
    isCurrent: (KeyOption) -> Boolean,
    onPick: (KeyOption) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (row in keys.chunked(3)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (option in row) {
                    MouseTile(
                        option = option,
                        current = isCurrent(option),
                        onClick = { onPick(option) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MouseTile(
    option: KeyOption,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Diterjemahkan dulu baru dipecah, karena teks hanya diterjemahkan secara utuh.
    val (main, sub) = remember(option) { splitLabel(Tr.t(option.displayName)) }
    Column(
        modifier = modifier
            .clip(KEY_SHAPE)
            .background(if (current) NuxColors.Yellow else NuxColors.SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = main,
            color = if (current) NuxColors.Ink else NuxColors.DarkGray,
            fontSize = 13.sp,
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
