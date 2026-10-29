package com.israadev.nuxlauncher.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.israadev.nuxlauncher.ui.i18n.Toast
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
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Tr
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
import com.israadev.nuxlauncher.core.controls.ControlLayoutIO
import com.israadev.nuxlauncher.core.controls.ControlLayoutManager
import com.israadev.nuxlauncher.core.device.DeviceProfiles
import com.israadev.nuxlauncher.core.models.LauncherSettings
import com.israadev.nuxlauncher.core.renderer.NuxRendererRegistry
import com.israadev.nuxlauncher.core.renderer.NuxRendererPluginManager
import com.israadev.nuxlauncher.core.renderer.NuxRendererInfo
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.core.utils.NuxVersionUtils
import com.israadev.nuxlauncher.ui.components.*
import com.israadev.nuxlauncher.ui.dialogs.NuxRendererV2ConfigDialog
import com.israadev.nuxlauncher.ui.dialogs.NuxAboutDialog
import com.israadev.nuxlauncher.ui.dialogs.NuxImportLayoutDialog
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
    onChangeBackground: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentSettings by SettingsManager.settings.collectAsState()

    var showAboutDialog by remember { mutableStateOf(false) }

    // Ekspor/impor layout kontrol lewat pemilih berkas sistem. Baca/tulis berkas dan penyimpanan
    // dikerjakan di thread IO; layar hanya menyimpan hasil pemeriksaan sampai pengguna menyetujui.
    var pendingImport by remember { mutableStateOf<ControlLayoutIO.ImportResult.Ok?>(null) }
    val exportLayoutLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { ControlLayoutManager.exportTo(context, uri) }
                Toast.makeText(
                    context,
                    if (ok) "Layout kontrol disimpan ke berkas" else "Gagal menyimpan berkas",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val importLayoutLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val r = withContext(Dispatchers.IO) { ControlLayoutManager.readImport(context, uri) }
                if (r is ControlLayoutIO.ImportResult.Ok) {
                    pendingImport = r
                } else if (r is ControlLayoutIO.ImportResult.Failed) {
                    val message = when (r.reason) {
                        ControlLayoutIO.Reason.TOO_LARGE -> "Berkas terlalu besar untuk layout kontrol"
                        ControlLayoutIO.Reason.NO_BUTTONS -> "Berkas tidak berisi tombol"
                        ControlLayoutIO.Reason.NOT_LAYOUT -> "Berkas ini bukan layout kontrol yang valid"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Local mutable state for instant responsive UI before saving
    var ramMb by remember(currentSettings.ramMb) { mutableIntStateOf(currentSettings.ramMb) }
    var initialHeapMb by remember(currentSettings.initialHeapMb) { mutableIntStateOf(currentSettings.initialHeapMb) }
    var customJvmArgs by remember(currentSettings.customJvmArgs) { mutableStateOf(currentSettings.customJvmArgs) }
    var defaultRuntime by remember(currentSettings.defaultJavaRuntime) { mutableStateOf(currentSettings.defaultJavaRuntime) }

    var mouseMode by remember(currentSettings.mouseControlMode) { mutableStateOf(currentSettings.mouseControlMode) }
    var cursorSensitivity by remember(currentSettings.cursorSensitivity) { mutableIntStateOf(currentSettings.cursorSensitivity) }
    var captureSensitivity by remember(currentSettings.captureSensitivity) { mutableIntStateOf(currentSettings.captureSensitivity) }
    var touchAttack by remember(currentSettings.touchAttackEnabled) { mutableStateOf(currentSettings.touchAttackEnabled) }
    var hotbarTouch by remember(currentSettings.hotbarTouchEnabled) { mutableStateOf(currentSettings.hotbarTouchEnabled) }
    var buttonLookDrag by remember(currentSettings.buttonLookDrag) { mutableStateOf(currentSettings.buttonLookDrag) }
    var touchControllerEnabled by remember(currentSettings.touchControllerEnabled) { mutableStateOf(currentSettings.touchControllerEnabled) }
    var touchControllerExclusive by remember(currentSettings.touchControllerExclusive) { mutableStateOf(currentSettings.touchControllerExclusive) }
    var gamepadEmulation by remember(currentSettings.gamepadEmulation) { mutableStateOf(currentSettings.gamepadEmulation) }
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
    var recordQuality by remember(currentSettings.recordQuality) { mutableStateOf(currentSettings.recordQuality) }
    var recordFps by remember(currentSettings.recordFps) { mutableIntStateOf(currentSettings.recordFps) }
    var recordMic by remember(currentSettings.recordMic) { mutableStateOf(currentSettings.recordMic) }
    var recordWithControls by remember(currentSettings.recordWithControls) { mutableStateOf(currentSettings.recordWithControls) }
    var recordThermalGuard by remember(currentSettings.recordThermalGuard) { mutableStateOf(currentSettings.recordThermalGuard) }
    var showRendererDialog by remember { mutableStateOf(false) }
    var rendererListTick by remember { mutableIntStateOf(0) }
    var showRendererConfigDialog by remember { mutableStateOf(false) }
    var selectedConfigRenderer by remember { mutableStateOf<NuxRendererInfo?>(null) }
    var showAdrenoWarningDialog by remember { mutableStateOf(false) }

    var appLanguage by remember { mutableStateOf(Tr.choice) }
    var bgAnimate by remember(currentSettings.homeBgAnimate) { mutableStateOf(currentSettings.homeBgAnimate) }
    var bgPauseOnPowerSave by remember(currentSettings.homeBgPauseOnPowerSave) { mutableStateOf(currentSettings.homeBgPauseOnPowerSave) }
    var bgSound by remember(currentSettings.homeBgSound) { mutableStateOf(currentSettings.homeBgSound) }
    var bgVolume by remember(currentSettings.homeBgVolume) { mutableIntStateOf(currentSettings.homeBgVolume) }
    var ambientOn by remember(currentSettings.homeAmbientEnabled) { mutableStateOf(currentSettings.homeAmbientEnabled) }
    var ambientIntensity by remember(currentSettings.homeAmbientIntensity) { mutableIntStateOf(currentSettings.homeAmbientIntensity) }

    val totalRamMb = remember { SettingsManager.getTotalDeviceMemoryMb(context) }
    val maxAllocatableRam = remember(totalRamMb) { (totalRamMb * 0.85f).toInt().coerceAtLeast(1024) }
    val cpuCores = remember { SettingsManager.getCpuCoreCount() }
    val showRefreshLock = remember { DeviceProfiles.supportsHighRefresh(context) }

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
            hotbarTouchEnabled = hotbarTouch,
            buttonLookDrag = buttonLookDrag,
            touchControllerEnabled = touchControllerEnabled,
            touchControllerExclusive = touchControllerExclusive,
            gamepadEmulation = gamepadEmulation,
            mouseSizeDp = mouseSizeDp,
            physicalMouseMode = physicalMouseMode,
            resolutionRatio = resolutionRatio,
            autoOptimizeMinecraft = autoOptimizeMC,
            sustainedPerformanceMode = sustainedPerf,
            selectedRenderer = selectedRenderer,
            vulkanDriver = vulkanDriver,
            graphicsApi = graphicsApi,
            zinkPreferSystemDriver = zinkPreferSystemDriver,
            vsyncInZink = vsyncInZink,
            recordQuality = recordQuality,
            recordFps = recordFps,
            recordMic = recordMic,
            recordWithControls = recordWithControls,
            recordThermalGuard = recordThermalGuard,
            homeBgAnimate = bgAnimate,
            homeBgPauseOnPowerSave = bgPauseOnPowerSave,
            homeBgSound = bgSound,
            homeBgVolume = bgVolume,
            homeAmbientEnabled = ambientOn,
            homeAmbientIntensity = ambientIntensity
        )
        SettingsManager.updateSettings(context, updated)
    }

    // Tab aktif (0 = Memori, 1 = Grafis, 2 = Kontrol, 3 = Tampilan, 4 = Pembaruan). Isi tiap tab digulir sendiri-sendiri.
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
            // Tab samping (bisa digulir bila layar pendek)
            Column(
                modifier = Modifier
                    .width(128.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SettingTab(icon = Icons.Outlined.Memory, label = "Memori", selected = section == 0) { section = 0 }
                SettingTab(icon = Icons.Outlined.Speed, label = "Grafis", selected = section == 1) { section = 1 }
                SettingTab(icon = Icons.Outlined.TouchApp, label = "Kontrol", selected = section == 2) { section = 2 }
                SettingTab(icon = Icons.Outlined.Palette, label = "Tampilan", selected = section == 3) { section = 3 }
                SettingTab(icon = Icons.Outlined.SystemUpdate, label = "Pembaruan", selected = section == 4) { section = 4 }
                SettingTab(icon = Icons.Outlined.Description, label = "Log game", selected = section == 5) { section = 5 }
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
                        // Pilihan mode GC untuk HP RAM kecil (di HP RAM besar tidak menggambar apa pun).
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

                        // Preset sekali ketuk untuk HP yang cepat panas atau baterainya boros.
                        PowerSaverCard()

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
                        // Rentang 40-125% kelipatan 5, sama dengan slider "Resolusi render" di menu dalam game.
                        SettingSliderRow(
                            title = "Skala resolusi layar",
                            desc = "Lebih kecil = FPS lebih tinggi, gambar lebih buram.",
                            valueText = "$resolutionRatio%",
                            value = resolutionRatio.toFloat(),
                            valueRange = 40f..125f,
                            steps = 16,
                            onValueChange = {
                                resolutionRatio = (it / 5f).roundToInt() * 5
                                commitSettings()
                            }
                        )
                        // Kunci 60 Hz untuk layar 90/120 Hz.
                        if (showRefreshLock) {
                            SettingDivider()
                            SettingSwitchRow(
                                title = "Kunci layar 60 Hz",
                                desc = "Layar 90/120 Hz dikunci 60 Hz saat main supaya gerak lebih rata dan sedikit lebih hemat panas. Cocok bila game jarang mencapai 90 FPS. HUD FPS menampilkan Hz yang aktif.",
                                checked = currentSettings.lockRefresh60,
                                onCheckedChange = { on ->
                                    SettingsManager.updateSettings(context, SettingsManager.settings.value.copy(lockRefresh60 = on))
                                }
                            )
                        }
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
                        SettingDivider()
                        // Rekam layar bawaan (tombol Rekam ada di menu dalam game). Bawaannya sengaja hemat.
                        SettingChoiceRow(
                            title = "Kualitas rekaman layar",
                            desc = "Makin kecil makin ringan untuk HP. Hemat 540p, Ringan 720p, Tajam 1080p.",
                            options = listOf("eco" to "Hemat", "light" to "Ringan", "high" to "Tajam"),
                            selected = recordQuality,
                            onSelect = {
                                recordQuality = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingChoiceRow(
                            title = "FPS rekaman",
                            desc = "30 fps jauh lebih ringan. 60 fps hanya untuk HP yang kuat.",
                            options = listOf(30 to "30 fps", 60 to "60 fps"),
                            selected = recordFps,
                            onSelect = {
                                recordFps = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingChoiceRow(
                            title = "Isi rekaman",
                            desc = "Game saja: tombol layar disembunyikan dari video (tetap bisa disentuh). Dengan tombol: tombol layar ikut terekam. Bisa diganti juga dari menu dalam game.",
                            options = listOf(false to "Game saja", true to "Dengan tombol"),
                            selected = recordWithControls,
                            onSelect = {
                                recordWithControls = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSwitchRow(
                            title = "Rekam suara mikrofon",
                            desc = "Menambahkan suara dari mikrofon ke rekaman. Suara game belum ikut terekam.",
                            checked = recordMic,
                            onCheckedChange = {
                                recordMic = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSwitchRow(
                            title = "Hentikan rekaman saat HP terlalu panas",
                            desc = "Menyimpan rekaman dan berhenti otomatis supaya game tidak ikut melambat karena panas.",
                            checked = recordThermalGuard,
                            onCheckedChange = {
                                recordThermalGuard = it
                                commitSettings()
                            }
                        )
                    }

                    3 -> {
                        // Bahasa tampilan. Berlaku langsung, tanpa memulai ulang aplikasi.
                        SettingChoiceRow(
                            title = "Bahasa",
                            desc = "Bahasa teks di launcher. Ikuti sistem memakai bahasa HP.",
                            options = listOf(
                                Tr.SYSTEM to "Ikuti sistem",
                                Tr.ID to "Bahasa Indonesia",
                                Tr.EN to "English",
                                Tr.ES to "Espa\u00f1ol",
                                Tr.VI to "Ti\u1ebfng Vi\u1ec7t"
                            ),
                            selected = appLanguage,
                            onSelect = {
                                appLanguage = it
                                Tr.setLanguage(context, it)
                            }
                        )
                        SettingDivider()

                        // Latar Beranda: foto (paling ringan), GIF, atau video; suara dan ambient
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingRowText(
                                title = "Latar Beranda",
                                desc = "Foto paling ringan, GIF bergerak dengan ringan, video paling halus (HD, bisa 60 fps) tapi memakai baterai lebih banyak.",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            NuxButton(
                                onClick = onChangeBackground,
                                backgroundColor = NuxColors.Yellow,
                                cornerRadius = 20.dp,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Ganti latar",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        SettingDivider()
                        SettingSwitchRow(
                            title = "Gerakkan GIF dan video",
                            desc = "Matikan untuk menampilkan gambar diam saja. Paling hemat baterai dan RAM.",
                            checked = bgAnimate,
                            onCheckedChange = {
                                bgAnimate = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSwitchRow(
                            title = "Jeda saat Hemat Baterai",
                            desc = "GIF dan video berhenti otomatis selama mode Hemat Baterai HP menyala.",
                            checked = bgPauseOnPowerSave,
                            onCheckedChange = {
                                bgPauseOnPowerSave = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSwitchRow(
                            title = "Suara video",
                            desc = "Bawaan mati. Otomatis bisu bila aplikasi lain sedang memutar musik.",
                            checked = bgSound,
                            onCheckedChange = {
                                bgSound = it
                                commitSettings()
                            }
                        )
                        if (bgSound) {
                            SettingSliderRow(
                                title = "Volume video",
                                valueText = "$bgVolume%",
                                value = bgVolume.toFloat(),
                                valueRange = 0f..100f,
                                steps = 19,
                                onValueChange = {
                                    bgVolume = ((it / 5f).roundToInt() * 5).coerceIn(0, 100)
                                    commitSettings()
                                }
                            )
                        }
                        SettingDivider()
                        SettingSwitchRow(
                            title = "Ambient",
                            desc = "Cahaya lembut di sidebar dan tepi kartu Beranda. Warnanya mengikuti isi GIF atau video. Tidak berlaku untuk foto.",
                            checked = ambientOn,
                            onCheckedChange = {
                                ambientOn = it
                                commitSettings()
                            }
                        )
                        if (ambientOn) {
                            val level = listOf(30, 60, 90).minByOrNull { kotlin.math.abs(it - ambientIntensity) } ?: 60
                            SettingChoiceRow(
                                title = "Kekuatan ambient",
                                desc = null,
                                options = listOf(30 to "Halus", 60 to "Sedang", 90 to "Kuat"),
                                selected = level,
                                onSelect = {
                                    ambientIntensity = it
                                    commitSettings()
                                }
                            )
                        }
                    }

                    4 -> {
                        // Versi terpasang, tombol Periksa pembaruan, dan sakelar pengecekan otomatis
                        UpdateSettingsSection()
                    }

                    5 -> {
                        // Log sesi game yang tersimpan otomatis, siap dibagikan ke pengembang
                        GameLogSettingsSection()
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
                        // Cadangkan atau bagikan tata letak tombol sebagai berkas .json
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp)
                        ) {
                            SettingRowText(
                                title = "Cadangan & bagikan layout",
                                desc = "Simpan tata letak tombol ke berkas .json untuk dicadangkan atau dibagikan, atau muat dari berkas milik orang lain.",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NuxButton(
                                    onClick = {
                                        val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                                            .format(java.util.Date())
                                        exportLayoutLauncher.launch("amber-controls-$stamp.json")
                                    },
                                    backgroundColor = Color.Transparent,
                                    cornerRadius = 20.dp,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FileUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Ekspor",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                NuxButton(
                                    onClick = { importLayoutLauncher.launch(arrayOf("*/*")) },
                                    backgroundColor = Color.Transparent,
                                    cornerRadius = 20.dp,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Impor",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
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
                        SettingSwitchRow(
                            title = "Hotbar sentuh",
                            desc = "Ketuk hotbar di bagian bawah layar untuk memilih slot, seperti di Minecraft Bedrock. Posisi slot mengikuti GUI scale game.",
                            checked = hotbarTouch,
                            onCheckedChange = {
                                hotbarTouch = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSwitchRow(
                            title = "Geser kamera dari tombol",
                            desc = "Menahan tombol pukul, pakai, atau lompat lalu menggeser jari ikut memutar kamera. Matikan supaya pandangan tidak bergeser saat bertarung (PvP).",
                            checked = buttonLookDrag,
                            onCheckedChange = {
                                buttonLookDrag = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSwitchRow(
                            title = "Dukungan TouchController",
                            desc = "Eksperimental. Bila mod TouchController terpasang dan tersambung, sentuhan saat bermain juga diteruskan ke mod. Tombol dan mouse virtual launcher tetap aktif, jadi keduanya bisa dipakai bersamaan.",
                            checked = touchControllerEnabled,
                            onCheckedChange = {
                                touchControllerEnabled = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSwitchRow(
                            title = "TouchController saja",
                            desc = "Sembunyikan tombol dan mouse virtual launcher (hanya gerigi menu yang tersisa) selama mod tersambung. Dipakai bila kontrol ganda saling bentrok, misalnya kamera berputar dua kali.",
                            checked = touchControllerExclusive,
                            onCheckedChange = {
                                touchControllerExclusive = it
                                commitSettings()
                            }
                        )
                        SettingDivider()
                        SettingSwitchRow(
                            title = "Dukungan controller",
                            desc = "Colok controller lalu main: stick kiri jalan, stick kanan kamera, A lompat, RT pukul, LT pasang, Y inventaris, Start = Esc. Di menu stick menggerakkan kursor. Bila mod controller (mis. Controlify) terdeteksi, controller langsung diserahkan ke mod.",
                            checked = gamepadEmulation,
                            onCheckedChange = {
                                gamepadEmulation = it
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
                    hotbarTouch = reset.hotbarTouchEnabled
                    buttonLookDrag = reset.buttonLookDrag
                    touchControllerEnabled = reset.touchControllerEnabled
                    touchControllerExclusive = reset.touchControllerExclusive
                    gamepadEmulation = reset.gamepadEmulation
                    mouseSizeDp = reset.mouseSizeDp
                    physicalMouseMode = reset.physicalMouseMode
                    resolutionRatio = reset.resolutionRatio
                    autoOptimizeMC = reset.autoOptimizeMinecraft
                    sustainedPerf = reset.sustainedPerformanceMode
                    recordQuality = reset.recordQuality
                    recordFps = reset.recordFps
                    recordMic = reset.recordMic
                    recordWithControls = reset.recordWithControls
                    recordThermalGuard = reset.recordThermalGuard
                    bgAnimate = reset.homeBgAnimate
                    bgPauseOnPowerSave = reset.homeBgPauseOnPowerSave
                    bgSound = reset.homeBgSound
                    bgVolume = reset.homeBgVolume
                    ambientOn = reset.homeAmbientEnabled
                    ambientIntensity = reset.homeAmbientIntensity
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
            GamepadWindowHook()
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
    pendingImport?.let { pending ->
        NuxImportLayoutDialog(
            buttonCount = pending.buttons.size,
            macroCommands = pending.macroCommands,
            onConfirm = {
                pendingImport = null
                scope.launch {
                    val count = withContext(Dispatchers.IO) {
                        ControlLayoutManager.importLayout(context, pending.buttons).size
                    }
                    Toast.makeText(context, "Layout kontrol diimpor ($count tombol)", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { pendingImport = null }
        )
    }

    if (showAboutDialog) {
        NuxAboutDialog(
            onDismissRequest = { showAboutDialog = false }
        )
    }
}
