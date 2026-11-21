package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.controls.ControlLayoutManager
import com.israadev.nuxlauncher.core.controls.ControlPresetManager
import com.israadev.nuxlauncher.core.horizon.HorizonEngine
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.ui.components.AmberChoicePill
import com.israadev.nuxlauncher.ui.components.AmberPage
import com.israadev.nuxlauncher.ui.components.AmberPageLabel
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.screens.AmberPillButton
import com.israadev.nuxlauncher.ui.theme.AmberUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pilihan layout kontrol khusus satu instance.
 *
 * "Ikuti global" (bawaan) memakai layout yang aktif di Pengaturan > Kontrol. Memilih salah satu preset membuat
 * instance ini selalu bermain dengan preset itu, tanpa mengubah layout global; tombol yang diatur ulang di dalam game
 * selama bermain instance ini disimpan ke preset tersebut. Berguna bila satu instance dimainkan dengan jari berbeda
 * (mis. modpack dengan banyak tombol vs dunia vanilla yang hanya butuh joystick).
 *
 * Tersimpan lewat [onSave] saat Simpan ditekan; panah kembali menutup tanpa menyimpan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NuxInstanceControlsDialog(
    instance: Instance,
    onDismiss: () -> Unit,
    onSave: (Instance) -> Unit
) {
    val context = LocalContext.current
    var presets by remember { mutableStateOf<List<ControlPresetManager.Preset>?>(null) }
    var selected by remember { mutableStateOf(instance.controlPresetId?.takeIf { it.isNotBlank() }) }

    // Daftar preset dibaca dari berkas, jadi di thread latar. Preset bawaan disalin sekali bila belum pernah.
    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) {
            val dir = ControlPresetManager.dir(context)
            ControlPresetManager.ensureBuiltins(dir, ControlLayoutManager.getDefaultButtons())
            ControlPresetManager.list(dir)
        }
        presets = list
        // Preset yang tersimpan di instance ini sudah dihapus: tampilkan sebagai "Ikuti global", itu yang dipakai game.
        if (selected != null && list.none { it.id == selected }) selected = null
    }

    val chosen = presets?.firstOrNull { it.id == selected }

    AmberPage(
        onDismiss = onDismiss,
        title = "Kontrol instance",
        icon = Icons.Outlined.TouchApp,
        subtitle = instance.name,
        actions = {
            AmberPillButton(
                text = "Simpan",
                onClick = {
                    onSave(instance.copy(controlPresetId = selected))
                    Toast.makeText(context, "Kontrol instance disimpan", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                icon = Icons.Outlined.Check
            )
        }
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pilih layout tombol layar yang selalu dipakai saat memainkan instance ini. \"Ikuti global\" memakai layout aktif di Pengaturan.",
                color = AmberUi.TextDim,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SectionCard {
                AmberPageLabel("LAYOUT KONTROL")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmberChoicePill(
                        text = "Ikuti global",
                        selected = selected == null,
                        onClick = { selected = null }
                    )
                    presets?.forEach { p ->
                        AmberChoicePill(
                            text = p.name,
                            selected = selected == p.id,
                            onClick = { selected = p.id }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (chosen == null) {
                        "Memakai layout yang aktif di Pengaturan, dan ikut berubah bila layout itu diubah."
                    } else {
                        "Preset ini punya ${chosen.buttonCount} tombol. Hanya dipakai untuk instance ini; layout global tidak berubah. " +
                            "Tombol yang kamu atur di dalam game ikut tersimpan ke preset ini."
                    },
                    color = AmberUi.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                if (HorizonEngine.isEngine(instance.loader)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mesin Rust memakai layout ini hanya bila \"Kontrol Amber\" menyala di Pengaturan.",
                        color = AmberUi.TextDim,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Text(
                text = "Buat atau kelola preset di Pengaturan > Kontrol > Preset.",
                color = AmberUi.TextDim,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
