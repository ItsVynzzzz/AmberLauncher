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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import com.israadev.nuxlauncher.ui.streamer.LinkRevealButton
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.streamer.ItemTitle
import com.israadev.nuxlauncher.ui.streamer.placeLabel
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
                                    Text(text = placeLabel(item), color = AmberUi.TextDim, fontSize = 11.sp)
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
            StreamerEditFields(draft, importing, onChange, onPickImage)
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
                enabled = !importing && StreamerLogic.canSave(draft),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Isian formulir satu overlay: jenis, isi, posisi, ukuran, warna, latar, dan "tampil saat game mulai".
 * Dipakai dialog di Pengaturan. Di dalam game, overlay diatur lewat editor di layar (lihat StreamerEditorLayer).
 * [onPickImage] null berarti memilih gambar tidak tersedia (supaya proses game tidak ditinggalkan ke
 * pemilih berkas di HP yang RAM-nya kecil); gambar yang sudah ada tetap bisa diatur posisi dan ukurannya.
 * Dipanggil di dalam Column pemanggil, jadi jarak antar bagian mengikuti Column itu.
 */
@Composable
internal fun StreamerEditFields(
    draft: StreamerItem,
    importing: Boolean,
    onChange: (StreamerItem) -> Unit,
    onPickImage: (() -> Unit)?
) {
    FieldLabel("Jenis")
    ChipRow {
        for (t in StreamerLogic.TYPES) {
            if (t == StreamerLogic.TYPE_IMAGE && onPickImage == null && draft.type != t) continue
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
            // Alamat overlay web sensitif: disamarkan sampai pengguna mengetuk ikon mata.
            var reveal by remember { mutableStateOf(false) }
            NuxTextField(
                value = draft.text,
                onValueChange = { onChange(StreamerLogic.withLinkText(draft, it)) },
                placeholder = if (draft.web) "Tempel link overlay" else "Contoh: ko-fi.com/namamu",
                visualTransformation = if (draft.web && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                trailingContent = if (draft.web) {
                    { LinkRevealButton(revealed = reveal, tint = Amber.TextDim, onToggle = { reveal = !reveal }) }
                } else {
                    null
                }
            )
            MenuGroup {
                MenuToggleRow(
                    title = "Overlay web",
                    subtitle = "Jalankan link overlay dari Saweria, Sociabuzz, Trakteer, StreamElements, Streamlabs, dan lainnya " +
                        "langsung di atas game. Alert, Media Share, Song Share, Subathon, Wheel, dan yang lain didukung. " +
                        "Link tidak ditampilkan.",
                    checked = draft.web,
                    onChange = { onChange(draft.copy(web = it)) }
                )
                if (!draft.web) {
                    MenuDivider()
                    MenuToggleRow(
                        title = "Tampilkan kode QR",
                        subtitle = "Penonton bisa memindainya langsung dari layar.",
                        checked = draft.qr,
                        onChange = { onChange(draft.copy(qr = it)) }
                    )
                }
            }
            if (draft.web) {
                Text(
                    text = "Overlay web memakai RAM dan kuota internet tambahan. Di HP dengan RAM kecil, nyalakan hanya saat dibutuhkan lewat panel di tepi kiri layar. Maksimal 4 overlay web tampil bersamaan.",
                    color = Amber.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                if (draft.text.isNotEmpty() && StreamerLogic.webUrl(draft.text) == null) {
                    Text(
                        text = "Hanya alamat https yang bisa jadi overlay web.",
                        color = Amber.DangerSoft,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            } else {
                val payload = StreamerLogic.qrPayload(draft.text)
                if (draft.qr && payload.isNotEmpty()) {
                    if (QrCode.encode(payload) == null) {
                        Text(
                            text = "Alamat ini terlalu panjang untuk kode QR, jadi hanya tulisannya yang tampil.",
                            color = Amber.DangerSoft,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    } else {
                        Text(
                            text = "Alamat tidak ditulis di bawah kode QR supaya tidak terbaca di layar.",
                            color = Amber.TextDim,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
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

    if (StreamerLogic.isWeb(draft)) {
        // Overlay web memenuhi layar dan menata dirinya sendiri: posisi, ukuran, warna, dan latar tidak berlaku.
        Text(
            text = "Overlay web memenuhi layar, jadi tidak bisa digeser atau diubah ukurannya. Tata letaknya diatur di pengaturan overlay situsnya.",
            color = Amber.TextDim,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    } else {
        // Posisi bebas (diatur lewat editor di layar game): tidak ada titik bawaan yang terpilih.
        val free = StreamerLogic.isFree(draft)
        Spacer(modifier = Modifier.height(4.dp))
        FieldLabel("Posisi")
        ChipRow {
            for (p in listOf(StreamerLogic.POS_TOP_START, StreamerLogic.POS_TOP, StreamerLogic.POS_TOP_END)) {
                SettingChip(posLabel(p), !free && draft.pos == p, { onChange(draft.copy(pos = p, x = -1f, y = -1f)) })
            }
        }
        ChipRow {
            for (p in listOf(StreamerLogic.POS_BOTTOM_START, StreamerLogic.POS_BOTTOM, StreamerLogic.POS_BOTTOM_END)) {
                SettingChip(posLabel(p), !free && draft.pos == p, { onChange(draft.copy(pos = p, x = -1f, y = -1f)) })
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        FieldLabel("Ukuran")
        ChipRow {
            val custom = draft.scale > 0f
            SettingChip("Kecil", !custom && draft.size == StreamerLogic.SIZE_SMALL, { onChange(draft.copy(size = StreamerLogic.SIZE_SMALL, scale = 0f)) })
            SettingChip("Sedang", !custom && draft.size == StreamerLogic.SIZE_MEDIUM, { onChange(draft.copy(size = StreamerLogic.SIZE_MEDIUM, scale = 0f)) })
            SettingChip("Besar", !custom && draft.size == StreamerLogic.SIZE_LARGE, { onChange(draft.copy(size = StreamerLogic.SIZE_LARGE, scale = 0f)) })
        }

        if (draft.type != StreamerLogic.TYPE_IMAGE) {
            Spacer(modifier = Modifier.height(4.dp))
            FieldLabel("Warna teks")
            StreamerColorRow(selected = draft.color, onPick = { onChange(draft.copy(color = it)) })
            MenuGroup {
                MenuToggleRow(
                    title = "Latar belakang",
                    subtitle = "Kotak di belakang tulisan. Matikan supaya tulisannya polos.",
                    checked = draft.bg,
                    onChange = { onChange(draft.copy(bg = it)) }
                )
            }
        }
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

/**
 * Pilihan warna tulisan: bulatan berwarna tanpa teks, jadi tidak perlu terjemahan. Dua baris, empat warna
 * tiap baris, supaya muat di panel yang sempit dan semua warna terlihat tanpa menggulir.
 */
@Composable
internal fun StreamerColorRow(selected: String, onPick: (String) -> Unit, swatch: Dp = 34.dp) {
    val current = StreamerLogic.normalizeColor(selected)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (rowColors in StreamerLogic.COLORS.chunked(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in rowColors) {
                    val on = current == c
                    Box(
                        modifier = Modifier
                            .size(swatch)
                            .clip(CircleShape)
                            .background(Color(StreamerLogic.textArgb(c)))
                            .border(
                                if (on) 3.dp else 1.dp,
                                if (on) AmberUi.Accent else AmberUi.BorderStrong,
                                CircleShape
                            )
                            .amberTap { onPick(c) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePicker(image: String, importing: Boolean, onPick: (() -> Unit)?) {
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
            if (onPick != null) {
                AmberButton(
                    text = if (image.isEmpty()) "Pilih gambar" else "Ganti gambar",
                    onClick = onPick,
                    style = AmberButtonStyle.Ghost,
                    enabled = !importing,
                    height = 40.dp
                )
            }
            Text(
                text = when {
                    importing -> "Memproses gambar..."
                    onPick == null -> "Gambar hanya bisa diganti dari layar utama: Pengaturan > Game > Mode streamer > Atur overlay."
                    else -> "Foto atau logo. Dikecilkan otomatis supaya ringan."
                },
                color = Amber.TextDim,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}
