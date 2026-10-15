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

AmberLauncher dibuat untuk HP Android kelas menengah ke bawah (misalnya RAM 4 GB, penyimpanan 64 GB): tampilannya ringan, pengaturannya sedikit tapi jelas, dan setelan awalnya disesuaikan dengan kemampuan perangkat. Tema visualnya gelap transparan dengan aksen kuning amber.

Proyek ini adalah turunan (fork) dari **NUX Launcher**, yang sendiri berbasis **Zalith Launcher 2** dan **PojavLauncher**. Lihat bagian [Lisensi dan Kredit](#lisensi-dan-kredit).

## Fitur

- **Beranda kaca gelap** yang minimalis, dengan latar yang bisa diganti.
- **Banyak instance terpisah**: tiap profil punya versi, mod, dan pengaturan sendiri.
- **Mod, resource pack, shader, dan datapack dari Modrinth**: cari dan pasang langsung dari dalam aplikasi, plus pemasangan file manual.
- **Modloader Fabric**: pilih Vanilla atau Fabric saat membuat instance.
- **Profil perangkat**: setelan RAM, resolusi, dan renderer yang disarankan menurut kemampuan HP.
- **Pilihan renderer dan runtime Java** yang sudah dibawa aplikasi (Java 8, 17, 21, dan 25).
- **Editor kontrol sentuh**: atur posisi, ukuran, dan tombol layar; pemilih tombol berdasarkan kategori; sakelar serang-sentuh.
- **Akun**: Microsoft, Ely.by, dan offline.
- **Laporan crash** dengan unggah log ke mclo.gs bila diinginkan.

Belum ada dan masih direncanakan: pemasang Forge, NeoForge, dan Quilt, serta unduhan dari CurseForge (membutuhkan kunci API sendiri).

> Semua fitur di atas dibangun lewat GitHub Actions dan belum tentu sudah diuji di semua perangkat. Bila ada yang rusak, buka *issue* dengan menyertakan log.

## Memasang

APK hasil build ada di halaman **Releases** repo ini (tiap build lolos membuat rilis *pre-release* otomatis) dan di tab **Actions** sebagai artifact selama 14 hari. Unduh APK-nya di HP, izinkan pemasangan dari sumber ini, lalu pasang.

Kebutuhan: Android 8.0 (API 26) atau lebih baru, CPU arm64-v8a.

Folder data aplikasi ada di `/storage/emulated/0/nuxlauncher` (nama folder dipertahankan agar data lama tetap terbaca).

## Build sendiri

### Lewat GitHub Actions (tanpa komputer)

1. Fork atau pakai repo ini, aktifkan **Actions**.
2. Dorong ke cabang `main` atau jalankan workflow **Build APK** secara manual.
3. Ambil APK dari **Releases** atau artifact hasil build.

### Lewat Android Studio / Gradle

Prasyarat: JDK 17, Android SDK (compile SDK 37, min SDK 26), dan NDK bila ikut mengompilasi kode native.

```bash
git clone https://github.com/ItsVynzzzz/NuxLite.git
cd NuxLite
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

## Privasi

- Kata sandi akun Microsoft tidak pernah disimpan; login memakai jendela OAuth resmi Microsoft.
- Aset dan pustaka Minecraft diunduh langsung dari server resmi Mojang.
- Kunci API, kunci tanda tangan, dan rahasia lain tidak boleh dimasukkan ke repo (lihat `local.properties.example`, berkas `local.properties` diabaikan git).

## Penafian

*Minecraft* adalah merek dagang Mojang AB / Microsoft Corporation. AmberLauncher tidak menyertakan atau membagikan salinan game; pemain wajib memiliki akun Minecraft Java Edition yang sah untuk bermain secara online.
