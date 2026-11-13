package com.israadev.nuxlauncher.ui.dialogs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.controls.ControlLayoutIO
import com.israadev.nuxlauncher.core.controls.ControlLayoutManager
import com.israadev.nuxlauncher.core.controls.ControlPresetManager
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import com.israadev.nuxlauncher.ui.components.AmberAlert
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.components.NuxTextField
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.i18n.Tr
import com.israadev.nuxlauncher.ui.theme.Amber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Preset yang sedang menunggu persetujuan untuk dipakai, lengkap dengan tombolnya yang sudah dibaca. */
private class PendingApply(val preset: ControlPresetManager.Preset, val buttons: List<CustomControlButton>)

/**
 * Preset layout kontrol: simpan layout yang sedang dipakai dengan nama, impor berkas (Amber, Pojav, Zalith, Mojo,
 * Zalith 2) sebagai preset tanpa mengganti layout sekarang, lalu pasang preset mana pun dengan satu ketukan.
 * Memasang preset menyimpan dulu layout yang diganti, jadi bisa dikembalikan lewat tombol "Kembalikan layout
 * sebelumnya". Semua pekerjaan berkas dikerjakan di thread IO.
 */
@Composable
fun NuxControlPresetsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dir = remember { ControlPresetManager.dir(context) }

    var presets by remember { mutableStateOf<List<ControlPresetManager.Preset>>(emptyList()) }
    var hasPrevious by remember { mutableStateOf(false) }
    var missingBuiltins by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingApply?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun reload() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                // Preset bawaan disalin sekali; yang sudah dihapus pengguna tidak muncul lagi
                ControlPresetManager.ensureBuiltins(dir, ControlLayoutManager.getDefaultButtons())
                Triple(
                    ControlPresetManager.list(dir),
                    ControlPresetManager.hasPrevious(dir),
                    ControlPresetManager.missingBuiltins(dir)
                )
            }
            presets = loaded.first
            hasPrevious = loaded.second
            missingBuiltins = loaded.third
        }
    }

    LaunchedEffect(Unit) { reload() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                val message = withContext(Dispatchers.IO) { importAsPreset(context, dir, uri) }
                busy = false
                toast(message)
                reload()
            }
        }
    }

    fun saveCurrent() {
        val clean = ControlPresetManager.cleanName(name)
        if (clean.isEmpty()) {
            toast("Nama preset tidak boleh kosong")
            return
        }
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                ControlPresetManager.saveJson(dir, clean, ControlLayoutManager.exportJson(context))
            }
            busy = false
            if (result is ControlPresetManager.SaveResult.Saved) name = ""
            toast(saveMessage(result))
            reload()
        }
    }

    fun doRename(id: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ControlPresetManager.rename(dir, id, name) }
            toast(if (ok) "Nama preset diubah" else "Gagal mengubah nama preset")
            if (ok) {
                renamingId = null
                name = ""
            }
            reload()
        }
    }

    fun askApply(preset: ControlPresetManager.Preset) {
        scope.launch {
            val buttons = withContext(Dispatchers.IO) { ControlPresetManager.read(dir, preset.id) }
            if (buttons == null) {
                toast("Preset tidak bisa dibaca")
                reload()
            } else {
                pending = PendingApply(preset, buttons)
            }
        }
    }

    fun deletePreset(id: String) {
        scope.launch {
            withContext(Dispatchers.IO) { ControlPresetManager.delete(dir, id) }
            confirmDeleteId = null
            if (renamingId == id) {
                renamingId = null
                name = ""
            }
            toast("Preset dihapus")
            reload()
        }
    }

    fun restoreBuiltins() {
        scope.launch {
            val restored = withContext(Dispatchers.IO) {
                ControlPresetManager.restoreBuiltins(dir, ControlLayoutManager.getDefaultButtons())
            }
            if (restored > 0) toast("Preset bawaan dipulihkan")
            reload()
        }
    }

    fun restorePrevious() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val previous = ControlPresetManager.readPrevious(dir) ?: return@withContext false
                ControlPresetManager.savePrevious(dir, ControlLayoutManager.exportJson(context))
                ControlLayoutManager.importLayout(context, previous)
                true
            }
            toast(if (ok) "Layout sebelumnya dikembalikan" else "Preset tidak bisa dibaca")
            reload()
        }
    }

    AmberAlert(
        onDismissRequest = onDismiss,
        title = "Preset kontrol",
        icon = "gear",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Tutup",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
        }
    ) {
        Text(
            text = "Simpan beberapa layout kontrol, lalu ganti kapan saja dengan satu ketukan. Layout yang sedang dipakai disimpan otomatis sebelum diganti.",
            color = Amber.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        NuxTextField(
            value = name,
            onValueChange = { name = it.take(ControlPresetManager.MAX_NAME) },
            placeholder = Tr.t("Nama preset")
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmberButton(
                text = if (renamingId != null) "Simpan nama" else "Simpan layout saat ini",
                onClick = {
                    val id = renamingId
                    if (id != null) doRename(id) else saveCurrent()
                },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Primary,
                enabled = !busy,
                height = 40.dp
            )
            if (renamingId != null) {
                AmberButton(
                    text = "Batal",
                    onClick = {
                        renamingId = null
                        name = ""
                    },
                    modifier = Modifier.weight(0.6f),
                    style = AmberButtonStyle.Ghost,
                    height = 40.dp
                )
            } else {
                AmberButton(
                    text = "Impor berkas",
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(0.8f),
                    style = AmberButtonStyle.Ghost,
                    icon = "download",
                    enabled = !busy,
                    height = 40.dp
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (presets.isEmpty()) {
            Text(
                text = "Belum ada preset. Simpan layout saat ini, atau impor berkas dari Amber, Pojav, Zalith, atau Mojo.",
                color = Amber.TextDim,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (preset in presets) {
                    PresetRow(
                        preset = preset,
                        confirmingDelete = confirmDeleteId == preset.id,
                        enabled = !busy,
                        onApply = { askApply(preset) },
                        onRename = {
                            renamingId = preset.id
                            name = preset.name
                            confirmDeleteId = null
                        },
                        onDelete = {
                            if (confirmDeleteId == preset.id) {
                                deletePreset(preset.id)
                            } else {
                                confirmDeleteId = preset.id
                            }
                        }
                    )
                }
            }
        }
        if (missingBuiltins > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            AmberButton(
                text = "Pulihkan preset bawaan",
                onClick = { restoreBuiltins() },
                modifier = Modifier.fillMaxWidth(),
                style = AmberButtonStyle.Ghost,
                enabled = !busy,
                height = 40.dp
            )
        }
        if (hasPrevious) {
            Spacer(modifier = Modifier.height(12.dp))
            AmberButton(
                text = "Kembalikan layout sebelumnya",
                onClick = { restorePrevious() },
                modifier = Modifier.fillMaxWidth(),
                style = AmberButtonStyle.Ghost,
                enabled = !busy,
                height = 40.dp
            )
        }
    }

    val target = pending
    if (target != null) {
        val macros = target.buttons.count { it.isMacro && it.macroType == "COMMAND" && it.macroCommand.isNotBlank() }
        // 262..265 = tombol panah (kanan, kiri, bawah, atas): game hanya bergerak bila tombol geraknya diatur ke panah
        val usesArrows = target.buttons.any { !it.isMouseButton && it.keyCode in 262..265 }
        AmberAlert(
            onDismissRequest = { pending = null },
            title = "Pakai preset ini?",
            icon = "warning",
            accent = Amber.Yellow,
            actions = {
                AmberButton(
                    text = "Batal",
                    onClick = { pending = null },
                    modifier = Modifier.weight(1f),
                    style = AmberButtonStyle.Ghost
                )
                AmberButton(
                    text = "Ganti layout",
                    onClick = {
                        pending = null
                        scope.launch {
                            busy = true
                            withContext(Dispatchers.IO) {
                                ControlPresetManager.savePrevious(dir, ControlLayoutManager.exportJson(context))
                                ControlLayoutManager.importLayout(context, target.buttons)
                            }
                            busy = false
                            toast("Preset dipakai")
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = AmberButtonStyle.Primary,
                    icon = "download"
                )
            }
        ) {
            Text(
                text = "Layout tombol saat ini akan diganti dengan preset \"${target.preset.name}\" (${target.buttons.size} tombol). Layout saat ini disimpan dulu supaya bisa dikembalikan.",
                color = Amber.TextDim,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
            if (usesArrows) {
                Text(
                    text = "Preset ini memakai tombol panah. Atur tombol gerak di opsi Kontrol Minecraft ke tombol panah supaya karakter bergerak.",
                    color = Amber.TextDim,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            if (macros > 0) {
                Text(
                    text = "Preset ini berisi $macros tombol makro yang mengetik chat atau perintah otomatis. Periksa dulu sebelum dipakai di server.",
                    color = Amber.TextDim,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: ControlPresetManager.Preset,
    confirmingDelete: Boolean,
    enabled: Boolean,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Amber.Fill, shape)
            .border(1.dp, Amber.Line, shape)
            .padding(12.dp)
    ) {
        Text(
            text = preset.name,
            color = Amber.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Berisi ${preset.buttonCount} tombol",
            color = Amber.TextFaint,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AmberButton(
                text = "Pakai",
                onClick = onApply,
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Primary,
                enabled = enabled,
                height = 36.dp
            )
            AmberButton(
                text = "Ubah nama",
                onClick = onRename,
                modifier = Modifier.weight(1.2f),
                style = AmberButtonStyle.Ghost,
                enabled = enabled,
                height = 36.dp
            )
            AmberButton(
                text = if (confirmingDelete) "Yakin?" else "Hapus",
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                style = if (confirmingDelete) AmberButtonStyle.Danger else AmberButtonStyle.Ghost,
                icon = "trash",
                enabled = enabled,
                height = 36.dp
            )
        }
    }
}

private fun saveMessage(r: ControlPresetManager.SaveResult): String = when (r) {
    is ControlPresetManager.SaveResult.Saved -> "Preset disimpan"
    ControlPresetManager.SaveResult.EmptyName -> "Nama preset tidak boleh kosong"
    ControlPresetManager.SaveResult.TooMany ->
        "Terlalu banyak preset. Hapus salah satu dulu (maksimal ${ControlPresetManager.MAX_PRESETS})."
    ControlPresetManager.SaveResult.Invalid -> "Layout tidak bisa disimpan sebagai preset"
    ControlPresetManager.SaveResult.Failed -> "Gagal menyimpan preset"
}

/** Membaca berkas pilihan pengguna (format Amber, Pojav, Zalith, Mojo, atau Zalith 2) lalu menyimpannya sebagai preset. */
private fun importAsPreset(context: Context, dir: File, uri: Uri): String {
    return when (val r = ControlLayoutManager.readImport(context, uri)) {
        is ControlLayoutIO.ImportResult.Failed -> when (r.reason) {
            ControlLayoutIO.Reason.TOO_LARGE -> "Berkas terlalu besar untuk layout kontrol"
            ControlLayoutIO.Reason.NO_BUTTONS -> "Berkas tidak berisi tombol"
            ControlLayoutIO.Reason.NOT_LAYOUT -> "Berkas ini bukan layout kontrol yang valid"
        }
        is ControlLayoutIO.ImportResult.Ok ->
            saveMessage(ControlPresetManager.saveButtons(dir, displayName(context, uri), r.buttons))
    }
}

/** Nama berkas tanpa ekstensi, dipakai sebagai nama preset hasil impor. */
private fun displayName(context: Context, uri: Uri): String {
    var raw = ""
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) raw = c.getString(index) ?: ""
            }
        }
    } catch (_: Exception) {
    }
    val base = raw.substringBeforeLast('.')
    return base.ifBlank { "Impor" }
}
