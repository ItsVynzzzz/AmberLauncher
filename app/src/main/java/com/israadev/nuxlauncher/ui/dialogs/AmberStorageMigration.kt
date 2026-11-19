package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.storage.StorageMigration
import com.israadev.nuxlauncher.core.storage.StorageMigrationRunner
import com.israadev.nuxlauncher.core.storage.StorageMigrationRunner.FailKind
import com.israadev.nuxlauncher.core.storage.StorageMigrationRunner.UiState
import com.israadev.nuxlauncher.ui.components.AmberAlert
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.components.AmberInfoBox
import com.israadev.nuxlauncher.ui.components.AmberWaitBar
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.Amber

/**
 * Semua dialog pemindahan data ke Android/data, mengikuti [StorageMigrationRunner.state]: penjelasan
 * untung-rugi (tombol Tetap dan Pindahkan), progres (bisa dibatalkan), hasil, dan pesan gagal. Dipasang sekali di
 * MainActivity; dibuka otomatis untuk pengguna lama dan dari Pengaturan > Penyimpanan data.
 */
@Composable
fun AmberStorageMigration() {
    val state by StorageMigrationRunner.state.collectAsState()
    when (val s = state) {
        is UiState.Offer -> OfferDialog(s)
        is UiState.Running -> RunningDialog(s)
        is UiState.Done -> DoneDialog(s)
        is UiState.Cancelled -> CancelledDialog()
        is UiState.Failed -> FailedDialog(s)
        is UiState.Checking -> Unit
        is UiState.Idle -> Unit
    }
}

@Composable
private fun OfferDialog(s: UiState.Offer) {
    val context = LocalContext.current
    // Isinya panjang: bisa digulir, dan tingginya dibatasi supaya tombol tetap terlihat di layar landscape
    val maxBody = maxOf(140f, LocalConfiguration.current.screenHeightDp * 0.5f).dp

    AmberAlert(
        onDismissRequest = { StorageMigrationRunner.decline(context) },
        title = "Pindahkan data game",
        icon = "download",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Tetap",
                onClick = { StorageMigrationRunner.decline(context) },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
            AmberButton(
                text = "Pindahkan",
                onClick = { StorageMigrationRunner.start(context) },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Primary,
                enabled = s.sizeBytes != null
            )
        }
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = maxBody)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Dunia, mod, instance, dan file game akan dipindahkan dari folder lama ke folder milik " +
                    "AmberLauncher sendiri di Android/data. Semuanya dipindahkan utuh, tidak perlu mengunduh ulang.",
                color = Amber.Text,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            AmberInfoBox(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "Keuntungan",
                    color = Amber.Yellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                BulletLine("Android 11 ke atas: membaca dan menulis file biasanya lebih cepat, karena Android/data tidak melewati lapisan FUSE seperti penyimpanan utama.")
                BulletLine("Tidak perlu izin akses semua file.")
                BulletLine("Lebih aman: di Android 11 ke atas, aplikasi lain tidak bisa membaca atau mengubah datamu.")
                BulletLine("Folder rapi dengan nama AmberLauncher, tidak lagi berserakan di penyimpanan utama.")
                BulletLine("Sesuai aturan penyimpanan Android terbaru, tidak ikut bermasalah bila Android memperketat akses.")
            }

            AmberInfoBox(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    text = "Kekurangan",
                    color = Amber.DangerSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                BulletLine("Data ikut terhapus bila AmberLauncher dihapus atau penyimpanannya dikosongkan. Cadangkan dunia yang penting dulu.")
                BulletLine("Folder sulit dibuka lewat pengelola file: Android 11 ke atas membatasi Android/data, dan di Android 13 ke atas hampir tidak bisa.")
                BulletLine("Bila Android tidak bisa memindahkan langsung, data disalin dulu sehingga butuh ruang kosong sebesar data, dan bisa memakan beberapa menit.")
            }

            Text(
                text = "Yang terasa biasanya waktu memuat dunia, mod, dan aset, bukan FPS saat bermain. Besarnya beda di tiap HP.",
                color = Amber.TextDim,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 10.dp)
            )

            val size = s.sizeBytes
            Text(
                text = if (size == null) {
                    "Menghitung ukuran data..."
                } else {
                    "Ukuran data: ${StorageMigration.formatSize(size)}. Ruang kosong: ${StorageMigration.formatSize(s.freeBytes ?: 0L)}."
                },
                color = Amber.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "Tujuan: ${s.target.absolutePath}",
                color = Amber.TextFaint,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = "\u2022",
            color = Amber.TextDim,
            fontSize = 13.sp,
            modifier = Modifier.width(14.dp)
        )
        Text(
            text = text,
            color = Amber.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RunningDialog(s: UiState.Running) {
    // Layar tetap menyala selama memindahkan, supaya HP tidak tidur di tengah jalan
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val cancelable = s.phase == StorageMigration.Phase.PREPARING ||
        s.phase == StorageMigration.Phase.COPYING ||
        s.phase == StorageMigration.Phase.VERIFYING
    val phaseText = when (s.phase) {
        StorageMigration.Phase.PREPARING -> "Menyiapkan..."
        StorageMigration.Phase.COPYING -> "Menyalin data..."
        StorageMigration.Phase.VERIFYING -> "Memeriksa hasil salinan..."
        StorageMigration.Phase.FINISHING -> "Menyelesaikan..."
        StorageMigration.Phase.CLEANING -> "Membersihkan data lama..."
    }

    AmberAlert(
        // Tidak bisa ditutup dengan ketukan di luar atau tombol kembali; hanya lewat Batal
        onDismissRequest = { },
        title = "Memindahkan data",
        icon = "download",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Batal",
                onClick = { StorageMigrationRunner.cancel() },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Danger,
                enabled = cancelable
            )
        }
    ) {
        Text(
            text = phaseText,
            color = Amber.Text,
            fontSize = 14.sp
        )
        if (s.totalBytes > 0L && s.phase != StorageMigration.Phase.PREPARING) {
            val fraction = (s.doneBytes.toFloat() / s.totalBytes.toFloat()).coerceIn(0f, 1f)
            val percent = (fraction * 100f).toInt()
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Amber.Yellow,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
            Text(
                text = "$percent% \u00b7 ${StorageMigration.formatSize(s.doneBytes)} dari ${StorageMigration.formatSize(s.totalBytes)}",
                color = Amber.TextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            AmberWaitBar(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Amber.Yellow,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
        }
        Text(
            text = "Jangan tutup AmberLauncher sampai selesai. Kalau terputus, data lama tetap utuh.",
            color = Amber.TextFaint,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun DoneDialog(s: UiState.Done) {
    AmberAlert(
        onDismissRequest = { StorageMigrationRunner.dismiss() },
        title = "Pemindahan selesai",
        icon = "download",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Selesai",
                onClick = { StorageMigrationRunner.dismiss() },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Primary
            )
        }
    ) {
        Text(
            text = "Data sekarang disimpan di folder AmberLauncher sendiri. Instance, dunia, dan modmu langsung bisa dipakai.",
            color = Amber.Text,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        AmberInfoBox(modifier = Modifier.padding(top = 12.dp)) {
            Text(
                text = s.path,
                color = Amber.TextDim,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        if (s.leftover) {
            Text(
                text = "Sebagian file lama belum bisa dihapus dan akan dibersihkan otomatis nanti.",
                color = Amber.TextDim,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun CancelledDialog() {
    AmberAlert(
        onDismissRequest = { StorageMigrationRunner.dismiss() },
        title = "Pemindahan dibatalkan",
        icon = "download",
        accent = Amber.Yellow,
        actions = {
            AmberButton(
                text = "Tutup",
                onClick = { StorageMigrationRunner.dismiss() },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Primary
            )
        }
    ) {
        Text(
            text = "Data lama tetap utuh. Kamu bisa memindahkannya lagi kapan saja dari Pengaturan, bagian Penyimpanan data.",
            color = Amber.Text,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
    }
}

@Composable
private fun FailedDialog(s: UiState.Failed) {
    val message = when (s.kind) {
        FailKind.GAME_RUNNING -> "Game masih berjalan. Tutup game sepenuhnya, lalu coba lagi."
        FailKind.INSTALL_RUNNING -> "Unduhan atau pemasangan instance masih berjalan. Tunggu sampai selesai, lalu coba lagi."
        FailKind.NO_SOURCE -> "Data lama tidak ditemukan atau tidak bisa dibaca. Pastikan izin akses semua file untuk AmberLauncher masih menyala."
        FailKind.TARGET_NOT_EMPTY -> "Folder tujuan sudah berisi data lain, jadi tidak ada yang diubah."
        FailKind.NOT_ENOUGH_SPACE ->
            "Ruang kosong tidak cukup. Dibutuhkan sekitar ${StorageMigration.formatSize(s.neededBytes)}, " +
                "tersedia ${StorageMigration.formatSize(s.availableBytes)}."
        FailKind.ERROR -> "Terjadi kesalahan saat memindahkan data."
    }
    AmberAlert(
        onDismissRequest = { StorageMigrationRunner.dismiss() },
        title = "Pemindahan gagal",
        icon = "warning",
        accent = Amber.Danger,
        actions = {
            AmberButton(
                text = "Tutup",
                onClick = { StorageMigrationRunner.dismiss() },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost
            )
        }
    ) {
        Text(
            text = message,
            color = Amber.Text,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        Text(
            text = "Data lama tidak diubah.",
            color = Amber.TextDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
