package com.israadev.nuxlauncher.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.R
import com.israadev.nuxlauncher.GameActivity
import java.io.File
import java.io.FileOutputStream
import com.israadev.nuxlauncher.core.account.AccountManager
import com.israadev.nuxlauncher.core.models.LauncherSettings
import com.israadev.nuxlauncher.core.renderer.NuxRendererRegistry
import com.israadev.nuxlauncher.core.renderer.NuxRendererPluginManager
import com.israadev.nuxlauncher.core.renderer.NuxRendererInfo
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.core.utils.NuxVersionUtils
import com.israadev.nuxlauncher.ui.components.*
import com.israadev.nuxlauncher.ui.dialogs.NuxRendererV2ConfigDialog
import com.israadev.nuxlauncher.ui.dialogs.NuxAboutDialog
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenGuiEditor: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentSettings by SettingsManager.settings.collectAsState()

    var showAboutDialog by remember { mutableStateOf(false) }

    // Local mutable state for instant responsive UI before saving
    var ramMb by remember(currentSettings.ramMb) { mutableIntStateOf(currentSettings.ramMb) }
    var initialHeapMb by remember(currentSettings.initialHeapMb) { mutableIntStateOf(currentSettings.initialHeapMb) }
    var customJvmArgs by remember(currentSettings.customJvmArgs) { mutableStateOf(currentSettings.customJvmArgs) }
    var defaultRuntime by remember(currentSettings.defaultJavaRuntime) { mutableStateOf(currentSettings.defaultJavaRuntime) }

    var mouseMode by remember(currentSettings.mouseControlMode) { mutableStateOf(currentSettings.mouseControlMode) }
    var cursorSensitivity by remember(currentSettings.cursorSensitivity) { mutableIntStateOf(currentSettings.cursorSensitivity) }
    var captureSensitivity by remember(currentSettings.captureSensitivity) { mutableIntStateOf(currentSettings.captureSensitivity) }
    var touchAttack by remember(currentSettings.touchAttackEnabled) { mutableStateOf(currentSettings.touchAttackEnabled) }
    var mouseSizeDp by remember(currentSettings.mouseSizeDp) { mutableIntStateOf(currentSettings.mouseSizeDp) }
    var physicalMouseMode by remember(currentSettings.physicalMouseMode) { mutableStateOf(currentSettings.physicalMouseMode) }

    var resolutionRatio by remember(currentSettings.resolutionRatio) { mutableIntStateOf(currentSettings.resolutionRatio) }
    var autoOptimizeMC by remember(currentSettings.autoOptimizeMinecraft) { mutableStateOf(currentSettings.autoOptimizeMinecraft) }
    var sustainedPerf by remember(currentSettings.sustainedPerformanceMode) { mutableStateOf(currentSettings.sustainedPerformanceMode) }
    var selectedRenderer by remember(currentSettings.selectedRenderer) { mutableStateOf(currentSettings.selectedRenderer) }
    var vulkanDriver by remember(currentSettings.vulkanDriver) { mutableStateOf(currentSettings.vulkanDriver) }
    var graphicsApi by remember(currentSettings.graphicsApi) { mutableStateOf(currentSettings.graphicsApi) }
    var zinkPreferSystemDriver by remember(currentSettings.zinkPreferSystemDriver) { mutableStateOf(currentSettings.zinkPreferSystemDriver) }
    var vsyncInZink by remember(currentSettings.vsyncInZink) { mutableStateOf(currentSettings.vsyncInZink) }
    var showRendererDialog by remember { mutableStateOf(false) }
    var rendererListTick by remember { mutableIntStateOf(0) }
    var showRendererConfigDialog by remember { mutableStateOf(false) }
    var selectedConfigRenderer by remember { mutableStateOf<NuxRendererInfo?>(null) }
    var showAdrenoWarningDialog by remember { mutableStateOf(false) }

    val totalRamMb = remember { SettingsManager.getTotalDeviceMemoryMb(context) }
    val maxAllocatableRam = remember(totalRamMb) { (totalRamMb * 0.85f).toInt().coerceAtLeast(1024) }
    val cpuCores = remember { SettingsManager.getCpuCoreCount() }

    fun commitSettings() {
        val updated = currentSettings.copy(
            ramMb = ramMb,
            initialHeapMb = initialHeapMb,
            customJvmArgs = customJvmArgs,
            defaultJavaRuntime = defaultRuntime,
            mouseControlMode = mouseMode,
            cursorSensitivity = cursorSensitivity,
            captureSensitivity = captureSensitivity,
            touchAttackEnabled = touchAttack,
            mouseSizeDp = mouseSizeDp,
            physicalMouseMode = physicalMouseMode,
            resolutionRatio = resolutionRatio,
            autoOptimizeMinecraft = autoOptimizeMC,
            sustainedPerformanceMode = sustainedPerf,
            selectedRenderer = selectedRenderer,
            vulkanDriver = vulkanDriver,
            graphicsApi = graphicsApi,
            zinkPreferSystemDriver = zinkPreferSystemDriver,
            vsyncInZink = vsyncInZink
        )
        SettingsManager.updateSettings(context, updated)
    }

    // Tab aktif (0 = Memori, 1 = Grafis, 2 = Kontrol). Isi tiap tab digulir sendiri-sendiri.
    var section by remember { mutableIntStateOf(0) }
    val contentScroll = rememberScrollState()
    LaunchedEffect(section) { contentScroll.scrollTo(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
        // Judul + tombol Tentang
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Pengaturan",
                    color = NuxColors.DarkGray,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "${totalRamMb / 1024} GB RAM · $cpuCores inti CPU",
                    color = NuxColors.GrayNeutral,
                    fontSize = 11.sp
                )
            }
            NuxButton(
                onClick = { showAboutDialog = true },
                backgroundColor = Color.Transparent,
                cornerRadius = 20.dp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Tentang & lisensi",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Tab samping
            Column(
                modifier = Modifier.width(128.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SettingTab(icon = Icons.Outlined.Memory, label = "Memori", selected = section == 0) { section = 0 }
                SettingTab(icon = Icons.Outlined.Speed, label = "Grafis", selected = section == 1) { section = 1 }
                SettingTab(icon = Icons.Outlined.TouchApp, label = "Kontrol", selected = section == 2) { section = 2 }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Isi tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(contentScroll)
            ) {
                when (section) {
                    0 -> {
                        // Profil khusus perangkat: hanya muncul di HP yang terdaftar (Oppo A3x 4G),
                        // di HP lain tidak menggambar apa pun.
                        DeviceProfileCard()

                        val ramTooBig = ramMb > (totalRamMb * 0.75f)
                        SettingSliderRow(
                            title = "Memori game (RAM)",
                            desc = if (ramTooBig) {
                                "Terlalu besar untuk HP ini, game dan HP bisa jadi lambat."
                            } else {
                                "Batas memori Java (-Xmx). Lebih besar tidak selalu lebih cepat."
                            },
                            valueText = "${String.format("%.1f", ramMb / 1024f)} GB",
                            value = ramMb.toFloat(),
                            valueRange = 512f..maxAllocatableRam.toFloat(),
                            steps = ((maxAllocatableRam - 512) / 128) - 1,
                            onValueChange = {
                                ramMb = ((it / 128).roundToInt() * 128).coerceIn(512, maxAllocatableRam)
                                commitSettings()
                            },
                            warn = ramTooBig,
                            footer = {
                                Row(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        1024 to "1 GB",
                                        1536 to "1,5 GB",
                                        2048 to "2 GB",
                                        3072 to "3 GB",
                                        4096 to "4 GB"
                                    ).forEach { (preset, label) ->
                                        if (preset <= maxAllocatableRam) {
                                            SettingChip(
                                                text = label,
                                                selected = ramMb == preset,
                                                onClick = {
                                                    ramMb = preset
                                                    commitSettings()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        SettingDivider()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp)
                        ) {
                            SettingRowText(
                                title = "Argumen JVM tambahan",
                                desc = "Opsional, untuk pengguna mahir. Salah isi bisa membuat game gagal jalan."
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            NuxTextField(
                                value = customJvmArgs,
                                onValueChange = {
                                    customJvmArgs = it
                                    commitSettings()
                                },
                                placeholder = "Contoh: -XX:+UseG1GC"
                            )
                        }
                    }

                    1 -> {
                        val activeRendererInfo = remember(selectedRenderer) {
                            NuxRendererRegistry.findRendererById(selectedRenderer)
                        }

                        // Renderer aktif + tombol ganti / konfigurasi
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Renderer",
                                    color = NuxColors.DarkGray,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = activeRendererInfo.displayName,
                                        color = NuxColors.Yellow,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    NuxBadge(
                                        text = activeRendererInfo.badge,
                                        textColor = NuxColors.SageGreen
                                    )
                                }
                                Text(
                                    text = activeRendererInfo.summary,
                                    color = NuxColors.GrayNeutral,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            if (activeRendererInfo.isConfigurable) {
                                NuxButton(
                                    onClick = {
                                        selectedConfigRenderer = activeRendererInfo
                                        showRendererConfigDialog = true
                                    },
                                    backgroundColor = Color.Transparent,
                                    cornerRadius = 20.dp,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Konfigurasi renderer",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            NuxButton(
                                onClick = { showRendererDialog = true },
                                backgroundColor = Color.Transparent,
                                cornerRadius = 20.dp,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Ganti",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        SettingDivider()
                        SettingSwitchRow(
                            title = "Optimasi otomatis options.txt",
                            desc = "Mematikan awan dan bayangan secara otomatis supaya FPS lebih tinggi.",
                            checked = autoOptimizeMC,
                            onCheckedChange = {
                                autoOptimizeMC = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingChoiceRow(
                            title = "Skala resolusi layar",
                            desc = "Lebih kecil = FPS lebih tinggi, gambar lebih buram.",
                            options = listOf(50 to "50%", 75 to "75%", 100 to "100%", 125 to "125%"),
                            selected = resolutionRatio,
                            onSelect = {
                                resolutionRatio = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingChoiceRow(
                            title = "Driver Vulkan",
                            desc = "Pilih implementasi driver Vulkan yang dipakai sistem/game.",
                            options = listOf("auto" to "Auto", "system" to "Sistem", "turnip" to "Turnip"),
                            selected = vulkanDriver,
                            onSelect = {
                                vulkanDriver = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingChoiceRow(
                            title = "API grafis",
                            desc = "Atur API grafis yang dipakai Minecraft modern 26.2+.",
                            options = listOf("DEFAULT" to "Default", "OPENGL" to "OpenGL", "VULKAN" to "Vulkan"),
                            selected = graphicsApi,
                            onSelect = {
                                graphicsApi = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSwitchRow(
                            title = "Gunakan driver Vulkan sistem",
                            desc = "Paksa pakai driver Vulkan bawaan HP (bukan Turnip). Memengaruhi renderer Zink.",
                            checked = zinkPreferSystemDriver,
                            onCheckedChange = { checked ->
                                if (checked && NuxVersionUtils.isAdrenoGPU()) {
                                    showAdrenoWarningDialog = true
                                } else {
                                    zinkPreferSystemDriver = checked
                                    commitSettings()
                                }
                            }
                        )
                        SettingDivider()
                        SettingSwitchRow(
                            title = "V-Sync Zink",
                            desc = "Sinkronisasi vertikal untuk renderer Kopper Zink lewat antarmuka sistem.",
                            checked = vsyncInZink,
                            onCheckedChange = {
                                vsyncInZink = it
                                commitSettings()
                            }
                        )
                    }

                    else -> {
                        // Editor tata letak tombol kontrol virtual
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingRowText(
                                title = "Kustomisasi kontrol virtual",
                                desc = "Ubah tata letak tombol, ukuran, transparansi, scroll hotbar, dan pemetaan tombol Minecraft.",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            NuxButton(
                                onClick = onOpenGuiEditor,
                                backgroundColor = NuxColors.Yellow,
                                cornerRadius = 20.dp,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Buka editor",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        SettingDivider()
                        SettingChoiceRow(
                            title = "Mode mouse di menu",
                            desc = if (mouseMode == "CLICK") {
                                "Sentuh tombol atau slot menu langsung di mana jari menekan."
                            } else {
                                "Geser jari untuk menggerakkan kursor virtual, ketuk layar untuk klik."
                            },
                            options = listOf("SLIDE" to "Slide (trackpad)", "CLICK" to "Click (langsung)"),
                            selected = mouseMode,
                            onSelect = {
                                mouseMode = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSwitchRow(
                            title = "Pukul lewat layar",
                            desc = "Ketuk atau tahan di area kosong layar untuk memukul. Matikan supaya menggeser kamera tidak ikut memukul; pakai tombol pukul.",
                            checked = touchAttack,
                            onCheckedChange = {
                                touchAttack = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSliderRow(
                            title = "Sensitivitas kamera",
                            desc = "Kecepatan memutar pandangan di dalam game.",
                            valueText = "$captureSensitivity%",
                            value = captureSensitivity.toFloat(),
                            valueRange = 50f..300f,
                            steps = 25,
                            onValueChange = {
                                captureSensitivity = it.roundToInt()
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSliderRow(
                            title = "Sensitivitas kursor virtual",
                            desc = "Kecepatan kursor saat berada di menu Minecraft.",
                            valueText = "$cursorSensitivity%",
                            value = cursorSensitivity.toFloat(),
                            valueRange = 50f..300f,
                            steps = 25,
                            onValueChange = {
                                cursorSensitivity = it.roundToInt()
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSliderRow(
                            title = "Ukuran kursor virtual",
                            valueText = "${mouseSizeDp}dp",
                            value = mouseSizeDp.toFloat(),
                            valueRange = 16f..48f,
                            steps = 16,
                            onValueChange = {
                                mouseSizeDp = it.roundToInt()
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSwitchRow(
                            title = "Mode mouse fisik (auto-hide)",
                            desc = "Sembunyikan kursor virtual otomatis jika mouse eksternal tersambung.",
                            checked = physicalMouseMode,
                            onCheckedChange = {
                                physicalMouseMode = it
                                commitSettings()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Pita bawah: semua perubahan langsung tersimpan, tombol reset ke bawaan
        SettingDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Perubahan tersimpan otomatis",
                color = NuxColors.GrayNeutral,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            NuxButton(
                onClick = {
                    SettingsManager.resetToDefaults(context)
                    val reset = SettingsManager.settings.value
                    ramMb = reset.ramMb
                    initialHeapMb = reset.initialHeapMb
                    customJvmArgs = reset.customJvmArgs
                    defaultRuntime = reset.defaultJavaRuntime
                    mouseMode = reset.mouseControlMode
                    cursorSensitivity = reset.cursorSensitivity
                    captureSensitivity = reset.captureSensitivity
                    touchAttack = reset.touchAttackEnabled
                    mouseSizeDp = reset.mouseSizeDp
                    physicalMouseMode = reset.physicalMouseMode
                    resolutionRatio = reset.resolutionRatio
                    autoOptimizeMC = reset.autoOptimizeMinecraft
                    sustainedPerf = reset.sustainedPerformanceMode
                    Toast.makeText(context, "Pengaturan di-reset ke default!", Toast.LENGTH_SHORT).show()
                },
                backgroundColor = Color.Transparent,
                contentColor = NuxColors.ErrorRed,
                borderColor = NuxColors.ErrorRed.copy(alpha = 0.45f),
                cornerRadius = 20.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Reset default",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }


        // Renderer Selection Dialog (Landscape Optimized with NuxDialog)
        if (showRendererDialog) {
            // Pindai plugin di thread latar; daftar langsung tampil dan diperbarui setelah selesai.
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    try { NuxRendererPluginManager.scanPlugins(context) } catch (_: Throwable) {}
                }
                rendererListTick++
            }
            val rendererList = remember(rendererListTick) { NuxRendererRegistry.availableRenderers }
            NuxDialog(
                onDismissRequest = { showRendererDialog = false },
                modifier = Modifier.fillMaxWidth(0.72f),
                fillMaxHeight = true
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = NuxColors.SageGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "PILIH RENDERER GRAFIK",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = NuxColors.DarkGray
                                )
                                Text(
                                    text = "Sesuaikan backend grafis dengan Minecraft & GPU perangkat",
                                    fontSize = 9.5.sp,
                                    color = NuxColors.GrayNeutral
                                )
                            }
                        }
                        IconButton(
                            onClick = { showRendererDialog = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = NuxColors.DarkGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Scrollable Renderer List
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(rendererList) { rendererItem ->
                            val isSelected = selectedRenderer.equals(rendererItem.id, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) NuxColors.YellowPale else NuxColors.SurfaceInput
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) NuxColors.MintGreen else NuxColors.CardBorder,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        selectedRenderer = rendererItem.id
                                        commitSettings()
                                        showRendererDialog = false
                                        Toast.makeText(context, "Renderer diubah ke: ${rendererItem.displayName}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = {
                                                    selectedRenderer = rendererItem.id
                                                    commitSettings()
                                                    showRendererDialog = false
                                                    Toast.makeText(context, "Renderer diubah ke: ${rendererItem.displayName}", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = NuxColors.ForestGreen,
                                                    unselectedColor = NuxColors.GrayNeutral
                                                ),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = rendererItem.displayName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = if (isSelected) NuxColors.SageGreen else NuxColors.DarkGray
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (rendererItem.isConfigurable) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(NuxColors.SurfaceElevated)
                                                        .border(1.dp, NuxColors.CardBorder, RoundedCornerShape(4.dp))
                                                        .clickable {
                                                            selectedConfigRenderer = rendererItem
                                                            showRendererDialog = false
                                                            showRendererConfigDialog = true
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Settings,
                                                        contentDescription = "Konfigurasi ${rendererItem.displayName}",
                                                        tint = NuxColors.SageGreen,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (isSelected) NuxColors.ForestGreen else NuxColors.SurfaceElevated,
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = rendererItem.badge,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) NuxColors.Ink else NuxColors.GrayNeutral
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = rendererItem.summary,
                                        fontSize = 9.5.sp,
                                        color = NuxColors.GrayNeutral,
                                        lineHeight = 12.sp,
                                        modifier = Modifier.padding(start = 24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Kesesuaian: ${rendererItem.compatibility}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NuxColors.SageGreen,
                                        modifier = Modifier.padding(start = 24.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        NuxButton(
                            onClick = { showRendererDialog = false },
                            modifier = Modifier.height(30.dp),
                            backgroundColor = NuxColors.SurfaceInput,
                            contentColor = NuxColors.DarkGray,
                            cornerRadius = 6.dp
                        ) {
                            Text("TUTUP", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }

    // Adreno GPU Warning Dialog (Persis seperti Zalith)
    if (showAdrenoWarningDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAdrenoWarningDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                NuxCard(
                    modifier = Modifier
                        .width(420.dp)
                        .wrapContentHeight(),
                    backgroundColor = NuxColors.SurfaceElevated,
                    borderColor = NuxColors.DarkGray.copy(alpha = 0.40f),
                    cornerRadius = NuxSizes.CornerRadiusLarge,
                    fillMaxHeight = false
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PERINGATAN DRIVER ADRENO",
                                color = NuxColors.AmberDark,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "Launcher mendeteksi perangkatmu menggunakan GPU Adreno. Mengaktifkan opsi ini dapat menyebabkan perender Zink tidak berfungsi atau crash. Apakah Anda yakin ingin mengaktifkannya?",
                            color = NuxColors.GrayNeutral,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                NuxButton(
                                    onClick = {
                                        zinkPreferSystemDriver = false
                                        commitSettings()
                                        showAdrenoWarningDialog = false
                                    },
                                    backgroundColor = NuxColors.SurfaceInput,
                                    contentColor = NuxColors.DarkGray,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "BATAL",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                NuxButton(
                                    onClick = {
                                        zinkPreferSystemDriver = true
                                        commitSettings()
                                        showAdrenoWarningDialog = false
                                    },
                                    backgroundColor = NuxColors.Amber,
                                    contentColor = Color.Black,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "AKTIFKAN",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Konfigurasi Renderer V2 (persis Zalith untuk MobileGlues, NGG dll)
    if (showRendererConfigDialog && selectedConfigRenderer != null) {
        NuxRendererV2ConfigDialog(
            rendererInfo = selectedConfigRenderer,
            onDismiss = { showRendererConfigDialog = false }
        )
    }

    // Dialog Tentang & Lisensi Open Source (GPL-3.0 & Zalith Compliance)
    if (showAboutDialog) {
        NuxAboutDialog(
            onDismissRequest = { showAboutDialog = false }
        )
    }
}
