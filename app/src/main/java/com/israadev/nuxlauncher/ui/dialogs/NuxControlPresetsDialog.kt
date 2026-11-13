package com.israadev.nuxlauncher.ui.dialogs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
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
import com.israadev.nuxlauncher.ui.components.AmberIcon
import com.israadev.nuxlauncher.ui.components.NuxDialog
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

/** Keadaan folder preset yang dibaca sekali per muat ulang. */
private class DialogData(
    val presets: List<ControlPresetManager.Preset>,
    val hasPrevious: Boolean,
    val missingBuiltins: Int,
    /** Preset yang isinya sama persis dengan layout yang sedang dipakai, bila ada. */
    val activeId: String?,
    /** Preset yang sedang diedit di editor, bila ada. */
    val editingId: String?
)

/**
 * Preset layout kontrol: simpan layout yang sedang dipakai dengan nama, impor berkas (Amber, Pojav, Zalith, Mojo,
 * Zalith 2) sebagai preset tanpa mengganti layout sekarang, lalu pasang preset mana pun dengan satu ketukan.
 * Memasang preset menyimpan dulu layout yang diganti, jadi bisa dikembalikan lewat tombol "Kembalikan layout
 * sebelumnya". Aksi lain per preset (edit, ubah nama, gandakan, timpa dengan layout saat ini, ekspor, hapus) ada di
 * bawah tombol "Opsi". "Edit" memasang preset lalu membuka editor; perubahannya disimpan balik lewat kartu
 * "Mengedit preset" yang muncul saat dialog dibuka lagi.
 *
 * Tata letak mengikuti ruang layar yang tersedia: tinggi dialog dibatasi sehingga bagian atas (judul, nama, tombol
 * simpan) tetap terlihat dan hanya daftar yang bisa digulir, dan di layar landscape yang pendek keterangan
 * disembunyikan. Semua pekerjaan berkas dikerjakan di thread IO.
 */
@Composable
fun NuxControlPresetsDialog(onDismiss: () -> Unit, onOpenEditor: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dir = remember { ControlPresetManager.dir(context) }

    var presets by remember { mutableStateOf<List<ControlPresetManager.Preset>>(emptyList()) }
    var hasPrevious by remember { mutableStateOf(false) }
    var missingBuiltins by remember { mutableIntStateOf(0) }
    var activeId by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    var confirmOverwriteId by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingApply?>(null) }
    var exportId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun reload() {
        scope.launch {
            val data = withContext(Dispatchers.IO) { loadDialogData(context, dir) }
            presets = data.presets
            hasPrevious = data.hasPrevious
            missingBuiltins = data.missingBuiltins
            activeId = data.activeId
            editingId = data.editingId
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

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val id = exportId
        exportId = null
        if (uri != null && id != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { writePresetTo(context, dir, id, uri) }
                toast(if (ok) "Layout kontrol disimpan ke berkas" else "Gagal menyimpan berkas")
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
            expandedId = null
            if (renamingId == id) {
                renamingId = null
                name = ""
            }
            toast("Preset dihapus")
            reload()
        }
    }

    fun duplicatePreset(id: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { ControlPresetManager.duplicate(dir, id) }
            toast(if (result is ControlPresetManager.SaveResult.Saved) "Preset diduplikat" else saveMessage(result))
            reload()
        }
    }

    fun overwritePreset(id: String, finishEdit: Boolean) {
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) {
                val saved = ControlPresetManager.overwrite(dir, id, ControlLayoutManager.exportJson(context))
                if (saved && finishEdit) ControlPresetManager.clearEditing(dir)
                saved
            }
            busy = false
            confirmOverwriteId = null
            toast(if (ok) "Preset diperbarui" else "Gagal memperbarui preset")
            reload()
        }
    }

    fun finishEditing() {
        scope.launch {
            withContext(Dispatchers.IO) { ControlPresetManager.clearEditing(dir) }
            reload()
        }
    }

    fun startEdit(preset: ControlPresetManager.Preset) {
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) {
                val buttons = ControlPresetManager.read(dir, preset.id) ?: return@withContext false
                ControlPresetManager.savePrevious(dir, ControlLayoutManager.exportJson(context))
                ControlLayoutManager.importLayout(context, buttons)
                ControlPresetManager.setEditing(dir, preset.id)
            }
            busy = false
            if (ok) {
                Toast.makeText(
                    context,
                    "Setelah selesai mengedit, buka Preset lagi lalu pilih Simpan perubahan.",
                    Toast.LENGTH_LONG
                ).show()
                onDismiss()
                onOpenEditor()
            } else {
                toast("Preset tidak bisa dibaca")
                reload()
            }
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
                ControlPresetManager.clearEditing(dir)
                true
            }
            toast(if (ok) "Layout sebelumnya dikembalikan" else "Preset tidak bisa dibaca")
            reload()
        }
    }

    NuxDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 560.dp),
        compact = true
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Tinggi yang benar-benar tersedia di dalam dialog; cadangan bila induknya tak terbatas
            val fallbackHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
            val availableHeight = if (maxHeight.value.isFinite()) maxHeight else fallbackHeight
            val wide = maxWidth >= 480.dp
            val shortScreen = availableHeight < 420.dp
            val pad = if (shortScreen) 14.dp else 20.dp
            val editingPreset = presets.firstOrNull { it.id == editingId }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = availableHeight)
                    .padding(pad)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(if (shortScreen) 32.dp else 40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Amber.Yellow.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AmberIcon("gear", if (shortScreen) 18.dp else 22.dp, Amber.Yellow)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Preset kontrol",
                        color = Amber.Text,
                        fontSize = if (shortScreen) 17.sp else 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        AmberIcon("close", 20.dp, Amber.Text)
                    }
                }
                Spacer(modifier = Modifier.height(if (shortScreen) 8.dp else 12.dp))

                if (!shortScreen) {
                    Text(
                        text = "Simpan beberapa layout kontrol, lalu ganti kapan saja dengan satu ketukan. Layout yang sedang dipakai disimpan otomatis sebelum diganti.",
                        color = Amber.TextDim,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (editingPreset != null) {
                    val bannerShape = RoundedCornerShape(12.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Amber.Yellow.copy(alpha = 0.10f), bannerShape)
                            .border(1.dp, Amber.Yellow.copy(alpha = 0.5f), bannerShape)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Mengedit preset \"${editingPreset.name}\"",
                            color = Amber.Text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AmberButton(
                                text = "Simpan perubahan",
                                onClick = { overwritePreset(editingPreset.id, true) },
                                modifier = Modifier.weight(1.3f),
                                style = AmberButtonStyle.Primary,
                                enabled = !busy,
                                height = 36.dp
                            )
                            AmberButton(
                                text = "Selesai",
                                onClick = { finishEditing() },
                                modifier = Modifier.weight(0.7f),
                                style = AmberButtonStyle.Ghost,
                                height = 36.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                val renaming = renamingId != null
                val onPrimary: () -> Unit = {
                    val id = renamingId
                    if (id != null) doRename(id) else saveCurrent()
                }
                val onCancelRename: () -> Unit = {
                    renamingId = null
                    name = ""
                }
                if (wide) {
                    // Satu baris: nama, simpan, impor
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NuxTextField(
                            value = name,
                            onValueChange = { name = it.take(ControlPresetManager.MAX_NAME) },
                            placeholder = Tr.t("Nama preset"),
                            modifier = Modifier.weight(1f)
                        )
                        AmberButton(
                            text = if (renaming) "Simpan nama" else "Simpan",
                            onClick = onPrimary,
                            style = AmberButtonStyle.Primary,
                            enabled = !busy,
                            height = 42.dp
                        )
                        if (renaming) {
                            AmberButton(
                                text = "Batal",
                                onClick = onCancelRename,
                                style = AmberButtonStyle.Ghost,
                                height = 42.dp
                            )
                        } else {
                            AmberButton(
                                text = "Impor",
                                onClick = { importLauncher.launch(arrayOf("*/*")) },
                                style = AmberButtonStyle.Ghost,
                                icon = "download",
                                enabled = !busy,
                                height = 42.dp
                            )
                        }
                    }
                } else {
                    // Layar sempit: nama di atas, tombol di bawahnya
                    NuxTextField(
                        value = name,
                        onValueChange = { name = it.take(ControlPresetManager.MAX_NAME) },
                        placeholder = Tr.t("Nama preset")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmberButton(
                            text = if (renaming) "Simpan nama" else "Simpan layout saat ini",
                            onClick = onPrimary,
                            modifier = Modifier.weight(1f),
                            style = AmberButtonStyle.Primary,
                            enabled = !busy,
                            height = 40.dp
                        )
                        if (renaming) {
                            AmberButton(
                                text = "Batal",
                                onClick = onCancelRename,
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
                }
                Spacer(modifier = Modifier.height(if (shortScreen) 8.dp else 12.dp))

                // Hanya bagian ini yang bergulir; sisanya tetap terlihat
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (presets.isEmpty()) {
                        Text(
                            text = "Belum ada preset. Simpan layout saat ini, atau impor berkas dari Amber, Pojav, Zalith, atau Mojo.",
                            color = Amber.TextDim,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                    for (preset in presets) {
                        PresetRow(
                            preset = preset,
                            active = activeId == preset.id,
                            expanded = expandedId == preset.id,
                            enabled = !busy,
                            confirmingDelete = confirmDeleteId == preset.id,
                            confirmingOverwrite = confirmOverwriteId == preset.id,
                            onApply = { askApply(preset) },
                            onToggle = {
                                expandedId = if (expandedId == preset.id) null else preset.id
                                confirmDeleteId = null
                                confirmOverwriteId = null
                            },
                            onEdit = { startEdit(preset) },
                            onRename = {
                                renamingId = preset.id
                                name = preset.name
                                confirmDeleteId = null
                                confirmOverwriteId = null
                            },
                            onDuplicate = { duplicatePreset(preset.id) },
                            onOverwrite = {
                                if (confirmOverwriteId == preset.id) {
                                    overwritePreset(preset.id, false)
                                } else {
                                    confirmOverwriteId = preset.id
                                    confirmDeleteId = null
                                }
                            },
                            onExport = {
                                exportId = preset.id
                                exportLauncher.launch("amber-preset-" + safeFileName(preset.name) + ".json")
                            },
                            onDelete = {
                                if (confirmDeleteId == preset.id) {
                                    deletePreset(preset.id)
                                } else {
                                    confirmDeleteId = preset.id
                                    confirmOverwriteId = null
                                }
                            }
                        )
                    }
                    if (wide && missingBuiltins > 0 && hasPrevious) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AmberButton(
                                text = "Pulihkan preset bawaan",
                                onClick = { restoreBuiltins() },
                                modifier = Modifier.weight(1f),
                                style = AmberButtonStyle.Ghost,
                                enabled = !busy,
                                height = 40.dp
                            )
                            AmberButton(
                                text = "Kembalikan layout sebelumnya",
                                onClick = { restorePrevious() },
                                modifier = Modifier.weight(1f),
                                style = AmberButtonStyle.Ghost,
                                enabled = !busy,
                                height = 40.dp
                            )
                        }
                    } else {
                        if (missingBuiltins > 0) {
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
                }
            }
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
                                ControlPresetManager.clearEditing(dir)
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
            // Teks bisa panjang di layar pendek: dibatasi tingginya dan bisa digulir supaya tombol tetap terlihat
            Column(
                modifier = Modifier
                    .heightIn(max = 160.dp)
                    .verticalScroll(rememberScrollState())
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(
    preset: ControlPresetManager.Preset,
    active: Boolean,
    expanded: Boolean,
    enabled: Boolean,
    confirmingDelete: Boolean,
    confirmingOverwrite: Boolean,
    onApply: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onOverwrite: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Amber.Fill, shape)
            .border(1.dp, if (active) Amber.Yellow.copy(alpha = 0.6f) else Amber.Line, shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggle() }
            ) {
                Text(
                    text = preset.name,
                    color = Amber.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Berisi ${preset.buttonCount} tombol",
                        color = Amber.TextFaint,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (active) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sedang dipakai",
                            color = Amber.Yellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            AmberButton(
                text = if (active) "Dipakai" else "Pakai",
                onClick = onApply,
                style = if (active) AmberButtonStyle.Ghost else AmberButtonStyle.Primary,
                enabled = enabled && !active,
                height = 36.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            AmberButton(
                text = "Opsi",
                onClick = onToggle,
                style = AmberButtonStyle.Ghost,
                icon = "gear",
                height = 36.dp
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            // Tombol turun ke baris berikutnya sendiri bila tidak muat, jadi aman di lebar layar berapa pun
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AmberButton(
                    text = "Edit",
                    onClick = onEdit,
                    style = AmberButtonStyle.Ghost,
                    enabled = enabled,
                    height = 36.dp
                )
                AmberButton(
                    text = "Ubah nama",
                    onClick = onRename,
                    style = AmberButtonStyle.Ghost,
                    enabled = enabled,
                    height = 36.dp
                )
                AmberButton(
                    text = "Duplikat",
                    onClick = onDuplicate,
                    style = AmberButtonStyle.Ghost,
                    icon = "copy",
                    enabled = enabled,
                    height = 36.dp
                )
                AmberButton(
                    text = if (confirmingOverwrite) "Yakin?" else "Simpan layout ke sini",
                    onClick = onOverwrite,
                    style = if (confirmingOverwrite) AmberButtonStyle.Danger else AmberButtonStyle.Ghost,
                    enabled = enabled,
                    height = 36.dp
                )
                AmberButton(
                    text = "Ekspor",
                    onClick = onExport,
                    style = AmberButtonStyle.Ghost,
                    icon = "share",
                    enabled = enabled,
                    height = 36.dp
                )
                AmberButton(
                    text = if (confirmingDelete) "Yakin?" else "Hapus",
                    onClick = onDelete,
                    style = if (confirmingDelete) AmberButtonStyle.Danger else AmberButtonStyle.Ghost,
                    icon = "trash",
                    enabled = enabled,
                    height = 36.dp
                )
            }
        }
    }
}

/** Membaca folder preset dan menentukan preset mana yang sama dengan layout yang sedang dipakai. */
private fun loadDialogData(context: Context, dir: File): DialogData {
    // Preset bawaan disalin sekali; yang sudah dihapus pengguna tidak muncul lagi
    ControlPresetManager.ensureBuiltins(dir, ControlLayoutManager.getDefaultButtons())
    val list = ControlPresetManager.list(dir)
    val current = (ControlLayoutIO.parse(ControlLayoutManager.exportJson(context)) as? ControlLayoutIO.ImportResult.Ok)?.buttons
    val activeId = if (current == null) null else list.firstOrNull { ControlPresetManager.read(dir, it.id) == current }?.id
    return DialogData(
        presets = list,
        hasPrevious = ControlPresetManager.hasPrevious(dir),
        missingBuiltins = ControlPresetManager.missingBuiltins(dir),
        activeId = activeId,
        editingId = ControlPresetManager.editingId(dir)
    )
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

/** Menulis isi preset [id] ke berkas pilihan pengguna dalam format ekspor Amber yang bersih. */
private fun writePresetTo(context: Context, dir: File, id: String, uri: Uri): Boolean {
    val buttons = ControlPresetManager.read(dir, id) ?: return false
    return try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(ControlLayoutIO.toJson(buttons).toByteArray(Charsets.UTF_8))
            true
        } ?: false
    } catch (_: Exception) {
        false
    }
}

/** Nama preset yang aman jadi nama berkas: huruf, angka, strip, dan garis bawah saja. */
private fun safeFileName(name: String): String {
    val cleaned = name.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
        .joinToString("")
        .trim('-')
    return cleaned.ifEmpty { "layout" }.take(40)
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
