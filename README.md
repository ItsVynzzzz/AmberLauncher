<div align="center">

# AmberLauncher

**Launcher Minecraft Java Edition untuk Android yang ringan, minimalis, dan mengutamakan performa.**

[![Lisensi: GPL v3](https://img.shields.io/badge/Lisensi-GPLv3-blue.svg)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)

</div>

> **Bukan produk resmi Minecraft.** AmberLauncher adalah proyek independen. Tidak berafiliasi dengan, didukung, atau disetujui oleh Mojang Studios, Microsoft, maupun tim pengembang proyek hulu yang disebut di bawah.

---

## Tentang

AmberLauncher dibuat supaya tetap lancar di HP Android kelas menengah ke bawah, dan tentu juga jalan di HP yang lebih kuat: tampilannya ringan, pengaturannya sedikit tapi jelas, dan setelan awalnya (RAM game dan jarak pandang) disesuaikan dengan RAM perangkat. Tema visualnya gelap transparan dengan aksen kuning amber. Untuk saat ini launcher ini berfokus pada **Fabric**.

Proyek ini adalah turunan (fork) dari **NUX Launcher**, yang sendiri berbasis **Zalith Launcher 2** dan **PojavLauncher**. Lihat bagian [Lisensi dan Kredit](#lisensi-dan-kredit).

## Fitur

- **Beranda kaca gelap** yang minimalis, dengan latar yang bisa diganti.
- **Banyak instance terpisah**: tiap profil punya versi, mod, dan pengaturan sendiri.
- **Mod, resource pack, shader, dan datapack dari Modrinth**: cari dan pasang langsung dari dalam aplikasi, plus pemasangan file manual.
- **Fokus Fabric**: pilih Vanilla atau Fabric saat membuat instance. Pencarian mod dan modpack otomatis disaring untuk Fabric, dan modpack untuk loader lain ditolak dengan pesan yang jelas.
- **Setelan awal sesuai HP**: RAM game dan jarak pandang awal menyesuaikan RAM perangkat. Model yang sudah diuji (saat ini Oppo A3x 4G) mendapat profil performa khusus; di HP lain launcher berjalan dengan setelan biasa.
- **Pilihan renderer dan runtime Java** yang sudah dibawa aplikasi (Java 8, 17, 21, dan 25).
- **Editor kontrol sentuh**: atur posisi, ukuran, dan tombol layar; pemilih tombol berdasarkan kategori; sakelar serang-sentuh.
- **Akun**: Microsoft, Ely.by, dan offline.
- **Laporan crash** dengan unggah log ke mclo.gs bila diinginkan.

Belum ada: Forge, NeoForge, dan Quilt (untuk sekarang fokusnya Fabric, ke depannya bisa berubah), serta unduhan dari CurseForge (membutuhkan kunci API sendiri).

> Semua fitur di atas dibangun lewat GitHub Actions dan belum tentu sudah diuji di semua perangkat. Bila ada yang rusak, buka *issue* dengan menyertakan log.

## Memasang

APK hasil build ada di halaman **Releases** repo ini (tiap build lolos membuat rilis *pre-release* otomatis) dan di tab **Actions** sebagai artifact selama 14 hari. Unduh APK-nya di HP, izinkan pemasangan dari sumber ini, lalu pasang.

**Kebutuhan:** Android 8.0 (API 26) atau lebih baru dan CPU 64-bit (arm64-v8a), yang dimiliki hampir semua HP keluaran beberapa tahun terakhir. RAM 3 GB ke atas disarankan; 4 GB atau lebih terasa lebih nyaman. Siapkan ruang kosong beberapa GB untuk aplikasi, Java bawaan, dan data game.

### Cara mulai

1. Buka AmberLauncher. Di Android 11 ke atas akan muncul penjelasan izin penyimpanan; izinkan agar data tersimpan di folder `nuxlauncher` (boleh dilewati, datanya lalu disimpan di folder privat aplikasi).
2. Buka **Akun** dan tambahkan akun: Microsoft untuk bermain online, atau offline untuk bermain sendiri.
3. Buat **instance** baru: pilih versi Minecraft, lalu Vanilla atau Fabric (pilih Fabric bila ingin memakai mod).
4. Di **Home**, tekan **UNDUH** dan tunggu sampai selesai (butuh internet; pertama kali bisa beberapa ratus MB), lalu tekan **PLAY**.
5. Mod, resource pack, dan shader dipasang lewat menu **Mod**.

### Sudah pernah memasang build lama?

ID aplikasi diganti menjadi `com.itsvynzzzz.amberlauncher`, jadi versi ini terpasang sebagai aplikasi terpisah dan tidak menimpa yang lama. Data game di folder `nuxlauncher` tetap terbaca, tetapi akun perlu ditambahkan lagi. Aplikasi lama boleh dihapus setelah semuanya aman.

### Bila ada masalah

- **Game tertutup sendiri atau crash**: jangan beri RAM game lebih dari sekitar setengah RAM HP, tutup aplikasi lain, atau turunkan rasio resolusi di **Pengaturan**. Laporan crash bisa dilihat dari dalam aplikasi.
- **FPS rendah**: kecilkan jarak pandang di dalam game, turunkan rasio resolusi, atau coba renderer lain di **Pengaturan**.
- **Mod tidak jalan**: pastikan instance memakai Fabric dan versi Minecraft-nya sama dengan versi mod.

### Lokasi data

Folder data aplikasi ada di `/storage/emulated/0/nuxlauncher` (nama folder dipertahankan agar data lama tetap terbaca). Bila izin penyimpanan tidak diberikan, datanya ada di folder privat aplikasi. Lokasi mengikuti izin: kalau izin diubah setelah instance dibuat, instance lama tetap berada di lokasi sebelumnya dan baru terlihat lagi setelah izinnya dikembalikan.

## Build sendiri

### Lewat GitHub Actions (tanpa komputer)

1. Fork atau pakai repo ini, aktifkan **Actions**.
2. Dorong ke cabang `main` atau jalankan workflow **Build APK** secara manual.
3. Ambil APK dari **Releases** atau artifact hasil build.

Fork tanpa kunci tanda tangan sendiri menghasilkan APK bertanda tangan kunci debug, cukup untuk dicoba tetapi jangan dibagikan sebagai rilis. Untuk membagikannya, buat kunci pribadi lalu isi Secrets `AMBER_KEYSTORE_BASE64` (isi keystore PKCS12, alias `amber`, dalam base64) dan `AMBER_KEYSTORE_PASSWORD`. Kunci itu harus dicadangkan di tempat aman: bila hilang, pengguna tidak bisa memperbarui aplikasi dan harus memasang ulang.

### Lewat Android Studio / Gradle

Prasyarat: JDK 17, Android SDK (compile SDK 37, min SDK 26), dan NDK bila ikut mengompilasi kode native.

```bash
git clone https://github.com/ItsVynzzzz/AmberLauncher.git
cd AmberLauncher
cp local.properties.example local.properties   # lalu isi sdk.dir
chmod +x gradlew
./gradlew assembleRelease
```

APK ada di `app/build/outputs/apk/release/`.

## Lisensi dan Kredit

AmberLauncher dirilis di bawah **GNU General Public License v3.0 (GPL-3.0)** (lihat berkas [LICENSE](LICENSE)).

**Hak cipta**
- Perubahan dan kode baru di AmberLauncher: Copyright © 2026 ItsVynzzzz.
- Kode asal dari proyek hulu tetap milik para pembuatnya masing-masing, dan pemberitahuan hak ciptanya tidak boleh dihapus.

Karena proyek hulunya berlisensi GPL-3.0, seluruh hasil turunannya juga harus tetap GPL-3.0 dan kode sumbernya terbuka. Siapa pun boleh memakai, mempelajari, mengubah, dan membagikannya selama syarat yang sama dipenuhi.

**Ketentuan tambahan dari Zalith Launcher 2 (GPL-3.0 pasal 7)** yang berlaku bagi turunannya:
- Nama "ZalithLauncher" atau "ZL" tidak boleh dipakai, itulah sebabnya proyek ini bernama AmberLauncher.
- Versi modifikasi harus menampilkan keterangan *Unofficial Modified Version* di layar utama; keterangan itu ada di Beranda dan di layar Tentang.
- Pemberitahuan hak cipta yang ditampilkan program tidak boleh dihapus (lihat layar Tentang).

**Proyek hulu dan pustaka yang dipakai**

| Proyek | Pembuat | Lisensi |
|---|---|---|
| [NUX Launcher](https://github.com/IsraaDeveloper/nuxlabs) (sumber fork) | IsraaDeveloper | GPL-3.0 |
| [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2) | MovTery dan kontributor | GPL-3.0 |
| [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) | artdeell, khanhtran, dan tim | LGPL-3.0 |
| [Fold Craft Launcher](https://github.com/FCL-Team/FoldCraftLauncher) | Tim FCL | GPL-3.0 |
| [MobileGlues](https://github.com/MobileGlues/MobileGlues) | Tim MobileGlues | LGPL-2.1 |
| [GL4ES](https://github.com/ptitSeb/gl4es) | ptitSeb | MIT |

Terima kasih juga kepada [Modrinth](https://modrinth.com) atas API publiknya dan [Ely.by](https://ely.by) atas sistem skin dan autentikasinya.

## Privasi dan keamanan

- Kata sandi akun Microsoft tidak pernah disimpan; login memakai jendela OAuth resmi Microsoft.
- Aset dan pustaka Minecraft diunduh langsung dari server resmi Mojang, lalu isinya dicocokkan dengan sidik jari (SHA-1) resmi.
- Semua sambungan internet memakai HTTPS. Server akun offline dan skin hanya bisa dijangkau dari HP itu sendiri.
- Log yang dibagikan atau diunggah dibersihkan dari token dan kata sandi lebih dulu.
- Kunci API, kunci tanda tangan, dan rahasia lain tidak boleh dimasukkan ke repo.
- Cara memastikan APK asli dan cara melapor celah keamanan ada di [SECURITY.md](SECURITY.md).

## Penafian

*Minecraft* adalah merek dagang Mojang AB / Microsoft Corporation. AmberLauncher tidak menyertakan atau membagikan salinan game; pemain wajib memiliki akun Minecraft Java Edition yang sah untuk bermain secara online.
