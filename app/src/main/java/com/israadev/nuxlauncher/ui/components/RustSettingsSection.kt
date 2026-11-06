package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.horizon.HorizonEngine
import com.israadev.nuxlauncher.core.horizon.RustSettingsStore
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.theme.AmberUi
import kotlin.math.roundToInt

/**
 * Isi tab Rust di layar Pengaturan. Pengaturan mesin Rust (Horizon) berbeda dari jalur Java (Fabric, Forge, dan
 * kawan-kawan), jadi punya tab sendiri dan tidak dicampur ke tab Game. Setiap fungsi di sini mengisi satu kelompok
 * (SettingsGroup di SettingsScreen). Nilainya disimpan oleh [RustSettingsStore] ke config milik mesin sendiri dan
 * berlaku pada PLAY berikutnya.
 */

/** Membaca ulang config mesin dari berkas setiap tab dibuka. Tidak menggambar apa pun. */
@Composable
fun RustSettingsLoader() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { RustSettingsStore.reload(context) }
}

/** Baris keterangan: judul + keterangan di kiri, nilai teks di kanan (tidak bisa diketuk). */
@Composable
private fun RustInfoRow(title: String, desc: String?, value: String, warn: Boolean = false) {
    SettingFrame(title, desc) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingRowText(title = title, desc = desc, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = value,
                color = if (warn) AmberUi.Danger else AmberUi.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/** Status mesin dan sumber daya game, plus catatan bahwa pengaturan ini hanya untuk instance Rust. */
@Composable
fun RustStatusRows() {
    val context = LocalContext.current
    val availability = remember { HorizonEngine.availability(context) }
    val installed = remember { HorizonEngine.gameInstalled(context) }

    val unavailable = availability as? HorizonEngine.Availability.Unavailable
    val lowRam = (availability as? HorizonEngine.Availability.Ready)?.lowRam == true
    RustInfoRow(
        title = "Mesin Rust (Horizon)",
        desc = when {
            unavailable != null -> unavailable.reason
            lowRam -> HorizonEngine.LOW_RAM_WARNING
            else -> "Pengganti seluruh client Java: tanpa JVM dan tanpa mod Java."
        },
        value = if (unavailable != null) "Tidak tersedia" else "Siap",
        warn = unavailable != null
    )
    SettingDivider()
    RustInfoRow(
        title = "Sumber daya game",
        desc = "Diunduh otomatis saat PLAY pertama, sekitar 500 MiB.",
        value = if (installed) "Terpasang" else "Belum diunduh"
    )
    SettingDivider()
    RustInfoRow(
        title = "Versi Minecraft",
        desc = "Satu-satunya versi yang dikerjakan mesin ini.",
        value = HorizonEngine.SUPPORTED_MC_VERSION
    )
    SettingDivider()
    SettingFrame(
        "Berlaku untuk instance Rust",
        "Pilihan di tab ini dipakai instance Rust saja dan berlaku pada PLAY berikutnya. Instance Vanilla, Fabric, dan Forge tidak terpengaruh."
    ) {
        SettingRowText(
            title = "Berlaku untuk instance Rust",
            desc = "Pilihan di tab ini dipakai instance Rust saja dan berlaku pada PLAY berikutnya. Instance Vanilla, Fabric, dan Forge tidak terpengaruh.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        )
    }
}

/** Renderer dan skala resolusi. */
@Composable
fun RustGraphicsRows() {
    val context = LocalContext.current
    val s by RustSettingsStore.settings.collectAsState()

    SettingChoiceRow(
        title = "Renderer Rust",
        desc = "Kompatibel paling aman untuk semua HP. Vulkan cepat memakai batching GPU. OpenGL ES masih eksperimental.",
        options = listOf(
            RustSettingsStore.RENDERER_COMPAT to "Vulkan kompatibel",
            "vulkan" to "Vulkan cepat",
            "open-gl" to "OpenGL ES"
        ),
        selected = s.renderer,
        onSelect = { value -> RustSettingsStore.update(context) { it.copy(renderer = value) } }
    )
    SettingDivider()
    SettingSliderRow(
        title = "Skala resolusi Rust",
        desc = "Menurunkan resolusi render membuat game lebih ringan, gambar jadi lebih buram.",
        valueText = "${s.renderScale}%",
        value = s.renderScale.toFloat(),
        valueRange = 25f..100f,
        steps = 14,
        onValueChange = { raw ->
            val percent = ((raw / 5f).roundToInt() * 5).coerceIn(25, 100)
            RustSettingsStore.update(context) { it.copy(renderScale = percent) }
        },
        footer = {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RustSettingsStore.RENDER_SCALE_PRESETS.forEach { preset ->
                    SettingChip(
                        text = "$preset%",
                        selected = s.renderScale == preset,
                        onClick = { RustSettingsStore.update(context) { it.copy(renderScale = preset) } }
                    )
                }
            }
        }
    )
}

/** Laju layar, petunjuk performa, batas pekerja chunk, dan cache. */
@Composable
fun RustPerformanceRows() {
    val context = LocalContext.current
    val s by RustSettingsStore.settings.collectAsState()

    SettingChoiceRow(
        title = "Laju layar",
        desc = "Permintaan ke Android, bukan batas FPS game. Laju yang tidak didukung layar bisa diabaikan.",
        options = RustSettingsStore.DISPLAY_RATES.map { hz -> hz to (if (hz == 0) "Bawaan sistem" else "$hz Hz") },
        selected = s.displayHz,
        onSelect = { hz -> RustSettingsStore.update(context) { it.copy(displayHz = hz) } }
    )
    SettingDivider()
    SettingSwitchRow(
        title = "Petunjuk performa",
        desc = "Memberi tahu Android bahwa game butuh performa stabil. Hanya berguna di HP yang mendukungnya.",
        checked = s.performanceHints,
        onCheckedChange = { on -> RustSettingsStore.update(context) { it.copy(performanceHints = on) } }
    )
    SettingDivider()
    SettingSliderRow(
        title = "Batas pekerja chunk",
        desc = "Batas pekerja yang membuat dan merakit chunk, bukan semua utas aplikasi. Nilai kecil mengurangi beban CPU tetapi memuat dunia lebih lambat.",
        valueText = if (s.workerLimit == 0) "Tanpa batas" else "${s.workerLimit}",
        value = s.workerLimit.toFloat(),
        valueRange = 0f..RustSettingsStore.MAX_WORKERS.toFloat(),
        steps = RustSettingsStore.MAX_WORKERS - 1,
        onValueChange = { raw ->
            val limit = raw.roundToInt().coerceIn(0, RustSettingsStore.MAX_WORKERS)
            RustSettingsStore.update(context) { it.copy(workerLimit = limit) }
        }
    )
    SettingDivider()
    SettingChoiceRow(
        title = "Cache RAM",
        desc = "Suara yang sudah didekode (80%) dan buffer mesh kosong (20%), dibuat ulang dengan kualitas sama. Bukan batas total RAM atau VRAM. Nilai kecil bisa menambah kerja CPU.",
        options = RustSettingsStore.CACHE_BUDGETS.map { mib -> mib to (if (mib == 0) "Bawaan (80 MiB)" else "$mib MiB") },
        selected = s.cacheBudgetMib,
        onSelect = { mib -> RustSettingsStore.update(context) { it.copy(cacheBudgetMib = mib) } }
    )
}

/** Arah layar, rasio, mode layar penuh, dan area poni. */
@Composable
fun RustScreenRows() {
    val context = LocalContext.current
    val s by RustSettingsStore.settings.collectAsState()

    SettingChoiceRow(
        title = "Arah layar",
        desc = "Arah layar saat bermain. Bawaannya landscape.",
        options = listOf(
            "landscape" to "Landscape",
            "reverse-landscape" to "Landscape terbalik",
            "portrait" to "Portrait",
            "reverse-portrait" to "Portrait terbalik",
            "auto" to "Otomatis"
        ),
        selected = s.orientation,
        onSelect = { value -> RustSettingsStore.update(context) { it.copy(orientation = value) } }
    )
    SettingDivider()
    SettingChoiceRow(
        title = "Rasio layar",
        desc = "Membatasi tampilan game ke rasio tertentu. Asli mengikuti rasio layar HP.",
        options = listOf(
            "native" to "Asli",
            "wide" to "16:9",
            "classic" to "4:3",
            "square" to "1:1"
        ),
        selected = s.aspect,
        onSelect = { value -> RustSettingsStore.update(context) { it.copy(aspect = value) } }
    )
    SettingDivider()
    SettingSwitchRow(
        title = "Layar penuh imersif",
        desc = "Menyembunyikan bilah navigasi dan bilah status saat bermain.",
        checked = s.immersive,
        onCheckedChange = { on -> RustSettingsStore.update(context) { it.copy(immersive = on) } }
    )
    SettingDivider()
    SettingSwitchRow(
        title = "Hindari poni layar",
        desc = "Menggeser tampilan game agar tidak tertutup lubang kamera atau poni.",
        checked = s.avoidCutout,
        onCheckedChange = { on -> RustSettingsStore.update(context) { it.copy(avoidCutout = on) } }
    )
}

/** Perangkat input utama mesin. */
@Composable
fun RustControlRows() {
    val context = LocalContext.current
    val s by RustSettingsStore.settings.collectAsState()
    val launcher by SettingsManager.settings.collectAsState()

    SettingSwitchRow(
        title = "Kontrol Amber",
        desc = "Posisi, ukuran, dan tombol layar mengikuti editor kontrol Amber, jadi hanya ada satu set kontrol. Matikan untuk memakai kontrol bawaan mesin (gaya Bedrock). Berlaku di PLAY berikutnya.",
        checked = launcher.rustAmberControls,
        onCheckedChange = { on ->
            SettingsManager.updateSettings(context, SettingsManager.settings.value.copy(rustAmberControls = on))
        }
    )
    SettingDivider()
    SettingChoiceRow(
        title = "Perangkat input",
        desc = "Otomatis memilih sendiri antara layar sentuh, keyboard dan mouse, atau gamepad sesuai yang terpasang.",
        options = listOf(
            "auto" to "Otomatis",
            "touch" to "Layar sentuh",
            "keyboard-mouse" to "Keyboard dan mouse",
            "controller" to "Gamepad"
        ),
        selected = s.inputHost,
        onSelect = { value -> RustSettingsStore.update(context) { it.copy(inputHost = value) } }
    )
}

/** Server yang langsung dimasuki saat PLAY, dan tombol setel ulang. */
@Composable
fun RustGameRows() {
    val context = LocalContext.current
    val s by RustSettingsStore.settings.collectAsState()

    // Teks isian dipegang di sini agar kursor tidak melompat; disamakan ulang bila config selesai dibaca.
    var text by remember { mutableStateOf(s.server) }
    LaunchedEffect(s.server) {
        if (RustSettingsStore.cleanServer(text) != s.server) text = s.server
    }

    SettingFrame(
        "Server otomatis",
        "Kosongkan untuk bermain di dunia sendiri. Bila diisi, PLAY langsung masuk ke server ini."
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp)
        ) {
            SettingRowText(
                title = "Server otomatis",
                desc = "Kosongkan untuk bermain di dunia sendiri. Bila diisi, PLAY langsung masuk ke server ini."
            )
            Spacer(modifier = Modifier.height(8.dp))
            NuxTextField(
                value = text,
                onValueChange = { raw ->
                    val cleaned = RustSettingsStore.cleanServer(raw)
                    text = cleaned
                    RustSettingsStore.update(context) { it.copy(server = cleaned) }
                },
                placeholder = "Contoh: mc.contoh.com:25565"
            )
        }
    }
    SettingDivider()
    SettingActionRow(
        title = "Setel ulang pengaturan Rust",
        desc = "Mengembalikan semua pilihan di tab ini ke bawaan mesin. Instance dan dunia tidak disentuh.",
        actionLabel = "Setel ulang",
        onClick = {
            RustSettingsStore.reset(context)
            text = ""
            Toast.makeText(context, "Pengaturan Rust dikembalikan ke bawaan.", Toast.LENGTH_SHORT).show()
        },
        danger = true
    )
}
