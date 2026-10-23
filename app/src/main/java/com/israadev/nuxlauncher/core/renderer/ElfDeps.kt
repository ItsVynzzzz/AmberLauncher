package com.israadev.nuxlauncher.core.renderer

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Membaca daftar dependensi (DT_NEEDED) sebuah pustaka .so tanpa memuatnya.
 *
 * Dipakai untuk renderer plugin: pustaka di folder aplikasi lain tidak bisa menemukan dependensinya
 * sendiri (misalnya `libSDL3.so`) lewat pencarian default Android, sehingga game gagal dengan
 * `dlopen failed: library "libSDL3.so" not found`. Dengan daftar ini launcher memuat dependensinya
 * lebih dulu lewat path lengkap, dan Android lalu mengenalinya berdasarkan nama.
 *
 * Murni Java/Kotlin standar (tanpa Android) supaya mudah diuji. Hanya membaca beberapa KB di awal
 * dan di sekitar tabel dinamis berkas, jadi murah.
 */
object ElfDeps {

    private const val PT_LOAD = 1
    private const val PT_DYNAMIC = 2
    private const val DT_NULL = 0L
    private const val DT_NEEDED = 1L
    private const val DT_STRTAB = 5L

    /** Batas pengaman agar berkas rusak tidak membuat memori atau waktu membengkak. */
    private const val MAX_DYNAMIC_BYTES = 1 shl 20
    private const val MAX_STRTAB_BYTES = 1 shl 22
    private const val MAX_PHDRS = 256

    /** Nama pustaka DT_NEEDED milik [file] sesuai urutan di berkas; kosong bila bukan ELF atau rusak. */
    fun neededLibs(file: File): List<String> = try {
        RandomAccessFile(file, "r").use { read(it) }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun read(raf: RandomAccessFile): List<String> {
        val ident = ByteArray(16)
        raf.seek(0)
        raf.readFully(ident)
        if (ident[0] != 0x7f.toByte() || ident[1] != 'E'.code.toByte() ||
            ident[2] != 'L'.code.toByte() || ident[3] != 'F'.code.toByte()
        ) return emptyList()
        val is64 = when (ident[4].toInt()) {
            1 -> false
            2 -> true
            else -> return emptyList()
        }
        val order = when (ident[5].toInt()) {
            1 -> ByteOrder.LITTLE_ENDIAN
            2 -> ByteOrder.BIG_ENDIAN
            else -> return emptyList()
        }

        val header = ByteBuffer.allocate(64).order(order)
        raf.seek(0)
        raf.readFully(header.array(), 0, if (is64) 64 else 52)
        val phOff: Long
        val phEntSize: Int
        val phNum: Int
        if (is64) {
            phOff = header.getLong(32)
            phEntSize = header.getShort(54).toInt() and 0xffff
            phNum = header.getShort(56).toInt() and 0xffff
        } else {
            phOff = header.getInt(28).toLong() and 0xffffffffL
            phEntSize = header.getShort(42).toInt() and 0xffff
            phNum = header.getShort(44).toInt() and 0xffff
        }
        if (phNum == 0 || phNum > MAX_PHDRS || phEntSize < (if (is64) 56 else 32)) return emptyList()

        val ph = ByteBuffer.allocate(phNum * phEntSize).order(order)
        raf.seek(phOff)
        raf.readFully(ph.array())

        class Seg(val vaddr: Long, val offset: Long, val fileSize: Long)

        val loads = ArrayList<Seg>()
        var dynOffset = -1L
        var dynSize = 0L
        for (i in 0 until phNum) {
            val b = i * phEntSize
            val type = ph.getInt(b)
            val offset: Long
            val vaddr: Long
            val fileSize: Long
            if (is64) {
                offset = ph.getLong(b + 8)
                vaddr = ph.getLong(b + 16)
                fileSize = ph.getLong(b + 32)
            } else {
                offset = ph.getInt(b + 4).toLong() and 0xffffffffL
                vaddr = ph.getInt(b + 8).toLong() and 0xffffffffL
                fileSize = ph.getInt(b + 16).toLong() and 0xffffffffL
            }
            if (type == PT_LOAD) loads.add(Seg(vaddr, offset, fileSize))
            if (type == PT_DYNAMIC) {
                dynOffset = offset
                dynSize = fileSize
            }
        }
        if (dynOffset < 0 || dynSize <= 0 || dynSize > MAX_DYNAMIC_BYTES) return emptyList()

        val dyn = ByteBuffer.allocate(dynSize.toInt()).order(order)
        raf.seek(dynOffset)
        raf.readFully(dyn.array())

        val entry = if (is64) 16 else 8
        val needed = ArrayList<Long>()
        var strtabVaddr = -1L
        var p = 0
        while (p + entry <= dyn.capacity()) {
            val tag: Long
            val value: Long
            if (is64) {
                tag = dyn.getLong(p)
                value = dyn.getLong(p + 8)
            } else {
                tag = dyn.getInt(p).toLong()
                value = dyn.getInt(p + 4).toLong() and 0xffffffffL
            }
            if (tag == DT_NULL) break
            if (tag == DT_NEEDED) needed.add(value)
            if (tag == DT_STRTAB) strtabVaddr = value
            p += entry
        }
        if (needed.isEmpty() || strtabVaddr < 0) return emptyList()

        // Alamat memori tabel string -> posisi di berkas lewat segmen PT_LOAD yang memuatnya.
        val seg = loads.firstOrNull { strtabVaddr >= it.vaddr && strtabVaddr < it.vaddr + it.fileSize }
            ?: return emptyList()
        val strOff = seg.offset + (strtabVaddr - seg.vaddr)
        val fileLen = raf.length()
        if (strOff < 0 || strOff >= fileLen) return emptyList()
        val strLen = minOf(fileLen - strOff, MAX_STRTAB_BYTES.toLong()).toInt()
        val strs = ByteArray(strLen)
        raf.seek(strOff)
        raf.readFully(strs)

        val out = ArrayList<String>(needed.size)
        for (nameOff in needed) {
            if (nameOff < 0 || nameOff >= strLen) continue
            var end = nameOff.toInt()
            while (end < strLen && strs[end] != 0.toByte()) end++
            if (end > nameOff) out.add(String(strs, nameOff.toInt(), end - nameOff.toInt(), Charsets.UTF_8))
        }
        return out
    }

    /**
     * Urutan memuat dependensi [root] yang tersedia sebagai berkas di [searchDirs] (urutan = prioritas),
     * dependensi terdalam lebih dulu; [root] sendiri tidak ikut. Pustaka sistem (libc.so, liblog.so, ...)
     * tidak ada di folder-folder itu sehingga otomatis dilewati.
     */
    fun loadOrder(root: File, searchDirs: List<File>, maxDepth: Int = 6): List<File> {
        val ordered = LinkedHashMap<String, File>()
        val seen = HashSet<String>()
        seen.add(canonical(root))

        fun visit(file: File, depth: Int) {
            if (depth >= maxDepth) return
            for (name in neededLibs(file)) {
                if (name.contains('/')) continue
                val dep = searchDirs.asSequence()
                    .map { File(it, name) }
                    .firstOrNull { it.isFile }
                    ?: continue
                val key = canonical(dep)
                if (!seen.add(key)) continue
                visit(dep, depth + 1)
                ordered[key] = dep
            }
        }
        visit(root, 0)
        return ordered.values.toList()
    }

    private fun canonical(f: File): String = try {
        f.canonicalPath
    } catch (_: Throwable) {
        f.absolutePath
    }
}
