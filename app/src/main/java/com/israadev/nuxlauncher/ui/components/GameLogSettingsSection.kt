package com.israadev.nuxlauncher.ui.components

import com.israadev.nuxlauncher.ui.theme.AmberUi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.crash.CrashUtils
import com.israadev.nuxlauncher.core.crash.GameLogArchive
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.theme.NuxColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Isi tab "Log game" di Pengaturan: daftar log sesi game yang tersimpan otomatis tiap keluar dari
 * Minecraft (lihat [GameLogArchive]), dengan tombol Bagikan untuk mengirimnya ke pengembang.
 */
@Composable
fun GameLogSettingsSection() {
    val context = LocalContext.current
    var logs by remember { mutableStateOf<List<File>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }

    // Sesi yang terputus (mis. game ditutup paksa oleh Android) disimpan dulu, lalu daftar dimuat.
    LaunchedEffect(reloadTick) {
        logs = withContext(Dispatchers.IO) {
            try {
                GameLogArchive.archiveIfNeeded(context)
            } catch (_: Throwable) {
            }
            GameLogArchive.list(context)
        }
        loaded = true
    }

    SettingRowText(
        title = "Log game",
        desc = "Setiap keluar dari Minecraft, log sesi itu disimpan otomatis (10 terakhir), tanpa token akun. " +
            "Bila ada masalah, misalnya shader tidak termuat, bagikan log terbaru ke pengembang."
    )
    SettingDivider()

    if (loaded && logs.isEmpty()) {
        Text(
            text = "Belum ada log. Mainkan game sekali lalu keluar; log tersimpan otomatis.",
            color = NuxColors.GrayNeutral,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(vertical = 9.dp)
        )
    }

    logs.forEachIndexed { index, file ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SettingRowText(
                title = if (index == 0) "Log terbaru" else "Log sebelumnya",
                desc = summary(file),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            NuxButton(
                onClick = {
                    CrashUtils.shareLogFile(context, file, "Log game AmberLauncher", "Bagikan log game")
                },
                backgroundColor = if (index == 0) AmberUi.Accent else Color.Transparent,
                cornerRadius = 20.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Bagikan",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        SettingDivider()
    }

    if (logs.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SettingRowText(
                title = "Hapus semua log",
                desc = null,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            NuxButton(
                onClick = {
                    if (!confirmClear) {
                        confirmClear = true
                    } else {
                        GameLogArchive.deleteAll(context)
                        confirmClear = false
                        reloadTick += 1
                        Toast.makeText(context, "Log dihapus.", Toast.LENGTH_SHORT).show()
                    }
                },
                backgroundColor = Color.Transparent,
                cornerRadius = 20.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (confirmClear) "Ketuk lagi untuk menghapus" else "Hapus",
                    color = if (confirmClear) NuxColors.ErrorRed else Color.Unspecified,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    Text(
        text = "Lokasi: " + GameLogArchive.logsDir(context).absolutePath,
        color = NuxColors.GrayNeutral,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 6.dp)
    )
    Text(
        text = "Kirim lewat WhatsApp, Telegram, atau Google Drive dari tombol Bagikan, atau ambil langsung dari folder itu lewat pengelola berkas.",
        color = NuxColors.GrayNeutral,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** Satu baris ringkas: waktu, nama instance, dan ukuran berkas. */
private fun summary(file: File): String {
    val (time, name) = GameLogArchive.describe(file)
    val bytes = file.length()
    val size = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024L -> (bytes / 1024L).toString() + " KB"
        else -> bytes.toString() + " B"
    }
    val label = name.replace('_', ' ').trim()
    return if (label.isEmpty()) "$time - $size" else "$time - $label - $size"
}
