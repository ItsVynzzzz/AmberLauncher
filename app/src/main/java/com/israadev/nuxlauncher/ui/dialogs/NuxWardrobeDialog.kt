package com.israadev.nuxlauncher.ui.dialogs

import android.net.Uri
import com.israadev.nuxlauncher.ui.i18n.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.israadev.nuxlauncher.core.account.AccountManager
import com.israadev.nuxlauncher.core.models.UserAccount
import com.israadev.nuxlauncher.core.skin.CapePreset
import com.israadev.nuxlauncher.core.skin.MicrosoftCape
import com.israadev.nuxlauncher.core.skin.MicrosoftWardrobeService
import com.israadev.nuxlauncher.core.skin.SkinUtils
import com.israadev.nuxlauncher.ui.components.AmberActionButton
import com.israadev.nuxlauncher.ui.components.AmberPage
import com.israadev.nuxlauncher.ui.components.AmberPageLabel
import com.israadev.nuxlauncher.ui.components.AmberPanel
import com.israadev.nuxlauncher.ui.components.AmberTag
import com.israadev.nuxlauncher.ui.components.AmberChoicePill
import com.israadev.nuxlauncher.ui.components.PlayerSkin
import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.israadev.nuxlauncher.ui.theme.amberGlass
import kotlinx.coroutines.launch
import java.io.File
import com.israadev.nuxlauncher.ui.components.AmberSpinner

/**
 * Halaman Wardrobe & Custom Skin: pratinjau 3D skin/jubah pemain dan pengaturannya.
 *
 * Bentuknya halaman kaca layar penuh seperti layar Mod/Akun/Pengaturan. Layar lebar: kartu
 * pratinjau 3D di kiri (kontrol putar/kamera dan pilihan animasi melayang di atasnya), pil tab
 * Skin/Jubah dan kartu pengaturan di kanan yang bisa digulir. Layar sempit: pratinjau di atas,
 * pengaturan di bawahnya. Tombol Simpan ada di bar atas (bukan di dasar layar), jadi tidak bisa
 * tertutup bar sistem atau taskbar tablet. Semua logika simpan/unggah/pratinjau tidak berubah.
 */
@Composable
fun NuxWardrobeDialog(
    account: UserAccount,
    onDismissRequest: () -> Unit,
    onSaved: (UserAccount) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isMicrosoft = account.safeAccountType == "microsoft"
    val isElyBy = account.safeAccountType == "elyby"

    // 3D Engine Instance
    val playerSkin = remember { PlayerSkin(context) }
    var isWebViewReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            playerSkin.destroy()
        }
    }

    // Tab state: "skin" or "cape"
    var activeTab by remember { mutableStateOf("skin") }

    // Wardrobe editing state
    var selectedModel by remember { mutableStateOf(account.safeSkinModel) }
    var currentSkinFile by remember {
        mutableStateOf(account.getSkinFile())
    }
    var currentCapeFile by remember {
        mutableStateOf(account.getCapeFile())
    }
    var selectedPresetCapeId by remember { mutableStateOf<String?>(null) }
    var isAutoRotating by remember { mutableStateOf(true) }

    var activeAccount by remember { mutableStateOf(account) }

    // Microsoft cape list state
    var isLoadingMicrosoftCapes by remember { mutableStateOf(false) }
    var microsoftCapes by remember { mutableStateOf<List<MicrosoftCape>>(emptyList()) }
    var selectedMicrosoftCapeId by remember { mutableStateOf<String?>(null) }
    var isSkinFileChanged by remember { mutableStateOf(false) }
    var isCapeChanged by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var selectedAnim by remember { mutableStateOf("Walking") }

    // Load Microsoft Capes from Minecraft.net if Microsoft account
    LaunchedEffect(account.id) {
        if (isMicrosoft) {
            isLoadingMicrosoftCapes = true
            scope.launch {
                val profRes = MicrosoftWardrobeService.getProfile(context, activeAccount)
                profRes.onSuccess { (updatedAcc, prof) ->
                    activeAccount = updatedAcc
                    microsoftCapes = prof.capes
                    val activeCape = prof.capes.find { it.state.equals("ACTIVE", ignoreCase = true) }
                    selectedMicrosoftCapeId = activeCape?.id
                    if (activeCape != null) {
                        playerSkin.loadCapeUrl(activeCape.url)
                    }
                }
                profRes.onFailure { err ->
                    statusMessage = "Gagal memuat jubah: ${err.message}"
                }
                isLoadingMicrosoftCapes = false
            }
        }
    }

    val persistAccountChanges: (File?, File?, String) -> UserAccount = { skinFile, capeFile, model ->
        val updated = activeAccount.copy(
            skinModel = model,
            accessToken = activeAccount.safeAccessToken,
            refreshToken = activeAccount.safeRefreshToken,
            email = activeAccount.safeEmail,
            tier = activeAccount.safeTier,
            customSkinPath = skinFile?.absolutePath,
            customCapePath = capeFile?.absolutePath
        )
        SkinUtils.invalidateHeadCache(activeAccount.id)
        AccountManager.updateAccount(context, updated)
        AccountManager.selectAccount(updated)
        onSaved(updated)
        updated
    }

    // Pickers (Preview only - do not persist immediately)
    val skinPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessing = true
                statusMessage = "Memvalidasi skin..."
                SkinUtils.importSkinFromUri(context, account.id, uri).fold(
                    onSuccess = { file ->
                        currentSkinFile = file
                        isSkinFileChanged = true
                        val isSlim = SkinUtils.isSlimModel(file)
                        selectedModel = if (isSlim) "slim" else "classic"
                        playerSkin.loadAccount(account, customSkinFile = file, customCapeFile = currentCapeFile)
                        playerSkin.loadSkin(file, selectedModel)
                        statusMessage = "Skin dimuat di preview (${if (isSlim) "Slim" else "Klasik"}) — Klik Simpan untuk menerapkan"
                    },
                    onFailure = { err ->
                        statusMessage = "Gagal: ${err.message}"
                        Toast.makeText(context, err.message ?: "File tidak valid", Toast.LENGTH_SHORT).show()
                    }
                )
                isProcessing = false
            }
        }
    }

    val capePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessing = true
                statusMessage = "Memvalidasi cape..."
                SkinUtils.importCapeFromUri(context, account.id, uri).fold(
                    onSuccess = { file ->
                        currentCapeFile = file
                        selectedPresetCapeId = null
                        playerSkin.loadAccount(account, customSkinFile = currentSkinFile, customCapeFile = file)
                        playerSkin.loadCape(file)
                        statusMessage = "Cape kustom dimuat di preview — Klik Simpan untuk menerapkan"
                    },
                    onFailure = { err ->
                        statusMessage = "Gagal: ${err.message}"
                        Toast.makeText(context, err.message ?: "File cape tidak valid", Toast.LENGTH_SHORT).show()
                    }
                )
                isProcessing = false
            }
        }
    }

    val openElyBy: () -> Unit = {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://account.ely.by"))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    // Simpan: akun Microsoft disinkronkan ke Minecraft.net dulu, akun offline langsung disimpan lokal.
    val save: () -> Unit = {
        if (isMicrosoft) {
            scope.launch {
                isProcessing = true
                statusMessage = "Menyinkronkan ke Minecraft.net..."
                var errorMsg: String? = null

                val skinToUpload = currentSkinFile
                if (isSkinFileChanged && skinToUpload != null) {
                    val res = MicrosoftWardrobeService.uploadSkin(context, activeAccount, skinToUpload, selectedModel == "slim")
                    if (res.isFailure) {
                        errorMsg = res.exceptionOrNull()?.message
                    } else {
                        activeAccount = res.getOrThrow()
                    }
                }

                if (isCapeChanged) {
                    val res = MicrosoftWardrobeService.changeCape(context, activeAccount, selectedMicrosoftCapeId)
                    if (res.isFailure) {
                        val capeErr = res.exceptionOrNull()?.message
                        errorMsg = if (errorMsg != null) "$errorMsg | $capeErr" else capeErr
                    } else {
                        activeAccount = res.getOrThrow()
                        // Pratinjau di halaman Accounts langsung memakai jubah baru ini
                        // (null = tanpa jubah; "mojang_active_cape" juga berarti dilepas).
                        SkinUtils.rememberMojangCape(
                            activeAccount.id,
                            microsoftCapes.firstOrNull { it.id == selectedMicrosoftCapeId }
                                ?.takeIf { it.id != "mojang_active_cape" }
                                ?.url
                        )
                    }
                }

                isProcessing = false
                if (errorMsg != null) {
                    Toast.makeText(context, "Gagal memperbarui Minecraft.net: $errorMsg", Toast.LENGTH_LONG).show()
                    statusMessage = "Gagal: $errorMsg"
                } else {
                    persistAccountChanges(currentSkinFile, currentCapeFile, selectedModel)
                    Toast.makeText(context, "Perubahan berhasil diterapkan ke Minecraft.net!", Toast.LENGTH_SHORT).show()
                    onDismissRequest()
                }
            }
        } else {
            persistAccountChanges(currentSkinFile, currentCapeFile, selectedModel)
            Toast.makeText(context, "Skin & Cape berhasil disimpan!", Toast.LENGTH_SHORT).show()
            onDismissRequest()
        }
    }

    // Kartu pratinjau 3D (satu-satunya tempat WebView dipasang)
    val viewport: @Composable (Modifier) -> Unit = { vpModifier ->
        val vpShape = RoundedCornerShape(26.dp)
        Box(
            modifier = vpModifier
                .clip(vpShape)
                .background(AmberUi.SurfaceRaised, vpShape)
                .border(1.dp, AmberUi.BorderStrong, vpShape)
        ) {
            AndroidView(
                factory = { ctx ->
                    playerSkin.loadWebView(ctx) {
                        isWebViewReady = true
                        playerSkin.loadAccount(account, currentSkinFile, currentCapeFile)
                        playerSkin.startAnim(selectedAnim, 0.8f)
                        playerSkin.setAutoRotate(isAutoRotating, 0.7f)
                        playerSkin.setCamera(0, 10, 52)
                    }
                },
                update = {
                    if (isWebViewReady) {
                        playerSkin.loadAccount(account, currentSkinFile, currentCapeFile)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Atas: nama akun di kiri, kontrol putar dan kamera di kanan
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 150.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .border(1.dp, AmberUi.Border, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = account.username,
                        color = AmberUi.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ViewportButton(
                        icon = Icons.Outlined.RotateRight,
                        description = "Putar Otomatis",
                        active = isAutoRotating,
                        onClick = {
                            isAutoRotating = !isAutoRotating
                            playerSkin.setAutoRotate(isAutoRotating, 0.7f)
                        }
                    )
                    ViewportButton(
                        icon = Icons.Outlined.CenterFocusStrong,
                        description = "Reset Kamera",
                        active = false,
                        onClick = { playerSkin.resetCamera() }
                    )
                }
            }

            // Bawah: pilihan animasi
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .border(1.dp, AmberUi.Border, CircleShape)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Walking" to "Jalan", "DefaultIdle" to "Diam", "Running" to "Lari", "Wave" to "Sapa").forEach { (animKey, label) ->
                    val isSelected = selectedAnim == animKey
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) AmberUi.Accent else Color.Transparent, CircleShape)
                            .clickable {
                                selectedAnim = animKey
                                playerSkin.startAnim(animKey, 0.8f)
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) AmberUi.Text else AmberUi.TextDim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    // Kolom pengaturan: status, tab Skin/Jubah, lalu kartu isi tab
    val controls: @Composable ColumnScope.() -> Unit = {
        val statusText = statusMessage
        if (statusText != null) {
            val statusShape = RoundedCornerShape(20.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(statusShape)
                    .amberGlass(statusShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isError = statusText.startsWith("Gagal")
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isError) AmberUi.Danger else AmberUi.AccentSoft, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = statusText,
                    color = if (isError) AmberUi.Danger else AmberUi.Text,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Tab: Skin vs Jubah
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AmberChoicePill(
                text = "KUSTOM SKIN",
                selected = activeTab == "skin",
                onClick = { activeTab = "skin" },
                modifier = Modifier.weight(1f)
            )
            AmberChoicePill(
                text = "JUBAH / CAPE",
                selected = activeTab == "cape",
                onClick = { activeTab = "cape" },
                modifier = Modifier.weight(1f)
            )
        }

        if (activeTab == "skin") {
            // ============================ TAB SKIN ============================
            if (isElyBy) {
                ElyByGuideCard(
                    title = "Sistem Skin & Jubah Ely.by",
                    body = "Akun Ely.by menggunakan server skinsystem terpusat. Untuk mengganti skin atau tipe model lengan, silakan unggah langsung melalui website resmi ely.by agar tersinkronisasi in-game.",
                    buttonText = "KUNJUNGI ACCOUNT.ELY.BY",
                    onOpen = openElyBy
                )
            } else {
                AmberPanel {
                    AmberActionButton(
                        text = "PILIH FILE SKIN (.PNG)",
                        onClick = { skinPickerLauncher.launch("image/png") },
                        icon = Icons.Outlined.UploadFile,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    AmberPageLabel("TIPE MODEL LENGAN:")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModelOption(
                            title = "Klasik",
                            subtitle = "Lengan tebal (4px)",
                            selected = selectedModel == "classic",
                            onClick = {
                                selectedModel = "classic"
                                playerSkin.loadSkin(currentSkinFile, "classic")
                                statusMessage = "Model Klasik dipilih (Preview) — Klik Simpan"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        ModelOption(
                            title = "Slim",
                            subtitle = "Lengan ramping (3px)",
                            selected = selectedModel == "slim",
                            onClick = {
                                selectedModel = "slim"
                                playerSkin.loadSkin(currentSkinFile, "slim")
                                statusMessage = "Model Slim dipilih (Preview) — Klik Simpan"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .clickable {
                                currentSkinFile = null
                                selectedModel = "classic"
                                playerSkin.resetSkin()
                                playerSkin.loadCape(currentCapeFile)
                                statusMessage = "Skin direset ke default Steve (Preview) — Klik Simpan"
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "↺ Reset ke Skin Default (Klasik)",
                            color = AmberUi.TextDim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                    }
                }
            }
        } else {
            // ============================ TAB JUBAH ============================
            if (isMicrosoft) {
                // Khusus akun Microsoft: hanya jubah resmi milik pengguna di minecraft.net
                AmberPanel {
                    AmberPageLabel("JUBAH RESMI MINECRAFT.NET:")
                    if (isLoadingMicrosoftCapes) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AmberSpinner(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AmberUi.AccentSoft
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Memuat jubah dari Minecraft.net...",
                                color = AmberUi.TextDim,
                                fontSize = 12.sp
                            )
                        }
                    } else if (microsoftCapes.isEmpty()) {
                        Text(
                            text = "Akun Microsoft Anda belum memiliki jubah resmi di Minecraft.net.",
                            color = AmberUi.TextDim,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Opsi lepas jubah
                            val isNoCapeSelected = selectedMicrosoftCapeId == null
                            WardrobeOption(
                                selected = isNoCapeSelected,
                                tint = CapeAccent,
                                onClick = {
                                    selectedMicrosoftCapeId = null
                                    isCapeChanged = true
                                    playerSkin.loadCapeUrl(null)
                                    statusMessage = "Jubah dilepas (Preview) — Klik Simpan"
                                }
                            ) {
                                Text(
                                    text = "Tanpa Jubah (Lepas Cape)",
                                    color = AmberUi.Text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isNoCapeSelected) {
                                    AmberTag("DIPILIH", color = CapeAccent)
                                }
                            }

                            // Daftar jubah resmi milik pengguna
                            microsoftCapes.forEach { cape ->
                                val isSelected = selectedMicrosoftCapeId == cape.id
                                val isMojangActive = cape.state.equals("ACTIVE", ignoreCase = true)
                                WardrobeOption(
                                    selected = isSelected,
                                    tint = CapeAccent,
                                    onClick = {
                                        selectedMicrosoftCapeId = cape.id
                                        isCapeChanged = true
                                        playerSkin.loadCapeUrl(cape.url)
                                        statusMessage = "Jubah ${cape.alias ?: "Mojang"} dipilih (Preview) — Klik Simpan"
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .background(CapeAccent, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = MicrosoftWardrobeService.getCapeDisplayName(cape.alias),
                                        color = AmberUi.Text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isMojangActive) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        AmberTag("AKTIF DI MOJANG")
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (isElyBy) {
                ElyByGuideCard(
                    title = "Sistem Skin & Cape Ely.by",
                    body = "Akun Ely.by menggunakan server skinsystem resmi. Seluruh skin dan jubah dikelola langsung melalui website ely.by dan akan otomatis tampil di dalam game.",
                    buttonText = "BUKA WEBSITE ELY.BY",
                    onOpen = openElyBy
                )
            } else {
                // Khusus akun offline: unggah jubah kustom atau pilih preset
                AmberPanel {
                    AmberActionButton(
                        text = "PILIH FILE CAPE KUSTOM (.PNG)",
                        onClick = { capePickerLauncher.launch("image/png") },
                        icon = Icons.Outlined.UploadFile,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    AmberPageLabel("ATAU PILIH PRESET JUBAH:")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkinUtils.PRESET_CAPES.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { preset ->
                                    val isPresetSelected = selectedPresetCapeId == preset.id || (preset.id == "none" && currentCapeFile == null)
                                    val presetColor = Color(preset.badgeColor)
                                    WardrobeOption(
                                        selected = isPresetSelected,
                                        tint = presetColor,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            selectedPresetCapeId = preset.id
                                            scope.launch {
                                                val generatedCape = SkinUtils.applyPresetCape(context, account.id, preset.id)
                                                currentCapeFile = generatedCape
                                                playerSkin.loadCape(generatedCape)
                                                statusMessage = if (preset.id == "none") "Cape dilepas (Preview) — Klik Simpan" else "Jubah ${preset.name} dipilih (Preview) — Klik Simpan"
                                            }
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .background(presetColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = preset.name,
                                            color = AmberUi.Text,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    AmberPage(
        onDismiss = onDismissRequest,
        title = "Wardrobe",
        icon = Icons.Outlined.Person,
        subtitle = account.username,
        actions = { info ->
            if (isElyBy) {
                AmberActionButton(
                    text = "MENGERTI",
                    onClick = onDismissRequest,
                    icon = Icons.Outlined.Check,
                    height = 40.dp
                )
            } else {
                AmberActionButton(
                    text = if (info.narrow) "Simpan" else if (isMicrosoft) "SIMPAN KE MOJANG" else "SIMPAN PERUBAHAN",
                    onClick = save,
                    icon = Icons.Outlined.Check,
                    loading = isProcessing,
                    height = 40.dp
                )
            }
        }
    ) { info ->
        if (info.narrow) {
            // Layar sempit: pratinjau di atas (tidak ikut digulir supaya geser memutar model tidak
            // bentrok dengan gulir halaman), pengaturan di bawahnya.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                viewport(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.85f)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.15f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    controls()
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                viewport(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                Column(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    controls()
                }
            }
        }
    }
}

private val CapeAccent = Color(0xFFC084FC)
private val ElyByAccent = Color(0xFFB79CFF)

/** Tombol bulat kecil di atas pratinjau 3D. Aktif = isian aksen. */
@Composable
private fun ViewportButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) AmberUi.Accent else Color.Black.copy(alpha = 0.55f), CircleShape)
            .border(1.dp, if (active) AmberUi.AccentEdge else AmberUi.Border, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = AmberUi.Text,
            modifier = Modifier.size(19.dp)
        )
    }
}

/** Satu baris pilihan berbentuk pil. Terpilih = tepi dan isian warna [tint], selain itu kaca. */
@Composable
private fun WardrobeOption(
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier
                        .background(tint.copy(alpha = 0.20f), CircleShape)
                        .border(1.dp, tint, CircleShape)
                } else {
                    Modifier.amberGlass(CircleShape)
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Pilihan tipe lengan (Klasik / Slim): judul dan keterangan kecil. */
@Composable
private fun ModelOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .background(AmberUi.Accent, shape)
                        .border(1.dp, AmberUi.AccentEdge, shape)
                } else {
                    Modifier.amberGlass(shape)
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = AmberUi.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = subtitle,
            color = AmberUi.TextDim,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

/** Panduan untuk akun Ely.by (skin & jubah dikelola lewat website mereka). */
@Composable
private fun ElyByGuideCard(
    title: String,
    body: String,
    buttonText: String,
    onOpen: () -> Unit
) {
    AmberPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ElyByAccent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Public,
                    contentDescription = null,
                    tint = ElyByAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = AmberUi.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = body,
            color = AmberUi.TextDim,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        AmberActionButton(
            text = buttonText,
            onClick = onOpen,
            icon = Icons.Outlined.OpenInBrowser,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
