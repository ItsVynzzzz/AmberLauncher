package com.israadev.nuxlauncher.ui.dialogs

import com.israadev.nuxlauncher.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.components.AmberPage
import com.israadev.nuxlauncher.ui.components.AmberPanel
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.AmberUi

private class HelpTopic(val title: String, val steps: List<String>)

/**
 * Isi bantuan. Hanya menyebut tombol dan pengaturan yang memang ada di aplikasi (nama persis seperti di layar),
 * supaya pengguna baru tidak diarahkan ke menu yang tidak ada.
 */
private val HELP_TOPICS = listOf(
    HelpTopic(
        "Game langsung tertutup atau tidak mau mulai",
        listOf(
            "Buka Pengaturan > Tentang > Log game. Di sana tercatat sebab terakhir; bagikan isinya bila meminta bantuan.",
            "Bila pesannya menyebut file atau library hilang: buka Ubah instance (ikon pensil di Beranda) lalu tekan \"Verifikasi & perbaiki\".",
            "Turunkan \"Memori game (RAM)\" di Pengaturan > Game. RAM yang diminta terlalu besar untuk HP bisa membuat Android menutup game.",
            "Coba versi Java lain di Ubah instance (mis. Java 17 atau 21 untuk Minecraft baru, Java 8 untuk yang lama)."
        )
    ),
    HelpTopic(
        "FPS rendah atau game patah-patah",
        listOf(
            "Turunkan \"Skala resolusi layar\" di Pengaturan > Game: layar digambar lebih kasar tetapi jauh lebih ringan.",
            "Di dalam game, kecilkan Render Distance dan Simulation Distance, dan matikan efek grafis yang berat.",
            "Nyalakan \"Tampilkan FPS dan RAM\" di Pengaturan > Kontrol untuk melihat apakah masalahnya RAM yang hampir habis atau prosesor yang terlalu sibuk.",
            "Tutup aplikasi lain, dan beri HP waktu dingin bila terasa panas: HP yang panas menurunkan kecepatannya sendiri."
        )
    ),
    HelpTopic(
        "Launcher terasa berat",
        listOf(
            "Matikan \"Gerakkan GIF dan video\" dan \"Efek di beranda\" di Pengaturan > Tampilan; latar yang bergerak memakai tenaga HP.",
            "Pakai latar foto biasa, bukan video atau GIF besar."
        )
    ),
    HelpTopic(
        "Unduhan gagal atau terputus",
        listOf(
            "Tekan UNDUH lagi. Hanya file yang kurang yang diunduh ulang, jadi tidak mulai dari nol.",
            "Pastikan ruang kosong di HP cukup (sekitar 500 MB untuk satu versi); lihat di Pengaturan bawaan HP > Penyimpanan.",
            "Bila game terasa tidak lengkap sesudah selesai, tekan \"Verifikasi & perbaiki\" di Ubah instance."
        )
    ),
    HelpTopic(
        "Login Microsoft gagal atau diminta masuk lagi",
        listOf(
            "Sesi bisa kedaluwarsa. Buka layar Akun dan masuk lagi dengan akun Microsoft yang sama.",
            "Akunnya harus memiliki Minecraft Java Edition; akun tanpa itu tidak bisa dipakai bermain online.",
            "Ikuti kode dan alamat yang ditampilkan launcher; kodenya berlaku singkat, jadi selesaikan segera."
        )
    ),
    HelpTopic(
        "Apa itu akun offline?",
        listOf(
            "Akun offline hanya berupa nama pemain di HP ini, tanpa login. Cocok untuk dunia sendiri (satu pemain) atau LAN.",
            "Namanya 3-16 karakter: huruf, angka, atau garis bawah.",
            "Sebagian besar server online hanya menerima akun resmi, jadi akun offline biasanya ditolak di sana."
        )
    ),
    HelpTopic(
        "Di mana dunia dan mod disimpan? Cara mencadangkan",
        listOf(
            "Lihat lokasinya di Pengaturan > Tentang > Penyimpanan data (\"Lokasi data\").",
            "Setiap instance punya folder sendiri. Tekan \"Buka folder instance\" di Ubah instance untuk menuju ke sana; dunia ada di folder saves.",
            "Data di folder AmberLauncher sendiri (Android/data) ikut terhapus bila aplikasi dihapus, jadi salin dunia yang penting ke tempat lain lebih dulu.",
            "Bila datamu masih di folder nuxlauncher yang lama, tombol \"Pindahkan\" di Penyimpanan data memindahkannya ke Android/data tanpa izin akses semua file."
        )
    ),
    HelpTopic(
        "Cara memasang mod",
        listOf(
            "Buka Ubah instance > Mod & konten untuk mencari dan memasang mod, atau tambahkan file yang sudah kamu unduh.",
            "Berkas .jar atau .mrpack juga bisa dibuka langsung dengan AmberLauncher dari pengelola file.",
            "Pastikan mod cocok dengan versi Minecraft dan loader (Fabric atau Forge) instance itu."
        )
    )
)

/** Halaman Bantuan: pertanyaan yang paling sering muncul beserta langkahnya, dibuka dari Pengaturan > Tentang. */
@Composable
fun AmberHelpDialog(onDismiss: () -> Unit) {
    AmberPage(
        onDismiss = onDismiss,
        title = "Bantuan",
        icon = Icons.Outlined.Info,
        subtitle = "Pertanyaan yang sering muncul"
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HELP_TOPICS.forEachIndexed { index, topic ->
                HelpTopicCard(topic, startOpen = index == 0)
            }
        }
    }
}

@Composable
private fun HelpTopicCard(topic: HelpTopic, startOpen: Boolean) {
    var open by remember { mutableStateOf(startOpen) }
    AmberPanel(modifier = Modifier.clickable { open = !open }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = topic.title,
                color = AmberUi.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (open) "−" else "+",
                color = AmberUi.AccentSoft,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (open) {
            Spacer(modifier = Modifier.height(8.dp))
            topic.steps.forEach { step ->
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "•",
                        color = AmberUi.AccentSoft,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = step,
                        color = AmberUi.TextDim,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
