# Catatan perubahan

Tiap patch membawa satu berkas catatan di `changelog/entries/`, jadi saat rilis tidak ada yang perlu ditulis lagi.
Saat build, `scripts/release-notes.py` mengumpulkan semua catatan yang ditambahkan sejak rilis sebelumnya, lalu:

- menulisnya ke isi Release GitHub (butir bahasa Inggris, plus blok tersembunyi berisi semua bahasa), dan
- membundelnya ke APK (`assets/whatsnew.json`) untuk halaman dan dialog "Yang baru" di aplikasi.

Langkahnya ada di `docs/workflows/build-apk.yml` (salin ke `.github/workflows/build-apk.yml`, satu langkah manual karena patch tidak boleh mengubah `.github/workflows`). Tanpa langkah itu Release tetap dibuat dari judul commit seperti dulu.

Aplikasi menampilkannya dalam bahasa launcher. Bahasa yang belum ditulis memakai Inggris, lalu Indonesia.

## Format

Nama berkas: `<tanggal>-<topik>.txt`, misalnya `20261118-catatan-rilis.txt`. Huruf, angka, titik, minus, dan garis bawah saja.

```
[id]
+ Fitur baru ditulis dengan awalan plus.
~ Peningkatan memakai tilde.
! Perbaikan memakai tanda seru.
[en]
+ New feature.
~ Improvement.
! Fix.
```

- Satu baris satu butir, kalimat pendek yang dipahami pengguna (bukan nama berkas atau istilah kode).
- Bagian bahasa: `[id] [en] [es] [fil] [pt] [th] [vi] [zh]`. Minimal `[id]` dan `[en]`.
- Baris kosong dan baris berawalan `#` diabaikan.
- Patch yang hanya mengubah dokumen tidak perlu catatan.

## Judul commit dan Discord

Daftar commit GitHub dan saluran Discord yang memakai webhook GitHub hanya menampilkan judul commit. Karena itu commit bot yang menyimpan patch memakai judul dari catatan patch itu, bukan lagi tulisan tetap "Terapkan patch (build lolos)". Judulnya diambil dari baris `# judul: ...` di paling atas berkas catatan (boleh dikosongkan; bila kosong dipakai kalimat pertama butir pertama), lalu semua butir ikut di badan commit:

```
# judul: Perbaiki crash GLFW saat game dimulai
[id]
! ...
```

Untuk pesan yang lebih lengkap di Discord, buat webhook di saluran yang diinginkan (Pengaturan Saluran > Integrasi > Webhook > Webhook Baru > Salin URL Webhook), lalu simpan URL itu di repo sebagai Secrets bernama `DISCORD_CHANGELOG_WEBHOOK` (Settings > Secrets and variables > Actions). Setelah build lolos, workflow mengirim satu pesan berisi butir Baru, Peningkatan, dan Perbaikan: untuk build uji hanya catatan dari patch itu, untuk rilis semua catatan sejak rilis sebelumnya. Tanpa Secrets itu langkahnya dilewati, dan gagal kirim tidak pernah menggagalkan build. Skripnya `scripts/discord-notes.py` (bahasa pesan diubah dengan `--lang`, bawaannya `id`). Webhook GitHub yang lama boleh tetap dipakai berdampingan; matikan centang Pushes di webhook itu bila tidak ingin pesan ganda.
