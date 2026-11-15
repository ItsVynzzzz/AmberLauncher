# Discord Rich Presence

Opsional dan **mati bawaan**: pemain menyalakannya di **Pengaturan > Discord**. Saat game berjalan, profil Discord
menampilkan "Bermain AmberLauncher" beserta versi Minecraft, loader, dan waktu bermain. Nama instance dan akun
pemain tidak ikut dikirim.

## Cara kerja

- Memakai Discord Social SDK resmi lewat jalur Rich Presence **tanpa login**: presence dikirim ke aplikasi Discord yang
  terpasang di HP yang sama. Tidak ada token atau kata sandi akun Discord yang dipakai atau disimpan.
- Kode: `discord/DiscordPresence.kt` (satu thread kerja berprioritas latar belakang), jembatan JNI
  `cpp/discord_bridge.cpp`, dan `cpp/CMakeLists.txt` (64-bit) atau `cpp/amber_discord.mk` (32-bit, ndk-build).
- Hemat: tanpa aplikasi Discord di HP, tidak ada thread dan tidak ada library yang dimuat. Waktu bermain dihitung
  Discord sendiri, jadi tidak ada pembaruan berkala. Callback SDK berjalan tiap 0,5 detik sampai presence pertama
  berhasil, lalu tiap 5 detik. Bila tak berhasil dalam 60 detik (mis. Discord belum login), semuanya dihentikan.
- Application ID (bukan rahasia) ada di `DiscordPresence.APP_ID`.

## SDK tidak ada di repo

Lisensi SDK mengizinkan menyertakannya di dalam aplikasi, tetapi tidak mengizinkan membagikan ulang SDK itu sendiri,
jadi `discord_partner_sdk.aar` **tidak boleh** dimasukkan ke repo publik (sudah ada di `.gitignore`).

Workflow `build-apk.yml` mengunduhnya saat build:

1. Taruh `discord_partner_sdk.aar` di akar repo privat `<pemilik>/amber-private-libs`.
2. Buat token GitHub fine-grained dengan akses hanya ke repo itu dan izin **Contents: read-only**.
3. Simpan token sebagai Secrets `SDK_TOKEN` di repo ini.

Tanpa `SDK_TOKEN`, atau bila unduhan gagal, APK dibangun tanpa fitur Discord (barisnya di Pengaturan juga tidak
tampil). Bila build dengan SDK gagal, workflow dan `scripts/build-arm32.sh` otomatis membangun ulang tanpa SDK supaya
APK tetap terbit; kesalahan pertamanya ada di ringkasan run (64-bit) atau log langkah 32-bit.

## Ketentuan yang perlu diingat

Social SDK Terms Discord antara lain melarang mendukung pengguna di bawah 13 tahun untuk fitur integrasinya, dan memberi
Discord hak meninjau implementasi. Tulisan di Pengaturan sudah menyebut batas usia itu.
