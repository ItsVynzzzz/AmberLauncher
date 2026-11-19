# Login Microsoft: memakai aplikasi Azure sendiri

Login akun Minecraft (Microsoft) di AmberLauncher berjalan atas nama sebuah **aplikasi Azure**, dikenali lewat *Client ID*.
Aplikasi itu yang menentukan apakah login tetap berfungsi: bila pemiliknya menghapusnya, mengubah pengaturannya, atau
Mojang mencabut persetujuannya, login gagal untuk **semua** pengguna sekaligus, tanpa perubahan apa pun di kode kita.

Bawaan saat ini adalah Client ID `c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb` (warisan dari kode awal). Tidak diketahui siapa
pemiliknya, jadi sebaiknya diganti dengan aplikasi yang kamu kendalikan sendiri. Langkah di bawah dilakukan **sekali**,
dan tidak ada yang berubah di kode: cukup isi Client ID baru di konfigurasi build.

## 1. Daftarkan aplikasi di Azure

1. Buka <https://portal.azure.com> dengan akun Microsoft, lalu cari **Microsoft Entra ID** > **App registrations** > **New registration**.
2. **Name**: bebas (mis. `AmberLauncher`).
3. **Supported account types**: pilih yang mengizinkan **akun Microsoft pribadi** (*Personal Microsoft accounts*).
   Login Minecraft hanya memakai tenant `consumers`, dan itu sudah dipakai kode (`MicrosoftAuthService`).
4. **Redirect URI**: dikosongkan. Launcher memakai *device code flow* (kode yang dimasukkan di halaman login), bukan pengalihan.
5. Setelah dibuat, buka **Authentication** > nyalakan **Allow public client flows** (Yes) > simpan.
   Tidak ada *client secret* yang perlu dibuat atau disimpan.
6. Salin **Application (client) ID** dari halaman *Overview*.

## 2. Minta persetujuan Mojang untuk aplikasinya

Aplikasi Azure baru **belum boleh** memakai API Minecraft. Tanpa persetujuan, langkah terakhir login
(`api.minecraftservices.com`) menjawab galat 403 dengan pesan sejenis *Invalid app registration*.
Ajukan Client ID-nya lewat formulir tinjauan aplikasi Mojang/Microsoft:

- Pesan galatnya sendiri menunjuk ke <https://aka.ms/AppRegInfo>.
- Komunitas (wiki.vg) menautkan formulirnya di <https://aka.ms/mce-reviewappid>.

Persetujuan bisa memakan waktu beberapa hari. Selama belum disetujui, login dengan Client ID baru akan ditolak; jangan
menyebarkan build dengan Client ID baru sebelum kamu mencoba login berhasil sampai masuk game.

> Halaman Microsoft/Mojang dapat berubah. Bila tautan di atas sudah usang, cari "Minecraft API app registration review".

## 3. Pakai Client ID-nya di build

Client ID dibaca saat build (`BuildConfig.MS_CLIENT_ID`) dengan urutan:

1. variabel lingkungan `AMBER_MS_CLIENT_ID` (untuk GitHub Actions: simpan sebagai *Variable* atau *Secret*, lalu teruskan
   ke langkah build sebagai `env`),
2. `amber.msClientId` di `local.properties` (build lokal),
3. bawaan lama bila keduanya kosong atau bukan GUID yang valid.

Contoh `local.properties`:

```
amber.msClientId=00000000-0000-0000-0000-000000000000
```

Langkah `env` di workflow tidak bisa diubah lewat patch (aturan repo untuk `.github/workflows`), jadi tambahkan
`AMBER_MS_CLIENT_ID: ${{ vars.AMBER_MS_CLIENT_ID }}` pada langkah build secara manual.

## 4. Uji sebelum dirilis

1. Pasang build uji, tambah akun Microsoft, selesaikan kode perangkat di halaman login.
2. Pastikan nama pemain tampil di layar Akun dan game bisa dibuka ke server online.
3. Akun yang sudah login dengan Client ID lama **perlu login ulang** setelah Client ID diganti (token dikaitkan ke aplikasi).
   Beri tahu pengguna di catatan rilis.
