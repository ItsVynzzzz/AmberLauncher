# Amber Lyrics (mod lirik untuk mesin Rust / Starling)

Mod native untuk mesin Rust (Starling) yang menampilkan **lirik tersinkron** dari lagu yang sedang diputar di HP, langsung di layar game. Mod ini **ditulis sendiri untuk proyek ini** dan tidak memakai kode mod Java mana pun. Mod Fabric seperti LyricsZymu tidak bisa dijalankan di mesin Rust (tidak ada JVM), dan lisensinya (All Rights Reserved) tidak mengizinkan kodenya dipindahkan tanpa izin penulisnya, jadi ini fitur baru yang terpisah, bukan port.

Status: **eksperimental.** Sisi mod (parser LRC, sinkronisasi, tata letak, pembacaan berkas) lolos 19 uji unit di mesin pembangun dan terpasang bersih di atas patch `amber5`. Yang belum pernah diuji di perangkat: pengambilan lirik langsung dari lrclib.net, jembatan Android, dan tampilan di layar game.

## Cara kerja

```
pemutar musik  ->  sesi media Android  ->  NowPlayingListenerService (Amber)
                                              |
                          <folder game>/config/amber_lyrics/now_playing.json
                                              |
                       mod amber_lyrics di mesin Starling (poll tiap 1 detik)
                                              |
                    lrclib.net (judul + artis + durasi) -> lirik LRC -> cache -> HUD
```

1. **Amber** (`core/horizon/NowPlayingBridge.kt`) memakai izin "Akses notifikasi" Android **hanya** untuk membaca sesi media: judul, artis, album, durasi, posisi, dan status putar/jeda. Isi notifikasi tidak dibaca. Berkas ditulis hanya saat pemutar berganti lagu, jeda, atau memutar, tanpa pekerjaan terjadwal.
2. **Mod** membaca berkas itu, mencari lirik tersinkron di **lrclib.net** (`/api/get`, lalu `/api/search` bila perlu; hasil dicocokkan dengan durasi lagu, selisih paling lama 10 detik), menyimpannya di `config/amber_lyrics/cache/`, dan menggambarnya di HUD. Lagu yang sama tidak diunduh dua kali.

## Format berkas `now_playing.json`

Ditulis Amber, boleh juga ditulis alat lain (misalnya saat menguji):

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

## Menyalakan

1. Bangun APK lewat **Build APK** seperti biasa. Mesin akan ikut dibangun ulang karena patch `amber5/0008` dan `starling-engine.properties` berubah.
2. Di aplikasi: **Pengaturan > Rust > Lirik > Beri akses**, pilih AmberLauncher di daftar Akses notifikasi Android.
3. Di layar **Mod** (atau menu Options > Mods di dalam game), hidupkan **Amber Lyrics**. Bawaannya mati.
4. Putar lagu di pemutar musik mana pun, lalu masuk game.

Pengaturan di halaman mod (di dalam game): posisi (atas/bawah), baris berikutnya, pop-up judul lagu, dan penyesuaian waktu (0, +250, +500, +1000, -1000, -500, -250 ms) untuk lagu yang liriknya terlalu cepat atau lambat.

## Privasi

- Judul, artis, dan durasi lagu dikirim ke **lrclib.net** (layanan lirik terbuka) untuk mencari lirik. Tidak ada data akun, perangkat, atau game yang dikirim.
- Amber tidak membaca isi notifikasi apa pun dan tidak menyimpan riwayat lagu. Satu-satunya berkas yang ditulis adalah `now_playing.json`, dan berkas itu dihapus saat tidak ada lagu atau akses dicabut.
- Mod berstatus mati sampai dihidupkan, dan tanpa akses notifikasi ia tidak menemukan lagu sehingga tidak mengirim apa pun.
- Pencabutan akses: Pengaturan Android > Akses notifikasi > AmberLauncher.

## Berkas yang terlibat

| Berkas | Isi |
| --- | --- |
| `third_party/horizon-patches/amber5/0008-amber-lyrics.diff` | Mod native Starling (bundel horizon-mixins) dan daftarnya di mesin |
| `starling-engine.properties` | `native_mods=` memuat `amber_lyrics` agar ikut dikompilasi |
| `core/horizon/NowPlayingBridge.kt` | Jembatan sesi media ke `now_playing.json` dan pendengar notifikasi |
| `core/horizon/HorizonMods.kt` | Nama mod di daftar Mod |
| `ui/components/RustSettingsSection.kt` | Grup Lirik di Pengaturan > Rust |

## Batas yang perlu diketahui

- Hanya lirik tersinkron yang ada di lrclib.net. Lagu yang tidak ada di sana hanya menampilkan pop-up judul dengan keterangan "(no synced lyrics)". Bila pencarian gagal karena jaringan (bukan karena lagunya tidak ada), mod mencoba lagi setelah 30 detik.
- Pengaturan di dalam game berbahasa Inggris (label mesin).
- Mod dikompilasi saat membangun mesin dengan `cargo install` dari sumber horizon-mixins. Jika sumber itu tidak terjangkau di CI, mesin dibangun **tanpa** mod native dan Amber Lyrics tidak muncul; periksa ringkasan build mesin untuk baris native mods.
