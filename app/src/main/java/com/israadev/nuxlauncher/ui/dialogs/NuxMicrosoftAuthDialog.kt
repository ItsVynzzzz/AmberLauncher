package com.israadev.nuxlauncher.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.ui.i18n.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.account.microsoft.MicrosoftAuthService
import com.israadev.nuxlauncher.core.account.microsoft.MicrosoftDeviceCode
import com.israadev.nuxlauncher.core.models.UserAccount
import com.israadev.nuxlauncher.ui.components.NuxButton
import com.israadev.nuxlauncher.ui.components.NuxDialog
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.israadev.nuxlauncher.ui.components.AmberSpinner

private const val CHROME_PACKAGE = "com.android.chrome"

/**
 * Dialog login Microsoft / Xbox Live lewat browser HP.
 *
 * Halaman masuk Microsoft di WebView sering berhenti di layar kosong, jadi login dikerjakan di
 * browser sungguhan (Chrome bila ada): browser dibuka otomatis dengan kode perangkat sudah terisi,
 * sedangkan launcher hanya menunggu hasilnya. Dialog ini sendiri hanya menampilkan status dan
 * tombol "Buka di browser" untuk pengguna yang kembali ke launcher tanpa sengaja.
 */
@Composable
fun NuxMicrosoftAuthDialog(
    deviceCode: MicrosoftDeviceCode,
    onDismiss: () -> Unit,
    onSuccess: (UserAccount) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isCancelled by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Menunggu login di browser...") }

    val linkUrl = remember(deviceCode.userCode) { "https://microsoft.com/link?otc=${deviceCode.userCode}" }

    // Chrome lebih dulu (login Microsoft paling lancar di sana); kalau tidak ada, browser bawaan HP.
    val openInBrowser: () -> Unit = {
        fun tryStart(pkg: String?): Boolean = try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pkg != null) intent.setPackage(pkg)
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
        if (!tryStart(CHROME_PACKAGE) && !tryStart(null)) {
            Toast.makeText(context, "Tidak ada browser yang bisa dibuka", Toast.LENGTH_SHORT).show()
        }
    }

    // Salin kode lalu buka browser otomatis, sekali saat dialog pertama kali muncul
    LaunchedEffect(deviceCode.userCode) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Microsoft User Code", deviceCode.userCode))
        openInBrowser()
    }

    // Polling Coroutine yang aktif selama dialog terbuka
    LaunchedEffect(deviceCode) {
        withContext(Dispatchers.IO) {
            val tokenResult = MicrosoftAuthService.pollForToken(
                deviceCode = deviceCode,
                isCancelled = { isCancelled },
                onStatusUpdate = { newStatus ->
                    scope.launch { statusText = newStatus }
                }
            )

            tokenResult.fold(
                onSuccess = { (msAccessToken, msRefreshToken) ->
                    scope.launch { statusText = "Token diterima! Memproses autentikasi Minecraft & Xbox..." }
                    val mcAuthResult = MicrosoftAuthService.completeMinecraftAuth(
                        msAccessToken = msAccessToken,
                        msRefreshToken = msRefreshToken,
                        onStatusUpdate = { stepStatus ->
                            scope.launch { statusText = stepStatus }
                        }
                    )

                    mcAuthResult.fold(
                        onSuccess = { account ->
                            LauncherLog.event("Login", "Login Microsoft berhasil")
                            withContext(Dispatchers.Main) {
                                onSuccess(account)
                            }
                        },
                        onFailure = { err ->
                            if (err !is CancellationException && !isCancelled) {
                                LauncherLog.w("Login", "Autentikasi Minecraft gagal: ${err.message}", err)
                                withContext(Dispatchers.Main) {
                                    onError(err.message ?: "Gagal memproses autentikasi Minecraft.")
                                }
                            }
                        }
                    )
                },
                onFailure = { err ->
                    if (err !is CancellationException && !isCancelled) {
                        LauncherLog.w("Login", "Otorisasi Microsoft gagal: ${err.message}", err)
                        withContext(Dispatchers.Main) {
                            onError(err.message ?: "Otorisasi Microsoft gagal.")
                        }
                    }
                }
            )
        }
    }

    // Hentikan polling saat dialog ditutup
    DisposableEffect(Unit) {
        onDispose {
            isCancelled = true
        }
    }

    NuxDialog(
        onDismissRequest = {
            isCancelled = true
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Judul + tombol tutup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF0078D4).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF0078D4).copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = "Microsoft",
                            tint = Color(0xFF0078D4),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "LOGIN AKUN MICROSOFT",
                            color = NuxColors.DarkGray,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Selesaikan login di browser, lalu kembali ke sini",
                            color = NuxColors.GrayNeutral,
                            fontSize = 9.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(NuxColors.SurfaceInput, CircleShape)
                        .border(NuxSizes.BorderWidth, NuxColors.CardBorder, CircleShape)
                        .clickable {
                            isCancelled = true
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Tutup",
                        tint = NuxColors.GrayNeutral,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Status / loading
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NuxColors.SurfaceInput, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AmberSpinner(
                    modifier = Modifier.size(16.dp),
                    color = Color(0xFF0078D4),
                    strokeWidth = 2.dp
                )
                Text(
                    text = statusText,
                    color = NuxColors.DarkGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Kode perangkat (ketuk untuk menyalin)
            Row(
                modifier = Modifier
                    .background(NuxColors.SurfaceInput, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF0078D4).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Microsoft User Code", deviceCode.userCode))
                        Toast.makeText(context, "Kode '${deviceCode.userCode}' disalin!", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "KODE:",
                    color = NuxColors.GrayNeutral,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = deviceCode.userCode,
                    color = Color(0xFF0078D4),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Salin Kode",
                    tint = Color(0xFF0078D4),
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Kode sudah disalin. Bila kolom kode di browser masih kosong, tempel lalu pilih Lanjutkan.",
                color = NuxColors.GrayNeutral,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Untuk yang tidak sengaja kembali ke launcher sebelum selesai login
            NuxButton(
                onClick = openInBrowser,
                backgroundColor = NuxColors.Yellow,
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Buka di browser", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    }
}
