# Mesin Rust (Horizon) di AmberLauncher

Pilihan **Rust** di layar Buat Instance menjalankan Minecraft lewat **Horizon**, yaitu Minecraft Java yang ditulis ulang dalam Rust (bukan Java). Mesinnya dikompilasi dari sumber proyek **Horizon Android** (Vasysik, MIT) di CI repo ini sendiri, lalu ikut dikemas ke APK AmberLauncher. Tidak ada APK Horizon yang dipasang, dan pengguna cukup membuat instance lalu menekan PLAY, seperti Fabric, Forge, atau NeoForge.

Status: **eksperimental, belum diuji di perangkat.** PLAY langsung masuk game dengan akun yang dipilih di launcher (offline atau Microsoft), tanpa layar peluncur Horizon. Itu butuh mesin yang dikompilasi dengan perubahan Amber (lihat "Peluncuran dari Amber").

## Apa ini, dan apa bukan

- Mesin ini **menggantikan seluruh client Java**. Tidak ada JVM, `client.jar`, atau library Java, jadi mod Fabric, Forge, NeoForge, dan mod Java apa pun **tidak bisa dipakai**. Horizon punya format mod native sendiri (`.hmod`), tidak bisa dipasang lewat layar Mod launcher.
- Hanya Minecraft **26.3** (versi yang dipatok proyek Horizon). Satu-satunya versi di daftar.
- Sumber daya game (sekitar 500 MiB) diunduh **oleh mesinnya sendiri** dari Mojang saat pertama dibuka. Launcher tidak mengunduhnya.
- Akun diambil dari launcher: **offline** dan **Microsoft**. Akun Ely.by belum didukung mesin ini.
- Layar mesin sendiri hanya tampil untuk unduhan sumber daya pertama (sekitar 500 MiB, kemajuannya digambar mesin) dan bila terjadi galat.
- Ini proyek pihak ketiga, tidak berafiliasi dengan Mojang atau Microsoft.

## Syarat perangkat

- Android 9 (API 28) ke atas, HP 64-bit (arm64). Selain itu opsinya ditolak dengan pesan yang jelas.
- RAM disarankan 4 GB ke atas. Di bawah itu tetap boleh dicoba, dengan peringatan.
- Ruang kosong sekitar 500 MiB untuk sumber daya, plus koneksi internet saat pertama dibuka.

## Cara kerja pembangunan (tanpa mengurus URL atau hash)

Satu workflow, **Build APK**, mengurus semuanya dalam **satu kali run**:

1. **Periksa** (sekitar 15 detik). Menerapkan patch run ini dan memutuskan perlu build atau tidak (lihat "Kapan build berjalan" di bawah). Lalu memeriksa mesin: ia menghitung tanda Release mesin untuk konfigurasi *sekarang* (`scripts/build-horizon-engine.sh --print-tag`) dan menanyakan ke GitHub apakah Release itu sudah ada.
2. **Bangun mesin Rust (Horizon)**. Hanya jalan bila Release-nya **belum ada**. Mengambil sumber Horizon Android pada **commit yang dipatok** di `horizon-engine.properties`, menerapkan perubahan Amber (`third_party/horizon-patches/<patch_set>/*.diff`), mengompilasinya untuk arm64 dengan jalur build resmi proyek itu (sekitar 12 menit untuk profil `release`), lalu menerbitkan hasilnya sebagai **pra-rilis** di repo ini. Untuk tabel datanya, ia memakai berkas versi Mojang yang **dipatok** proyek sumber (`scripts/prepare-horizon-data.py`), karena Mojang sesekali menerbitkan ulang berkas itu dan mesin menolak versi yang tidak dikenalnya.
3. **Build APK**. Tugas Gradle `fetchHorizonEngine` menjalankan `scripts/fetch-horizon-engine.sh`, mengunduh paket mesin dari Release itu (di dalam jaringan GitHub, hitungan detik), memeriksanya, dan menaruh pustaka serta data mesin untuk dikemas. Bila langkah 2 gagal, APK tetap dibangun tanpa mesin Rust dan run memberi peringatan jelas; run berikutnya mencoba membangun mesin lagi.

**Tanda Release** mesin: `horizon-engine-<12 digit commit>[-<patch_set>-<8 digit hash isi patch>][-fast]`. Hash dihitung dari nama dan isi semua `.diff` di folder patch, jadi **mengedit patch Amber otomatis berarti mesin baru**: tidak perlu mengganti nama `patch_set=` dan tidak perlu menjalankan apa pun terpisah. Sebaliknya, selama commit, patch, dan profil tidak berubah, mesin **tidak dibangun ulang** dan tidak diunduh ulang oleh siapa pun kecuali build APK di GitHub.

Workflow lama **Bangun mesin Rust (Horizon)** (`docs/workflows/build-horizon-engine.yml`) tetap boleh dipakai manual, misalnya untuk profil `fast`, tetapi tidak diperlukan lagi.

### Kapan build berjalan, dan apa yang terbit

| Yang terjadi | Hasil |
| --- | --- |
| Upload patch yang mengubah isi APK (kode, skrip, `third_party/`, `VERSION`, ...) | Build **uji**: APK ditaruh di satu Release bernama `uji` yang ditimpa tiap build. **Bukan rilis**, jadi aplikasi pengguna tidak menawarkan pembaruan. Hanya APK 64-bit. |
| Upload patch yang hanya mengubah dokumen (`README.md`, `docs/`, `*.md`, `LICENSE`, ...) | Patch diterapkan dan disimpan ke repo. **Tanpa build, tanpa APK, tanpa Release.** |
| Edit `.md`, `docs/`, `.github/` langsung di GitHub | Run bahkan tidak dimulai. |
| Run workflow manual, *rilis* **tidak** dicentang | Build uji, seperti baris pertama. |
| Run workflow manual, *rilis* **dicentang** | Rilis bernomor baru (`v0.1.0-beta.N`) dengan APK 64-bit dan 32-bit. Hanya ini yang membuat pengguna aplikasi ditawari pembaruan. |

Pilihan *mesin* pada Run workflow: `otomatis` (bawaan: bangun hanya bila belum ada), `ulang` (paksa bangun ulang), `lewati` (jangan bangun mesin di run ini).

Build uji mengambil nomor turunan dari rilis terakhir, misalnya `0.1.0-beta.56.uji.130`. Menurut aturan urutan versi di aplikasi, itu lebih baru daripada rilis 56 (tidak pernah dianggap pembaruan) dan lebih lama daripada rilis 57 (rilis berikutnya tetap ditawarkan). Untuk memasang build uji di HP, unduh APK-nya dari halaman Release `uji`.

Jangan mengunggah patch baru saat sebuah build masih berjalan: run lama dibatalkan oleh yang baru (itulah yang menjaga patch tidak diterapkan dua kali), dan pembangunan mesin yang terpotong harus mulai dari awal.

### Menyiapkan (sekali)

1. Ganti isi `.github/workflows/build-apk.yml` dengan `docs/workflows/build-apk.yml` (editor web GitHub: buka berkasnya, ikon pensil, pilih semua, tempel). Berkas di `.github/workflows/` tidak bisa diubah lewat patch, jadi ini satu-satunya langkah manual. Perubahan pada folder `.github` tidak memulai run.
2. Setelah itu cukup upload patch seperti biasa. Bila Release mesin untuk konfigurasi sekarang belum ada, run yang sama membangunnya.

Bila pembangunan mesin gagal, ringkasan run memuat tahap yang gagal, baris galatnya, dan 60 baris terakhir lognya. Salin bagian itu untuk diperbaiki.

### Peluncuran dari Amber

Mesin bawaan Horizon selalu menampilkan layar peluncurnya sendiri. Supaya PLAY di AmberLauncher langsung masuk game, workflow pembangun menerapkan perubahan kecil milik Amber pada salinan sumber sebelum mengompilasinya. Berkasnya ada di `third_party/horizon-patches/<patch_set>/*.diff` (nilai `patch_set=` di `horizon-engine.properties`, sekarang `amber2`) dan hanya menyentuh dua tempat di `crates/horizon-client/src/android/`: berkas baru `amber.rs` dan beberapa baris di `launcher.rs`.

Alurnya:

1. PLAY pada instance Rust menulis `amber-launch.json` (jenis akun, nama, dan untuk Microsoft uuid, token Minecraft, xuid) ke `files/horizon/`, lalu membuka mesin. Akun Microsoft yang token-nya sudah lama diperbarui dulu, sama seperti game Java. Berkas itu hanya bisa dibaca aplikasi ini.
2. Mesin membaca berkas itu **sekali lalu menghapusnya**, dan menolaknya bila lebih tua dari 2 menit. Bila sumber daya game belum ada, mesin mengunduhnya (layar kemajuan milik mesin); begitu selesai, ia langsung bermain.
3. Akun Microsoft diteruskan ke game lewat berkas sesi yang ditulis **saat main dimulai** (hanya bisa dibaca pemiliknya, dihapus game begitu dibaca, sama seperti jalur resmi proyek sumber). Akun offline memakai namanya saja.
4. Saat game ditutup normal, mesin menutup dirinya dan pemain kembali ke launcher. Bila game berhenti karena galat, layar mesin tampil dengan pesannya.

**Masuk langsung (`amber2`, performa).** Mesin bawaan selalu membuat layar peluncurnya dulu: jendela, atlas HUD, dan satu renderer grafis penuh, yang langsung dibuang lagi begitu game dimulai (game membuat renderernya sendiri). Itu pekerjaan ganda tepat di jalur PLAY. Dari `amber2`, bila permintaan Amber ada dan game sudah terpasang, mesin tidak membuat layar peluncur sama sekali dan langsung memulai game. Bila game belum terpasang (unduhan pertama) layar peluncur tetap dibuat untuk menampilkan kemajuan, dan bila memulai game gagal, layar itu dibuat saat itu juga supaya galatnya terbaca. Patch ada di `third_party/horizon-patches/amber2/0002-amber-direct.diff`. Dibuat tanpa bisa dikompilasi atau dijalankan di sandbox: dicek hanya dapat diterapkan di atas `amber1` pada commit yang dipatok. Dampaknya pada waktu muat belum diukur.

Sisi launcher: token Microsoft yang berumur kurang dari 16 jam dipakai langsung dan diperbarui di latar (sebelumnya PLAY menunggu sampai 12 detik bila token lebih tua dari 8 jam), peralihan ke layar mesin tanpa animasi, latar jendela mesin hitam, dan proses `:horizon` tidak lagi memuat kamus terjemahan.

Mesin tanpa perubahan ini (`patch_set=` kosong, atau Release lama) mengabaikan berkas itu dan menampilkan layar peluncur Horizon seperti sebelumnya. Perubahan Amber dijaga sekecil mungkin; bila `commit=` diganti, berkas `.diff` harus dicek ulang terhadap sumber baru (skrip pembangun berhenti dengan pesan jelas bila tidak cocok).

### Mengganti versi mesin

Ubah `commit=` di `horizon-engine.properties` (commit baru dari `source_repo`, setelah diperiksa; periksa juga bahwa `third_party/horizon-patches/<patch_set>/*.diff` masih cocok), lalu upload perubahannya sebagai patch seperti biasa: run yang sama membangun mesin barunya. Mengedit isi `.diff` juga cukup begitu (hash isinya ikut di tanda Release). Perlu folder patch baru? Buat `third_party/horizon-patches/<nama baru>/` dan ganti `patch_set=`. Bila mesinnya gagal dibangun, build APK tetap lolos tanpa mesin dan memberi peringatan.

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
- Mesin dikompilasi dengan perubahan kecil milik Amber (`third_party/horizon-patches/`), yang diperbolehkan MIT; berkas `.diff`-nya terbuka di repo ini.
- MIT kompatibel dengan GPL-3.0 milik AmberLauncher. **Aku bukan pengacara.**
- Untuk menyusun tabel data mesin, workflow pembangun **mengunduh sementara berkas Mojang** (jar game dan aset) ke runner dan menjalankan eksporter proyek sumber, persis seperti jalur build publik proyek itu. Hanya berkas dalam daftar putih proyek sumber (`generated_engine_file`: tabel hasil generate, tanpa jar, aset, atau sumber Mojang) yang masuk ke paket. Berkas Mojang tidak disimpan ke repo atau Release. Meski begitu, pernyataan di README bahwa AmberLauncher tidak menyertakan salinan game perlu kamu tinjau terhadap isi `engine-data.zip`; keputusan akhirnya ada padamu.

## Isi di dalam kode

| Bagian | Peran |
|---|---|
| `core/horizon/HorizonEngine.kt` | Memeriksa ketersediaan (API 28+, arm64, pustaka ikut terpasang, RAM) dan membuka mesin |
| `dev/horizon/android/HorizonActivity.java` | `NativeActivity` yang memuat `libhorizon_android.so`. Paket dan nama kelas **tidak boleh diganti**: fungsi JNI di pustakanya terikat padanya |
| `AndroidManifest.xml` | Mendaftarkan activity itu di proses `:horizon` |
| `CreateInstanceScreen.kt` | Opsi Rust di rel loader; versi 26.3 terpilih otomatis; Install dimatikan bila mesin tidak tersedia |
| `DashboardScreen.kt`, `GameLauncher.kt` | PLAY pada instance Rust memakai akun yang dipilih lalu membuka mesin, tanpa Java atau unduhan launcher |
| `third_party/horizon-patches/` | Perubahan Amber pada sumber mesin (membaca `amber-launch.json`, langsung main, kembali ke launcher) |
| `InstanceManager.kt` | Instance Rust selalu dianggap sudah diunduh |
| `horizon-engine.properties` | Patokan: sumber, commit, perubahan Amber (`patch_set`), profil, NDK, repo Release |
| `scripts/build-horizon-engine.sh` | Mengompilasi mesin dari sumber (dipakai workflow pembangun) dan menerbitkan Release |
| `scripts/prepare-horizon-data.py` | Menyiapkan tabel data memakai berkas versi Mojang yang dipatok (pembungkus `tools/android_prepare.py` milik sumber) |
| `scripts/package-horizon-engine.sh` | Mengemas pustaka, data, lisensi, dan manifest menjadi satu paket zip |
| `scripts/fetch-horizon-engine.sh` | Mengambil dan memeriksa paket dari Release, lalu memasangnya untuk Gradle |
| `app/build.gradle.kts` (`fetchHorizonEngine`) | Menjalankan skrip pengambil sebelum build; tidak pernah menggagalkan build |

Data mesin (dunia, profil, sumber daya, log) ada di `files/horizon/` di dalam data aplikasi, terpisah dari data launcher. Menghapus instance Rust dari launcher **tidak** menghapus data itu karena dipakai bersama; hapus lewat Pengaturan Android > Info aplikasi > Hapus data.

## Daftar uji di perangkat

1. Jalankan workflow pembangun sampai selesai, lalu build APK (ringkasan langkah build menulis "Mesin Rust (Horizon) ikut dibangun"), dan pasang APK-nya.
2. Buat Instance: opsi **Rust** muncul, 26.3 sudah terpilih, tombol Install aktif tanpa peringatan merah.
3. Pilih akun **offline**, Install, lalu PLAY di Beranda. Pertama kali: layar mesin mengunduh sumber daya (sekitar 500 MiB), lalu game langsung mulai tanpa menekan apa pun. Nama pemain di game sama dengan nama akun.
4. Keluar dari game: launcher harus kembali tampil dan tetap hidup. PLAY lagi: langsung masuk game (tanpa unduhan).
5. Ulangi dengan akun **Microsoft**: nama dan skin sesuai akun, dan masuk server online tanpa "Invalid session".
6. Instance Fabric, Forge, NeoForge, dan Vanilla yang sudah ada tetap jalan seperti biasa.
7. Pada build **tanpa** mesin: opsi Rust tampil dengan keterangan "belum disertakan" dan Install mati.

Bila gagal, kirim isi log launcher (buku harian) dan logcat dengan filter proses `:horizon`. Kegagalan paling mungkin: `UnsatisfiedLinkError` (nama kelas atau pustaka tidak cocok), layar hitam (Vulkan/GLES tidak didukung), atau game tertutup sendiri (RAM kurang).

## Batasan saat ini

- Hanya Minecraft 26.3, hanya arm64, API 28 ke atas.
- Tanpa mod Java, tanpa Fabric, Forge, NeoForge, dan layar Mod launcher tidak berlaku untuk instance Rust.
- Akun Ely.by belum didukung; unduhan sumber daya pertama masih digambar UI mesin (bukan UI launcher).
- Server tujuan, mod, dan pengaturan mesin belum bisa diatur dari launcher.
- Pembangunan mesin belum pernah dijalankan di CI; run pertama bisa gagal dan perlu diperbaiki berdasarkan ringkasan run.
- Perilaku di perangkat nyata (kinerja, kestabilan, kebutuhan RAM sebenarnya, ukuran APK) belum diuji.

## Rencana berikutnya

- Mod milik Horizon (`.hmod`) dikelola dari layar Mod launcher, dan pengaturan mesin dibuat tempatnya sendiri di launcher.
- Kemajuan unduhan sumber daya tampil di UI launcher, bukan di layar mesin.
- Akun Ely.by.
