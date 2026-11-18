package com.israadev.nuxlauncher.ui.dialogs

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.update.WhatsNew
import com.israadev.nuxlauncher.core.update.WhatsNewStore
import com.israadev.nuxlauncher.ui.components.AmberAlert
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.components.WhatsNewList
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.Amber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dialog "Yang baru" yang muncul sekali setelah launcher diperbarui (lihat [WhatsNewStore.pendingAutoShow]).
 * Isinya dalam bahasa launcher dan bisa digulir, jadi tidak terpotong di layar pendek. Dibuka dari Tentang
 * lewat tab "Yang baru", bukan lewat dialog ini.
 *
 * [enabled] false menahan dialog, mis. selagi dialog lain (izin penyimpanan, pembaruan) sedang tampil.
 */
@Composable
fun AmberWhatsNewPrompt(enabled: Boolean) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf<WhatsNew.Notes?>(null) }
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (enabled && !checked) {
            checked = true
            notes = withContext(Dispatchers.IO) { WhatsNewStore.pendingAutoShow(context) }
        }
    }

    val shown = notes ?: return
    val close = {
        WhatsNewStore.markSeen(context)
        notes = null
    }
    // Sisakan ruang untuk judul dan tombol; tidak kurang dari 120dp dan tidak lebih dari 360dp
    val maxListHeight = (LocalConfiguration.current.screenHeightDp - 220).coerceIn(120, 360).dp

    AmberAlert(
        onDismissRequest = close,
        title = "Yang baru",
        icon = "chat",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Tutup",
                onClick = close,
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Primary
            )
        }
    ) {
        if (shown.version.isNotBlank()) {
            Text(
                text = "Versi ${shown.version}",
                color = Amber.Text,
                fontSize = 14.sp
            )
        }
        Column(
            modifier = Modifier
                .padding(top = 10.dp)
                .heightIn(max = maxListHeight)
                .verticalScroll(rememberScrollState())
        ) {
            WhatsNewList(notes = shown)
        }
    }
}
