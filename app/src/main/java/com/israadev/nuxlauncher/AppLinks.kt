package com.israadev.nuxlauncher

/**
 * Tautan yang dipakai di dalam aplikasi. Ubah di sini saja.
 */
object AppLinks {
    /**
     * Tautan dukungan (tombol Buy me a coffee di dialog Tentang).
     * Kalau dikosongkan, tombolnya otomatis disembunyikan.
     */
    const val DONATE_URL = "https://sociabuzz.com/amberlauncher/tribe"

    /**
     * Server Discord komunitas (ide, laporan bug, tanya jawab). Ikonnya tampil di dialog Tentang dan
     * membuka tautan di peramban. Tautan undangan ini dibuat tanpa masa berlaku; kalau diganti,
     * cukup ubah di sini. Kalau dikosongkan, ikonnya otomatis disembunyikan.
     */
    const val DISCORD_URL = "https://discord.gg/NHNf9T4kuf"

    /** Akun TikTok AmberLauncher (ikon di dialog Tentang). Kalau dikosongkan, ikonnya otomatis disembunyikan. */
    const val TIKTOK_URL = "https://www.tiktok.com/@itsvynzz_"

    /**
     * Repo GitHub tempat rilis APK diterbitkan ("pemilik/nama"). Dipakai pengecek pembaruan
     * (core/update/UpdateChecker) untuk membaca daftar Release.
     */
    const val GITHUB_REPO = "ItsVynzzzz/AmberLauncher"
}
