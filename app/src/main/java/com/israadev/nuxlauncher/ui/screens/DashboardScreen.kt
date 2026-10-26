package com.israadev.nuxlauncher.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import com.israadev.nuxlauncher.ui.i18n.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.israadev.nuxlauncher.R
import com.israadev.nuxlauncher.core.account.AccountManager
import com.israadev.nuxlauncher.core.background.BgImportResult
import com.israadev.nuxlauncher.core.background.HomeVideo
import com.israadev.nuxlauncher.core.crash.CrashManager
import com.israadev.nuxlauncher.core.download.MinecraftDownloader
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.launch.GameLauncher
import com.israadev.nuxlauncher.core.mods.NuxAddonImportManager
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.models.UserAccount
import com.israadev.nuxlauncher.core.renderer.NuxRendererInfo
import com.israadev.nuxlauncher.core.renderer.NuxRendererRegistry
import com.israadev.nuxlauncher.core.runtime.JavaRuntimeManager
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.ui.components.AmbientGlow
import com.israadev.nuxlauncher.ui.components.AmbientSampler
import com.israadev.nuxlauncher.ui.components.AmbientState
import com.israadev.nuxlauncher.ui.components.HomeInfo
import com.israadev.nuxlauncher.ui.components.HomeVideoLayer
import com.israadev.nuxlauncher.ui.components.NuxButton
import com.israadev.nuxlauncher.ui.components.NuxDialog
import com.israadev.nuxlauncher.ui.components.NUX_NAV_ITEMS
import com.israadev.nuxlauncher.ui.components.NuxSidebar
import com.israadev.nuxlauncher.ui.components.rememberIsResumed
import com.israadev.nuxlauncher.ui.components.rememberPowerSaveMode
import com.israadev.nuxlauncher.ui.dialogs.NuxAboutDialog
import com.israadev.nuxlauncher.ui.dialogs.NuxAddInstanceDialog
import com.israadev.nuxlauncher.ui.dialogs.NuxAddonImportDialog
import com.israadev.nuxlauncher.ui.dialogs.NuxCrashDialog
import com.israadev.nuxlauncher.ui.dialogs.NuxDeleteInstanceDialog
import com.israadev.nuxlauncher.ui.dialogs.NuxDownloadProgressDialog
import com.israadev.nuxlauncher.ui.dialogs.NuxEditInstanceDialog
import com.israadev.nuxlauncher.ui.dialogs.NuxRendererWarningDialog
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxReadable
import com.israadev.nuxlauncher.ui.theme.NuxSizes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf("home") }

    // Controller: LB/RB berpindah antar tab (Home, Akun, Mod, Pengaturan).
    DisposableEffect(Unit) {
        val ids = NUX_NAV_ITEMS.map { it.id }
        val listener: (Int) -> Unit = { step ->
            val index = ids.indexOf(currentTab)
            if (index >= 0) currentTab = ids[(index + step + ids.size) % ids.size]
        }
        com.israadev.nuxlauncher.core.controls.GamepadNav.bumperListener = listener
        onDispose {
            if (com.israadev.nuxlauncher.core.controls.GamepadNav.bumperListener === listener) {
                com.israadev.nuxlauncher.core.controls.GamepadNav.bumperListener = null
            }
        }
    }

    val activeCrash by CrashManager.activeCrash.collectAsState()

    val instances by InstanceManager.instances.collectAsState()
    val selectedInstance by InstanceManager.selectedInstance.collectAsState()
    val currentAccount by AccountManager.currentAccount.collectAsState()
    val launcherSettings by SettingsManager.settings.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditInstanceDialog by remember { mutableStateOf(false) }
    var showInstancePicker by remember { mutableStateOf(false) }
    var instanceToDelete by remember { mutableStateOf<Instance?>(null) }
    var pendingLaunchInstance by remember { mutableStateOf<Instance?>(null) }
    var unsupportedRendererInfo by remember { mutableStateOf<NuxRendererInfo?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val pendingImport by NuxAddonImportManager.pendingImport.collectAsState()

    // Download progress state
    var isDownloading by remember { mutableStateOf(false) }
    var downloadTargetName by remember { mutableStateOf("") }
    // Kemajuan unduhan disimpan di objek tersendiri yang hanya dibaca dialog,
    // sehingga tiap kemajuan tidak menggambar ulang seluruh layar Home.
    val dl = remember { DownloadUiState() }

    val downloader = remember { MinecraftDownloader(context) }

    // Latar belakang Home yang bisa diganti (foto, GIF, atau video dari galeri). Untuk video,
    // bgFile berisi satu bingkai diam (poster) dan videonya disimpan terpisah di bgVideoFile.
    val bgFile = remember { File(context.filesDir, BG_FILE_NAME) }
    val bgVideoFile = remember { HomeVideo.file(context) }
    var hasCustomBg by remember { mutableStateOf(bgFile.exists()) }
    var hasVideoBg by remember { mutableStateOf(bgFile.exists() && bgVideoFile.exists()) }
    var bgVersion by remember { mutableIntStateOf(0) }
    var showBgDialog by remember { mutableStateOf(false) }

    // Animasi latar (GIF/video) boleh jalan atau tidak, plus suara video dan cahaya ambient.
    val powerSave = rememberPowerSaveMode()
    val animateBackground = launcherSettings.homeBgAnimate &&
        !(launcherSettings.homeBgPauseOnPowerSave && powerSave)
    val ambientActive = animateBackground && launcherSettings.homeAmbientEnabled
    val videoVolume = if (launcherSettings.homeBgSound) {
        val p = launcherSettings.homeBgVolume.coerceIn(0, 100) / 100f
        p * p
    } else {
        0f
    }
    val ambientState = remember { AmbientState() }
    var cardBounds by remember { mutableStateOf<Rect?>(null) }

    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Memproses berkas...", Toast.LENGTH_SHORT).show()
            scope.launch {
                val result = withContext(Dispatchers.IO) { importMedia(context, uri, bgFile, bgVideoFile) }
                when (result) {
                    BgImportResult.IMAGE, BgImportResult.VIDEO -> {
                        hasCustomBg = bgFile.exists()
                        hasVideoBg = bgVideoFile.exists()
                        bgVersion++
                        val msg = if (result == BgImportResult.VIDEO) {
                            "Video dipasang sebagai latar. Suara dan ambient diatur di Pengaturan > Tampilan"
                        } else {
                            "Latar belakang diganti"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                    BgImportResult.TOO_BIG -> Toast.makeText(
                        context,
                        "Terlalu besar: foto atau GIF maksimal 30 MB, video maksimal 200 MB dan resolusi 2560 px",
                        Toast.LENGTH_LONG
                    ).show()
                    BgImportResult.UNSUPPORTED -> Toast.makeText(
                        context,
                        "Berkas tidak bisa dipakai. Pakai foto, GIF, atau video MP4 (H.264)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Status unduhan instance terpilih: dihitung ulang hanya saat instance berganti
    // atau saat proses unduh selesai (hindari cek file di tiap recomposition).
    val isInstanceReady = remember(selectedInstance, isDownloading) {
        selectedInstance?.let { it.isDownloaded && InstanceManager.isInstanceDownloaded(context, it) } ?: false
    }

    // Tombol PLAY / UNDUH: unduh dulu bila belum lengkap, siapkan Java bila belum ada, lalu luncurkan.
    fun startInstance(inst: Instance) {
        val account = currentAccount
        if (account == null) {
            Toast.makeText(context, "Silakan buat atau pilih akun terlebih dahulu!", Toast.LENGTH_SHORT).show()
            currentTab = "accounts"
            return
        }

        val isFullyDownloaded = inst.isDownloaded && InstanceManager.isInstanceDownloaded(context, inst)
        if (!isFullyDownloaded) {
            val targetRuntime = JavaRuntimeManager.getRecommendedRuntime(inst.mcVersion)
            isDownloading = true
            downloadTargetName = inst.name
            dl.progress = 0f
            dl.message = "Menyiapkan OpenJDK (${JavaRuntimeManager.getRuntimeDisplayName(targetRuntime)})..."

            scope.launch {
                JavaRuntimeManager.extractRuntime(context, targetRuntime) { p, msg ->
                    dl.progress = p
                    dl.message = msg
                }

                val res = downloader.downloadInstance(inst) { p, msg ->
                    dl.progress = p
                    dl.message = msg
                }
                isDownloading = false
                if (res.isSuccess) {
                    Toast.makeText(context, "Instalasi selesai! Tekan PLAY untuk bermain.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Gagal mengunduh: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            val targetRuntime = JavaRuntimeManager.getRecommendedRuntime(inst.mcVersion)
            if (!JavaRuntimeManager.isRuntimeInstalled(context, targetRuntime)) {
                isDownloading = true
                downloadTargetName = inst.name
                dl.progress = 0f
                dl.message = "Menyiapkan OpenJDK (${JavaRuntimeManager.getRuntimeDisplayName(targetRuntime)})..."
                scope.launch {
                    val extRes = JavaRuntimeManager.extractRuntime(context, targetRuntime) { p, msg ->
                        dl.progress = p
                        dl.message = msg
                    }
                    isDownloading = false
                    if (extRes.isSuccess) {
                        Toast.makeText(context, "Meluncurkan ${inst.name}...", Toast.LENGTH_SHORT).show()
                        GameLauncher.launch(context, inst, account)
                    } else {
                        Toast.makeText(context, "Gagal menyiapkan OpenJDK: ${extRes.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
                return
            }

            val currentRendererInfo = NuxRendererRegistry.findRendererById(launcherSettings.selectedRenderer)
            val isSupported = NuxRendererRegistry.isSupportedForVersion(currentRendererInfo, inst.mcVersion)
            if (!isSupported) {
                unsupportedRendererInfo = currentRendererInfo
                pendingLaunchInstance = inst
            } else {
                Toast.makeText(context, "Meluncurkan ${inst.name}...", Toast.LENGTH_SHORT).show()
                GameLauncher.launch(context, inst, account)
            }
        }
    }

    if (currentTab == "gui_editor") {
        CustomGuiEditorScreen(
            onNavigateBack = { currentTab = "settings" },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Latar hitam polos memenuhi layar (area poni/kamera ikut menyatu), lalu satu kartu
        // panel bersudut bulat dengan tepi putih tipis ala kaca. Kuning hanya dipakai sebagai aksen.
        val cutout = WindowInsets.displayCutout.asPaddingValues()
        val layoutDir = LocalLayoutDirection.current
        val leftInset = cutout.calculateLeftPadding(layoutDir)
        val rightInset = cutout.calculateRightPadding(layoutDir)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NuxColors.Ink)
        ) {
            // Cahaya ambient di sekeliling kartu: hanya di Home dan hanya saat GIF/video bergerak.
            LaunchedEffect(ambientActive, currentTab) {
                if (!ambientActive || currentTab != "home") ambientState.colors = null
            }
            if (currentTab == "home" && ambientActive) {
                AmbientGlow(
                    state = ambientState,
                    intensity = launcherSettings.homeAmbientIntensity,
                    bounds = cardBounds,
                    cornerRadius = 24.dp
                )
            }

            Row(modifier = Modifier.fillMaxSize()) {
                NuxSidebar(
                    activeTab = currentTab,
                    onTabSelected = { tabId -> currentTab = tabId },
                    startInset = leftInset,
                    onOpenAbout = { showAboutDialog = true }
                )

                // Kartu panel: jarak sama di atas, kanan, dan bawah (8dp), jadi tingginya sejajar sidebar.
                val cardShape = RoundedCornerShape(24.dp)
                val cardEdge = remember {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.42f),
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.22f)
                        )
                    )
                }
                // Latar layar Akun/Mod/Pengaturan: hitam hangat dengan sedikit cahaya kuning di atas.
                val cardFill = remember {
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1606), NuxColors.Background, NuxColors.Background)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned {
                                val r = it.boundsInRoot()
                                cardBounds = r
                                if (r.height > 0f) ambientState.aspect = r.width / r.height
                            }
                            .clip(cardShape)
                            .background(cardFill, cardShape)
                            .border(1.dp, cardEdge, cardShape)
                    ) {
                        when (currentTab) {
                            "accounts" -> NuxReadable {
                                AccountsScreen(
                                    onNavigateBack = { currentTab = "home" },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            "mods" -> NuxReadable {
                                ModsScreen(
                                    onNavigateBack = { currentTab = "home" },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            "settings" -> NuxReadable {
                                SettingsScreen(
                                    onNavigateBack = { currentTab = "home" },
                                    onOpenGuiEditor = { currentTab = "gui_editor" },
                                    onChangeBackground = {
                                        currentTab = "home"
                                        showBgDialog = true
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                val rendererLabel = remember(launcherSettings.selectedRenderer) {
                                    val info = NuxRendererRegistry.findRendererById(launcherSettings.selectedRenderer)
                                    HomeInfo.rendererLabel(info.id, info.displayName)
                                }
                                HomeScene(
                                    instance = selectedInstance,
                                    isReady = isInstanceReady,
                                    account = currentAccount,
                                    rendererLabel = rendererLabel,
                                    onOpenAccounts = { currentTab = "accounts" },
                                    onOpenAbout = { showAboutDialog = true },
                                    onPickInstance = { showInstancePicker = true },
                                    onPlay = { selectedInstance?.let { startInstance(it) } },
                                    onCreateInstance = { showAddDialog = true },
                                    onOpenFolder = {
                                        selectedInstance?.let { inst ->
                                            Toast.makeText(context, "Membuka folder instance...", Toast.LENGTH_SHORT).show()
                                            InstanceManager.openInstanceFolder(context, inst)
                                        }
                                    },
                                    onEditInstance = { showEditInstanceDialog = true },
                                    backgroundFile = if (hasCustomBg) bgFile else null,
                                    backgroundVersion = bgVersion,
                                    backgroundVideo = if (hasVideoBg && hasCustomBg) bgVideoFile else null,
                                    animateBackground = animateBackground,
                                    videoVolume = videoVolume,
                                    ambient = if (ambientActive) ambientState else null,
                                    onChangeBackground = { showBgDialog = true },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                if (rightInset > 0.dp) {
                    // Ruang poni/kamera di kanan (warnanya sama dengan latar hitam).
                    Spacer(modifier = Modifier.width(rightInset).fillMaxHeight())
                }
            }
        }

        // Dialog ganti latar belakang
        if (showBgDialog) {
            BackgroundDialog(
                hasCustom = hasCustomBg,
                onPick = {
                    showBgDialog = false
                    pickBackground.launch(arrayOf("image/*", "video/*"))
                },
                onReset = {
                    showBgDialog = false
                    bgFile.delete()
                    bgVideoFile.delete()
                    hasCustomBg = false
                    hasVideoBg = false
                    bgVersion++
                    Toast.makeText(context, "Latar belakang dikembalikan ke bawaan", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showBgDialog = false }
            )
        }

        // Pemilih instance (ganti, tambah, ubah, hapus, buka folder)
        if (showInstancePicker) {
            InstancePickerDialog(
                instances = instances,
                selectedId = selectedInstance?.id,
                onSelect = { inst ->
                    InstanceManager.selectInstance(inst)
                    showInstancePicker = false
                },
                onOpenFolder = { inst ->
                    showInstancePicker = false
                    Toast.makeText(context, "Membuka folder instance...", Toast.LENGTH_SHORT).show()
                    InstanceManager.openInstanceFolder(context, inst)
                },
                onEdit = { inst ->
                    showInstancePicker = false
                    InstanceManager.selectInstance(inst)
                    showEditInstanceDialog = true
                },
                onDelete = { inst ->
                    showInstancePicker = false
                    instanceToDelete = inst
                },
                onAdd = {
                    showInstancePicker = false
                    showAddDialog = true
                },
                onDismiss = { showInstancePicker = false }
            )
        }

        // Add Instance Dialog
        if (showAddDialog) {
            NuxAddInstanceDialog(
                onDismiss = { showAddDialog = false },
                onInstanceCreated = { newInst ->
                    showAddDialog = false
                    InstanceManager.createInstance(context, newInst)
                    Toast.makeText(context, "Instance ${newInst.name} berhasil dibuat!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Edit Instance Dialog
        if (showEditInstanceDialog) {
            selectedInstance?.let { inst ->
                NuxEditInstanceDialog(
                    instance = inst,
                    onDismiss = { showEditInstanceDialog = false },
                    onInstanceUpdated = { updated ->
                        InstanceManager.updateInstance(context, updated)
                    },
                    onInstanceDeleted = { toDelete ->
                        InstanceManager.deleteInstance(context, toDelete.id)
                        Toast.makeText(context, "Instance ${toDelete.name} berhasil dihapus!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Delete Instance Confirmation Dialog
        instanceToDelete?.let { inst ->
            NuxDeleteInstanceDialog(
                instance = inst,
                onConfirm = {
                    instanceToDelete = null
                    InstanceManager.deleteInstance(context, inst.id)
                    Toast.makeText(context, "Instance ${inst.name} berhasil dihapus!", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { instanceToDelete = null }
            )
        }

        // Download Progress Dialog
        if (isDownloading) {
            DownloadDialogHost(name = downloadTargetName, state = dl)
        }

        // Unsupported Renderer Warning Dialog (Persis seperti Zalith)
        val warnRenderer = unsupportedRendererInfo
        val launchInst = pendingLaunchInstance
        if (warnRenderer != null && launchInst != null) {
            NuxRendererWarningDialog(
                renderer = warnRenderer,
                mcVersion = launchInst.mcVersion,
                onConfirm = {
                    val toLaunch = pendingLaunchInstance
                    val account = currentAccount
                    unsupportedRendererInfo = null
                    pendingLaunchInstance = null
                    if (toLaunch != null && account != null) {
                        Toast.makeText(context, "Meluncurkan ${toLaunch.name}...", Toast.LENGTH_SHORT).show()
                        GameLauncher.launch(context, toLaunch, account)
                    }
                },
                onDismiss = {
                    unsupportedRendererInfo = null
                    pendingLaunchInstance = null
                }
            )
        }

        // Game Crash Popup Dialog
        activeCrash?.let { crash ->
            NuxCrashDialog(
                crashInfo = crash,
                onDismiss = {
                    CrashManager.dismissCrash(context)
                }
            )
        }

        // External Addon Import Dialog (Open With from File Manager)
        pendingImport?.let { importItem ->
            NuxAddonImportDialog(
                pendingImport = importItem,
                onDismiss = {
                    NuxAddonImportManager.clearPendingImport()
                }
            )
        }

        // About & Open Source Licenses Dialog (GPL-3.0 & Zalith Compliance)
        if (showAboutDialog) {
            NuxAboutDialog(
                onDismissRequest = { showAboutDialog = false }
            )
        }
    }
}

/** True setelah animasi masuk Home diputar sekali; kembali ke Home dari tab lain tidak mengulangnya. */
private var homeIntroPlayed = false

/**
 * Layar Home: gambar latar penuh, nama instance besar di kiri bawah dengan tombol PLAY kuning,
 * tombol Folder/Ubah yang halus, dan status RAM + renderer kecil di kanan bawah. Tidak ada
 * kotak atau dock; teks terbaca lewat gradien gelap di tepi.
 */
@Composable
private fun HomeScene(
    instance: Instance?,
    isReady: Boolean,
    account: UserAccount?,
    rendererLabel: String,
    onOpenAccounts: () -> Unit,
    onOpenAbout: () -> Unit,
    onPickInstance: () -> Unit,
    onPlay: () -> Unit,
    onCreateInstance: () -> Unit,
    onOpenFolder: () -> Unit,
    onEditInstance: () -> Unit,
    backgroundFile: File?,
    backgroundVersion: Int,
    backgroundVideo: File?,
    animateBackground: Boolean,
    videoVolume: Float,
    ambient: AmbientState?,
    onChangeBackground: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Satu animasi masuk (sekali per peluncuran aplikasi): tiap bagian muncul bertahap.
    val enter = remember { Animatable(if (homeIntroPlayed) 1f else 0f) }
    val enterState = enter.asState()
    LaunchedEffect(Unit) {
        if (enter.value < 1f) {
            homeIntroPlayed = true
            enter.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
        }
    }

    val freeRamMb = rememberFreeRamMb()
    val config = LocalConfiguration.current
    val compact = config.screenWidthDp < 640
    val nameSize = if (config.screenHeightDp < 380) 36.sp else 46.sp
    val nameShadow = remember { Shadow(NuxColors.Ink.copy(alpha = 0.45f), Offset(0f, 2f), 14f) }

    Box(modifier = modifier) {
        // Latar foto/GIF/video memenuhi panel
        HomeBackground(
            customFile = backgroundFile,
            version = backgroundVersion,
            videoFile = backgroundVideo,
            animate = animateBackground,
            videoVolume = videoVolume,
            ambient = ambient,
            modifier = Modifier.fillMaxSize()
        )

        // Gradien atas + bawah, lalu gradien kiri: teks putih terbaca di latar apa pun
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to NuxColors.Ink.copy(alpha = 0.45f),
                        0.24f to Color.Transparent,
                        0.34f to Color.Transparent,
                        0.66f to NuxColors.Ink.copy(alpha = 0.50f),
                        1f to NuxColors.Ink.copy(alpha = 0.90f)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to NuxColors.Ink.copy(alpha = 0.50f),
                        0.58f to Color.Transparent
                    )
                )
        )

        // ---- BARIS ATAS ----
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 16.dp)
                .homeEnter(enterState, 0f, 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Akun (ketuk = ganti / tambah akun)
            val chipShape = RoundedCornerShape(20.dp)
            Row(
                modifier = Modifier
                    .clip(chipShape)
                    .background(NuxColors.Ink.copy(alpha = 0.38f), chipShape)
                    .border(1.dp, Color.White.copy(alpha = 0.26f), chipShape)
                    .clickable { onOpenAccounts() }
                    .defaultMinSize(minHeight = 40.dp)
                    .padding(start = 5.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (account != null) {
                    NuxAccountAvatar(
                        account = account,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(NuxColors.Yellow, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = NuxColors.Ink,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = account?.username ?: "Tambah akun",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Ganti akun",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Label wajib GPL-3.0 (ketuk = Tentang & Lisensi)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenAbout() }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(NuxColors.Amber, CircleShape)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "UNOFFICIAL MODIFIED VERSION",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Tentang & Lisensi",
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(13.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Ganti latar belakang
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(NuxColors.Ink.copy(alpha = 0.38f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.26f), CircleShape)
                    .clickable { onChangeBackground() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "Ganti latar belakang",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ---- KIRI BAWAH: instance aktif + aksi ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (compact) 1f else 0.68f)
                .padding(start = 30.dp, end = if (compact) 30.dp else 0.dp, bottom = 28.dp)
        ) {
            if (instance != null) {
                val javaLabel = remember(instance.id, instance.mcVersion, instance.javaRuntime) {
                    val rt = if (instance.javaRuntime != "auto") instance.javaRuntime
                    else JavaRuntimeManager.getRecommendedRuntime(instance.mcVersion)
                    rt.replace("jre-", "Java ")
                }

                // Nama besar (ketuk = ganti instance)
                Row(
                    modifier = Modifier
                        .homeEnter(enterState, 0f, 14.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onPickInstance() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = instance.name,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = nameSize,
                        lineHeight = nameSize * 1.1f,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = LocalTextStyle.current.copy(shadow = nameShadow),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.White.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Ganti instance",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Loader (kuning) | versi Minecraft | Java
                Row(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .homeEnter(enterState, 0.1f, 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = com.israadev.nuxlauncher.core.forge.ForgeFlavor.label(instance.loader),
                        color = NuxColors.Yellow,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    HomeMetaDivider()
                    Text(
                        text = "Minecraft ${instance.mcVersion}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    HomeMetaDivider()
                    Text(
                        text = javaLabel,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }

                // PLAY / UNDUH + Folder + Ubah
                Row(
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .homeEnter(enterState, 0.22f, 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NuxButton(
                        onClick = onPlay,
                        backgroundColor = NuxColors.Yellow,
                        contentColor = NuxColors.Ink,
                        cornerRadius = 32.dp,
                        shadowOffset = 4.dp,
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isReady) Icons.Default.PlayArrow else Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isReady) "PLAY" else "UNDUH",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            letterSpacing = 0.8.sp,
                            maxLines = 1
                        )
                    }
                    if (!compact) {
                        Spacer(modifier = Modifier.width(10.dp))
                        HomeRoundButton(
                            icon = Icons.Outlined.Folder,
                            description = "Buka folder instance",
                            onClick = onOpenFolder
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        HomeRoundButton(
                            icon = Icons.Outlined.Tune,
                            description = "Ubah instance",
                            onClick = onEditInstance
                        )
                    }
                }
            } else {
                // Belum ada instance
                Text(
                    text = "Belum ada instance",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = nameSize,
                    lineHeight = nameSize * 1.1f,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(shadow = nameShadow),
                    modifier = Modifier.homeEnter(enterState, 0f, 14.dp)
                )
                Text(
                    text = "Buat instance Minecraft pertamamu untuk mulai bermain.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .homeEnter(enterState, 0.1f, 14.dp)
                )
                Row(
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .homeEnter(enterState, 0.22f, 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NuxButton(
                        onClick = onCreateInstance,
                        backgroundColor = NuxColors.Yellow,
                        contentColor = NuxColors.Ink,
                        cornerRadius = 32.dp,
                        shadowOffset = 4.dp,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TAMBAH",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // ---- KANAN BAWAH: status RAM dan renderer (teks kecil, tanpa kotak) ----
        if (!compact) {
            val ramColor = when (HomeInfo.ramLevel(freeRamMb)) {
                2 -> NuxColors.SuccessGreen
                1 -> NuxColors.Amber
                else -> NuxColors.ErrorRed
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 30.dp, bottom = 32.dp)
                    .homeEnter(enterState, 0.35f, 0.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(ramColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = HomeInfo.ramText(freeRamMb),
                        color = Color.White.copy(alpha = 0.75f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                Text(
                    text = "Renderer $rendererLabel",
                    color = Color.White.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** Muncul bertahap: transparan dan sedikit di bawah sebelum [start], lalu naik ke tempatnya. */
private fun Modifier.homeEnter(enter: State<Float>, start: Float, rise: Dp): Modifier =
    graphicsLayer {
        val t = HomeInfo.stagger(enter.value, start)
        alpha = t
        translationY = (1f - t) * rise.toPx()
        // Tanpa lapisan terpisah, supaya bayangan keras tombol di luar kotaknya tidak terpotong.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }

/** Garis tegak tipis pemisah loader | versi | Java. */
@Composable
private fun HomeMetaDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .width(1.dp)
            .height(12.dp)
            .background(Color.White.copy(alpha = 0.35f))
    )
}

/** Tombol bulat 52dp, latar putih transparan dan tepi tipis (aksi kedua di Home). */
@Composable
private fun HomeRoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.26f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Sisa RAM HP (dibulatkan 100 MB), diperbarui tiap 5 detik selama Home tampil. */
@Composable
private fun rememberFreeRamMb(): Int {
    val context = LocalContext.current.applicationContext
    val initial = remember { HomeInfo.ramBucket(SettingsManager.getAvailableDeviceMemoryMb(context)) }
    val state = produceState(initialValue = initial) {
        while (true) {
            delay(5000L)
            value = HomeInfo.ramBucket(SettingsManager.getAvailableDeviceMemoryMb(context))
        }
    }
    return state.value
}

private const val BG_FILE_NAME = "home_bg"
private const val BG_MAX_BYTES = 30L * 1024 * 1024
private const val BG_MAX_SIDE = 1600
private const val BG_ANIM_SIDE = 1280   // GIF/WebP bergerak: sisi terpanjang pas dengan lebar panel Home (tidak diperbesar kasar)

/** Status unduhan; hanya dibaca oleh dialog unduhan. */
private class DownloadUiState {
    var progress by mutableFloatStateOf(0f)
    var message by mutableStateOf("")
}

@Composable
private fun DownloadDialogHost(name: String, state: DownloadUiState) {
    NuxDownloadProgressDialog(
        instanceName = name,
        progress = state.progress,
        message = state.message
    )
}

/** Hasil muat latar: drawable null berarti gagal. */
private class BgState(val drawable: Drawable?)

/**
 * Decode foto/GIF/WebP jadi Drawable. Foto dikecilkan (sisi terpanjang <= 1600 px) supaya hemat RAM.
 * GIF/WebP animasi menjadi AnimatedImageDrawable (Android 9 ke atas) dan diubah ukurannya saat
 * decode ke sisi terpanjang 1280 px, kira-kira selebar panel Home. Kalau lebih kecil dari itu ikut
 * diperhalus saat decode, bukan diperbesar kasar oleh ImageView (itu yang tampak kotak-kotak).
 */
private fun decodeBackground(file: File): Drawable? = try {
    if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val longSide = maxOf(w, h)
            val mime = info.mimeType
            val maybeAnimated = mime.equals("image/gif", ignoreCase = true) ||
                mime.equals("image/webp", ignoreCase = true)
            val target = if (maybeAnimated) BG_ANIM_SIDE else BG_MAX_SIDE
            // Foto: hanya dikecilkan. GIF/WebP: dibawa ke ukuran target (naik atau turun).
            val needsResize = if (maybeAnimated) longSide != target else longSide > target
            if (needsResize) {
                val scale = target.toFloat() / longSide
                decoder.setTargetSize(maxOf(1, (w * scale).toInt()), maxOf(1, (h * scale).toInt()))
            }
        }
    } else {
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        BitmapFactory.decodeFile(file.absolutePath, opts)?.let {
            BitmapDrawable(android.content.res.Resources.getSystem(), it)
        }
    }
} catch (e: Throwable) {
    null
}

/** Salin gambar pilihan ke penyimpanan aplikasi, uji decode, lalu pasang sebagai latar. */
private fun importBackground(context: Context, uri: Uri, target: File): Boolean {
    val tmp = File(target.parentFile, "$BG_FILE_NAME.tmp")
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return false
        input.use { ins ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > BG_MAX_BYTES) throw java.io.IOException("terlalu besar")
                    out.write(buf, 0, n)
                }
            }
        }
        if (decodeBackground(tmp) == null) {
            tmp.delete()
            return false
        }
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    } catch (e: Throwable) {
        tmp.delete()
        false
    }
}

/**
 * Memasang berkas pilihan sebagai latar. Video disimpan bersama poster-nya; foto/GIF menghapus
 * video lama supaya tidak ada dua latar sekaligus.
 */
private fun importMedia(context: Context, uri: Uri, imageTarget: File, videoTarget: File): BgImportResult {
    val mime = try {
        context.contentResolver.getType(uri)
    } catch (_: Throwable) {
        null
    }
    if (mime != null && mime.startsWith("video/")) {
        return HomeVideo.importVideo(context, uri, videoTarget, imageTarget)
    }
    if (importBackground(context, uri, imageTarget)) {
        videoTarget.delete()
        return BgImportResult.IMAGE
    }
    // Jenis berkas tidak dikenali oleh pemilih file: coba sebagai video sebelum menyerah.
    if (mime == null || mime == "application/octet-stream") {
        val result = HomeVideo.importVideo(context, uri, videoTarget, imageTarget)
        if (result != BgImportResult.UNSUPPORTED) return result
    }
    return BgImportResult.UNSUPPORTED
}

/**
 * Latar Home: gambar bawaan, foto/GIF pilihan pengguna, atau video. Video diputar di bawah dan
 * posternya (satu bingkai diam) menutupinya sampai bingkai pertama tampil; bila video gagal diputar
 * atau animasi dimatikan, poster itu tetap jadi latarnya.
 */
@Composable
private fun HomeBackground(
    customFile: File?,
    version: Int,
    videoFile: File?,
    animate: Boolean,
    videoVolume: Float,
    ambient: AmbientState?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var videoFailed by remember(videoFile, version) { mutableStateOf(false) }
    var videoReady by remember(videoFile, version) { mutableStateOf(false) }
    LaunchedEffect(videoFailed) {
        if (videoFailed) {
            Toast.makeText(
                context,
                "Video tidak bisa diputar di HP ini, latar memakai gambar diam",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    if (videoFile != null && animate && !videoFailed) {
        val posterAlpha by animateFloatAsState(
            targetValue = if (videoReady) 0f else 1f,
            animationSpec = tween(durationMillis = 450),
            label = "homeVideoPoster"
        )
        Box(modifier = modifier) {
            HomeVideoLayer(
                file = videoFile,
                volume = videoVolume,
                playing = true,
                ambient = ambient,
                onFrameReady = { videoReady = it },
                onFailed = { videoFailed = true },
                modifier = Modifier.fillMaxSize()
            )
            if (posterAlpha > 0.01f) {
                HomeStillBackground(
                    customFile = customFile,
                    version = version,
                    animate = false,
                    ambient = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = posterAlpha }
                )
            }
        }
    } else {
        HomeStillBackground(
            customFile = customFile,
            version = version,
            animate = animate,
            ambient = ambient,
            modifier = modifier
        )
    }
}

/** Gambar bawaan, atau foto/GIF pilihan pengguna bila ada (GIF bergerak bila [animate]). */
@Composable
private fun HomeStillBackground(
    customFile: File?,
    version: Int,
    animate: Boolean,
    ambient: AmbientState?,
    modifier: Modifier = Modifier
) {
    if (customFile == null) {
        Image(
            painter = painterResource(id = R.drawable.mc_hero_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    val state by produceState<BgState?>(initialValue = null, customFile, version) {
        value = BgState(withContext(Dispatchers.IO) { decodeBackground(customFile) })
    }
    val st = state
    val drawable = st?.drawable
    when {
        st == null -> Box(modifier = modifier.background(NuxColors.Ink))
        drawable == null -> Image(
            painter = painterResource(id = R.drawable.mc_hero_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        else -> {
            // GIF: berhenti otomatis saat keluar dari layar Home
            DisposableEffect(drawable) {
                onDispose {
                    if (Build.VERSION.SDK_INT >= 28 && drawable is AnimatedImageDrawable) drawable.stop()
                }
            }

            // Cahaya ambient dari GIF: ambil warna bingkai yang sedang tampil beberapa kali per detik.
            val resumed = rememberIsResumed()
            if (ambient != null && animate && resumed && Build.VERSION.SDK_INT >= 28 && drawable is AnimatedImageDrawable) {
                val sampleBitmap = remember(drawable) { AmbientSampler.newBitmap() }
                val samplePixels = remember(drawable) { AmbientSampler.newPixels() }
                LaunchedEffect(drawable, ambient) {
                    delay(400)
                    while (true) {
                        try {
                            AmbientSampler.fromDrawable(drawable, sampleBitmap, samplePixels, ambient.aspect)
                                ?.let { ambient.colors = it }
                        } catch (_: Throwable) {
                        }
                        delay(AmbientSampler.INTERVAL_MS)
                    }
                }
            }

            AndroidView(
                factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                update = { iv ->
                    iv.setImageDrawable(drawable)
                    if (Build.VERSION.SDK_INT >= 28 && drawable is AnimatedImageDrawable) {
                        drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        if (animate) drawable.start() else drawable.stop()
                    }
                },
                modifier = modifier
            )
        }
    }
}

/** Dialog kecil: pilih foto, GIF, atau video dari galeri, atau kembali ke latar bawaan. */
@Composable
private fun BackgroundDialog(
    hasCustom: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    NuxDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 460.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LATAR BELAKANG",
                    color = NuxColors.DarkGray,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f)
                )
                PickerIconButton(
                    icon = Icons.Default.Close,
                    description = "Tutup",
                    tint = NuxColors.DarkGray,
                    onClick = onDismiss
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Foto paling ringan. GIF bergerak dengan ringan. Video paling halus dan bisa HD 60 fps " +
                    "(MP4 H.264, maksimal 200 MB), tapi memakai baterai lebih banyak. " +
                    "Suara video, ambient, dan hemat baterai diatur di Pengaturan > Tampilan.",
                color = NuxColors.GrayNeutral,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            NuxButton(
                onClick = onPick,
                backgroundColor = NuxColors.Yellow,
                contentColor = NuxColors.Ink,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PILIH FOTO / GIF / VIDEO",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
            }

            if (hasCustom) {
                Spacer(modifier = Modifier.height(10.dp))
                NuxButton(
                    onClick = onReset,
                    backgroundColor = NuxColors.SurfaceWhite,
                    contentColor = NuxColors.DarkGray,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "KEMBALI KE BAWAAN",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

/**
 * Dialog pemilih instance: ketuk baris untuk memilih; ikon di kanan untuk
 * buka folder, ubah, atau hapus; tombol kuning di bawah untuk menambah instance.
 */
@Composable
private fun InstancePickerDialog(
    instances: List<Instance>,
    selectedId: String?,
    onSelect: (Instance) -> Unit,
    onOpenFolder: (Instance) -> Unit,
    onEdit: (Instance) -> Unit,
    onDelete: (Instance) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    NuxDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 460.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PILIH INSTANCE",
                    color = NuxColors.DarkGray,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f)
                )
                PickerIconButton(
                    icon = Icons.Default.Close,
                    description = "Tutup",
                    tint = NuxColors.DarkGray,
                    onClick = onDismiss
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (instances.isEmpty()) {
                Text(
                    text = "Belum ada instance. Buat yang pertama di bawah.",
                    color = NuxColors.GrayNeutral,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 210.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(instances, key = { it.id }) { inst ->
                        val isSelected = inst.id == selectedId
                        val rowShape = RoundedCornerShape(16.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .background(if (isSelected) NuxColors.YellowPale else NuxColors.SurfaceWhite, rowShape)
                                .border(
                                    NuxSizes.BorderWidth,
                                    if (isSelected) NuxColors.Yellow else NuxColors.LightGray,
                                    rowShape
                                )
                                .clickable { onSelect(inst) }
                                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = inst.name,
                                    color = NuxColors.DarkGray,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${inst.mcVersion} · ${com.israadev.nuxlauncher.core.forge.ForgeFlavor.label(inst.loader)}",
                                    color = NuxColors.GrayNeutral,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            PickerIconButton(
                                icon = Icons.Outlined.Folder,
                                description = "Buka folder",
                                tint = NuxColors.DarkGray,
                                onClick = { onOpenFolder(inst) }
                            )
                            PickerIconButton(
                                icon = Icons.Outlined.Tune,
                                description = "Ubah instance",
                                tint = NuxColors.DarkGray,
                                onClick = { onEdit(inst) }
                            )
                            PickerIconButton(
                                icon = Icons.Outlined.DeleteOutline,
                                description = "Hapus instance",
                                tint = NuxColors.ErrorRed,
                                onClick = { onDelete(inst) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            NuxButton(
                onClick = onAdd,
                backgroundColor = NuxColors.Yellow,
                contentColor = NuxColors.Ink,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TAMBAH INSTANCE",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/** Tombol ikon bulat 40dp (target sentuh nyaman) untuk dialog pemilih instance. */
@Composable
private fun PickerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
