# Mesin Rust (Horizon) di AmberLauncher

Pilihan **Rust** di layar Buat Instance menjalankan Minecraft lewat **Horizon**, yaitu Minecraft Java yang ditulis ulang dalam Rust (bukan Java), memakai port Android buatan Vasysik. Cara pakainya sama seperti Fabric, Forge, atau NeoForge: buat instance, lalu tekan PLAY.

Status: **tahap 1, eksperimental, belum diuji di perangkat.**

## Apa ini, dan apa bukan

- Mesin ini **menggantikan seluruh client Java**. Tidak ada JVM, `client.jar`, atau library Java, jadi mod Fabric, Forge, NeoForge, dan mod Java apa pun **tidak bisa dipakai**. Horizon punya format mod native sendiri (`.hmod`), tidak bisa dipasang lewat layar Mod launcher.
- Hanya Minecraft **26.3** (versi yang dipatok proyek Horizon). Satu-satunya versi di daftar.
- Sumber daya game (sekitar 500 MiB) diunduh **oleh mesinnya sendiri** dari Mojang saat pertama dibuka. Launcher tidak mengunduhnya.
- Layar peluncur, login Microsoft (kode perangkat), dan dunia permainan diurus UI milik Horizon. **Akun launcher belum dihubungkan** (rencana tahap 2).
- Ini proyek pihak ketiga, tidak berafiliasi dengan Mojang atau Microsoft.

## Syarat perangkat

- Android 9 (API 28) ke atas, HP 64-bit (arm64). Selain itu opsinya ditolak dengan pesan yang jelas.
- RAM disarankan 4 GB ke atas. Di bawah itu tetap boleh dicoba, dengan peringatan.
- Ruang kosong sekitar 500 MiB untuk sumber daya, plus koneksi internet saat pertama dibuka.

## Cara menyertakan mesin di build

Pustaka mesin **tidak disimpan di repo**. Skrip `scripts/fetch-horizon-engine.sh` mengambilnya saat build, dari APK Horizon Android, dan hanya bila kamu mengisi alamat dan sidik jarinya. Tanpa itu build tetap normal dan opsi Rust tampil dengan keterangan "belum disertakan".

### 1. Tambah satu langkah di workflow (sekali, manual)

Berkas di `.github/workflows/` tidak bisa diubah lewat patch, jadi tambahkan lewat editor web GitHub. Letakkan **tepat setelah** langkah `Terapkan patch` dan **sebelum** `Periksa keaslian Gradle wrapper` di `.github/workflows/build-apk.yml`:

```yaml
      # Opsional: menyertakan mesin Rust (Horizon). Tanpa url dan sha256 di horizon-engine.properties langkah ini
      # tidak melakukan apa-apa. continue-on-error: gagal mengambil mesin tidak boleh menggagalkan build biasa.
      - name: Ambil mesin Rust (Horizon, opsional)
        continue-on-error: true
        run: bash scripts/fetch-horizon-engine.sh
```

Bila langkah ini gagal, alasannya muncul sebagai peringatan dan di ringkasan run, dan build jalan terus tanpa mesin.

### 2. Isi `horizon-engine.properties`

1. Buka halaman Releases Horizon Android (`github.com/Vasysik/horizon-android`) dan salin alamat unduhan APK **arm64** (harus `https://`). Isi `url=`.
2. Biarkan `sha256=` kosong, lalu jalankan build sekali. Skrip mengunduh APK dan **tidak memasang apa pun**, hanya mencetak SHA-256 hasil unduhan di ringkasan run.
3. Bandingkan angka itu dengan `SHA256SUMS.txt` di halaman rilis yang sama. Bila cocok dan kamu percaya berkasnya, isi `sha256=` dengan angka tersebut.
4. Jalankan build lagi. Kali ini mesin dipasang, dan ringkasan run mencatat ukuran dua berkasnya.

Bila rilisnya diganti di alamat yang sama, SHA-256 tidak lagi cocok dan mesin tidak dipasang (kode keluar 3). Itu disengaja.

Skrip hanya membaca dua entri bernama pasti dari APK (`lib/arm64-v8a/libhorizon_android.so` dan `assets/engine-data.zip`), memeriksa bahwa pustakanya benar-benar ELF 64-bit arm64 dan data zip-nya utuh, lalu menaruhnya di `app/src/main/jniLibs/arm64-v8a/` dan `app/src/main/assets/`. Tidak ada isi APK yang dijalankan saat build.

### Build di komputer sendiri

```bash
HORIZON_ENGINE_SHA256=<sha256 APK> bash scripts/fetch-horizon-engine.sh --apk /jalur/horizon-android.apk
bash scripts/fetch-horizon-engine.sh --remove   # menghapus berkas mesin dari pohon kerja
```

Variabel `HORIZON_ENGINE_URL` dan `HORIZON_ENGINE_SHA256` menang atas isi `horizon-engine.properties`.

### APK 32-bit

Mesin hanya untuk arm64. `scripts/build-arm32.sh` membuang pustaka dan data mesin dari salinan kerjanya, jadi APK 32-bit tidak menjadi lebih besar.

## Kepercayaan dan keamanan

Pustaka native pihak ketiga yang dikemas ke APK berjalan **dengan hak yang sama dengan launcher** (satu UID Android). Proses `:horizon` terpisah melindungi launcher dari mesin yang mati (`process::exit`), bukan dari kode jahat. Karena itu:

- Pin SHA-256 itu wajib, dan skrip menolak jalan tanpanya.
- Periksa sendiri sumber dan rilis yang kamu patok. Sandbox tempat patch ini disusun tidak bisa mengunduh rilis Horizon Android, jadi **pustakanya belum pernah kuperiksa atau kujalankan**.
- Sebelum menaruh APK berisi mesin ini di rilis publik, pertimbangkan apakah kamu mau bertanggung jawab atas kode itu bagi semua penggunamu.

## Lisensi

- Horizon dan port Android-nya berlisensi **MIT**, Copyright (c) 2026 Ludovic Marie and the Horizon contributors. Salinan `LICENSE` dan `THIRD_PARTY_NOTICES.md` ada di `third_party/horizon/`, dan skrip ikut menaruhnya di `assets/licenses/horizon/` setiap kali mesin dikemas (MIT mensyaratkan pemberitahuannya ikut). Skrip menolak memasang mesin bila kedua berkas itu tidak ada.
- `app/src/main/java/dev/horizon/android/HorizonActivity.java` diadaptasi dari Horizon Android (MIT), dengan perubahan: folder data diarahkan ke subfolder `horizon`.
- MIT kompatibel dengan GPL-3.0 milik AmberLauncher. **Aku bukan pengacara.**
- Satu hal yang belum bisa kupastikan: asal isi `engine-data.zip`. README menyatakan AmberLauncher tidak menyertakan atau membagikan salinan game. Buka zip itu dan baca lisensi serta `THIRD_PARTY_NOTICES.md` Horizon sebelum membagikan build publik berisi mesin ini. Bila isinya ternyata turunan data Minecraft, pernyataan di README perlu disesuaikan, atau mesin tidak ikut di build publik.

## Cara kerja (tahap 1)

| Bagian | Peran |
|---|---|
| `core/horizon/HorizonEngine.kt` | Memeriksa ketersediaan (API 28+, arm64, pustaka ikut terpasang, RAM) dan membuka mesin |
| `dev/horizon/android/HorizonActivity.java` | `NativeActivity` yang memuat `libhorizon_android.so`. Paket dan nama kelas **tidak boleh diganti**: fungsi JNI di pustakanya terikat padanya |
| `AndroidManifest.xml` | Mendaftarkan activity itu di proses `:horizon` |
| `CreateInstanceScreen.kt` | Opsi Rust di rel loader; versi 26.3 terpilih otomatis; Install dimatikan bila mesin tidak tersedia |
| `DashboardScreen.kt`, `GameLauncher.kt` | PLAY pada instance Rust membuka mesin, tanpa cek akun, Java, atau unduhan launcher |
| `InstanceManager.kt` | Instance Rust selalu dianggap sudah diunduh |

Data mesin (dunia, profil, sumber daya, log) ada di `files/horizon/` di dalam data aplikasi, terpisah dari data launcher. Menghapus instance Rust dari launcher **tidak** menghapus data itu karena dipakai bersama; hapus lewat Pengaturan Android > Info aplikasi > Hapus data.

## Daftar uji di perangkat

1. Build dengan mesin terpasang (ringkasan run menulis "Mesin Rust (Horizon) ikut dibangun"), pasang APK-nya.
2. Buat Instance: opsi **Rust** muncul, 26.3 sudah terpilih, tombol Install aktif tanpa peringatan merah.
3. Install, lalu PLAY di Beranda: layar peluncur Horizon terbuka.
4. Login dan unduh sumber daya (sekitar 500 MiB), lalu masuk ke dunia.
5. Keluar dari mesin: launcher harus kembali tampil dan tetap hidup.
6. Instance Fabric, Forge, NeoForge, dan Vanilla yang sudah ada tetap jalan seperti biasa.
7. Pada build **tanpa** mesin: opsi Rust tampil dengan keterangan "belum disertakan" dan Install mati.

Bila gagal, kirim isi log launcher (buku harian) dan logcat dengan filter proses `:horizon`. Kegagalan paling mungkin: `UnsatisfiedLinkError` (nama kelas atau pustaka tidak cocok), layar hitam (Vulkan/GLES tidak didukung), atau game tertutup sendiri (RAM kurang).

## Batasan tahap 1

- Hanya Minecraft 26.3, hanya arm64, API 28 ke atas.
- Tanpa mod Java, tanpa Fabric, Forge, NeoForge, dan layar Mod launcher tidak berlaku untuk instance Rust.
- Akun Horizon terpisah dari akun launcher; unduhan dan UI-nya milik Horizon.
- Belum ada integrasi lebih jauh dengan launcher (dunia dan setelan launcher tidak dibagi).
- Perilaku di perangkat nyata (kinerja, kestabilan, kebutuhan RAM sebenarnya) belum diuji.

## Rencana berikutnya

- **Tahap 2**: patch di sisi Rust (fork Horizon Android) supaya mesin mau menerima instruksi mulai otomatis, kembali ke launcher saat keluar, dan menerima akun dari launcher lewat Intent. Butuh membangun Rust sendiri untuk Android.
- **Tahap 3**: otomatisasi pembaruan, yaitu workflow terjadwal yang memeriksa rilis baru Horizon Android, memverifikasi `SHA256SUMS.txt`-nya, lalu mengajukan perubahan `url=` dan `sha256=` untuk kamu setujui, supaya `horizon-engine.properties` tidak perlu diisi dan diperbarui manual.
