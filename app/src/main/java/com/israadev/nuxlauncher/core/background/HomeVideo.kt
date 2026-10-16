package com.israadev.nuxlauncher.core.background

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

/** Hasil memasang berkas pilihan pengguna sebagai latar Beranda. */
enum class BgImportResult { IMAGE, VIDEO, TOO_BIG, UNSUPPORTED }

/**
 * Menyimpan video latar Beranda. Video disalin ke penyimpanan aplikasi bersama satu bingkai diam
 * (poster) yang dipakai sebagai gambar cadangan: tampil selagi video disiapkan, saat animasi
 * dimatikan, dan bila HP ternyata tidak bisa memutar videonya.
 */
object HomeVideo {
    const val FILE_NAME = "home_bg_video"

    /** Batas ukuran berkas video (200 MB) dan sisi terpanjang resolusinya. */
    const val MAX_BYTES = 200L * 1024 * 1024
    private const val MAX_LONG_SIDE = 2560
    private const val POSTER_MAX_SIDE = 1280

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /**
     * Menyalin video, memastikan HP punya decoder-nya, lalu menyimpan satu bingkai sebagai
     * [posterTarget] (JPEG). Berkas lama baru diganti setelah semuanya berhasil.
     */
    fun importVideo(context: Context, uri: Uri, videoTarget: File, posterTarget: File): BgImportResult {
        val dir = videoTarget.parentFile ?: return BgImportResult.UNSUPPORTED
        val tmp = File(dir, "$FILE_NAME.tmp")
        val posterTmp = File(dir, "${posterTarget.name}.tmp")
        val retriever = MediaMetadataRetriever()
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return BgImportResult.UNSUPPORTED
            var total = 0L
            input.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return BgImportResult.TOO_BIG
                        out.write(buf, 0, n)
                    }
                }
            }

            if (!hasDecoder(tmp)) return BgImportResult.UNSUPPORTED

            retriever.setDataSource(tmp.absolutePath)
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") {
                return BgImportResult.UNSUPPORTED
            }
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (maxOf(w, h) > MAX_LONG_SIDE) return BgImportResult.TOO_BIG

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val atUs = if (durationMs > 4000L) 1_000_000L else 0L
            val frame = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return BgImportResult.UNSUPPORTED

            val longSide = maxOf(frame.width, frame.height)
            val poster = if (longSide > POSTER_MAX_SIDE) {
                val scale = POSTER_MAX_SIDE.toFloat() / longSide
                Bitmap.createScaledBitmap(
                    frame,
                    maxOf(1, (frame.width * scale).toInt()),
                    maxOf(1, (frame.height * scale).toInt()),
                    true
                )
            } else {
                frame
            }
            posterTmp.outputStream().use { poster.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (poster !== frame) poster.recycle()
            frame.recycle()

            if (posterTarget.exists()) posterTarget.delete()
            if (!posterTmp.renameTo(posterTarget)) return BgImportResult.UNSUPPORTED
            if (videoTarget.exists()) videoTarget.delete()
            return if (tmp.renameTo(videoTarget)) BgImportResult.VIDEO else BgImportResult.UNSUPPORTED
        } catch (e: Throwable) {
            return BgImportResult.UNSUPPORTED
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
            tmp.delete()
            posterTmp.delete()
        }
    }

    /** True bila HP punya decoder untuk trek video di berkas ini (mis. H.264 hampir selalu ada). */
    private fun hasDecoder(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var found = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    // Format baru hanya berisi jenis dan ukuran: findDecoderForFormat menolak
                    // format yang membawa laju bingkai pada Android lama.
                    val w = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 1280
                    val h = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 720
                    val probe = MediaFormat.createVideoFormat(mime, w, h)
                    found = MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(probe) != null
                    break
                }
            }
            found
        } catch (e: Throwable) {
            false
        } finally {
            try {
                extractor.release()
            } catch (_: Throwable) {
            }
        }
    }
}
