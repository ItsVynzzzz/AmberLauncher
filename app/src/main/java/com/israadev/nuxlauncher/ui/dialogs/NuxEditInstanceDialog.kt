package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.mods.NuxModManager
import com.israadev.nuxlauncher.core.runtime.JavaRuntimeManager
import com.israadev.nuxlauncher.ui.components.AmberSheet
import com.israadev.nuxlauncher.ui.components.NuxTextField
import com.israadev.nuxlauncher.ui.components.SettingChip
import com.israadev.nuxlauncher.ui.components.SheetActionRow
import com.israadev.nuxlauncher.ui.components.SheetButton
import com.israadev.nuxlauncher.ui.components.SheetDivider
import com.israadev.nuxlauncher.ui.components.SheetGroup
import com.israadev.nuxlauncher.ui.components.SheetLabel
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.theme.NuxColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dialog "Ubah instance" (tombol pensil di Home): nama, versi Java, lalu aksi (Mod & konten,
 * Buka folder) dan zona hapus yang dipisah.
 *
 * Dibangun di atas [AmberSheet]: judul dan tombol Batal/Simpan selalu terlihat, tidak tertutup bar
 * sistem, dan hanya bagian tengah yang digulir. Di HP landscape yang pendek isi dibagi dua kolom
 * (kiri: nama + Java, kanan: aksi) supaya hampir tidak perlu menggulir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NuxEditInstanceDialog(
    instance: Instance,
    onDismiss: () -> Unit,
    onInstanceUpdated: (Instance) -> Unit,
    onInstanceDeleted: (Instance) -> Unit,
    onOpenContent: () -> Unit
) {
    val context = LocalContext.current
    var editName by remember { mutableStateOf(instance.name) }
    var selectedJavaRuntime by remember { mutableStateOf(instance.javaRuntime) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
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

    val subtitle = "MC ${instance.mcVersion} · ${instance.loader.uppercase()}" +
        if (instance.loaderVersion.isNotBlank()) " · ${instance.loaderVersion}" else ""

    val save = {
        val trimmed = editName.trim()
        if (trimmed.isBlank()) {
            Toast.makeText(context, "Nama instance tidak boleh kosong", Toast.LENGTH_SHORT).show()
        } else {
            val updated = instance.copy(
                name = trimmed,
                javaRuntime = selectedJavaRuntime
            )
            onInstanceUpdated(updated)
            Toast.makeText(context, "Instance berhasil diperbarui!", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    // Bagian kiri: identitas yang bisa diubah (nama dan versi Java)
    val identity: @Composable () -> Unit = {
        Column {
            SheetLabel("Nama")
            NuxTextField(
                value = editName,
                onValueChange = { editName = it },
                placeholder = "Nama instance"
            )
        }
        Column {
            SheetLabel("Java")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
    }

    // Bagian kanan: aksi, lalu zona hapus yang dipisah supaya tidak tersentuh tak sengaja
    val actions: @Composable () -> Unit = {
        SheetGroup {
            SheetActionRow(
                icon = Icons.Outlined.Extension,
                label = "Mod & konten",
                trailing = modCount?.let { "$it terpasang" },
                showChevron = true,
                onClick = onOpenContent
            )
            SheetDivider()
            SheetActionRow(
                icon = Icons.Outlined.Folder,
                label = "Buka folder instance",
                showChevron = true,
                onClick = {
                    Toast.makeText(context, "Membuka folder instance...", Toast.LENGTH_SHORT).show()
                    InstanceManager.openInstanceFolder(context, instance)
                }
            )
        }

        SheetGroup(borderColor = NuxColors.ErrorRed.copy(alpha = 0.40f)) {
            if (!showDeleteConfirm) {
                SheetActionRow(
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
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SheetButton(
                            text = "Batal",
                            onClick = { showDeleteConfirm = false },
                            modifier = Modifier.weight(1f)
                        )
                        SheetButton(
                            text = "Ya, hapus",
                            onClick = {
                                onInstanceDeleted(instance)
                                onDismiss()
                            },
                            danger = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    AmberSheet(
        onDismiss = onDismiss,
        title = "Ubah instance",
        subtitle = subtitle,
        icon = Icons.Outlined.Tune,
        panelWidth = 640.dp,
        // Ada isian yang belum disimpan: jangan tutup hanya karena salah ketuk area gelap.
        dismissOnScrimTap = false,
        footer = {
            SheetButton(
                text = "Batal",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            SheetButton(
                text = "Simpan",
                onClick = { save() },
                primary = true,
                icon = Icons.Outlined.Check,
                modifier = Modifier.weight(1.4f)
            )
        }
    ) { info ->
        if (info.twoPane) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) { identity() }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) { actions() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                identity()
                actions()
            }
        }
    }
}
