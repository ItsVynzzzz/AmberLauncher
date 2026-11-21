package com.israadev.nuxlauncher.ui.streamer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.streamer.StreamerItem
import com.israadev.nuxlauncher.core.streamer.StreamerLogic
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.components.AmberIcon
import com.israadev.nuxlauncher.ui.components.amberTap
import com.israadev.nuxlauncher.ui.dialogs.MenuDivider
import com.israadev.nuxlauncher.ui.dialogs.MenuGroup
import com.israadev.nuxlauncher.ui.dialogs.StreamerEditFields
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.AmberUi

/**
 * Sidebar kiri di menu pengaturan game: daftar overlay (tampil/sembunyi, timer) dan formulir untuk
 * menambah, mengubah, atau menghapus overlay tanpa keluar dari game. Isinya dipasang di jendela menu
 * (lihat GameMenuSheet), jadi hanya dibuat selama menu terbuka dan hilang bersamanya.
 *
 * Ringan: tanpa animasi dan tanpa blur. Daftar hanya menggambar ulang saat daftar overlay atau
 * keadaan tampilnya berubah. Perubahan disimpan lewat [onItemsChange] saat menekan Simpan atau Hapus
 * (bukan tiap ketukan huruf), jadi tidak ada penulisan berkas selama mengetik.
 *
 * [onEditingChange] memberi tahu menu saat formulir terbuka, supaya mengetuk area kosong tidak menutup
 * menu dan membuang isian. Memilih gambar sengaja tidak tersedia di dalam game.
 */
@Composable
fun StreamerSidePanel(
    items: List<StreamerItem>,
    onItemsChange: (List<StreamerItem>) -> Unit,
    onEditingChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val list = remember(items) { StreamerLogic.sanitize(items) }
    var draft by remember { mutableStateOf<StreamerItem?>(null) }
    var isNew by remember { mutableStateOf(false) }

    val currentOnEditingChange by rememberUpdatedState(onEditingChange)
    LaunchedEffect(draft != null) { currentOnEditingChange(draft != null) }
    DisposableEffect(Unit) { onDispose { currentOnEditingChange(false) } }

    val shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            // Sebagian layar di layar sempit, paling lebar 290dp, supaya muat bersama panel kanan menu.
            .fillMaxWidth(0.44f)
            .widthIn(max = PANEL_MAX_WIDTH_DP.dp)
            .clip(shape)
            .background(AmberUi.MenuCard)
            .border(1.dp, AmberUi.Border, shape)
            // Menelan ketukan di area kosong supaya tidak menutup menu.
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
            )
            // Papan ketik mengangkat seluruh panel, jadi kolom isian dan tombol Simpan tetap terlihat.
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        val d = draft
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (d == null) "Mode streamer" else if (isNew) "Overlay baru" else "Ubah overlay",
                    color = AmberUi.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (d == null) {
                    Text(
                        text = "Ketuk untuk menampilkan atau menyembunyikan.",
                        color = AmberUi.TextDim,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AmberUi.SurfaceRaised)
                    .amberTap(onClose),
                contentAlignment = Alignment.Center
            ) {
                AmberIcon("close", 13.dp, AmberUi.Text)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (d == null) {
            ListBody(
                list = list,
                onAdd = {
                    val id = StreamerLogic.newId(list.map { it.id }.toSet(), System.currentTimeMillis())
                    draft = StreamerItem(id = id, type = StreamerLogic.TYPE_TEXT, on = true)
                    isNew = true
                },
                onEdit = {
                    draft = it
                    isNew = false
                }
            )
        } else {
            EditBody(
                draft = d,
                isNew = isNew,
                onChange = { draft = it },
                onSave = {
                    val clean = StreamerLogic.sanitizeOne(d)
                    onItemsChange(if (isNew) list + clean else list.map { if (it.id == clean.id) clean else it })
                    draft = null
                },
                onDelete = {
                    onItemsChange(list.filter { it.id != d.id })
                    draft = null
                },
                onCancel = { draft = null }
            )
        }
    }
}

private const val PANEL_MAX_WIDTH_DP = 290

@Composable
private fun ColumnScope.ListBody(
    list: List<StreamerItem>,
    onAdd: () -> Unit,
    onEdit: (StreamerItem) -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
    ) {
        if (list.isEmpty()) {
            Text(
                text = "Belum ada overlay. Ketuk Tambah untuk membuatnya.",
                color = AmberUi.TextDim,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            MenuGroup {
                list.forEachIndexed { index, item ->
                    if (index > 0) MenuDivider()
                    key(item.id) { PanelRow(item, onEdit = { onEdit(item) }) }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    AmberButton(
        text = "Tambah",
        onClick = onAdd,
        enabled = list.size < StreamerLogic.MAX_ITEMS,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ColumnScope.EditBody(
    draft: StreamerItem,
    isNew: Boolean,
    onChange: (StreamerItem) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StreamerEditFields(
            draft = draft,
            importing = false,
            onChange = onChange,
            onPickImage = null
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AmberButton(
            text = "Batal",
            onClick = onCancel,
            style = AmberButtonStyle.Ghost,
            modifier = Modifier.weight(1f)
        )
        AmberButton(
            text = "Simpan",
            onClick = onSave,
            enabled = StreamerLogic.canSave(draft),
            modifier = Modifier.weight(1f)
        )
    }
    if (!isNew) {
        Spacer(modifier = Modifier.height(8.dp))
        AmberButton(
            text = "Hapus",
            onClick = onDelete,
            style = AmberButtonStyle.Danger,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
