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

AmberLauncher dibuat supaya tetap lancar di HP Android kelas menengah ke bawah, dan tentu juga jalan di HP yang lebih kuat: tampilannya ringan, pengaturannya sedikit tapi jelas, dan setelan awalnya (RAM game dan jarak pandang) disesuaikan dengan RAM perangkat. Tema visualnya gelap transparan dengan aksen kuning amber. Untuk saat ini launcher ini mendukung **Fabric**, **Forge** (Minecraft 1.13 ke atas), dan **NeoForge** (Minecraft 1.20.2 ke atas).

Proyek ini adalah turunan (fork) dari **NUX Launcher**, yang sendiri berbasis **Zalith Launcher 2** dan **PojavLauncher**. Lihat bagian [Lisensi dan Kredit](#lisensi-dan-kredit).

## Fitur

- **Beranda kaca gelap** yang minimalis, dengan latar yang bisa diganti.
- **Banyak instance terpisah**: tiap profil punya versi, mod, dan pengaturan sendiri.
- **Mod, resource pack, shader, dan datapack dari Modrinth**: cari dan pasang langsung dari dalam aplikasi, plus pemasangan file manual.
- **Fabric, Forge, dan NeoForge**: pilih Vanilla, Fabric, Forge, atau NeoForge saat membuat instance. Pencarian mod dan modpack otomatis disaring sesuai loader instance, dan modpack untuk loader lain ditolak dengan pesan yang jelas.
- **Forge dan NeoForge hemat**: keduanya dipasang sekali per versi lewat installer resmi di proses terpisah yang langsung dimatikan setelah selesai. Saat bermain, mereka hanya menambah beban dari Forge/NeoForge itu sendiri (kelas, mod, dan waktu muat); launcher tidak menjalankan layanan latar apa pun untuknya.
- **Setelan awal sesuai HP**: RAM game dan jarak pandang awal menyesuaikan RAM perangkat. Model yang sudah diuji (saat ini Oppo A3x 4G) mendapat profil performa khusus; di HP lain launcher berjalan dengan setelan biasa.
- **Pilihan renderer dan runtime Java** yang sudah dibawa aplikasi (Java 8, 17, 21, dan 25).
- **Editor kontrol sentuh**: atur posisi, ukuran, dan tombol layar; pemilih tombol berdasarkan kategori; sakelar serang-sentuh.
- **Hotbar sentuh (gaya Bedrock)**: ketuk hotbar game di bagian bawah layar untuk memilih slot. Posisi slot dihitung dari ukuran jendela dan GUI scale Minecraft (tanpa menggambar apa pun, tanpa beban saat bermain), getar singkat saat memilih, dan bisa dimatikan di Pengaturan > Kontrol.
- **Mod TouchController**: bila modnya ada di folder mods, launcher meneruskan sentuhan layar ke mod itu dan menyembunyikan tombol bawaan (hanya gerigi menu yang tersisa). Tanpa modnya tidak ada tambahan beban sama sekali. Tombol bawaan baru disembunyikan setelah modnya benar-benar tersambung; selama belum (atau bila tidak pernah), tombol bawaan tetap bisa dipakai. Eksperimental dan bawaannya mati: nyalakan di Pengaturan > Kontrol bila memakai mod ini. Belum diuji di perangkat; bila klien proxy gagal dimulai, tombol bawaan dipakai dan alasannya tertulis di log dengan awalan `[TouchController]`.
- **Lari otomatis joystick bisa dimatikan**: di Atur tombol, pilih joystick lalu matikan "Lari otomatis" bila lebih suka lari lewat tombol CTRL sendiri. Shift/Ctrl/Alt dari tombol layar kini ikut dikirim sebagai modifier ke Minecraft (dipakai untuk Shift + klik di inventory).
- **Akun**: Microsoft, Ely.by, dan offline.
- **Laporan crash** dengan unggah log ke mclo.gs bila diinginkan.
- **Rekam layar bawaan**: tombol Rekam di menu dalam game, dengan dua mode: *Game saja* (tombol layar disembunyikan dari video, tetap bisa disentuh) atau *Dengan tombol*. Memakai encoder video perangkat keras (tanpa kerja CPU per frame), bawaannya 720p/30 fps, otomatis jeda saat menu terbuka atau saat keluar dari game, dan berhenti bila HP terlalu panas. Hasilnya ada di Galeri (Movies/AmberLauncher). Suara game belum ikut terekam (mikrofon opsional).
- **Pembaruan dari dalam aplikasi**: tiap launcher dibuka, versi baru di GitHub Releases dicek (murah, memakai ETag) dan muncul pop-up untuk mengunduh serta memasangnya; APK 32-bit/64-bit dipilih otomatis. Ada tombol **Periksa pembaruan** di Pengaturan > Pembaruan.

Belum ada: Forge untuk Minecraft 1.12.2 ke bawah (butuh LWJGL 2), NeoForge untuk 1.20.1 ke bawah, dan Quilt, serta unduhan dari CurseForge (membutuhkan kunci API sendiri).

> Semua fitur di atas dibangun lewat GitHub Actions dan belum tentu sudah diuji di semua perangkat. Bila ada yang rusak, buka *issue* dengan menyertakan log.

## Memasang

APK hasil build ada di halaman **Releases** repo ini (tiap build lolos membuat rilis *pre-release* otomatis) dan di tab **Actions** sebagai artifact selama 14 hari. Unduh APK-nya di HP, izinkan pemasangan dari sumber ini, lalu pasang.

**Kebutuhan:** Android 8.0 (API 26) atau lebih baru. RAM 3 GB ke atas disarankan; 4 GB atau lebih terasa lebih nyaman. Siapkan ruang kosong beberapa GB untuk aplikasi, Java bawaan, dan data game.

**Pilih APK yang cocok dengan HP-mu.** Tiap rilis punya dua berkas:

- `AmberLauncher-<versi>.apk` untuk CPU 64-bit (arm64-v8a). Hampir semua HP keluaran beberapa tahun terakhir memakai ini, jadi pilih yang ini bila ragu.
- `AmberLauncher-<versi>-32bit.apk` hanya untuk HP lama yang CPU-nya 32-bit (armeabi-v7a). Versi ini lebih terbatas: RAM untuk game dibatasi sekitar 1,2 GB dan sebagian renderer tidak tersedia, jadi FPS bisa lebih rendah daripada versi 64-bit.

Setelah terpasang, pembaruan berikutnya bisa langsung dari dalam launcher (Pengaturan > Pembaruan). Android tetap meminta satu kali konfirmasi pemasangan, dan game harus ditutup dulu.

### Cara mulai

1. Buka AmberLauncher. Di Android 11 ke atas akan muncul penjelasan izin penyimpanan; izinkan agar data tersimpan di folder `nuxlauncher` (boleh dilewati, datanya lalu disimpan di folder privat aplikasi).
2. Buka **Akun** dan tambahkan akun: Microsoft untuk bermain online, atau offline untuk bermain sendiri.
3. Buat **instance** baru: pilih versi Minecraft, lalu Vanilla, Fabric, Forge, atau NeoForge (pilih salah satu loader mod bila ingin memakai mod).
4. Di **Home**, tekan **UNDUH** dan tunggu sampai selesai (butuh internet; pertama kali bisa beberapa ratus MB), lalu tekan **PLAY**.
5. Mod, resource pack, dan shader dipasang lewat menu **Mod**.

### Sudah pernah memasang build lama?

ID aplikasi diganti menjadi `com.itsvynzzzz.amberlauncher`, jadi versi ini terpasang sebagai aplikasi terpisah dan tidak menimpa yang lama. Data game di folder `nuxlauncher` tetap terbaca, tetapi akun perlu ditambahkan lagi. Aplikasi lama boleh dihapus setelah semuanya aman.

### Bila ada masalah

- **Game tertutup sendiri atau crash**: jangan beri RAM game lebih dari sekitar setengah RAM HP, tutup aplikasi lain, atau turunkan rasio resolusi di **Pengaturan**. Laporan crash bisa dilihat dari dalam aplikasi.
- **FPS rendah**: kecilkan jarak pandang di dalam game, turunkan rasio resolusi, atau coba renderer lain di **Pengaturan**.
- **Mod tidak jalan**: pastikan loader instance (Fabric, Forge, atau NeoForge) dan versi Minecraft-nya sama dengan mod.

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
