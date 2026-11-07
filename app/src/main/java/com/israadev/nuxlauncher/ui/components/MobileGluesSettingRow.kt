package com.israadev.nuxlauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.israadev.nuxlauncher.core.settings.SettingsManager

/**
 * Saklar config MobileGlues di Pengaturan > Game > Grafis (hanya ditampilkan bila renderer yang dipilih
 * MobileGlues). Menulis lewat SettingsManager langsung, seperti DeviceProfileCard, jadi tidak menambah
 * state di layar Pengaturan.
 */
@Composable
fun MobileGluesCacheRow() {
    val context = LocalContext.current
    val settings by SettingsManager.settings.collectAsState()

    SettingSwitchRow(
        title = "Cache shader MobileGlues",
        desc = "Menyimpan hasil terjemahan shader di HP, jadi game berikutnya memuat shader lebih cepat dan lebih sedikit patah-patah. Dimatikan = MobileGlues memakai pengaturan bawaannya tanpa cache. Berlaku saat game dijalankan berikutnya.",
        checked = settings.mobileGluesConfig,
        onCheckedChange = { on ->
            SettingsManager.updateSettings(context, SettingsManager.settings.value.copy(mobileGluesConfig = on))
        }
    )
}
