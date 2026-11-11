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
 * Jubah ikut digambar di punggung (geser untuk melihatnya dari belakang). Akun Microsoft memakai jubah
 * aktifnya di server Mojang, yaitu yang juga muncul di dalam game; akun lain memakai jubah lokalnya.
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

    // Jubah dimuat terpisah dari skin supaya skin tampil duluan; naik revisi = jubah diganti di wardrobe
    val capeRevision by SkinUtils.capeRevision.collectAsState()
    val capeState = produceState<ImageBitmap?>(
        initialValue = null,
        account.id,
        account.customCapePath,
        account.safeAccountType,
        capeRevision
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = if (account.safeAccountType == "microsoft") {
                    SkinUtils.ensureMojangCape(context, account)
                } else {
                    SkinUtils.resolveCapeFile(context, account)
                }
                file?.takeIf { it.length() > 0 }
                    ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    val cape = capeState.value

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
            drawSkin3D(skin, cape, slim, angle)
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
private fun DrawScope.drawSkin3D(img: ImageBitmap, cape: ImageBitmap?, slim: Boolean, angle: Float) {
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

    // Jubah menggantung di belakang badan: dilihat dari depan ia terjauh (digambar dulu),
    // dilihat dari belakang ia terdekat (digambar terakhir).
    val capeBehind = cosA >= 0f
    if (cape != null && capeBehind) drawCape(cape, unit, ox, oy, cosA, sinA)

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

    if (cape != null && !capeBehind) drawCape(cape, unit, ox, oy, cosA, sinA)
}

/**
 * Jubah: kotak 10 x 16 x 1 tepat di belakang badan (tanpa kemiringan). Sisi luar, yang terlihat dari
 * belakang, memakai petak (1,1) pada tekstur jubah; dua sisi samping setebal 1 piksel. Tekstur HD
 * (128x64 dan seterusnya) diskalakan lewat [s]. Sisi dalam dan atas tertutup badan, jadi tidak digambar.
 */
private fun DrawScope.drawCape(cape: ImageBitmap, unit: Float, ox: Float, oy: Float, cosA: Float, sinA: Float) {
    val s = when {
        cape.width >= 64 -> cape.width / 64
        cape.width >= 22 -> cape.width / 22 // format lama 22x17
        else -> 1
    }
    // Petak yang dibutuhkan harus ada di dalam gambar (22 x 17 satuan tekstur)
    if (cape.width < 22 * s || cape.height < 17 * s) return

    val top = 8f      // sejajar bahu
    val hw = 5f       // lebar 10
    val zIn = 2f      // menempel punggung (badan setebal 4, dari -2 sampai 2)
    val zOut = 3f

    fun face(u0: Int, v0: Int, srcW: Int, x1: Float, z1: Float, x2: Float, z2: Float) {
        val sx1 = x1 * cosA + z1 * sinA
        val sx2 = x2 * cosA + z2 * sinA
        if (sx2 - sx1 < 0.01f) return   // sisi membelakangi kamera
        val dstW = ((sx2 - sx1) * unit).roundToInt().coerceAtLeast(1)
        drawImage(
            image = cape,
            srcOffset = IntOffset(u0 * s, v0 * s),
            srcSize = IntSize(srcW * s, 16 * s),
            dstOffset = IntOffset((ox + sx1 * unit).roundToInt(), (oy + top * unit).roundToInt()),
            dstSize = IntSize(dstW, (16 * unit).roundToInt()),
            filterQuality = FilterQuality.None
        )
    }

    face(1, 1, 10, hw, zOut, -hw, zOut)   // luar (terlihat dari belakang)
    face(0, 1, 1, -hw, zOut, -hw, zIn)    // sisi kanan tubuh
    face(11, 1, 1, hw, zIn, hw, zOut)     // sisi kiri tubuh
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

    val tintOffline = AmberUi.AccentSoft
    val tintMicrosoft = Color(0xFF6CB6FF)
    val tintElyBy = Color(0xFFB79CFF)

    val saveOffline: () -> Unit = {
        val cleanName = offlineName.trim()
        if (cleanName.isBlank()) {
            Toast.makeText(context, "Username tidak boleh kosong", Toast.LENGTH_SHORT).show()
        } else {
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
        }
    }

    // Isi kartu kanan: judul penyedia terpilih, lalu form/aksi khusus penyedia itu.
    val providerPanel: @Composable () -> Unit = {
        val tint = when (selectedTab) {
            "microsoft" -> tintMicrosoft
            "elyby" -> tintElyBy
            else -> tintOffline
        }
        val headIcon = when (selectedTab) {
            "microsoft" -> Icons.Outlined.Language
            "elyby" -> Icons.Outlined.Public
            else -> Icons.Outlined.Person
        }
        val headTitle = when (selectedTab) {
            "microsoft" -> "Masuk dengan akun Microsoft / Xbox"
            "elyby" -> "Masuk dengan akun Ely.by"
            else -> "Offline"
        }
        val headDesc = when (selectedTab) {
            "microsoft" -> "Jendela otorisasi dibuka langsung di launcher, tanpa perlu keluar aplikasi."
            "elyby" -> "Browser dibuka otomatis untuk konfirmasi akun Ely.by, tanpa mengetik password."
            else -> "Lokal · tanpa login"
        }
        val elyWaiting = selectedTab == "elyby" && elyLoginState is ElyByLoginState.WaitingForApproval

        AmberPanel {
            if (!elyWaiting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(tint.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = headIcon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = headTitle,
                        color = AmberUi.Text,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = headDesc,
                    color = AmberUi.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            when (selectedTab) {
                // ---------------- OFFLINE ----------------
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
                                .clip(CircleShape)
                                .background(AmberUi.SurfaceRaised, CircleShape)
                                .border(1.dp, AmberUi.Border, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (offlineName.trim().isNotBlank()) {
                                NuxNetworkImage(
                                    model = "https://mc-heads.net/avatar/${offlineName.trim()}/80",
                                    contentDescription = offlineName,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    fallbackInitials = offlineName.trim().take(2).uppercase(),
                                    shape = RoundedCornerShape(10.dp),
                                    maxSidePx = 128
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = AmberUi.TextDim,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        AmberField(
                            value = offlineName,
                            onValueChange = { offlineName = it },
                            placeholder = "Nama pemain (Steve, Alex...)",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    AmberActionButton(
                        text = "Simpan akun",
                        onClick = saveOffline,
                        icon = Icons.Outlined.Check,
                        enabled = offlineName.trim().isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ---------------- MICROSOFT ----------------
                "microsoft" -> {
                    if (msErrorMsg != null) {
                        Text(
                            text = msErrorMsg!!,
                            color = AmberUi.Danger,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    AmberActionButton(
                        text = if (isMsLoading) "Menghubungi..." else "Masuk dengan Microsoft",
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
                        icon = Icons.Outlined.Language,
                        loading = isMsLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ---------------- ELY.BY ----------------
                "elyby" -> {
                    when (val state = elyLoginState) {
                        is ElyByLoginState.WaitingForApproval -> {
                            val dc = state.deviceCode
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AmberSpinner(
                                    modifier = Modifier.size(18.dp),
                                    color = tintElyBy,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = state.statusMsg.ifBlank { "Menunggu otorisasi di browser Ely.by..." },
                                    color = AmberUi.Text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(CircleShape)
                                        .background(AmberUi.SurfaceRaised, CircleShape)
                                        .border(1.dp, AmberUi.BorderStrong, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dc.userCode,
                                        color = tintElyBy,
                                        fontSize = 18.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp,
                                        maxLines = 1
                                    )
                                }
                                AmberActionButton(
                                    text = "Salin",
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(dc.userCode))
                                        Toast.makeText(context, "Kode disalin", Toast.LENGTH_SHORT).show()
                                    },
                                    icon = Icons.Outlined.ContentCopy,
                                    filled = false,
                                    height = 48.dp
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AmberActionButton(
                                    text = "Buka lagi",
                                    onClick = {
                                        ElyByAuthService.openBrowser(context, dc.verificationUrlWithCode)
                                    },
                                    icon = Icons.Outlined.OpenInBrowser,
                                    modifier = Modifier.weight(1f)
                                )
                                AmberActionButton(
                                    text = "Batal",
                                    onClick = { ElyByAuthService.cancelLogin() },
                                    filled = false,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        else -> {
                            val isStarting = state is ElyByLoginState.Starting
                            val errMsg = (state as? ElyByLoginState.Error)?.message
                            if (errMsg != null) {
                                Text(
                                    text = errMsg,
                                    color = AmberUi.Danger,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                            AmberActionButton(
                                text = if (isStarting) "Menghubungi..." else "Buka browser Ely.by",
                                onClick = { ElyByAuthService.startLogin(context) },
                                icon = Icons.Outlined.OpenInBrowser,
                                loading = isStarting,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    AmberPage(
        onDismiss = onDismiss,
        title = "Tambah akun",
        icon = Icons.Outlined.PersonAdd,
        subtitle = "Pilih cara masuk ke Minecraft"
    ) { info ->
        if (info.narrow) {
            // HP portrait: tiga pil penyedia sejajar di atas, kartu di bawahnya, semua bisa digulir.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmberChoicePill(
                        text = "Offline",
                        selected = selectedTab == "offline",
                        onClick = { selectedTab = "offline" },
                        modifier = Modifier.weight(1f)
                    )
                    AmberChoicePill(
                        text = "Microsoft",
                        selected = selectedTab == "microsoft",
                        onClick = { selectedTab = "microsoft" },
                        modifier = Modifier.weight(1f)
                    )
                    AmberChoicePill(
                        text = "Ely.by",
                        selected = selectedTab == "elyby",
                        onClick = { selectedTab = "elyby" },
                        modifier = Modifier.weight(1f)
                    )
                }
                providerPanel()
            }
        } else {
            // Layar lebar: pil penyedia di rail kiri, kartu isi di kanan.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AmberRailItem(
                        title = "Offline",
                        description = "Lokal · tanpa login",
                        icon = Icons.Outlined.Person,
                        tint = tintOffline,
                        selected = selectedTab == "offline",
                        onClick = { selectedTab = "offline" }
                    )
                    AmberRailItem(
                        title = "Microsoft",
                        description = "Resmi · akun Mojang",
                        icon = Icons.Outlined.Language,
                        tint = tintMicrosoft,
                        selected = selectedTab == "microsoft",
                        onClick = { selectedTab = "microsoft" }
                    )
                    AmberRailItem(
                        title = "Ely.by",
                        description = "Browser · skin & jubah",
                        icon = Icons.Outlined.Public,
                        tint = tintElyBy,
                        selected = selectedTab == "elyby",
                        onClick = { selectedTab = "elyby" }
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .verticalScroll(rememberScrollState())
                ) {
                    providerPanel()
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

