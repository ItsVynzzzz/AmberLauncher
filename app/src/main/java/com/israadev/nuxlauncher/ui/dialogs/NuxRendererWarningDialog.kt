package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.layout.padding
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.renderer.NuxRendererInfo
import com.israadev.nuxlauncher.ui.components.AmberAlert
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.components.AmberInfoBox
import com.israadev.nuxlauncher.ui.theme.Amber

/**
 * Peringatan saat perender yang dipilih tidak didukung resmi untuk versi Minecraft ini.
 * Pemain tetap bisa melanjutkan.
 */
@Composable
fun NuxRendererWarningDialog(
    renderer: NuxRendererInfo,
    mcVersion: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AmberAlert(
        onDismissRequest = onDismiss,
        title = "Perender tidak cocok",
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
                text = "Tetap jalankan",
                onClick = onConfirm,
                modifier = Modifier.weight(1.3f),
                style = AmberButtonStyle.Primary
            )
        }
    ) {
        AmberInfoBox {
            Text(
                text = renderer.displayName,
                color = Amber.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Minecraft $mcVersion",
                color = Amber.TextDim,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        Text(
            text = "Perender ini tidak didukung secara resmi untuk Minecraft $mcVersion. Memaksakannya bisa membuat tampilan rusak, layar membeku, atau game crash. Lanjutkan?",
            color = Amber.TextDim,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
        if (renderer.compatibility.isNotBlank()) {
            Text(
                text = "Dukungan resmi: ${renderer.compatibility}",
                color = Amber.Yellow,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
