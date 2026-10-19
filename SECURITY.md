# Keamanan

## Memastikan APK asli

Unduh AmberLauncher hanya dari halaman **Releases** repo ini. APK resmi memakai:

- ID aplikasi: `com.itsvynzzzz.amberlauncher`
- Sidik jari sertifikat tanda tangan (SHA-256):
  `B2:29:4B:4E:2B:5E:65:0C:7C:43:B2:30:1F:35:9B:2C:17:83:C4:0C:82:DF:10:D8:F5:69:D0:56:A1:7B:5E:3F`

Cara memeriksanya dari komputer: `apksigner verify --print-certs AmberLauncher.apk`. APK dari tempat lain, atau yang sidik jarinya berbeda, jangan dipasang.

## Melaporkan celah keamanan

Gunakan tab **Security** di repo ini, lalu pilih **Report a vulnerability** agar laporannya tidak terbaca publik. Mohon jangan menaruh rincian teknis celah di Issue terbuka. Sertakan versi aplikasi, langkah yang menimbulkan masalah, dan dampaknya.

## Perlindungan yang ada

- Berkas dari luar (nama berkas, isi modpack, daftar unduhan) diperiksa agar tidak bisa menulis atau menghapus berkas di luar folder instance, dan modpack hanya boleh mengunduh dari sumber HTTPS yang dikenal.
- Unduhan mod dan game dicocokkan dengan sidik jari resmi; unduhan yang terputus tidak ditinggalkan sebagai berkas rusak.
- Server lokal untuk akun offline dan skin hanya menerima sambungan dari HP itu sendiri, dengan batas ukuran dan waktu.
- Aplikasi lain tidak bisa memakai launcher untuk menyalin berkas pribadinya (data akun dan pengaturan) lewat fitur impor.
- Cadangan otomatis Android dimatikan agar data akun tidak ikut tersalin ke luar HP.
- Kunci tanda tangan rilis bersifat pribadi dan hanya ada di GitHub Secrets.

## Batasan yang perlu diketahui

- Mod, modpack, dan shader dari pihak ketiga adalah kode yang dijalankan bersama game. Pasang hanya dari sumber yang dipercaya.
- Data game disimpan di folder `nuxlauncher` pada penyimpanan bersama. Aplikasi lain yang punya izin akses semua berkas di HP bisa membacanya. Data akun tetap di folder privat aplikasi.
- Token akun disimpan di folder privat aplikasi tanpa enkripsi tambahan. Perlindungannya adalah isolasi aplikasi bawaan Android.
