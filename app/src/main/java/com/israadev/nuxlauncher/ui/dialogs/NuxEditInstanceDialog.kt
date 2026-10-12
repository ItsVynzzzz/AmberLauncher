package com.israadev.nuxlauncher.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.mods.NuxModManager
import com.israadev.nuxlauncher.core.runtime.JavaRuntimeManager
import com.israadev.nuxlauncher.ui.components.NuxButton
import com.israadev.nuxlauncher.ui.components.NuxDialog
import com.israadev.nuxlauncher.ui.components.NuxIconButton
import com.israadev.nuxlauncher.ui.components.NuxTextField
import com.israadev.nuxlauncher.ui.components.SettingChip
import com.israadev.nuxlauncher.ui.components.SettingDivider
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dialog "Ubah instance" (tombol slider di Home): nama, versi Java, lalu tiga baris aksi
 * (Lihat mod, Buka folder, Hapus). Tampilan dibuat lega dan sedikit isi supaya tidak padat.
 */
@Composable
fun NuxEditInstanceDialog(
    instance: Instance,
    onDismiss: () -> Unit,
    onInstanceUpdated: (Instance) -> Unit,
    onInstanceDeleted: (Instance) -> Unit
) {
    val context = LocalContext.current
    var editName by remember { mutableStateOf(instance.name) }
    var selectedJavaRuntime by remember { mutableStateOf(instance.javaRuntime) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMods by remember { mutableStateOf(false) }
    var modCount by remember { mutableStateOf<Int?>(null) }

    val autoRecommendedRuntime = remember(instance.mcVersion) {
        JavaRuntimeManager.getRecommendedRuntime(instance.mcVersion)
    }

    // Jumlah mod hanya dihitung dari nama file (cepat), tanpa membuka isi jar.
    LaunchedEffect(instance.id) {
        modCount = withContext(Dispatchers.IO) {
            val files = NuxModManager.getTargetDir(context, instance, "mods").listFiles()
            files?.count { f ->
                f.isFile && (f.name.endsWith(".jar", true) || f.name.endsWith(".jar.disabled", true))
            } ?: 0
        }
    }

    val runtimeOptions = listOf(
        "auto" to "Auto (${autoRecommendedRuntime.replace("jre-", "Java ")})",
        "jre-8" to "Java 8",
        "jre-17" to "Java 17",
        "jre-21" to "Java 21",
        "jre-25" to "Java 25"
    )

    NuxDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 460.dp),
        compact = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // Judul + tutup
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ubah instance",
                        color = NuxColors.DarkGray,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        text = "MC ${instance.mcVersion} · ${instance.loader.uppercase()}" +
                            if (instance.loaderVersion.isNotBlank()) " · ${instance.loaderVersion}" else "",
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column {
                    EditFieldLabel("Nama")
                    Spacer(modifier = Modifier.height(4.dp))
                    NuxTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        placeholder = "Nama instance"
                    )
                }

                Column {
                    EditFieldLabel("Java")
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        runtimeOptions.forEach { (key, label) ->
                            SettingChip(
                                text = label,
                                selected = selectedJavaRuntime == key,
                                onClick = { selectedJavaRuntime = key }
                            )
                        }
                    }
                }

                // Aksi: Lihat mod, Buka folder, Hapus
                val groupShape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(groupShape)
                        .background(NuxColors.SurfaceWhite, groupShape)
                        .border(NuxSizes.GlassBorder, NuxColors.CardBorder, groupShape)
                ) {
                    EditActionRow(
                        icon = Icons.Outlined.Extension,
                        label = "Lihat mod",
                        trailing = modCount?.let { "$it terpasang" },
                        showChevron = true,
                        onClick = { showMods = true }
                    )
                    SettingDivider()
                    EditActionRow(
                        icon = Icons.Outlined.Folder,
                        label = "Buka folder instance",
                        showChevron = true,
                        onClick = {
                            Toast.makeText(context, "Membuka folder instance...", Toast.LENGTH_SHORT).show()
                            InstanceManager.openInstanceFolder(context, instance)
                        }
                    )
                    SettingDivider()
                    if (!showDeleteConfirm) {
                        EditActionRow(
                            icon = Icons.Outlined.DeleteOutline,
                            label = "Hapus instance",
                            danger = true,
                            onClick = { showDeleteConfirm = true }
                        )
                    } else {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                text = "Hapus '${instance.name}'?",
                                color = NuxColors.ErrorRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Semua world, mod, dan file instance dihapus permanen.",
                                color = NuxColors.GrayNeutral,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                NuxButton(
                                    onClick = { showDeleteConfirm = false },
                                    backgroundColor = Color.Transparent,
                                    cornerRadius = 20.dp,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Batal", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                NuxButton(
                                    onClick = {
                                        onInstanceDeleted(instance)
                                        onDismiss()
                                    },
                                    backgroundColor = NuxColors.ErrorRed,
                                    contentColor = NuxColors.DarkGray,
                                    cornerRadius = 20.dp,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Ya, hapus", fontWeight = FontWeight.Black, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Batal / Simpan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NuxButton(
                    onClick = onDismiss,
                    backgroundColor = Color.Transparent,
                    cornerRadius = 20.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Batal", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                NuxButton(
                    onClick = {
                        val trimmed = editName.trim()
                        if (trimmed.isBlank()) {
                            Toast.makeText(context, "Nama instance tidak boleh kosong", Toast.LENGTH_SHORT).show()
                            return@NuxButton
                        }
                        val updated = instance.copy(
                            name = trimmed,
                            javaRuntime = selectedJavaRuntime
                        )
                        onInstanceUpdated(updated)
                        Toast.makeText(context, "Instance berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    backgroundColor = NuxColors.Yellow,
                    cornerRadius = 20.dp,
                    modifier = Modifier.weight(1.4f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simpan", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }

    if (showMods) {
        NuxInstalledModsDialog(
            instance = instance,
            onDismiss = { showMods = false }
        )
    }
}

@Composable
private fun EditFieldLabel(text: String) {
    Text(
        text = text,
        color = NuxColors.GrayNeutral,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
    )
}

/** Satu baris aksi: ikon, nama, keterangan kecil di kanan, dan panah bila membuka halaman lain. */
@Composable
private fun EditActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailing: String? = null,
    showChevron: Boolean = false,
    danger: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (danger) NuxColors.ErrorRed else NuxColors.Yellow,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = if (danger) NuxColors.ErrorRed else NuxColors.DarkGray,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = NuxColors.GrayNeutral,
                fontSize = 12.sp
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = NuxColors.GrayNeutral,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
