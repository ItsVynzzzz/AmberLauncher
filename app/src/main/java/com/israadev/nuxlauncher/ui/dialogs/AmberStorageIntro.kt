package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.components.AmberAlert
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.theme.Amber

/**
 * Penjelasan singkat sebelum Android membuka halaman izin "akses semua file".
 * Hanya tampil satu kali di Android 11 ke atas bila izinnya belum diberikan, supaya pengguna baru
 * tidak tiba-tiba dilempar ke pengaturan sistem tanpa tahu alasannya.
 */
@Composable
fun AmberStorageIntro(
    initiallyVisible: Boolean,
    onOpenSettings: () -> Unit,
    onFinished: () -> Unit
) {
    var visible by remember { mutableStateOf(initiallyVisible) }

    if (visible) {
        AmberAlert(
            onDismissRequest = {
                visible = false
                onFinished()
            },
            title = "Izin penyimpanan",
            icon = "gear",
            accent = Amber.Yellow,
            actions = {
                AmberButton(
                    text = "Nanti",
                    onClick = {
                        visible = false
                        onFinished()
                    },
                    modifier = Modifier.weight(1f),
                    style = AmberButtonStyle.Ghost
                )
                AmberButton(
                    text = "Buka pengaturan",
                    onClick = {
                        visible = false
                        onOpenSettings()
                    },
                    modifier = Modifier.weight(1f),
                    style = AmberButtonStyle.Primary
                )
            }
        ) {
            Text(
                text = "AmberLauncher menyimpan dunia, mod, dan pengaturan di folder nuxlauncher pada penyimpanan HP, " +
                    "supaya mudah dicadangkan dan dipindahkan.",
                color = Amber.Text,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
            Text(
                text = "Android akan membuka halaman pengaturan. Nyalakan izin akses ke semua file untuk AmberLauncher, " +
                    "lalu kembali ke sini.",
                color = Amber.TextDim,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = "Kalau memilih Nanti, game tetap bisa dipakai. Datanya disimpan di folder privat aplikasi " +
                    "yang tidak mudah dibuka lewat pengelola file.",
                color = Amber.TextDim,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
