package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.components.AmberAlert
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.Amber

/**
 * Konfirmasi impor layout kontrol. Mengganti layout yang sekarang, jadi perlu persetujuan dulu.
 * Bila berkas berisi makro pengetik chat/perintah, ditampilkan peringatan karena berkas dari orang
 * lain bisa membuat tombol yang mengirim perintah ke server saat diketuk.
 */
@Composable
fun NuxImportLayoutDialog(
    buttonCount: Int,
    macroCommands: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AmberAlert(
        onDismissRequest = onDismiss,
        title = "Impor layout kontrol?",
        icon = "warning",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Batal",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
            AmberButton(
                text = "Ganti layout",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Primary,
                icon = "download"
            )
        }
    ) {
        Text(
            text = "Layout tombol saat ini akan diganti dengan $buttonCount tombol dari berkas ini.",
            color = Amber.TextDim,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        if (macroCommands > 0) {
            Text(
                text = "Berkas ini berisi $macroCommands tombol makro yang mengetik chat atau perintah otomatis. Periksa dulu sebelum dipakai di server.",
                color = Amber.TextDim,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
