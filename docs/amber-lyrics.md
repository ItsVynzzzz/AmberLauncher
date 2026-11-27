# Amber Lyrics (mod lirik untuk mesin Rust / Starling)

Mod native untuk mesin Rust (Starling) yang menampilkan **lirik tersinkron** dari lagu yang sedang diputar di HP, langsung di layar game. Mod ini **ditulis sendiri untuk proyek ini** dan tidak memakai kode mod Java mana pun. Mod Fabric seperti LyricsZymu tidak bisa dijalankan di mesin Rust (tidak ada JVM), dan lisensinya (All Rights Reserved) tidak mengizinkan kodenya dipindahkan tanpa izin penulisnya, jadi ini fitur baru yang terpisah, bukan port.

Status: **eksperimental.** Sisi mod (parser LRC, sinkronisasi, tata letak, pembacaan berkas) lolos 19 uji unit dan terpasang bersih di atas patch `amber5`. Yang belum pernah diuji di perangkat: pengambilan lirik langsung dari lrclib.net, penerima siaran pemutar musik, dan tampilan di layar game.

## Tanpa izin khusus (dan kenapa)

APK ini **tidak meminta akses notifikasi, aksesibilitas, SMS, atau izin sensitif lain untuk lirik**. Versi awal fitur ini memakai layanan "Akses notifikasi" untuk membaca sesi media Android. Itu dibuang karena Google Play Protect **memblokir pemasangan** APK yang diunduh dari luar Play Store (browser, aplikasi pesan, pengelola berkas) dan mendeklarasikan izin pendengar notifikasi ("This app can request access to sensitive data"; lihat [panduan Google untuk peringatan Play Protect](https://developers.google.com/android/play-protect/warning-dev-guidance)), dan karena izin itu membuat pengguna wajar curiga. Sekarang tidak ada layanan, izin, atau entri manifest tambahan untuk fitur ini.

## Cara kerja

```
pemutar musik --siaran "lagu berganti / jeda / putar"--> penerima siaran (NowPlayingBridge, proses game)
                                                              |
                                  <folder game>/config/amber_lyrics/now_playing.json
                                                              |
                                  mod amber_lyrics di mesin Starling (membaca tiap 1 detik)
                                                              |
                           lrclib.net (judul + artis + durasi) -> lirik LRC -> cache -> HUD
```

1. **Amber** (`core/horizon/NowPlayingBridge.kt`) mendaftarkan penerima siaran biasa **hanya di proses game Starling dan hanya selama game terbuka**. Ia mendengar siaran yang dikirim sebagian pemutar musik: Spotify (`com.spotify.music.metadatachanged` dan `playbackstatechanged`, perlu "Device Broadcast Status" menyala di pengaturan Spotify) dan siaran gaya lama `com.android.music.*` beserta beberapa varian pabrikan. Ia **menulis hanya bila mod Amber Lyrics dihidupkan** (`mod.amber_lyrics:true` di `horizon-mods.txt`). Berkas dihapus saat game ditutup atau saat lagu selesai.
2. **Mod** membaca berkas itu, mencari lirik tersinkron di **lrclib.net** (`/api/get`, lalu `/api/search` bila perlu; hasil dicocokkan dengan durasi lagu, selisih paling lama 10 detik), menyimpannya di `config/amber_lyrics/cache/`, dan menggambarnya di HUD. Lagu yang sama tidak diunduh dua kali.

## Batas yang perlu diketahui

- **Hanya pemutar yang menyiarkan lagunya.** Spotify (dengan Device Broadcast Status) dan pemutar yang memakai siaran gaya `com.android.music.*`. Pemutar yang tidak menyiarkan apa-apa, termasuk YouTube Music, **tidak terbaca**. Daftar pemutar yang cocok tidak diuji satu per satu.
- **Siaran datang saat ada kejadian.** Lagu yang sudah berjalan sebelum game dibuka baru terbaca setelah kamu **menjeda lalu memutar sekali, atau mengganti lagu**.
- Bila siaran tidak membawa posisi, lagu baru dianggap mulai dari 0 detik. Kalau liriknya terlalu cepat atau lambat, geser dengan pengaturan waktu mod.
- Hanya lirik tersinkron yang ada di lrclib.net. Lagu yang tidak ada di sana hanya menampilkan pop-up judul dengan keterangan "(no synced lyrics)". Bila pencarian gagal karena jaringan (bukan karena lagunya tidak ada), mod mencoba lagi setelah 30 detik.
- Pengaturan di dalam game berbahasa Inggris (label mesin).
- Mod dikompilasi saat membangun mesin dengan `cargo install` dari sumber horizon-mixins. Jika sumber itu tidak terjangkau di CI, mesin dibangun **tanpa** mod native dan Amber Lyrics tidak muncul; periksa ringkasan build mesin untuk baris native mods.

## Menyalakan

1. Di Spotify: hidupkan **Device Broadcast Status** di pengaturannya (pemutar lain: tidak ada langkah khusus bila ia memang menyiarkan lagunya).
2. Di layar **Mod** (atau Options > Mods di dalam game), hidupkan **Amber Lyrics**. Bawaannya mati.
3. Masuk game, putar lagu (atau jeda lalu putar sekali, atau ganti lagu bila sudah berjalan).

Pengaturan di halaman mod (di dalam game): posisi (atas/bawah), baris berikutnya, pop-up judul lagu, dan penyesuaian waktu (0, +250, +500, +1000, -1000, -500, -250 ms).

## Format berkas `now_playing.json`

Ditulis penerima siaran Amber, boleh juga ditulis alat lain (misalnya saat menguji):

```json
{
  "title": "Judul lagu",
  "artist": "Artis",
  "album": "Album",
  "duration_ms": 215000,
  "position_ms": 61250,
  "playing": true,
  "updated_at_ms": 1796000000000
}
```

- `title` wajib; berkas tanpa judul dianggap tidak ada lagu.
- `position_ms` dihitung maju oleh mod sejak `updated_at_ms` selama `playing` bernilai `true`, jadi berkas tidak perlu ditulis ulang tiap detik.
- Saat jeda, lirik tetap tampil sekitar 5 detik, lalu hilang. Berkas yang dihapus berarti tidak ada lagu.
- Berkas lebih dari 256 KiB diabaikan.

## Privasi

- Judul, artis, dan durasi lagu dikirim ke **lrclib.net** (layanan lirik terbuka) untuk mencari lirik. Tidak ada data akun, perangkat, atau game yang dikirim.
- Amber tidak menyimpan riwayat lagu. Satu-satunya berkas yang ditulis adalah `now_playing.json`, dan hanya bila mod dihidupkan.
- Tidak ada izin tambahan di APK untuk fitur ini.

## Berkas yang terlibat

| Berkas | Isi |
| --- | --- |
| `third_party/horizon-patches/amber5/0008-amber-lyrics.diff` | Mod native Starling (bundel horizon-mixins) dan daftarnya di mesin |
| `starling-engine.properties` | `native_mods=` memuat `amber_lyrics` agar ikut dikompilasi |
| `core/horizon/NowPlayingBridge.kt` | Penerima siaran pemutar musik yang menulis `now_playing.json` |
| `starling/StarlingOverlay.kt` | Memulai dan menghentikan penerima bersama game |
| `core/horizon/HorizonMods.kt` | Nama mod di daftar Mod |
| `ui/components/RustSettingsSection.kt` | Keterangan di Pengaturan > Rust > Lirik |
