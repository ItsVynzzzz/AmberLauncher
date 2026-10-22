package com.israadev.nuxlauncher.core.update

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLException

/**
 * Satu permintaan ke GitHub untuk daftar rilis, dengan ETag: bila daftarnya tidak berubah GitHub
 * membalas 304 (hampir tanpa isi) dan balasan itu tidak dihitung ke batas 60 permintaan per jam,
 * jadi aman dilakukan tiap aplikasi dibuka. Tanpa ketergantungan Android.
 */
object ReleaseFetcher {
    sealed interface Result {
        /** Daftar baru. [etag] dipakai untuk permintaan berikutnya. */
        data class Fresh(val body: String, val etag: String?) : Result

        /** Daftar sama seperti yang sudah disimpan. */
        data object NotModified : Result

        data class Failure(val failure: UpdateFailure) : Result
    }

    private const val MAX_BODY_BYTES = 2L * 1024 * 1024
    private const val RATE_LIMIT_DEFAULT_MS = 15L * 60 * 1000
    private const val RATE_LIMIT_MAX_MS = 60L * 60 * 1000

    fun fetch(
        client: OkHttpClient,
        url: String,
        userAgent: String,
        etag: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Result {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", userAgent)
            .apply { if (!etag.isNullOrEmpty()) header("If-None-Match", etag) }
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                when {
                    resp.code == 304 -> Result.NotModified
                    resp.isSuccessful -> {
                        val body = resp.body ?: return@use Result.Failure(
                            UpdateFailure(FailureKind.BAD_RESPONSE, "Balasan GitHub kosong.")
                        )
                        val length = body.contentLength()
                        if (length > MAX_BODY_BYTES) {
                            Result.Failure(UpdateFailure(FailureKind.BAD_RESPONSE, "Balasan GitHub terlalu besar."))
                        } else {
                            Result.Fresh(body.string(), resp.header("ETag"))
                        }
                    }
                    isRateLimited(resp) -> rateLimited(resp, nowMs)
                    resp.code >= 500 -> Result.Failure(
                        UpdateFailure(FailureKind.SERVER, "GitHub sedang bermasalah (kode ${resp.code}). Coba lagi nanti.")
                    )
                    else -> Result.Failure(
                        UpdateFailure(FailureKind.SERVER, "GitHub membalas kode ${resp.code}.")
                    )
                }
            }
        } catch (e: IOException) {
            Result.Failure(failureFor(e))
        }
    }

    /** Pesan untuk kegagalan jaringan yang umum, supaya pengguna tahu harus berbuat apa. */
    fun failureFor(e: Throwable): UpdateFailure = when (e) {
        is UnknownHostException, is ConnectException, is NoRouteToHostException ->
            UpdateFailure(FailureKind.OFFLINE, "Tidak ada koneksi internet.")
        is InterruptedIOException ->
            UpdateFailure(FailureKind.TIMEOUT, "Koneksi terlalu lambat. Coba lagi.")
        is SSLException ->
            UpdateFailure(FailureKind.OFFLINE, "Koneksi aman ke GitHub gagal. Periksa tanggal dan jam HP.")
        is IOException ->
            UpdateFailure(FailureKind.OFFLINE, "Koneksi internet terputus. Coba lagi.")
        else ->
            UpdateFailure(FailureKind.BAD_RESPONSE, "Terjadi kesalahan yang tidak terduga.")
    }

    private fun isRateLimited(resp: Response): Boolean =
        resp.code == 429 ||
            (resp.code == 403 && (resp.header("x-ratelimit-remaining") == "0" || resp.header("retry-after") != null))

    private fun rateLimited(resp: Response, nowMs: Long): Result.Failure {
        val retryAfterSec = resp.header("retry-after")?.trim()?.toLongOrNull()
        val resetEpochSec = resp.header("x-ratelimit-reset")?.trim()?.toLongOrNull()
        val until = when {
            retryAfterSec != null -> nowMs + retryAfterSec * 1000
            resetEpochSec != null -> resetEpochSec * 1000
            else -> nowMs + RATE_LIMIT_DEFAULT_MS
        }.coerceIn(nowMs + 60_000L, nowMs + RATE_LIMIT_MAX_MS)
        val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(until))
        return Result.Failure(
            UpdateFailure(
                FailureKind.RATE_LIMITED,
                "Batas permintaan GitHub tercapai (biasanya karena banyak orang memakai jaringan yang sama). Coba lagi sekitar pukul $clock.",
                retryAtMillis = until
            )
        )
    }
}
