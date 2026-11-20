package com.israadev.nuxlauncher.core.streamer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Gambar untuk overlay streamer. Foto yang dipilih pengguna dikecilkan (sisi terpanjang paling besar
 * [StreamerLogic.IMAGE_MAX_SIDE]) lalu disimpan sebagai PNG di folder aplikasi sendiri, jadi tidak
 * bergantung pada berkas aslinya dan tidak butuh izin penyimpanan. Semua fungsi di sini memakai disk:
 * panggil dari thread latar, bukan dari komposisi.
 */
object StreamerImages {
    private const val DIR = "streamer"

    /** Batas jumlah piksel foto asli yang mau dibaca (cegah foto panorama raksasa memenuhi memori). */
    private const val MAX_SOURCE_PIXELS = 120_000_000L

    fun dir(context: Context): File = File(context.filesDir, DIR)

    private fun fileOf(context: Context, name: String?): File? {
        val safe = StreamerLogic.safeImageName(name) ?: return null
        return File(dir(context), safe)
    }

    /** Memuat gambar tersimpan, atau null bila berkas tidak ada atau rusak. */
    fun load(context: Context, name: String?): Bitmap? {
        val f = fileOf(context, name) ?: return null
        if (!f.isFile) return null
        return try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Membaca foto dari [uri] (pemilih berkas sistem), mengecilkannya, dan menyimpannya.
     * Hasilnya nama berkas baru, atau null bila foto tidak bisa dibaca.
     */
    fun importImage(context: Context, uri: Uri, id: String): String? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val opened = resolver.openInputStream(uri) ?: return null
            opened.use { BitmapFactory.decodeStream(it, null, bounds) }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0 || w.toLong() * h.toLong() > MAX_SOURCE_PIXELS) return null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = StreamerLogic.sampleSize(w, h, StreamerLogic.IMAGE_MAX_SIDE)
            }
            val src = (resolver.openInputStream(uri) ?: return null).use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val rotation = readRotation(context, uri)
            val (tw, th) = StreamerLogic.scaledSize(src.width, src.height, StreamerLogic.IMAGE_MAX_SIDE)
            val matrix = Matrix()
            if (tw != src.width || th != src.height) {
                matrix.postScale(tw.toFloat() / src.width, th.toFloat() / src.height)
            }
            if (rotation != 0) matrix.postRotate(rotation.toFloat())
            val out = if (matrix.isIdentity) src else Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)

            val dir = dir(context)
            if (!dir.isDirectory && !dir.mkdirs()) return null
            val name = "${safeId(id)}_${System.currentTimeMillis()}.png"
            val target = File(dir, name)
            // Tulis ke berkas sementara dulu supaya berkas setengah jadi tidak pernah terbaca game.
            val tmp = File(dir, "$name.tmp")
            val ok = try {
                FileOutputStream(tmp).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (_: Throwable) {
                false
            }
            if (out !== src) out.recycle()
            src.recycle()
            if (!ok || !tmp.renameTo(target)) {
                tmp.delete()
                return null
            }
            name
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeId(id: String): String {
        val cleaned = id.filter { it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '-' }.take(24)
        return if (cleaned.isEmpty()) "img" else cleaned
    }

    private fun readRotation(context: Context, uri: Uri): Int {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return 0
            val orientation = stream.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Throwable) {
            0
        }
    }

    fun delete(context: Context, name: String?) {
        try {
            fileOf(context, name)?.delete()
        } catch (_: Throwable) {
        }
    }

    /** Menghapus semua berkas di folder streamer yang tidak ada di [keep] (sisa gambar yang diganti atau dihapus). */
    fun sweep(context: Context, keep: Set<String>) {
        try {
            dir(context).listFiles()?.forEach { f ->
                if (f.isFile && f.name !in keep) f.delete()
            }
        } catch (_: Throwable) {
        }
    }
}
