package com.israadev.nuxlauncher.ui.control

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntSize
import com.movtery.zalithlauncher.bridge.CursorShape
import java.io.File

/*
 * Tampilan kursor launcher (kursor virtual): satu gambar per bentuk kursor game (panah, tangan, kursor teks,
 * crosshair, ubah ukuran, pindahkan, tidak diizinkan), masing-masing bisa diganti gambar sendiri dan diberi
 * hotspot (titik yang dianggap ujung kursor, dalam persen kotak kursor).
 *
 * Tanpa gambar kustom, bentuknya digambar dari path vektor kecil (tanpa berkas gambar bawaan, tanpa decode).
 * Gambar kustom didecode sekali, dikecilkan ke maksimal 192 px, dan disimpan sebagai ImageBitmap di cache
 * sampai gambarnya berubah. Menggambar kursor tidak mengalokasikan apa pun per frame.
 */
internal object PointerStyle {
    private const val MAX_SIDE = 192
    private const val DIR = "pointers"

    /** Naik tiap kali gambar kustom berubah, supaya tampilan yang membacanya ikut diperbarui. */
    var revision by mutableIntStateOf(0)
        private set

    fun bump() {
        revision++
    }

    val shapes: List<CursorShape> = CursorShape.values().toList()

    fun key(shape: CursorShape): String = when (shape) {
        CursorShape.Arrow -> "arrow"
        CursorShape.IBeam -> "ibeam"
        CursorShape.Hand -> "hand"
        CursorShape.CrossHair -> "crosshair"
        CursorShape.ResizeNS -> "resize_ns"
        CursorShape.ResizeEW -> "resize_ew"
        CursorShape.ResizeAll -> "resize_all"
        CursorShape.NotAllowed -> "not_allowed"
    }

    fun title(shape: CursorShape): String = when (shape) {
        CursorShape.Arrow -> "Penunjuk (Default)"
        CursorShape.IBeam -> "Penunjuk (Kursor Teks)"
        CursorShape.Hand -> "Penunjuk (Tangan)"
        CursorShape.CrossHair -> "Penunjuk (Crosshair)"
        CursorShape.ResizeNS -> "Penunjuk (Atas/Bawah)"
        CursorShape.ResizeEW -> "Penunjuk (Kiri/Kanan)"
        CursorShape.ResizeAll -> "Penunjuk (Pindahkan)"
        CursorShape.NotAllowed -> "Penunjuk (Tidak Diizinkan)"
    }

    fun summary(shape: CursorShape): String = when (shape) {
        CursorShape.Arrow -> "Bentuk biasa. Dipakai di semua versi Minecraft."
        CursorShape.IBeam -> "MC 1.21.9+: dipakai saat kursor di atas kolom teks."
        CursorShape.Hand -> "MC 1.21.9+: dipakai saat kursor di atas komponen yang bisa diklik."
        CursorShape.CrossHair -> "MC 1.21.9+: bentuk crosshair."
        CursorShape.ResizeNS -> "MC 1.21.9+: dipakai di komponen yang bisa digulir ke atas/bawah."
        CursorShape.ResizeEW -> "MC 1.21.9+: dipakai di komponen penggeser (slider)."
        CursorShape.ResizeAll -> "MC 1.21.9+: bentuk pindahkan."
        CursorShape.NotAllowed -> "MC 1.21.9+: dipakai di komponen yang dinonaktifkan."
    }

    // Hotspot bawaan (persen). Panah: ujungnya di pojok kiri atas. Tangan: ujung jari telunjuk.
    fun defaultHotspotX(shape: CursorShape): Int = when (shape) {
        CursorShape.Arrow -> 0
        CursorShape.Hand -> 41
        else -> 50
    }

    fun defaultHotspotY(shape: CursorShape): Int = when (shape) {
        CursorShape.Arrow -> 0
        CursorShape.Hand -> 6
        else -> 50
    }

    fun hotspotX(map: Map<String, Int>, shape: CursorShape): Int =
        (map[key(shape) + ".x"] ?: defaultHotspotX(shape)).coerceIn(0, 100)

    fun hotspotY(map: Map<String, Int>, shape: CursorShape): Int =
        (map[key(shape) + ".y"] ?: defaultHotspotY(shape)).coerceIn(0, 100)

    fun hasHotspot(map: Map<String, Int>, shape: CursorShape): Boolean =
        map.containsKey(key(shape) + ".x") || map.containsKey(key(shape) + ".y")

    fun withHotspot(map: Map<String, Int>, shape: CursorShape, x: Int, y: Int): Map<String, Int> {
        val out = HashMap<String, Int>(map)
        out[key(shape) + ".x"] = x.coerceIn(0, 100)
        out[key(shape) + ".y"] = y.coerceIn(0, 100)
        return out
    }

    fun withoutHotspot(map: Map<String, Int>, shape: CursorShape): Map<String, Int> {
        val out = HashMap<String, Int>(map)
        out.remove(key(shape) + ".x")
        out.remove(key(shape) + ".y")
        return out
    }

    // ---------------------------------------------------------------- gambar kustom

    private fun dir(context: Context): File = File(context.filesDir, DIR)

    fun file(context: Context, shape: CursorShape): File = File(dir(context), key(shape) + ".png")

    private class Entry(val revision: Int, val bitmap: ImageBitmap?)

    // Hanya diakses dari thread utama (saat menyusun tampilan).
    private val cache = HashMap<String, Entry>()

    /**
     * Gambar kustom untuk bentuk ini, atau null bila memakai bentuk vektor bawaan. Dipanggil saat menyusun
     * tampilan: membaca [revision], jadi tampilan ikut disusun ulang begitu gambarnya diganti atau dihapus.
     */
    fun bitmap(context: Context, shape: CursorShape): ImageBitmap? {
        val rev = revision
        val k = key(shape)
        val hit = cache[k]
        if (hit != null && hit.revision == rev) return hit.bitmap
        val f = file(context, shape)
        val bmp: ImageBitmap? = if (f.isFile) {
            try {
                BitmapFactory.decodeFile(f.path)?.asImageBitmap()
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
        cache[k] = Entry(rev, bmp)
        return bmp
    }

    /**
     * Salin gambar pilihan pengguna jadi gambar kursor. Dijalankan di thread IO. Hasilnya dibuat persegi
     * (gambar di pojok kiri atas) dan dikecilkan ke maksimal 192 px, jadi hotspot dalam persen selalu merujuk
     * ke kotak yang sama. Mengembalikan false bila gambar tidak bisa dibaca.
     */
    fun importImage(context: Context, shape: CursorShape, uri: Uri): Boolean {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val src = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false

            val side = maxOf(src.width, src.height)
            val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(square).drawBitmap(src, 0f, 0f, null)
            val result = if (side > MAX_SIDE) Bitmap.createScaledBitmap(square, MAX_SIDE, MAX_SIDE, true) else square

            val folder = dir(context)
            folder.mkdirs()
            val target = file(context, shape)
            val tmp = File(folder, key(shape) + ".tmp")
            tmp.outputStream().use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Hapus gambar kustom (kembali ke bentuk bawaan). */
    fun removeImage(context: Context, shape: CursorShape) {
        file(context, shape).delete()
        bump()
    }

    // ---------------------------------------------------------------- bentuk vektor bawaan

    /**
     * Bentuk bawaan dalam kisi 24 x 24 (dikali kepadatan layar). [fill] diisi putih dengan garis tepi hitam,
     * [lines] digambar sebagai garis tebal hitam lalu garis tipis berwarna [lineColor].
     */
    class Glyph(
        val fill: Path?,
        val shadow: Path?,
        val lines: Path?,
        val lineColor: Color,
        val outline: Stroke,
        val lineBack: Stroke,
        val lineFront: Stroke
    )

    // Bentuk bawaan dibuat sekali per bentuk dan kepadatan layar, bukan tiap kali bentuk kursor game berganti
    // (kursor sering bolak-balik panah/tangan saat melewati tombol). Hanya diakses dari thread utama.
    private var glyphDensity = 0f
    private val glyphCache = arrayOfNulls<Glyph>(shapes.size)

    fun glyphFor(shape: CursorShape, d: Float): Glyph {
        if (glyphDensity != d) {
            glyphDensity = d
            java.util.Arrays.fill(glyphCache, null)
        }
        val i = shape.ordinal
        return glyphCache[i] ?: glyph(shape, d).also { glyphCache[i] = it }
    }

    private fun glyph(shape: CursorShape, d: Float): Glyph {
        var fill: Path? = null
        var shadow: Path? = null
        var lines: Path? = null
        var lineColor = Color.White

        when (shape) {
            CursorShape.Arrow -> {
                val arrow = Path()
                arrow.moveTo(0f, 0f)
                arrow.lineTo(0f, 22f * d)
                arrow.lineTo(5.5f * d, 16.5f * d)
                arrow.lineTo(10.5f * d, 24.5f * d)
                arrow.lineTo(13.5f * d, 23f * d)
                arrow.lineTo(8.5f * d, 15f * d)
                arrow.lineTo(15.5f * d, 15f * d)
                arrow.close()
                val sh = Path()
                sh.addPath(arrow, Offset(2f * d, 2f * d))
                fill = arrow
                shadow = sh
            }

            CursorShape.Hand -> fill = polygon(
                d,
                8f, 1.5f, 11.5f, 1.5f, 11.5f, 9f, 13.5f, 9f, 13.5f, 10f, 16f, 10f, 16f, 11f,
                18.5f, 11f, 18.5f, 12f, 21f, 12f, 21f, 19f, 18.5f, 22.5f, 10.5f, 22.5f,
                7.5f, 18.5f, 4f, 13f, 5.2f, 11.8f, 8f, 14.2f
            )

            CursorShape.IBeam -> {
                val p = Path()
                p.moveTo(12f * d, 3f * d)
                p.lineTo(12f * d, 21f * d)
                p.moveTo(8.5f * d, 3f * d)
                p.lineTo(15.5f * d, 3f * d)
                p.moveTo(8.5f * d, 21f * d)
                p.lineTo(15.5f * d, 21f * d)
                lines = p
            }

            CursorShape.CrossHair -> {
                val p = Path()
                p.moveTo(12f * d, 3f * d)
                p.lineTo(12f * d, 21f * d)
                p.moveTo(3f * d, 12f * d)
                p.lineTo(21f * d, 12f * d)
                lines = p
            }

            CursorShape.ResizeNS -> fill = polygon(
                d,
                12f, 2f, 17f, 8f, 13.5f, 8f, 13.5f, 16f, 17f, 16f, 12f, 22f, 7f, 16f, 10.5f, 16f, 10.5f, 8f, 7f, 8f
            )

            CursorShape.ResizeEW -> fill = polygon(
                d,
                2f, 12f, 8f, 7f, 8f, 10.5f, 16f, 10.5f, 16f, 7f, 22f, 12f, 16f, 17f, 16f, 13.5f, 8f, 13.5f, 8f, 17f
            )

            CursorShape.ResizeAll -> fill = polygon(
                d,
                12f, 1.5f, 16f, 6f, 13.2f, 6f, 13.2f, 10.8f, 18f, 10.8f, 18f, 8f, 22.5f, 12f, 18f, 16f,
                18f, 13.2f, 13.2f, 13.2f, 13.2f, 18f, 16f, 18f, 12f, 22.5f, 8f, 18f, 10.8f, 18f,
                10.8f, 13.2f, 6f, 13.2f, 6f, 16f, 1.5f, 12f, 6f, 8f, 6f, 10.8f, 10.8f, 10.8f, 10.8f, 6f, 8f, 6f
            )

            CursorShape.NotAllowed -> {
                val p = Path()
                p.addOval(Rect(3f * d, 3f * d, 21f * d, 21f * d))
                p.moveTo(5.6f * d, 5.6f * d)
                p.lineTo(18.4f * d, 18.4f * d)
                lines = p
                lineColor = Color(0xFFE53935)
            }
        }

        return Glyph(
            fill = fill,
            shadow = shadow,
            lines = lines,
            lineColor = lineColor,
            outline = Stroke(width = 1.8f * d, cap = StrokeCap.Round, join = StrokeJoin.Round),
            lineBack = Stroke(width = 4.4f * d, cap = StrokeCap.Round, join = StrokeJoin.Round),
            lineFront = Stroke(width = 2.2f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    /** Poligon tertutup dari pasangan x, y dalam kisi 24 x 24. */
    private fun polygon(d: Float, vararg xy: Float): Path {
        val p = Path()
        var i = 0
        while (i + 1 < xy.size) {
            if (i == 0) p.moveTo(xy[i] * d, xy[i + 1] * d) else p.lineTo(xy[i] * d, xy[i + 1] * d)
            i += 2
        }
        p.close()
        return p
    }
}

/** Gambar satu bentuk bawaan di kisi 24 x 24 (dikali kepadatan layar). */
internal fun DrawScope.drawGlyph(g: PointerStyle.Glyph) {
    val lines = g.lines
    if (lines != null) {
        drawPath(path = lines, color = Color.Black, style = g.lineBack)
        drawPath(path = lines, color = g.lineColor, style = g.lineFront)
    }
    val fill = g.fill
    if (fill != null) {
        val shadow = g.shadow
        if (shadow != null) {
            drawPath(path = shadow, color = Color.Black.copy(alpha = 0.45f), style = Fill)
        }
        drawPath(path = fill, color = Color.White, style = Fill)
        drawPath(path = fill, color = Color.Black, style = g.outline)
    }
}

/**
 * Gambar kursor di kotak persegi [sizePx] yang pojok kiri atasnya ada di titik (0, 0). Titik hotspot
 * ([hotX], [hotY], 0..1 dari kotak) jadi pusat efek "ditekan" (mengecil sedikit). Gambar kustom dipakai bila
 * ada, kalau tidak bentuk vektor bawaan.
 */
internal fun DrawScope.drawPointer(
    glyph: PointerStyle.Glyph,
    custom: ImageBitmap?,
    d: Float,
    sizePx: Float,
    hotX: Float,
    hotY: Float,
    pressed: Boolean
) {
    val press = if (pressed) 0.88f else 1.0f
    scale(press, pivot = Offset(hotX * sizePx, hotY * sizePx)) {
        if (custom != null) {
            val side = sizePx.toInt().coerceAtLeast(1)
            drawImage(custom, dstSize = IntSize(side, side))
        } else {
            scale(sizePx / (24f * d), pivot = Offset.Zero) {
                drawGlyph(glyph)
            }
        }
    }
}
