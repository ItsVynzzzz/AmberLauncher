package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.forge.ForgeFlavor
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.ui.components.AmberPage
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.israadev.nuxlauncher.ui.theme.amberGlass

/**
 * Pemilih instance berbentuk rak kartu (seperti perpustakaan game), bukan daftar di dalam kotak.
 *
 * Tiap instance adalah kartu kaca: label loader, nama, versi, dan tiga tombol bulat (buka folder,
 * ubah, hapus). Yang aktif diberi isian aksen dan centang. Kartu putus-putus di ujung untuk menambah
 * instance, dan tombol + di bar atas supaya tetap terjangkau saat instance banyak.
 *
 * Rak hanya setinggi kartu dan digulir ke samping, jadi tinggi layar sekecil apa pun tidak memotong
 * isinya, dan tidak ada tombol di dasar layar yang bisa tertutup bar sistem. Ketuk area gelap di
 * luar rak untuk menutup.
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
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    // Buka dengan instance aktif sudah terlihat, bukan selalu dari kartu pertama.
    LaunchedEffect(Unit) {
        val index = instances.indexOfFirst { it.id == selectedId }
        if (index > 0) listState.scrollToItem(index)
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
                    .background(AmberUi.Accent, CircleShape)
                    .border(1.dp, AmberUi.AccentEdge, CircleShape)
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "TAMBAH INSTANCE",
                    tint = AmberUi.Text,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { _ ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (instances.isEmpty()) {
                Column(
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Belum ada instance. Buat yang pertama di bawah.",
                        color = AmberUi.TextDim,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AddInstanceCard(onClick = onAdd)
                }
            } else {
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Celah di antara kartu tidak ikut menutup dialog
                        .pointerInput(Unit) { detectTapGestures { } },
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    items(instances, key = { it.id }) { inst ->
                        InstanceShelfCard(
                            inst = inst,
                            selected = inst.id == selectedId,
                            onSelect = { onSelect(inst) },
                            onOpenFolder = { onOpenFolder(inst) },
                            onEdit = { onEdit(inst) },
                            onDelete = { onDelete(inst) }
                        )
                    }
                    item(key = "__add_card__") {
                        AddInstanceCard(onClick = onAdd)
                    }
                }
            }
        }
    }
}

private val ShelfCardShape = RoundedCornerShape(26.dp)

/** Satu kartu instance. Tinggi tetap supaya semua kartu rata walau nama panjang. */
@Composable
private fun InstanceShelfCard(
    inst: Instance,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpenFolder: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(184.dp)
            .height(176.dp)
            .clip(ShelfCardShape)
            .then(
                if (selected) {
                    Modifier
                        .background(AmberUi.Accent.copy(alpha = 0.62f), ShelfCardShape)
                        .border(1.5.dp, AmberUi.AccentEdge, ShelfCardShape)
                } else {
                    Modifier.amberGlass(ShelfCardShape)
                }
            )
            .clickable { onSelect() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Label loader (ruang di kanan disisakan untuk tanda centang)
                Box(
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = ForgeFlavor.label(inst.loader).uppercase(),
                        color = AmberUi.AccentSoft,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = inst.name,
                    color = AmberUi.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 21.sp,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = inst.mcVersion,
                    color = AmberUi.TextDim,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShelfRoundButton(Icons.Outlined.Folder, "Buka folder", AmberUi.Text, onOpenFolder)
                ShelfRoundButton(Icons.Outlined.Tune, "Ubah instance", AmberUi.Text, onEdit)
                ShelfRoundButton(Icons.Outlined.DeleteOutline, "Hapus instance", AmberUi.Danger, onDelete)
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = AmberUi.Text,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .size(20.dp)
            )
        }
    }
}

/** Tombol bulat kecil di dasar kartu. */
@Composable
private fun ShelfRoundButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable { onClick() },
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

/** Kartu putus-putus di ujung rak untuk menambah instance. */
@Composable
private fun AddInstanceCard(onClick: () -> Unit) {
    val dash = AmberUi.BorderStrong
    Column(
        modifier = Modifier
            .width(150.dp)
            .height(176.dp)
            .clip(ShelfCardShape)
            .clickable { onClick() }
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(26.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
                    )
                )
            }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AmberUi.Accent, CircleShape)
                .border(1.dp, AmberUi.AccentEdge, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = AmberUi.Text,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "TAMBAH INSTANCE",
            color = AmberUi.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
