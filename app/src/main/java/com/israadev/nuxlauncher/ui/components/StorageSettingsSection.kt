package com.israadev.nuxlauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.israadev.nuxlauncher.core.storage.DataLocation
import com.israadev.nuxlauncher.core.storage.StorageMigrationRunner

/**
 * Isi grup "Penyimpanan data" di Pengaturan: lokasi folder data sekarang, dan bagi pengguna lama
 * (data masih di folder nuxlauncher) tombol untuk memindahkannya ke Android/data. Tombolnya membuka
 * penjelasan untung-rugi yang sama dengan tawaran otomatis (lihat AmberStorageMigration).
 */
@Composable
fun StorageSettingsSection() {
    val context = LocalContext.current
    // Naik setelah pemindahan berhasil, supaya lokasi yang tampil ikut berubah
    val epoch by StorageMigrationRunner.epoch.collectAsState()
    val legacy = remember(epoch) { DataLocation.isLegacy(context) }
    val path = remember(epoch) { DataLocation.root(context).absolutePath }

    SettingRowText(
        title = "Lokasi data",
        desc = "Dunia, mod, dan file game disimpan di:\n$path"
    )
    SettingDivider()
    if (legacy) {
        SettingActionRow(
            title = "Pindahkan ke Android/data",
            desc = "Pindahkan data ke folder AmberLauncher sendiri tanpa izin akses semua file. " +
                "Untung-ruginya dijelaskan dulu sebelum dimulai, dan bisa dibatalkan.",
            actionLabel = "Pindahkan",
            onClick = { StorageMigrationRunner.offer(context) }
        )
    } else {
        SettingRowText(
            title = "Penyimpanan aplikasi",
            desc = "Data disimpan di folder AmberLauncher sendiri (Android/data). " +
                "Data ikut terhapus bila aplikasi dihapus, jadi cadangkan dunia yang penting."
        )
    }
}
