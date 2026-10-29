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
import com.israadev.nuxlauncher.core.device.DeviceProfiles
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.ui.theme.NuxColors

/**
 * Kartu "Optimasi HP RAM kecil" di Pengaturan > Memori: pilihan mode GC untuk HP yang RAM-nya
 * kecil. Tidak ada lagi kartu khusus satu model HP; semua HP mendapat kartu yang sama. Di HP RAM
 * besar fungsi ini tidak menggambar apa pun, jadi Pengaturan tampil seperti biasa.
 */
@Composable
fun DeviceProfileCard() {
    val context = LocalContext.current
    val settings by SettingsManager.settings.collectAsState()
    val totalRamMb = remember { SettingsManager.getTotalDeviceMemoryMb(context) }
    if (!DeviceProfiles.isSmallRamPhone(totalRamMb)) return
    val gcMode = DeviceProfiles.normalizeGeneralGcMode(settings.generalGcMode)

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
                        text = "Optimasi HP RAM kecil",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = NuxColors.DarkGray
                    )
                    Text(
                        text = "RAM HP ini ${totalRamMb} MB, jadi cara kerja pembersih memori Java bisa disetel.",
                        fontSize = 11.sp,
                        color = NuxColors.GrayNeutral,
                        lineHeight = 15.sp
                    )
                }
            }
            SettingChoiceRow(
                title = "Mode GC (pembersih memori Java)",
                desc = when (gcMode) {
                    DeviceProfiles.GC_SERIAL ->
                        "Serial: paling ringan untuk prosesor, jeda GC sedikit lebih panjang. Coba bila game sering tersendat."
                    DeviceProfiles.GC_DEFAULT ->
                        "Bawaan: tanpa flag GC tambahan dari launcher."
                    else ->
                        "Otomatis: G1 dengan memori terbatas, cocok untuk kebanyakan HP. Coba ini dulu."
                },
                options = listOf(
                    DeviceProfiles.GC_AUTO to "Otomatis",
                    DeviceProfiles.GC_SERIAL to "Serial",
                    DeviceProfiles.GC_DEFAULT to "Bawaan"
                ),
                selected = gcMode,
                onSelect = { modeId ->
                    SettingsManager.updateSettings(context, SettingsManager.settings.value.copy(generalGcMode = modeId))
                }
            )
            Text(
                text = "Berlaku saat game dijalankan berikutnya. Untuk membandingkan, lihat baris [Kinerja] di log game.",
                fontSize = 11.sp,
                color = NuxColors.GrayNeutral,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
            )
        }
    }
}
