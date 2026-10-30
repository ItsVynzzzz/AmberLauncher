package com.israadev.nuxlauncher.ui.components

import com.israadev.nuxlauncher.ui.theme.AmberUi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.AppLinks
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.core.update.UpdateChecker
import com.israadev.nuxlauncher.core.update.UpdateManager
import com.israadev.nuxlauncher.core.update.UpdateState
import com.israadev.nuxlauncher.ui.dialogs.openUrl
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.NuxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Isi tab "Pembaruan" di Pengaturan: versi terpasang, status pembaruan, tombol "Periksa pembaruan",
 * dan sakelar pengecekan otomatis. Tombolnya selalu bertanya ke GitHub saat itu juga dan
 * menjelaskan hasilnya (termasuk kalau gagal), sedangkan unduh dan pasang berjalan di pop-up
 * pembaruan (lihat AmberUpdatePrompt).
 */
@Composable
fun UpdateSettingsSection() {
    val context = LocalContext.current
    val state by UpdateManager.state.collectAsState()
    val settings by SettingsManager.settings.collectAsState()

    val versionName = remember { UpdateChecker.currentVersionName(context) }
    val versionCode = remember { UpdateChecker.currentVersionCode(context) }
    val bits = remember { if (UpdateChecker.wants32BitApk()) "32-bit" else "64-bit" }

    SettingRowText(
        title = "Versi terpasang",
        desc = "$versionName · build $versionCode · $bits"
    )
    SettingDivider()

    // Status + tombol utama
    val (statusText, statusColor) = statusFor(state, bits)
    val checking = state is UpdateState.Checking
    val showsPrompt = when (state) {
        is UpdateState.Available,
        is UpdateState.Downloading,
        is UpdateState.ReadyToInstall,
        is UpdateState.NeedsInstallPermission,
        is UpdateState.Installing,
        is UpdateState.ActionFailed -> true
        else -> false
    }
    val buttonLabel = when {
        checking -> "Memeriksa..."
        state is UpdateState.Available -> "Lihat pembaruan"
        showsPrompt -> "Buka"
        else -> "Periksa pembaruan"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Pembaruan",
                color = NuxColors.DarkGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        NuxButton(
            onClick = {
                if (showsPrompt) UpdateManager.openPrompt() else UpdateManager.checkNow(context)
            },
            enabled = !checking,
            backgroundColor = AmberUi.Accent,
            borderColor = AmberUi.AccentEdge,
            cornerRadius = 20.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
        ) {
            if (checking) {
                AmberSpinner(
                    modifier = Modifier.size(14.dp),
                    color = NuxColors.Ink,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = buttonLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
        }
    }

    SettingDivider()
    SettingSwitchRow(
        title = "Periksa otomatis",
        desc = "Cek versi baru di GitHub setiap launcher dibuka (hanya beberapa KB). Kalau ada, muncul pop-up untuk mengunduh dan memasangnya.",
        checked = settings.autoCheckUpdates,
        onCheckedChange = { on ->
            SettingsManager.updateSettings(
                context,
                SettingsManager.settings.value.copy(autoCheckUpdates = on)
            )
        }
    )

    SettingDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SettingRowText(
            title = "Semua rilis",
            desc = "Daftar versi dan catatan perubahannya di GitHub.",
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        NuxButton(
            onClick = { openUrl(context, "https://github.com/${AppLinks.GITHUB_REPO}/releases") },
            backgroundColor = Color.Transparent,
            cornerRadius = 20.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Buka GitHub",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    Text(
        text = "Pembaruan diunduh dan dipasang dari dalam launcher. Android tetap meminta satu kali konfirmasi pemasangan, dan game harus ditutup dulu.",
        color = NuxColors.GrayNeutral,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** Kalimat status dan warnanya untuk tiap keadaan. */
private fun statusFor(state: UpdateState, bits: String): Pair<String, Color> = when (state) {
    is UpdateState.Idle ->
        "Belum diperiksa. Ketuk Periksa pembaruan." to NuxColors.GrayNeutral
    is UpdateState.Checking ->
        "Memeriksa pembaruan..." to NuxColors.GrayNeutral
    is UpdateState.UpToDate ->
        "Sudah versi terbaru. Diperiksa pukul ${clock(state.checkedAt)}." to NuxColors.DarkGray
    is UpdateState.NoApk ->
        "Versi ${state.tag.removePrefix("v")} sudah terbit, tetapi APK untuk HP ini ($bits) belum tersedia. Coba lagi nanti." to AmberUi.AccentSoft
    is UpdateState.Failed ->
        state.failure.message to NuxColors.ErrorRed
    is UpdateState.Available ->
        "Versi ${state.update.versionName} tersedia." to AmberUi.AccentSoft
    is UpdateState.Downloading -> {
        val percent = if (state.totalBytes > 0) (state.doneBytes * 100 / state.totalBytes).toInt() else 0
        "Mengunduh versi ${state.update.versionName}... $percent%" to AmberUi.AccentSoft
    }
    is UpdateState.ReadyToInstall ->
        "Versi ${state.update.versionName} siap dipasang." to AmberUi.AccentSoft
    is UpdateState.NeedsInstallPermission ->
        "Butuh izin memasang aplikasi dari Android." to AmberUi.AccentSoft
    is UpdateState.Installing ->
        "Menunggu konfirmasi pemasangan dari Android..." to AmberUi.AccentSoft
    is UpdateState.ActionFailed ->
        state.failure.message to NuxColors.ErrorRed
}

private fun clock(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
