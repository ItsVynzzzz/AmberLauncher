package com.israadev.nuxlauncher.ui.screens

import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.israadev.nuxlauncher.ui.theme.AmberStyle
import com.israadev.nuxlauncher.ui.theme.AMBER_ACCENTS
import com.israadev.nuxlauncher.core.background.HomeVideo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import android.graphics.BitmapFactory
import android.graphics.Bitmap
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
    onOpenMods: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
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
    var accentId by remember(currentSettings.accentColor) { mutableStateOf(currentSettings.accentColor) }
    var uiStyleId by remember(currentSettings.uiStyle) { mutableStateOf(currentSettings.uiStyle) }
    var bgDim by remember(currentSettings.bgDim) { mutableIntStateOf(currentSettings.bgDim) }
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
            homeAmbientIntensity = ambientIntensity,
            accentColor = accentId,
            uiStyle = uiStyleId,
            bgDim = bgDim
        )
        SettingsManager.updateSettings(context, updated)
    }

    // Kategori aktif: 0 = Game, 1 = Tampilan, 2 = Kontrol, 3 = Tentang. Isinya digulir sendiri.
    var section by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()
    var showLanguageDialog by remember { mutableStateOf(false) }
    val contentScroll = rememberScrollState()
    LaunchedEffect(section, searching) { contentScroll.scrollTo(0) }

    val languageOptions = listOf(
        Tr.SYSTEM to "Ikuti sistem",
        Tr.ID to "Bahasa Indonesia",
        Tr.EN to "English",
        Tr.ES to "Espa\u00f1ol",
        Tr.VI to "Ti\u1ebfng Vi\u1ec7t"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)
    ) {
        // Baris atas: navigasi di kiri, kolom cari di kanan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AmberNavPill(
                active = "settings",
                activeLabel = "Pengaturan",
                onBack = onNavigateBack,
                onMods = onOpenMods,
                onAccounts = onOpenAccounts,
                onSettings = {}
            )
            Spacer(modifier = Modifier.weight(1f))
            AmberSearchPill(
                value = query,
                onValueChange = { query = it },
                placeholder = "Cari pengaturan...",
                modifier = Modifier.width(230.dp)
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Kategori (sidebar)
            Column(
                modifier = Modifier
                    .width(148.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf(
                    Triple(0, "Game", Icons.Outlined.ViewInAr),
                    Triple(1, "Tampilan", Icons.Outlined.Image),
                    Triple(2, "Kontrol", Icons.Outlined.SportsEsports),
                    Triple(3, "Tentang", Icons.Outlined.Info)
                ).forEach { (index, label, icon) ->
                    AmberSideItem(
                        icon = icon,
                        label = label,
                        selected = !searching && section == index,
                        onClick = {
                            section = index
                            query = ""
                        }
                    )
                }
                if (!searching) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${totalRamMb / 1024} GB RAM · $cpuCores inti CPU",
                        color = AmberUi.TextDim,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }

            // Isi kategori: tiap kelompok berupa kartu panjang. Saat mencari, semua baris yang cocok
            // dari semua kategori tampil, masing-masing di kartunya sendiri.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(contentScroll)
            ) {
                CompositionLocalProvider(
                    LocalSettingsFilter provides (if (searching) SettingsFilter(query) else null)
                ) {
                    // ===================== GAME =====================
                    if (searching || section == 0) {
                        // Profil khusus perangkat: hanya muncul di HP yang terdaftar, di HP lain tidak menggambar apa pun.
                        if (!searching) DeviceProfileCard()

                        SettingsGroup("Memori") {
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
                            SettingFrame("Argumen JVM tambahan", "Opsional, untuk pengguna mahir. Salah isi bisa membuat game gagal jalan.") {
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
                        }

                        if (!searching) PowerSaverCard()

                        SettingsGroup("Grafis") {
                        val activeRendererInfo = remember(selectedRenderer) {
                            NuxRendererRegistry.findRendererById(selectedRenderer)
                        }
                            SettingValueRow(
                                title = "Renderer",
                                desc = activeRendererInfo.summary,
                                value = activeRendererInfo.displayName,
                                onClick = { showRendererDialog = true }
                            )
                            if (activeRendererInfo.isConfigurable) {
                                SettingDivider()
                                SettingActionRow(
                                    title = "Konfigurasi renderer",
                                    desc = "Pengaturan khusus untuk renderer yang sedang dipakai.",
                                    actionLabel = "Atur",
                                    onClick = {
                                        selectedConfigRenderer = activeRendererInfo
                                        showRendererConfigDialog = true
                                    }
                                )
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
                        }

                        SettingsGroup("Perekaman layar") {
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
                    }

                    // ===================== TAMPILAN =====================
                    if (searching || section == 1) {
                        SettingsGroup("Latar") {
                            SettingBackgroundRow(onChange = onChangeBackground)
                            SettingDivider()
                            SettingSliderRow(
                                title = "Redupkan latar",
                                desc = "Seberapa gelap latar di layar selain Home.",
                                valueText = "$bgDim%",
                                value = bgDim.toFloat(),
                                valueRange = 0f..100f,
                                steps = 19,
                                onValueChange = {
                                    bgDim = ((it / 5f).roundToInt() * 5).coerceIn(0, 100)
                                    commitSettings()
                                }
                            )
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
                        }

                        SettingsGroup("Tema") {
                            SettingFrame("Aksen warna", "Warna tombol dan penanda terpilih di seluruh launcher.") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp)
                                ) {
                                    SettingRowText(
                                        title = "Aksen warna",
                                        desc = "Warna tombol dan penanda terpilih di seluruh launcher."
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AMBER_ACCENTS.forEach { accent ->
                                            val picked = accent.id == accentId
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(accent.base, CircleShape)
                                                    .border(
                                                        if (picked) 2.5.dp else 1.dp,
                                                        if (picked) Color.White else AmberUi.BorderStrong,
                                                        CircleShape
                                                    )
                                                    .clickable {
                                                        accentId = accent.id
                                                        AmberUi.setAccent(accent.id)
                                                        commitSettings()
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (picked) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Check,
                                                        contentDescription = accent.label,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        text = AMBER_ACCENTS.firstOrNull { it.id == accentId }?.label ?: "",
                                        color = AmberUi.TextDim,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                            SettingDivider()
                            SettingChoiceRow(
                                title = "Gaya tampilan",
                                desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    "Default memakai tema AmberLauncher sendiri. Liquid Glass memburamkan latar di belakang panel dan tombol."
                                } else {
                                    "Liquid Glass butuh Android 12 ke atas. Di HP ini otomatis memakai Default."
                                },
                                options = AmberStyle.entries.map { it.id to it.label },
                                selected = uiStyleId,
                                onSelect = {
                                    uiStyleId = it
                                    AmberUi.setStyle(it)
                                    commitSettings()
                                }
                            )
                        }

                        SettingsGroup("Bahasa") {
                            SettingValueRow(
                                title = "Bahasa",
                                desc = "Bahasa teks di launcher. Berlaku langsung tanpa memulai ulang.",
                                value = languageOptions.firstOrNull { it.first == appLanguage }?.second ?: "",
                                onClick = { showLanguageDialog = true }
                            )
                        }
                    }

                    // ===================== KONTROL =====================
                    if (searching || section == 2) {
                        if (!searching) SettingsGroup("Tata letak kontrol") {
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
                                backgroundColor = AmberUi.Accent,
 borderColor = AmberUi.AccentEdge,
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

                        }
                        SettingsGroup("Input dan sensitivitas") {
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

                    // ===================== TENTANG =====================
                    if (searching || section == 3) {
                        if (!searching) {
                            SettingsGroup("Pembaruan") {
                                UpdateSettingsSection()
                            }
                            SettingsGroup("Log game") {
                                GameLogSettingsSection()
                            }
                        }
                        SettingsGroup("Aplikasi") {
                            SettingActionRow(
                                title = "Tentang & lisensi",
                                desc = "Informasi aplikasi, lisensi GPL-3.0, dan kredit.",
                                actionLabel = "Buka",
                                onClick = { showAboutDialog = true }
                            )
                            SettingDivider()
                            SettingActionRow(
                                title = "Reset ke default",
                                desc = "Semua pengaturan kembali ke bawaan. Perubahan lain tersimpan otomatis.",
                                actionLabel = "Reset default",
                                danger = true,
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
                    accentId = reset.accentColor
                    uiStyleId = reset.uiStyle
                    bgDim = reset.bgDim
                    AmberUi.setAccent(reset.accentColor)
                    AmberUi.setStyle(reset.uiStyle)
                    Toast.makeText(context, "Pengaturan di-reset ke default!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Pilih bahasa
        if (showLanguageDialog) {
            AmberPickerDialog(
                title = "Bahasa",
                options = languageOptions,
                selectedId = appLanguage,
                onPick = { picked ->
                    appLanguage = picked
                    Tr.setLanguage(context, picked)
                    showLanguageDialog = false
                },
                onDismiss = { showLanguageDialog = false }
            )
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

/** Satu kelompok pengaturan: judul kecil + kartu panjang. Saat mencari, baris tampil sendiri-sendiri. */
@Composable
private fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    if (LocalSettingsFilter.current != null) {
        Column(content = content)
    } else {
        AmberSectionLabel(label)
        AmberCard(content = content)
    }
}

/** Baris latar: pratinjau kecil latar saat ini, keterangan, dan tombol Ganti latar. */
@Composable
private fun SettingBackgroundRow(onChange: () -> Unit) {
    val context = LocalContext.current
    val thumbState = produceState<ImageBitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val video = HomeVideo.file(context)
                val still = File(context.filesDir, "home_bg")
                val bmp: Bitmap? = if (video.exists() && video.length() > 0) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(video.absolutePath)
                        retriever.getFrameAtTime(0)
                    } finally {
                        retriever.release()
                    }
                } else if (still.exists() && still.length() > 0) {
                    BitmapFactory.decodeFile(still.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })
                } else null
                bmp?.asImageBitmap()
            }.getOrNull()
        }
    }
    val thumb = thumbState.value
    val shape = RoundedCornerShape(14.dp)
    SettingFrame("Latar", "Foto, GIF, atau video") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .height(80.dp)
                    .clip(shape)
                    .border(1.dp, AmberUi.BorderStrong, shape)
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = com.israadev.nuxlauncher.R.drawable.mc_hero_bg),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Latar",
                    color = AmberUi.Text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Foto, GIF, atau video",
                    color = AmberUi.TextDim,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                AmberPillButton(
                    text = "Ganti latar",
                    icon = Icons.Outlined.Image,
                    filled = false,
                    onClick = onChange
                )
            }
        }
    }
}
