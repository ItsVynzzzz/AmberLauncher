package com.israadev.nuxlauncher.ui.streamer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.streamer.QrCode
import com.israadev.nuxlauncher.core.streamer.StreamerItem
import com.israadev.nuxlauncher.core.streamer.StreamerLogic
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.components.AmberIcon
import com.israadev.nuxlauncher.ui.components.NuxTextField
import com.israadev.nuxlauncher.ui.components.amberTap
import com.israadev.nuxlauncher.ui.dialogs.MenuSliderRow
import com.israadev.nuxlauncher.ui.dialogs.MenuSwitch
import com.israadev.nuxlauncher.ui.dialogs.StreamerColorRow
import com.israadev.nuxlauncher.ui.dialogs.fractionWidthCapped
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.AmberUi

/*
 * Editor overlay streamer di layar, seperti editor kontrol: overlay digeser bebas ke mana saja dengan
 * satu jari, dicubit untuk mengubah ukuran, dan diatur lewat panel kecil yang menempel di sisi layar
 * yang berlawanan dengan overlay yang dipilih (jadi tidak menutupinya).
 *
 * Ringan: lapisan ini hanya ada selama editor terbuka. Latarnya digambar sekali (tanpa blur, tanpa
 * animasi). Selama menggeser, yang tersusun ulang hanya item yang dipegang. Perubahan disimpan sekali,
 * saat menekan Selesai.
 */

private val OutlineShape = RoundedCornerShape(10.dp)
private val BarShape = RoundedCornerShape(14.dp)

/** Lebar panel pengaturan: sepertiga layar, paling lebar 200dp, supaya layar tetap terlihat. */
private const val INSPECTOR_MAX_WIDTH_DP = 200

/**
 * Lapisan editor. [items] adalah daftar overlay saat ini; [onDone] dipanggil dengan daftar baru (item yang
 * masih kosong dibuang) saat menekan Selesai.
 */
@Composable
fun StreamerEditorLayer(
    items: List<StreamerItem>,
    onDone: (List<StreamerItem>) -> Unit
) {
    // Item lama yang masih memakai titik bawaan diubah jadi posisi bebas di sini (letaknya tetap sama).
    var work by remember { mutableStateOf(StreamerLogic.withFreePositions(StreamerLogic.sanitize(items))) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    // Pilihan manual sisi panel (null = otomatis di sisi yang berlawanan dengan item).
    var flip by remember { mutableStateOf<Boolean?>(null) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val maxWidthDp = LocalConfiguration.current.screenWidthDp * 0.42f

    Box(modifier = Modifier.fillMaxSize()) {
        // Latar redup, titik-titik bantu, dan garis tengah. Isinya tidak berubah, jadi digambar sekali.
        // Mengetuk area kosong melepas pilihan; sentuhan tidak tembus ke game di bawahnya.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { selectedId = null } }
        ) {
            drawRect(Color(0x4D000000))
            val step = 32.dp.toPx()
            val dot = Color(0x33FFFFFF)
            var x = step
            while (x < size.width) {
                var y = step
                while (y < size.height) {
                    drawCircle(dot, radius = 1.5.dp.toPx(), center = Offset(x, y))
                    y += step
                }
                x += step
            }
            val guide = Color(0x26FFFFFF)
            drawLine(guide, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.dp.toPx())
            drawLine(guide, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
        }

        // Wadah yang sama persis dengan lapisan overlay di game (poni + 8dp), supaya letaknya identik.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(8.dp)
                .onSizeChanged { area = it }
        ) {
            // Bar atas: Selesai dan tombol tambah. Di bawah item, jadi item di pojok tetap bisa dipegang.
            TopBar(
                canAdd = work.size < StreamerLogic.MAX_ITEMS,
                onAdd = { type ->
                    val id = StreamerLogic.newId(work.map { it.id }.toSet(), System.currentTimeMillis())
                    // Item baru muncul di tengah, sedikit bergeser tiap tambahan supaya tidak menumpuk persis.
                    val y = 0.4f + 0.1f * (work.size % 4)
                    val item = StreamerItem(
                        id = id, type = type, on = true,
                        x = 0.5f, y = y, scale = 1f
                    )
                    work = work + item
                    selectedId = id
                    flip = null
                },
                onDone = { onDone(work.filter { StreamerLogic.canSave(it) }) }
            )

            if (work.isEmpty()) {
                Text(
                    text = "Belum ada overlay.",
                    color = AmberUi.TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (selectedId == null) {
                HintPill(modifier = Modifier.align(Alignment.BottomCenter))
            }

            for (item in work) {
                key(item.id) {
                    Box(modifier = Modifier.freePlaced(item.x, item.y)) {
                        EditableItem(
                            item = item,
                            selected = item.id == selectedId,
                            maxWidthDp = maxWidthDp,
                            area = { area },
                            read = { id -> work.firstOrNull { it.id == id } },
                            write = { changed -> work = work.map { if (it.id == changed.id) changed else it } },
                            onSelect = { id ->
                                if (selectedId != id) {
                                    selectedId = id
                                    flip = null
                                }
                            }
                        )
                    }
                }
            }

            val sel = work.firstOrNull { it.id == selectedId }
            if (sel != null) {
                val dockLeft = flip ?: (sel.x > 0.5f)
                Inspector(
                    item = sel,
                    dockLeft = dockLeft,
                    onFlip = { flip = !dockLeft },
                    onClose = { selectedId = null },
                    onChange = { changed -> work = work.map { if (it.id == changed.id) changed else it } },
                    onDelete = {
                        work = work.filter { it.id != sel.id }
                        selectedId = null
                    },
                    modifier = Modifier.align(if (dockLeft) Alignment.CenterStart else Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
private fun TopBar(canAdd: Boolean, onAdd: (String) -> Unit, onDone: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        val doneShape = RoundedCornerShape(12.dp)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(doneShape)
                .background(AmberUi.AccentBrush, doneShape)
                .border(1.dp, AmberUi.AccentEdge, doneShape)
                .amberTap(onDone)
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Text(
                text = "Selesai",
                color = AmberUi.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .clip(BarShape)
                .background(AmberUi.MenuCard)
                .border(1.dp, AmberUi.Border, BarShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gambar hanya bisa ditambah dari layar utama (pemilih berkas tidak dibuka dari dalam game).
            for (t in listOf(StreamerLogic.TYPE_TEXT, StreamerLogic.TYPE_LINK, StreamerLogic.TYPE_TIMER)) {
                Row(
                    modifier = Modifier
                        .clip(OutlineShape)
                        .background(AmberUi.SurfaceRaised)
                        .then(if (canAdd) Modifier.amberTap { onAdd(t) } else Modifier.graphicsLayer { alpha = 0.4f })
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "+", color = AmberUi.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = typeLabel(t),
                        color = AmberUi.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun HintPill(modifier: Modifier) {
    Box(
        modifier = modifier
            .padding(bottom = 4.dp)
            .clip(BarShape)
            .background(AmberUi.MenuCard)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "Seret overlay untuk memindahkan, cubit untuk mengubah ukuran.",
            color = AmberUi.TextDim,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Satu overlay di editor: sama dengan tampilannya di game, ditambah garis tepi (kuning bila dipilih).
 * Satu jari menggeser, dua jari mencubit. Posisi dan skala dibaca dari daftar terbaru lewat [read] di
 * setiap gerakan, jadi gerakan cepat tidak ada yang hilang sebelum tampilan sempat tersusun ulang.
 */
@Composable
private fun EditableItem(
    item: StreamerItem,
    selected: Boolean,
    maxWidthDp: Float,
    area: () -> IntSize,
    read: (String) -> StreamerItem?,
    write: (StreamerItem) -> Unit,
    onSelect: (String) -> Unit
) {
    val id = item.id
    Box(
        modifier = Modifier
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) AmberUi.Accent else Color(0x66FFFFFF),
                OutlineShape
            )
            .pointerInput(id) { detectTapGestures { onSelect(id) } }
            .pointerInput(id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val cur = read(id) ?: return@detectTransformGestures
                    onSelect(id)
                    val a = area()
                    val scale =
                        if (zoom == 1f) cur.scale else StreamerLogic.zoomedScale(StreamerLogic.scaleOf(cur), zoom)
                    write(
                        cur.copy(
                            x = StreamerLogic.movedFraction(cur.x, pan.x, (a.width - size.width).toFloat()),
                            y = StreamerLogic.movedFraction(cur.y, pan.y, (a.height - size.height).toFloat()),
                            scale = scale
                        )
                    )
                }
            }
    ) {
        if (StreamerLogic.canSave(item)) {
            StreamerItemView(item, maxWidthDp)
        } else {
            // Teks atau link yang belum diisi: tanda tempat, supaya tetap bisa dipilih dan diisi.
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    text = when (item.type) {
                        StreamerLogic.TYPE_TEXT -> "Tulis teks overlay"
                        StreamerLogic.TYPE_LINK -> if (item.web) "Tempel link overlay" else "Contoh: ko-fi.com/namamu"
                        else -> typeLabel(item.type)
                    },
                    color = Color(0xCCFFFFFF),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Panel pengaturan overlay yang dipilih. Menempel di sisi yang berlawanan dengan overlay-nya (bisa dibalik
 * dengan panah), di bawah bar atas, dan bisa digulir. Perubahan langsung terlihat di layar.
 */
@Composable
private fun Inspector(
    item: StreamerItem,
    dockLeft: Boolean,
    onFlip: () -> Unit,
    onClose: () -> Unit,
    onChange: (StreamerItem) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val isImage = item.type == StreamerLogic.TYPE_IMAGE
    val isWeb = StreamerLogic.isWeb(item)
    Column(
        modifier = modifier
            .padding(top = 44.dp)
            .fillMaxHeight()
            .fractionWidthCapped(0.30f, INSPECTOR_MAX_WIDTH_DP.dp)
            .clip(shape)
            .background(AmberUi.MenuCard)
            .border(1.dp, AmberUi.Border, shape)
            // Menelan ketukan di area kosong panel supaya pilihan tidak terlepas.
            .pointerInput(Unit) { detectTapGestures { } }
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = typeLabel(item.type),
                color = AmberUi.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            RoundIconButton(onClick = onFlip) {
                // Panah menunjuk ke sisi tempat panel akan pindah.
                AmberIcon(
                    "chev_right",
                    12.dp,
                    AmberUi.Text,
                    Modifier.graphicsLayer { rotationZ = if (dockLeft) 0f else 180f }
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            RoundIconButton(onClick = onClose) { AmberIcon("close", 12.dp, AmberUi.Text) }
        }

        val fieldStyle = TextStyle(color = AmberUi.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        when (item.type) {
            StreamerLogic.TYPE_TEXT -> NuxTextField(
                value = item.text,
                onValueChange = { onChange(item.copy(text = StreamerLogic.limitInput(it, StreamerLogic.MAX_TEXT_CHARS))) },
                placeholder = "Tulis teks overlay",
                textStyle = fieldStyle
            )
            StreamerLogic.TYPE_LINK -> {
                // Alamat overlay web sensitif: disamarkan sampai pengguna mengetuk ikon mata.
                var reveal by remember(item.id) { mutableStateOf(false) }
                NuxTextField(
                    value = item.text,
                    onValueChange = { onChange(StreamerLogic.withLinkText(item, it)) },
                    placeholder = if (item.web) "Tempel link overlay" else "Contoh: ko-fi.com/namamu",
                    textStyle = fieldStyle,
                    visualTransformation = if (item.web && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                    trailingContent = if (item.web) {
                        { LinkRevealButton(revealed = reveal, tint = AmberUi.TextDim, onToggle = { reveal = !reveal }) }
                    } else {
                        null
                    }
                )
                SwitchRow("Overlay web", item.web) { onChange(item.copy(web = it)) }
                if (item.web) {
                    Text(
                        text = "Overlay web memakai RAM dan kuota internet tambahan. Di HP dengan RAM kecil, nyalakan hanya saat dibutuhkan lewat panel di tepi kiri layar. Maksimal 4 overlay web tampil bersamaan.",
                        color = AmberUi.TextDim,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                    if (item.text.isNotEmpty() && StreamerLogic.webUrl(item.text) == null) {
                        Text(
                            text = "Hanya alamat https yang bisa jadi overlay web.",
                            color = AmberUi.TextDim,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                } else {
                    SwitchRow("Tampilkan kode QR", item.qr) { onChange(item.copy(qr = it)) }
                    val payload = StreamerLogic.qrPayload(item.text)
                    if (item.qr && payload.isNotEmpty()) {
                        Text(
                            text = if (QrCode.encode(payload) == null) {
                                "Alamat ini terlalu panjang untuk kode QR, jadi hanya tulisannya yang tampil."
                            } else {
                                "Alamat tidak ditulis di bawah kode QR supaya tidak terbaca di layar."
                            },
                            color = AmberUi.TextDim,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
            else -> Unit
        }

        // Overlay web memenuhi layar dan menata dirinya sendiri: ukuran, warna, dan latar tidak berlaku.
        if (!isWeb) MenuSliderRow(
            label = "Ukuran",
            value = Math.round(StreamerLogic.scaleOf(item) * 100f),
            min = Math.round(StreamerLogic.SCALE_MIN * 100f),
            max = Math.round(StreamerLogic.SCALE_MAX * 100f),
            step = 5,
            onCommit = {},
            onLive = { onChange(item.copy(scale = it / 100f)) }
        )
        MenuSliderRow(
            label = "Opasitas",
            value = Math.round(item.alpha * 100f),
            min = Math.round(StreamerLogic.ALPHA_MIN * 100f),
            max = 100,
            step = 5,
            onCommit = {},
            onLive = { onChange(item.copy(alpha = it / 100f)) }
        )

        if (!isImage && !isWeb) {
            Text(
                text = "Warna teks",
                color = AmberUi.TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            StreamerColorRow(
                selected = item.color,
                onPick = { onChange(item.copy(color = it)) },
                swatch = 30.dp
            )
            SwitchRow("Latar belakang", item.bg) { onChange(item.copy(bg = it)) }
        }
        SwitchRow("Tampil saat game mulai", item.on) { onChange(item.copy(on = it)) }

        AmberButton(
            text = "Hapus",
            onClick = onDelete,
            style = AmberButtonStyle.Danger,
            height = 36.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(AmberUi.SurfaceRaised)
            .amberTap(onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Baris judul dengan saklar, tanpa keterangan: seluruh baris bisa diketuk. */
@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .amberTap { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = AmberUi.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        MenuSwitch(checked)
    }
}
