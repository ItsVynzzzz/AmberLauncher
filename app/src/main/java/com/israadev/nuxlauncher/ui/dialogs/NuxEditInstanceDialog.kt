package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.mods.NuxModManager
import com.israadev.nuxlauncher.core.runtime.JavaRuntimeManager
import com.israadev.nuxlauncher.ui.components.AmberActionPill
import com.israadev.nuxlauncher.ui.components.AmberChoicePill
import com.israadev.nuxlauncher.ui.components.AmberField
import com.israadev.nuxlauncher.ui.components.AmberPage
import com.israadev.nuxlauncher.ui.components.AmberPageLabel
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.screens.AmberPillButton
import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.israadev.nuxlauncher.ui.theme.amberGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Halaman "Ubah instance" (tombol pensil di Home): nama, versi Java, aksi (Mod & konten, Buka
 * folder), dan Hapus.
 *
 * Bentuknya halaman kaca layar penuh seperti layar Mod/Akun/Pengaturan, bukan kotak di tengah:
 * bar atas melayang berisi panah kembali, judul, dan tombol Simpan; di bawahnya dua kolom (kiri
 * isian, kanan aksi berbentuk pil). Tidak ada tombol di dasar layar, jadi tidak bisa tertutup bar
 * sistem atau taskbar tablet. Di layar sempit (HP portrait) menjadi satu kolom yang bisa digulir.
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

    val save: () -> Unit = {
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

    // Isi kartu kiri: nama dan versi Java
    val identity: @Composable () -> Unit = {
        AmberPageLabel("Nama")
        AmberField(
            value = editName,
            onValueChange = { editName = it },
            placeholder = "Nama instance",
            icon = Icons.Outlined.Edit
        )
        Spacer(modifier = Modifier.height(16.dp))
        AmberPageLabel("Java")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            runtimeOptions.forEach { (key, label) ->
                AmberChoicePill(
                    text = label,
                    selected = selectedJavaRuntime == key,
                    onClick = { selectedJavaRuntime = key }
                )
            }
        }
    }

    // Kolom kanan: aksi berbentuk pil. Hapus dipisah paling bawah dan butuh konfirmasi.
    val actions: @Composable () -> Unit = {
        AmberActionPill(
            icon = Icons.Outlined.Extension,
            label = "Mod & konten",
            trailing = modCount?.let { "$it terpasang" },
            showChevron = true,
            onClick = onOpenContent
        )
        AmberActionPill(
            icon = Icons.Outlined.Folder,
            label = "Buka folder instance",
            showChevron = true,
            onClick = {
                Toast.makeText(context, "Membuka folder instance...", Toast.LENGTH_SHORT).show()
                InstanceManager.openInstanceFolder(context, instance)
            }
        )
        if (!showDeleteConfirm) {
            AmberActionPill(
                icon = Icons.Outlined.DeleteOutline,
                label = "Hapus instance",
                danger = true,
                onClick = { showDeleteConfirm = true }
            )
        } else {
            DeleteConfirmCard(
                name = instance.name,
                onCancel = { showDeleteConfirm = false },
                onConfirm = {
                    onInstanceDeleted(instance)
                    onDismiss()
                }
            )
        }
    }

    AmberPage(
        onDismiss = onDismiss,
        title = "Ubah instance",
        icon = Icons.Outlined.Tune,
        subtitle = subtitle,
        actions = {
            AmberPillButton(
                text = "Simpan",
                onClick = save,
                icon = Icons.Outlined.Check
            )
        }
    ) { info ->
        if (info.narrow) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EditCard { identity() }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    EditCard { identity() }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    actions()
                }
            }
        }
    }
}

/** Kartu kaca bersudut besar untuk satu kelompok isian. */
@Composable
private fun EditCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .amberGlass(shape)
            .padding(16.dp)
    ) {
        content()
    }
}

/** Konfirmasi hapus di tempat (menggantikan pil Hapus): pesan, lalu Batal dan Ya, hapus. */
@Composable
private fun DeleteConfirmCard(
    name: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AmberUi.Danger.copy(alpha = 0.10f), shape)
            .border(1.dp, AmberUi.Danger.copy(alpha = 0.55f), shape)
            .padding(14.dp)
    ) {
        Text(
            text = "Hapus '$name'?",
            color = AmberUi.Danger,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Semua world, mod, dan file instance dihapus permanen.",
            color = AmberUi.TextDim,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AmberPillButton(
                text = "Batal",
                onClick = onCancel,
                filled = false,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(AmberUi.Danger, CircleShape)
                    .clickable { onConfirm() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ya, hapus",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
}
