package com.israadev.nuxlauncher.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.update.AppUpdate
import com.israadev.nuxlauncher.core.update.ReleaseParser
import com.israadev.nuxlauncher.core.update.UpdateChecker
import com.israadev.nuxlauncher.core.update.UpdateFailure
import com.israadev.nuxlauncher.core.update.UpdateManager
import com.israadev.nuxlauncher.core.update.UpdateState
import com.israadev.nuxlauncher.ui.components.AmberAlert
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.components.AmberInfoBox
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.Amber
import java.util.Locale

/**
 * Pop-up auto-update. Isinya mengikuti [UpdateManager.state]: ada versi baru, mengunduh (dengan
 * progres), siap dipasang, butuh izin pemasangan, menunggu konfirmasi Android, atau gagal. Muncul
 * sendiri bila pengecekan otomatis menemukan versi baru, dan terbuka dari tombol "Periksa pembaruan"
 * di Pengaturan. Pengecekannya dijalankan oleh [UpdateManager.onForeground] dari MainActivity.
 *
 * [enabled] false menahan pop-up, mis. selagi dialog izin penyimpanan sedang tampil.
 */
@Composable
fun AmberUpdatePrompt(enabled: Boolean) {
    val open by UpdateManager.promptOpen.collectAsState()
    val state by UpdateManager.state.collectAsState()
    if (!enabled || !open) return

    when (val s = state) {
        is UpdateState.Available -> AvailableDialog(s.update)
        is UpdateState.Downloading -> DownloadingDialog(s)
        is UpdateState.ReadyToInstall -> ReadyDialog(s)
        is UpdateState.NeedsInstallPermission -> PermissionDialog()
        is UpdateState.Installing -> InstallingDialog(s.update)
        is UpdateState.ActionFailed -> FailedDialog(s)
        else -> Unit // tidak ada yang perlu ditampilkan (belum ada versi baru)
    }
}

@Composable
private fun AvailableDialog(info: AppUpdate) {
    val context = LocalContext.current
    val current = remember { UpdateChecker.currentVersionName(context) }
    val notes = remember(info) { ReleaseParser.readableNotes(info.notes) }
    val metered = remember { isMetered(context) }

    AmberAlert(
        onDismissRequest = { UpdateManager.dismissPrompt() },
        title = "Pembaruan tersedia",
        icon = "download",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Nanti",
                onClick = { UpdateManager.dismissPrompt() },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
            AmberButton(
                text = "Lewati",
                onClick = { UpdateManager.skipVersion(context) },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
            AmberButton(
                text = "Unduh",
                onClick = { UpdateManager.startDownload(context) },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Primary
            )
        }
    ) {
        Text(
            text = "Versi ${info.versionName} sudah tersedia. Kamu memakai versi $current.",
            color = Amber.Text,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        info.apk.sizeBytes?.let { size ->
            Text(
                text = "Ukuran unduhan: ${formatMegabytes(size)} MB",
                color = Amber.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (metered) {
            Text(
                text = "Kamu sedang memakai data seluler, unduhan ini memakai kuota.",
                color = Amber.DangerSoft,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (notes.isNotBlank()) {
            AmberInfoBox(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "Yang baru",
                    color = Amber.Yellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Column(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = notes,
                        color = Amber.TextDim,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadingDialog(s: UpdateState.Downloading) {
    val fraction = if (s.totalBytes > 0) (s.doneBytes.toFloat() / s.totalBytes).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100f).toInt()

    AmberAlert(
        onDismissRequest = { UpdateManager.dismissPrompt() },
        title = "Mengunduh pembaruan",
        icon = "download",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Sembunyikan",
                onClick = { UpdateManager.dismissPrompt() },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
            AmberButton(
                text = "Batal",
                onClick = { UpdateManager.cancelDownload() },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Danger
            )
        }
    ) {
        Text(
            text = "Versi ${s.update.versionName}",
            color = Amber.Text,
            fontSize = 14.sp
        )
        if (s.totalBytes > 0) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Amber.Yellow,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
            Text(
                text = "$percent% · ${formatMegabytes(s.doneBytes)} / ${formatMegabytes(s.totalBytes)} MB",
                color = Amber.TextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Amber.Yellow,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
            Text(
                text = "${formatMegabytes(s.doneBytes)} MB",
                color = Amber.TextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Text(
            text = "Unduhan berjalan selama launcher terbuka. Kalau terputus, ketuk Unduh lagi; lanjut dari bagian yang sudah ada.",
            color = Amber.TextFaint,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun ReadyDialog(s: UpdateState.ReadyToInstall) {
    val context = LocalContext.current
    AmberAlert(
        onDismissRequest = { UpdateManager.dismissPrompt() },
        title = "Siap dipasang",
        icon = "download",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Nanti",
                onClick = { UpdateManager.dismissPrompt() },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
            AmberButton(
                text = "Pasang",
                onClick = { UpdateManager.install(context) },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Primary
            )
        }
    ) {
        Text(
            text = "Versi ${s.update.versionName} sudah diunduh dan diperiksa.",
            color = Amber.Text,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        if (s.note != null) {
            Text(
                text = s.note,
                color = Amber.DangerSoft,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Text(
            text = "Android akan meminta konfirmasi pemasangan. Launcher tertutup selama pemasangan; buka lagi setelah selesai.",
            color = Amber.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun PermissionDialog() {
    val context = LocalContext.current
    AmberAlert(
        onDismissRequest = { UpdateManager.dismissPrompt() },
        title = "Izin pemasangan diperlukan",
        icon = "gear",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Nanti",
                onClick = { UpdateManager.dismissPrompt() },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
            AmberButton(
                text = "Buka pengaturan",
                onClick = { UpdateManager.openInstallPermissionSettings(context) },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Primary
            )
        }
    ) {
        Text(
            text = "Android perlu izin agar AmberLauncher bisa memasang pembaruannya sendiri.",
            color = Amber.Text,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        Text(
            text = "Nyalakan \"Izinkan dari sumber ini\", lalu kembali ke sini. Pemasangan dilanjutkan otomatis.",
            color = Amber.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun InstallingDialog(update: AppUpdate) {
    AmberAlert(
        onDismissRequest = { UpdateManager.dismissPrompt() },
        title = "Memasang pembaruan",
        icon = "download",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Tutup",
                onClick = { UpdateManager.dismissPrompt() },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
        }
    ) {
        Text(
            text = "Versi ${update.versionName}",
            color = Amber.Text,
            fontSize = 14.sp
        )
        LinearProgressIndicator(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Amber.Yellow,
            trackColor = Color.White.copy(alpha = 0.12f)
        )
        Text(
            text = "Ikuti konfirmasi dari Android. Launcher tertutup otomatis saat pemasangan selesai.",
            color = Amber.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun FailedDialog(s: UpdateState.ActionFailed) {
    val context = LocalContext.current
    val failure: UpdateFailure = s.failure
    AmberAlert(
        onDismissRequest = { UpdateManager.dismissPrompt() },
        title = "Pembaruan gagal",
        icon = "warning",
        accent = Amber.Danger,
        actions = {
            AmberButton(
                text = "Tutup",
                onClick = { UpdateManager.dismissPrompt() },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
            AmberButton(
                text = "Buka GitHub",
                onClick = { openUrl(context, UpdateManager.releasePageUrl()) },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
            AmberButton(
                text = "Coba lagi",
                onClick = { UpdateManager.startDownload(context) },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Primary
            )
        }
    ) {
        Text(
            text = "Versi ${s.update.versionName}",
            color = Amber.TextDim,
            fontSize = 12.sp
        )
        Text(
            text = failure.message,
            color = Amber.Text,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

internal fun formatMegabytes(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f", bytes / (1024.0 * 1024.0))

private fun isMetered(context: Context): Boolean = try {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    cm.isActiveNetworkMetered
} catch (_: Throwable) {
    false
}

internal fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        // Tidak ada peramban: tidak ada yang bisa dilakukan.
    }
}
