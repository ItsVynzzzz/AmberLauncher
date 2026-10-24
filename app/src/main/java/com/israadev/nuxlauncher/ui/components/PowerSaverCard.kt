package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.settings.PowerSaver
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.NuxColors

/**
 * Kartu "Mode Hemat" di Pengaturan > Grafis: satu saklar untuk HP yang cepat panas atau baterainya
 * boros. Saklar ini hanya mengubah tiga setelan yang sudah ada (lihat [PowerSaver]), jadi game
 * tidak menjalankan kode tambahan.
 */
@Composable
fun PowerSaverCard() {
    val context = LocalContext.current
    val settings by SettingsManager.settings.collectAsState()

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
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = NuxColors.Yellow,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Mode Hemat",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = NuxColors.DarkGray
                    )
                    Text(
                        text = "Untuk HP yang cepat panas atau baterainya cepat habis.",
                        fontSize = 11.sp,
                        color = NuxColors.GrayNeutral,
                        lineHeight = 15.sp
                    )
                }
            }
            SettingSwitchRow(
                title = "Nyalakan Mode Hemat",
                desc = "Menyalakan mode performa stabil sistem dan V-Sync Zink, serta membatasi skala resolusi maksimal ${PowerSaver.MAX_RESOLUTION}%.",
                checked = settings.powerSaverMode,
                onCheckedChange = { on ->
                    SettingsManager.updateSettings(context, PowerSaver.set(SettingsManager.settings.value, on))
                }
            )
            Text(
                text = "Berlaku saat game dijalankan berikutnya. Dimatikan kembali, setelan sebelumnya dipulihkan.",
                fontSize = 11.sp,
                color = NuxColors.GrayNeutral,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
            )
        }
    }
}
