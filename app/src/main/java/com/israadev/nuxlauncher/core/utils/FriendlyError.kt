package com.israadev.nuxlauncher.core.utils

import android.os.Build
import com.israadev.nuxlauncher.BuildConfig
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Mengubah exception mentah menjadi kalimat yang bisa ditindaklanjuti pengguna, plus rincian teknis terpisah yang
 * bisa disalin untuk laporan. Dulu pengguna melihat teks seperti "Gagal mengunduh: Unable to resolve host
 * piston-meta.mojang.com" dalam Toast yang hilang dalam beberapa detik.
 */
object FriendlyError {

    class Info(
        /** Kalimat untuk pengguna: apa yang terjadi dan apa yang bisa dilakukan. */
        val message: String,
        /** Rincian teknis (jenis galat, rantai penyebab, versi) untuk disalin. */
        val detail: String
    )

    fun describe(error: Throwable?): Info {
        if (error == null) {
            return Info("Terjadi masalah yang tidak diketahui. Coba lagi.", header())
        }
        val chain = causeChain(error)
        val message = when {
            chain.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException } ->
                "Tidak bisa terhubung ke server unduhan. Periksa koneksi internet, lalu coba lagi."
            chain.any { it is SocketTimeoutException || (it is InterruptedIOException && it.message?.contains("timeout", true) == true) } ->
                "Server terlalu lama menjawab. Koneksi mungkin lambat atau tidak stabil; coba lagi."
            chain.any { it is SSLException } ->
                "Sambungan aman ke server gagal. Periksa tanggal dan jam HP serta koneksi internet, lalu coba lagi."
            chain.any { it is SocketException } ->
                "Koneksi terputus di tengah jalan. Coba lagi; file yang sudah selesai tidak diunduh ulang."
            chain.any { isOutOfSpace(it) } ->
                "Penyimpanan HP penuh. Kosongkan ruang (sekitar 500 MB untuk satu versi), lalu coba lagi."
            else -> {
                // Pesan dari kode kita sendiri (mis. "Sebagian file game belum terunduh...") sudah berbahasa jelas: pakai apa adanya.
                val own = error.localizedMessage?.trim().orEmpty()
                if (own.isNotEmpty() && own.length <= 220) own else "Terjadi masalah yang tidak terduga. Coba lagi."
            }
        }
        return Info(message, buildDetail(chain))
    }

    private fun isOutOfSpace(t: Throwable): Boolean {
        val m = t.message ?: return false
        return m.contains("No space left", ignoreCase = true) || m.contains("ENOSPC") || m.contains("Ruang penyimpanan", ignoreCase = true)
    }

    private fun causeChain(error: Throwable): List<Throwable> {
        val out = ArrayList<Throwable>(4)
        var cur: Throwable? = error
        while (cur != null && out.size < 6 && out.none { it === cur }) {
            out.add(cur)
            cur = cur.cause
        }
        return out
    }

    private fun header(): String =
        "AmberLauncher ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"

    private fun buildDetail(chain: List<Throwable>): String {
        val sb = StringBuilder(header()).append('\n')
        chain.forEachIndexed { i, t ->
            sb.append(if (i == 0) "" else "Penyebab: ").append(t.javaClass.name)
            t.message?.let { sb.append(": ").append(it.take(300)) }
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }
}
