package com.israadev.nuxlauncher.core.streamer

/**
 * Pembuat kode QR sederhana untuk link overlay: mode byte (UTF-8), koreksi galat M, versi 1 sampai 10
 * (sampai 213 byte). Ditulis sendiri supaya launcher tidak perlu pustaka tambahan. Murni JVM, diuji dengan
 * pembaca QR independen.
 *
 * Hasilnya matriks modul (true = gelap) TANPA zona tenang; yang menggambar menambah tepi kosong sendiri.
 */
object QrCode {
    const val MAX_VERSION = 10

    // Level koreksi M, versi 1..10: jumlah kodeword data, kodeword koreksi per blok, jumlah blok.
    private val DATA_WORDS = intArrayOf(16, 28, 44, 64, 86, 108, 124, 154, 182, 216)
    private val ECC_PER_BLOCK = intArrayOf(10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
    private val BLOCKS = intArrayOf(1, 1, 1, 2, 2, 4, 4, 4, 5, 5)

    /** Berapa byte UTF-8 yang muat di versi terbesar. */
    fun maxBytes(): Int = (DATA_WORDS[MAX_VERSION - 1] * 8 - 4 - 16) / 8

    /** Matriks modul untuk [text], atau null bila kosong atau terlalu panjang. */
    fun encode(text: String): Array<BooleanArray>? {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) return null
        var version = 0
        for (v in 1..MAX_VERSION) {
            val countBits = if (v <= 9) 8 else 16
            if (4 + countBits + 8 * bytes.size <= DATA_WORDS[v - 1] * 8) {
                version = v
                break
            }
        }
        if (version == 0) return null

        val data = buildData(bytes, version)
        val all = addEccAndInterleave(data, version)
        return build(all, version)
    }

    private class BitBuffer {
        val bits = ArrayList<Boolean>()
        fun append(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits.add(((value ushr i) and 1) != 0)
        }
    }

    private fun buildData(bytes: ByteArray, version: Int): ByteArray {
        val capacityBits = DATA_WORDS[version - 1] * 8
        val bb = BitBuffer()
        bb.append(0x4, 4)
        bb.append(bytes.size, if (version <= 9) 8 else 16)
        for (b in bytes) bb.append(b.toInt() and 0xFF, 8)
        bb.append(0, minOf(4, capacityBits - bb.bits.size))
        while (bb.bits.size % 8 != 0) bb.bits.add(false)
        var pad = 0xEC
        while (bb.bits.size < capacityBits) {
            bb.append(pad, 8)
            pad = pad xor (0xEC xor 0x11)
        }
        val out = ByteArray(capacityBits / 8)
        for (i in bb.bits.indices) {
            if (bb.bits[i]) out[i ushr 3] = (out[i ushr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()
        }
        return out
    }

    // --- Reed-Solomon di GF(256), polinomial 0x11D ---------------------------------------------

    private fun gfMul(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun rsDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                result[j] = gfMul(result[j], root)
                if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
            }
            root = gfMul(root, 0x02)
        }
        return result
    }

    private fun rsRemainder(data: IntArray, divisor: IntArray): IntArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = b xor result[0]
            for (i in 0 until result.size - 1) result[i] = result[i + 1]
            result[result.size - 1] = 0
            for (i in result.indices) result[i] = result[i] xor gfMul(divisor[i], factor)
        }
        return result
    }

    private fun rawDataModules(version: Int): Int {
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val numAlign = version / 7 + 2
            result -= (25 * numAlign - 10) * numAlign - 55
            if (version >= 7) result -= 36
        }
        return result
    }

    private fun addEccAndInterleave(data: ByteArray, version: Int): ByteArray {
        val numBlocks = BLOCKS[version - 1]
        val eccLen = ECC_PER_BLOCK[version - 1]
        val rawWords = rawDataModules(version) / 8
        val numShort = numBlocks - rawWords % numBlocks
        val shortLen = rawWords / numBlocks
        val divisor = rsDivisor(eccLen)

        val blocks = ArrayList<IntArray>()
        var k = 0
        for (i in 0 until numBlocks) {
            val datLen = shortLen - eccLen + (if (i < numShort) 0 else 1)
            val dat = IntArray(datLen) { data[k + it].toInt() and 0xFF }
            k += datLen
            val ecc = rsRemainder(dat, divisor)
            // Semua blok dibuat sepanjang blok panjang. Blok pendek punya satu sel kosong tepat sebelum
            // kodeword koreksi, supaya kodeword koreksi tiap blok sejajar saat disisipkan.
            val block = IntArray(shortLen + 1)
            for (j in dat.indices) block[j] = dat[j]
            val eccStart = shortLen + 1 - eccLen
            for (j in ecc.indices) block[eccStart + j] = ecc[j]
            blocks.add(block)
        }

        val result = ByteArray(rawWords)
        var out = 0
        for (i in 0..shortLen) {
            for (j in 0 until numBlocks) {
                // Sel kosong (kolom data terakhir) hanya ada di blok pendek dan dilewati.
                if (i != shortLen - eccLen || j >= numShort) {
                    result[out++] = blocks[j][i].toByte()
                }
            }
        }
        return result
    }

    // --- Menyusun matriks ----------------------------------------------------------------------

    private class Grid(val size: Int) {
        val dark = Array(size) { BooleanArray(size) }
        val fn = Array(size) { BooleanArray(size) }
        fun setFn(x: Int, y: Int, v: Boolean) {
            if (x < 0 || y < 0 || x >= size || y >= size) return
            dark[y][x] = v
            fn[y][x] = true
        }
    }

    private fun alignPositions(version: Int, size: Int): IntArray {
        if (version == 1) return IntArray(0)
        val num = version / 7 + 2
        val step = (version * 4 + num * 2 + 1) / (num * 2 - 2) * 2
        val result = IntArray(num)
        result[0] = 6
        var pos = size - 7
        for (i in num - 1 downTo 1) {
            result[i] = pos
            pos -= step
        }
        return result
    }

    private fun bit(v: Int, i: Int): Boolean = ((v ushr i) and 1) != 0

    private fun drawFormat(g: Grid, mask: Int) {
        // Level M = 0b00.
        val data = (0 shl 3) or mask
        var rem = data
        for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        val bits = ((data shl 10) or rem) xor 0x5412
        val size = g.size
        for (i in 0..5) g.setFn(8, i, bit(bits, i))
        g.setFn(8, 7, bit(bits, 6))
        g.setFn(8, 8, bit(bits, 7))
        g.setFn(7, 8, bit(bits, 8))
        for (i in 9 until 15) g.setFn(14 - i, 8, bit(bits, i))
        for (i in 0 until 8) g.setFn(size - 1 - i, 8, bit(bits, i))
        for (i in 8 until 15) g.setFn(8, size - 15 + i, bit(bits, i))
        g.setFn(8, size - 8, true)
    }

    private fun drawVersion(g: Grid, version: Int) {
        if (version < 7) return
        var rem = version
        for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
        val bits = (version shl 12) or rem
        for (i in 0 until 18) {
            val b = bit(bits, i)
            val a = g.size - 11 + i % 3
            val c = i / 3
            g.setFn(a, c, b)
            g.setFn(c, a, b)
        }
    }

    private fun drawFinder(g: Grid, cx: Int, cy: Int) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val dist = maxOf(Math.abs(dx), Math.abs(dy))
                g.setFn(cx + dx, cy + dy, dist != 2 && dist != 4)
            }
        }
    }

    private fun drawAlign(g: Grid, cx: Int, cy: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                g.setFn(cx + dx, cy + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
            }
        }
    }

    private fun drawFunctionPatterns(g: Grid, version: Int) {
        val size = g.size
        for (i in 0 until size) {
            g.setFn(6, i, i % 2 == 0)
            g.setFn(i, 6, i % 2 == 0)
        }
        drawFinder(g, 3, 3)
        drawFinder(g, size - 4, 3)
        drawFinder(g, 3, size - 4)
        val pos = alignPositions(version, size)
        val n = pos.size
        for (i in 0 until n) {
            for (j in 0 until n) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue
                drawAlign(g, pos[i], pos[j])
            }
        }
        drawFormat(g, 0)
        drawVersion(g, version)
    }

    private fun drawCodewords(g: Grid, data: ByteArray) {
        val size = g.size
        var i = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0 until 2) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!g.fn[y][x] && i < data.size * 8) {
                        g.dark[y][x] = ((data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0
                        i++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(g: Grid, mask: Int) {
        for (y in 0 until g.size) {
            for (x in 0 until g.size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert && !g.fn[y][x]) g.dark[y][x] = !g.dark[y][x]
            }
        }
    }

    private fun penalty(g: Grid): Int {
        val n = g.size
        var score = 0
        // Aturan 1: deret lima modul atau lebih yang sewarna, di baris dan kolom.
        for (dir in 0..1) {
            for (a in 0 until n) {
                var run = 1
                for (b in 1 until n) {
                    val cur = if (dir == 0) g.dark[a][b] else g.dark[b][a]
                    val prev = if (dir == 0) g.dark[a][b - 1] else g.dark[b - 1][a]
                    if (cur == prev) {
                        run++
                        if (run == 5) score += 3 else if (run > 5) score += 1
                    } else {
                        run = 1
                    }
                }
            }
        }
        // Aturan 2: kotak 2x2 sewarna.
        for (y in 0 until n - 1) {
            for (x in 0 until n - 1) {
                val c = g.dark[y][x]
                if (c == g.dark[y][x + 1] && c == g.dark[y + 1][x] && c == g.dark[y + 1][x + 1]) score += 3
            }
        }
        // Aturan 3: pola mirip penanda sudut (1:1:3:1:1) dengan empat modul terang di salah satu sisi.
        val p1 = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
        val p2 = booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true)
        for (dir in 0..1) {
            for (a in 0 until n) {
                for (s in 0..n - 11) {
                    var m1 = true
                    var m2 = true
                    for (k in 0 until 11) {
                        val v = if (dir == 0) g.dark[a][s + k] else g.dark[s + k][a]
                        if (v != p1[k]) m1 = false
                        if (v != p2[k]) m2 = false
                        if (!m1 && !m2) break
                    }
                    if (m1) score += 40
                    if (m2) score += 40
                }
            }
        }
        // Aturan 4: keseimbangan modul gelap dan terang.
        var dark = 0
        for (y in 0 until n) for (x in 0 until n) if (g.dark[y][x]) dark++
        val total = n * n
        val k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1
        score += maxOf(k, 0) * 10
        return score
    }

    private fun build(codewords: ByteArray, version: Int): Array<BooleanArray> {
        val size = version * 4 + 17
        val g = Grid(size)
        drawFunctionPatterns(g, version)
        drawCodewords(g, codewords)

        var bestMask = 0
        var bestScore = Int.MAX_VALUE
        for (m in 0 until 8) {
            applyMask(g, m)
            drawFormat(g, m)
            val s = penalty(g)
            if (s < bestScore) {
                bestScore = s
                bestMask = m
            }
            // Mengulang topeng yang sama membatalkannya.
            applyMask(g, m)
        }
        applyMask(g, bestMask)
        drawFormat(g, bestMask)
        return Array(size) { y -> BooleanArray(size) { x -> g.dark[y][x] } }
    }
}
