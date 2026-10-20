package com.israadev.nuxlauncher.ui.dialogs

import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.ui.components.AmberAlert
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.components.AmberInfoBox
import com.israadev.nuxlauncher.ui.theme.Amber

/**
 * Konfirmasi hapus instance. Tombol merah hanya muncul di sini, jadi tidak ada hapus tanpa sengaja.
 */
@Composable
fun NuxDeleteInstanceDialog(
    instance: Instance,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val loaderLine = buildString {
        append("Minecraft ")
        append(instance.mcVersion)
        append("  \u00B7  ")
        append(instance.loader.uppercase())
        if (instance.loaderVersion.isNotBlank()) {
            append(" ")
            append(instance.loaderVersion)
        }
    }

    AmberAlert(
        onDismissRequest = onDismiss,
        title = "Hapus instance?",
        icon = "trash",
        accent = Amber.Danger,
        actions = {
            AmberButton(
                text = "Batal",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
            AmberButton(
                text = "Hapus",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Danger,
                icon = "trash"
            )
        }
    ) {
        AmberInfoBox {
            Text(
                text = instance.name,
                color = Amber.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = loaderLine,
                color = Amber.TextDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "Semua dunia, mod, savegame, dan konfigurasi di instance ini akan dihapus permanen dan tidak bisa dikembalikan.",
            color = Amber.TextDim,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
