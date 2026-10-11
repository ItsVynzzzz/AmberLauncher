package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.device.DeviceProfile
import com.israadev.nuxlauncher.core.device.DeviceProfiles
import com.israadev.nuxlauncher.core.models.LauncherSettings
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NuxColors.SurfaceInput, RoundedCornerShape(6.dp))
            .border(NuxSizes.BorderWidth, NuxColors.CardBorder, RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Memory,
                contentDescription = null,
                tint = NuxColors.SageGreen,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "PROFIL PERANGKAT",
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                color = NuxColors.DarkGray
            )
        }
        Text(
            text = "${profile.displayName} · ${profile.specSummary}",
            fontSize = 9.5.sp,
            color = NuxColors.GrayNeutral,
            lineHeight = 13.sp
        )

        // Saklar utama: dengan/tanpa profil, untuk membandingkan FPS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Profil performa HP ini",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuxColors.DarkGray
                )
                Text(
                    text = "Batasi heap, atur GC, kunci layar 60 Hz. Matikan untuk membandingkan FPS.",
                    fontSize = 9.5.sp,
                    color = NuxColors.GrayNeutral
                )
            }
            Switch(
                checked = settings.deviceProfileEnabled,
                onCheckedChange = { on -> change { it.copy(deviceProfileEnabled = on) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NuxColors.Ink,
                    checkedTrackColor = NuxColors.ForestGreen,
                    uncheckedTrackColor = NuxColors.SurfaceElevated
                )
            )
        }

        if (settings.deviceProfileEnabled) {
            // Pilihan mode GC
            Text(
                text = "Mode GC (pembersih memori Java)",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = NuxColors.GrayNeutral
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    DeviceProfiles.GC_BALANCED to "Seimbang",
                    DeviceProfiles.GC_SERIAL to "Serial",
                    DeviceProfiles.GC_DEFAULT to "Bawaan"
                ).forEach { (modeId, label) ->
                    val isSelected = gcMode == modeId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) NuxColors.ForestGreen else NuxColors.SurfaceElevated,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) NuxColors.MintGreen else NuxColors.CardBorder,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { change { it.copy(deviceProfileGcMode = modeId) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) NuxColors.Ink else NuxColors.GrayNeutral
                        )
                    }
                }
            }
            Text(
                text = when (gcMode) {
                    DeviceProfiles.GC_SERIAL ->
                        "Serial: paling ringan untuk prosesor, jeda GC sedikit lebih panjang."
                    DeviceProfiles.GC_DEFAULT ->
                        "Bawaan: tanpa flag GC tambahan dari profil (hanya batas heap dan layar)."
                    else ->
                        "Seimbang: G1 dengan jeda pendek dan memori terbatas. Coba ini dulu."
                },
                fontSize = 9.5.sp,
                color = NuxColors.GrayNeutral,
                lineHeight = 13.sp
            )

            // Kunci refresh rate layar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kunci layar ${profile.refreshRateHz.toInt()} Hz",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NuxColors.DarkGray
                    )
                    Text(
                        text = "Layar 90 Hz dikunci 60 Hz saat main supaya gerak lebih rata. HUD FPS menampilkan Hz yang aktif.",
                        fontSize = 9.5.sp,
                        color = NuxColors.GrayNeutral
                    )
                }
                Switch(
                    checked = settings.deviceProfileLockRefresh,
                    onCheckedChange = { on -> change { it.copy(deviceProfileLockRefresh = on) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NuxColors.Ink,
                        checkedTrackColor = NuxColors.ForestGreen,
                        uncheckedTrackColor = NuxColors.SurfaceElevated
                    )
                )
            }

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
                    fontSize = 9.5.sp,
                    color = NuxColors.GrayNeutral,
                    lineHeight = 13.sp
                )
            }
        }

        Text(
            text = "Berlaku saat game dijalankan berikutnya. Dampaknya terlihat di HUD FPS (sisa RAM · PANAS · Hz).",
            fontSize = 9.sp,
            color = NuxColors.GrayNeutral,
            lineHeight = 12.sp
        )
    }
}
