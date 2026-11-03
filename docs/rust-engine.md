# Mesin Rust (Horizon) di AmberLauncher

Pilihan **Rust** di layar Buat Instance menjalankan Minecraft lewat **Horizon**, yaitu Minecraft Java yang ditulis ulang dalam Rust (bukan Java). Mesinnya dikompilasi dari sumber proyek **Horizon Android** (Vasysik, MIT) di CI repo ini sendiri, lalu ikut dikemas ke APK AmberLauncher. Tidak ada APK Horizon yang dipasang, dan pengguna cukup membuat instance lalu menekan PLAY, seperti Fabric, Forge, atau NeoForge.

Status: **eksperimental, belum diuji di perangkat.** Peluncuran masih lewat layar peluncur milik Horizon (lihat "Rencana berikutnya").

## Apa ini, dan apa bukan

- Mesin ini **menggantikan seluruh client Java**. Tidak ada JVM, `client.jar`, atau library Java, jadi mod Fabric, Forge, NeoForge, dan mod Java apa pun **tidak bisa dipakai**. Horizon punya format mod native sendiri (`.hmod`), tidak bisa dipasang lewat layar Mod launcher.
- Hanya Minecraft **26.3** (versi yang dipatok proyek Horizon). Satu-satunya versi di daftar.
- Sumber daya game (sekitar 500 MiB) diunduh **oleh mesinnya sendiri** dari Mojang saat pertama dibuka. Launcher tidak mengunduhnya.
- Layar peluncur, login, dan dunia permainan masih diurus UI milik Horizon. **Akun launcher belum dihubungkan** (rencana berikutnya, termasuk akun offline).
- Ini proyek pihak ketiga, tidak berafiliasi dengan Mojang atau Microsoft.

## Syarat perangkat

- Android 9 (API 28) ke atas, HP 64-bit (arm64). Selain itu opsinya ditolak dengan pesan yang jelas.
- RAM disarankan 4 GB ke atas. Di bawah itu tetap boleh dicoba, dengan peringatan.
- Ruang kosong sekitar 500 MiB untuk sumber daya, plus koneksi internet saat pertama dibuka.

## Cara kerja pembangunan (tanpa mengurus URL atau hash)

Ada dua tahap yang terpisah supaya build APK tetap cepat:

1. **Workflow "Bangun mesin Rust (Horizon)"** (manual, sekali per versi mesin). Ia mengambil sumber Horizon Android pada **commit yang dipatok** di `horizon-engine.properties`, mengompilasinya untuk arm64 dengan jalur build resmi proyek itu, lalu menerbitkan hasilnya sebagai **Release pra-rilis** di repo ini dengan tag `horizon-engine-<12 digit commit>`. Satu run memakan waktu lama (puluhan menit sampai lebih dari satu jam, terutama profil `release`).
2. **Build APK biasa** hanya mengambil hasil itu. Tugas Gradle `fetchHorizonEngine` menjalankan `scripts/fetch-horizon-engine.sh` sebelum build, mengunduh paket dari Release repo yang sedang membangun, memeriksanya, dan menaruh pustaka serta data mesin untuk dikemas. Tanpa Release-nya, build jalan terus tanpa mesin dan alasannya muncul di ringkasan run.

Tidak ada langkah tambahan di `build-apk.yml`, dan tidak ada URL atau SHA-256 yang perlu diisi.

### Menyiapkan (sekali)

1. Buka `docs/workflows/build-horizon-engine.yml` di repo ini, salin isinya, lalu buat berkas baru `.github/workflows/build-horizon-engine.yml` lewat editor web GitHub (Add file > Create new file) dan tempel. Berkas di `.github/workflows/` tidak bisa dibuat lewat patch, jadi ini satu-satunya langkah manual. Semua logikanya ada di skrip yang bisa diperbarui lewat patch, jadi berkas workflow tidak perlu diubah lagi.
2. Buka tab **Actions > Bangun mesin Rust (Horizon) > Run workflow**. Untuk percobaan pertama boleh pilih profil `fast` (tanpa LTO, lebih cepat dan hemat RAM) supaya alurnya terbukti dulu; profil `release` yang dipakai build APK secara bawaan (`profile=` di `horizon-engine.properties`).
3. Setelah run selesai, ringkasan run mencatat ukuran, SHA-256, dan waktu tiap tahap, dan di halaman Releases muncul pra-rilis `Mesin Rust (Horizon) ...`. Jalankan build APK seperti biasa: mesin terpasang otomatis (ringkasan langkah build menulis "Mesin Rust (Horizon) ikut dibangun").

Bila run gagal, ringkasan run memuat tahap yang gagal, baris galatnya, dan 60 baris terakhir lognya. Salin bagian itu untuk diperbaiki.

### Mengganti versi mesin

Ubah `commit=` di `horizon-engine.properties` (commit baru dari `source_repo`, setelah diperiksa), jalankan workflow pembangun sekali, lalu build APK. Bila `commit=` diganti tetapi workflow belum dijalankan, build APK tetap lolos tanpa mesin dan memberi tahu bahwa Release-nya belum ada.

### Build di komputer sendiri

```bash
bash scripts/fetch-horizon-engine.sh                      # ambil dari Release (repo di release_repo=)
bash scripts/fetch-horizon-engine.sh --bundle paket.zip   # pasang dari paket yang sudah ada
bash scripts/fetch-horizon-engine.sh --remove             # hapus berkas mesin dari pohon kerja
```

Gradle menjalankan skrip itu sendiri di Linux dan macOS (butuh `bash`, `curl`, `unzip`); di Windows dilewati. `HORIZON_ENGINE=off` mematikannya. Release harus bisa diunduh tanpa login, jadi repo-nya harus publik.

Mengompilasi mesin sendiri (Linux x86-64, JDK 25, NDK 28.2, Rust, Python 3.11): `bash scripts/build-horizon-engine.sh --work /folder/lapang`. Butuh puluhan GB disk dan RAM besar.

### APK 32-bit

Mesin hanya untuk arm64. Build 32-bit melewati langkah ini dan `scripts/build-arm32.sh` membuang pustaka dan data mesin dari salinan kerjanya, jadi APK 32-bit tidak membesar.

### Ukuran APK

Mesin menambah ukuran APK 64-bit. Berapa persisnya baru diketahui setelah run pertama: ukuran pustaka dan datanya tercatat di ringkasan run pembangun.

## Kepercayaan dan keamanan

Pustaka native yang dikemas ke APK berjalan **dengan hak yang sama dengan launcher** (satu UID Android). Proses `:horizon` terpisah melindungi launcher dari mesin yang mati (`process::exit`), bukan dari kode jahat. Karena itu:

- Sumbernya dipatok pada **satu commit** (40 digit). Skrip pembangun menolak bila HEAD hasil ambil bukan commit itu, dan skrip pengambil menolak paket yang manifest-nya menyebut commit lain.
- Yang kamu percayai adalah kode sumber di commit itu: **periksa sendiri** sebelum mengganti `commit=`. Aku belum pernah menjalankan hasil build-nya di perangkat.
- Paket diperiksa lagi saat dipasang: SHA-256 paket harus cocok dengan berkas `.sha256` di Release yang sama (menangkap unduhan rusak, bukan Release yang diubah orang yang punya akses tulis ke repo ini), SHA-256 tiap berkas harus cocok dengan manifest di dalamnya, dan pustaka harus ELF 64-bit arm64. Tidak ada isi paket yang dijalankan saat build.
- Release mesin ditandai pra-rilis dengan tag yang bukan nomor versi, jadi tidak dianggap pembaruan aplikasi oleh pemeriksa pembaruan dan tidak mengubah penomoran versi build.

## Lisensi dan data Mojang

- Horizon dan port Android-nya berlisensi **MIT**, Copyright (c) 2026 Ludovic Marie and the Horizon contributors. Pemberitahuan lisensinya dikemas ke APK di `assets/licenses/horizon/` (diambil dari sumber yang dikompilasi; salinan cadangan ada di `third_party/horizon/`). Skrip menolak memasang mesin tanpa pemberitahuan itu.
- `app/src/main/java/dev/horizon/android/HorizonActivity.java` diadaptasi dari Horizon Android (MIT), dengan perubahan: folder data diarahkan ke subfolder `horizon`.
- MIT kompatibel dengan GPL-3.0 milik AmberLauncher. **Aku bukan pengacara.**
- Untuk menyusun tabel data mesin, workflow pembangun **mengunduh sementara berkas Mojang** (jar game dan aset) ke runner dan menjalankan eksporter proyek sumber, persis seperti jalur build publik proyek itu. Hanya berkas dalam daftar putih proyek sumber (`generated_engine_file`: tabel hasil generate, tanpa jar, aset, atau sumber Mojang) yang masuk ke paket. Berkas Mojang tidak disimpan ke repo atau Release. Meski begitu, pernyataan di README bahwa AmberLauncher tidak menyertakan salinan game perlu kamu tinjau terhadap isi `engine-data.zip`; keputusan akhirnya ada padamu.

## Isi di dalam kode

| Bagian | Peran |
|---|---|
| `core/horizon/HorizonEngine.kt` | Memeriksa ketersediaan (API 28+, arm64, pustaka ikut terpasang, RAM) dan membuka mesin |
| `dev/horizon/android/HorizonActivity.java` | `NativeActivity` yang memuat `libhorizon_android.so`. Paket dan nama kelas **tidak boleh diganti**: fungsi JNI di pustakanya terikat padanya |
| `AndroidManifest.xml` | Mendaftarkan activity itu di proses `:horizon` |
| `CreateInstanceScreen.kt` | Opsi Rust di rel loader; versi 26.3 terpilih otomatis; Install dimatikan bila mesin tidak tersedia |
| `DashboardScreen.kt`, `GameLauncher.kt` | PLAY pada instance Rust membuka mesin, tanpa cek akun, Java, atau unduhan launcher |
| `InstanceManager.kt` | Instance Rust selalu dianggap sudah diunduh |
| `horizon-engine.properties` | Patokan: sumber, commit, profil, NDK, repo Release |
| `scripts/build-horizon-engine.sh` | Mengompilasi mesin dari sumber (dipakai workflow pembangun) dan menerbitkan Release |
| `scripts/package-horizon-engine.sh` | Mengemas pustaka, data, lisensi, dan manifest menjadi satu paket zip |
| `scripts/fetch-horizon-engine.sh` | Mengambil dan memeriksa paket dari Release, lalu memasangnya untuk Gradle |
| `app/build.gradle.kts` (`fetchHorizonEngine`) | Menjalankan skrip pengambil sebelum build; tidak pernah menggagalkan build |

Data mesin (dunia, profil, sumber daya, log) ada di `files/horizon/` di dalam data aplikasi, terpisah dari data launcher. Menghapus instance Rust dari launcher **tidak** menghapus data itu karena dipakai bersama; hapus lewat Pengaturan Android > Info aplikasi > Hapus data.

## Daftar uji di perangkat

1. Jalankan workflow pembangun sampai selesai, lalu build APK (ringkasan langkah build menulis "Mesin Rust (Horizon) ikut dibangun"), dan pasang APK-nya.
2. Buat Instance: opsi **Rust** muncul, 26.3 sudah terpilih, tombol Install aktif tanpa peringatan merah.
3. Install, lalu PLAY di Beranda: layar peluncur Horizon terbuka.
4. Login dan unduh sumber daya (sekitar 500 MiB), lalu masuk ke dunia.
5. Keluar dari mesin: launcher harus kembali tampil dan tetap hidup.
6. Instance Fabric, Forge, NeoForge, dan Vanilla yang sudah ada tetap jalan seperti biasa.
7. Pada build **tanpa** mesin: opsi Rust tampil dengan keterangan "belum disertakan" dan Install mati.

Bila gagal, kirim isi log launcher (buku harian) dan logcat dengan filter proses `:horizon`. Kegagalan paling mungkin: `UnsatisfiedLinkError` (nama kelas atau pustaka tidak cocok), layar hitam (Vulkan/GLES tidak didukung), atau game tertutup sendiri (RAM kurang).

## Batasan saat ini

- Hanya Minecraft 26.3, hanya arm64, API 28 ke atas.
- Tanpa mod Java, tanpa Fabric, Forge, NeoForge, dan layar Mod launcher tidak berlaku untuk instance Rust.
- Login dan unduhan sumber daya masih lewat UI Horizon; akun launcher belum dipakai.
- Pembangunan mesin belum pernah dijalankan di CI; run pertama bisa gagal dan perlu diperbaiki berdasarkan ringkasan run.
- Perilaku di perangkat nyata (kinerja, kestabilan, kebutuhan RAM sebenarnya, ukuran APK) belum diuji.

## Rencana berikutnya

- **Ronde 2**: patch kecil di sisi Rust (pada salinan sumber di CI) supaya mesin mau dimulai tanpa layar peluncur Horizon, dari berkas permintaan yang ditulis AmberLauncher (versi, folder, akun, server), dengan mode siapkan sumber daya dan mode main, serta berkas kemajuan yang bisa dibaca launcher.
- **Ronde 3**: sisi AmberLauncher: PLAY langsung masuk ke mesin dengan akun yang dipilih di launcher. **Akun offline** (hanya nama) dan akun Microsoft (sesi dari token yang sudah diperbarui launcher) sama-sama didukung, serta progres unduhan sumber daya di UI launcher sendiri.
