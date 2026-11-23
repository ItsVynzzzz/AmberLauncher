# Discord Rich Presence

Opsional dan **mati bawaan**: pemain menyalakannya di **Pengaturan > Tampilan > Discord**. Saat game berjalan, profil Discord
menampilkan "Bermain AmberLauncher" beserta versi Minecraft, loader, dan waktu bermain. Nama instance dan akun
pemain tidak ikut dikirim.

## Cara kerja

- Memakai Discord Social SDK resmi lewat jalur Rich Presence **tanpa login**: presence dikirim ke aplikasi Discord yang
  terpasang di HP yang sama. Tidak ada token atau kata sandi akun Discord yang dipakai atau disimpan.
- Kode: `discord/DiscordPresence.kt` (satu thread kerja berprioritas latar belakang), jembatan JNI
  `cpp/discord_bridge.cpp`, dan `cpp/CMakeLists.txt` (64-bit) atau `cpp/amber_discord.mk` (32-bit, ndk-build).
- Hemat: tanpa aplikasi Discord di HP, tidak ada thread dan tidak ada library yang dimuat. Waktu bermain dihitung
  Discord sendiri dari waktu mulai, jadi tidak ada pembaruan untuk itu.
- Bertahan lama (`discord/DiscordPresenceEngine.kt`, diuji dengan waktu virtual): koneksi ke aplikasi Discord sering gagal
  saat game baru dibuka dan bisa putus di tengah sesi, dan presence yang hanya dikirim sekali ikut hilang. Karena itu
  presence dikirim ulang tiap 15 detik sampai Discord menjawab ok, dikirim ulang segera bila SDK melaporkan koneksi putus,
  dan dikirim ulang tiap 5 menit sebagai jaring pengaman. Callback SDK berjalan tiap 1 detik.
- Hemat saat Discord tidak ada: bila tak tersambung dalam 3 menit, klien SDK dilepas sama sekali (tanpa thread SDK
  dan tanpa percobaan sambung) lalu dicoba lagi 10 menit kemudian, jadi Discord yang mati tidak dibangunkan terus-menerus
  saat game butuh memori. Presence tetap menyusul begitu Discord siap.
- Application ID (bukan rahasia) ada di `DiscordPresence.APP_ID`.

## Java dan mesin Rust

- **Loader Java**: `GameActivity` (proses `:game`) menyalakan presence saat game dibuka dan menghapusnya saat berhenti.
- **Mesin Rust (Horizon)**: `dev/horizon/android/HorizonActivity.java` (proses `:horizon`) melakukan hal yang sama.
  Karena proses itu tidak membaca pengaturan launcher, pilihan pemain dititipkan lewat Intent (`EXTRA_DISCORD_RPC`)
  oleh `HorizonEngine.start`. Mesin memanggil `process::exit` saat selesai, jadi `onDestroy` tidak selalu sempat
  jalan; Discord sendiri menghapus presence begitu prosesnya mati.

## Presence di launcher

Toggle terpisah "Perlihatkan aktivitas di launcher" (mati bawaan) menampilkan "Getting ready to play" selama MainActivity di
depan (`discord/DiscordLauncherPresence.kt`, proses utama). Saat layar launcher ditinggalkan (`onPause`, mis. game
dibuka), presence dihapus dan klien SDK dilepas dengan segera, supaya tidak bentrok dengan presence game. Bila proses
`:game` atau `:horizon` masih hidup di latar, presence launcher dilewati.

## Tampilan, bahasa, dan gambar

Isi dan susunan teks dirakit di `discord/DiscordPresenceComposer.kt` (tanpa Android, jadi seluruh kombinasinya diuji):

- Baris atas: nama instance (hanya bila "Tampilkan nama instance" menyala) atau "Minecraft 1.21.11 · Fabric".
- Baris bawah: "On server: mc.contoh.com" (bila "Tampilkan server yang dimainkan" menyala), "Singleplayer", atau, bila nama
  instance tampil, "Minecraft 1.21.11 · Fabric".
- Gambar besar: ikon server (bila alamat server tampil dan "Tampilkan ikon server" menyala), dengan logo AmberLauncher
  sebagai gambar kecil. Selain itu logo AmberLauncher jadi gambar besar.
- Waktu bermain dihitung Discord dari waktu mulai.

**Bahasa.** Teks mengikuti bahasa pemain, karena tampil di profil Discord orang lain: bahasa yang dipilih di Pengaturan >
Tampilan, atau bahasa HP bila "Ikuti sistem". Ada 21 bahasa di `discord/DiscordPresenceText.kt`; bahasa lain jatuh ke bahasa
Inggris. Terjemahan ditulis oleh AI dan belum ditinjau penutur asli, jadi perbaiki langsung di tabel itu bila ada yang janggal.
Nama game, versi, dan loader tidak diterjemahkan. Catatan di log launcher tetap bahasa Indonesia.

**Gambar lewat URL, bukan aset.** SDK menerima URL gambar, jadi tidak ada yang perlu diunggah di Developer Portal:
logo diambil dari repo publik (`PresenceComposer.LOGO_URL`, jangan pindahkan berkas ikonnya tanpa mengubah konstanta itu), dan ikon
server dari layanan pihak ketiga mcsrvstat.us (`PresenceComposer.SERVER_ICON_URL`) memakai alamat servernya. Discord yang
mengambil gambarnya, bukan launcher. Server tanpa ikon bisa menampilkan gambar bawaan layanan itu. Bila layanan itu mati atau
berubah, gambar server tidak muncul tetapi teksnya tetap tampil.

**Server.** Launcher tidak memilihkan server (pemain memilihnya di menu Minecraft), jadi alamatnya dibaca dari baris log game
(`discord/DiscordServerLog.kt`): "Connecting to host, port" untuk masuk, "Starting integrated minecraft server" untuk
singleplayer, serta "Client disconnected with reason" dan "Stopping singleplayer server" untuk keluar. Minecraft tidak
mencatat saat pemain keluar dari server ke menu dengan sengaja, jadi alamat terakhir bisa bertahan sampai pemain masuk server
lain atau membuka dunia singleplayer. Alamat jaringan lokal (LAN, localhost, IPv6) tidak pernah ditampilkan atau dipakai untuk
ikon. Mesin Rust belum mendukung server dan nama instance.

## Melacak presence yang tidak muncul

Tiap langkah (SDK siap, klien dibuat, presence diminta, hasil dari Discord, alasan dilewati) dicatat di log launcher
dengan tag "Discord" (Pengaturan > Tentang > Log game > bagikan). Baris ditulis oleh proses yang bersangkutan
(`main`, `game`, `horizon`), jadi terlihat di proses mana presence berhenti.

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
