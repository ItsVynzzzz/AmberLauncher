package com.israadev.nuxlauncher.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Bitmap
import com.israadev.nuxlauncher.core.skin.SkinUtils
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.account.AccountManager
import com.israadev.nuxlauncher.core.account.elyby.ElyByAuthService
import com.israadev.nuxlauncher.core.account.elyby.ElyByDeviceCode
import com.israadev.nuxlauncher.core.account.elyby.ElyByLoginState
import com.israadev.nuxlauncher.core.account.microsoft.MicrosoftAuthService
import com.israadev.nuxlauncher.core.account.microsoft.MicrosoftDeviceCode
import com.israadev.nuxlauncher.core.models.UserAccount
import com.israadev.nuxlauncher.ui.components.*
import com.israadev.nuxlauncher.ui.dialogs.NuxMicrosoftAuthDialog
import com.israadev.nuxlauncher.ui.dialogs.NuxWardrobeDialog
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Layar Akun gaya baru (kaca gelap seperti Home).
 * - Kiri: kartu profil aktif dengan model 3D, nama besar, dan tombol skin & jubah.
 * - Kanan: daftar akun berbaris besar; ketuk baris untuk berganti, tombol "Gunakan" jelas.
 * - Tambah akun: dialog dengan 3 pilihan penyedia dan tombol utama kuning selebar dialog.
 * Semua target sentuh minimal 44dp dan huruf terkecil 11-12sp supaya enak dipakai di HP.
 */
@Composable
fun AccountsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val accounts by AccountManager.accounts.collectAsState()
    val currentAccount by AccountManager.currentAccount.collectAsState()

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<UserAccount?>(null) }
    var accountForWardrobe by remember { mutableStateOf<UserAccount?>(null) }

    // Pratinjau 3D pemain (viewport interaktif)
    val leftPlayerSkin = remember { PlayerSkin(context) }
    var leftPageFinished by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            leftPlayerSkin.destroy()
        }
    }

    fun selectAccount(acc: UserAccount) {
        AccountManager.selectAccount(acc)
        coroutineScope.launch {
            val updated = SkinUtils.ensureSkinAndCapeCached(context, acc)
            leftPlayerSkin.loadAccount(updated)
        }
    }

    fun openWardrobe(acc: UserAccount) {
        val allowed = acc.safeAccountType == "microsoft" || acc.safeAccountType == "offline"
        if (allowed) {
            accountForWardrobe = acc
        } else {
            Toast.makeText(
                context,
                "Skin & Jubah akun Ely.by dikelola langsung di https://account.ely.by",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp)
    ) {
        // Judul + tombol tambah akun
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Akun",
                    color = NuxColors.DarkGray,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = if (accounts.isEmpty()) {
                        "Belum ada akun tersimpan"
                    } else {
                        "${accounts.size} akun tersimpan · ketuk untuk berganti"
                    },
                    color = NuxColors.GrayNeutral,
                    fontSize = 12.sp
                )
            }
            NuxButton(
                onClick = { showAddAccountDialog = true },
                backgroundColor = NuxColors.Yellow,
                cornerRadius = 22.dp,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tambah akun",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // -----------------------------------------------------
            // KIRI: PROFIL AKTIF
            // -----------------------------------------------------
            NuxCard(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight(),
                cornerRadius = 24.dp
            ) {
                val acc = currentAccount
                if (acc != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(NuxColors.Yellow, CircleShape)
                                )
                                Text(
                                    text = "Profil aktif",
                                    color = NuxColors.GrayNeutral,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            AccountTypePill(acc.safeAccountType)
                        }

                        // Model 3D pemain
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    leftPlayerSkin.loadWebView(ctx) {
                                        leftPageFinished = true
                                        leftPlayerSkin.loadAccount(acc)
                                        leftPlayerSkin.startAnim("NewIdle", 0.7f)
                                        leftPlayerSkin.setAutoRotate(true, 0.6f)
                                        leftPlayerSkin.setCamera(20, 8, 44)
                                    }
                                },
                                update = {
                                    if (leftPageFinished) {
                                        leftPlayerSkin.loadAccount(acc)
                                    }
                                }
                            )
                        }

                        LaunchedEffect(acc.id, acc.customSkinPath, acc.customCapePath, acc.skinModel, leftPageFinished) {
                            if (leftPageFinished) {
                                val updated = SkinUtils.ensureSkinAndCapeCached(context, acc)
                                leftPlayerSkin.loadAccount(updated)
                            }
                        }

                        // Nama + keterangan di kiri, tombol skin & jubah di kanan
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = acc.username,
                                    color = NuxColors.DarkGray,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    letterSpacing = (-0.2).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = acctTypeDesc(acc.safeAccountType),
                                    color = NuxColors.GrayNeutral,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            AccountIconAction(
                                icon = Icons.Outlined.Checkroom,
                                description = "Skin & Jubah",
                                tint = NuxColors.Yellow,
                                ring = true,
                                onClick = { openWardrobe(acc) }
                            )
                        }
                    }
                } else {
                    // Belum ada profil aktif
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "Belum ada akun",
                            tint = NuxColors.GrayNeutral,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Belum ada profil aktif",
                            color = NuxColors.DarkGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pilih atau tambahkan akun untuk mulai bermain.",
                            color = NuxColors.GrayNeutral,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // -----------------------------------------------------
            // KANAN: DAFTAR AKUN
            // -----------------------------------------------------
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
            ) {
                if (accounts.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Belum ada akun",
                            color = NuxColors.DarkGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tambahkan akun Offline, Microsoft, atau Ely.by.",
                            color = NuxColors.GrayNeutral,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        NuxButton(
                            onClick = { showAddAccountDialog = true },
                            backgroundColor = NuxColors.Yellow,
                            cornerRadius = 22.dp,
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Tambah akun",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(accounts, key = { "${it.safeAccountType}_${it.id}_${it.username}" }) { acc ->
                            val isCurrent = acc.id == currentAccount?.id ||
                                (acc.username == currentAccount?.username &&
                                    acc.safeAccountType == currentAccount?.safeAccountType)
                            val accent = acctAccent(acc.safeAccountType)
                            val itemShape = RoundedCornerShape(18.dp)
                            val shortUuid = if (acc.uuid.length > 10) {
                                "${acc.uuid.take(6)}...${acc.uuid.takeLast(4)}"
                            } else {
                                acc.uuid
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(itemShape)
                                    .background(
                                        if (isCurrent) NuxColors.YellowPale else NuxColors.SurfaceWhite,
                                        itemShape
                                    )
                                    .border(
                                        NuxSizes.GlassBorder,
                                        if (isCurrent) NuxColors.Yellow.copy(alpha = 0.55f) else NuxColors.CardBorder,
                                        itemShape
                                    )
                                    .clickable { selectAccount(acc) }
                                    .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar kepala pemain
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            NuxSizes.GlassBorder,
                                            accent.copy(alpha = 0.55f),
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NuxAccountAvatar(
                                        account = acc,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        fallbackInitials = acc.username.take(2).uppercase()
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = acc.username,
                                        color = NuxColors.DarkGray,
                                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AccountTypePill(acc.safeAccountType)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = shortUuid,
                                            color = NuxColors.GrayNeutral,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (isCurrent) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(NuxColors.Yellow, RoundedCornerShape(18.dp))
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = NuxColors.Ink,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Aktif",
                                            color = NuxColors.Ink,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                } else {
                                    NuxButton(
                                        onClick = { selectAccount(acc) },
                                        backgroundColor = Color.Transparent,
                                        cornerRadius = 22.dp,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Gunakan",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                AccountIconAction(
                                    icon = Icons.Outlined.Checkroom,
                                    description = "Skin & Jubah",
                                    onClick = { openWardrobe(acc) }
                                )
                                AccountIconAction(
                                    icon = Icons.Outlined.DeleteOutline,
                                    description = "Hapus",
                                    onClick = { accountToDelete = acc }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // =============================================================
    // DIALOG: SKIN & JUBAH
    // =============================================================
    if (accountForWardrobe != null) {
        NuxWardrobeDialog(
            account = accountForWardrobe!!,
            onDismissRequest = { accountForWardrobe = null },
            onSaved = { updated ->
                accountForWardrobe = null
                leftPlayerSkin.loadAccount(updated)
            }
        )
    }

    // =============================================================
    // DIALOG: TAMBAH AKUN
    // =============================================================
    if (showAddAccountDialog) {
        AddAccountModal(
            onDismiss = { showAddAccountDialog = false },
            onAccountAdded = { newAcc ->
                AccountManager.addAccount(context, newAcc)
                Toast.makeText(context, "Akun ${newAcc.username} berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                showAddAccountDialog = false
            }
        )
    }

    // =============================================================
    // DIALOG: KONFIRMASI HAPUS
    // =============================================================
    if (accountToDelete != null) {
        val target = accountToDelete!!
        NuxDialog(
            onDismissRequest = { accountToDelete = null },
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(NuxColors.ErrorRed.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = NuxColors.ErrorRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Hapus akun?",
                    color = NuxColors.DarkGray,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hapus profil '${target.username}' (${acctTypeLabel(target.safeAccountType)}) dari perangkat ini?",
                    color = NuxColors.GrayNeutral,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NuxButton(
                        onClick = { accountToDelete = null },
                        backgroundColor = Color.Transparent,
                        cornerRadius = 22.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    NuxButton(
                        onClick = {
                            AccountManager.removeAccount(context, target)
                            Toast.makeText(context, "Profil '${target.username}' dihapus", Toast.LENGTH_SHORT).show()
                            accountToDelete = null
                        },
                        backgroundColor = NuxColors.ErrorRed,
                        contentColor = NuxColors.DarkGray,
                        cornerRadius = 22.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hapus", fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private fun acctAccent(type: String): Color = when (type) {
    "microsoft" -> NuxColors.SkyBlueDark
    "elyby" -> NuxColors.PurpleDark
    else -> NuxColors.Yellow
}

private fun acctTypeLabel(type: String): String = when (type) {
    "microsoft" -> "Microsoft"
    "elyby" -> "Ely.by"
    else -> "Offline"
}

private fun acctTypeDesc(type: String): String = when (type) {
    "microsoft" -> "Akun resmi Microsoft"
    "elyby" -> "Akun terverifikasi Ely.by"
    else -> "Akun offline / lokal"
}

/** Lencana jenis akun (Offline / Microsoft / Ely.by) berwarna sesuai penyedia. */
@Composable
private fun AccountTypePill(type: String) {
    val accent = acctAccent(type)
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.16f), shape)
            .border(NuxSizes.GlassBorder, accent.copy(alpha = 0.4f), shape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = acctTypeLabel(type),
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Tombol ikon bulat 44dp (skin & jubah, hapus). */
@Composable
private fun AccountIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = NuxColors.GrayNeutral,
    ring: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (ring) {
                    Modifier
                        .background(NuxColors.SurfaceElevated, CircleShape)
                        .border(NuxSizes.GlassBorder, NuxColors.CardBorder, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Dialog tambah akun: 3 pilihan penyedia di atas, area aksi di bawah dengan tombol utama
 * kuning selebar dialog (mudah ditekan jempol). Bisa digulir bila layar pendek.
 */
@Composable
private fun AddAccountModal(
    onDismiss: () -> Unit,
    onAccountAdded: (UserAccount) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf("offline") } // "offline", "microsoft", "elyby"

    // Offline State
    var offlineName by remember { mutableStateOf("") }

    // Microsoft State
    var msDeviceCode by remember { mutableStateOf<MicrosoftDeviceCode?>(null) }
    var msErrorMsg by remember { mutableStateOf<String?>(null) }
    var isMsLoading by remember { mutableStateOf(false) }

    // Ely.by State (Browser Device Code Flow)
    val elyLoginState by ElyByAuthService.loginState.collectAsState()

    // Handle Ely.by login success
    LaunchedEffect(elyLoginState) {
        val state = elyLoginState
        if (state is ElyByLoginState.Success) {
            Toast.makeText(context, "Akun ${state.account.username} berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
            ElyByAuthService.resetState()
            onDismiss()
        }
    }

    NuxDialog(
        onDismissRequest = {
            onDismiss()
        },
        modifier = Modifier
            .fillMaxWidth(0.72f)
            .wrapContentHeight(),
        fillMaxHeight = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tambah akun",
                        color = NuxColors.DarkGray,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        text = "Pilih cara masuk ke Minecraft",
                        color = NuxColors.GrayNeutral,
                        fontSize = 12.sp
                    )
                }
                AccountIconAction(
                    icon = Icons.Outlined.Close,
                    description = "Tutup",
                    ring = true,
                    onClick = { onDismiss() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3 pilihan penyedia
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProviderCard(
                    title = "Offline",
                    description = "Gratis · tanpa login",
                    isSelected = selectedTab == "offline",
                    accentColor = NuxColors.Yellow,
                    icon = Icons.Outlined.Person,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = "offline" }
                )
                ProviderCard(
                    title = "Microsoft",
                    description = "Resmi · akun Mojang",
                    isSelected = selectedTab == "microsoft",
                    accentColor = NuxColors.SkyBlueDark,
                    icon = Icons.Outlined.Language,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = "microsoft" }
                )
                ProviderCard(
                    title = "Ely.by",
                    description = "Browser · skin & jubah",
                    isSelected = selectedTab == "elyby",
                    accentColor = NuxColors.PurpleDark,
                    icon = Icons.Outlined.Public,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = "elyby" }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                // =============================================
                // OFFLINE
                // =============================================
                "offline" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Kepala pemain (pratinjau nama)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(14.dp))
                                .border(NuxSizes.GlassBorder, NuxColors.CardBorder, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (offlineName.trim().isNotBlank()) {
                                NuxNetworkImage(
                                    model = "https://mc-heads.net/avatar/${offlineName.trim()}/80",
                                    contentDescription = offlineName,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    fallbackInitials = offlineName.trim().take(2).uppercase(),
                                    shape = RoundedCornerShape(10.dp),
                                    maxSidePx = 128
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = NuxColors.GrayNeutral,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            NuxTextField(
                                value = offlineName,
                                onValueChange = { offlineName = it },
                                placeholder = "Nama pemain (Steve, Alex...)"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    NuxButton(
                        onClick = {
                            val cleanName = offlineName.trim()
                            if (cleanName.isBlank()) {
                                Toast.makeText(context, "Username tidak boleh kosong", Toast.LENGTH_SHORT).show()
                                return@NuxButton
                            }
                            val offlineUuid = UUID.nameUUIDFromBytes("OfflinePlayer:$cleanName".toByteArray(Charsets.UTF_8))
                                .toString()
                                .replace("-", "")
                            val newAcc = UserAccount(
                                id = "offline_${System.currentTimeMillis()}",
                                username = cleanName,
                                uuid = offlineUuid,
                                accessToken = "0",
                                isOffline = true,
                                accountType = "offline"
                            )
                            onAccountAdded(newAcc)
                        },
                        backgroundColor = NuxColors.Yellow,
                        enabled = offlineName.trim().isNotBlank(),
                        cornerRadius = 24.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Simpan akun",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }

                // =============================================
                // MICROSOFT
                // =============================================
                "microsoft" -> {
                    Text(
                        text = "Masuk dengan akun Microsoft / Xbox",
                        color = NuxColors.SkyBlueDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Jendela otorisasi dibuka langsung di launcher, tanpa perlu keluar aplikasi.",
                        color = NuxColors.GrayNeutral,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    if (msErrorMsg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = msErrorMsg!!,
                            color = NuxColors.ErrorRed,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    NuxButton(
                        onClick = {
                            isMsLoading = true
                            msErrorMsg = null
                            scope.launch {
                                val res = MicrosoftAuthService.startDeviceCodeFlow()
                                isMsLoading = false
                                res.fold(
                                    onSuccess = { dc ->
                                        msDeviceCode = dc
                                    },
                                    onFailure = { err ->
                                        msErrorMsg = err.message ?: "Gagal memulai otorisasi Microsoft."
                                    }
                                )
                            }
                        },
                        backgroundColor = NuxColors.Yellow,
                        enabled = !isMsLoading,
                        cornerRadius = 24.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isMsLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = NuxColors.GrayNeutral,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Menghubungi...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Masuk dengan Microsoft", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                }

                // =============================================
                // ELY.BY
                // =============================================
                "elyby" -> {
                    when (val state = elyLoginState) {
                        is ElyByLoginState.WaitingForApproval -> {
                            val dc = state.deviceCode
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = NuxColors.PurpleDark,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = state.statusMsg.ifBlank { "Menunggu otorisasi di browser Ely.by..." },
                                    color = NuxColors.DarkGray,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(NuxColors.SurfaceInput, RoundedCornerShape(14.dp))
                                        .border(NuxSizes.GlassBorder, NuxColors.CardBorder, RoundedCornerShape(14.dp))
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = dc.userCode,
                                        color = NuxColors.PurpleDark,
                                        fontSize = 20.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                NuxButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(dc.userCode))
                                        Toast.makeText(context, "Kode disalin", Toast.LENGTH_SHORT).show()
                                    },
                                    backgroundColor = Color.Transparent,
                                    cornerRadius = 22.dp,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Salin", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                NuxButton(
                                    onClick = {
                                        ElyByAuthService.openBrowser(context, dc.verificationUrlWithCode)
                                    },
                                    backgroundColor = NuxColors.Yellow,
                                    cornerRadius = 24.dp,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Buka lagi", fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                                NuxButton(
                                    onClick = {
                                        ElyByAuthService.cancelLogin()
                                    },
                                    backgroundColor = Color.Transparent,
                                    cornerRadius = 24.dp,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Batal", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                        else -> {
                            val isStarting = state is ElyByLoginState.Starting
                            val errMsg = (state as? ElyByLoginState.Error)?.message

                            Text(
                                text = "Masuk dengan akun Ely.by",
                                color = NuxColors.PurpleDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Browser dibuka otomatis untuk konfirmasi akun Ely.by, tanpa mengetik password.",
                                color = NuxColors.GrayNeutral,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            if (errMsg != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = errMsg,
                                    color = NuxColors.ErrorRed,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            NuxButton(
                                onClick = {
                                    ElyByAuthService.startLogin(context)
                                },
                                backgroundColor = NuxColors.Yellow,
                                enabled = !isStarting,
                                cornerRadius = 24.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isStarting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = NuxColors.GrayNeutral,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Menghubungi...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.OpenInBrowser,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Buka browser Ely.by", fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (msDeviceCode != null) {
        NuxMicrosoftAuthDialog(
            deviceCode = msDeviceCode!!,
            onDismiss = {
                msDeviceCode = null
            },
            onSuccess = { acc ->
                msDeviceCode = null
                onAccountAdded(acc)
            },
            onError = { err ->
                msDeviceCode = null
                msErrorMsg = err
            }
        )
    }
}

/**
 * Kartu pilihan penyedia akun. Terpilih = tint dan tepi warna penyedia.
 */
@Composable
private fun ProviderCard(
    title: String,
    description: String,
    isSelected: Boolean,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (isSelected) accentColor.copy(alpha = 0.14f) else NuxColors.SurfaceInput,
                shape
            )
            .border(
                NuxSizes.GlassBorder,
                if (isSelected) accentColor else NuxColors.CardBorder,
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) accentColor else NuxColors.GrayNeutral,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = if (isSelected) accentColor else NuxColors.DarkGray,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = description,
            color = NuxColors.GrayNeutral,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2
        )
    }
}

@Composable
fun NuxAccountAvatar(
    account: UserAccount,
    modifier: Modifier = Modifier,
    fallbackInitials: String = account.username.take(2).uppercase()
) {
    val context = LocalContext.current
    val headBitmapState = produceState<Bitmap?>(
        initialValue = null,
        account.id,
        account.customSkinPath,
        account.safeAccountType
    ) {
        value = SkinUtils.getAccountHeadBitmap(context, account, targetSize = 64)
    }

    val headBitmap = headBitmapState.value

    if (headBitmap != null) {
        Image(
            bitmap = headBitmap.asImageBitmap(),
            contentDescription = account.username,
            modifier = modifier,
            filterQuality = FilterQuality.None
        )
    } else {
        Box(
            modifier = modifier.background(NuxColors.SurfaceInput),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackInitials,
                color = NuxColors.DarkGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

