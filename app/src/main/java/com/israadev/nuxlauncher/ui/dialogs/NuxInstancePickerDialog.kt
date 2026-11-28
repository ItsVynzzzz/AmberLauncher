package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import com.israadev.nuxlauncher.ui.components.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.forge.ForgeFlavor
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.ui.components.AmberPage
import com.israadev.nuxlauncher.ui.components.AmberPageLabel
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Tr
import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.israadev.nuxlauncher.ui.theme.amberGlass

/**
 * Pemilih instance berbentuk daftar ke bawah: satu instance satu baris, mudah dibaca dan digulir dengan jempol.
 *
 * Tiap baris kaca berisi lingkaran pilihan, nama instance, loader + versi Minecraft, dan tiga tombol bulat
 * (buka folder, ubah, hapus). Yang aktif diberi isian aksen. Baris putus-putus di ujung daftar untuk menambah
 * instance, dan tombol + di bar atas supaya tetap terjangkau saat instance banyak. Daftar dibatasi lebarnya
 * supaya nyaman dibaca di layar lebar, dan digulir ke bawah sehingga tinggi layar sekecil apa pun tidak
 * memotong isinya. Ketuk area gelap di luar daftar untuk menutup.
 */
@Composable
fun NuxInstancePickerDialog(
    instances: List<Instance>,
    selectedId: String?,
    onSelect: (Instance) -> Unit,
    onOpenFolder: (Instance) -> Unit,
    onEdit: (Instance) -> Unit,
    onDelete: (Instance) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    onTogglePin: (Instance) -> Unit = {}
) {
    val listState = rememberLazyListState()
    // Yang disematkan di atas, sisanya tetap berurutan seperti di daftar. Judul DISEMATKAN / LAINNYA hanya tampil
    // bila daftarnya memang terbagi dua (ada yang disematkan dan ada yang tidak).
    val ordered = remember(instances) { InstanceManager.pinnedFirst(instances) }
    val pinnedCount = ordered.count { it.pinned }
    val showHeaders = pinnedCount > 0 && pinnedCount < ordered.size
    // Buka dengan instance aktif sudah terlihat, bukan selalu dari baris pertama.
    LaunchedEffect(Unit) {
        val index = ordered.indexOfFirst { it.id == selectedId }
        if (index > 0) {
            // Judul juga memakai satu butir daftar: satu di atas yang disematkan, satu lagi di atas sisanya
            val headers = if (showHeaders) (if (index >= pinnedCount) 2 else 1) else 0
            listState.scrollToItem(index + headers)
        }
    }

    AmberPage(
        onDismiss = onDismiss,
        title = "PILIH INSTANCE",
        icon = Icons.Outlined.Folder,
        dismissOnScrimTap = true,
        scrimAlpha = 0.74f,
        actions = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AmberUi.AccentBrush, CircleShape)
                    .border(1.dp, AmberUi.AccentEdge, CircleShape)
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = Tr.t("TAMBAH INSTANCE"),
                    tint = AmberUi.Text,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { info ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (instances.isEmpty()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .pointerInput(Unit) { detectTapGestures { } },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Belum ada instance. Buat yang pertama di bawah.",
                        color = AmberUi.TextDim,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AddInstanceRow(onClick = onAdd)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        // Celah di antara baris tidak ikut menutup dialog
                        .pointerInput(Unit) { detectTapGestures { } },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showHeaders) {
                        item(key = "__pinned_header__") { AmberPageLabel("DISEMATKAN") }
                    }
                    items(ordered.take(pinnedCount), key = { it.id }) { inst ->
                        InstanceRow(
                            inst = inst,
                            selected = inst.id == selectedId,
                            compact = info.narrow,
                            onSelect = { onSelect(inst) },
                            onOpenFolder = { onOpenFolder(inst) },
                            onEdit = { onEdit(inst) },
                            onDelete = { onDelete(inst) },
                            onTogglePin = { onTogglePin(inst) }
                        )
                    }
                    if (showHeaders) {
                        item(key = "__rest_header__") { AmberPageLabel("LAINNYA") }
                    }
                    items(ordered.drop(pinnedCount), key = { it.id }) { inst ->
                        InstanceRow(
                            inst = inst,
                            selected = inst.id == selectedId,
                            compact = info.narrow,
                            onSelect = { onSelect(inst) },
                            onOpenFolder = { onOpenFolder(inst) },
                            onEdit = { onEdit(inst) },
                            onDelete = { onDelete(inst) },
                            onTogglePin = { onTogglePin(inst) }
                        )
                    }
                    item(key = "__add_row__") {
                        AddInstanceRow(onClick = onAdd)
                    }
                }
            }
        }
    }
}

private val RowShape = RoundedCornerShape(16.dp)

/** Satu baris instance. Nama dan keterangan satu baris (dipotong bila panjang) supaya semua baris rata. */
@Composable
private fun InstanceRow(
    inst: Instance,
    selected: Boolean,
    compact: Boolean,
    onSelect: () -> Unit,
    onOpenFolder: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RowShape)
            .then(
                if (selected) {
                    Modifier
                        .background(AmberUi.Accent.copy(alpha = 0.40f), RowShape)
                        .border(1.dp, AmberUi.AccentEdge, RowShape)
                } else {
                    Modifier.amberGlass(RowShape)
                }
            )
            .clickable { onSelect() }
            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioDot(selected)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = inst.name,
                color = AmberUi.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = ForgeFlavor.label(inst.loader) + " \u00b7 " + inst.mcVersion,
                color = AmberUi.TextDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // Di layar sempit (HP portrait) tombolnya sedikit lebih kecil supaya nama instance tetap terbaca
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
            PinRoundButton(inst.pinned, compact, onTogglePin)
            RowRoundButton(Icons.Outlined.Folder, "Buka folder", AmberUi.Text, onOpenFolder, compact)
            RowRoundButton(Icons.Outlined.Tune, "Ubah instance", AmberUi.Text, onEdit, compact)
            RowRoundButton(Icons.Outlined.DeleteOutline, "Hapus instance", AmberUi.Danger, onDelete, compact)
        }
    }
}

/** Lingkaran pilihan: cincin tipis, terisi titik bila instance ini yang aktif. */
@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .border(2.dp, if (selected) AmberUi.Text else AmberUi.BorderStrong, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(AmberUi.Text, CircleShape)
            )
        }
    }
}

/** Tombol bulat kecil di ujung kanan baris. */
@Composable
private fun RowRoundButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    val box: Dp = if (compact) 32.dp else 36.dp
    Box(
        modifier = Modifier
            .size(box)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
        )
    }
}

/**
 * Tombol sematkan: peniti terisi terang bila instance disematkan, hanya garis tepi redup bila belum. Peniti
 * digambar langsung dari garis (tanpa ikon tambahan), jadi tidak menambah ukuran aplikasi.
 */
@Composable
private fun PinRoundButton(
    pinned: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val box: Dp = if (compact) 32.dp else 36.dp
    val glyph = AmberUi.Text.copy(alpha = if (pinned) 1f else 0.62f)
    val description = Tr.t(if (pinned) "Lepas sematan" else "Sematkan instance")
    Box(
        modifier = Modifier
            .size(box)
            .clip(CircleShape)
            .background(if (pinned) AmberUi.Accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(if (compact) 16.dp else 18.dp)) {
            val u = size.minDimension / 24f
            // Peniti tegak: kepala melebar di atas, badan, lalu jarum ke bawah
            val path = Path().apply {
                moveTo(7f * u, 2f * u)
                lineTo(17f * u, 2f * u)
                lineTo(17f * u, 4.5f * u)
                lineTo(16f * u, 4.5f * u)
                lineTo(16f * u, 9f * u)
                lineTo(19f * u, 12f * u)
                lineTo(19f * u, 14f * u)
                lineTo(13f * u, 14f * u)
                lineTo(13f * u, 20.5f * u)
                lineTo(12f * u, 22.5f * u)
                lineTo(11f * u, 20.5f * u)
                lineTo(11f * u, 14f * u)
                lineTo(5f * u, 14f * u)
                lineTo(5f * u, 12f * u)
                lineTo(8f * u, 9f * u)
                lineTo(8f * u, 4.5f * u)
                lineTo(7f * u, 4.5f * u)
                close()
            }
            if (pinned) {
                drawPath(path = path, color = glyph)
            } else {
                drawPath(path = path, color = glyph, style = Stroke(width = 1.8f * u, join = StrokeJoin.Round))
            }
        }
    }
}

/** Baris putus-putus di ujung daftar untuk menambah instance. */
@Composable
private fun AddInstanceRow(onClick: () -> Unit) {
    val dash = AmberUi.BorderStrong
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RowShape)
            .clickable { onClick() }
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
                    )
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(AmberUi.AccentBrush, CircleShape)
                .border(1.dp, AmberUi.AccentEdge, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = AmberUi.Text,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "TAMBAH INSTANCE",
            color = AmberUi.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            maxLines = 1
        )
    }
}
