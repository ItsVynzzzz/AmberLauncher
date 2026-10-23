package com.israadev.nuxlauncher.core.update

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/**
 * Mengunduh APK pembaruan: bisa dilanjutkan (berkas .part + header Range), diverifikasi (ukuran dan
 * SHA-256 dari GitHub), dan diulang otomatis bila koneksi putus di tengah jalan. Hasilnya ditulis ke
 * berkas sementara lalu diganti nama, jadi [download] yang sukses selalu menghasilkan berkas utuh.
 * Tanpa ketergantungan Android, jadi bisa diuji di JVM.
 */
object ApkDownloader {
    private const val BUFFER_BYTES = 128 * 1024
    private const val MAX_ATTEMPTS = 4
    private const val PROGRESS_INTERVAL_MS = 250L

    /** Dilempar bila unduhan gagal dan tidak ada gunanya diulang otomatis. */
    class DownloadException(val failure: UpdateFailure, cause: Throwable? = null) :
        Exception(failure.message, cause)

    /** Membatalkan unduhan dari thread lain, termasuk memutus soket yang sedang menunggu data. */
    class CancelToken {
        @Volatile
        var cancelled = false
            private set

        @Volatile
        private var call: Call? = null

        internal fun attach(c: Call) {
            call = c
            if (cancelled) c.cancel()
        }

        fun cancel() {
            cancelled = true
            call?.cancel()
        }

        internal fun throwIfCancelled() {
            if (cancelled) throw CancellationException("Unduhan dibatalkan")
        }
    }

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    // Satu unduhan pada satu waktu: unduhan yang baru dibatalkan menyelesaikan penutupan berkasnya
    // dulu sebelum unduhan berikutnya menyentuh berkas .part yang sama.
    private val lock = Any()

    /**
     * @param expectedSize ukuran berkas menurut GitHub (null = tidak diketahui)
     * @param expectedSha256 SHA-256 huruf kecil menurut GitHub (null = tidak diketahui)
     * @param freeSpace sisa ruang penyimpanan saat ini (byte)
     * @param spareBytes ruang yang harus tetap tersisa setelah unduhan (untuk salinan pemasang + cadangan)
     * @param onProgress dipanggil paling cepat tiap 250 ms; total 0 = ukuran belum diketahui
     */
    fun download(
        client: OkHttpClient,
        url: String,
        target: File,
        expectedSize: Long?,
        expectedSha256: String?,
        userAgent: String,
        token: CancelToken = CancelToken(),
        freeSpace: () -> Long = { Long.MAX_VALUE },
        spareBytes: Long = 0L,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ) {
        synchronized(lock) {
            target.parentFile?.mkdirs()
            val part = File(target.path + ".part")

            // Sudah pernah diunduh utuh (mis. pengguna menutup aplikasi sebelum sempat memasang)?
            if ((expectedSize != null || expectedSha256 != null) && target.isFile &&
                isValid(target, expectedSize, expectedSha256)
            ) {
                onProgress(target.length(), target.length())
                return
            }
            target.delete()

            var attempts = 0
            var restartedAfterMismatch = false
            var resumedThisRun = false
            while (true) {
                token.throwIfCancelled()
                val sizeBefore = part.length()
                try {
                    if (sizeBefore > 0) resumedThisRun = true
                    transfer(client, url, part, expectedSize, userAgent, token, freeSpace, spareBytes, onProgress)

                    val problem = checkComplete(part, expectedSize, expectedSha256)
                    if (problem == null) {
                        if (!part.renameTo(target)) {
                            part.copyTo(target, overwrite = true)
                            part.delete()
                        }
                        return
                    }
                    part.delete()
                    // Sambungan yang dilanjutkan bisa tercampur bila berkas di server diganti di tengah
                    // jalan: ulangi sekali dari awal sebelum menyerah.
                    if (resumedThisRun && !restartedAfterMismatch) {
                        restartedAfterMismatch = true
                        resumedThisRun = false
                        continue
                    }
                    throw DownloadException(problem)
                } catch (e: DownloadException) {
                    throw e
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    if (token.cancelled) throw CancellationException("Unduhan dibatalkan")
                    if (e is HttpStatusException && !isTransient(e.code)) throw DownloadException(httpFailure(e.code), e)
                    // Pengulangan dihitung hanya bila tidak ada kemajuan, jadi jaringan yang putus-nyambung
                    // tetap bisa menyelesaikan berkas besar.
                    if (part.length() > sizeBefore) attempts = 0
                    attempts++
                    if (attempts >= MAX_ATTEMPTS) {
                        throw DownloadException(
                            UpdateFailure(
                                FailureKind.DOWNLOAD,
                                "Unduhan terputus. Ketuk Coba lagi; unduhan dilanjutkan dari bagian yang sudah ada."
                            ),
                            e
                        )
                    }
                    sleepBackoff(attempts, token)
                }
            }
        }
    }

    /** Satu putaran unduh ke [part]; berhenti dengan IOException bila terputus atau tidak lengkap. */
    private fun transfer(
        client: OkHttpClient,
        url: String,
        part: File,
        expectedSize: Long?,
        userAgent: String,
        token: CancelToken,
        freeSpace: () -> Long,
        spareBytes: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        var offset = part.length()
        if (expectedSize != null && offset > expectedSize) {
            part.delete()
            offset = 0
        }
        if (expectedSize != null && offset == expectedSize) return // sudah penuh, tinggal diverifikasi

        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/octet-stream")
            .header("Accept-Encoding", "identity")
        if (offset > 0) builder.header("Range", "bytes=$offset-")
        val call = client.newCall(builder.build())
        token.attach(call)

        call.execute().use { resp ->
            when (resp.code) {
                200 -> offset = 0 // server tidak mendukung Range: tulis ulang dari awal
                206 -> {
                    val start = contentRangeStart(resp.header("Content-Range"))
                    if (start != offset) {
                        part.delete()
                        throw IOException("Content-Range tidak cocok")
                    }
                }
                416 -> {
                    // Bagian yang tersimpan tidak cocok dengan berkas di server: mulai dari awal.
                    part.delete()
                    throw IOException("Range ditolak")
                }
                else -> throw HttpStatusException(resp.code)
            }
            val body = resp.body ?: throw IOException("Balasan kosong")

            val remaining = when {
                resp.code == 206 -> body.contentLength().takeIf { it > 0 }
                else -> body.contentLength().takeIf { it > 0 } ?: expectedSize
            }
            val total = expectedSize ?: remaining?.let { it + offset } ?: 0L
            if (remaining != null) {
                val need = remaining + spareBytes
                val free = freeSpace()
                if (free < need) {
                    throw DownloadException(
                        UpdateFailure(
                            FailureKind.STORAGE_FULL,
                            "Ruang penyimpanan tidak cukup. Kosongkan sekitar ${megabytes(need - free + 16L * 1024 * 1024)} MB lalu coba lagi."
                        )
                    )
                }
            }

            var done = offset
            var lastReport = 0L
            val buffer = ByteArray(BUFFER_BYTES)
            FileOutputStream(part, offset > 0).use { out ->
                val input = body.byteStream()
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    done += n
                    token.throwIfCancelled()
                    val now = System.nanoTime() / 1_000_000L
                    if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                        lastReport = now
                        onProgress(done, total)
                    }
                }
                out.flush()
                try {
                    out.fd.sync()
                } catch (_: IOException) {
                }
            }
            onProgress(done, if (total > 0) total else done)
        }
    }

    /** null = berkas utuh dan cocok; selain itu alasan kegagalan, atau IOException bila belum lengkap. */
    private fun checkComplete(part: File, expectedSize: Long?, expectedSha256: String?): UpdateFailure? {
        val length = part.length()
        if (expectedSize != null) {
            if (length < expectedSize) throw IOException("Unduhan belum lengkap ($length dari $expectedSize byte)")
            if (length > expectedSize) return corrupt()
        }
        if (length == 0L) throw IOException("Berkas kosong")
        if (expectedSha256 != null && sha256Of(part) != expectedSha256) return corrupt()
        return null
    }

    private fun corrupt() = UpdateFailure(
        FailureKind.CORRUPT_FILE,
        "Berkas hasil unduhan rusak (tidak cocok dengan yang di GitHub). Ketuk Coba lagi untuk mengunduh ulang."
    )

    private fun isValid(file: File, expectedSize: Long?, expectedSha256: String?): Boolean {
        if (expectedSize != null && file.length() != expectedSize) return false
        if (expectedSha256 != null && sha256Of(file) != expectedSha256) return false
        return true
    }

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Awal rentang dari "bytes 100-199/200"; null bila headernya tidak ada atau aneh. */
    private fun contentRangeStart(header: String?): Long? {
        val m = Regex("""^bytes\s+(\d+)-\d+/(\d+|\*)$""").matchEntire(header?.trim().orEmpty()) ?: return null
        return m.groupValues[1].toLongOrNull()
    }

    private fun isTransient(code: Int): Boolean = code == 408 || code == 429 || code >= 500

    private fun httpFailure(code: Int): UpdateFailure = when (code) {
        404 -> UpdateFailure(FailureKind.SERVER, "Berkas APK tidak ditemukan di GitHub (kode 404). Mungkin rilisnya sudah dihapus.")
        403 -> UpdateFailure(FailureKind.SERVER, "GitHub menolak unduhan (kode 403). Coba lagi nanti.")
        else -> UpdateFailure(FailureKind.SERVER, "GitHub membalas kode $code saat mengunduh.")
    }

    private fun megabytes(bytes: Long): Long = (bytes + 1024 * 1024 - 1) / (1024 * 1024)

    private fun sleepBackoff(attempt: Int, token: CancelToken) {
        val waitMs = 1000L * attempt
        val end = System.nanoTime() + waitMs * 1_000_000L
        while (System.nanoTime() < end) {
            token.throwIfCancelled()
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                throw CancellationException("Unduhan dibatalkan")
            }
        }
    }
}
