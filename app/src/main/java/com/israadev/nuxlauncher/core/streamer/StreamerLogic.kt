package com.israadev.nuxlauncher.core.streamer

/**
 * Satu item overlay streamer (teks, gambar, link, atau timer). Disimpan di LauncherSettings sebagai JSON.
 * Semua bidang punya nilai bawaan supaya berkas pengaturan yang kurang kolom tetap terbaca.
 */
data class StreamerItem(
    val id: String = "",
    val type: String = StreamerLogic.TYPE_TEXT,
    /** Teks: isi tulisan. Link: alamatnya. Jenis lain: tidak dipakai. */
    val text: String = "",
    /** Gambar: nama berkas di folder streamer (lihat StreamerImages). */
    val image: String = "",
    /** Link: tampilkan kode QR di atas alamat. */
    val qr: Boolean = true,
    val pos: String = StreamerLogic.POS_TOP,
    val size: String = StreamerLogic.SIZE_MEDIUM,
    /** Tampil di layar sejak game dimulai. Menyalakan atau mematikan lewat sidebar tidak mengubah ini. */
    val on: Boolean = false,
    /** Teks, link, dan timer: kotak latar di belakang tulisan. Mati = tulisan polos dengan bayangan keras (tanpa blur). */
    val bg: Boolean = true,
    /** Warna tulisan (salah satu dari [StreamerLogic.COLORS]). */
    val color: String = StreamerLogic.COLOR_WHITE
)

/**
 * Aturan murni (tanpa Android) untuk overlay streamer: batas isian, perapian teks, alamat link,
 * ukuran, dan stopwatch. Dipisah supaya bisa diuji di JVM.
 */
object StreamerLogic {
    const val TYPE_TEXT = "text"
    const val TYPE_IMAGE = "image"
    const val TYPE_LINK = "link"
    const val TYPE_TIMER = "timer"
    val TYPES = listOf(TYPE_TEXT, TYPE_IMAGE, TYPE_LINK, TYPE_TIMER)

    const val POS_TOP_START = "topstart"
    const val POS_TOP = "top"
    const val POS_TOP_END = "topend"
    const val POS_BOTTOM_START = "bottomstart"
    const val POS_BOTTOM = "bottom"
    const val POS_BOTTOM_END = "bottomend"
    val POSITIONS = listOf(POS_TOP_START, POS_TOP, POS_TOP_END, POS_BOTTOM_START, POS_BOTTOM, POS_BOTTOM_END)

    const val SIZE_SMALL = "small"
    const val SIZE_MEDIUM = "medium"
    const val SIZE_LARGE = "large"
    val SIZES = listOf(SIZE_SMALL, SIZE_MEDIUM, SIZE_LARGE)

    const val COLOR_WHITE = "white"
    const val COLOR_AMBER = "amber"
    const val COLOR_BLACK = "black"
    const val COLOR_RED = "red"
    const val COLOR_BLUE = "blue"
    val COLORS = listOf(COLOR_WHITE, COLOR_AMBER, COLOR_BLACK, COLOR_RED, COLOR_BLUE)

    const val MAX_ITEMS = 8
    const val MAX_TEXT_CHARS = 60
    const val MAX_LINK_CHARS = 190

    /** Sisi terpanjang gambar yang disimpan. Cukup tajam untuk tampil di layar, tetap kecil di memori. */
    const val IMAGE_MAX_SIDE = 512
    private const val MAX_ID_CHARS = 24
    private val IMAGE_NAME = Regex("^[A-Za-z0-9_-]{1,64}\\.png$")
    private val ID_OK = Regex("^[A-Za-z0-9_-]{1,$MAX_ID_CHARS}$")

    fun normalizeType(t: String?): String = TYPES.firstOrNull { it == t } ?: TYPE_TEXT
    fun normalizePos(p: String?): String = POSITIONS.firstOrNull { it == p } ?: POS_TOP
    fun normalizeSize(s: String?): String = SIZES.firstOrNull { it == s } ?: SIZE_MEDIUM
    fun normalizeColor(c: String?): String = COLORS.firstOrNull { it == c } ?: COLOR_WHITE

    /** Warna tulisan (ARGB). */
    fun textArgb(color: String?): Int = when (normalizeColor(color)) {
        COLOR_AMBER -> 0xFFFFD60A.toInt()
        COLOR_BLACK -> 0xFF111111.toInt()
        COLOR_RED -> 0xFFFF5A4D.toInt()
        COLOR_BLUE -> 0xFF5AC8FA.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    /** Warna bayangan tulisan saat tanpa latar: gelap untuk tulisan terang, terang untuk tulisan hitam. */
    fun shadowArgb(color: String?): Int =
        if (normalizeColor(color) == COLOR_BLACK) 0xCCFFFFFF.toInt() else 0xCC000000.toInt()

    /** Warna kotak latar: kaca gelap, atau kaca terang bila tulisannya hitam supaya tetap terbaca. */
    fun chipBgArgb(color: String?): Int =
        if (normalizeColor(color) == COLOR_BLACK) 0xD9F4F3EE.toInt() else 0xB8121217.toInt()

    private fun isBidiControl(cp: Int): Boolean =
        cp in 0x202A..0x202E || cp in 0x2066..0x2069 || cp == 0x200E || cp == 0x200F || cp == 0x061C

    /**
     * Merapikan teks dari pengguna: spasi dan baris baru jadi satu spasi, ujung dipangkas, karakter kontrol
     * dan pengubah arah tulisan dibuang, lalu dipotong di [maxChars] huruf (emoji dihitung satu).
     */
    fun cleanText(raw: String?, maxChars: Int): String {
        if (raw.isNullOrEmpty() || maxChars <= 0) return ""
        val sb = StringBuilder()
        var count = 0
        var pendingSpace = false
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            i += Character.charCount(cp)
            // Setengah pasangan surrogate tidak bisa digambar.
            if (cp in 0xD800..0xDFFF) continue
            if (isBidiControl(cp)) continue
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp) || Character.isISOControl(cp)) {
                if (sb.isNotEmpty()) pendingSpace = true
                continue
            }
            if (count >= maxChars) break
            if (pendingSpace) {
                // Spasi dan satu huruf sesudahnya harus sama-sama muat.
                if (count + 1 >= maxChars) break
                sb.append(' ')
                count++
                pendingSpace = false
            }
            sb.appendCodePoint(cp)
            count++
        }
        return sb.toString()
    }

    /** Memotong isian di [maxChars] huruf tanpa merapikan, supaya spasi masih bisa diketik. */
    fun limitInput(raw: String, maxChars: Int): String {
        if (raw.length <= maxChars) return raw
        val total = raw.codePointCount(0, raw.length)
        if (total <= maxChars) return raw
        return raw.substring(0, raw.offsetByCodePoints(0, maxChars))
    }

    /** Alamat link: dirapikan seperti teks, lalu semua spasi dibuang (alamat tidak mengandung spasi). */
    fun cleanLink(raw: String?): String = cleanText(raw, MAX_LINK_CHARS).replace(" ", "")

    private fun hasScheme(s: String): Boolean =
        s.contains("://") || s.startsWith("mailto:", ignoreCase = true)

    private fun looksLikeHost(s: String): Boolean {
        val host = s.substringBefore('/')
        return host.contains('.') && !host.startsWith('.') && !host.endsWith('.') && !host.contains('@')
    }

    /** Isi kode QR: alamat apa adanya; kalau hanya nama situs tanpa skema, ditambah https:// supaya terbaca sebagai link. */
    fun qrPayload(raw: String?): String {
        val s = cleanLink(raw)
        if (s.isEmpty()) return ""
        return if (hasScheme(s) || !looksLikeHost(s)) s else "https://$s"
    }

    /** Alamat untuk ditampilkan: tanpa skema, tanpa www., tanpa garis miring di ujung. */
    fun linkDisplay(raw: String?): String {
        val s = cleanLink(raw)
        var t = s
        for (prefix in listOf("https://", "http://")) {
            if (t.startsWith(prefix, ignoreCase = true)) {
                t = t.substring(prefix.length)
                break
            }
        }
        if (t.startsWith("www.", ignoreCase = true)) t = t.substring(4)
        t = t.trimEnd('/')
        return if (t.isEmpty()) s else t
    }

    /** Nama berkas gambar yang aman (tanpa folder, tanpa ".."), atau null. */
    fun safeImageName(name: String?): String? = name?.takeIf { IMAGE_NAME.matches(it) }

    private fun cleanId(id: String?): String = id?.takeIf { ID_OK.matches(it) } ?: ""

    fun newId(used: Set<String>, seed: Long): String {
        var n = if (seed < 0) -seed else seed
        while (true) {
            val id = "i" + n.toString(36)
            if (id !in used) return id
            n++
        }
    }

    /** Merapikan satu item. Id dibiarkan kosong bila tidak valid; [sanitize] yang memberi id. */
    fun sanitizeOne(src: StreamerItem): StreamerItem {
        val type = normalizeType(src.type)
        return StreamerItem(
            id = cleanId(src.id),
            type = type,
            text = when (type) {
                TYPE_TEXT -> cleanText(src.text, MAX_TEXT_CHARS)
                TYPE_LINK -> cleanLink(src.text)
                else -> ""
            },
            image = if (type == TYPE_IMAGE) safeImageName(src.image) ?: "" else "",
            qr = src.qr,
            pos = normalizePos(src.pos),
            size = normalizeSize(src.size),
            on = src.on,
            bg = src.bg,
            color = normalizeColor(src.color)
        )
    }

    /** Daftar item yang aman dipakai: batas jumlah, jenis/posisi/ukuran valid, id unik dan tidak kosong. */
    fun sanitize(raw: List<StreamerItem?>?): List<StreamerItem> {
        if (raw == null) return emptyList()
        val out = ArrayList<StreamerItem>()
        val used = HashSet<String>()
        for (src in raw) {
            // Berkas pengaturan yang rusak bisa memuat elemen kosong (null) meski tipenya tidak boleh null.
            if (src == null) continue
            if (out.size >= MAX_ITEMS) break
            val item = sanitizeOne(src)
            val id = if (item.id.isEmpty() || item.id in used) newId(used, out.size.toLong() + 1L) else item.id
            used.add(id)
            out.add(if (id == item.id) item else item.copy(id = id))
        }
        return out
    }

    /** Item yang punya isi untuk digambar (teks kosong atau gambar belum dipilih tidak ikut tampil). */
    fun isDisplayable(item: StreamerItem): Boolean = when (item.type) {
        TYPE_TEXT, TYPE_LINK -> item.text.isNotEmpty()
        TYPE_IMAGE -> item.image.isNotEmpty()
        TYPE_TIMER -> true
        else -> false
    }

    /** Boleh disimpan: setelah dibersihkan, item masih punya isi untuk digambar. */
    fun canSave(item: StreamerItem): Boolean = isDisplayable(sanitizeOne(item))

    /** MM:SS, atau H:MM:SS bila sudah lewat satu jam. */
    fun formatElapsed(ms: Long): String {
        val total = if (ms < 0L) 0L else ms / 1000L
        val h = total / 3600L
        val m = (total / 60L) % 60L
        val s = total % 60L
        return if (h > 0L) "$h:${two(m)}:${two(s)}" else "${two(m)}:${two(s)}"
    }

    private fun two(v: Long): String = if (v < 10L) "0$v" else "$v"

    fun textSp(size: String): Int = when (normalizeSize(size)) {
        SIZE_SMALL -> 12
        SIZE_LARGE -> 20
        else -> 15
    }

    fun linkSp(size: String): Int = when (normalizeSize(size)) {
        SIZE_SMALL -> 10
        SIZE_LARGE -> 15
        else -> 12
    }

    fun imageHeightDp(size: String): Int = when (normalizeSize(size)) {
        SIZE_SMALL -> 48
        SIZE_LARGE -> 128
        else -> 80
    }

    fun qrSizeDp(size: String): Int = when (normalizeSize(size)) {
        SIZE_SMALL -> 88
        SIZE_LARGE -> 168
        else -> 124
    }

    /** Ukuran gambar tampil: setinggi [maxH], tetap seimbang, dan tidak lebih lebar dari [maxW]. */
    fun fitSize(ratio: Float, maxH: Float, maxW: Float): Pair<Float, Float> {
        val r = if (ratio.isFinite() && ratio > 0f) ratio else 1f
        var h = maxH
        var w = h * r
        if (w > maxW) {
            w = maxW
            h = w / r
        }
        return Pair(w, h)
    }

    /** Pembagi pengecilan saat memuat gambar besar: sisi terpanjang hasilnya tetap minimal [maxSide]. */
    fun sampleSize(w: Int, h: Int, maxSide: Int): Int {
        if (w <= 0 || h <= 0 || maxSide <= 0) return 1
        val longSide = maxOf(w, h)
        var s = 1
        while (longSide / (s * 2) >= maxSide) s *= 2
        return s
    }

    /** Ukuran akhir gambar yang disimpan: sisi terpanjang paling besar [maxSide]. */
    fun scaledSize(w: Int, h: Int, maxSide: Int): Pair<Int, Int> {
        val longSide = maxOf(w, h)
        if (longSide <= maxSide || longSide <= 0) return Pair(w, h)
        val k = maxSide.toDouble() / longSide.toDouble()
        return Pair(maxOf(1, Math.round(w * k).toInt()), maxOf(1, Math.round(h * k).toInt()))
    }
}

/** Stopwatch dengan jam yang bisa diganti (untuk uji). Jam harus naik terus, mis. SystemClock.elapsedRealtime. */
class Stopwatch(private val clock: () -> Long) {
    private var base = 0L
    private var startedAt = 0L
    var running = false
        private set

    fun elapsedMs(): Long {
        val extra = if (running) (clock() - startedAt).coerceAtLeast(0L) else 0L
        return base + extra
    }

    fun start() {
        if (running) return
        startedAt = clock()
        running = true
    }

    fun pause() {
        if (!running) return
        base = elapsedMs()
        running = false
    }

    /** Kembali ke nol. Bila sedang berjalan, lanjut menghitung dari nol. */
    fun reset() {
        base = 0L
        startedAt = clock()
    }

    /** Berapa milidetik lagi sampai detik berikutnya, supaya tampilan berganti tepat waktu. */
    fun msToNextSecond(): Long = 1000L - (elapsedMs() % 1000L)
}
