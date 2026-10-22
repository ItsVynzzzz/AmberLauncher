package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.models.InstalledModItem
import com.israadev.nuxlauncher.core.mods.NuxModManager
import com.israadev.nuxlauncher.ui.components.ModIcon
import com.israadev.nuxlauncher.ui.components.NuxDialog
import com.israadev.nuxlauncher.ui.components.NuxIconButton
import com.israadev.nuxlauncher.ui.components.SettingChip
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes

/**
 * "Lihat mod": tampilan bersih isi satu instance. Kotak-kotak kecil dengan gambar, nama, dan versi
 * tiap mod (dibaca dari filenya, tanpa internet). Hanya untuk dilihat; memasang dan menghapus tetap
 * lewat menu Mod di sidebar.
 */
@Composable
fun NuxInstalledModsDialog(
    instance: Instance,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf("mods") }
    var loaded by remember { mutableStateOf<Map<String, List<InstalledModItem>>>(emptyMap()) }

    LaunchedEffect(instance.id, type) {
        if (!loaded.containsKey(type)) {
            val result = NuxModManager.getInstalledItems(context, instance, type)
            loaded = loaded + (type to result)
        }
    }

    val tabs = listOf(
        "mods" to "Mod",
        "resourcepacks" to "Resource",
        "shaderpacks" to "Shader"
    )
    val shown = loaded[type]

    NuxDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 700.dp),
        fillMaxHeight = true,
        compact = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // Judul + tutup
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mod terpasang",
                        color = NuxColors.DarkGray,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        text = "${instance.name} · ${instance.mcVersion} · ${instance.loader.uppercase()}",
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

            Spacer(modifier = Modifier.height(10.dp))

            // Jenis: Mod / Resource / Shader
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEach { (id, label) ->
                    val count = loaded[id]?.size
                    SettingChip(
                        text = if (count != null) "$label $count" else label,
                        selected = type == id,
                        onClick = { type = id }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (shown == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = NuxColors.Yellow,
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                } else if (shown.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Extension,
                            contentDescription = null,
                            tint = NuxColors.GrayNeutral,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (type) {
                                "resourcepacks" -> "Belum ada resource pack"
                                "shaderpacks" -> "Belum ada shader"
                                else -> "Belum ada mod terpasang"
                            },
                            color = NuxColors.DarkGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Pasang lewat menu Mod di sidebar.",
                            color = NuxColors.GrayNeutral,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 190.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(shown, key = { it.originalFilename }) { item ->
                            InstalledModTile(item)
                        }
                    }
                }
            }
        }
    }
}

/** Satu kotak mod: gambar di kiri, nama di atas dan versi di bawahnya. */
@Composable
private fun InstalledModTile(item: InstalledModItem) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NuxColors.SurfaceWhite, shape)
            .border(NuxSizes.GlassBorder, NuxColors.CardBorder, shape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModIcon(
            item = item,
            size = 40.dp,
            dimmed = !item.isEnabled
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = if (item.isEnabled) NuxColors.DarkGray else NuxColors.GrayNeutral,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (item.isEnabled) item.version else "Nonaktif · ${item.version}",
                color = NuxColors.GrayNeutral,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
