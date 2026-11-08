package com.israadev.nuxlauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.israadev.nuxlauncher.core.settings.SettingsManager

/**
 * Saklar preview ketikan di Pengaturan > Kontrol > Kontrol sentuh. Menulis lewat SettingsManager langsung,
 * seperti MobileGluesCacheRow, jadi tidak menambah state di layar Pengaturan.
 */
@Composable
fun TypingPreviewSettingRow() {
    val context = LocalContext.current
    val settings by SettingsManager.settings.collectAsState()

    SettingSwitchRow(
        title = "Preview ketikan",
        desc = "Saat keyboard layar terbuka, huruf yang kamu ketik tampil di bar kecil tepat di atas keyboard supaya bisa diperiksa sebelum dikirim. Hanya menampilkan yang kamu ketik sejak keyboard dibuka.",
        checked = settings.typingPreview,
        onCheckedChange = { on ->
            SettingsManager.updateSettings(context, SettingsManager.settings.value.copy(typingPreview = on))
        }
    )
}
