package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.device.DeviceProfile
import com.israadev.nuxlauncher.core.device.DeviceProfiles
import com.israadev.nuxlauncher.core.models.LauncherSettings
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.ui.theme.NuxColors

/**
 * Kartu "Profil Perangkat" di Pengaturan. Hanya tampil di HP yang punya profil khusus
 * (saat ini Oppo A3x 4G). Di HP lain fungsi ini tidak menggambar apa pun, jadi Pengaturan
 * tampil dan berfungsi persis seperti biasa.
 */
@Composable
fun DeviceProfileCard() {
    val profile = DeviceProfiles.detect()
    if (profile != null) {
        DeviceProfileCardContent(profile)
    }
}

@Composable
private fun DeviceProfileCardContent(profile: DeviceProfile) {
    val context = LocalContext.current
    val settings by SettingsManager.settings.collectAsState()
    val totalRamMb = remember { SettingsManager.getTotalDeviceMemoryMb(context) }
    val gcMode = DeviceProfiles.normalizeGcMode(settings.deviceProfileGcMode)
    val capApplies = DeviceProfiles.heapCapApplies(profile, totalRamMb)

    fun change(transform: (LauncherSettings) -> LauncherSettings) {
        SettingsManager.updateSettings(context, transform(SettingsManager.settings.value))
    }

    NuxCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        cornerRadius = 20.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = NuxColors.Yellow,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Profil perangkat",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = NuxColors.DarkGray
                    )
                    Text(
                        text = "${profile.displayName} · ${profile.specSummary}",
                        fontSize = 11.sp,
                        color = NuxColors.GrayNeutral,
                        lineHeight = 15.sp
                    )
                }
            }

            // Saklar utama: dengan/tanpa profil, untuk membandingkan FPS
            SettingSwitchRow(
                title = "Profil performa HP ini",
                desc = "Batasi heap, atur GC, kunci layar 60 Hz. Matikan untuk membandingkan FPS.",
                checked = settings.deviceProfileEnabled,
                onCheckedChange = { on -> change { it.copy(deviceProfileEnabled = on) } }
            )

            if (settings.deviceProfileEnabled) {
                SettingDivider()
                // Pilihan mode GC
                SettingChoiceRow(
                    title = "Mode GC (pembersih memori Java)",
                    desc = when (gcMode) {
                        DeviceProfiles.GC_SERIAL ->
                            "Serial: paling ringan untuk prosesor, jeda GC sedikit lebih panjang."
                        DeviceProfiles.GC_DEFAULT ->
                            "Bawaan: tanpa flag GC tambahan dari profil (hanya batas heap dan layar)."
                        else ->
                            "Seimbang: G1 dengan jeda pendek dan memori terbatas. Coba ini dulu."
                    },
                    options = listOf(
                        DeviceProfiles.GC_BALANCED to "Seimbang",
                        DeviceProfiles.GC_SERIAL to "Serial",
                        DeviceProfiles.GC_DEFAULT to "Bawaan"
                    ),
                    selected = gcMode,
                    onSelect = { modeId -> change { it.copy(deviceProfileGcMode = modeId) } }
                )

                SettingDivider()
                // Kunci refresh rate layar
                SettingSwitchRow(
                    title = "Kunci layar ${profile.refreshRateHz.toInt()} Hz",
                    desc = "Layar 90 Hz dikunci 60 Hz saat main supaya gerak lebih rata. HUD FPS menampilkan Hz yang aktif.",
                    checked = settings.deviceProfileLockRefresh,
                    onCheckedChange = { on -> change { it.copy(deviceProfileLockRefresh = on) } }
                )

                // Batas heap di HP RAM kecil
                if (capApplies) {
                    val cap = profile.smallRamHeapCapMb
                    Text(
                        text = if (settings.ramMb > cap) {
                            "Heap game: pilihanmu ${settings.ramMb} MB, dipakai $cap MB. RAM HP ini ${totalRamMb} MB; " +
                                "heap lebih besar membuat HP menukar memori dan FPS turun."
                        } else {
                            "Heap game ${settings.ramMb} MB (batas profil $cap MB)."
                        },
                        fontSize = 11.sp,
                        color = NuxColors.GrayNeutral,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Text(
                text = "Berlaku saat game dijalankan berikutnya. Dampaknya terlihat di HUD FPS (sisa RAM · PANAS · Hz).",
                fontSize = 11.sp,
                color = NuxColors.GrayNeutral,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
            )
        }
    }
}
