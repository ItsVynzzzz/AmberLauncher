package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.renderer.NuxRendererRegistry
import com.israadev.nuxlauncher.core.settings.PowerSaver
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.ui.components.AmberChoicePill
import com.israadev.nuxlauncher.ui.components.AmberField
import com.israadev.nuxlauncher.ui.components.AmberPage
import com.israadev.nuxlauncher.ui.components.AmberPageLabel
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.screens.AmberPillButton
import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.israadev.nuxlauncher.ui.theme.amberGlass

private fun ramLabel(mb: Int): String =
    if (mb % 1024 == 0) "${mb / 1024} GB" else "${String.format("%.1f", mb / 1024f)} GB"

/**
 * Pengaturan khusus satu instance: RAM, skala resolusi, renderer, dan argumen JVM.
 *
 * Tiap bagian punya pilihan "Ikuti global" (bawaan): selama itu terpilih, instance memakai Pengaturan umum dan ikut
 * berubah bila Pengaturan diubah. Memilih nilai lain mengunci nilai itu hanya untuk instance ini, tanpa menyentuh
 * Pengaturan umum. Berguna untuk HP kelas bawah: modpack berat diberi RAM besar, dunia vanilla ringan tetap hemat.
 *
 * Tersimpan lewat [onSave] saat tombol Simpan ditekan; panah kembali menutup tanpa menyimpan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NuxInstanceSettingsDialog(
    instance: Instance,
    onDismiss: () -> Unit,
    onSave: (Instance) -> Unit
) {
    val context = LocalContext.current
    val global by SettingsManager.settings.collectAsState()

    var ram by remember { mutableStateOf(instance.overrideRamMb) }
    var resolution by remember { mutableStateOf(instance.overrideResolutionRatio) }
    var renderer by remember { mutableStateOf(instance.overrideRenderer?.takeIf { it.isNotBlank() }) }
    var useJvm by remember { mutableStateOf(instance.overrideJvmArgs != null) }
    var jvm by remember { mutableStateOf(instance.overrideJvmArgs.orEmpty()) }

    val totalRamMb = remember { SettingsManager.getTotalDeviceMemoryMb(context) }
    val maxRam = remember(totalRamMb) { (totalRamMb * 0.6f).toInt().coerceAtLeast(1024) }
    val ramChoices = remember(ram, maxRam) {
        (listOf(1024, 1536, 2048, 3072, 4096).filter { it <= maxRam } + listOfNotNull(ram)).distinct().sorted()
    }
    val resolutionChoices = remember(resolution) {
        (listOf(50, 60, 70, 80, 90, 100) + listOfNotNull(resolution)).distinct().sorted()
    }
    val rendererChoices = remember(renderer, instance.mcVersion) {
        val list = NuxRendererRegistry.availableRenderers
            .filter { NuxRendererRegistry.isSupportedForVersion(it, instance.mcVersion) }
            .toMutableList()
        // Pilihan tersimpan yang tidak ada di daftar tetap ditampilkan, supaya pengguna bisa melihat dan menggantinya.
        renderer?.let { id ->
            val info = NuxRendererRegistry.findRendererById(id)
            if (list.none { it.id == info.id }) list.add(info)
        }
        list
    }
    val globalRenderer = remember(global.selectedRenderer) {
        NuxRendererRegistry.findRendererById(global.selectedRenderer).displayName
    }

    val anySet = ram != null || resolution != null || renderer != null || useJvm

    val save: () -> Unit = {
        onSave(
            instance.copy(
                overrideRamMb = ram,
                overrideResolutionRatio = resolution,
                overrideRenderer = renderer,
                overrideJvmArgs = if (useJvm) jvm.trim().replace(Regex("\\s+"), " ") else null
            )
        )
        Toast.makeText(context, "Pengaturan instance disimpan", Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    AmberPage(
        onDismiss = onDismiss,
        title = "Pengaturan instance",
        icon = Icons.Outlined.Tune,
        subtitle = instance.name,
        actions = {
            AmberPillButton(
                text = "Simpan",
                onClick = save,
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
                text = "Nilai yang dipilih di sini hanya berlaku untuk instance ini. \"Ikuti global\" memakai Pengaturan umum, " +
                    "dan ikut berubah bila Pengaturan umum diubah.",
                color = AmberUi.TextDim,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SectionCard {
                AmberPageLabel("MEMORI (RAM)")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmberChoicePill(
                        text = "Ikuti global (${ramLabel(global.ramMb)})",
                        selected = ram == null,
                        onClick = { ram = null }
                    )
                    ramChoices.forEach { mb ->
                        AmberChoicePill(text = ramLabel(mb), selected = ram == mb, onClick = { ram = mb })
                    }
                }
                val chosen = ram
                if (chosen != null && chosen > totalRamMb * 0.5f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lebih dari separuh RAM HP: game bisa ditutup sendiri oleh Android.",
                        color = AmberUi.Danger,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            SectionCard {
                AmberPageLabel("SKALA RESOLUSI")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmberChoicePill(
                        text = "Ikuti global (${global.resolutionRatio}%)",
                        selected = resolution == null,
                        onClick = { resolution = null }
                    )
                    resolutionChoices.forEach { r ->
                        AmberChoicePill(text = "$r%", selected = resolution == r, onClick = { resolution = r })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Lebih kecil = FPS lebih tinggi, gambar lebih buram.",
                    color = AmberUi.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                val chosen = resolution
                if (chosen != null && global.powerSaverMode && chosen > PowerSaver.MAX_RESOLUTION) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mode Hemat sedang menyala: resolusi dibatasi ${PowerSaver.MAX_RESOLUTION}%.",
                        color = AmberUi.TextDim,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            SectionCard {
                AmberPageLabel("RENDERER")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmberChoicePill(
                        text = "Ikuti global ($globalRenderer)",
                        selected = renderer == null,
                        onClick = { renderer = null }
                    )
                    rendererChoices.forEach { info ->
                        AmberChoicePill(
                            text = info.displayName,
                            selected = renderer == info.id,
                            onClick = { renderer = info.id }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hanya renderer yang cocok dengan Minecraft ${instance.mcVersion} yang ditampilkan.",
                    color = AmberUi.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            SectionCard {
                AmberPageLabel("ARGUMEN JVM TAMBAHAN")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmberChoicePill(
                        text = if (global.customJvmArgs.isBlank()) "Ikuti global (kosong)" else "Ikuti global",
                        selected = !useJvm,
                        onClick = { useJvm = false }
                    )
                    AmberChoicePill(
                        text = "Khusus instance ini",
                        selected = useJvm,
                        onClick = { useJvm = true }
                    )
                }
                if (useJvm) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AmberField(
                        value = jvm,
                        onValueChange = { jvm = it.replace("\n", " ") },
                        placeholder = "Contoh: -XX:+UseG1GC"
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (useJvm) {
                        "Untuk pengguna mahir; salah isi bisa membuat game gagal jalan. Dikosongkan = tanpa argumen tambahan untuk instance ini."
                    } else if (global.customJvmArgs.isBlank()) {
                        "Untuk pengguna mahir. Pengaturan umum tidak memakai argumen tambahan."
                    } else {
                        "Memakai argumen dari Pengaturan umum: ${global.customJvmArgs}"
                    },
                    color = AmberUi.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            if (anySet) {
                AmberPillButton(
                    text = "Kembalikan semua ke global",
                    onClick = {
                        ram = null
                        resolution = null
                        renderer = null
                        useJvm = false
                        jvm = ""
                    },
                    icon = Icons.Outlined.Refresh,
                    filled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Kartu kaca bersudut besar untuk satu kelompok pilihan. */
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
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
