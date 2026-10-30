package com.israadev.nuxlauncher.ui.screens

import com.israadev.nuxlauncher.ui.theme.AmberGlassKind
import com.israadev.nuxlauncher.ui.theme.amberGlass
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import com.israadev.nuxlauncher.ui.theme.AmberUi
import androidx.compose.animation.slideInVertically
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.israadev.nuxlauncher.ui.theme.rememberCameraSideInsets
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.israadev.nuxlauncher.ui.theme.AmberStyle
import com.israadev.nuxlauncher.ui.theme.LocalGlassBackdrop
import com.israadev.nuxlauncher.ui.theme.amberScrimBrush
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.CompositionLocalProvider
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
    // Jenis addon yang dibuka di halaman Mod dan tab tujuan tombol kembali
    // (dari Home = Home, dari layar kelola konten = layar kelola konten).
    var modsInitialType by remember { mutableStateOf("mods") }
    var modsBackTab by remember { mutableStateOf("home") }

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
    val videoVolume = if (launcherSettings.homeBgSound) {
        val p = launcherSettings.homeBgVolume.coerceIn(0, 100) / 100f
        p * p
    } else {
        0f
    }

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
        // Semua layar utama memakai layar penuh dengan latar pilihan pengguna di belakangnya.
        // Jarak kiri/kanan hanya selebar kamera (plus sedikit celah); navigasi sistem tidak dihitung.
        val (leftInset, rightInset) = rememberCameraSideInsets()

        // Latar pilihan pengguna (foto/GIF/video, atau gambar bawaan) dan lapisan gelap tipisnya dipakai
        // di semua layar. Untuk gaya Liquid Glass, latar direkam lalu diburamkan sekali di sini dan
        // dipakai semua permukaan kaca (Android 12 ke atas; di bawah itu otomatis kembali ke Default).
        val liquid = AmberUi.style == AmberStyle.LIQUID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val rawBackdrop = rememberGraphicsLayer()
        val blurBackdrop = rememberGraphicsLayer()
        val blurPx = with(LocalDensity.current) { 26.dp.toPx() }
        // Redupkan latar: di Liquid Glass lapisan gelapnya dikurangi supaya wallpaper tetap terlihat.
        val scrim = remember(launcherSettings.bgDim, liquid) {
            amberScrimBrush(launcherSettings.bgDim / 100f * (if (liquid) 0.7f else 1f))
        }
        SideEffect {
            AmberUi.applyDim(launcherSettings.bgDim / 100f)
            if (liquid) {
                // Latar yang diburamkan dibuat lebih hidup warnanya supaya kaca terasa menyatu dengan wallpaper.
                blurBackdrop.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                blurBackdrop.colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NuxColors.Ink)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        rawBackdrop.record { this@drawWithContent.drawContent() }
                        drawLayer(rawBackdrop)
                        // Yang direkam hanya wallpaper (tanpa lapisan gelap), jadi kaca menampilkan warna aslinya.
                        if (liquid) blurBackdrop.record { drawLayer(rawBackdrop) }
                    }
            ) {
                HomeBackground(
                    customFile = if (hasCustomBg) bgFile else null,
                    version = bgVersion,
                    videoFile = if (hasVideoBg && hasCustomBg) bgVideoFile else null,
                    animate = animateBackground,
                    videoVolume = videoVolume,
                    ambient = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (currentTab != "home") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrim)
                )
            }

            CompositionLocalProvider(LocalGlassBackdrop provides (if (liquid) blurBackdrop else null)) {
            when (currentTab) {
                // Home: layar penuh tanpa sidebar dan kartu; navigasinya ada di bar bawah.
                "home" -> NuxReadable {
                    val rendererLabel = remember(launcherSettings.selectedRenderer) {
                        val info = NuxRendererRegistry.findRendererById(launcherSettings.selectedRenderer)
                        HomeInfo.rendererLabel(info.id, info.displayName)
                    }
                    HomeScene(
                        instance = selectedInstance,
                        isReady = isInstanceReady,
                        account = currentAccount,
                        rendererLabel = rendererLabel,
                        onOpenAbout = { showAboutDialog = true },
                        onPickInstance = { showInstancePicker = true },
                        onPlay = { selectedInstance?.let { startInstance(it) } },
                        onCreateInstance = { showAddDialog = true },
                        onEditInstance = { showEditInstanceDialog = true },
                        onNavigate = { tab ->
                            if (tab == "mods") {
                                modsInitialType = "mods"
                                modsBackTab = "home"
                            }
                            currentTab = tab
                        },
                        startInset = leftInset,
                        endInset = rightInset,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Mod, kelola konten, Akun, dan Pengaturan: layar penuh dengan pil navigasi yang sama
                // di kiri atas (kembali, Mod, Akun, Pengaturan).
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = leftInset, end = rightInset)
                ) {
                    NuxReadable {
                        val openMods = {
                            modsInitialType = "mods"
                            modsBackTab = "home"
                            currentTab = "mods"
                        }
                        when (currentTab) {
                            "mods" -> ModsScreen(
                                onNavigateBack = { currentTab = modsBackTab },
                                onOpenAccounts = { currentTab = "accounts" },
                                onOpenSettings = { currentTab = "settings" },
                                initialType = modsInitialType,
                                modifier = Modifier.fillMaxSize()
                            )
                            "instance_content" -> ModsScreen(
                                mode = ModsMode.MANAGE,
                                onNavigateBack = { currentTab = "home" },
                                onOpenAccounts = { currentTab = "accounts" },
                                onOpenSettings = { currentTab = "settings" },
                                onOpenBrowse = { type ->
                                    modsInitialType = type
                                    modsBackTab = "instance_content"
                                    currentTab = "mods"
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            "accounts" -> AccountsScreen(
                                onNavigateBack = { currentTab = "home" },
                                onOpenMods = openMods,
                                onOpenSettings = { currentTab = "settings" },
                                modifier = Modifier.fillMaxSize()
                            )
                            else -> SettingsScreen(
                                onNavigateBack = { currentTab = "home" },
                                onOpenMods = openMods,
                                onOpenAccounts = { currentTab = "accounts" },
                                onOpenGuiEditor = { currentTab = "gui_editor" },
                                onChangeBackground = { showBgDialog = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
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
                    onOpenContent = {
                        showEditInstanceDialog = false
                        currentTab = "instance_content"
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

/**
 * Layar Home. Latar (foto/GIF/video) digambar DashboardScreen di belakang semua layar.
 * Kiri atas pil pemain (ketuk = menu akun cepat), kanan atas satu tombol bulat Tentang & Lisensi.
 * Di kiri bawah nama instance aktif beserta info versi, lalu bar navigasi (Home, Mod, Akun,
 * Pengaturan) dan tombol PLAY di kanan bawah. Semua tombol berkaca hitam tembus pandang seperti
 * layar Pengaturan. Tanpa animasi masuk, supaya ringan di HP kelas bawah.
 */
@Composable
private fun HomeScene(
    instance: Instance?,
    isReady: Boolean,
    account: UserAccount?,
    rendererLabel: String,
    onOpenAbout: () -> Unit,
    onPickInstance: () -> Unit,
    onPlay: () -> Unit,
    onCreateInstance: () -> Unit,
    onEditInstance: () -> Unit,
    onNavigate: (String) -> Unit,
    startInset: Dp,
    endInset: Dp,
    modifier: Modifier = Modifier
) {
    val freeRamMb = rememberFreeRamMb()
    val config = LocalConfiguration.current
    val compact = config.screenWidthDp < 640
    val nameSize = if (config.screenHeightDp < 380) 40.sp else 54.sp
    val textShadow = remember { Shadow(NuxColors.Ink.copy(alpha = 0.40f), Offset(0f, 2f), 12f) }

    // Menu akun cepat di bawah pil pemain: ganti, hapus, dan tambah akun tanpa membuka tab Akun
    val context = LocalContext.current
    val accounts by AccountManager.accounts.collectAsState()
    var accountMenuOpen by remember { mutableStateOf(false) }
    var showAddAccount by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<UserAccount?>(null) }

    Box(modifier = modifier) {
        // Gradien bawah dan kiri: teks putih terbaca di latar terang
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to NuxColors.Ink.copy(alpha = 0.18f),
                        0.24f to Color.Transparent,
                        0.58f to NuxColors.Ink.copy(alpha = 0.18f),
                        1f to NuxColors.Ink.copy(alpha = 0.78f)
                    )
                )
                .background(
                    Brush.horizontalGradient(
                        0f to NuxColors.Ink.copy(alpha = 0.40f),
                        0.55f to Color.Transparent
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = startInset + 16.dp,
                    end = endInset + 16.dp,
                    top = 16.dp,
                    bottom = 16.dp
                )
        ) {
            // ---- BARIS ATAS: pil pemain di kiri, satu tombol bulat Tentang di kanan ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pil pemain (ketuk = menu akun cepat)
                Row(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(CircleShape)
                        .amberGlass(CircleShape)
                        .clickable {
                            // Belum ada akun: langsung buka dialog tambah akun
                            if (accounts.isEmpty()) showAddAccount = true
                            else accountMenuOpen = !accountMenuOpen
                        }
                        .padding(start = 6.dp, end = 12.dp),
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
                                .background(AmberUi.Accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = AmberUi.Text,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = account?.username ?: "Tambah akun",
                        color = AmberUi.Text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (accountMenuOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Ganti akun",
                        tint = AmberUi.Text.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Tentang & Lisensi (memuat pemberitahuan versi modifikasi tidak resmi dan lisensi GPL-3.0)
                Box(
                    modifier = Modifier
                        .size(43.dp)
                        .clip(CircleShape)
                        .amberGlass(CircleShape)
                        .clickable { onOpenAbout() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Tentang & Lisensi",
                        tint = AmberUi.Text,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ---- KIRI BAWAH: instance aktif, lalu navigasi + PLAY ----
            Column(modifier = Modifier.fillMaxWidth()) {
                if (instance != null) {
                    val javaLabel = remember(instance.id, instance.mcVersion, instance.javaRuntime) {
                        val rt = if (instance.javaRuntime != "auto") instance.javaRuntime
                        else JavaRuntimeManager.getRecommendedRuntime(instance.mcVersion)
                        rt.replace("jre-", "Java ")
                    }
                    val textStyle = LocalTextStyle.current.copy(shadow = textShadow)

                    Text(
                        text = "INSTANCE AKTIF",
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 2.6.sp,
                        maxLines = 1,
                        style = textStyle
                    )

                    // Nama besar + dropdown (ganti instance) + pensil (ubah instance)
                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(if (compact) 1f else 0.7f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = instance.name,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = nameSize,
                            lineHeight = nameSize * 1.1f,
                            letterSpacing = (-1.5).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = textStyle,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        HomeInstanceButton(
                            icon = Icons.Default.KeyboardArrowDown,
                            description = "Ganti instance",
                            onClick = onPickInstance
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        HomeInstanceButton(
                            icon = Icons.Outlined.Edit,
                            description = "Ubah instance",
                            onClick = onEditInstance
                        )
                    }

                    // Versi | loader (biru muda) | Java
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = instance.mcVersion,
                            color = Color.White.copy(alpha = 0.92f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            style = textStyle
                        )
                        HomeMetaDivider()
                        Text(
                            text = com.israadev.nuxlauncher.core.forge.ForgeFlavor.label(instance.loader),
                            color = AmberUi.AccentSoft,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        HomeMetaDivider()
                        Text(
                            text = javaLabel,
                            color = Color.White.copy(alpha = 0.92f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            style = textStyle
                        )
                    }

                    // Status RAM dan renderer
                    val ramColor = when (HomeInfo.ramLevel(freeRamMb)) {
                        2 -> AmberUi.AccentSoft
                        1 -> NuxColors.Amber
                        else -> NuxColors.ErrorRed
                    }
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(ramColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = HomeInfo.ramText(freeRamMb),
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            maxLines = 1,
                            style = textStyle
                        )
                        if (!compact) {
                            Spacer(modifier = Modifier.width(18.dp))
                            Text(
                                text = "Renderer $rendererLabel",
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                maxLines = 1,
                                style = textStyle
                            )
                        }
                    }
                } else {
                    // Belum ada instance
                    val textStyle = LocalTextStyle.current.copy(shadow = textShadow)
                    Text(
                        text = "Belum ada instance",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = nameSize,
                        lineHeight = nameSize * 1.1f,
                        letterSpacing = (-1.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = textStyle
                    )
                    Text(
                        text = "Buat instance Minecraft pertamamu untuk mulai bermain.",
                        color = Color.White.copy(alpha = 0.88f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 2,
                        style = textStyle,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Bar navigasi + tombol utama
                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        HomeNavBar(
                            selected = "home",
                            onSelect = onNavigate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 430.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    if (instance != null) {
                        HomeMainButton(
                            text = if (isReady) "PLAY" else "UNDUH",
                            icon = if (isReady) Icons.Default.PlayArrow else Icons.Default.Download,
                            onClick = onPlay,
                            modifier = Modifier.width(if (compact) 160.dp else 224.dp)
                        )
                    } else {
                        HomeMainButton(
                            text = "TAMBAH",
                            icon = Icons.Default.Add,
                            onClick = onCreateInstance,
                            modifier = Modifier.width(if (compact) 160.dp else 224.dp)
                        )
                    }
                }
            }
        }

        // Ketuk di luar menu = tutup
        if (accountMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { accountMenuOpen = false }
            )
            HomeAccountMenu(
                accounts = accounts,
                currentAccountId = account?.id,
                onSelect = { picked ->
                    AccountManager.selectAccount(picked)
                    accountMenuOpen = false
                },
                onDelete = { accountToDelete = it },
                onAdd = {
                    accountMenuOpen = false
                    showAddAccount = true
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = startInset + 16.dp, top = 16.dp + 42.dp + 8.dp)
                    .width(280.dp)
            )
        }
    }

    if (showAddAccount) {
        AddAccountModal(
            onDismiss = { showAddAccount = false },
            onAccountAdded = { newAcc ->
                AccountManager.addAccount(context, newAcc)
                Toast.makeText(context, "Akun ${newAcc.username} berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                showAddAccount = false
            }
        )
    }
    accountToDelete?.let { target ->
        DeleteAccountDialog(
            target = target,
            onDismiss = { accountToDelete = null }
        )
    }
}

/** Garis tegak tipis pemisah versi | loader | Java. */
@Composable
private fun HomeMetaDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .width(1.dp)
            .height(14.dp)
            .background(Color.White.copy(alpha = 0.40f))
    )
}

/** Tombol bulat kecil di samping nama instance (dropdown dan pensil). */
@Composable
private fun HomeInstanceButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .amberGlass(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

private val HOME_NAV_ITEMS = listOf(
    Triple("home", "Home", Icons.Outlined.Home),
    Triple("mods", "Mod", Icons.Outlined.FileDownload),
    Triple("accounts", "Akun", Icons.Outlined.Person),
    Triple("settings", "Pengaturan", Icons.Outlined.Tune)
)

/** Bar navigasi berbentuk pil di kiri bawah: Home, Mod, Akun, Pengaturan (ikon di atas label). */
@Composable
private fun HomeNavBar(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(61.dp)
            .clip(CircleShape)
            .amberGlass(CircleShape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        HOME_NAV_ITEMS.forEach { (id, label, icon) ->
            val isSelected = id == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (isSelected) AmberUi.SurfaceRaised else Color.Transparent, CircleShape)
                    .border(
                        1.dp,
                        if (isSelected) AmberUi.BorderStrong else Color.Transparent,
                        CircleShape
                    )
                    .clickable { if (!isSelected) onSelect(id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.78f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/** Tombol utama biru (PLAY / UNDUH / TAMBAH) dengan bayangan keras di bawah; menekan = turun. */
@Composable
private fun HomeMainButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lift by animateDpAsState(if (pressed) 0.dp else 4.dp, label = "homeMainLift")
    Box(
        modifier = modifier
            .height(64.dp)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.BottomCenter)
                .background(AmberUi.AccentDeep, CircleShape)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.TopCenter)
                .offset(y = 4.dp - lift)
                .clip(CircleShape)
                .background(AmberUi.Accent, CircleShape)
                .border(1.5.dp, AmberUi.AccentEdge, CircleShape),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                letterSpacing = 3.sp,
                maxLines = 1
            )
        }
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
            // Poster diam menutupi video sampai bingkai pertama tampil, lalu hilang seketika (tanpa pudar).
            if (!videoReady) {
                HomeStillBackground(
                    customFile = customFile,
                    version = version,
                    animate = false,
                    ambient = null,
                    modifier = Modifier.fillMaxSize()
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
