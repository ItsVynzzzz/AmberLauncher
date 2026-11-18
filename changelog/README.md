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
