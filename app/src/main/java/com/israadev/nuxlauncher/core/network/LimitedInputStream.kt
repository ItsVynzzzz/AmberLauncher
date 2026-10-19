package com.israadev.nuxlauncher.core.network

import java.io.FilterInputStream
import java.io.InputStream

/**
 * Membatasi total byte yang boleh dibaca dari satu koneksi. Dipakai server lokal supaya kiriman
 * tanpa akhir (satu baris panjang tanpa baris baru) tidak menghabiskan memori. Setelah batas
 * tercapai, pembacaan dianggap selesai (EOF).
 */
class LimitedInputStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {

    override fun read(): Int {
        if (remaining <= 0L) return -1
        val value = super.read()
        if (value >= 0) remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0L) return -1
        val count = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count > 0) remaining -= count.toLong()
        return count
    }
}
