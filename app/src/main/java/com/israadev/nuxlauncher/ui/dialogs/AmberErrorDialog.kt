package com.israadev.nuxlauncher.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.components.AmberAlert
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.theme.Amber
import com.israadev.nuxlauncher.ui.theme.AmberUi

/**
 * Dialog galat yang menetap sampai ditutup (Toast hilang dalam beberapa detik, dan terlalu singkat untuk dibaca
 * atau disalin). Menjelaskan apa yang terjadi, dan menawarkan "Coba lagi" (bila ada) serta "Salin detail" untuk laporan.
 */
@Composable
fun AmberErrorDialog(
    title: String,
    message: String,
    detail: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val context = LocalContext.current
    AmberAlert(
        onDismissRequest = onDismiss,
        title = title,
        icon = "warning",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Salin detail",
                onClick = {
                    copyToClipboard(context, detail)
                    Toast.makeText(context, "Detail disalin", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
            AmberButton(
                text = if (onRetry != null) "Coba lagi" else "Tutup",
                onClick = { if (onRetry != null) onRetry() else onDismiss() },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Primary
            )
        }
    ) {
        Text(
            text = message,
            color = AmberUi.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AmberLauncher", text))
    } catch (_: Exception) {
    }
}
