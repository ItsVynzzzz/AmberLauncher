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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NuxColors.Background)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        // =========================================================================
        // 1. TOP HEADER BAR (Minimalist Cyber-Glass matching Home, Accounts & Mods)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Back to Dashboard Button (Standard 26.dp)
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NuxColors.SurfaceElevated, RoundedCornerShape(6.dp))
                        .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                        .clickable { onNavigateBack() }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Kembali",
                            tint = NuxColors.SageGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "DASHBOARD",
                            color = NuxColors.DarkGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Text(
                    text = "PENGATURAN",
                    color = NuxColors.DarkGray,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 0.8.sp
                )

            }

            // Hardware Specs Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NuxColors.SoftLime, RoundedCornerShape(6.dp))
                        .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                        .clickable { showAboutDialog = true }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "TENTANG & LISENSI",
                        color = NuxColors.DarkGray,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NuxColors.SurfaceElevated, RoundedCornerShape(6.dp))
                        .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${totalRamMb / 1024} GB RAM",
                        color = NuxColors.SageGreen,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NuxColors.SurfaceElevated, RoundedCornerShape(6.dp))
                        .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$cpuCores CORES",
                        color = NuxColors.GrayNeutral,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // =========================================================================
        // 2. MAIN CONTENT PANELS (Preferensi Game)
        // =========================================================================
        run {
            // =====================================================================
            // PREFERENSI GAME (Memori/Java/Grafis + Kontrol/Mouse)
            // =====================================================================
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // LEFT COLUMN: Memory & Java + Graphics
                NuxCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    backgroundColor = NuxColors.SurfaceElevated,
                    borderColor = NuxColors.CardBorder,
                    borderWidth = 1.dp,
                    cornerRadius = 8.dp,
                    fillMaxHeight = true
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Profil khusus perangkat: hanya muncul di HP yang terdaftar (Oppo A3x 4G),
                        // di HP lain tidak menggambar apa pun.
                        DeviceProfileCard()

                        // Section 1: Java Memory (RAM)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Memory,
                                contentDescription = null,
                                tint = NuxColors.SageGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ALOKASI RAM & JVM MEMORY",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = NuxColors.DarkGray
                            )
                        }

                        // RAM Slider & Value Display
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(6.dp))
                                .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Maksimum RAM (-Xmx)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuxColors.DarkGray
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (ramMb > (totalRamMb * 0.75f)) NuxColors.Coral.copy(alpha = 0.2f) else NuxColors.YellowPale,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (ramMb > (totalRamMb * 0.75f)) NuxColors.Coral.copy(alpha = 0.4f) else NuxColors.CardBorder,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$ramMb MB (${String.format("%.1f", ramMb / 1024f)} GB)",
                                        color = if (ramMb > (totalRamMb * 0.75f)) NuxColors.Coral else NuxColors.SageGreen,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Slider(
                                value = ramMb.toFloat(),
                                onValueChange = {
                                    ramMb = ((it / 128).roundToInt() * 128).coerceIn(512, maxAllocatableRam)
                                    commitSettings()
                                },
                                valueRange = 512f..maxAllocatableRam.toFloat(),
                                steps = ((maxAllocatableRam - 512) / 128) - 1,
                                colors = SliderDefaults.colors(
                                    thumbColor = NuxColors.MintGreen,
                                    activeTrackColor = NuxColors.ForestGreen,
                                    inactiveTrackColor = NuxColors.CardBorder
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Quick RAM Presets
                            Text(
                                text = "Preset Cepat:",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = NuxColors.GrayNeutral
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(1024, 1536, 2048, 3072, 4096).forEach { preset ->
                                    if (preset <= maxAllocatableRam) {
                                        val isSelected = ramMb == preset
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(24.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (isSelected) NuxColors.ForestGreen else NuxColors.SurfaceElevated,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) NuxColors.MintGreen else NuxColors.CardBorder,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .clickable {
                                                    ramMb = preset
                                                    commitSettings()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (preset >= 1024) "${preset / 1024}G" else "${preset}M",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) NuxColors.Ink else NuxColors.GrayNeutral
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Custom JVM Arguments
                        Column {
                            Text(
                                text = "Custom JVM Arguments (Opsional)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = NuxColors.GrayNeutral
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            NuxTextField(
                                value = customJvmArgs,
                                onValueChange = {
                                    customJvmArgs = it
                                    commitSettings()
                                },
                                placeholder = "Contoh: -XX:+UseG1GC -Dminecraft.applet.TargetDirectory=..."
                            )
                        }

                        HorizontalDivider(color = NuxColors.CardBorder, thickness = 0.5.dp)

                        // Section 2: Graphics & Rendering
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Speed,
                                contentDescription = null,
                                tint = NuxColors.SageGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GRAFIK & OPTIMASI MINECRAFT",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = NuxColors.DarkGray
                            )
                        }

                        // Renderer Selection Card
                        val activeRendererInfo = remember(selectedRenderer) {
                            NuxRendererRegistry.findRendererById(selectedRenderer)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(NuxColors.SurfaceInput)
                                .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            // Top Row: Section label & GANTI button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Renderer Grafik (Backend)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuxColors.DarkGray
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (activeRendererInfo.isConfigurable) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(NuxColors.SurfaceElevated)
                                                .border(1.dp, NuxColors.CardBorder, RoundedCornerShape(4.dp))
                                                .clickable {
                                                    selectedConfigRenderer = activeRendererInfo
                                                    showRendererConfigDialog = true
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Konfigurasi Renderer",
                                                tint = NuxColors.SageGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .height(24.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(NuxColors.SurfaceElevated)
                                            .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(4.dp))
                                            .clickable { showRendererDialog = true }
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "GANTI",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NuxColors.SageGreen
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Active Renderer Name & Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = activeRendererInfo.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NuxColors.SageGreen
                                )
                                Box(
                                    modifier = Modifier
                                        .background(NuxColors.YellowPale, RoundedCornerShape(4.dp))
                                        .border(1.dp, NuxColors.CardBorder, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = activeRendererInfo.badge,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NuxColors.SageGreen,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = activeRendererInfo.summary,
                                fontSize = 9.5.sp,
                                color = NuxColors.GrayNeutral,
                                lineHeight = 13.sp
                            )
                        }

                        // Auto Optimize options.txt Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(6.dp))
                                .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Optimize options.txt",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuxColors.DarkGray
                                )
                                Text(
                                    text = "Otomatis optimasi setting visual (Clouds, Shadows) untuk FPS maksimal",
                                    fontSize = 9.5.sp,
                                    color = NuxColors.GrayNeutral
                                )
                            }
                            Switch(
                                checked = autoOptimizeMC,
                                onCheckedChange = {
                                    autoOptimizeMC = it
                                    commitSettings()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NuxColors.Ink,
                                    checkedTrackColor = NuxColors.ForestGreen,
                                    uncheckedTrackColor = NuxColors.SurfaceElevated
                                )
                            )
                        }

                        // Resolution Scaling
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(6.dp))
                                .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Skala Resolusi Layar",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuxColors.DarkGray
                                )
                                Text(
                                    text = "$resolutionRatio%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NuxColors.SageGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(50, 75, 100, 125).forEach { ratio ->
                                    val isSelected = resolutionRatio == ratio
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(24.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isSelected) NuxColors.ForestGreen else NuxColors.SurfaceElevated,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) NuxColors.MintGreen else NuxColors.CardBorder,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                resolutionRatio = ratio
                                                commitSettings()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$ratio%",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) NuxColors.Ink else NuxColors.GrayNeutral
                                        )
                                    }
                                }
                            }
                        }

                        // Driver Vulkan Selector
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(6.dp))
                                .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Driver Vulkan",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NuxColors.DarkGray
                            )
                            Text(
                                text = "Pilih implementasi driver Vulkan yang digunakan sistem/game",
                                fontSize = 9.5.sp,
                                color = NuxColors.GrayNeutral
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("auto" to "Auto", "system" to "Sistem", "turnip" to "Turnip").forEach { (id, label) ->
                                    val isSelected = vulkanDriver == id
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(24.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isSelected) NuxColors.ForestGreen else NuxColors.SurfaceElevated,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) NuxColors.MintGreen else NuxColors.CardBorder,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                vulkanDriver = id
                                                commitSettings()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) NuxColors.Ink else NuxColors.GrayNeutral
                                        )
                                    }
                                }
                            }
                        }

                        // API Grafis Selector
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(6.dp))
                                .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "API Grafis",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NuxColors.DarkGray
                            )
                            Text(
                                text = "Atur API grafis yang digunakan oleh Minecraft modern 26.2+",
                                fontSize = 9.5.sp,
                                color = NuxColors.GrayNeutral
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("DEFAULT" to "Default", "OPENGL" to "OpenGL", "VULKAN" to "Vulkan").forEach { (id, label) ->
                                    val isSelected = graphicsApi == id
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(24.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isSelected) NuxColors.ForestGreen else NuxColors.SurfaceElevated,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) NuxColors.MintGreen else NuxColors.CardBorder,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                graphicsApi = id
                                                commitSettings()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) NuxColors.Ink else NuxColors.GrayNeutral
                                        )
                                    }
                                }
                            }
                        }

                        // Gunakan Driver Vulkan Sistem (Zink)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(6.dp))
                                .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gunakan Driver Vulkan Sistem",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuxColors.DarkGray
                                )
                                Text(
                                    text = "Paksa menggunakan driver Vulkan bawaan sistem HP (bukan Turnip). Mempengaruhi perender Zink.",
                                    fontSize = 9.5.sp,
                                    color = NuxColors.GrayNeutral
                                )
                            }
                            Switch(
                                checked = zinkPreferSystemDriver,
                                onCheckedChange = { checked ->
                                    if (checked && NuxVersionUtils.isAdrenoGPU()) {
                                        showAdrenoWarningDialog = true
                                    } else {
                                        zinkPreferSystemDriver = checked
                                        commitSettings()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NuxColors.Ink,
                                    checkedTrackColor = NuxColors.ForestGreen,
                                    uncheckedTrackColor = NuxColors.SurfaceElevated
                                )
                            )
                        }

                        // Sinkronisasi Vertikal Zink (V-Sync)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(6.dp))
                                .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sinkronisasi Vertikal Zink",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuxColors.DarkGray
                                )
                                Text(
                                    text = "Aktifkan sinkronisasi vertikal (V-Sync) untuk perender Kopper Zink via antarmuka sistem.",
                                    fontSize = 9.5.sp,
                                    color = NuxColors.GrayNeutral
                                )
                            }
                            Switch(
                                checked = vsyncInZink,
                                onCheckedChange = {
                                    vsyncInZink = it
                                    commitSettings()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NuxColors.Ink,
                                    checkedTrackColor = NuxColors.ForestGreen,
                                    uncheckedTrackColor = NuxColors.SurfaceElevated
                                )
                            )
                        }
                    }
                }

                // RIGHT COLUMN: In-Game Mouse & Touch Controls
                NuxCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    backgroundColor = NuxColors.SurfaceElevated,
                    borderColor = NuxColors.CardBorder,
                    borderWidth = 1.dp,
                    cornerRadius = 8.dp,
                    fillMaxHeight = true
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header for Mouse settings
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Mouse,
                                contentDescription = null,
                                tint = NuxColors.SageGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "KONTROL MOUSE & SENTUHAN IN-GAME",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = NuxColors.DarkGray
                            )
                        }

                        Text(
                            text = "Pengaturan mode mouse untuk menu Minecraft (Inventory, Pause Menu, Chat, Crafting).",
                            fontSize = 9.5.sp,
                            color = NuxColors.GrayNeutral
                        )

                        // CUSTOM CONTROLS / GUI EDITOR ACTION CARD
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(6.dp))
                                .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Tune,
                                        contentDescription = null,
                                        tint = NuxColors.SageGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "KUSTOMISASI KONTROL VIRTUAL",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = NuxColors.DarkGray
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Ubah tata letak tombol, ukuran, transparansi, scroll hotbar, dan mapping input key Minecraft.",
                                    fontSize = 9.5.sp,
                                    color = NuxColors.GrayNeutral,
                                    lineHeight = 12.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                NuxButton(
                                    onClick = onOpenGuiEditor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp),
                                    backgroundColor = NuxColors.ForestGreen,
                                    contentColor = NuxColors.DarkGray,
                                    cornerRadius = 6.dp
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = NuxColors.Ink, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("BUKA GUI EDITOR", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = NuxColors.Ink)
                                }
                            }
                        }

                        // Mouse Control Mode Selection (SLIDE vs CLICK)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Trackpad / Slide Card
                            val isSlide = mouseMode == "SLIDE"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSlide) NuxColors.YellowPale else NuxColors.SurfaceInput,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSlide) NuxColors.MintGreen else NuxColors.CardBorder,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        mouseMode = "SLIDE"
                                        commitSettings()
                                    }
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.TouchApp,
                                            contentDescription = null,
                                            tint = if (isSlide) NuxColors.SageGreen else NuxColors.DarkGray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "SLIDE (Trackpad)",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isSlide) NuxColors.SageGreen else NuxColors.DarkGray
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Geser jari untuk kursor virtual, ketuk layar untuk klik item.",
                                        fontSize = 9.sp,
                                        color = NuxColors.GrayNeutral,
                                        lineHeight = 11.sp
                                    )
                                }
                            }

                            // Direct Touch / Click Card
                            val isClick = mouseMode == "CLICK"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isClick) NuxColors.YellowPale else NuxColors.SurfaceInput,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isClick) NuxColors.MintGreen else NuxColors.CardBorder,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        mouseMode = "CLICK"
                                        commitSettings()
                                    }
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.NearMe,
                                            contentDescription = null,
                                            tint = if (isClick) NuxColors.SageGreen else NuxColors.DarkGray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "CLICK (Direct)",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isClick) NuxColors.SageGreen else NuxColors.DarkGray
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Sentuh tombol atau slot menu langsung di mana jari menekan.",
                                        fontSize = 9.sp,
                                        color = NuxColors.GrayNeutral,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }

                        // In-Game Camera Look Sensitivity
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(6.dp))
                                .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Sensitivitas Kamera Game (Rotasi)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuxColors.DarkGray
                                )
                                Box(
                                    modifier = Modifier
                                        .background(NuxColors.YellowPale, RoundedCornerShape(4.dp))
                                        .border(1.dp, NuxColors.CardBorder, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$captureSensitivity%",
                                        color = NuxColors.SageGreen,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Slider(
                                value = captureSensitivity.toFloat(),
                                onValueChange = {
                                    captureSensitivity = it.roundToInt()
                                    commitSettings()
                                },
                                valueRange = 50f..300f,
                                steps = 25,
                                colors = SliderDefaults.colors(
                                    thumbColor = NuxColors.MintGreen,
                                    activeTrackColor = NuxColors.ForestGreen,
                                    inactiveTrackColor = NuxColors.CardBorder
                                )
                            )
                        }

                        // Virtual Cursor Sensitivity
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(6.dp))
                                .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Sensitivitas Kursor Virtual (Menu)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuxColors.DarkGray
                                )
                                Box(
                                    modifier = Modifier
                                        .background(NuxColors.YellowPale, RoundedCornerShape(4.dp))
                                        .border(1.dp, NuxColors.CardBorder, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$cursorSensitivity%",
                                        color = NuxColors.SageGreen,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Slider(
                                value = cursorSensitivity.toFloat(),
                                onValueChange = {
                                    cursorSensitivity = it.roundToInt()
                                    commitSettings()
                                },
                                valueRange = 50f..300f,
                                steps = 25,
                                colors = SliderDefaults.colors(
                                    thumbColor = NuxColors.MintGreen,
                                    activeTrackColor = NuxColors.ForestGreen,
                                    inactiveTrackColor = NuxColors.CardBorder
                                )
                            )
                        }

                        // Virtual Cursor Size with Preview
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(6.dp))
                                .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ukuran Kursor Virtual",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuxColors.DarkGray
                                )
                                Text(
                                    text = "${mouseSizeDp}dp",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NuxColors.SageGreen
                                )
                            }
                            Slider(
                                value = mouseSizeDp.toFloat(),
                                onValueChange = {
                                    mouseSizeDp = it.roundToInt()
                                    commitSettings()
                                },
                                valueRange = 16f..48f,
                                steps = 16,
                                colors = SliderDefaults.colors(
                                    thumbColor = NuxColors.MintGreen,
                                    activeTrackColor = NuxColors.ForestGreen,
                                    inactiveTrackColor = NuxColors.CardBorder
                                )
                            )
                        }

                        // Physical Mouse Mode Card (Zalith Style: Auto-Hide Virtual Cursor)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(6.dp))
                                .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mode Mouse Fisik (Auto-Hide)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuxColors.DarkGray
                                )
                                Text(
                                    text = "Sembunyikan kursor virtual otomatis jika mouse eksternal tersambung",
                                    fontSize = 9.sp,
                                    color = NuxColors.GrayNeutral
                                )
                            }
                            Switch(
                                checked = physicalMouseMode,
                                onCheckedChange = {
                                    physicalMouseMode = it
                                    commitSettings()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NuxColors.Ink,
                                    checkedTrackColor = NuxColors.ForestGreen,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = NuxColors.CardBorder
                                )
                            )
                        }

                        // Device Info & Reset Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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
                                    mouseSizeDp = reset.mouseSizeDp
                                    physicalMouseMode = reset.physicalMouseMode
                                    resolutionRatio = reset.resolutionRatio
                                    autoOptimizeMC = reset.autoOptimizeMinecraft
                                    sustainedPerf = reset.sustainedPerformanceMode
                                    Toast.makeText(context, "Pengaturan di-reset ke default!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                backgroundColor = NuxColors.Coral.copy(alpha = 0.15f),
                                contentColor = NuxColors.Coral,
                                borderColor = NuxColors.Coral.copy(alpha = 0.35f),
                                cornerRadius = 6.dp
                            ) {
                                Text(
                                    text = "RESET DEFAULT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    color = NuxColors.Coral
                                )
                            }

                            NuxButton(
                                onClick = {
                                    commitSettings()
                                    Toast.makeText(context, "Semua pengaturan tersimpan!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                backgroundColor = NuxColors.ForestGreen,
                                contentColor = NuxColors.DarkGray,
                                cornerRadius = 6.dp
                            ) {
                                Text(
                                    text = "SIMPAN",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    color = NuxColors.Ink
                                )
                            }
                        }
                    }
                }
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
