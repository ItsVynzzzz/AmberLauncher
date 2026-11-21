package com.israadev.nuxlauncher.ui.dialogs

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.ui.components.AmberActionPill
import com.israadev.nuxlauncher.ui.components.AmberPage
import com.israadev.nuxlauncher.ui.components.AmberPageLabel
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.theme.AmberUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Pemakaian ruang satu instance, dipecah per kelompok folder tingkat atas. Semua dalam byte. */
private class StorageUsage(
    val worlds: Long,
    val mods: Long,
    val packs: Long,
    val logs: Long,
    /** Bagian [logs] yang aman dihapus: semua log lama dan laporan crash, kecuali latest.log. */
    val cleanable: Long,
    val other: Long
) {
    val total: Long get() = worlds + mods + packs + logs + other
}

/** Ukuran berkas atau folder. Tautan simbolik tidak diikuti, jadi tidak bisa berputar atau keluar dari instance. */
private fun sizeOf(file: File): Long {
    if (!file.isDirectory) return file.length()
    var total = 0L
    try {
        Files.walkFileTree(file.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                total += attrs.size()
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
    } catch (_: Exception) {
        // Sebagian ukuran tetap berguna; yang tidak terbaca dilewati.
    }
    return total
}

private fun scanUsage(gameDir: File): StorageUsage {
    var worlds = 0L
    var mods = 0L
    var packs = 0L
    var logs = 0L
    var other = 0L
    gameDir.listFiles()?.forEach { child ->
        val size = sizeOf(child)
        when (child.name) {
            "saves" -> worlds += size
            "mods" -> mods += size
            "resourcepacks", "shaderpacks" -> packs += size
            "logs", "crash-reports" -> logs += size
            else -> other += size
        }
    }
    val keep = sizeOf(File(gameDir, "logs/latest.log"))
    return StorageUsage(worlds, mods, packs, logs, (logs - keep).coerceAtLeast(0L), other)
}

/** Menghapus log lama dan laporan crash (latest.log dibiarkan). Mengembalikan jumlah byte yang dibebaskan. */
private fun clearOldLogs(gameDir: File): Long {
    var freed = 0L
    for (name in listOf("logs", "crash-reports")) {
        File(gameDir, name).listFiles()?.forEach { f ->
            if (name == "logs" && f.name == "latest.log") return@forEach
            val size = sizeOf(f)
            if (f.deleteRecursively()) freed += size
        }
    }
    return freed
}

/**
 * Rincian ruang yang dipakai satu instance (dunia, mod, resource/shader pack, log) dan satu aksi aman untuk
 * membebaskan ruang: hapus log lama dan laporan crash. Dunia dan mod tidak pernah dihapus dari sini.
 *
 * Penghitungan menelusuri folder instance, jadi dijalankan di thread latar dan hasilnya muncul beberapa saat kemudian.
 */
@Composable
fun NuxInstanceStorageDialog(
    instance: Instance,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gameDir = remember(instance.id) { InstanceManager.getInstanceGameDir(context, instance) }
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(gameDir, reload) {
        usage = withContext(Dispatchers.IO) { scanUsage(gameDir) }
    }

    fun size(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    AmberPage(
        onDismiss = onDismiss,
        title = "Penyimpanan instance",
        icon = Icons.Outlined.Storage,
        subtitle = instance.name
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard {
                AmberPageLabel("RUANG DIPAKAI")
                val u = usage
                if (u == null) {
                    Text(
                        text = "Menghitung...",
                        color = AmberUi.TextDim,
                        fontSize = 13.sp
                    )
                } else {
                    UsageRow(label = "Total", value = size(u.total), bold = true)
                    Spacer(modifier = Modifier.height(6.dp))
                    UsageRow(label = "Dunia", value = size(u.worlds))
                    UsageRow(label = "Mod", value = size(u.mods))
                    UsageRow(label = "Resource & shader pack", value = size(u.packs))
                    UsageRow(label = "Log & laporan crash", value = size(u.logs))
                    UsageRow(label = "Lainnya", value = size(u.other))
                }
            }

            val u = usage
            if (u != null && u.cleanable > 0L) {
                AmberActionPill(
                    icon = Icons.Outlined.DeleteOutline,
                    label = if (confirmClear) "Ketuk lagi untuk menghapus" else "Hapus log lama & laporan crash",
                    trailing = size(u.cleanable),
                    danger = confirmClear,
                    onClick = {
                        if (busy) return@AmberActionPill
                        if (!confirmClear) {
                            confirmClear = true
                        } else {
                            busy = true
                            scope.launch {
                                val freed = withContext(Dispatchers.IO) { clearOldLogs(gameDir) }
                                busy = false
                                confirmClear = false
                                reload++
                                Toast.makeText(context, "Dibebaskan ${size(freed)}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
            Text(
                text = "Dunia dan mod tidak pernah dihapus dari sini. Yang dihapus hanya log lama dan laporan crash; latest.log tetap ada.",
                color = AmberUi.TextDim,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun UsageRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (bold) AmberUi.Text else AmberUi.TextDim,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = AmberUi.Text,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}
