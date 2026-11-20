package com.israadev.nuxlauncher.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.core.streamer.QrCode
import com.israadev.nuxlauncher.core.streamer.StreamerImages
import com.israadev.nuxlauncher.core.streamer.StreamerItem
import com.israadev.nuxlauncher.core.streamer.StreamerLogic
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.components.AmberIcon
import com.israadev.nuxlauncher.ui.components.NuxDialog
import com.israadev.nuxlauncher.ui.components.NuxTextField
import com.israadev.nuxlauncher.ui.components.SettingChip
import com.israadev.nuxlauncher.ui.components.amberTap
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.streamer.ItemTitle
import com.israadev.nuxlauncher.ui.streamer.posLabel
import com.israadev.nuxlauncher.ui.streamer.rememberStreamerBitmap
import com.israadev.nuxlauncher.ui.streamer.typeLabel
import com.israadev.nuxlauncher.ui.theme.Amber
import com.israadev.nuxlauncher.ui.theme.AmberUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun keepNames(list: List<StreamerItem?>?): Set<String> =
    StreamerLogic.sanitize(list).mapNotNull { it.image.takeIf { n -> n.isNotEmpty() } }.toSet()

private fun canSave(d: StreamerItem): Boolean = StreamerLogic.isDisplayable(StreamerLogic.sanitizeOne(d))

/**
 * Dialog untuk mengatur overlay streamer: daftar overlay (maksimal [StreamerLogic.MAX_ITEMS]) dan
 * formulir tambah/ubah/hapus. Perubahan langsung disimpan ke pengaturan saat menekan Simpan atau Hapus
 * dan berlaku mulai game berikutnya. Gambar yang tidak terpakai lagi dihapus dari folder aplikasi.
 */
@Composable
fun StreamerManagerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SettingsManager.settings.collectAsState()
    val items = remember(settings.streamerItems) { StreamerLogic.sanitize(settings.streamerItems) }

    var draft by remember { mutableStateOf<StreamerItem?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    // Sisa gambar dari perubahan atau reset sebelumnya dibersihkan saat dialog dibuka.
    LaunchedEffect(Unit) {
        val keep = keepNames(SettingsManager.settings.value.streamerItems)
        withContext(Dispatchers.IO) { StreamerImages.sweep(context, keep) }
    }

    fun sweepInBackground(list: List<StreamerItem?>?) {
        val keep = keepNames(list)
        Thread { StreamerImages.sweep(context, keep) }.start()
    }

    fun persist(newItems: List<StreamerItem>) {
        val clean = StreamerLogic.sanitize(newItems)
        SettingsManager.updateSettings(context, SettingsManager.settings.value.copy(streamerItems = clean))
        sweepInBackground(clean)
    }

    fun closeEditor() {
        draft = null
        importing = false
        // Gambar yang dipilih lalu dibatalkan ikut dibersihkan.
        sweepInBackground(SettingsManager.settings.value.streamerItems)
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val current = draft
        if (uri != null && current != null) {
            importing = true
            scope.launch {
                val name = withContext(Dispatchers.IO) { StreamerImages.importImage(context, uri, current.id) }
                importing = false
                if (name == null) {
                    Toast.makeText(context, "Gambar tidak bisa dibaca", Toast.LENGTH_SHORT).show()
                } else {
                    draft = draft?.copy(image = name)
                }
            }
        }
    }

    NuxDialog(
        onDismissRequest = { if (draft != null) closeEditor() else onDismiss() },
        modifier = Modifier.widthIn(max = 540.dp),
        compact = true
    ) {
        val d = draft
        if (d == null) {
            ListPane(
                items = items,
                onAdd = {
                    val id = StreamerLogic.newId(items.map { it.id }.toSet(), System.currentTimeMillis())
                    draft = StreamerItem(id = id, type = StreamerLogic.TYPE_TEXT, on = true)
                    isNew = true
                },
                onEdit = {
                    draft = it
                    isNew = false
                },
                onDone = onDismiss
            )
        } else {
            EditPane(
                draft = d,
                isNew = isNew,
                importing = importing,
                onChange = { draft = it },
                onPickImage = { pickImage.launch(arrayOf("image/*")) },
                onSave = {
                    val clean = StreamerLogic.sanitizeOne(d)
                    persist(if (isNew) items + clean else items.map { if (it.id == clean.id) clean else it })
                    draft = null
                    importing = false
                },
                onDelete = {
                    persist(items.filter { it.id != d.id })
                    draft = null
                    importing = false
                },
                onCancel = { closeEditor() }
            )
        }
    }
}

@Composable
private fun ListPane(
    items: List<StreamerItem>,
    onAdd: () -> Unit,
    onEdit: (StreamerItem) -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text(
            text = "Atur overlay",
            color = Amber.Text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tampilkan teks, gambar, link, atau timer di atas game. Buka dan sembunyikan lewat panel di tepi kiri layar saat bermain.",
            color = Amber.TextDim,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            if (items.isEmpty()) {
                Text(
                    text = "Belum ada overlay. Ketuk Tambah untuk membuatnya.",
                    color = Amber.TextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            } else {
                MenuGroup {
                    items.forEachIndexed { index, item ->
                        if (index > 0) MenuDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .amberTap { onEdit(item) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                ItemTitle(item, 14)
                                // Dua bagian diterjemahkan sendiri-sendiri (kunci terjemahan per kata).
                                Row {
                                    Text(text = typeLabel(item.type), color = AmberUi.TextDim, fontSize = 11.sp)
                                    Text(text = " \u00B7 ", color = AmberUi.TextDim, fontSize = 11.sp)
                                    Text(text = posLabel(item.pos), color = AmberUi.TextDim, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            AmberIcon("chev_right", 14.dp, AmberUi.TextDim)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AmberButton(
                text = "Selesai",
                onClick = onDone,
                style = AmberButtonStyle.Ghost,
                modifier = Modifier.weight(1f)
            )
            AmberButton(
                text = "Tambah",
                onClick = onAdd,
                enabled = items.size < StreamerLogic.MAX_ITEMS,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EditPane(
    draft: StreamerItem,
    isNew: Boolean,
    importing: Boolean,
    onChange: (StreamerItem) -> Unit,
    onPickImage: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text(
            text = if (isNew) "Overlay baru" else "Ubah overlay",
            color = Amber.Text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FieldLabel("Jenis")
            ChipRow {
                for (t in StreamerLogic.TYPES) {
                    SettingChip(
                        text = typeLabel(t),
                        selected = draft.type == t,
                        onClick = { if (draft.type != t) onChange(draft.copy(type = t)) }
                    )
                }
            }

            when (draft.type) {
                StreamerLogic.TYPE_TEXT -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    NuxTextField(
                        value = draft.text,
                        onValueChange = { onChange(draft.copy(text = StreamerLogic.limitInput(it, StreamerLogic.MAX_TEXT_CHARS))) },
                        placeholder = "Tulis teks overlay"
                    )
                }
                StreamerLogic.TYPE_LINK -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    NuxTextField(
                        value = draft.text,
                        onValueChange = { onChange(draft.copy(text = StreamerLogic.limitInput(it, StreamerLogic.MAX_LINK_CHARS))) },
                        placeholder = "Contoh: ko-fi.com/namamu"
                    )
                    MenuGroup {
                        MenuToggleRow(
                            title = "Tampilkan kode QR",
                            subtitle = "Penonton bisa memindainya langsung dari layar.",
                            checked = draft.qr,
                            onChange = { onChange(draft.copy(qr = it)) }
                        )
                    }
                    val payload = StreamerLogic.qrPayload(draft.text)
                    if (draft.qr && payload.isNotEmpty() && QrCode.encode(payload) == null) {
                        Text(
                            text = "Alamat ini terlalu panjang untuk kode QR, jadi hanya tulisannya yang tampil.",
                            color = Amber.DangerSoft,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
                StreamerLogic.TYPE_IMAGE -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    ImagePicker(draft.image, importing, onPickImage)
                }
                else -> {
                    Text(
                        text = "Timer mulai dari 00:00. Mulai, jeda, dan reset-nya lewat panel di tepi kiri layar saat bermain.",
                        color = Amber.TextDim,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            FieldLabel("Posisi")
            ChipRow {
                for (p in listOf(StreamerLogic.POS_TOP_START, StreamerLogic.POS_TOP, StreamerLogic.POS_TOP_END)) {
                    SettingChip(posLabel(p), draft.pos == p, { onChange(draft.copy(pos = p)) })
                }
            }
            ChipRow {
                for (p in listOf(StreamerLogic.POS_BOTTOM_START, StreamerLogic.POS_BOTTOM, StreamerLogic.POS_BOTTOM_END)) {
                    SettingChip(posLabel(p), draft.pos == p, { onChange(draft.copy(pos = p)) })
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            FieldLabel("Ukuran")
            ChipRow {
                SettingChip("Kecil", draft.size == StreamerLogic.SIZE_SMALL, { onChange(draft.copy(size = StreamerLogic.SIZE_SMALL)) })
                SettingChip("Sedang", draft.size == StreamerLogic.SIZE_MEDIUM, { onChange(draft.copy(size = StreamerLogic.SIZE_MEDIUM)) })
                SettingChip("Besar", draft.size == StreamerLogic.SIZE_LARGE, { onChange(draft.copy(size = StreamerLogic.SIZE_LARGE)) })
            }

            Spacer(modifier = Modifier.height(4.dp))
            MenuGroup {
                MenuToggleRow(
                    title = "Tampil saat game mulai",
                    subtitle = "Bila mati, nyalakan lewat panel di tepi kiri layar saat bermain.",
                    checked = draft.on,
                    onChange = { onChange(draft.copy(on = it)) }
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isNew) {
                AmberButton(
                    text = "Hapus",
                    onClick = onDelete,
                    style = AmberButtonStyle.Danger,
                    modifier = Modifier.weight(1f)
                )
            }
            AmberButton(
                text = "Batal",
                onClick = onCancel,
                style = AmberButtonStyle.Ghost,
                modifier = Modifier.weight(1f)
            )
            AmberButton(
                text = "Simpan",
                onClick = onSave,
                enabled = !importing && canSave(draft),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = Amber.TextDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun ImagePicker(image: String, importing: Boolean, onPick: () -> Unit) {
    val bmp = if (image.isNotEmpty()) rememberStreamerBitmap(image) else null
    val shape = RoundedCornerShape(12.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(shape)
                .background(Amber.Fill)
                .border(1.dp, Amber.Line, shape),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AmberButton(
                text = if (image.isEmpty()) "Pilih gambar" else "Ganti gambar",
                onClick = onPick,
                style = AmberButtonStyle.Ghost,
                enabled = !importing,
                height = 40.dp
            )
            Text(
                text = if (importing) "Memproses gambar..." else "Foto atau logo. Dikecilkan otomatis supaya ringan.",
                color = Amber.TextDim,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}
