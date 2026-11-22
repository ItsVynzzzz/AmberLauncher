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

- Android 9 (API 28) ke atas. APK 64-bit menjalankan mesin arm64; APK 32-bit menjalankan mesin armeabi-v7a (**eksperimental**, lihat "APK 32-bit"). Perangkat lain ditolak dengan pesan yang jelas.
- RAM disarankan 4 GB ke atas. Di bawah itu tetap boleh dicoba, dengan peringatan.
- Ruang kosong sekitar 500 MiB untuk sumber daya, plus koneksi internet saat pertama dibuka.

## Cara kerja pembangunan (tanpa mengurus URL atau hash)

Satu workflow, **Build APK**, mengurus semuanya dalam **satu kali run**:

1. **Periksa** (sekitar 15 detik). Menerapkan patch run ini dan memutuskan perlu build atau tidak (lihat "Kapan build berjalan" di bawah). Lalu memeriksa mesin: ia menghitung tanda Release mesin untuk konfigurasi *sekarang* (`scripts/build-horizon-engine.sh --print-tag`) dan menanyakan ke GitHub apakah Release itu sudah ada.
2. **Bangun mesin Rust (Horizon)**. Hanya jalan bila Release-nya **belum ada**. Mengambil sumber Horizon Android pada **commit yang dipatok** di `horizon-engine.properties`, menerapkan perubahan Amber (`third_party/horizon-patches/<patch_set>/*.diff`), mengompilasinya untuk arm64 dengan jalur build resmi proyek itu (sekitar 12 menit untuk profil `release`), lalu untuk armeabi-v7a (32-bit, eksperimental; bila gagal, mesin arm64 tetap terbit), dan menerbitkan hasilnya sebagai **pra-rilis** di repo ini. Untuk tabel datanya, ia memakai berkas versi Mojang yang **dipatok** proyek sumber (`scripts/prepare-horizon-data.py`), karena Mojang sesekali menerbitkan ulang berkas itu dan mesin menolak versi yang tidak dikenalnya.
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
| Run workflow manual, *rilis* **dicentang** | Rilis bernomor baru (`vX.Y.Z`, mis. `v1.0.1`) dengan APK 64-bit dan 32-bit. Hanya ini yang membuat pengguna aplikasi ditawari pembaruan. Pilihan *jenis* menentukan kenaikannya: `patch` (1.0.0 jadi 1.0.1), `minor` (jadi 1.1.0), `major` (jadi 2.0.0). Rilis pertama memakai isi berkas `VERSION`. |

Pilihan *mesin* pada Run workflow: `otomatis` (bawaan: bangun hanya bila belum ada), `ulang` (paksa bangun ulang), `lewati` (jangan bangun mesin di run ini).

Build uji diberi nama versi rilis berikutnya plus akhiran `-uji.<nomor run>`, misalnya `1.0.1-uji.130`. Menurut aturan urutan versi di aplikasi, itu lebih baru daripada rilis 1.0.0 (tidak pernah dianggap pembaruan) dan lebih lama daripada rilis 1.0.1 (rilis berikutnya tetap ditawarkan). Untuk memasang build uji di HP, unduh APK-nya dari halaman Release `uji`. Upload patch tidak menaikkan nomor rilis; hanya run manual dengan *rilis* dicentang yang menaikkannya.

Jangan mengunggah patch baru saat sebuah build masih berjalan: run lama dibatalkan oleh yang baru (itulah yang menjaga patch tidak diterapkan dua kali), dan pembangunan mesin yang terpotong harus mulai dari awal.

### Menyiapkan (sekali)

1. Ganti isi `.github/workflows/build-apk.yml` dengan `docs/workflows/build-apk.yml` (editor web GitHub: buka berkasnya, ikon pensil, pilih semua, tempel). Berkas di `.github/workflows/` tidak bisa diubah lewat patch, jadi ini satu-satunya langkah manual. Perubahan pada folder `.github` tidak memulai run.
2. Setelah itu cukup upload patch seperti biasa. Bila Release mesin untuk konfigurasi sekarang belum ada, run yang sama membangunnya.

Bila pembangunan mesin gagal, ringkasan run memuat tahap yang gagal, baris galatnya, dan 60 baris terakhir lognya. Salin bagian itu untuk diperbaiki.

### Peluncuran dari Amber

Mesin bawaan Horizon selalu menampilkan layar peluncurnya sendiri. Supaya PLAY di AmberLauncher langsung masuk game, workflow pembangun menerapkan perubahan kecil milik Amber pada salinan sumber sebelum mengompilasinya. Berkasnya ada di `third_party/horizon-patches/<patch_set>/*.diff` (nilai `patch_set=` di `horizon-engine.properties`, sekarang `amber3`) dan hanya menyentuh dua tempat di `crates/horizon-client/src/android/`: berkas baru `amber.rs` dan beberapa baris di `launcher.rs`.

Alurnya:

1. PLAY pada instance Rust menulis `amber-launch.json` (jenis akun, nama, dan untuk Microsoft uuid, token Minecraft, xuid) ke `files/horizon/`, lalu membuka mesin. Akun Microsoft yang token-nya sudah lama diperbarui dulu, sama seperti game Java. Berkas itu hanya bisa dibaca aplikasi ini.
2. Mesin membaca berkas itu **sekali lalu menghapusnya**, dan menolaknya bila lebih tua dari 2 menit. Bila sumber daya game belum ada, mesin mengunduhnya (layar kemajuan milik mesin); begitu selesai, ia langsung bermain.
3. Akun Microsoft diteruskan ke game lewat berkas sesi yang ditulis **saat main dimulai** (hanya bisa dibaca pemiliknya, dihapus game begitu dibaca, sama seperti jalur resmi proyek sumber). Akun offline memakai namanya saja.
4. Saat game ditutup normal, mesin menutup dirinya dan pemain kembali ke launcher. Bila game berhenti karena galat, layar mesin tampil dengan pesannya.

**Masuk langsung (`amber2`, performa).** Mesin bawaan selalu membuat layar peluncurnya dulu: jendela, atlas HUD, dan satu renderer grafis penuh, yang langsung dibuang lagi begitu game dimulai (game membuat renderernya sendiri). Itu pekerjaan ganda tepat di jalur PLAY. Dari `amber2`, bila permintaan Amber ada dan game sudah terpasang, mesin tidak membuat layar peluncur sama sekali dan langsung memulai game. Bila game belum terpasang (unduhan pertama) layar peluncur tetap dibuat untuk menampilkan kemajuan, dan bila memulai game gagal, layar itu dibuat saat itu juga supaya galatnya terbaca. Patch ada di `third_party/horizon-patches/amber3/0002-amber-direct.diff`. Dibuat tanpa bisa dikompilasi atau dijalankan di sandbox: dicek hanya dapat diterapkan berurutan (0001 lalu 0002) pada commit yang dipatok. Dampaknya pada waktu muat belum diukur.

Sisi launcher: token Microsoft yang berumur kurang dari 16 jam dipakai langsung dan diperbarui di latar (sebelumnya PLAY menunggu sampai 12 detik bila token lebih tua dari 8 jam), peralihan ke layar mesin tanpa animasi, latar jendela mesin hitam, dan proses `:horizon` tidak lagi memuat kamus terjemahan.

**Versi mesin dan `amber3`.** Mesin dipatok ke Horizon Android 0.7.8 (commit `68a2c4dd1b9a`, repo Starling, cabang `android`). Launcher mesin dirombak di versi ini, jadi patch ditulis ulang sebagai `amber3`: pre-warm sesi akun milik mesin dimatikan bila akun datang dari Amber (kalau tidak, PLAY menunggu jaringan), dan skin offline milik mesin tidak ikut akun Microsoft dari Amber. `amber1` dan `amber2` tetap disimpan untuk commit lama `844ef35f2b14`; kembali ke versi lama berarti mengganti `commit=` dan `patch_set=` bersama-sama. Aktivitas mesin di manifest kini memuat `navigation` pada `configChanges` (mencolok gamepad atau keyboard tidak lagi membuat ulang aktivitas), dan layar penuh imersif mati secara bawaan mengikuti mesin. Mesin 0.7.8 sudah menerima akun Ely.by, tetapi jembatan Amber belum meneruskannya.

Mesin tanpa perubahan ini (`patch_set=` kosong, atau Release lama) mengabaikan berkas itu dan menampilkan layar peluncur Horizon seperti sebelumnya. Perubahan Amber dijaga sekecil mungkin; bila `commit=` diganti, berkas `.diff` harus dicek ulang terhadap sumber baru (skrip pembangun berhenti dengan pesan jelas bila tidak cocok).

### Pengaturan Rust

Pengaturan mesin Rust berbeda dari jalur Java (tidak ada JVM, RAM Java, argumen JVM, atau driver Zink), jadi punya tab sendiri: **Pengaturan > Rust**, terpisah dari tab Game. Isinya: status mesin dan sumber daya game, renderer (Vulkan kompatibel, Vulkan cepat, OpenGL ES), skala resolusi, mode performa (Normal, Super hemat, Hemat baterai, FPS maksimum), kurangi animasi game, laju layar, petunjuk performa, batas pekerja chunk, unggah chunk cepat, cache RAM, sederhanakan daun (eksperimental), arah dan rasio layar, layar penuh imersif, poni, perangkat input, rekam dan bagikan laporan performa (grup Diagnostik), server yang langsung dimasuki saat PLAY, dan tombol setel ulang.

Semuanya ditulis langsung ke `files/horizon/android-launcher.json`, berkas config milik mesin sendiri, dengan nama kunci dan nilai persis seperti yang dibaca mesin (`renderer`, `render_scale`, `worker_limit`, `cache_budget_mib`, `display_hz`, `performance_hints`, `input_host`, `orientation`, `immersive`, `avoid_cutout`, `aspect`, `server`, dan sejak mesin 0.7.8 `super_perf_mode`, `battery_perf_mode`, `max_fps_mode`, `disable_game_animations`, `fast_chunk_upload`, `experimental_leaf_culling`, `record_performance`). Karena itu:

- Tidak ada perubahan pada mesin, jadi **tidak perlu membangun ulang mesin**; cukup APK baru.
- Perubahan berlaku pada PLAY berikutnya (mesin membaca config saat dibuka) dan hanya untuk instance Rust. Instance Java tidak terpengaruh.
- Hanya kunci milik tab ini yang disentuh. Kunci lain di berkas itu dibiarkan, dan berkasnya baru dibuat saat pemain mengubah sesuatu. Tombol setel ulang menghapus kunci-kunci itu sehingga mesin memakai bawaannya.
- Tulis berurutan dan atomik (berkas sementara lalu dipindah) di utas latar. Nilai di luar yang dikenal mesin tidak pernah ditulis, karena satu nilai asing membuat mesin membuang seluruh config.
- Layar peluncur milik mesin menulis berkas yang sama; keduanya saling konsisten karena memakai nama kunci yang sama.
- **Mode performa** hanya satu pilihan karena mesin menganggap ketiga mode (super, baterai, FPS) saling meniadakan; launcher menulis ketiga kuncinya sekaligus. Mesin menerapkannya pada PLAY dengan mengubah `options.txt` game (jarak render, awan, partikel, batas FPS, dan seterusnya), sama seperti saat dipilih di layar pengaturan mesin. Kunci lain milik mesin 0.7.8 dibiarkan di bawaan mesin karena sengaja tidak dipasang di tab ini (lihat "Opsi mesin yang sengaja tidak dipasang" di bawah).

**Unggah chunk cepat** (`fast_chunk_upload`, bawaan mesin: menyala) menaikkan jatah unggah mesh chunk ke GPU menjadi paling sedikit 4 MiB per frame setiap PLAY (mesin menerapkannya ke `options.txt`); bila dimatikan, nilai di dalam game yang berlaku. **Sederhanakan daun** (`experimental_leaf_culling`, bawaan mesin: mati) menyederhanakan dedaunan rapat dan mengubah tampilannya; mesin sendiri menyebutnya eksperimental. Keduanya berlaku di PLAY berikutnya.

**Diagnostik.** **Rekam performa** (`record_performance`, mati secara bawaan) membuat mesin merekam waktu frame dan pembuatan chunk saat bermain (paling lama 30 menit, paling besar 8 MiB, tiga berkas terakhir disimpan) ke `files/horizon/performance/horizon-performance-<waktu>.jsonl`. Mesin menyatakan laporan itu tidak berisi akun, chat, nama dunia, atau posisi. Tombol **Bagikan laporan performa** menyalin laporan terbaru ke folder cache berbagi (token disamarkan seperti log lain, di utas latar) lalu membuka pilihan aplikasi; launcher tidak pernah mengubah atau menghapus laporan milik mesin. Perekaman menambah sedikit beban, jadi hanya dinyalakan bila perlu.

**Opsi mesin yang sengaja tidak dipasang** (hasil pemeriksaan kode mesin 0.7.8, bukan kelalaian): `zero_latency_touch` hanya disimpan di config dan tidak dibaca kode mana pun (tombol di layar mesin tidak berefek), `ui_scale_percent` hanya memperbesar layar peluncur milik mesin (HUD game tidak berubah; ukuran HUD diatur di dalam game), `keypad_controls` hanya migrasi sekali dari versi lama (mesin mengosongkannya setiap PLAY; keypad kini diatur di Key Binds dalam game), `proxy` hanya dibaca peluncur desktop (kode Android tidak membacanya), `disable_launcher_animations`, `shader_source`, dan `mod_source` hanya untuk layar peluncur mesin yang tidak dipakai Amber, `gpu_profile` hanya menambah waktu GPU per tahap ke laporan teknis, dan `profile`, `name`, serta `theme` diatur Amber sendiri. Pengaturan video di dalam game (jarak render, FOV, VSync, dan lain-lain) tetap diatur di menu game, karena disimpan di `options.txt` milik mesin.

#### Kontrol Amber (Pengaturan > Rust > Kontrol)

Saklar **Kontrol Amber** (bawaan mati) membuat tombol layar mesin memakai **tata letak dan tampilan editor kontrol Amber**, supaya hanya ada satu set kontrol dan tidak ada lapisan tambahan di atas mesin. Tombol **Menu garis tiga** (menu cepat milik mesin, kunci `show_control_hub`) nyala secara bawaan dan bisa dimatikan di Pengaturan Rust; letak tombol tetap diambil dari editor Amber setiap PLAY, jadi perubahan tata letak yang dibuat di dalam editor mesin tidak bertahan.

**Tema UI mesin.** Saat PLAY, Amber menulis kunci `theme` di `android-launcher.json` (`core/horizon/RustTheme.kt`): preset `custom`, mode gelap, latar `#121115`, kaca `#1B1720`, dan aksen yang dipilih di Pengaturan > Tampilan (dicampur dengan versi terangnya supaya teks terbaca di atas gelap). Mesin memakai satu tema ini di semua layarnya, termasuk menu garis tiga dan editor kontrolnya, jadi warna dan aksennya sama dengan Amber. Selain warna, `amber3/0003-amber-hub-editor.diff` mengubah dua layar mesin: (1) sidebar menu garis tiga menjadi rail ala sidebar Amber (ikon dalam pil beraksen, label di bawahnya, label Indonesia singkat) dengan tombol merah **Keluar** di bawahnya, yang menyimpan dunia lalu kembali ke instance (`begin_closing_save` lalu jalur keluar biasa mesin; dari layar judul langsung keluar); (2) panel properti di editor kontrol menempel di sisi berlawanan dari kontrol yang dipilih, seperti panel editor Amber, dengan latar yang tidak lagi digelapkan, jadi kontrol yang diedit tetap terlihat. Isi tab dan panel masih berbahasa Inggris dan bertata letak mesin. Mesin tetap menggambar dan menangani sentuhan tombolnya sendiri (satu-satunya cara tombol bisa disentuh di jendela mesin). Yang dipakai adalah gaya **GoyControl** mesin 0.7.8 (kontrol hitam), karena gaya ini bisa diatur warna, sudut, label, dan posisi tiap tombolnya. Gaya Bedrock bawaan mesin tidak dipakai selama saklar hidup.

Cara kerjanya: saat PLAY, `core/horizon/RustTouchLayout.kt` menerjemahkan tata letak Amber ke `horizon-touch.json` di folder data mesin (`files/horizon/`, atau `profiles/profile-N/` untuk profil mesin lain), lalu mesin membacanya saat dibuka. Tidak ada kode yang berjalan saat bermain.

- **Tampilan.** Warna meniru kulit Amber: isi kaca hitam, tepi putih tipis, sudut membulat sesuai editor, kuning saat ditekan atau aktif. Joystick memakai lingkaran hitam dengan cincin putih dan tombol bulat. Tidak ada ikon: tombol memakai label teks (nama tombol di editor, paling banyak 9 huruf).
- **Posisi dan ukuran.** Titik tengah, lebar, tinggi, dan transparansi tiap tombol mengikuti editor Amber. Ukuran dalam dp diubah ke pecahan tinggi layar, jadi tombol tampak sama di HP yang berbeda.
- **Tombol yang ikut.** Tombol keyboard dan mouse (paling banyak 32), kombinasi 2 sampai 3 tombol (jadi tombol kunci), tombol tahan/toggle, dan joystick (lari otomatis mengikuti pilihan Amber). Hotbar geser menjadi dua tombol **UP** dan **DN** (gulir). Sembunyikan GUI menjadi F1, dan menu atau tutup menjadi ESC, bila belum ada tombol lain yang sudah mengirim tombol yang sama.
- **Makro.** Makro perintah, turbo, dan urutan aksi ikut: tombolnya di `horizon-touch.json` hanya berisi `amber.macro:<id>`, sedangkan langkahnya ditulis `core/horizon/RustMacros.kt` ke `amber-macros.json` di folder yang sama. Pemutarnya ada di mesin (`amber3/0004-amber-macro.diff`, berkas baru `touch/amber_macro.rs`) dan memakai aturan yang sama dengan pemutar makro Amber: jeda 10 ms antar langkah, jeda minimal 30 ms antar putaran, jitter, ulang, mode sekali/tahan/kunci, dan semua tombol yang tertahan dilepas saat makro berhenti. Perintah dan teks dikirim langsung ke obrolan mesin, tanpa membuka layar obrolan. Makro hanya berjalan saat dunia sedang dimainkan: membuka menu, menjeda, keluar ke judul, atau meninggalkan jendela menghentikannya. Tombolnya tampil menyala selama makro berjalan. Mesin yang dibangun tanpa `0004` mengabaikan tombol makro. Kombo tombol tetap tombol biasa (sampai 3 tombol).
- **Yang dilewati.** Tombol FPS dan keyboard layar tidak punya padanan di mesin.
- **Tombol bawaan mesin** disembunyikan semua, kecuali joystick gerak: tata letak Amber-lah satu-satunya yang tampil. Tombol bawaan Amber sudah memuat serang, pakai, lompat, jongkok, dan seterusnya, jadi game tetap bisa dimainkan. Bila tata letak Amber tidak punya joystick dan tombol sama sekali, berkas mesin tidak disentuh.
- **Geser lihat.** Bila "Geser kamera dari tombol" (`buttonLookDrag`) hidup, tombol serang, pakai, dan lompat meneruskan gerakan jari ke kamera, seperti di Amber.
- Hanya kunci yang diperlukan yang ditulis (`control_style`, `mode`, `layouts`, `button_visibility`, `joystick_style`, `custom_buttons`, dan beberapa pengaturan tata letak); sisanya dibiarkan. Hasilnya sudah diuji terhadap definisi berkas dari kode mesin 0.7.8 (bisa dibaca, tidak ada tombol yang dibuang, dan tersimpan ulang tanpa perubahan).
- Berkas asli disalin ke `horizon-touch.amber-backup.json` sebelum Amber menyentuhnya, dan dipulihkan pada PLAY berikutnya setelah saklar dimatikan. Tanpa berkas asli, mesin kembali ke bawaannya.
- Perubahan tata letak dan makro di editor Amber berlaku pada PLAY berikutnya (berkas makro dibaca ulang tiap ada perubahan), jadi tidak perlu membangun ulang mesin.
- Menu pop-up milik mesin (Horizon) tidak diubah; hanya tombol layar yang mengikuti Amber.

### Mengganti versi mesin

Ubah `commit=` di `horizon-engine.properties` (commit baru dari `source_repo`, setelah diperiksa; periksa juga bahwa `third_party/horizon-patches/<patch_set>/*.diff` masih cocok), lalu upload perubahannya sebagai patch seperti biasa: run yang sama membangun mesin barunya. Mengedit isi `.diff` juga cukup begitu (hash isinya ikut di tanda Release). Perlu folder patch baru? Buat `third_party/horizon-patches/<nama baru>/` dan ganti `patch_set=`. Bila mesinnya gagal dibangun, build APK tetap lolos tanpa mesin dan memberi peringatan.

### Menjaga mesin tetap bisa diperbarui

Perubahan Amber pada mesin dibuat supaya gampang dipindahkan ke versi mesin yang lebih baru:

- Kode baru milik Amber ditaruh di **berkas baru** (`android/amber.rs`, `touch/amber_macro.rs`), bukan disisipkan ke berkas Horizon. Berkas Horizon hanya diberi kait pendek, beberapa baris yang memanggil berkas Amber.
- Data dari launcher ke mesin lewat berkas JSON di folder data mesin (`amber-launch.json`, `horizon-touch.json`, `amber-macros.json`); tidak ada antarmuka lain yang harus dijaga.
- Logika yang bisa diuji tanpa perangkat (misalnya pemutar makro) ada di crate yang bisa dikompilasi di komputer biasa, dengan tesnya sendiri.
- Satu patch per topik di `third_party/horizon-patches/<patch_set>/`, bernomor berurutan.

Keadaan sumber (diperiksa 16 November 2026): cabang `android` repo Starling hanya maju dua commit dari 0.7.8 (keduanya README). Proyek sumber sudah menggabungkan port Android ke cabang utamanya (tag `v0.8.0`, 10 November 2026) dengan susunan berkas yang berbeda (misalnya `android_controls/custom.rs` dan `android_control_ui.rs`, tanpa `android/launcher.rs`), jadi `amber3` tidak bisa langsung diterapkan di sana: berkas Amber yang baru bisa dipakai ulang, kait dan patch yang menyentuh berkas Horizon perlu ditulis ulang. Sampai itu dikerjakan, mesin tetap dipatok di 0.7.8.

### Build di komputer sendiri

```bash
bash scripts/fetch-horizon-engine.sh                      # ambil dari Release (repo di release_repo=)
bash scripts/fetch-horizon-engine.sh --bundle paket.zip   # pasang dari paket yang sudah ada
bash scripts/fetch-horizon-engine.sh --remove             # hapus berkas mesin dari pohon kerja
```

Gradle menjalankan skrip itu sendiri di Linux dan macOS (butuh `bash`, `curl`, `unzip`); di Windows dilewati. `HORIZON_ENGINE=off` mematikannya. Release harus bisa diunduh tanpa login, jadi repo-nya harus publik.

Mengompilasi mesin sendiri (Linux x86-64, JDK 25, NDK 28.2, Rust, Python 3.11): `bash scripts/build-horizon-engine.sh --work /folder/lapang`. Butuh puluhan GB disk dan RAM besar.

### APK 32-bit (eksperimental)

Horizon Android sendiri menyediakan build ARMv7 yang ia sebut eksperimental, dan Amber mengikutinya:

- Workflow pembangun mesin juga mengompilasi `armeabi-v7a` (setelah arm64 selesai) dan menerbitkannya di Release yang sama sebagai `horizon-engine-armeabi-v7a.zip`. Bila langkah itu gagal, Release tetap terbit dengan paket arm64 saja; ringkasan run menuliskan baris galatnya.
- `scripts/build-arm32.sh` memasang paket itu ke APK 32-bit lewat `scripts/fetch-horizon-engine.sh --abi armeabi-v7a`. Tanpa paketnya APK 32-bit tetap dibangun, hanya tanpa mesin Rust (pilihan Rust di HP itu menampilkan "belum disertakan").
- APK 32-bit hanya dibangun pada **rilis** (Run workflow dengan "rilis" dicentang); build uji hanya 64-bit.
- Bedanya dengan 64-bit (dari proyek sumber): tanpa runtime mod `.hmod` dan tanpa jaminan performa di GPU lama. Renderer bawaannya sama (Vulkan kompatibel); bila HP tidak punya Vulkan yang sehat, coba OpenGL ES di Pengaturan > Rust. Android minimal tetap 9 (API 28).
- Mesin 32-bit menambah ukuran APK 32-bit (pustaka ditambah tabel data mesin).
- Release mesin yang terbit sebelum langkah 32-bit ada tidak punya paketnya. Jalankan Build APK sekali dengan pilihan mesin = ulang supaya Release itu dibangun lagi lengkap.

### Ukuran APK

Mesin menambah ukuran APK 64-bit. Berapa persisnya baru diketahui setelah run pertama: ukuran pustaka dan datanya tercatat di ringkasan run pembangun.

## Kepercayaan dan keamanan

Pustaka native yang dikemas ke APK berjalan **dengan hak yang sama dengan launcher** (satu UID Android). Proses `:horizon` terpisah melindungi launcher dari mesin yang mati (`process::exit`), bukan dari kode jahat. Karena itu:

- Sumbernya dipatok pada **satu commit** (40 digit). Skrip pembangun menolak bila HEAD hasil ambil bukan commit itu, dan skrip pengambil menolak paket yang manifest-nya menyebut commit lain.
- Yang kamu percayai adalah kode sumber di commit itu: **periksa sendiri** sebelum mengganti `commit=`. Aku belum pernah menjalankan hasil build-nya di perangkat.
- Paket diperiksa lagi saat dipasang: SHA-256 paket harus cocok dengan berkas `.sha256` di Release yang sama (menangkap unduhan rusak, bukan Release yang diubah orang yang punya akses tulis ke repo ini), SHA-256 tiap berkas harus cocok dengan manifest di dalamnya, dan pustaka harus ELF yang sesuai ABI paket (64-bit arm64, atau 32-bit ARM untuk paket armeabi-v7a). Tidak ada isi paket yang dijalankan saat build.
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
| `core/horizon/RustSettings.kt` | Membaca dan menulis `android-launcher.json` (pengaturan tab Rust) tanpa menyentuh kunci lain |
| `ui/components/RustSettingsSection.kt` | Isi tab Rust di Pengaturan, memakai komponen `Setting*` yang sama dengan tab lain |
| `core/horizon/RustTouchLayout.kt` | Menerjemahkan tata letak dan kulit kontrol Amber ke gaya GoyControl di `horizon-touch.json` mesin saat PLAY (saklar Kontrol Amber) |
| `core/horizon/RustMacros.kt` | Menerjemahkan makro Amber (perintah, turbo, urutan aksi) ke `amber-macros.json` untuk pemutar makro di mesin |
| `third_party/horizon-patches/` | Perubahan Amber pada sumber mesin (membaca `amber-launch.json`, langsung main, kembali ke launcher, pemutar makro) |
| `InstanceManager.kt` | Instance Rust selalu dianggap sudah diunduh |
| `core/horizon/HorizonContent.kt` | Shader dan Resource Pack untuk instance Rust: memasang, memilih, dan menghapus langsung di folder dan berkas pengaturan mesin (lihat bagian di bawah) |
| `horizon-engine.properties` | Patokan: sumber, commit, perubahan Amber (`patch_set`), profil, NDK, repo Release |
| `scripts/build-horizon-engine.sh` | Mengompilasi mesin dari sumber (dipakai workflow pembangun) dan menerbitkan Release |
| `scripts/prepare-horizon-data.py` | Menyiapkan tabel data memakai berkas versi Mojang yang dipatok (pembungkus `tools/android_prepare.py` milik sumber) |
| `scripts/package-horizon-engine.sh` | Mengemas pustaka, data, lisensi, dan manifest menjadi satu paket zip |
| `scripts/fetch-horizon-engine.sh` | Mengambil dan memeriksa paket dari Release, lalu memasangnya untuk Gradle |
| `app/build.gradle.kts` (`fetchHorizonEngine`) | Menjalankan skrip pengambil sebelum build; tidak pernah menggagalkan build |

Data mesin (dunia, profil, sumber daya, log) ada di `files/horizon/` di dalam data aplikasi, terpisah dari data launcher. Menghapus instance Rust dari launcher **tidak** menghapus data itu karena dipakai bersama; hapus lewat Pengaturan Android > Info aplikasi > Hapus data.

## Mod, Shader, dan Resource Pack (layar Mod untuk instance Rust)

Untuk instance Rust, layar Mod punya tiga tab: Mod, Shader, dan Resource Pack. Shader dan Resource Pack datang dari katalog Horizon (shader asli dan resource pack untuk mesin) atau dari Modrinth (zip klasik), dipilih lewat pil sumber di layar Jelajahi; Mod datang dari katalog Horizon atau dari berkas `.hmod` di HP. Semua berkasnya memakai folder dan pengaturan yang sama dengan mesin, jadi apa yang dipasang atau dipilih di Amber langsung terlihat di mesin.

### Mod (tab Mod)

Mesin Rust tidak menjalankan mod Java (`.jar` Fabric, Forge, NeoForge). Yang dikenalnya: empat modul bawaan dan mod berformat `.hmod` (data dan aset, atau kode WASM yang berjalan dalam kotak pasir). Kode: `core/horizon/HorizonMods.kt` (berkas, validasi, hidup/mati) dan `core/horizon/HorizonRegistry.kt` (katalog).

- Modul bawaan (Zoom, Minimap, Resep, SmartF3) tampil di daftar Terpasang tanpa tombol hapus. Semuanya mati sampai dihidupkan.
- Berkas `.hmod` ada di `files/horizon/horizon-mods/<id>.hmod` (id dari manifest, sama untuk pembaruan). Mod berbentuk folder yang dipasang dari dalam mesin ikut tampil dan bisa dihidupkan atau dimatikan, tetapi tidak dihapus dari Amber.
- Hidup/mati ditulis ke `files/horizon/horizon-mods.txt` sebagai baris `mod.<id>:true`. Baris lain di berkas itu (misalnya tombol milik mod) dibiarkan apa adanya. Mod yang tidak tercantum dianggap mati. Perubahan berlaku saat game dibuka lagi.
- Pasang dari berkas: tombol Impor menerima `.hmod` (maksimal 32 MB). Diperiksa seperti mesin: arsip zip tanpa jalur berbahaya, `horizon-mod.toml` di dasar arsip dan sah (id, api 0.1 sampai 0.3, kemampuan, namespace), isi paling besar 64 MB, `mod.wasm` paling besar 32 MB, dan kode wajib ada kecuali mod konten murni (kemampuan `registries`). Isi kode WASM sendiri diperiksa mesin saat game dimuat. Mod dengan id yang sama menggantikan yang lama dan pilihan hidup/matinya dipertahankan.
- Mod baru yang hanya berisi konten langsung hidup. Mod berkode dipasang dalam keadaan mati; pengguna menghidupkannya sendiri setelah memeriksa izinnya di Options > Mods dalam game.
- Katalog: `https://horizon-rust.github.io/horizon-mods/index.json` (schema 1), disimpan di memori 10 menit. Hanya proyek `mod` yang punya versi untuk 26.3, bukan yang ditarik (`revoked`), berkas `.hmod` paling besar 32 MB, dan alamat unduhan berbentuk rilis repo horizon-mods (`https://github.com/horizon-rust/horizon-mods/releases/download/<tag>/<nama>`, satu-satunya tempat unduh yang diterima mesin). Unduhan harus sama persis dengan ukuran dan SHA-256 di katalog sebelum dipasang. Tanda tangan minisign belum diperiksa di Amber.
- APK 32-bit: mesin tidak menjalankan mod di sana, jadi Amber menolak menghidupkannya dan menampilkan catatan; memasang dan menghapus tetap bisa.
- Tidak ada penyaringan kemampuan di katalog: mod berkode ikut tampil dengan tanda "Berkode", mod konten murni dengan tanda "Konten".

### Shader dan Resource Pack

- Shader: folder `files/horizon/shaderpacks/`. Satu shader aktif dicatat di `files/horizon/horizon-graphics.txt` (baris `pickedShaderPack:<nama berkas>`). Hidupkan saklarnya di daftar Terpasang untuk memilihnya; mematikannya mengosongkan pilihan.
- Resource Pack: folder `files/horizon/resourcepacks/`. Daftar aktif ada di baris `resourcePacks:` pada `files/horizon/options.txt` (yang terakhir paling atas). Berkas `options.txt` dibuat oleh game, bukan oleh launcher (tanpa baris versi, mesin menganggapnya berkas versi sangat lama dan mengisi ulang pengaturan grafik); bila belum ada, saklar menampilkan petunjuk untuk membuka game sekali dulu.
- Perubahan dibaca mesin saat game dibuka, jadi berlaku pada permainan berikutnya.
- Sumber Modrinth: hanya berkas `.zip` yang punya sidik jari (dicocokkan setelah unduh), paling besar 256 MB, lalu diperiksa dengan aturan mesin sendiri (nama berkas polos, jalur entri aman, total isi paling besar 512 MB, `pack.mcmeta` untuk resource pack, folder `shaders/` untuk shader). Versi yang mencantumkan 26.3 diurutkan lebih dulu, sisanya mengikuti urutan Modrinth. Dependensi diabaikan.
- Sumber Horizon (bawaan untuk Shader dan Resource Pack): katalog yang sama dengan tab Mod, dengan dua jenis tambahan. `shader` = shader asli Horizon (`.hshader`, paling besar 16 MB) dan `resourcepack` = resource pack `.zip` yang dibuat atau diuji untuk mesin (paling besar 64 MB). Aturan unduhannya sama dengan mod: hanya versi untuk 26.3, bukan yang ditarik, alamat rilis horizon-mods, ukuran dan SHA-256 harus sama dengan katalog. Berkas `.hshader` diperiksa bentuknya seperti paket mesin (zip, paling banyak 512 entri, jalur entri aman, `horizon-shaders.toml` di dasar arsip, berkas `.wgsl` paling besar 256 KB, berkas lain 1 MB, total isi 8 MB); isi WGSL dan manifestnya diperiksa katalog dan mesin sendiri. Saklar di daftar Terpasang memilih berkasnya lewat `pickedShaderPack` seperti shader lain. Filter Kategori tidak ada untuk sumber ini.
- Shader asli yang diunduh dari Modrinth sebagai `.zip` (berisi `horizon-shaders.toml`) ditolak dengan petunjuk untuk memakai sumber Horizon. Impor berkas `.hshader` dari HP belum ada di Amber.
- Bila pengguna memakai profil selain 0 di dalam mesin (`android-launcher.json`, kunci `profile`), Amber mengikuti folder profil itu.
- Dukungan shader di mesin masih eksperimental dan bisa berat di HP lemah, jadi shader yang baru diunduh tidak dipilih otomatis.
- Bila shader tidak tampil seperti seharusnya (mis. hanya kabut yang berubah): mainkan sebentar, keluar dari game, lalu Pengaturan > Log game > Log launcher > Bagikan. Laporannya memuat bagian "Mesin Rust (Horizon)": pengaturan mesin, isi folder shaderpacks dan resourcepacks, `horizon-graphics.txt`, dan baris "shader pack ..." dari `files/horizon/performance/diagnostic.log` (daftar program yang berhasil dimuat, atau alasan gagalnya).
- Setiap kali versi mesin dinaikkan, periksa ulang bahwa shader pack masih ada di sumber mesin (desktop Horizon menghapusnya pada 24 Oktober 2026 dan berjanji mengembalikannya ditulis ulang): `crates/horizon-shaders` dan `game/shader_packs.rs`.

## Daftar uji di perangkat

1. Jalankan workflow pembangun sampai selesai, lalu build APK (ringkasan langkah build menulis "Mesin Rust (Horizon) ikut dibangun"), dan pasang APK-nya.
2. Buat Instance: opsi **Rust** muncul, 26.3 sudah terpilih, tombol Install aktif tanpa peringatan merah.
3. Pilih akun **offline**, Install, lalu PLAY di Beranda. Pertama kali: layar mesin mengunduh sumber daya (sekitar 500 MiB), lalu game langsung mulai tanpa menekan apa pun. Nama pemain di game sama dengan nama akun.
4. Keluar dari game: launcher harus kembali tampil dan tetap hidup. PLAY lagi: langsung masuk game (tanpa unduhan).
5. Ulangi dengan akun **Microsoft**: nama dan skin sesuai akun, dan masuk server online tanpa "Invalid session".
6. Instance Fabric, Forge, NeoForge, dan Vanilla yang sudah ada tetap jalan seperti biasa.
7. Pada build **tanpa** mesin: opsi Rust tampil dengan keterangan "belum disertakan" dan Install mati.
8. Pengaturan > **Rust**: ubah skala resolusi atau renderer, PLAY lagi, dan perubahannya terasa; tab Game tidak berubah. Isi **Server otomatis** lalu PLAY: langsung masuk server itu. **Setel ulang** mengembalikan semuanya.
   Di bagian **Kontrol**, nyalakan saklar **Kontrol Amber** (bawaannya mati): PLAY lagi dan tombol layar mesin tampil berwarna dan bertata letak seperti editor kontrol Amber (kaca gelap, sudut membulat, kuning saat ditekan), tanpa tombol ganda. Matikan saklarnya lalu PLAY: tombol bawaan mesin kembali (itu juga tampilan bawaan).
   Di bagian **Performa**, pilih **Super hemat** lalu PLAY: jarak render dan partikel turun; kembali ke **Normal** lalu PLAY, dan pengaturan video game bisa dikembalikan sendiri di dalam game (mesin tidak mengembalikan angka yang sudah diturunkannya). Coba matikan **Unggah chunk cepat** lalu PLAY dan bandingkan terasa tidak tersendat saat berlari menembus chunk baru; nyalakan **Sederhanakan daun** dan PLAY: dedaunan tampak lebih sederhana.
   Di bagian **Diagnostik**: nyalakan **Rekam performa**, PLAY dan main beberapa menit, keluar, lalu ketuk **Bagikan laporan performa**: pilihan aplikasi muncul dengan berkas `horizon-performance-....jsonl`. Sebelum pernah merekam, tombol itu hanya menampilkan pesan petunjuk.
9. Mod: buka layar Mod pada instance Rust. Tab Mod menampilkan empat modul bawaan (mati) tanpa tombol hapus. Hidupkan **Zoom**, PLAY, lalu tahan Z di game: pandangan membesar; matikan lalu PLAY: tidak lagi.
   Di Jelajahi, cari satu mod dari Horizon dan pasang: ia muncul di Terpasang (hidup bila mod konten, mati bila berkode), dan tampil di Options > Mods dalam game. Impor satu berkas `.hmod` dari HP: terpasang; impor lagi berkas yang sama: diganti tanpa dobel. Coba impor `.jar` Fabric: ditolak dengan penjelasan.
   Shader: di Jelajahi, sumber **Horizon** tampil lebih dulu. Pasang satu shader asli, hidupkan saklarnya di daftar Terpasang, lalu PLAY: di dalam mesin shader itu sudah terpilih dan tampil di game; matikan saklar lalu PLAY lagi: shader hilang. Pindah ke **Modrinth** untuk shader klasik (zip) dan coba cara yang sama. Bila katalog Horizon belum punya shader atau resource pack, daftarnya kosong dan layar menyarankan pindah ke Modrinth.
10. Resource Pack: setelah game pernah dibuka dan satu pengaturan diubah (supaya `options.txt` ada), unduh satu resource pack (dari Horizon atau Modrinth), hidupkan saklarnya, lalu PLAY: teksturnya berlaku. Pilihan yang sama terlihat di layar resource pack milik mesin.
11. HP 32-bit (opsional, hanya rilis): pasang `AmberLauncher-...-32bit-armeabi-v7a.apk`, buat instance Rust. Bila build mesin 32-bit lolos, PLAY membuka mesin; bila tidak, layar Buat Instance menyebut mesin belum disertakan.

Bila gagal, kirim laporan log launcher (Pengaturan > Log game; sudah memuat log mesin Rust) dan logcat dengan filter proses `:horizon`. Kegagalan paling mungkin: `UnsatisfiedLinkError` (nama kelas atau pustaka tidak cocok), layar hitam (Vulkan/GLES tidak didukung), atau game tertutup sendiri (RAM kurang).

## Batasan saat ini

- Hanya Minecraft 26.3, API 28 ke atas. arm64 untuk APK 64-bit; armeabi-v7a (APK 32-bit) eksperimental dan belum pernah diuji di perangkat.
- Tanpa mod Java, tanpa Fabric, Forge, NeoForge: mesin Rust bukan Minecraft Java, jadi mod `.jar` tidak bisa berjalan. Yang tersedia adalah modul bawaan, mod `.hmod` (katalog Horizon atau impor berkas), Shader, dan Resource Pack.
- Akun Ely.by belum didukung; unduhan sumber daya pertama masih digambar UI mesin (bukan UI launcher).
- Mod `.hmod` belum bisa dipasang lewat tautan, dan mod Java belum bisa diporting ke mesin dari Amber (itu fitur peluncur mesin sendiri). Pengaturan mesin yang berfungsi di Android sudah ada di tab Rust; sisanya sengaja tidak dipasang (lihat daftar di bagian Pengaturan Rust).
- Pembangunan mesin belum pernah dijalankan di CI; run pertama bisa gagal dan perlu diperbaiki berdasarkan ringkasan run.
- Perilaku di perangkat nyata (kinerja, kestabilan, kebutuhan RAM sebenarnya, ukuran APK) belum diuji.

## Rencana berikutnya

- Pembaruan dari katalog: mod, shader asli, dan resource pack yang sudah terpasang diberi tanda bila ada versi yang lebih baru (sekarang versi lama dan baru bisa terpasang berdampingan untuk shader dan resource pack).
- Kemajuan unduhan sumber daya tampil di UI launcher, bukan di layar mesin. Untuk Starling, unduhan berkas resmi sudah begitu (`StarlingFiles`); sisanya penyiapan game di HP (Java 25 dan `horizon-prepare`). Langkah berikutnya: patch Rust kecil (`0007` di seri `amber5`) yang menulis tahap `preparer::stage()` ke sebuah berkas JSON supaya launcher menampilkannya di dialog Amber, atau menjalankan penyiapan itu dari launcher.
- Akun Ely.by.

## Dua mesin Rust: Horizon dan Starling

Amber punya **dua mesin Rust yang berdampingan**. Horizon (di atas) tidak berubah. **Starling** (penerus Horizon Android, MIT; fork `CihuyAkz/starling-extras`) adalah loader kedua di Buat Instance, dengan shader, mod bawaan, dan kontrolnya sendiri. Tujuan akhirnya: Starling dimodifikasi dan disatukan penuh dengan Amber. Sejauh ini: Starling bisa dibuat dan dimainkan dari Amber, dan mod, shader, serta resource pack-nya dikelola dari layar Mod Amber.

| | Horizon | Starling |
|---|---|---|
| Pengaturan | `horizon-engine.properties` | `starling-engine.properties` |
| Loader instance | `horizon` (tab Rust) | `starling` (tab Starling) |
| Pustaka | `libhorizon_android.so` | `libstarling_android.so` (+ `libstarling_java.so`, `libstarling_javac.so`) |
| Kelas Java | `HorizonActivity` (di repo, proses `:horizon`) | `GameActivity` dan pendampingnya (dari sumber, proses `:starling`) |
| Folder data | `files/horizon` | `files/starling` |
| Perubahan Amber pada sumber | patch `amber3` (menu, makro, akun lewat `amber-launch.json`) | patch `amber5` (Java, pembaruan, mod native) |
| Kontrol Kotlin | `HorizonEngine` | `StarlingEngine` |
| Pengaturan tab Rust di Amber | `android-launcher.json` (dibaca peluncur mesin) | `horizon-graphics.txt` (sebagian, lewat `StarlingSettingsSync`) |
| Mod, shader, resource pack | layar Mod Amber (folder `files/horizon`) | layar Mod Amber yang sama (folder `files/starling`) |

**Build (satu run, satu Release).** Workflow hanya mengenal `scripts/build-horizon-engine.sh` dan satu tanda Release, jadi skrip itu yang membagi kerjanya: `--print-tag` mencetak tanda gabungan (`horizon-engine-...-starling-engine-...-r<revisi>`), dan membangun menjalankan skrip yang sama untuk tiap mesin (`AMBER_ENGINE`, `HORIZON_ENGINE_CONFIG`). **Satu mesin gagal tidak menggagalkan yang lain**: Release diterbitkan dengan paket yang berhasil dan run ditandai gagal supaya terlihat. Karena Release yang sudah ada dilewati, mencoba lagi yang gagal butuh salah satu: jalankan ulang workflow dengan *mesin = ulang*, atau naikkan `revision=` di `starling-engine.properties` (untuk perbaikan skrip build). `enabled=false` di `starling-engine.properties` mengembalikan cara kerja lama (hanya Horizon, tanda Release lama).

**Bentuk sumber Starling.** Berbeda dari Horizon 0.7.8: tidak ada `launcher.rs`, `tools/android_build.py`, maupun `HorizonActivity`. Skrip build mengenalinya otomatis: pustaka dibangun dengan `cargo ndk -p horizon-android` (sama dengan `packaging/android/build_apk.py`), ditambah peluncur `java`/`javac` untuk menyiapkan game di HP, aset APK lewat `build_apk.assets()`, serta kelas Java dan resource sumbernya (setelah patch diterapkan) dalam satu paket **schema 2** supaya selalu satu commit dengan pustakanya. Tidak ada `engine-data.zip` dan tidak ada berkas Mojang yang diunduh CI: game disiapkan di HP. Hanya arm64.

**Pemasangan.** `scripts/fetch-horizon-engine.sh` dijalankan Gradle dua kali (`fetchHorizonEngine`, `fetchStarlingEngine`). Paket Starling diperiksa (commit, patch, sidik jari, ELF, nama berkas di zip) lalu pustaka masuk ke `jniLibs` dan kelas Java, resource, serta aset ke `app/src/starling` (diabaikan git; dibaca Gradle lewat `sourceSets`). Tiap mesin hanya menyentuh berkasnya sendiri, termasuk saat `--remove`.

**Patch `amber5`** (seri Rust/Java di `third_party/horizon-patches/amber5`, diterapkan berurutan oleh CI pada commit yang dipatok). `0001`: kelas Java memakai `R` milik Amber; game selalu di folder privat (bukan folder bersama); pustaka dimuat sebagai `starling_android` (diganti nama supaya tidak bentrok dengan Horizon); `GameActivity` memisahkan folder data (`files/starling`) dan membawa Discord Rich Presence. `0002`: pemberitahuan pembaruan Starling dimatikan bila `AMBER_EMBEDDED` diatur. `0003` (Rust, `game/native_builds.rs`): bila mesin dikompilasi dengan `AMBER_EMBEDDED` (lihat di bawah), tombol Compile di Options > Mods tidak lagi mengirim kode ke server pembangun pihak ketiga; yang tampil adalah pesan bahwa mod native sudah dibangun ke dalam mesin oleh Amber. Mesin yang dibangun tanpa `AMBER_EMBEDDED` berperilaku seperti sumbernya. `0004` (Rust, `android.rs`): bila `AMBER_PERF_RECORD=1` ada di `horizon-args.txt`, rekaman performa dimulai saat mesin dibuka (seperti Options > Diagnostics > Record di dalam mesin); tanpa variabel itu tidak ada yang berubah. `0005` (Rust, `game.rs`, `game/files_offer.rs`, `game/setup.rs`): bila mesin dikompilasi dengan `AMBER_EMBEDDED`, mesin tidak menawarkan sendiri memindahkan folder game ke penyimpanan bersama atau membawa dunia dari Horizon Android, dan tidak menyisipkan server resmi Horizon ke `servers.dat`; folder game diatur launcher. `0006` (Rust, `game.rs`): lihat "Game keluar sendiri di HP lambat" di bawah. Perubahan mesin berikutnya (menu, tema, kemajuan penyiapan) ditulis sebagai `0007` dan seterusnya di folder yang sama, jadi semuanya tetap berupa diff atas sumber yang dipatok, bukan salinan sumbernya.

**Mod native di dalam mesin (`native_mods`).** Sebelumnya pustaka Starling di Amber dibangun dengan `cargo ndk` biasa: `build_apk.py` milik sumber hanya menyalin bundel mod ke APK sebagai kode sumber, jadi pustakanya **tidak memuat satu pun mod**, walaupun layar Mod Amber menampilkan 20 saklar bawaan dan Compile di dalam game tidak bisa dipakai pada mesin yang diganti namanya (hasilnya akan menimpa pustaka Amber dengan pustaka dari sumber tanpa patch). Sekarang `native_mods=` di `starling-engine.properties` (atau variabel lingkungan `HORIZON_ENGINE_NATIVE_MODS`) berisi id mod, dan tahap **2b** `scripts/build-horizon-engine.sh` mengompilasinya ke dalam pustaka dengan alur yang sama dengan `packaging/android/native_build.sh` milik sumber:
  1. `horizon-mixins` dipasang pada revisi yang dipatok `native_build.sh` (`MIXINS_REV`, `MIXINS_GIT`) lewat `cargo install --locked`.
  2. Sumber yang **sudah diberi patch Amber** disalin tanpa `.git` dan tanpa tautan; selama merencanakan, pustaka `horizon-android` diganti berkas kosong dan atribut bersyarat di `lib.rs` dilepas (modul game dikompilasi dua crate, dan `horizon-mixins` menolak tujuan yang dicapai lewat dua nama; tidak berpengaruh di Android).
  3. Bundel `native-mods/<id>/mixins/<id>` dipatok ke commit yang dibangun, disesuaikan oleh `native_mod_adapt.py`, lalu `horizon-mixins apply` menyisipkannya ke kode game.
  4. Pustaka dikompilasi dengan perintah, flag, dan halaman 16 KB yang sama dengan `build_library()` (`AMBER_EMBEDDED=1` diatur saat kompilasi untuk patch `0003`).
  5. Daftar mod yang berhasil ditulis ke `assets/native-mods/compiled.txt` (lewat `STARLING_APK_NATIVE_MODS`, fungsi `build_apk.assets()` sumber). Mesin membacanya lewat `StarlingActivity` dan memakai pustaka APK sendiri tanpa kompilasi di HP bila semua mod yang diminta ada di daftar itu; Amber membacanya lewat `StarlingEngine.compiledNativeMods()` supaya layar Mod hanya menampilkan saklar bagi modul yang benar-benar ada di pustakanya.
  Id harus ada di `BUILTIN_NATIVE_MODS` sumber (diperiksa di awal build). Mengubah daftar mengubah tanda Release (`-m<6 hex>`), jadi mesin baru dibangun. `StarlingEngine.start()` juga menghapus pilihan pustaka lama (`files/starling/native/current`) sebelum PLAY, supaya yang jalan selalu pustaka APK.
  - **Lima mod tidak dimasukkan** karena catatan sumber sendiri (`docs/native-mods-android-port.md`) menyebut arsipnya tanpa lisensi dan belum boleh disebarkan: `veinminer`, `appleskin`, `rightharvest`, `customhud`, `carryblock`. Tambahkan idnya ke `native_mods` bila sudah ada izin dari pembuatnya.
  - **Gagal lembut.** Bila tahap 2b gagal (pemasangan `horizon-mixins`, penyisipan, atau kompilasi), build jatuh ke pustaka tanpa mod, memberi `::warning`, dan menulis kegagalannya di ringkasan dan catatan Release. Mesin tetap bisa dimainkan, dan tanpa `compiled.txt` Amber menyembunyikan saklar bawaan. Release yang sudah ada dilewati, jadi untuk mencoba lagi setelah memperbaiki: jalankan ulang dengan *mesin = ulang* atau naikkan `revision=`.
  - **Belum diuji.** Tahap 2b dan patch `0003` disusun tanpa bisa dikompilasi (sandbox pengembangan tidak bisa mengakses crates.io dan GitHub): patch hanya dicek dapat diterapkan pada commit yang dipatok, dan perintah `horizon-mixins` mengikuti `native_build.sh` sumber. Hasil run CI pertama yang menentukan; log tahap 2b adalah bahan untuk memperbaikinya.

**Pengaturan Amber untuk Starling (`StarlingSettingsSync`).** Tab Pengaturan > Rust dulu hanya menulis `android-launcher.json` milik Horizon, yang tidak dibaca Starling. Saat PLAY, `StarlingEngine.start()` memanggil `StarlingSettingsSync.sync()`, yang menerjemahkan pilihan Amber yang punya padanan ke `horizon-graphics.txt` (satu baris `kunci:nilai` di folder game, format di `graphics.rs` sumber): Renderer → `graphicsApi` (kompatibel = `auto`, Vulkan cepat = `vulkan`, OpenGL ES = `opengl`); Skala resolusi → `resolutionScale`; Batas pekerja chunk → `workers`; Mode performa Hemat baterai → `batteryMode`; Kurangi animasi game → `animatedTextures` (di Starling hanya tekstur beranimasi, tidak termasuk fade chunk, goyang kamera, dan blur menu); Sederhanakan daun → `leafCulling` (`solid` atau `off`). Rekam performa diteruskan sebagai `AMBER_PERF_RECORD=1` di `horizon-args.txt` (dibaca patch `0004`; lihat bagian log di bawah). Pilihan lain (arah layar, laju layar, immersive, poni, rasio, server otomatis, unggah chunk cepat, cache RAM, petunjuk performa, perangkat input) diatur kelas Java dan peluncur milik Horizon, atau tidak punya padanan yang dipastikan di Starling, jadi belum diteruskan. **Aturan menimpa:** pemain juga bisa mengubah nilai yang sama dari menu di dalam game, jadi Amber tidak menimpa tiap PLAY. Nilai terakhir yang Amber tulis dicatat di `amber-applied.json` (folder game); sebuah kunci hanya ditulis ulang bila pilihannya di Amber berubah sejak itu. Pada PLAY pertama hanya pilihan yang berbeda dari bawaan Amber yang ditulis, jadi pemain yang tidak pernah menyentuh tab itu tidak kehilangan apa pun di Starling. Kunci lain di `horizon-graphics.txt` (preset, HDR, shader, dan seterusnya) tidak disentuh. Berkas ditulis lewat berkas sementara lalu dipindah, dan setiap kegagalan hanya dicatat di log: Starling lalu memakai pengaturannya sendiri. **Belum diuji di HP** (nama kunci dan nilainya dibaca dari kode sumber yang dipatok, bukan dari mesin yang berjalan); bila Starling mengabaikan sebuah kunci, `horizon-graphics.txt` di `files/starling` akan memperlihatkannya.

**Log dan diagnostik Starling di Amber.** Mesin berjalan di proses `:starling` dan menulis sendiri: `logs/latest.log` dan `crash-reports/` di folder game, serta `performance/` di folder data mesin (`current-session.json` dan `previous-session.json` dengan fase terakhir, `clean_exit`, dan memori; `diagnostic.log`; `horizon-performance-*.jsonl` bila rekaman dinyalakan). Dulu tidak satu pun sampai ke Amber. Sekarang `StarlingEngine.start()` mencatat sesi ke `GameLogArchive` (`beginSession` dengan `engine=starling`; sesi sebelumnya, Java atau Starling, disimpan dulu bila prosesnya sudah berhenti), dan `archiveSession` menyusun laporan `amber-log_<waktu>_<instance>.txt` yang muncul di Pengaturan > Log game dan bisa dibagikan seperti log Java. Isinya: ringkasan HP dan pengaturan Rust Amber; kejadian launcher; **sebab Android menghentikan proses `:starling`** (`getHistoricalProcessExitReasons`, Android 11 ke atas: LOW_MEMORY berarti HP kehabisan RAM, CRASH_NATIVE crash di kode Rust atau driver, EXIT_SELF keluar biasa); keadaan sesi dari mesin; `horizon-graphics.txt`, `horizon-extras.txt`, `horizon-mods.txt`, `amber-applied.json`, dan kunci grafik di `options.txt`; ringkasan rekaman performa (jumlah sampel, kejadian lambat, tiga ringkasan frame terakhir; berkas lengkapnya dibagikan dari Pengaturan > Rust > Bagikan laporan performa, yang kini juga mencari di folder Starling); akhir `diagnostic.log`; laporan crash yang terbit selama sesi; `logs/latest.log` (maksimal 4 MiB, awal dan akhir); dan peringatan logcat milik aplikasi. Sesi disimpan walau `latest.log` kosong, karena justru saat itulah sebab berhentinya yang berguna. Sesi yang prosesnya masih berjalan tidak disimpan dulu (PLAY lagi hanya membawa mesin ke depan; sesinya tetap satu). Laporan launcher biasa juga memuat bagian "Mesin Starling". Log disamarkan oleh `LogRedactor` seperti log lain. Catatan: bagian logcat dilewati bila laporan disusun di thread utama (saat PLAY berikutnya); buka Pengaturan > Log game untuk menyimpan ulang dengan logcat. **Belum diuji di HP.**

**Game keluar sendiri di HP lambat (`0006`).** Laporan Amber pertama dari HP nyata (Adreno 610, RAM 3,5 GB) menunjukkan game kembali ke layar utama tanpa pesan: sebab proses `SIGNALED status=9` dengan deskripsi `exit_self_<pid>_<uid>` (proses mematikan dirinya sendiri), fase `world_loading`, `clean_exit:false`, dan di `latest.log` satu baris `panicked at crates/horizon-client/src/game.rs:2023:25: world resources joined before entry`. Itu bukan kehabisan RAM atau crash GPU. Menu Starling memang tampil sebelum sumber daya dunia (item, resep, mob) selesai dimuat oleh thread latar `world-rest-loader`, tetapi ada kode yang meminta data itu tanpa memeriksa dulu (`Resources::rest()` memanggil `expect`). Di HP cepat loader sudah selesai sebelum frame pertama; di HP lambat, dan terutama dengan `workers:1` (Batas pekerja chunk = 1, yang membuat pemuatan awal tidak paralel: `parallel = threads > 1` di `load_resources`), loader masih jalan dan mesin panic. Panic di thread render yang tidak bernama hanya ditulis ke log, tanpa berkas `crash-reports/`, dan prosesnya lalu berhenti sendiri. `0006` mengubah `Resources::rest()`: bila data belum ada, ia menunggu loader (polling 5 ms, seperti `join_world_rest` saat masuk dunia) lalu lanjut, dan hanya panic seperti semula bila loader sudah berhenti tanpa mengisinya (gagal). Penghitung `REST_LOADERS_RUNNING` (dipegang thread loader lewat penjaga `Drop`) membedakan "masih jalan" dari "gagal"; thread loader sendiri tidak pernah menunggu dirinya. Baris `Amber: world resources asked for at <berkas:baris:kolom> before the loader finished` di `latest.log` menyebut pemanggil yang terlalu cepat (atribut `#[track_caller]` ada di semua pengakses `Resources` dan `Game::items/recipes/stations`), supaya pemanggil itu nanti diberi penjaga `rest_ready()` sendiri dan layar judul tidak membeku selama menunggu. Saran bagi pemain: biarkan Batas pekerja chunk di Tanpa batas (Starling memilih jumlah pekerja sendiri, cores dikurangi 2 di HP) atau pakai 3 ke atas; di Starling angka itu jumlah pekerja yang dipakai persis, bukan sekadar batas atas. **Belum diuji di HP.**

**Akun tanpa patch Rust.** `StarlingEngine` memakai antarmuka peluncur yang sudah ada di mesin: PLAY menulis `accounts/accounts.json` (pemain offline), `amber-session.json` (sesi Microsoft, dibaca sekali lalu dihapus mesin), dan `horizon-args.txt` (`--session-file`, `AMBER_EMBEDDED=1`).

**Unduhan berkas resmi lewat launcher (`StarlingFiles`).** Tombol UNDUH di Home (dan otomatis begitu instance Starling dibuat) mengunduh berkas resmi Minecraft 26.3 (client, server, library, aset; sekitar 500 MiB) dengan dialog unduhan Amber, bukan layar Starling. Kotlin mengerjakan langkah yang sama dengan `accounts::download_files()` di sumber Starling: berkas versi diambil dari piston-meta dengan hash yang dipatok, sidik jari jar client dan server harus sama dengan yang dipatok, library disaring untuk Linux, aset dari `resources.download.minecraft.net`, semuanya ditaruh di `files/starling/minecraft/` dengan tata letak launcher resmi, dan `starling-files.json` ditulis paling akhir dengan isi yang sama dengan buatan mesin. Begitu penanda itu ada, mesin menganggap berkasnya siap dan langsung menyiapkan game di HP tanpa menanyakan akun atau mengunduh lagi. Unduhan bisa dilanjutkan (berkas yang sudah benar dilewati). Patokannya ada di `app/src/main/assets/starling-pins.json`, salinan blok `minecraft` dari `research-lock.json` sumber Starling; `scripts/build-horizon-engine.sh` menghentikan build Starling bila salinan itu beda dengan sumber yang dibangun, jadi **saat `commit=` di `starling-engine.properties` dinaikkan, salin ulang patokannya**. Tanpa berkas patokan (atau bila versinya bukan 26.3), launcher tidak mengunduh dan mesin mengunduh sendiri seperti sebelumnya.

**Yang belum (dan perlu diketahui):**
- Penyiapan game di HP sesudah unduhan (unduh Java 25, buka bungkusannya, jalankan `horizon-prepare` atas berkas Mojang menjadi folder resources; sekali saja) masih memakai layar Starling. Menu, editor kontrol, dan pengaturan di dalam game juga masih milik Starling. Pengaturan Rust di tab Pengaturan Amber sebagian sudah berlaku untuk Starling (lihat di atas); tata letak tombol, tema, dan makro masih hanya untuk Horizon.
- Layar Mod Amber memperlakukan Starling seperti Horizon untuk shader dan resource pack: folder `shaderpacks` dan `resourcepacks` serta kunci `pickedShaderPack` dan `resourcePacks` sama di kedua mesin (satu garis keturunan). **Mod berbeda**, karena starling-extras sudah berubah dari Horizon (`HorizonMods.kt` mengenali Starling dari nama folder datanya, `files/starling`):
  - **Modul bawaan**: 20 modul native yang dikenal sumber (`ANDROID_BUNDLED` di `mod_list.rs`, sama dengan `BUILTIN_NATIVE_MODS` di `build_apk.py`): zoom, recipes, smartf3, freecam_plus, connected_textures, armorhud, jade, basic_hud, appleskin, visuality_horizon, punchy, freelook, cinematic_interactions, firstpersonmodel, eatinganimation, veinminer, liteminer, rightharvest, customhud, carryblock. **Yang benar-benar ada di pustaka hanya yang dikompilasi oleh build Amber** (`native_mods`, lihat di atas); layar Mod menampilkan saklar hanya untuk itu. Semuanya mati sampai dihidupkan (baris `mod.<id>:true` di `horizon-mods.txt`, sama seperti Horizon). Minimap dan worldmap sudah dihapus di sana. Judul di Amber diturunkan dari id; nama aslinya ada di kode tiap mod.
  - **Mod WebAssembly tidak jalan lagi.** `.hmod` yang membawa `mod.wasm` ditolak dengan pesan yang jelas; yang diterima: `.hmod` konten (kemampuan `registries`) dan `.hmod` yang membawa kode native (`horizon-native.json`).
  - **Mod native `.hnmod`**: zip berisi `horizon-native.json` dan mixin (kode sumber yang menambal game sebelum dikompilasi). Amber memasangnya ke `horizon-native-mods/<id>.hnmod`, setelah memeriksanya seperti mesin (`Bundle::read`: skema 1, id `[a-z0-9_]{1,64}`, dua revisi 40 digit hex, tanpa kunci asing, path aman, tanpa `mod.wasm`, paling banyak 512 berkas dan 64 MB setelah dibuka). Mod itu **tidak ikut di game** lewat HP: Compile di dalam game (gateway GitHub Actions milik Starling) dimatikan oleh patch `0003` karena akan menimpa pustaka Amber dengan buatan sumber tanpa patch. Mod native masuk ke game hanya lewat build mesin Amber (`native_mods`), jadi `.hnmod` yang dipasang di sini hanya disimpan. Di daftar Terpasang mod native hanya bisa dihapus, tidak ada saklarnya.
- Fitur mod native Starling yang mencari `libhorizon_android.so` memakai nama lama; pustakanya diganti nama di Amber. Karena Compile sudah dimatikan (`0003`), bagian itu tidak terpakai.
- Diagnostik Starling membaca folder data biasa, bukan `files/starling`. Mesin 32-bit belum ada.
- Fork itu ditulis tangan pihak ketiga, dan `SYNC.md`-nya menyebut hasil penggabungannya belum dijalankan di HP. Commit dipatok; periksa diff-nya sebelum mengganti `commit=`. Pemeriksaan awal terhadap upstream hanya menemukan dua URL baru di kode Rust (penunjuk server kompilasi mod native di repo fork, dan tautan donasi). Itu bukan audit.
- Build mesin Starling belum pernah dijalankan di CI dan belum diuji di perangkat. Run pertama bisa gagal dan perlu diperbaiki berdasarkan ringkasan run.
- `StarlingFiles` ditulis dari membaca sumber Starling dan belum dijalankan di perangkat. Bila mesin menolak berkas hasil unduhan launcher, kirim log-nya; jalan keluar sementara: build tanpa `assets/starling-pins.json` mengembalikan unduhan ke mesin.

## Kontrol Amber di Starling

Tombol layar Starling memakai **tata letak dan tampilan editor kontrol Amber**, tetapi digambar dan diproses oleh mesin sendiri, sama seperti di Horizon. Tidak ada jendela Android tambahan di atas game.

- **Amber** (`core/horizon/RustTouchLayout.kt`): saat PLAY dengan "Kontrol Amber" menyala, tata letak preset kontrol instance diterjemahkan ke `files/starling/horizon-touch.json` (gaya `goy-control`: joystick dan tombol kustom). Berkas lama disalin ke `horizon-touch.amber-backup.json` dan dikembalikan saat saklar dimatikan. Starling tidak punya pemutar makro Amber, jadi tombol makro (selain kombo tombol) dilewati.
- **Mesin**: tidak ada perubahan Rust untuk ini. Membuka atau menutup menu hanya mengubah tombol mana yang tampil (`visible_in_game` dan `visible_in_menu`), jadi tidak ada yang dibuat ulang. Geser kamera, hotbar, dan sentuhan di menu ditangani mesin langsung. Kecepatan putar kamera memakai setelan mesin, bukan setelan kursor Amber.
- **Jalur lama (tidak dipakai lagi)**: lapisan Amber di jendela terpisah (`starling/StarlingOverlay.kt`, `AMBER_CONTROLS=1`, patch `amber5/0005-amber-controls.diff`) dihentikan: jendela tambahan di atas permukaan Vulkan membebani GPU, tombol dibuat ulang tiap menu dibuka atau ditutup, dan Android menampilkan peringatan "menampilkan di atas aplikasi lainnya". Kodenya masih ada tetapi tidak dipasang, karena `AMBER_CONTROLS` tidak ditulis lagi ke `horizon-args.txt`.
- **Dimatikan**: Pengaturan > Rust > "Kontrol Amber" (bawaan mati). Mati = tombol bawaan mesin (tata letak Bedrock) kembali.
- **Belum / diketahui**: makro Amber, papan ketik Amber, rekam layar, FPS dan resolusi di menu belum tersambung ke Starling. Tombol menu cepat mesin (garis tiga) mengikuti "Menu garis tiga". Belum diuji di perangkat.
