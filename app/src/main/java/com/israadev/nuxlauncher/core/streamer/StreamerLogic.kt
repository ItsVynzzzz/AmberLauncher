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
    val color: String = StreamerLogic.COLOR_WHITE,
    /**
     * Posisi bebas di layar, pecahan 0..1 dari ruang gerak (0 = rapat kiri atau atas, 1 = rapat kanan atau
     * bawah). Selama keduanya di luar 0..1 (bawaan -1), posisi mengikuti titik [pos].
     */
    val x: Float = -1f,
    val y: Float = -1f,
    /** Skala ukuran bebas ([StreamerLogic.SCALE_MIN]..[StreamerLogic.SCALE_MAX]). 0 = ikuti [size]. */
    val scale: Float = 0f,
    /** Kepekatan seluruh item ([StreamerLogic.ALPHA_MIN]..1). */
    val alpha: Float = 1f,
    /**
     * Link: jalankan alamatnya sebagai overlay web di atas game (seperti browser source di OBS), untuk link overlay
     * Saweria, Sociabuzz, Trakteer, StreamElements, Streamlabs, dan sejenisnya. Alamatnya sensitif, jadi tidak
     * pernah ditulis di layar, di panel, maupun (tanpa ketukan mata) di kolom isian.
     */
    val web: Boolean = false,
    /**
     * Overlay web: lebar dan tinggi kotak tempatnya di layar, pecahan dari lebar dan tinggi layar
     * ([StreamerLogic.WEB_BOX_MIN]..1). 0 = penuh. Letak kiri-atas kotak mengikuti [x] dan [y].
     */
    val boxW: Float = 0f,
    val boxH: Float = 0f,
    /**
     * Overlay web: skala halaman ([StreamerLogic.ZOOM_MIN]..1, 0 = 100%). Di bawah 100% halaman ditata untuk layar
     * yang lebih besar lalu dikecilkan ke kotaknya, seperti ukuran kanvas browser source di OBS: alert besar yang
     * dirancang untuk 1080p jadi muat di layar HP. Makin kecil, makin besar RAM yang dipakai.
     */
    val zoom: Float = 0f
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
    const val COLOR_GREEN = "green"
    const val COLOR_PURPLE = "purple"
    const val COLOR_PINK = "pink"
    val COLORS = listOf(
        COLOR_WHITE, COLOR_AMBER, COLOR_RED, COLOR_PINK,
        COLOR_PURPLE, COLOR_BLUE, COLOR_GREEN, COLOR_BLACK
    )

    const val SCALE_MIN = 0.5f
    const val SCALE_MAX = 3f
    const val ALPHA_MIN = 0.3f

    /** Jarak tumpukan (pecahan tinggi layar) saat item lama yang berbagi satu titik diubah jadi posisi bebas. */
    private const val STACK_STEP = 0.18f

    const val MAX_ITEMS = 8
    const val MAX_TEXT_CHARS = 60
    const val MAX_LINK_CHARS = 190

    /** Alamat overlay web membawa token panjang (StreamElements bisa lebih dari 400 huruf), jadi batasnya longgar. */
    const val MAX_WEB_CHARS = 1024

    /** Paling banyak overlay web yang tampil bersamaan; tiap halaman memakan RAM. */
    const val MAX_WEB_SHOWN = 4

    /** Kotak overlay web paling kecil (pecahan layar) dan skala halaman paling kecil. */
    const val WEB_BOX_MIN = 0.1f
    const val ZOOM_MIN = 0.5f

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
        COLOR_GREEN -> 0xFF4CD964.toInt()
        COLOR_PURPLE -> 0xFFBF5AF2.toInt()
        COLOR_PINK -> 0xFFFF6FB5.toInt()
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

    /** Item ini dijalankan sebagai overlay web (bukan ditulis sebagai teks atau kode QR). */
    fun isWeb(item: StreamerItem): Boolean = item.web && normalizeType(item.type) == TYPE_LINK

    /** Batas isian kolom link: lebih longgar untuk overlay web. */
    fun maxLinkChars(web: Boolean): Int = if (web) MAX_WEB_CHARS else MAX_LINK_CHARS

    /** Alamat overlay web: dirapikan seperti [cleanLink] tetapi dengan batas [MAX_WEB_CHARS]. */
    fun cleanWebUrl(raw: String?): String = cleanText(raw, MAX_WEB_CHARS).replace(" ", "")

    /**
     * Alamat https yang aman dimuat sebagai overlay web, atau null. Nama situs tanpa skema diberi https://.
     * Skema lain (http, javascript:, file:, content:, intent:, data:, dll) ditolak, begitu juga alamat tanpa nama
     * situs yang utuh, supaya halaman bertoken tidak pernah terkirim lewat jalur tanpa enkripsi.
     */
    fun webUrl(raw: String?): String? {
        val s = cleanWebUrl(raw)
        if (s.isEmpty()) return null
        val full = when {
            s.startsWith("https://", ignoreCase = true) -> s
            hasScheme(s) -> return null
            looksLikeHost(s) -> "https://$s"
            else -> return null
        }
        val host = try {
            java.net.URI(full).host
        } catch (_: Exception) {
            null
        }
        if (host.isNullOrEmpty() || !host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
        return full
    }

    /** Nama situs saja (tanpa skema, www., jalur, token, dan port), huruf kecil. Kosong bila tidak terbaca. */
    fun hostOf(raw: String?): String {
        var t = cleanWebUrl(raw)
        val scheme = t.indexOf("://")
        if (scheme >= 0) t = t.substring(scheme + 3)
        t = t.substringBefore('/').substringBefore('?').substringBefore('#')
        t = t.substringAfterLast('@').substringBefore(':')
        if (t.startsWith("www.", ignoreCase = true)) t = t.substring(4)
        return t.lowercase()
    }

    // Petunjuk di alamat bahwa ini link overlay (bukan halaman donasi biasa). Hanya kata yang cukup panjang dan khas,
    // supaya token acak tidak ikut terhitung.
    private val OVERLAY_HINTS = listOf("overlay", "widget", "alert", "streamkey", "stream-key", "browsersource", "browser-source")

    /**
     * Alamat ini tampak seperti link overlay untuk browser source (Alert, Media Share, Song Share, Subathon, Wheel,
     * Goal, dan seterusnya di Saweria, Sociabuzz, Trakteer, StreamElements, Streamlabs, dll)? Dipakai untuk
     * menyalakan mode overlay web otomatis saat link ditempel; pengguna tetap bisa mematikannya.
     */
    fun looksLikeOverlay(raw: String?): Boolean {
        val s = cleanWebUrl(raw).lowercase()
        if (s.length < 8) return false
        val body = s.substringAfter("://", s)
        return OVERLAY_HINTS.any { body.contains(it) }
    }

    /**
     * Mengganti alamat link dari isian pengguna. Alamat yang baru tampak seperti link overlay (mis. ditempel dari
     * dasbor Saweria atau Sociabuzz) otomatis menyalakan mode overlay web; setelah itu pengguna bebas mematikannya.
     */
    fun withLinkText(item: StreamerItem, raw: String): StreamerItem {
        val overlayLike = looksLikeOverlay(raw)
        val text = limitInput(raw, maxLinkChars(item.web || overlayLike))
        val auto = !item.web && overlayLike && !looksLikeOverlay(item.text)
        return if (auto) item.copy(text = text, web = true) else item.copy(text = text)
    }

    /**
     * Penanda link di daftar dan panel. Link sensitif (overlay web, atau yang tampil sebagai kode QR) hanya
     * menampilkan nama situsnya; alamat lengkap tidak pernah ditulis. Link biasa tanpa QR tetap apa adanya.
     */
    fun linkLabel(item: StreamerItem): String =
        if (isWeb(item) || item.qr) hostOf(item.text).ifEmpty { linkDisplay(item.text) } else linkDisplay(item.text)

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
        val web = src.web && type == TYPE_LINK
        return StreamerItem(
            id = cleanId(src.id),
            type = type,
            text = when (type) {
                TYPE_TEXT -> cleanText(src.text, MAX_TEXT_CHARS)
                TYPE_LINK -> if (src.web) cleanWebUrl(src.text) else cleanLink(src.text)
                else -> ""
            },
            image = if (type == TYPE_IMAGE) safeImageName(src.image) ?: "" else "",
            qr = src.qr,
            pos = normalizePos(src.pos),
            size = normalizeSize(src.size),
            on = src.on,
            bg = src.bg,
            color = normalizeColor(src.color),
            x = if (src.x in 0f..1f && src.y in 0f..1f) src.x else -1f,
            y = if (src.x in 0f..1f && src.y in 0f..1f) src.y else -1f,
            scale = if (src.scale.isFinite() && src.scale > 0f) src.scale.coerceIn(SCALE_MIN, SCALE_MAX) else 0f,
            alpha = if (src.alpha.isFinite()) src.alpha.coerceIn(ALPHA_MIN, 1f) else 1f,
            web = web,
            boxW = if (web) boxFractionOrZero(src.boxW) else 0f,
            boxH = if (web) boxFractionOrZero(src.boxH) else 0f,
            zoom = if (web && src.zoom.isFinite() && src.zoom > 0f) src.zoom.coerceIn(ZOOM_MIN, 1f) else 0f
        )
    }

    /** Pecahan kotak yang valid, atau 0 (penuh) bila kosong atau tidak masuk akal. */
    private fun boxFractionOrZero(v: Float): Float =
        if (v.isFinite() && v > 0f) v.coerceIn(WEB_BOX_MIN, 1f) else 0f

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
        TYPE_TEXT -> item.text.isNotEmpty()
        // Overlay web hanya layak tampil bila alamatnya https yang utuh.
        TYPE_LINK -> if (item.web) webUrl(item.text) != null else item.text.isNotEmpty()
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

    /** Item punya posisi bebas (bukan titik [StreamerItem.pos]). */
    fun isFree(item: StreamerItem): Boolean = item.x in 0f..1f && item.y in 0f..1f

    private fun hasScale(item: StreamerItem): Boolean = item.scale.isFinite() && item.scale > 0f

    /** Skala ukuran item: skala bebas bila ada, kalau tidak setara pilihan kecil/sedang/besar. */
    fun scaleOf(item: StreamerItem): Float =
        if (hasScale(item)) {
            item.scale.coerceIn(SCALE_MIN, SCALE_MAX)
        } else {
            when (normalizeSize(item.size)) {
                SIZE_SMALL -> 0.8f
                SIZE_LARGE -> 1.35f
                else -> 1f
            }
        }

    private fun scaled(item: StreamerItem, base: Int, bySize: Int): Int =
        if (hasScale(item)) maxOf(1, Math.round(base * scaleOf(item))) else bySize

    fun textSpOf(item: StreamerItem): Int = scaled(item, textSp(SIZE_MEDIUM), textSp(item.size))
    fun linkSpOf(item: StreamerItem): Int = scaled(item, linkSp(SIZE_MEDIUM), linkSp(item.size))
    fun imageHeightDpOf(item: StreamerItem): Int = scaled(item, imageHeightDp(SIZE_MEDIUM), imageHeightDp(item.size))
    fun qrSizeDpOf(item: StreamerItem): Int = scaled(item, qrSizeDp(SIZE_MEDIUM), qrSizeDp(item.size))

    /** Letak titik bawaan sebagai posisi bebas (pecahan 0..1). */
    fun anchorXY(pos: String): Pair<Float, Float> = when (normalizePos(pos)) {
        POS_TOP_START -> Pair(0f, 0f)
        POS_TOP_END -> Pair(1f, 0f)
        POS_BOTTOM_START -> Pair(0f, 1f)
        POS_BOTTOM -> Pair(0.5f, 1f)
        POS_BOTTOM_END -> Pair(1f, 1f)
        else -> Pair(0.5f, 0f)
    }

    /**
     * Mengubah item yang masih memakai titik bawaan jadi posisi bebas (untuk editor di layar). Item yang
     * berbagi satu titik disusun bertumpuk seperti sebelumnya supaya tidak saling menutup.
     */
    fun withFreePositions(items: List<StreamerItem>): List<StreamerItem> {
        val seen = HashMap<String, Int>()
        return items.map { item ->
            if (isFree(item)) {
                item
            } else {
                val k = seen[item.pos] ?: 0
                seen[item.pos] = k + 1
                val (ax, ay) = anchorXY(item.pos)
                val dy = STACK_STEP * k
                item.copy(x = ax, y = (if (ay < 0.5f) ay + dy else ay - dy).coerceIn(0f, 1f))
            }
        }
    }

    /** Lebar atau tinggi kotak overlay web sebagai pecahan layar: 0 atau tidak valid = penuh. */
    fun webBoxFraction(v: Float): Float = if (!v.isFinite() || v <= 0f) 1f else v.coerceIn(WEB_BOX_MIN, 1f)

    /** Skala halaman overlay web (1 = 100%). */
    fun webZoomOf(item: StreamerItem): Float =
        if (!item.zoom.isFinite() || item.zoom <= 0f) 1f else item.zoom.coerceIn(ZOOM_MIN, 1f)

    /** Kotak overlay web di layar, dalam piksel. */
    data class WebRect(val left: Int, val top: Int, val width: Int, val height: Int)

    /**
     * Kotak overlay web di wadah seluas [areaW] x [areaH] piksel. Ukurannya pecahan wadah ([boxW], [boxH]; 0 = penuh),
     * letaknya mengikuti aturan posisi bebas ([x], [y]), jadi tidak pernah keluar wadah. Tanpa posisi bebas
     * (bawaan -1) kotak rapat ke kiri atas.
     */
    fun webRect(areaW: Int, areaH: Int, x: Float, y: Float, boxW: Float, boxH: Float): WebRect {
        val w = maxOf(1, Math.round(maxOf(areaW, 0) * webBoxFraction(boxW)))
        val h = maxOf(1, Math.round(maxOf(areaH, 0) * webBoxFraction(boxH)))
        val free = x in 0f..1f && y in 0f..1f
        return WebRect(
            left = placeAt(if (free) x else 0f, areaW - w),
            top = placeAt(if (free) y else 0f, areaH - h),
            width = w,
            height = h
        )
    }

    /**
     * Mengubah ukuran kotak overlay web sebesar [dxPx], [dyPx] piksel dari sudut kanan bawahnya (seret pegangan sudut).
     * Sudut kiri atas tetap di tempatnya: [StreamerItem.x] dan [StreamerItem.y] disesuaikan dengan ukuran barunya.
     * Kotak tidak bisa lebih kecil dari [WEB_BOX_MIN] dan tidak melewati tepi kanan atau bawah wadah.
     */
    fun resizedWebBox(item: StreamerItem, areaW: Int, areaH: Int, dxPx: Float, dyPx: Float): StreamerItem {
        if (areaW <= 0 || areaH <= 0 || !dxPx.isFinite() || !dyPx.isFinite()) return item
        val r = webRect(areaW, areaH, item.x, item.y, item.boxW, item.boxH)
        val minW = maxOf(1, Math.round(areaW * WEB_BOX_MIN))
        val minH = maxOf(1, Math.round(areaH * WEB_BOX_MIN))
        val nw = Math.round(r.width + dxPx).coerceIn(minOf(minW, areaW - r.left).coerceAtLeast(1), maxOf(areaW - r.left, 1))
        val nh = Math.round(r.height + dyPx).coerceIn(minOf(minH, areaH - r.top).coerceAtLeast(1), maxOf(areaH - r.top, 1))
        // Kiri atas tetap: x dan y baru dihitung dari ruang gerak yang baru (0 bila kotak memenuhi sisi itu).
        val nx = if (areaW - nw > 0) r.left.toFloat() / (areaW - nw) else 0f
        val ny = if (areaH - nh > 0) r.top.toFloat() / (areaH - nh) else 0f
        return item.copy(
            x = nx.coerceIn(0f, 1f),
            y = ny.coerceIn(0f, 1f),
            boxW = (nw.toFloat() / areaW).coerceIn(WEB_BOX_MIN, 1f),
            boxH = (nh.toFloat() / areaH).coerceIn(WEB_BOX_MIN, 1f)
        )
    }

    /** Titik piksel tempat item ditaruh: pecahan [fraction] dari ruang gerak [rangePx] (tidak pernah keluar wadah). */
    fun placeAt(fraction: Float, rangePx: Int): Int =
        if (rangePx <= 0 || !fraction.isFinite()) 0 else Math.round(fraction.coerceIn(0f, 1f) * rangePx)

    /** Pecahan posisi baru setelah digeser [deltaPx] piksel; tanpa ruang gerak, posisi tidak berubah. */
    fun movedFraction(current: Float, deltaPx: Float, rangePx: Float): Float {
        val c = if (current.isFinite()) current.coerceIn(0f, 1f) else 0f
        return if (rangePx < 1f || !deltaPx.isFinite()) c else (c + deltaPx / rangePx).coerceIn(0f, 1f)
    }

    /** Skala baru setelah dicubit sebesar [zoom] kali. */
    fun zoomedScale(current: Float, zoom: Float): Float =
        if (!zoom.isFinite() || zoom <= 0f) current else (current * zoom).coerceIn(SCALE_MIN, SCALE_MAX)

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
