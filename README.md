<div align="center">

# AmberLauncher

**Launcher Minecraft Java Edition untuk Android: ringan, simpel, dan mengutamakan performa.**

[![Rilis terbaru](https://img.shields.io/github/v/release/ItsVynzzzz/AmberLauncher?include_prereleases&label=rilis)](https://github.com/ItsVynzzzz/AmberLauncher/releases)
[![Lisensi: GPL v3](https://img.shields.io/badge/Lisensi-GPLv3-blue.svg)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)

[**Unduh**](#unduh) · [Cara mulai](#cara-mulai) · [Fitur](#fitur) · [Bantuan](#bantuan) · [Kredit](#lisensi-dan-kredit) · [English](README.en.md)

</div>

> AmberLauncher adalah proyek independen dan tidak berafiliasi dengan Mojang Studios, Microsoft, maupun proyek pihak ketiga yang disebut di bawah.

## Tentang

AmberLauncher dibuat supaya Minecraft Java tetap lancar di HP Android kelas menengah ke bawah, dan tetap nyaman di HP yang lebih kuat.

- **Ringan**: tampilan sederhana, pengaturan sedikit tapi jelas.
- **Setelan awal pintar**: RAM game, skala resolusi, dan jarak pandang menyesuaikan RAM HP-mu.
- **Siap untuk mod**: Vanilla, Fabric, Forge, dan NeoForge, dengan mod dari Modrinth dan CurseForge.

Tampilannya gelap transparan dengan aksen kuning amber. Masih tahap beta, jadi masukanmu sangat berarti.

## Unduh

Ambil APK terbaru di halaman **[Releases](https://github.com/ItsVynzzzz/AmberLauncher/releases)**, lalu pasang di HP-mu.

**Kebutuhan:** Android 8.0 (API 26) atau lebih baru, RAM 3 GB ke atas (4 GB lebih nyaman), dan ruang kosong beberapa GB.

| Berkas | Untuk siapa |
|---|---|
| `AmberLauncher-<versi>-64bit-arm64-v8a-RECOMMENDED.apk` | **Pilihan utama.** Hampir semua HP keluaran beberapa tahun terakhir. Pilih ini bila ragu. |
| `AmberLauncher-<versi>-32bit-armeabi-v7a-OLD-PHONES-ONLY.apk` | Hanya untuk HP lama berCPU 32-bit. RAM game dibatasi sekitar 1,2 GB dan sebagian renderer tidak tersedia. Tidak selalu ada di setiap rilis. |

Pembaruan berikutnya bisa dipasang langsung dari dalam launcher (**Pengaturan > Pembaruan**).

## Cara mulai

1. **Buka AmberLauncher.** Di Android 11 ke atas, izinkan akses penyimpanan agar data tersimpan di folder `nuxlauncher` (boleh dilewati; data lalu disimpan di folder privat aplikasi).
2. **Tambahkan akun** di menu **Akun**: Microsoft untuk bermain online, atau offline untuk bermain sendiri.
3. **Buat instance** lewat **Buat Instance**: pilih versi Minecraft, lalu Vanilla, Fabric, Forge, atau NeoForge.
4. Di **Home**, tekan **UNDUH** dan tunggu selesai (butuh internet), lalu tekan **PLAY**.
5. Pasang mod, resource pack, dan shader lewat menu **Mod**.

## Fitur

**Game**
- **Banyak instance terpisah**, masing-masing dengan versi, mod, dan pengaturan sendiri.
- **Vanilla, Fabric, Forge (1.13+), dan NeoForge (1.20.2+)**. Pencarian mod dan modpack otomatis disesuaikan dengan loader instance.
- **Java bawaan**: Java 8, 17, 21, dan 25, tanpa perlu memasang apa pun.
- **Pilihan renderer**: Auto (rekomendasi), Krypton, MobileGlues, GL4ES, Kopper Zink, dan Freedreno (Snapdragon Turnip).

**Mod dan konten**
- **Modrinth dan CurseForge** langsung dari aplikasi: mod, modpack, resource pack, shader, dan datapack, lengkap dengan dependensinya. Pemasangan file manual juga bisa.

**Kontrol**
- **Editor kontrol sentuh**: atur posisi, ukuran, dan tombol layar sesukamu.
- **Hotbar sentuh** gaya Bedrock: ketuk hotbar untuk memilih slot, juga dengan jari lain saat kamera sedang digeser. Bisa dimatikan di **Pengaturan > Kontrol**.
- **Controller (gamepad)** didukung, termasuk mod seperti Controlify.
- **Lari otomatis joystick** bisa dimatikan, dan tombol Shift/Ctrl/Alt di layar ikut terbaca sebagai modifier (misalnya Shift + klik di inventory).

**Lainnya**
- **Akun** Microsoft, Ely.by, dan offline.
- **Rekam layar bawaan**: pilih *Game saja* atau *Dengan tombol*. Memakai encoder perangkat keras (bawaannya 720p/30 fps), hasilnya ada di Galeri (Movies/AmberLauncher). Suara game belum ikut terekam.
- **Laporan crash** dengan opsi unggah log ke mclo.gs.
- **Pembaruan dari dalam aplikasi**, dengan APK 32-bit/64-bit dipilih otomatis. Yang ditawarkan hanya rilis bernomor; build uji tiap patch dan perubahan dokumen tidak memunculkan pembaruan.
- **Antarmuka multibahasa**: Indonesia, English, Español, dan Tiếng Việt.
- **Opsional**: dukungan mod TouchController dan mesin Rust (Horizon) yang masih eksperimental.

**Belum didukung:** Forge untuk Minecraft 1.12.2 ke bawah, NeoForge untuk 1.20.1 ke bawah, dan Quilt.

## Bantuan

- **Game tertutup sendiri atau crash**: beri RAM game tidak lebih dari sekitar setengah RAM HP, tutup aplikasi lain, atau turunkan rasio resolusi di **Pengaturan**. Laporan crash bisa dilihat dari dalam aplikasi.
- **FPS rendah**: kecilkan jarak pandang, turunkan rasio resolusi, atau coba renderer lain di **Pengaturan**.
- **Mod tidak jalan**: pastikan loader dan versi Minecraft instance sama dengan yang dibutuhkan mod.
- **Menemukan bug?** Buka [Issue](https://github.com/ItsVynzzzz/AmberLauncher/issues) dan sertakan versi AmberLauncher, model HP dan versi Android, langkah yang menimbulkan masalah, serta log. Log yang dibagikan dari launcher sudah dibersihkan dari token dan kata sandi.

## Privasi dan keamanan

- Kata sandi Microsoft tidak pernah disimpan; login memakai jendela OAuth resmi Microsoft.
- Aset dan pustaka Minecraft diunduh langsung dari server resmi Mojang dan dicocokkan dengan sidik jari (SHA-1) resminya.
- Semua sambungan internet memakai HTTPS.
- Cara memastikan APK asli dan cara melapor celah keamanan ada di [SECURITY.md](SECURITY.md).

## Untuk pengembang

Butuh JDK 17 dan Android SDK (compile SDK 37, min SDK 26).

```bash
git clone https://github.com/ItsVynzzzz/AmberLauncher.git
cd AmberLauncher
cp local.properties.example local.properties   # isi sdk.dir
./gradlew assembleRelease
```

## Lisensi dan kredit

AmberLauncher dirilis di bawah **GNU General Public License v3.0** (lihat [LICENSE](LICENSE)). Copyright © 2026 ItsVynzzzz untuk perubahan dan kode baru; kode dari proyek hulu tetap milik pembuatnya masing-masing.

AmberLauncher adalah turunan (fork) dari NUX Launcher, yang berbasis Zalith Launcher 2 dan PojavLauncher. Sesuai ketentuan tambahan Zalith Launcher 2, nama "ZalithLauncher" atau "ZL" tidak dipakai, dan keterangan *Unofficial Modified Version* serta pemberitahuan hak cipta tetap ditampilkan di aplikasi.

| Proyek | Pembuat | Lisensi |
|---|---|---|
| [NUX Launcher](https://github.com/IsraaDeveloper/nuxlabs) (sumber fork) | IsraaDeveloper | GPL-3.0 |
| [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2) | MovTery dan kontributor | GPL-3.0 |
| [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) | artdeell, khanhtran, dan tim | LGPL-3.0 |
| [Fold Craft Launcher](https://github.com/FCL-Team/FoldCraftLauncher) | Tim FCL | GPL-3.0 |
| [MobileGlues](https://github.com/MobileGlues/MobileGlues) | Tim MobileGlues | LGPL-2.1 |
| [GL4ES](https://github.com/ptitSeb/gl4es) | ptitSeb | MIT |
| [Horizon](https://github.com/horizon-rust/horizon) dan [port Android-nya](https://github.com/starling-client/starling) (opsional) | Ludovic Marie, Vasysik, dan kontributor | MIT |

Terima kasih juga kepada [Modrinth](https://modrinth.com) atas API publiknya dan [Ely.by](https://ely.by) atas sistem skin dan autentikasinya.

*Minecraft* adalah merek dagang Mojang AB / Microsoft Corporation. AmberLauncher tidak menyertakan atau membagikan salinan game; untuk bermain online, kamu perlu akun Minecraft Java Edition yang sah.
