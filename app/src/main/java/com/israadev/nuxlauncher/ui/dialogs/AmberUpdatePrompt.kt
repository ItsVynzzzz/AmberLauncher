package com.israadev.nuxlauncher.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.update.AppUpdate
import com.israadev.nuxlauncher.core.update.UpdateChecker
import com.israadev.nuxlauncher.ui.components.AmberAlert
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.components.AmberInfoBox
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.Amber

/**
 * Pop-up "Pembaruan tersedia". Saat aplikasi dibuka, rilis terbaru di GitHub dicek di latar
 * (paling sering sekali per 6 jam, lihat [UpdateChecker]). Kalau ada versi yang lebih baru, muncul
 * dialog berisi catatan perubahan dengan pilihan Unduh, Lewati, atau Nanti. Unduh membuka APK
 * rilis di peramban; Android yang menanyakan izin pemasangannya.
 *
 * [enabled] false menahan pengecekan, mis. selagi dialog izin penyimpanan sedang tampil.
 */
@Composable
fun AmberUpdatePrompt(enabled: Boolean) {
    val context = LocalContext.current
    var update by remember { mutableStateOf<AppUpdate?>(null) }

    LaunchedEffect(enabled) {
        if (enabled && update == null) {
            update = UpdateChecker.checkIfDue(context.applicationContext)
        }
    }

    val info = update
    if (info != null) {
        UpdateDialog(
            info = info,
            onLater = { update = null },
            onSkip = {
                UpdateChecker.skip(context, info.tag)
                update = null
            },
            onDownload = {
                openUrl(context, info.downloadUrl ?: info.pageUrl)
                update = null
            }
        )
    }
}

@Composable
private fun UpdateDialog(
    info: AppUpdate,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    val current = remember { UpdateChecker.currentVersionName(context) }
    val notes = remember(info) { UpdateChecker.readableNotes(info.notes) }

    AmberAlert(
        onDismissRequest = onLater,
        title = "Pembaruan tersedia",
        icon = "download",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Nanti",
                onClick = onLater,
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
            AmberButton(
                text = "Lewati",
                onClick = onSkip,
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
            AmberButton(
                text = "Unduh",
                onClick = onDownload,
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

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        // Tidak ada peramban: tidak ada yang bisa dilakukan.
    }
}
