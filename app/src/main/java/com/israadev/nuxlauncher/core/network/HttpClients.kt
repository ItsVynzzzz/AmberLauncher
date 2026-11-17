package com.israadev.nuxlauncher.core.network

import okhttp3.OkHttpClient

/**
 * Klien HTTP dasar launcher. Semua pemanggil yang butuh batas waktu sendiri memakai `HttpClients.base.newBuilder()`:
 * klien turunan berbagi ConnectionPool dan Dispatcher dengan klien dasar ini, jadi tidak ada kumpulan koneksi dan
 * thread baru untuk tiap layanan. DNS-nya [NuxDns]: DNS sistem lebih dulu, DNS-over-HTTPS hanya bila DNS sistem gagal
 * (mis. DNS operator memblokir alamat Mojang atau Microsoft).
 */
object HttpClients {
    val base: OkHttpClient by lazy { OkHttpClient.Builder().dns(NuxDns).build() }
}
