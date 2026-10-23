package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.israadev.nuxlauncher.core.crash.GameCrashInfo
import com.israadev.nuxlauncher.ui.components.AmberCrashPanel
import com.israadev.nuxlauncher.ui.components.NuxDialog
import java.io.File

/**
 * Dialog crash di Beranda. Isinya ada di [AmberCrashPanel] (dipakai juga oleh halaman Error):
 * penjelasan dari mana crash berasal, ajakan mencari solusi lewat AI, lalu cara melapor.
 */
@Composable
fun NuxCrashDialog(
    crashInfo: GameCrashInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val logFile = remember(crashInfo.fullLogPath) {
        if (crashInfo.fullLogPath.isNotBlank()) File(crashInfo.fullLogPath)
        else File(context.filesDir, "latestlog.txt")
    }

    NuxDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 900.dp),
        fillMaxHeight = true,
        compact = true
    ) {
        AmberCrashPanel(
            info = crashInfo,
            logText = crashInfo.logSnippet,
            logFile = logFile,
            onClose = onDismiss
        )
    }
}
