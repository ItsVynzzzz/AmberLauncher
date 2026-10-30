package com.israadev.nuxlauncher.ui.screens

import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlin.math.roundToInt
import okhttp3.Request
import okhttp3.OkHttpClient
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.israadev.nuxlauncher.ui.theme.amberGlass
import com.israadev.nuxlauncher.ui.theme.AmberUi
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import com.israadev.nuxlauncher.ui.i18n.Toast
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
import androidx.compose.material3.Icon
import com.israadev.nuxlauncher.ui.i18n.Text
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
import com.israadev.nuxlauncher.ui.components.AmberSpinner

/**
 * Layar Akun gaya baru: kiri daftar akun berupa kartu panjang (ketuk untuk berganti, akun aktif
 * diberi lencana "Aktif"), kanan detail akun terpilih dengan pratinjau skin 3D yang ringan (diam, bisa diputar manual).
 * Tombol tambah akun ada di kanan atas, navigasi di pil kiri atas yang sama dengan layar lain.
 */
@Composable
fun AccountsScreen(
    onNavigateBack: () -> Unit,
    onOpenMods: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val accounts by AccountManager.accounts.collectAsState()
    val currentAccount by AccountManager.currentAccount.collectAsState()

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<UserAccount?>(null) }
    var accountForWardrobe by remember { mutableStateOf<UserAccount?>(null) }
    var refreshing by remember { mutableStateOf(false) }

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
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)
    ) {
        // Baris atas: navigasi di kiri, tambah akun di kanan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AmberNavPill(
                active = "accounts",
                activeLabel = "Akun",
                onBack = onNavigateBack,
                onMods = onOpenMods,
                onAccounts = {},
                onSettings = onOpenSettings
            )
            Spacer(modifier = Modifier.weight(1f))
            AmberPillButton(
                text = "Tambah akun",
                icon = Icons.Outlined.Add,
                onClick = { showAddAccountDialog = true }
            )
        }

        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = AmberUi.TextDim,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Belum ada akun",
                        color = AmberUi.Text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tambahkan akun Offline, Microsoft, atau Ely.by.",
                        color = AmberUi.TextDim,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // KIRI: daftar akun
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(accounts, key = { it.id }) { acc ->
                        AccountListCard(
                            account = acc,
                            isCurrent = acc.id == currentAccount?.id,
                            onClick = { AccountManager.selectAccount(acc) }
                        )
                    }
                }

                // KANAN: detail akun terpilih
                val acc = currentAccount
                if (acc != null) {
                    AccountDetailCard(
                        account = acc,
                        refreshing = refreshing,
                        onEdit = { openWardrobe(acc) },
                        onOpenSkin = { openWardrobe(acc) },
                        onRefresh = {
                            if (acc.safeAccountType != "microsoft") return@AccountDetailCard
                            coroutineScope.launch {
                                refreshing = true
                                MicrosoftAuthService.refreshMicrosoftAccount(acc)
                                    .onSuccess { fresh ->
                                        AccountManager.updateAccount(context, fresh)
                                        Toast.makeText(context, "Login berhasil disegarkan", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure { e ->
                                        Toast.makeText(
                                            context,
                                            e.message ?: "Gagal menyegarkan login",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                refreshing = false
                            }
                        },
                        onDelete = { accountToDelete = acc },
                        modifier = Modifier
                            .weight(1.7f)
                            .fillMaxHeight()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1.7f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pilih akun untuk bermain",
                            color = AmberUi.TextDim,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // DIALOG: SKIN & JUBAH
    if (accountForWardrobe != null) {
        NuxWardrobeDialog(
            account = accountForWardrobe!!,
            onDismissRequest = { accountForWardrobe = null },
            onSaved = { accountForWardrobe = null }
        )
    }

    // DIALOG: TAMBAH AKUN
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

    // DIALOG: KONFIRMASI HAPUS
    if (accountToDelete != null) {
        DeleteAccountDialog(
            target = accountToDelete!!,
            onDismiss = { accountToDelete = null }
        )
    }
}

/** Kartu satu akun di daftar kiri: avatar bulat, nama, jenis akun, dan lencana "Aktif". */
@Composable
private fun AccountListCard(
    account: UserAccount,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (isCurrent) {
                    Modifier
                        .background(AmberUi.SurfaceRaised, shape)
                        .border(1.5.dp, AmberUi.BorderStrong, shape)
                } else {
                    Modifier.amberGlass(shape)
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NuxAccountAvatar(
            account = account,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.username,
                color = AmberUi.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = acctTypeLabel(account.safeAccountType),
                color = AmberUi.TextDim,
                fontSize = 14.sp,
                maxLines = 1
            )
        }
        if (isCurrent) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(CircleShape)
                    .background(AmberUi.Accent, CircleShape)
                    .border(1.dp, AmberUi.AccentEdge, CircleShape)
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = AmberUi.Text,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Aktif",
                    color = AmberUi.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/** Kartu detail akun aktif: pratinjau skin di kiri, info dan aksi di kanan. */
@Composable
private fun AccountDetailCard(
    account: UserAccount,
    refreshing: Boolean,
    onEdit: () -> Unit,
    onOpenSkin: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(30.dp)
    val type = account.safeAccountType
    val needsRefresh = type == "microsoft" && MicrosoftAuthService.needsRefresh(account)
    val (statusText, statusColor) = when {
        type == "offline" -> "Offline" to AmberUi.TextDim
        needsRefresh -> "Perlu disegarkan" to NuxColors.Amber
        else -> "Tersambung" to AmberUi.AccentSoft
    }

    Row(
        modifier = modifier
            .clip(shape)
            .amberGlass(shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pratinjau skin: gambar diam, tanpa animasi dan tanpa WebView supaya ringan
        Box(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            AccountSkinPreview(
                account = account,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.username,
                        color = AmberUi.Text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Akun aktif untuk bermain",
                        color = AmberUi.TextDim,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                AmberRoundIcon(
                    icon = Icons.Outlined.Edit,
                    description = "Ubah skin & jubah",
                    onClick = onEdit
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            AccountInfoRow(label = "Jenis akun") {
                Text(
                    text = acctTypeLabel(type),
                    color = AmberUi.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            AccountInfoRow(label = "Status login") {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    color = AmberUi.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            AccountInfoRow(label = "Skin", onClick = onOpenSkin) {
                Text(
                    text = if (account.safeSkinModel == "slim") "Slim" else "Klasik",
                    color = AmberUi.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = AmberUi.TextDim,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AmberPillButton(
                    text = if (refreshing) "Menyegarkan..." else "Segarkan login",
                    icon = Icons.Outlined.Refresh,
                    filled = false,
                    enabled = type == "microsoft" && !refreshing,
                    onClick = onRefresh
                )
                AmberPillButton(
                    text = "Hapus akun",
                    icon = Icons.Outlined.Delete,
                    filled = false,
                    danger = true,
                    onClick = onDelete
                )
            }
        }
    }
}

/** Satu baris info (label redup di kiri, nilai di kanan) dengan garis pemisah di atasnya. */
@Composable
private fun AccountInfoRow(
    label: String,
    onClick: (() -> Unit)? = null,
    value: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AmberUi.Border)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AmberUi.TextDim,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        value()
    }
}

/**
 * Pratinjau skin 3D yang ringan: enam kotak (kepala, badan, dua tangan, dua kaki) digambar langsung
 * dari berkas skin di Canvas, lengkap dengan lapisan luarnya. Diam saja (tanpa putar otomatis dan
 * tanpa animasi), tetapi bisa diputar dengan menggeser ke kiri atau kanan. Tanpa WebView.
 * Akun Microsoft yang belum punya berkas lokal mengunduh skinnya sekali dari mc-heads.net.
 */
@Composable
private fun AccountSkinPreview(
    account: UserAccount,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val skinState = produceState<ImageBitmap?>(
        initialValue = null,
        account.id,
        account.customSkinPath,
        account.skinModel,
        account.safeAccountType
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val updated = SkinUtils.ensureSkinAndCapeCached(context, account)
                val local = SkinUtils.resolveSkinFile(context, updated)
                val bmp = if (local != null) {
                    BitmapFactory.decodeFile(local.absolutePath)
                } else if (account.safeAccountType == "microsoft" && account.uuid.isNotBlank()) {
                    val req = Request.Builder().url("https://mc-heads.net/skin/${account.uuid}").build()
                    OkHttpClient().newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                        } else null
                    }
                } else null
                bmp?.asImageBitmap()
            }.getOrNull()
        }
    }
    val skin = skinState.value
    val slim = account.safeSkinModel == "slim"

    // Sudut putar: dibaca hanya saat menggambar, jadi menggeser tidak memicu komposisi ulang
    var angle by remember(account.id) { mutableFloatStateOf(0.5f) }

    if (skin == null) {
        // Belum ada berkas skin: tampilkan kepala saja
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            NuxAccountAvatar(
                account = account,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    } else {
        Canvas(
            modifier = modifier
                .padding(vertical = 6.dp)
                .pointerInput(account.id) {
                    detectHorizontalDragGestures { _, dragX -> angle += dragX * 0.012f }
                }
        ) {
            drawSkin3D(skin, slim, angle)
        }
    }
}

/** Satu kotak bagian tubuh: posisi (pusat x, sisi atas) dan ukuran dalam piksel skin, plus asal tekstur. */
private class SkinBox(
    val cx: Float,
    val top: Float,
    val w: Int,
    val h: Int,
    val d: Int,
    val u: Int,
    val v: Int,
    val ou: Int,
    val ov: Int,
    val hasOverlay: Boolean
)

/**
 * Menggambar model skin diputar sebesar [angle] radian mengelilingi sumbu tegak. Karena hanya berputar
 * ke samping (tanpa miring), setiap sisi tegak tampil sebagai persegi panjang yang lebarnya mengecil,
 * jadi cukup beberapa drawImage tanpa matriks. Bagian terjauh digambar lebih dulu.
 */
private fun DrawScope.drawSkin3D(img: ImageBitmap, slim: Boolean, angle: Float) {
    val legacy = img.height < 64
    val armW = if (slim) 3 else 4
    val unit = minOf(size.width / 16f, size.height / 32.6f)
    if (unit <= 0f) return
    val ox = size.width / 2f
    val oy = (size.height - 32f * unit) / 2f
    val cosA = cos(angle)
    val sinA = sin(angle)

    // Bayangan lembut di bawah kaki
    drawOval(
        color = Color.Black.copy(alpha = 0.32f),
        topLeft = Offset(ox - 6f * unit, oy + 32f * unit - unit * 0.3f),
        size = Size(12f * unit, unit * 1.3f)
    )

    val boxes = listOf(
        SkinBox(0f, 0f, 8, 8, 8, 0, 0, 32, 0, true),                                  // kepala + topi
        SkinBox(0f, 8f, 8, 12, 4, 16, 16, 16, 32, !legacy),                           // badan
        SkinBox(-(4f + armW / 2f), 8f, armW, 12, 4, 40, 16, 40, 32, !legacy),         // tangan kanan
        SkinBox(
            4f + armW / 2f, 8f, armW, 12, 4,
            if (legacy) 40 else 32, if (legacy) 16 else 48, 48, 48, !legacy
        ),                                                                            // tangan kiri
        SkinBox(-2f, 20f, 4, 12, 4, 0, 16, 0, 32, !legacy),                           // kaki kanan
        SkinBox(2f, 20f, 4, 12, 4, if (legacy) 0 else 16, if (legacy) 16 else 48, 0, 48, !legacy) // kaki kiri
    )

    for (b in boxes.sortedByDescending { -it.cx * sinA }) {
        val hw = b.w / 2f
        val hd = b.d / 2f

        fun face(u0: Int, v0: Int, srcW: Int, x1: Float, z1: Float, x2: Float, z2: Float) {
            val sx1 = (b.cx + x1) * cosA + z1 * sinA
            val sx2 = (b.cx + x2) * cosA + z2 * sinA
            if (sx2 - sx1 < 0.01f) return   // sisi membelakangi kamera
            val dstW = ((sx2 - sx1) * unit).roundToInt().coerceAtLeast(1)
            drawImage(
                image = img,
                srcOffset = IntOffset(u0, v0),
                srcSize = IntSize(srcW, b.h),
                dstOffset = IntOffset((ox + sx1 * unit).roundToInt(), (oy + b.top * unit).roundToInt()),
                dstSize = IntSize(dstW, (b.h * unit).roundToInt()),
                filterQuality = FilterQuality.None
            )
        }

        fun layer(u: Int, v: Int) {
            face(u + b.d, v + b.d, b.w, -hw, -hd, hw, -hd)             // depan
            face(u + 2 * b.d + b.w, v + b.d, b.w, hw, hd, -hw, hd)     // belakang
            face(u, v + b.d, b.d, -hw, hd, -hw, -hd)                   // sisi kanan tubuh
            face(u + b.d + b.w, v + b.d, b.d, hw, -hd, hw, hd)         // sisi kiri tubuh
        }

        layer(b.u, b.v)
        if (b.hasOverlay) layer(b.ou, b.ov)
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
            .padding(horizontal = 7.dp, vertical = 1.dp)
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

/** Tombol ikon bulat 38dp (skin & jubah, hapus). */
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
            .size(38.dp)
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
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Dialog tambah akun: 3 pilihan penyedia di atas, area aksi di bawah dengan tombol utama
 * kuning selebar dialog (mudah ditekan jempol). Bisa digulir bila layar pendek.
 */
/** Dialog konfirmasi hapus akun. Dipakai di layar Akun dan di menu akun cepat Home. */
@Composable
internal fun DeleteAccountDialog(
    target: UserAccount,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    NuxDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.55f)
            .wrapContentHeight(),
        compact = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(NuxColors.ErrorRed.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = NuxColors.ErrorRed,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Hapus akun?",
                color = NuxColors.DarkGray,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Hapus profil '${target.username}' (${acctTypeLabel(target.safeAccountType)}) dari perangkat ini?",
                color = NuxColors.GrayNeutral,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NuxButton(
                    onClick = onDismiss,
                    backgroundColor = Color.Transparent,
                    cornerRadius = 20.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Batal", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                NuxButton(
                    onClick = {
                        AccountManager.removeAccount(context, target)
                        Toast.makeText(context, "Profil '${target.username}' dihapus", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    backgroundColor = NuxColors.ErrorRed,
                    contentColor = NuxColors.DarkGray,
                    cornerRadius = 20.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Hapus", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
internal fun AddAccountModal(
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
        fillMaxHeight = false,
        compact = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
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
                        fontSize = 17.sp,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        text = "Pilih cara masuk ke Minecraft",
                        color = NuxColors.GrayNeutral,
                        fontSize = 11.sp
                    )
                }
                AccountIconAction(
                    icon = Icons.Outlined.Close,
                    description = "Tutup",
                    ring = true,
                    onClick = { onDismiss() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3 pilihan penyedia
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProviderCard(
                    title = "Offline",
                    description = "Lokal · tanpa login",
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

            Spacer(modifier = Modifier.height(12.dp))

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
                                .size(42.dp)
                                .background(NuxColors.SurfaceInput, RoundedCornerShape(12.dp))
                                .border(NuxSizes.GlassBorder, NuxColors.CardBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (offlineName.trim().isNotBlank()) {
                                NuxNetworkImage(
                                    model = "https://mc-heads.net/avatar/${offlineName.trim()}/80",
                                    contentDescription = offlineName,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    fallbackInitials = offlineName.trim().take(2).uppercase(),
                                    shape = RoundedCornerShape(8.dp),
                                    maxSidePx = 128
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = NuxColors.GrayNeutral,
                                    modifier = Modifier.size(22.dp)
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

                    Spacer(modifier = Modifier.height(10.dp))

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
                        cornerRadius = 20.dp,
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
                            fontSize = 13.sp
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
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Jendela otorisasi dibuka langsung di launcher, tanpa perlu keluar aplikasi.",
                        color = NuxColors.GrayNeutral,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    if (msErrorMsg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = msErrorMsg!!,
                            color = NuxColors.ErrorRed,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isMsLoading) {
                            AmberSpinner(
                                modifier = Modifier.size(16.dp),
                                color = NuxColors.GrayNeutral,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Menghubungi...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Masuk dengan Microsoft", fontWeight = FontWeight.Black, fontSize = 13.sp)
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
                                AmberSpinner(
                                    modifier = Modifier.size(16.dp),
                                    color = NuxColors.PurpleDark,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = state.statusMsg.ifBlank { "Menunggu otorisasi di browser Ely.by..." },
                                    color = NuxColors.DarkGray,
                                    fontSize = 12.sp,
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
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = dc.userCode,
                                        color = NuxColors.PurpleDark,
                                        fontSize = 17.sp,
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
                                    cornerRadius = 20.dp,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Salin", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                    cornerRadius = 20.dp,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Buka lagi", fontWeight = FontWeight.Black, fontSize = 13.sp)
                                }
                                NuxButton(
                                    onClick = {
                                        ElyByAuthService.cancelLogin()
                                    },
                                    backgroundColor = Color.Transparent,
                                    cornerRadius = 20.dp,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Batal", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Browser dibuka otomatis untuk konfirmasi akun Ely.by, tanpa mengetik password.",
                                color = NuxColors.GrayNeutral,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                            if (errMsg != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = errMsg,
                                    color = NuxColors.ErrorRed,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            NuxButton(
                                onClick = {
                                    ElyByAuthService.startLogin(context)
                                },
                                backgroundColor = NuxColors.Yellow,
                                enabled = !isStarting,
                                cornerRadius = 20.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isStarting) {
                                    AmberSpinner(
                                        modifier = Modifier.size(16.dp),
                                        color = NuxColors.GrayNeutral,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Menghubungi...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.OpenInBrowser,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Buka browser Ely.by", fontWeight = FontWeight.Black, fontSize = 13.sp)
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
    val shape = RoundedCornerShape(14.dp)
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
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) accentColor else NuxColors.GrayNeutral,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = if (isSelected) accentColor else NuxColors.DarkGray,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = description,
            color = NuxColors.GrayNeutral,
            fontSize = 11.sp,
            lineHeight = 15.sp,
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

