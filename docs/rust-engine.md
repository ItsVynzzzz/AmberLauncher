# Mesin Rust (Starling) di AmberLauncher

Pilihan **Starling** di Buat Instance menjalankan Minecraft Java 26.3 yang ditulis ulang dalam Rust: [Starling](https://github.com/starling-client/starling) (MIT), penerus Horizon Android. Tidak ada JVM, `client.jar`, atau mod Fabric/Forge. Slot "Horizon" yang lama sudah dihapus; instance lamanya dijalankan Starling.

Status: **eksperimental, belum diuji di perangkat.**

## Di HP

- Android 9 ke atas. arm64; APK 32-bit memakai mesin armeabi-v7a bila berhasil dibangun (eksperimental, tanpa mod native dan driver GPU custom).
- Akun offline dan Microsoft diambil dari launcher. PLAY menulis `accounts/accounts.json`, `horizon-args.txt`, dan `launcher-play` di `files/starling`, jadi game langsung terbuka.
- Berkas resmi Minecraft (sekitar 500 MiB) diunduh launcher lewat UNDUH (patokan: `assets/starling-pins.json`).
- Pengaturan > Rust ditulis ke `android-launcher.json` dan `horizon-graphics.txt`. "Kontrol Amber" ditulis ke `horizon-touch.json`.
- Data Horizon lama (`files/horizon`) disalin sekali ke Starling dan tidak dihapus (`HorizonImport.kt`).

## Cara membangun

Tidak ada yang perlu diurus manual: workflow Build APK menghitung tanda Release mesin (`scripts/build-horizon-engine.sh --print-tag`); bila belum ada, job mesin membangunnya, lalu Gradle (`fetchHorizonEngine`) mengambilnya. Bila gagal, APK tetap dibangun tanpa mesin Rust.

Konfigurasi ada di `horizon-engine.properties` (nama dipertahankan untuk workflow):

| Kunci | Arti |
|---|---|
| `source_repo`, `commit` | sumber resmi Starling yang dipatok |
| `profile` | `fast` (sama dengan rilis resmi) atau `release` |
| `patch_set` | folder perubahan Amber di `third_party/horizon-patches/` |
| `revision` | naikkan untuk memaksa bangun ulang |
| `mods_repo`, `mods_commit`, `native_mods` | bundel mod native yang dikompilasi ke dalam mesin |

Mengganti `commit` berarti juga memperbarui `app/src/main/assets/starling-pins.json` dari `research-lock.json` sumbernya (build berhenti bila beda), dan membuat ulang patch yang tidak lagi cocok.

Urutan build: sumber dan patch, alat (Rust dari `tools/ci/rust-version`, cargo-ndk 3.5.4, swap), aset, pustaka arm64 tanpa mod, mod native, terbit, lalu 32-bit. Tahap mod dan 32-bit dibatasi waktu dan tidak bisa menggagalkan mesin arm64.

### Mod native

Dikompilasi dengan `packaging/android/native_build.sh` (horizon-mixins). Bila gabungannya gagal, build memeriksa mod per kelompok, membuang yang bermasalah, dan menuliskannya di ringkasan run. Yang berhasil dicatat di `assets/native-mods/compiled.txt`; mod bawaan mati sampai dinyalakan.

## Perubahan Amber (`amber6`)

| Patch | Isi |
|---|---|
| 0001 | kelas Java: R dan Discord Rich Presence milik Amber, data di `files/starling` |
| 0002 | pemberitahuan pembaruan Starling mati |
| 0003 | Compile mod native lewat server pihak ketiga mati |
| 0004 | Rekam performa dari launcher |
| 0005 | patuh pada pilihan launcher |
| 0006 | penantian untuk mod native |
| 0007 | sidebar Amber |
| 0008 | pemutar makro Amber |
| 0009 | dok editor kontrol |
| 0010 | Keluar dari game menyimpan dunia; tombol Launcher kembali ke Amber |
| 0011 | mod bawaan mati sampai dinyalakan |
| 0012 | `native_build.sh`: mode periksa untuk mencari mod yang gagal |

## Memeriksa di komputer

```bash
scripts/build-horizon-engine.sh --print-tag      # tanda Release untuk konfigurasi sekarang
scripts/fetch-horizon-engine.sh                  # pasang mesin dari Release
scripts/fetch-horizon-engine.sh --remove         # lepas lagi
```
